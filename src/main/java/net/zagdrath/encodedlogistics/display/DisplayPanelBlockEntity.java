/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.display;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.client.display.DisplayCanvases;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.device.DisplayDevice;
import net.zagdrath.encodedlogistics.elcl.exec.NamedDevice;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.net.DisplayFramePayload;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.network.NetworkStatus;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// A Display Panel. Every panel knows its screen's master (DisplayScreens); the master - the screen's bottom-left block -
// is the device: its size in panels, what it shows (DisplayContent), its device name (DSP01) and its state: no signal
// off a running network, off while it has no lane, booting (the test pattern, BOOT_TICKS) when it comes on, then online.
// Once a second it works out its live widgets (DisplayData) for the players near it and samples its graphs' history;
// its images are rendered off the server thread (DisplayImages) and go to clients with it. The canvas is CANVAS px a
// panel each way (DisplayCanvases draws it on the client).
public class DisplayPanelBlockEntity extends BlockEntity implements NetworkDevice, NamedDevice, DisplayDevice {
    public static final String TYPE = "DSP";
    public static final int CANVAS = 32, CHAR_W = 6, CHAR_H = 10, BOOT_TICKS = 40;
    private static final int REFRESH = 20;

    // What a screen shows, carried to the screen a re-merge makes of it.
    public record Content(int width, int height, DisplayContent content) {}

    // The screen's master (null: this one, before it's merged).
    private @Nullable BlockPos master;
    private int width = 1, height = 1;
    private DisplayContent content = new DisplayContent();
    private String deviceName = "";
    private boolean online;
    private int boot, timer;
    // Rendered images by region (synced, not saved: rendered again from their files after a load).
    private final Map<String, int[]> images = new HashMap<>();
    private final Set<String> pendingImages = new HashSet<>();
    private final DisplayHistory history = new DisplayHistory();
    private int framesHash;

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

    public DisplayContent displayContent() {
        return content;
    }

    public List<String> textLines() {
        return content.texts();
    }

    public Map<String, int[]> images() {
        return images;
    }

    public Content content() {
        return new Content(width, height, content.copy());
    }

    void joinScreen(BlockPos master) {
        if (!master.equals(this.master)) {
            this.master = master.immutable();
            if (!isMaster()) {
                width = 1;
                height = 1;
                content = new DisplayContent();
                images.clear();
            }
            sync();
        }
    }

    // This block is a screen's master now, with the content of the screen that was there (null: keep its own); a
    // changed size keeps the setup, clamped to it.
    void becomeMaster(int width, int height, @Nullable Content carried) {
        boolean resized = this.width != width || this.height != height;
        this.width = width;
        this.height = height;
        if (carried != null && carried.content() != content) {
            content = carried.content().copy();
            resized |= carried.width() != width || carried.height() != height;
        }
        content.clamp(canvasWidth(), canvasHeight(), lines());
        if (resized) {
            images.clear();
        }
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

    // A change to what it shows (from the commands or its screen): saved and sent to the players near it.
    public void changed() {
        content.clamp(canvasWidth(), canvasHeight(), lines());
        // Images for regions that no longer show one go.
        Set<String> showing = new HashSet<>();
        for (DisplayContent.Region region : content.regions(canvasWidth(), canvasHeight())) {
            if (region.widget().kind().equals("*IMAGE")) {
                showing.add(region.name().toUpperCase(Locale.ROOT));
            }
        }
        images.keySet().retainAll(showing);
        framesHash = 0;
        timer = REFRESH;
        sync();
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
        if (++timer >= REFRESH) {
            timer = 0;
            NetworkRef network = network();
            DisplayData.sample(level.getServer(), network, content, canvasWidth(), canvasHeight(), history);
            sendFrames(level, DisplayData.frames(level.getServer(), network, content, canvasWidth(), canvasHeight(), history));
            loadImages(level.getServer());
        }
    }

    // Its live widgets now (what the players near it are sent each second).
    public List<DisplayFrame> liveFrames() {
        return level instanceof ServerLevel serverLevel ? DisplayData.frames(serverLevel.getServer(), network(), content, canvasWidth(), canvasHeight(), history)
                : List.of();
    }

    public @Nullable NetworkRef network() {
        return level instanceof ServerLevel serverLevel ? ControllerStructures.networkOf(serverLevel, worldPosition) : null;
    }

    private void sendFrames(ServerLevel level, List<DisplayFrame> frames) {
        int hash = Objects.hash(frames);
        if (hash == framesHash) {
            return;
        }
        framesHash = hash;
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(worldPosition.getX() >> 4, worldPosition.getZ() >> 4), new DisplayFramePayload(worldPosition, frames));
    }

    // Images for regions showing one that hasn't been rendered (after a load, a resize, a new file).
    private void loadImages(MinecraftServer server) {
        NetworkRef network = network();
        if (network == null || !DisplayImages.allowed(server)) {
            return;
        }
        String system = new ElclSystem(server, network).name();
        for (DisplayContent.Region region : content.regions(canvasWidth(), canvasHeight())) {
            DisplayContent.Widget widget = region.widget();
            String key = region.name().toUpperCase(Locale.ROOT);
            if (!widget.kind().equals("*IMAGE") || images.containsKey(key) || pendingImages.contains(key)) {
                continue;
            }
            try {
                Path path = DisplayImages.check(server, system, widget.file());
                renderImage(server, region, path);
            } catch (ElclException e) {
                // Gone or too big since: the region stays blank.
                pendingImages.add(key);
            }
        }
    }

    // Renders a region's image off the server thread; it shows when it's done.
    public void renderImage(MinecraftServer server, DisplayContent.Region region, Path path) {
        String key = region.name().toUpperCase(Locale.ROOT);
        DisplayContent.Widget widget = region.widget();
        DisplayImages.Colors colors = DisplayImages.capped(widget.colors().equals("*DFT") ? DisplayImages.Colors.C256 : DisplayImages.Colors.of(widget.colors()));
        pendingImages.add(key);
        DisplayImages.render(path, region.w(), region.h(), widget.scale().equals("*NEAREST"), colors).whenComplete((pixels, error) -> server.execute(() -> {
            pendingImages.remove(key);
            DisplayContent.Region now = content.region(region.name(), canvasWidth(), canvasHeight());
            if (error == null && pixels != null && !isRemoved() && now != null && now.w() == region.w() && now.h() == region.h()
                    && now.widget().kind().equals("*IMAGE")) {
                images.put(key, pixels);
                sync();
            }
        }));
    }

    public void forgetImage(String region) {
        String key = region.toUpperCase(Locale.ROOT);
        images.remove(key);
        pendingImages.remove(key);
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

    // Line n (1-based), or the line after the last one written (0; past the bottom the lines roll up). A line keeps its
    // colour, alignment and scale.
    @Override
    public void write(int line, String text, boolean clear) {
        List<DisplayContent.TextLine> lines = content.lines;
        if (clear) {
            lines.clear();
            content.next = 0;
        }
        int at = line > 0 ? line - 1 : content.next;
        if (at >= lines()) {
            lines.removeFirst();
            at = lines() - 1;
        }
        while (lines.size() <= at) {
            lines.add(DisplayContent.TextLine.of(""));
        }
        String cut = text.length() > columns() ? text.substring(0, columns()) : text;
        lines.set(at, lines.get(at).withText(cut));
        content.next = at + 1;
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

    // Everything saved, and the rendered images.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        CompoundTag shown = new CompoundTag();
        images.forEach(shown::putIntArray);
        tag.put("images", shown);
        return tag;
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
        content = input.read("content", DisplayContent.CODEC).orElseGet(DisplayContent::new);
        // Phase 2's plain lines.
        input.read("lines", Codec.STRING.listOf()).ifPresent(old -> {
            content.lines.clear();
            old.forEach(text -> content.lines.add(DisplayContent.TextLine.of(text)));
            content.next = input.getIntOr("next", 0);
        });
        deviceName = input.getStringOr("device_name", "");
        // The client's images.
        input.read("images", CompoundTag.CODEC).ifPresent(shown -> {
            images.clear();
            for (String key : shown.keySet()) {
                shown.getIntArray(key).ifPresent(pixels -> images.put(key, pixels));
            }
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (master != null) {
            output.store("master", BlockPos.CODEC, master);
        }
        output.putInt("width", width);
        output.putInt("height", height);
        output.store("content", DisplayContent.CODEC, content);
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
            DisplayFramePayload.forget(worldPosition);
        }
    }
}
