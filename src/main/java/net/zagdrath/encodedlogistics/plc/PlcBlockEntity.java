/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.plc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.RedstoneChannels;
import net.zagdrath.encodedlogistics.blockentity.RedstoneDevice;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.compile.FileResolver;
import net.zagdrath.encodedlogistics.elcl.compile.VarDecl;
import net.zagdrath.encodedlogistics.elcl.exec.Authority;
import net.zagdrath.encodedlogistics.elcl.exec.ElclEvents;
import net.zagdrath.encodedlogistics.elcl.exec.NamedDevice;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.store.SystemData;
import net.zagdrath.encodedlogistics.elcl.vm.Vm;
import net.zagdrath.encodedlogistics.elcl.vm.VmHost;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;

// A PLC (docs/plc HANDOFF 1, 4): its six redstone channels (RedstoneChannels, as a Control Interface's), four module
// slots and their settings, its program (PlcProgram: kept here, on its item, on a cartridge) and its mode.
//
// Power: plcEnergy FE a tick from its own buffer (filled from any face), or from its network while it's online on one.
// Without power it's OFF - outputs dark, the program halted; when power comes back it runs again from the top (as
// STOP -> RUN), if it was running.
//
// The scan: the program runs continuously, plcInstructionsPerTick instructions a tick - a pass that needs more carries
// on next tick; on ENDPGM the next pass starts from the top next tick, its variables carried on from this one (a VALUE
// is set again only on STOP -> RUN and power-up). RETAIN(*YES) variables are kept in the program itself, so they last
// through STOP / RUN, power loss, world reloads and cartridges. A world reload is a power-up.
//
// STOP clears its outputs. An unmonitored escape puts it in FAULT: the message and its line are kept for the status
// screen and the LCD, outputs hold their last levels (or drop to 0: plcFaultOutputs). F9 / STRPLC clears it and runs
// from the top. At most plcMaxPerChunk run in a chunk and plcMaxPerServer on the server; another stays in STOP
// ("PLC limit reached") until there's room.
//
// On a network it's a device (PLC01, ... - NamedDevice), its program running with the Firewall authority of the
// player who last loaded it; a change on a face fires *RSCHANGE there. Each face's I/O LED lights for 10 ticks after
// its level changes (PlcRenderer, from the update tag's io bits).
public class PlcBlockEntity extends BlockEntity implements NetworkDevice, NamedDevice, RedstoneDevice {
    public static final String TYPE = "PLC";
    public static final String DEFAULT_PROGRAM = "PLCPGM";
    private static final Direction[] FACES = RedstoneChannels.FACES;
    private static final int LOG_SIZE = 16, ACTIVITY_TICKS = 10;
    // The PLCs running now (the limits count them).
    private static final Set<PlcBlockEntity> RUNNING = Collections.newSetFromMap(new WeakHashMap<>());

    // The last fault: its message, the line it came from (-1: none) and the text as shown.
    public record Fault(String id, String text, int line) {
        static final Codec<Fault> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.STRING.fieldOf("id").forGetter(Fault::id),
                Codec.STRING.fieldOf("text").forGetter(Fault::text), Codec.INT.fieldOf("line").forGetter(Fault::line)).apply(i, Fault::new));
    }

    private final RedstoneChannels channels = new RedstoneChannels();
    // When each face's level last changed (game time), and the program line whose CHGRSOUT set its output (0: none).
    private final long[] changedAt = new long[6];
    private final int[] drivenLine = new int[6];
    private final PlcModule[] modules = new PlcModule[4];
    private final PlcSensors.Setting[] settings = new PlcSensors.Setting[4];
    private @Nullable PlcProgram program;
    // The RUN switch (F6 / STRPLC on, F7 / ENDPLC off).
    private boolean running;
    private @Nullable Fault fault, lastError;
    private String note = "", name = "";
    private @Nullable UUID owner;
    private boolean online, powered, inputsDirty = true, stateDirty = true;
    private final Buffer energy = new Buffer();
    // The running program: its VM (null between scans), the variables the next scan starts from (null: the retained
    // ones), and the scan's numbers.
    private @Nullable Vm vm;
    private @Nullable Map<String, Object> carry;
    private long scanStart;
    private int scanInstructions, lastScanTicks, lastScanInstructions;
    private final Deque<String> log = new ArrayDeque<>();
    // The program's declarations (for its retained variables), as compiled for a PLC; null: not compiled yet.
    private @Nullable Map<String, VarDecl> declarations;
    // The I/O LEDs lit (a bit per face, Direction order): the client's copy draws them.
    private int ioBits;

    public PlcBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.PLC.get(), pos, state);
        Arrays.fill(modules, PlcModule.EMPTY);
        Arrays.fill(settings, PlcSensors.Setting.DEFAULT);
    }

    // --- What it is ---

    public Direction facing() {
        return getBlockState().getValue(PlcBlock.FACING);
    }

    public PlcBlock.Mode mode() {
        if (!powered) {
            return PlcBlock.Mode.OFF;
        }
        if (fault != null) {
            return PlcBlock.Mode.FAULT;
        }
        return running && program != null && note.isEmpty() ? PlcBlock.Mode.RUN : PlcBlock.Mode.STOP;
    }

    public @Nullable PlcProgram program() {
        return program;
    }

    public @Nullable Fault fault() {
        return fault;
    }

    // The last error (the current fault, or the one before F9 cleared it).
    public @Nullable Fault lastError() {
        return fault != null ? fault : lastError;
    }

    // Why it isn't running though it's switched on ("PLC limit reached"), or "".
    public String note() {
        return note;
    }

    public boolean runSwitch() {
        return running;
    }

    public int scanTicks() {
        return lastScanTicks;
    }

    // Instructions a tick over the last scan.
    public int instructionsPerTick() {
        return lastScanTicks <= 0 ? 0 : Math.round((float) lastScanInstructions / lastScanTicks);
    }

    public List<String> log() {
        return List.copyOf(log);
    }

    public PlcModule module(int slot) {
        return modules[slot];
    }

    public PlcSensors.Setting setting(int slot) {
        return settings[slot];
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public @Nullable NetworkRef network() {
        return online && level instanceof ServerLevel serverLevel ? ControllerStructures.networkOf(serverLevel, worldPosition) : null;
    }

    public int energyStored() {
        return energy.getAmountAsInt();
    }

    public EnergyHandler getEnergyHandler(@Nullable Direction side) {
        return energy;
    }

    // --- Device (NamedDevice) ---

    @Override
    public String deviceType() {
        return TYPE;
    }

    @Override
    public String deviceName() {
        return name;
    }

    @Override
    public void setDeviceName(String name) {
        this.name = name;
        setChanged();
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    @Override
    public void setNetworkOnline(boolean online) {
        if (this.online == online) {
            return;
        }
        this.online = online;
        inputsDirty = true;
        stateDirty = true;
    }

    // --- Redstone ---

    public int emitted(Direction face) {
        return channels.emitted(face, powered);
    }

    public int output(Direction face) {
        return channels.output(face);
    }

    @Override
    public int input(Direction face) {
        if (inputsDirty) {
            readInputs();
        }
        return channels.input(face);
    }

    @Override
    public int maxInput() {
        if (inputsDirty) {
            readInputs();
        }
        return channels.maxInput();
    }

    // The program line whose CHGRSOUT last set a face's output (0: none since it started).
    public int drivenLine(Direction face) {
        return drivenLine[face.ordinal()];
    }

    @Override
    public void setOutput(@Nullable Direction face, int level, int line) {
        Direction[] changed = channels.setOutput(face, level);
        for (Direction side : changed) {
            drivenLine[side.ordinal()] = line;
            changedAt[side.ordinal()] = gameTime();
        }
        if (changed.length > 0) {
            setChanged();
            notifyNeighbours();
        }
    }

    public void inputsChanged() {
        inputsDirty = true;
    }

    private void clearOutputs() {
        setOutput(null, 0, 0);
        Arrays.fill(drivenLine, 0);
    }

    private void notifyNeighbours() {
        if (level != null && !level.isClientSide()) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    private long gameTime() {
        return level != null ? level.getGameTime() : 0;
    }

    // What arrives on each face, its own output left out; a change fires *RSCHANGE on its network.
    private void readInputs() {
        inputsDirty = false;
        if (level == null || level.isClientSide()) {
            return;
        }
        int[] before = channels.read(level, worldPosition, powered);
        if (before == null) {
            return;
        }
        NetworkRef network = network();
        for (Direction face : FACES) {
            if (before[face.ordinal()] != channels.input(face)) {
                changedAt[face.ordinal()] = gameTime();
                if (network != null && level instanceof ServerLevel serverLevel) {
                    ElclEvents.redstoneChanged(serverLevel.getServer(), network, name, face, channels.input(face));
                }
            }
        }
    }

    // --- Modules ---

    // A sensor module into the first free slot (false: none free, or not a module).
    public boolean insert(ItemStack stack) {
        if (!(stack.getItem() instanceof PlcModuleItem item)) {
            return false;
        }
        for (int slot = 0; slot < modules.length; slot++) {
            if (modules[slot] == PlcModule.EMPTY) {
                modules[slot] = item.module();
                settings[slot] = PlcSensors.Setting.DEFAULT;
                stateDirty = true;
                setChanged();
                return true;
            }
        }
        return false;
    }

    // The module in a slot, out (its item; empty when there's none).
    public ItemStack remove(int slot) {
        PlcModule module = modules[slot];
        if (module == PlcModule.EMPTY) {
            return ItemStack.EMPTY;
        }
        modules[slot] = PlcModule.EMPTY;
        settings[slot] = PlcSensors.Setting.DEFAULT;
        stateDirty = true;
        setChanged();
        return new ItemStack(item(module));
    }

    // Sneaking with an empty hand: the last module out.
    public ItemStack removeLast() {
        for (int slot = modules.length - 1; slot >= 0; slot--) {
            if (modules[slot] != PlcModule.EMPTY) {
                return remove(slot);
            }
        }
        return ItemStack.EMPTY;
    }

    public void setSetting(int slot, PlcSensors.Setting setting) {
        settings[slot] = setting;
        setChanged();
    }

    public static Item item(PlcModule module) {
        return switch (module) {
            case PRESENCE_SENSOR -> ModItems.PRESENCE_SENSOR.get();
            case INVENTORY_SENSOR -> ModItems.INVENTORY_SENSOR.get();
            case FLUID_SENSOR -> ModItems.FLUID_SENSOR.get();
            case LIGHT_SENSOR -> ModItems.LIGHT_SENSOR.get();
            case TIMER_MODULE -> ModItems.TIMER_MODULE.get();
            case EMPTY -> net.minecraft.world.item.Items.AIR;
        };
    }

    // A module's reading now (slot 0-3).
    public PlcSensors.Reading read(int slot) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return PlcSensors.NO_MODULE;
        }
        return PlcSensors.read(serverLevel, worldPosition, facing(), modules[slot], settings[slot]);
    }

    // The modules as they go with the item (Display: the screen) - only what's installed.
    public List<ItemStack> drops() {
        List<ItemStack> drops = new ArrayList<>();
        for (PlcModule module : modules) {
            if (module != PlcModule.EMPTY) {
                drops.add(new ItemStack(item(module)));
            }
        }
        return drops;
    }

    // --- The program ---

    // A program in (from the editor, a cartridge or SNDPLCPGM): it stops first, then runs from the top if run.
    public void load(PlcProgram loaded, boolean run) {
        stop();
        program = loaded;
        declarations = null;
        fault = null;
        lastError = null;
        log.clear();
        setChanged();
        if (run) {
            start();
        }
        stateDirty = true;
    }

    // Saved in the editor: compiled for this PLC (on its network or not); the first error back ("ELC1502  ...  (line
    // 14)"), or null when it's in - and running again from the top if it was running.
    public @Nullable String save(List<String> source, ServerPlayer player) {
        Compiler.Result result = Compiler.compileTexts(source, FileResolver.NONE, target());
        if (!result.ok()) {
            var error = result.firstError();
            return error != null ? error.message() + "  (line " + (error.line() + 1) + ")" : ElclMessage.of("ELC0206", programName()).toString();
        }
        boolean wasRunning = running && fault == null;
        PlcProgram base = program != null ? program : new PlcProgram(DEFAULT_PROGRAM, List.of(), Map.of(), "", Optional.empty(), "", "", Map.of());
        PlcProgram saved = base.withSource(source, SystemData.saved(result.formats()), player.getName().getString(), player.getUUID(), authority(player, player.getUUID()),
                now());
        load(saved, wasRunning);
        return null;
    }

    // What the program's compiled for: a PLC on its network, or one on its own (ELC1502 for what needs a network).
    public Compiler.Target target() {
        return online ? Compiler.Target.PLC : Compiler.Target.PLC_LOCAL;
    }

    public String programName() {
        return program != null ? program.name() : DEFAULT_PROGRAM;
    }

    // The highest Firewall permission a player has here, as the status screen shows it (*ALL off a network).
    public String authority(@Nullable ServerPlayer player, @Nullable UUID id) {
        NetworkRef network = network();
        if (network == null || !(level instanceof ServerLevel serverLevel)) {
            return "*ALL";
        }
        RackPermission[] order = { RackPermission.BUILD, RackPermission.CRAFT, RackPermission.EXTRACT, RackPermission.INSERT, RackPermission.VIEW };
        String[] shown = { "*CONFIGURE", "*CRAFT", "*EXTRACT", "*INSERT", "*VIEW" };
        for (int i = 0; i < order.length; i++) {
            if (Authority.allowed(serverLevel.getServer(), network, player, player != null ? player.getUUID() : id, order[i])) {
                return shown[i];
            }
        }
        return "*NONE";
    }

    private String now() {
        if (level instanceof ServerLevel serverLevel) {
            NetworkRef network = network();
            return network != null ? new ElclSystem(serverLevel.getServer(), network).nowShort()
                    : ElclSystem.clock(serverLevel.getOverworldClockTime(), false);
        }
        return "";
    }

    // RUN (F6, STRPLC): a fault cleared, the program from the top, its retained variables kept.
    public void start() {
        if (fault != null) {
            lastError = fault;
        }
        fault = null;
        note = "";
        running = true;
        restart();
        setChanged();
        stateDirty = true;
    }

    // STOP (F7, ENDPLC): the program halted, its retained variables kept, its outputs cleared.
    public void stop() {
        keepRetained();
        running = false;
        halt();
        note = "";
        clearOutputs();
        setChanged();
        stateDirty = true;
    }

    // From the top: the next tick starts a scan with the retained variables only.
    private void restart() {
        halt();
        clearOutputs();
    }

    private void halt() {
        vm = null;
        carry = null;
        RUNNING.remove(this);
    }

    // An unmonitored escape ended the program: FAULT.
    void faulted(ElclMessage message) {
        int line = vm != null ? vm.failureLine() : -1;
        if (vm != null) {
            keepRetained(vm.variables());
        }
        fault = new Fault(message.id(), message.text(), line);
        log(message);
        halt();
        if (Config.PLC_FAULT_OUTPUTS.get() == Config.PlcFaultOutputs.ZERO) {
            clearOutputs();
        }
        setChanged();
        stateDirty = true;
    }

    void log(ElclMessage message) {
        log.addLast(message.id() + "  " + message.text());
        while (log.size() > LOG_SIZE) {
            log.removeFirst();
        }
    }

    private PlcContext context(ServerLevel level) {
        PlcProgram p = program;
        String user = p != null && !p.loadedBy().isEmpty() ? p.loadedBy() : "QPLC";
        return new PlcContext(this, level.getServer(), network(), user, p != null ? p.loaderId().orElse(null) : null);
    }

    // The program's declarations as compiled for a PLC (the retained variables among them).
    public Map<String, VarDecl> declarations() {
        if (declarations == null) {
            declarations = program == null ? Map.of()
                    : Compiler.compileTexts(program.source(), FileResolver.of(SystemData.formats(program.files())), Compiler.Target.PLC).variables();
        }
        return declarations;
    }

    // The retained variables' values as the next STOP -> RUN starts with them.
    private Map<String, Object> retainedValues() {
        Map<String, Object> values = new HashMap<>();
        if (program == null) {
            return values;
        }
        for (VarDecl decl : declarations().values()) {
            List<String> kept = program.retained().get(decl.name());
            if (decl.retain() && kept != null) {
                Object value = PlcProgram.decode(decl, kept);
                if (value != null) {
                    values.put(decl.name(), value);
                }
            }
        }
        return values;
    }

    private void keepRetained() {
        if (vm != null) {
            keepRetained(vm.variables());
        } else if (carry != null) {
            keepRetained(carry);
        }
    }

    // The retained variables' values into the program (so they go with it).
    private void keepRetained(Map<String, Object> vars) {
        if (program == null) {
            return;
        }
        Map<String, List<String>> kept = new LinkedHashMap<>();
        for (VarDecl decl : declarations().values()) {
            Object value = vars.get(decl.name());
            if (decl.retain() && value != null) {
                kept.put(decl.name(), PlcProgram.encode(value));
            }
        }
        if (!kept.equals(program.retained())) {
            program = program.withRetained(kept);
            setChanged();
        }
    }

    // --- Ticking ---

    public static void serverTick(Level level, BlockPos pos, BlockState state, PlcBlockEntity plc) {
        plc.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        energy.refreshLimits();
        boolean nowPowered = online || energy.use(Config.PLC_ENERGY.getAsInt());
        if (nowPowered != powered) {
            powered = nowPowered;
            inputsDirty = true;
            stateDirty = true;
            if (!powered) {
                keepRetained();
                halt();
            } else if (running && fault == null) {
                restart();
            }
            notifyNeighbours();
        }
        if (inputsDirty) {
            readInputs();
        }
        if (powered && running && fault == null && program != null) {
            scan(level);
        }
        updateActivity(level);
        if (stateDirty) {
            stateDirty = false;
            updateBlockState();
        }
    }

    // One tick of the scan: up to the budget's instructions; a pass that ends lets the next start next tick.
    private void scan(ServerLevel level) {
        if (!RUNNING.contains(this)) {
            if (!admitted(level)) {
                if (note.isEmpty()) {
                    note = "PLC limit reached";
                    stateDirty = true;
                }
                return;
            }
            RUNNING.add(this);
            if (!note.isEmpty()) {
                note = "";
                stateDirty = true;
            }
        }
        long now = level.getGameTime();
        if (vm == null) {
            PlcProgram p = program;
            try {
                vm = Vm.start(new PlcVmHost(this, context(level)), new VmHost.Loaded("*PLC/" + p.name(), p.source(), p.files()),
                        carry != null ? carry : retainedValues());
            } catch (ElclException e) {
                faulted(e.elclMessage());
                return;
            }
            scanStart = now;
            scanInstructions = 0;
        }
        Vm running = vm;
        scanInstructions += running.run(Config.PLC_INSTRUCTIONS_PER_TICK.getAsInt());
        if (vm == running && running.state() == Vm.State.ENDED && running.failure() == null) {
            lastScanTicks = (int) (now - scanStart + 1);
            lastScanInstructions = scanInstructions;
            carry = running.variables();
            keepRetained(carry);
            vm = null;
        }
    }

    // Room under plcMaxPerChunk and plcMaxPerServer.
    private boolean admitted(ServerLevel level) {
        RUNNING.removeIf(plc -> plc.isRemoved() || plc.getLevel() == null);
        if (RUNNING.size() >= Config.PLC_MAX_PER_SERVER.getAsInt()) {
            return false;
        }
        ChunkPos chunk = ChunkPos.containing(worldPosition);
        long here = RUNNING.stream().filter(plc -> plc.getLevel() == level && ChunkPos.containing(plc.getBlockPos()).equals(chunk)).count();
        return here < Config.PLC_MAX_PER_CHUNK.getAsInt();
    }

    // The I/O LEDs: a face whose level changed in the last ACTIVITY_TICKS, while it runs.
    private void updateActivity(ServerLevel level) {
        int bits = 0;
        if (mode() == PlcBlock.Mode.RUN) {
            long now = level.getGameTime();
            for (Direction face : FACES) {
                if (changedAt[face.ordinal()] > 0 && now - changedAt[face.ordinal()] < ACTIVITY_TICKS) {
                    bits |= 1 << face.ordinal();
                }
            }
        }
        if (bits != ioBits) {
            ioBits = bits;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public int ioBits() {
        return ioBits;
    }

    private void updateBlockState() {
        if (level == null || !(getBlockState().getBlock() instanceof PlcBlock)) {
            return;
        }
        BlockState state = getBlockState();
        BlockState next = state.setValue(PlcBlock.STATE, mode());
        for (int slot = 0; slot < modules.length; slot++) {
            next = next.setValue(PlcBlock.SLOTS.get(slot), modules[slot]);
        }
        if (next != state) {
            level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        RUNNING.remove(this);
    }

    // Breaking it drops its modules (the program goes with its item: the loot table).
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            for (ItemStack stack : drops()) {
                Block.popResource(level, pos, stack);
            }
        }
    }

    // --- The client's copy: only the I/O LEDs ---

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("io", ioBits);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        ioBits = input.getIntOr("io", 0);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        ioBits = input.getIntOr("io", 0);
    }

    // --- Components (the program and the device name go with the item) ---

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        PlcProgram carried = components.get(ModDataComponents.PLC_PROGRAM.get());
        if (carried != null) {
            program = carried;
            declarations = null;
        }
        String carriedName = components.get(ModDataComponents.DEVICE_NAME.get());
        if (carriedName != null) {
            name = carriedName;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        keepRetained();
        if (program != null) {
            components.set(ModDataComponents.PLC_PROGRAM.get(), program);
        }
        if (!name.isEmpty()) {
            components.set(ModDataComponents.DEVICE_NAME.get(), name);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("program");
        output.discard("name");
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        name = input.getStringOr("name", "");
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        program = input.read("program", PlcProgram.CODEC).orElse(null);
        declarations = null;
        running = input.getBooleanOr("running", false);
        fault = input.read("fault", Fault.CODEC).orElse(null);
        lastError = input.read("last_error", Fault.CODEC).orElse(null);
        List<String> installed = input.read("modules", Codec.STRING.listOf()).orElse(List.of());
        List<PlcSensors.Setting> set = input.read("settings", PlcSensors.Setting.CODEC.listOf()).orElse(List.of());
        for (int slot = 0; slot < modules.length; slot++) {
            modules[slot] = slot < installed.size() ? PlcModule.byName(installed.get(slot)) : PlcModule.EMPTY;
            settings[slot] = slot < set.size() ? set.get(slot) : PlcSensors.Setting.DEFAULT;
        }
        channels.load(input);
        energy.deserialize(input);
        // A reload is a power-up: the program from the top.
        vm = null;
        carry = null;
        powered = false;
        inputsDirty = true;
        stateDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        keepRetained();
        if (!name.isEmpty()) {
            output.putString("name", name);
        }
        if (owner != null) {
            output.store("owner", UUIDUtil.CODEC, owner);
        }
        if (program != null) {
            output.store("program", PlcProgram.CODEC, program);
        }
        output.putBoolean("running", running);
        if (fault != null) {
            output.store("fault", Fault.CODEC, fault);
        }
        if (lastError != null) {
            output.store("last_error", Fault.CODEC, lastError);
        }
        output.store("modules", Codec.STRING.listOf(), Arrays.stream(modules).map(PlcModule::getSerializedName).toList());
        output.store("settings", PlcSensors.Setting.CODEC.listOf(), List.of(settings));
        channels.save(output);
        energy.serialize(output);
    }

    // Its own FE buffer: any face fills it; it's used a tick at a time.
    private final class Buffer extends SimpleEnergyHandler {
        Buffer() {
            super(Config.PLC_BUFFER.getDefault(), Config.PLC_BUFFER.getDefault(), 0);
        }

        // Limits come from the config, which may not be loaded when the block entity is created.
        void refreshLimits() {
            if (Config.SPEC.isLoaded()) {
                capacity = Config.PLC_BUFFER.getAsInt();
                maxInsert = capacity;
            }
        }

        // A tick's energy, if it's there.
        boolean use(int amount) {
            if (amount <= 0) {
                return true;
            }
            if (getAmountAsInt() < amount) {
                return false;
            }
            set(getAmountAsInt() - amount);
            return true;
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    }
}
