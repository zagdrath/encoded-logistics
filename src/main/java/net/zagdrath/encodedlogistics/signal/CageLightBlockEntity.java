/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// A Cage Light's settings (signals handoff 2): what controls it - the redstone at its block (on with a signal, the
// default), the redstone inverted, always on, or its network only - and its light level (1-15). Its network's state
// (CHGLGT STATUS) adds to the redstone's: either turns it on; Always on ignores it. Type LGT on its network.
public class CageLightBlockEntity extends SignalBlockEntity {
    public static final String TYPE = "LGT";

    public enum Control {
        REDSTONE, INVERTED, ALWAYS, NETWORK;

        public Component label() {
            return Component.translatable("gui.encodedlogistics.signal.control." + name().toLowerCase(Locale.ROOT));
        }
    }

    private Control control = Control.REDSTONE;
    private int lightLevel = 15;
    private boolean networkOn;

    public CageLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CAGE_LIGHT.get(), pos, state);
    }

    @Override
    public String deviceType() {
        return TYPE;
    }

    public Control control() {
        return control;
    }

    public int lightLevel() {
        return lightLevel;
    }

    public boolean lit() {
        return switch (control) {
            case REDSTONE -> powered() || networkOn;
            case INVERTED -> !powered() || networkOn;
            case ALWAYS -> true;
            case NETWORK -> networkOn;
        };
    }

    // CHGLGT: on, off or toggled (null: unchanged), and the level (0: unchanged).
    public void command(@Nullable Boolean on, boolean toggle, int level) {
        if (toggle) {
            networkOn = !networkOn;
        } else if (on != null) {
            networkOn = on;
        }
        if (level > 0) {
            lightLevel = Math.clamp(level, 1, 15);
        }
        changed();
    }

    @Override
    protected void redstone(boolean now) {
        changed();
    }

    private void changed() {
        sync();
        BlockState state = getBlockState();
        if (state.getBlock() instanceof CageLightBlock) {
            showState(state.setValue(CageLightBlock.LIT, lit()).setValue(CageLightBlock.LEVEL, lightLevel));
        }
    }

    // --- Screen and popup ---

    @Override
    public Component statusText() {
        return Component.translatable(lit() ? "gui.encodedlogistics.signal.status.on" : "gui.encodedlogistics.signal.status.off");
    }

    @Override
    public RackDeviceInfo.Status status() {
        return lit() ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.OFFLINE;
    }

    @Override
    public List<Row> rows() {
        Component color = getBlockState().getBlock() instanceof CageLightBlock light
                ? Component.translatable("color.minecraft." + light.color().getSerializedName()) : Component.empty();
        return List.of(
                Row.shown("colour", Component.translatable("gui.encodedlogistics.signal.colour"), color),
                Row.cycled("control", Component.translatable("gui.encodedlogistics.signal.control"), control.label()),
                Row.cycled("level", Component.translatable("gui.encodedlogistics.signal.level"), Component.literal(Integer.toString(lightLevel))),
                deviceRow());
    }

    @Override
    protected boolean setting(ServerPlayer player, String key, int step, @Nullable String text) {
        switch (key) {
            case "control" -> control = cycle(control, step);
            case "level" -> lightLevel = Math.clamp(lightLevel + Integer.signum(step) * (Math.abs(step) >= 10 ? 5 : 1), 1, 15);
            default -> {
                return false;
            }
        }
        changed();
        return false;
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> hudLines() {
        return List.of(new RackDeviceInfo.InfoLine(Component.translatable("gui.encodedlogistics.signal.mode"), control.label()),
                new RackDeviceInfo.InfoLine(Component.translatable("gui.encodedlogistics.signal.level"), Component.literal(Integer.toString(lightLevel))));
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        control = load(input, "control", Control.REDSTONE);
        lightLevel = Math.clamp(input.getIntOr("level", 15), 1, 15);
        networkOn = input.getBooleanOr("network_on", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("control", control.name());
        output.putInt("level", lightLevel);
        output.putBoolean("network_on", networkOn);
    }
}
