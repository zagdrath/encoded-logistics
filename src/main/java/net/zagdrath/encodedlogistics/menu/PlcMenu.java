/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.compile.VarDecl;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.midrange.Midranges;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.plc.PlcBlockEntity;
import net.zagdrath.encodedlogistics.plc.PlcModule;
import net.zagdrath.encodedlogistics.plc.PlcProgram;
import net.zagdrath.encodedlogistics.plc.PlcSensors;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.terminal.TerminalCommands;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalOutput;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// A PLC's green screens (docs/plc HANDOFF 5; layouts plcsts, plcio, plcmod), one menu with a view at a time:
//   STATUS    PLCSTS: the PLC, its network, program, size, mode, scan time, instructions per tick, who loaded it, the
//             last error; the menu on the command line (1 edit, 2 I/O table, 3 modules, 4 / 5 save to / load from the
//             EEPROM Cartridge held, 6 retained variables) - anything else runs as an ELCL command on its network.
//   IO        PLCIO: each face's input and output and what drives it (the CHGRSOUT line, or the block next to it).
//   MODULES   PLCMOD: each slot's module, setting, value and status; 2=Change setting, 4=Remove module.
//   SETTING   a module's settings (PLCMOD 2): radius and what it counts, or its face.
//   RETAINED  the program's retained variables and the PLC's own log (its SNDPGMMSG messages and escapes).
// Lines, tab-separated: V view slot; S mode program size saved scan ipt budget loadedBy authority network errorId
// errorLine errorText note energy; F face in out drivenBy (IO); M slot module setting value status (MODULES); E slot module
// radius count face (SETTING); R name type value and L text (RETAINED); X program - open the editor on it (option 1).
// Buttons: F6 RUN, F7 STOP, F9 clear the fault (and run), F10 I/O, F11 modules, F12 back to the status.
// It also answers the source editor's requests (CrtHost) for the PLC's program: source, save (compiled for this PLC on
// the server: ELC0213 when it's in, else the first error and its line), and, on a network, the rest as a desk would.
public class PlcMenu extends PeripheralMenu implements CrtHost {
    public static final int BUTTON_RUN = 6, BUTTON_STOP = 7, BUTTON_CLEAR = 9, BUTTON_IO = 10, BUTTON_MODULES = 11, BUTTON_BACK = 12;
    public static final int FIELD_RADIUS = 1, FIELD_COUNT = 2, FIELD_FACE = 3;
    public static final String LIBRARY = "*PLC";
    private static final Direction[] IO_ORDER = { Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };

    public enum View {
        STATUS, IO, MODULES, SETTING, RETAINED
    }

    private final BlockPos pos;
    private final @Nullable PlcBlockEntity plc;
    private View view = View.STATUS;
    private int editing = -1;
    private boolean openEditor;
    private final List<SourceLine> saving = new ArrayList<>();
    // On the client: the answer (received()) whose "open the editor" the screen has acted on.
    public int editorOpenedAt = -1;

    // Client constructor.
    public PlcMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), null, Opening.read(buf));
    }

    public PlcMenu(int containerId, Inventory inventory, PlcBlockEntity plc) {
        this(containerId, inventory, plc.getBlockPos(), plc, Opening.SERVER);
    }

    private PlcMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable PlcBlockEntity plc, Opening opening) {
        super(ModMenuTypes.PLC.get(), containerId, inventory, new SimpleContainer(0), plc, opening);
        this.pos = pos;
        this.plc = plc;
    }

    public static void open(ServerPlayer player, PlcBlockEntity plc) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new PlcMenu(id, inventory, plc), Component.translatable("block.encodedlogistics.plc")),
                buf -> {
                    buf.writeBlockPos(plc.getBlockPos());
                    Midranges.writeOpening(plc, plc, buf);
                });
    }

    // Who may use it: on a network, the Firewall's build permission; without one, anyone - or, with plcOpenToAnyone
    // off, whoever placed it (and operators).
    public static boolean mayOpen(ServerLevel level, PlcBlockEntity plc, Player player) {
        if (ControllerStructures.networkOf(level, plc.getBlockPos()) != null) {
            return NetworkAccess.check(level, plc.getBlockPos(), player, RackPermission.BUILD);
        }
        if (Config.PLC_OPEN_TO_ANYONE.getAsBoolean() || plc.owner() == null || plc.owner().equals(player.getUUID())
                || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.encodedlogistics.plc.not_owner"));
        }
        return false;
    }

    private boolean allowed() {
        return plc != null && plc.getLevel() instanceof ServerLevel level && mayOpen(level, plc, player);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof PlcBlockEntity && player.isWithinBlockInteractionRange(pos, 4.0);
    }

    // The I/O table is live: every 10 ticks.
    @Override
    protected int refreshTicks() {
        return 10;
    }

    // --- What the screen shows ---

    @Override
    protected void refresh() {
        if (plc == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add("V\t" + view.name() + "\t" + editing);
        lines.add(status(plc));
        switch (view) {
            case IO -> {
                for (Direction face : IO_ORDER) {
                    lines.add(String.join("\t", "F", PlcSensors.side(face), Integer.toString(plc.input(face)), Integer.toString(plc.output(face)), drivenBy(face)));
                }
            }
            case MODULES -> {
                for (int slot = 0; slot < 4; slot++) {
                    PlcModule module = plc.module(slot);
                    PlcSensors.Reading reading = plc.read(slot);
                    lines.add(String.join("\t", "M", Integer.toString(slot + 1), module.descriptionId(),
                            module == PlcModule.EMPTY ? "" : plc.setting(slot).shown(module, plc.facing()), value(module, reading),
                            module == PlcModule.EMPTY ? "" : reading.status()));
                }
            }
            case SETTING -> {
                PlcModule module = editing >= 0 ? plc.module(editing) : PlcModule.EMPTY;
                if (!module.hasSetting()) {
                    view = View.MODULES;
                    refresh();
                    return;
                }
                PlcSensors.Setting setting = plc.setting(editing);
                lines.add(String.join("\t", "E", Integer.toString(editing + 1), module.descriptionId(), Integer.toString(setting.radius()), setting.count(),
                        PlcSensors.side(setting.faceOf(plc.facing())), module.getSerializedName()));
            }
            case RETAINED -> {
                PlcProgram program = plc.program();
                if (program != null) {
                    for (VarDecl decl : plc.declarations().values()) {
                        if (decl.retain()) {
                            List<String> kept = program.retained().get(decl.name());
                            lines.add(String.join("\t", "R", decl.name(), decl.type().special(),
                                    kept == null ? "" : PlcProgram.shown(decl.type(), kept).replace('\t', ' ')));
                        }
                    }
                }
                for (String entry : plc.log()) {
                    lines.add("L\t" + entry.replace('\t', ' '));
                }
            }
            default -> {}
        }
        if (openEditor) {
            openEditor = false;
            lines.add("X\t" + plc.programName());
        }
        send(Component.empty(), lines.size() > 64 ? lines.subList(0, 64) : lines, List.of());
    }

    // S: the status line.
    private String status(PlcBlockEntity plc) {
        PlcProgram program = plc.program();
        PlcBlockEntity.Fault error = plc.lastError();
        String network = "";
        if (plc.getLevel() instanceof ServerLevel level) {
            NetworkRef ref = ControllerStructures.networkOf(level, plc.getBlockPos());
            if (ref != null) {
                network = new ElclSystem(level.getServer(), ref).name();
            }
        }
        return String.join("\t", "S", plc.mode().getSerializedName(), program != null ? program.name() : "", program != null ? Integer.toString(program.size()) : "",
                program != null ? program.saved() : "", Integer.toString(plc.scanTicks()), Integer.toString(plc.instructionsPerTick()),
                Integer.toString(Config.PLC_INSTRUCTIONS_PER_TICK.getAsInt()), program != null ? program.loadedBy() : "", program != null ? program.authority() : "",
                network, error != null ? error.id() : "", error != null ? Integer.toString(error.line()) : "", error != null ? error.text().replace('\t', ' ') : "",
                plc.note(), Integer.toString(plc.energyStored()));
    }

    // What drives a face: its output's CHGRSOUT line, else (with a level coming in) the block next to it - as a lang key, #.
    private String drivenBy(Direction face) {
        if (plc == null) {
            return "";
        }
        if (plc.output(face) > 0) {
            int line = plc.drivenLine(face);
            return line > 0 ? String.format(Locale.ROOT, "CHGRSOUT line %04d", line) : "CHGRSOUT";
        }
        if (plc.input(face) > 0 && plc.getLevel() != null) {
            return "#" + plc.getLevel().getBlockState(plc.getBlockPos().relative(face)).getBlock().getDescriptionId();
        }
        return "";
    }

    // A module's value as PLCMOD shows it: "2", "64% (1,284)", "11, day", "Day 2 14:32".
    private static String value(PlcModule module, PlcSensors.Reading reading) {
        if (module == PlcModule.EMPTY || !reading.ok()) {
            return "";
        }
        long value = reading.value().longValue();
        return switch (module) {
            case INVENTORY_SENSOR -> String.format(Locale.ROOT, "%d%% (%,d)", value, Long.parseLong(reading.aux()));
            case FLUID_SENSOR -> String.format(Locale.ROOT, "%d%% (%,d mB)", value, Long.parseLong(reading.aux()));
            case LIGHT_SENSOR -> value + ", " + reading.aux().substring(1).toLowerCase(Locale.ROOT);
            case TIMER_MODULE -> reading.aux();
            default -> Long.toString(value);
        };
    }

    // --- The command line: the menu's options, else a command ---

    @Override
    protected Component command(String line) {
        if (plc == null || !(player instanceof ServerPlayer serverPlayer) || !(plc.getLevel() instanceof ServerLevel level)) {
            return Component.empty();
        }
        String text = line.trim();
        if (view == View.STATUS && text.matches("[1-6]")) {
            return option(serverPlayer, Integer.parseInt(text));
        }
        NetworkRef network = ControllerStructures.networkOf(level, plc.getBlockPos());
        if (network == null) {
            return Component.translatable("crt.encodedlogistics.plcsts.no_network");
        }
        TerminalOutput output = TerminalCommands.execute(new TerminalContext(level.getServer(), network, null, serverPlayer), text);
        if (output.message() != null) {
            return output.message();
        }
        return output.lines().isEmpty() ? Component.translatable("crt.encodedlogistics.machine.command_done", text.split("\\s", 2)[0].toUpperCase(Locale.ROOT))
                : Component.literal(output.lines().getFirst().text());
    }

    // PLCSTS's menu.
    private Component option(ServerPlayer player, int option) {
        if (plc == null || !allowed()) {
            return Component.empty();
        }
        return switch (option) {
            case 1 -> {
                openEditor = true;
                yield Component.empty();
            }
            case 2 -> {
                view = View.IO;
                yield Component.empty();
            }
            case 3 -> {
                view = View.MODULES;
                yield Component.empty();
            }
            case 4 -> saveToCartridge(player);
            case 5 -> loadFromCartridge(player);
            default -> {
                view = View.RETAINED;
                yield Component.empty();
            }
        };
    }

    // The EEPROM Cartridge in the player's hand (main, else off), or null.
    private static @Nullable InteractionHand cartridge(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).is(ModItems.EEPROM_CARTRIDGE.get())) {
                return hand;
            }
        }
        return null;
    }

    // 4: the program (and its retained variables) onto the cartridge held - one of a stack.
    private Component saveToCartridge(ServerPlayer player) {
        PlcProgram program = plc.program();
        if (program == null) {
            return Component.literal(ElclMessage.of("ELC1505", name()).toString());
        }
        InteractionHand hand = cartridge(player);
        if (hand == null) {
            return Component.translatable("crt.encodedlogistics.plcsts.no_cartridge");
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getCount() > 1) {
            ItemStack one = held.split(1);
            one.set(ModDataComponents.PLC_PROGRAM.get(), program);
            give(one);
        } else {
            held.set(ModDataComponents.PLC_PROGRAM.get(), program);
        }
        return Component.translatable("crt.encodedlogistics.plcsts.saved", program.name());
    }

    // 5: the cartridge's program into the PLC (it stops; F6 runs it), loaded by this player.
    private Component loadFromCartridge(ServerPlayer player) {
        InteractionHand hand = cartridge(player);
        if (hand == null) {
            return Component.translatable("crt.encodedlogistics.plcsts.no_cartridge");
        }
        PlcProgram program = player.getItemInHand(hand).get(ModDataComponents.PLC_PROGRAM.get());
        if (program == null) {
            return Component.translatable("crt.encodedlogistics.plcsts.blank");
        }
        plc.load(program.loadedBy(player.getName().getString().toUpperCase(Locale.ROOT), player.getUUID(), plc.authority(player, player.getUUID())), false);
        return Component.translatable("crt.encodedlogistics.plcsts.loaded", program.name());
    }

    private String name() {
        return plc == null || plc.deviceName().isEmpty() ? PlcBlockEntity.TYPE : plc.deviceName();
    }

    // --- PLCMOD's options and the setting's fields ---

    @Override
    protected @Nullable Component option(int row, String option) {
        if (plc == null || view != View.MODULES || row < 0 || row > 3 || !allowed()) {
            return null;
        }
        PlcModule module = plc.module(row);
        return switch (option) {
            case "2" -> {
                if (module == PlcModule.EMPTY) {
                    yield Component.translatable("crt.encodedlogistics.plcmod.empty", row + 1);
                }
                if (!module.hasSetting()) {
                    yield Component.translatable("crt.encodedlogistics.plcmod.no_setting", Component.translatable(module.descriptionId()));
                }
                view = View.SETTING;
                editing = row;
                yield Component.empty();
            }
            case "4" -> {
                ItemStack out = plc.remove(row);
                if (out.isEmpty()) {
                    yield Component.translatable("crt.encodedlogistics.plcmod.empty", row + 1);
                }
                give(out);
                yield Component.translatable("crt.encodedlogistics.plcmod.removed", out.getHoverName(), row + 1);
            }
            default -> null;
        };
    }

    @Override
    protected void field(int key, String text) {
        if (plc == null || view != View.SETTING || editing < 0 || !allowed()) {
            return;
        }
        PlcSensors.Setting setting = plc.setting(editing);
        String value = text.trim().toUpperCase(Locale.ROOT);
        switch (key) {
            case FIELD_RADIUS -> {
                int radius;
                try {
                    radius = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    radius = -1;
                }
                if (radius < 1 || radius > 16) {
                    send(Component.literal(ElclMessage.of("ELC0004", value).toString()));
                    return;
                }
                plc.setSetting(editing, new PlcSensors.Setting(radius, setting.count(), setting.face()));
            }
            case FIELD_COUNT -> {
                if (!PlcSensors.COUNTS.contains(value)) {
                    send(Component.literal(ElclMessage.of("ELC0103", value, "COUNT").toString()));
                    return;
                }
                plc.setSetting(editing, new PlcSensors.Setting(setting.radius(), value, setting.face()));
            }
            case FIELD_FACE -> {
                Direction face = value.startsWith("*") ? Direction.byName(value.substring(1).toLowerCase(Locale.ROOT)) : null;
                if (face == null) {
                    send(Component.literal(ElclMessage.of("ELC0103", value, "FACE").toString()));
                    return;
                }
                plc.setSetting(editing, new PlcSensors.Setting(setting.radius(), setting.count(), Optional.of(face)));
            }
            default -> {
                return;
            }
        }
        send(Component.translatable("crt.encodedlogistics.plcmod.changed", editing + 1));
    }

    // --- Function keys ---

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (plc == null) {
            return false;
        }
        Component said = Component.empty();
        switch (id) {
            case BUTTON_RUN, BUTTON_STOP, BUTTON_CLEAR -> {
                if (!allowed()) {
                    return true;
                }
                if (id == BUTTON_STOP) {
                    plc.stop();
                    said = Component.literal(ElclMessage.of("ELC1508", name()).toString());
                } else if (id == BUTTON_CLEAR && plc.fault() == null) {
                    said = Component.translatable("crt.encodedlogistics.plcsts.no_fault");
                } else if (plc.program() == null) {
                    said = Component.literal(ElclMessage.of("ELC1505", name()).toString());
                } else {
                    plc.start();
                    said = Component.literal(ElclMessage.of("ELC1507", name()).toString());
                }
            }
            case BUTTON_IO -> view = View.IO;
            case BUTTON_MODULES -> view = View.MODULES;
            case BUTTON_BACK -> {
                view = view == View.SETTING ? View.MODULES : View.STATUS;
                editing = -1;
            }
            default -> {
                return false;
            }
        }
        refresh();
        if (!said.getString().isEmpty()) {
            send(said);
        }
        return true;
    }

    // --- The source editor's requests (option 1) ---

    @Override
    public void handle(ServerPlayer player, int kind, String text) {
        if (plc == null || !(plc.getLevel() instanceof ServerLevel level) || !stillValid(player)) {
            return;
        }
        NetworkRef network = ControllerStructures.networkOf(level, plc.getBlockPos());
        TerminalOutput output;
        if (kind == TerminalService.SCREEN && editorQuery(text)) {
            output = editor(player, text);
        } else if (network != null) {
            output = TerminalService.handle(new TerminalContext(level.getServer(), network, null, player), kind, text);
        } else if (kind == TerminalService.COMMAND) {
            output = TerminalOutput.message(Component.translatable("crt.encodedlogistics.plcsts.no_network"));
        } else {
            output = new TerminalOutput();
        }
        if (player.connection.hasChannel(CrtResponsePayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new CrtResponsePayload(containerId, kind, TerminalService.topic(kind, text), output.lines(),
                    Optional.ofNullable(output.message()), -1));
        }
    }

    private static boolean editorQuery(String text) {
        String first = text.trim().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        return switch (first) {
            case "lock", "unlock", "source", "savebegin", "savepart", "savecommit" -> true;
            default -> false;
        };
    }

    // The PLC's program as a member of *PLC: its source (numbered as it's sent), and saving it back.
    TerminalOutput editor(ServerPlayer player, String text) {
        List<String> words = TerminalCommands.words(text);
        String verb = words.getFirst().toLowerCase(Locale.ROOT);
        TerminalOutput out = new TerminalOutput();
        switch (verb) {
            case "source" -> {
                int from = words.size() > 3 ? Math.max(0, parse(words.get(3))) : 0;
                PlcProgram program = plc.program();
                List<SourceLine> lines = SourceLine.number(program != null ? program.source() : List.of(), 0);
                boolean readOnly = !allowed();
                out.line(row(lines.size(), from, readOnly ? "1" : "0", "ELCLP"));
                for (int i = from; i < Math.min(lines.size(), from + 1_000); i++) {
                    out.line(row(lines.get(i).seq(), lines.get(i).date(), lines.get(i).text()));
                }
            }
            case "savebegin" -> saving.clear();
            case "savepart" -> {
                for (String line : text.substring(text.indexOf(' ') + 1).split("\n", -1)) {
                    String[] parts = line.split("\t", 3);
                    if (parts.length == 3) {
                        try {
                            saving.add(new SourceLine(Integer.parseInt(parts[0]), parts[2], Integer.parseInt(parts[1])));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            case "savecommit" -> {
                if (!allowed()) {
                    return TerminalOutput.message(Component.translatable("message.encodedlogistics.plc.not_owner"));
                }
                String error = plc.save(SourceLine.texts(saving), player);
                saving.clear();
                String name = plc.programName();
                out.setMessage(Component.literal(error != null ? error : ElclMessage.of("ELC0213", name, LIBRARY).toString()));
            }
            default -> {}
        }
        return out;
    }

    private static int parse(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static TerminalLine row(Object... cells) {
        TerminalLine.Builder builder = TerminalLine.builder();
        for (Object cell : cells) {
            builder.left(String.valueOf(cell), 0);
        }
        return builder.build();
    }
}
