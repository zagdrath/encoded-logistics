/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// An Alarm Strobe's settings and state (signals handoff 3): its light mode (solid or flashing slow, medium, fast), its
// tone (SirenSound, or none), volume (0-100 %), range (blocks, up to sirenMaxRange) and trigger (the redstone at its
// block, its network - STRSRN / ENDSRN - or either). Active while its trigger says so: the lens lights (LIGHT) and the
// tone plays from the sounder, again at the end of each sample (the client's tick, SignalSounds), counted from start -
// a game tick shared by the network's active sirens with the same tone, so a hall of them wails together. Type SRN.
public class SirenBlockEntity extends SignalBlockEntity {
    public static final String TYPE = "SRN";
    public static final SirenBlock.Light[] MODES = { SirenBlock.Light.SOLID, SirenBlock.Light.SLOW, SirenBlock.Light.MEDIUM, SirenBlock.Light.FAST };

    private SirenBlock.Light mode = SirenBlock.Light.FAST;
    private SirenSound sound = SirenSound.WAIL;
    private int volume = 80, range = 48;
    private Trigger trigger = Trigger.BOTH;
    private boolean networkActive, active;
    private long start;

    public SirenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SIREN.get(), pos, state);
    }

    @Override
    public String deviceType() {
        return TYPE;
    }

    public SirenBlock.Light mode() {
        return mode;
    }

    public SirenSound sound() {
        return sound;
    }

    public int volume() {
        return volume;
    }

    public int range() {
        return Math.min(range, Config.SIREN_MAX_RANGE.getAsInt());
    }

    public Trigger trigger() {
        return trigger;
    }

    public boolean active() {
        return active;
    }

    // The game tick its tone was started on (the client counts samples from it).
    public long start() {
        return start;
    }

    public SirenBlock.@Nullable Colour colour() {
        return getBlockState().getBlock() instanceof SirenBlock siren ? siren.colour() : null;
    }

    // STRSRN: on, with its tone and light mode (null: unchanged); ENDSRN: off.
    public void start(@Nullable SirenSound newSound, SirenBlock.@Nullable Light newMode) {
        boolean restart = newSound != null && newSound != sound;
        if (newSound != null) {
            sound = newSound;
        }
        if (newMode != null && newMode != SirenBlock.Light.OFF) {
            mode = newMode;
        }
        networkActive = true;
        update(restart);
    }

    public void end() {
        networkActive = false;
        update(false);
    }

    @Override
    protected void redstone(boolean now) {
        update(false);
    }

    // Active as its trigger says; started again (a new shared start) when it comes on or its tone changes.
    private void update(boolean restart) {
        boolean now = trigger.redstone() && powered() || trigger.network() && networkActive;
        if (now && (!active || restart)) {
            start = sharedStart();
        }
        active = now;
        sync();
        BlockState state = getBlockState();
        if (state.getBlock() instanceof SirenBlock) {
            showState(state.setValue(SirenBlock.LIGHT, active ? mode : SirenBlock.Light.OFF));
        }
    }

    // When another active siren on the network with this tone started (so they play together), else now.
    private long sharedStart() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        NetworkRef network = ControllerStructures.networkOf(serverLevel, worldPosition);
        if (network != null && sound != SirenSound.NONE) {
            for (SirenBlockEntity other : ControllerStructures.onNetwork(serverLevel.getServer(), network, SirenBlockEntity.class, false)) {
                if (other != this && other.active && other.sound == sound && !other.isRemoved()) {
                    return other.start;
                }
            }
        }
        return serverLevel.getGameTime();
    }

    @Override
    protected void clientTick() {
        SignalClientHooks.get().tick(this);
    }

    // --- Screen and popup ---

    @Override
    public Component statusText() {
        return Component.translatable(active ? "gui.encodedlogistics.signal.status.active" : "gui.encodedlogistics.signal.status.idle");
    }

    @Override
    public RackDeviceInfo.Status status() {
        return active ? RackDeviceInfo.Status.WARNING : RackDeviceInfo.Status.OFFLINE;
    }

    @Override
    public List<Row> rows() {
        SirenBlock.Colour colour = colour();
        return List.of(
                Row.shown("colour", Component.translatable("gui.encodedlogistics.signal.colour"), colour != null ? colour.label() : Component.empty()),
                Row.cycled("mode", Component.translatable("gui.encodedlogistics.signal.light_mode"), mode.label()),
                Row.cycled("sound", Component.translatable("gui.encodedlogistics.signal.sound"), sound.label()),
                Row.cycled("volume", Component.translatable("gui.encodedlogistics.signal.volume"), Component.literal(volume + "%")),
                Row.cycled("range", Component.translatable("gui.encodedlogistics.signal.range"),
                        Component.translatable("gui.encodedlogistics.signal.blocks", range())),
                Row.cycled("trigger", Component.translatable("gui.encodedlogistics.signal.trigger"), trigger.label()),
                deviceRow());
    }

    @Override
    protected boolean setting(ServerPlayer player, String key, int step, @Nullable String text) {
        switch (key) {
            case "mode" -> {
                int index = 0;
                for (int i = 0; i < MODES.length; i++) {
                    index = MODES[i] == mode ? i : index;
                }
                mode = MODES[Math.floorMod(index + Integer.signum(step), MODES.length)];
            }
            case "sound" -> sound = cycle(sound, step);
            case "volume" -> volume = Math.clamp(volume + (Math.abs(step) >= 10 ? Integer.signum(step) : step * 10), 0, 100);
            case "range" -> range = Math.clamp(range() + (Math.abs(step) >= 10 ? Integer.signum(step) : step * 8), 8, Config.SIREN_MAX_RANGE.getAsInt());
            case "trigger" -> trigger = cycle(trigger, step);
            default -> {
                return false;
            }
        }
        update(key.equals("sound"));
        return false;
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> hudLines() {
        return List.of(new RackDeviceInfo.InfoLine(Component.translatable("gui.encodedlogistics.signal.mode"), mode.label()),
                new RackDeviceInfo.InfoLine(Component.translatable("gui.encodedlogistics.signal.sound"), sound.label()));
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mode = load(input, "mode", SirenBlock.Light.FAST);
        if (mode == SirenBlock.Light.OFF) {
            mode = SirenBlock.Light.FAST;
        }
        sound = load(input, "sound", SirenSound.WAIL);
        volume = Math.clamp(input.getIntOr("volume", 80), 0, 100);
        range = Math.clamp(input.getIntOr("range", 48), 1, 256);
        trigger = load(input, "trigger", Trigger.BOTH);
        networkActive = input.getBooleanOr("network_active", false);
        active = input.getBooleanOr("active", false);
        start = input.getLongOr("start", 0L);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("mode", mode.name());
        output.putString("sound", sound.name());
        output.putInt("volume", volume);
        output.putInt("range", range);
        output.putString("trigger", trigger.name());
        output.putBoolean("network_active", networkActive);
        output.putBoolean("active", active);
        output.putLong("start", start);
    }
}
