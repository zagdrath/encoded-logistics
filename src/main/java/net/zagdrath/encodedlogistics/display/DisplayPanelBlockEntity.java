/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.display;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.client.display.DisplayCanvases;
import net.zagdrath.encodedlogistics.elcl.device.DisplayDevice;
import net.zagdrath.encodedlogistics.elcl.exec.NamedDevice;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.network.NetworkStatus;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// A Display Panel. Every panel knows its screen's master (DisplayScreens); the master - the screen's bottom-left block -
// is the device: its size in panels, its content (the lines SNDDSPTXT writes), its device name (DSP01) and its state:
// no signal off a running network, off while it has no lane, booting (the test pattern, BOOT_TICKS) when it comes on,
// then online. The canvas is CANVAS px a panel each way (DisplayCanvases draws it on the client).
public class DisplayPanelBlockEntity extends BlockEntity implements NetworkDevice, NamedDevice, DisplayDevice {
    public static final String TYPE = "DSP";
    public static final int CANVAS = 32, CHAR_W = 6, CHAR_H = 10, BOOT_TICKS = 40;

    // What a screen shows, carried to the screen a re-merge makes of it.
    public record Content(int width, int height, List<String> lines, int next) {
        public Content {
            lines = List.copyOf(lines);
        }
    }

    // The screen's master (null: this one, before it's merged).
    private @Nullable BlockPos master;
    private int width = 1, height = 1;
    private final List<String> lines = new ArrayList<>();
    private int next;
    private String deviceName = "";
    private boolean online;
    private int boot;

    public DisplayPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.DISPLAY_PANEL.get(), pos, state);
    }

    // --- The screen ---

    public boolean isMaster() {
        return master == null || master.equals(worldPosition);
    }

    public BlockPos masterPos() {
        return master != null ? master : worldPosition;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int canvasWidth() {
        return width * CANVAS;
    }

    public int canvasHeight() {
        return height * CANVAS;
    }

    public List<String> textLines() {
        return lines;
    }

    public Content content() {
        return new Content(width, height, lines, next);
    }

    void joinScreen(BlockPos master) {
        if (!master.equals(this.master)) {
            this.master = master.immutable();
            if (!isMaster()) {
                width = 1;
                height = 1;
                lines.clear();
            }
            sync();
        }
    }

    // This block is a screen's master now, with the content of the screen that was there (null: keep its own).
    void becomeMaster(int width, int height, @Nullable Content content) {
        this.width = width;
        this.height = height;
        if (content != null) {
            lines.clear();
            lines.addAll(content.lines());
            next = content.next();
        }
        // A smaller screen keeps what fits.
        while (lines.size() > lines()) {
            lines.removeLast();
        }
        next = Math.min(next, lines());
        sync();
    }

    // The screen's blocks, from its master.
    public List<BlockPos> screenBlocks() {
        Direction facing = getBlockState().getValue(DisplayPanelBlock.FACING);
        List<BlockPos> blocks = new ArrayList<>();
        for (int u = 0; u < width; u++) {
            for (int v = 0; v < height; v++) {
                blocks.add(DisplayScreens.at(worldPosition, facing, u, v));
            }
        }
        return blocks;
    }

    // --- State ---

    @Override
    public void setNetworkOnline(boolean online) {
        if (online && !this.online) {
            boot = BOOT_TICKS;
        }
        this.online = online;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DisplayPanelBlockEntity panel) {
        if (level instanceof ServerLevel serverLevel && panel.isMaster()) {
            panel.tick(serverLevel);
        }
    }

    private void tick(ServerLevel level) {
        if (boot > 0) {
            boot--;
        }
        show(level, shownState(level));
    }

    private DisplayPanelBlock.Shown shownState(ServerLevel level) {
        if (online) {
            return boot > 0 ? DisplayPanelBlock.Shown.BOOT : DisplayPanelBlock.Shown.ONLINE;
        }
        NetworkRef network = ControllerStructures.networkOf(level, worldPosition);
        NetworkStatus status = network != null ? ControllerStructures.statusOf(level.getServer(), network) : null;
        boolean running = status == NetworkStatus.ONLINE || status == NetworkStatus.FAILOVER;
        // On a running network without its lane: off; no network, or its controller down: no signal.
        return running ? DisplayPanelBlock.Shown.OFF : DisplayPanelBlock.Shown.NO_SIGNAL;
    }

    // The state on every block of the screen (only the LED's block shows it; the canvas follows the master's).
    private void show(ServerLevel level, DisplayPanelBlock.Shown shown) {
        if (getBlockState().getValue(DisplayPanelBlock.STATE) == shown) {
            return;
        }
        for (BlockPos pos : screenBlocks()) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DisplayPanelBlock && state.getValue(DisplayPanelBlock.STATE) != shown) {
                level.setBlock(pos, state.setValue(DisplayPanelBlock.STATE, shown), Block.UPDATE_CLIENTS);
            }
        }
    }

    public DisplayPanelBlock.Shown shown() {
        return getBlockState().getValue(DisplayPanelBlock.STATE);
    }

    // --- DisplayDevice (SNDDSPTXT) ---

    @Override
    public String name() {
        return deviceName.isEmpty() ? TYPE + "01" : deviceName;
    }

    @Override
    public boolean online() {
        return online;
    }

    // Lines of text at 1x: the canvas height over the font's 10 px.
    @Override
    public int lines() {
        return Math.max(1, canvasHeight() / CHAR_H);
    }

    // Characters a line at 1x.
    public int columns() {
        return Math.max(1, canvasWidth() / CHAR_W);
    }

    // Line n (1-based), or the line after the last one written (0; past the bottom the lines roll up).
    @Override
    public void write(int line, String text, boolean clear) {
        if (clear) {
            lines.clear();
            next = 0;
        }
        int at = line > 0 ? line - 1 : next;
        if (at >= lines()) {
            lines.removeFirst();
            at = lines() - 1;
        }
        while (lines.size() <= at) {
            lines.add("");
        }
        lines.set(at, text.length() > columns() ? text.substring(0, columns()) : text);
        next = at + 1;
        sync();
    }

    // --- Name ---

    @Override
    public String deviceType() {
        return TYPE;
    }

    @Override
    public String deviceName() {
        return deviceName;
    }

    @Override
    public void setDeviceName(String name) {
        if (!deviceName.equals(name)) {
            deviceName = name;
            setChanged();
        }
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    // --- Syncing and saving ---

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        master = input.read("master", BlockPos.CODEC).orElse(null);
        width = Math.max(1, input.getIntOr("width", 1));
        height = Math.max(1, input.getIntOr("height", 1));
        lines.clear();
        lines.addAll(input.read("lines", Codec.STRING.listOf()).orElse(List.of()));
        next = input.getIntOr("next", 0);
        deviceName = input.getStringOr("device_name", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (master != null) {
            output.store("master", BlockPos.CODEC, master);
        }
        output.putInt("width", width);
        output.putInt("height", height);
        if (!lines.isEmpty()) {
            output.store("lines", Codec.STRING.listOf(), lines);
        }
        output.putInt("next", next);
        if (!deviceName.isEmpty()) {
            output.putString("device_name", deviceName);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        String carried = components.get(ModDataComponents.DEVICE_NAME.get());
        if (carried != null) {
            deviceName = carried;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!deviceName.isEmpty()) {
            components.set(ModDataComponents.DEVICE_NAME.get(), deviceName);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("device_name");
    }

    // Its canvas texture goes with it (client).
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide()) {
            DisplayCanvases.release(worldPosition);
        }
    }
}
