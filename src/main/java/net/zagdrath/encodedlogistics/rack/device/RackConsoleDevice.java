/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.NonNullList;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.menu.RackConsoleMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.registry.ModSounds;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The Rack Console (1U): an Access Terminal in a rack drawer. Right-click it through the open front door and the
// drawer slides out (8 ticks) and its screen folds up (6 ticks); right-click it open (its network online) for the
// terminal. Sneak-right-click, closing the door, or nobody within 4 blocks folds it away again, screen first - snapped
// shut together in 2 ticks when the door is closing, ahead of the door.
//
// Three kinds share it (Kind, by device type): the Rack Console (the Access Terminal's screen), the Rack Fabrication
// Console (the Fabrication Terminal's, its crafting grid kept in the console) and the Rack Schematic Console (the
// Schematic Terminal's). The Memory Die upgrade that once gave a Rack Console its crafting grid is gone: a console saved
// with it becomes a Rack Fabrication Console when its rack loads (RackBlockEntity), keeping its grid, and the die comes
// back out of the rack; the next player to open it is told (migrated).
//
// Open, its lid stands in front of the rack about 7.6 px (U) above its own bottom, where the drawer of a console above
// would slide out: two consoles need SPACING units between their bottoms (7U free between them), or they'd clip.
public class RackConsoleDevice extends RackDevice {
    // Which terminal it opens.
    public enum Kind {
        ACCESS, FABRICATION, SCHEMATIC;

        public static Kind of(RackDeviceType type) {
            return type == RackDeviceType.RACK_FABRICATION_CONSOLE ? FABRICATION : type == RackDeviceType.RACK_SCHEMATIC_CONSOLE ? SCHEMATIC : ACCESS;
        }
    }

    // Units from one console's bottom to the next's, at least: the lid's 7 px on its 0.625 px hinge, rounded up.
    public static final int SPACING = 8;

    // Whether consoles at these units would clip when both are open.
    public static boolean tooClose(int u, int other) {
        return u != other && Math.abs(u - other) < SPACING;
    }

    public static final int DRAWER_TICKS = 8, LID_TICKS = 6;
    private static final double REACH = 4.0;
    private static final int CHECK_INTERVAL = 10;

    private boolean open;
    // Converted from an upgraded Rack Console: the next player to open it hears about it.
    private boolean migrated;
    private final NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
    // Server: a sound due in so many ticks.
    private int soundIn = -1, checkTimer;
    private Holder<SoundEvent> sound = ModSounds.RACK_CONSOLE_HINGE;
    // Client: the drawer and the lid, ticks out of DRAWER_TICKS and LID_TICKS, now and last tick.
    private int drawer, lastDrawer, lid, lastLid;
    private boolean seen;

    public RackConsoleDevice(RackDeviceType type) {
        super(type);
    }

    public boolean isOpen() {
        return open;
    }

    public Kind kind() {
        return Kind.of(type());
    }

    // An old Rack Console with the Memory Die upgrade, made a Rack Fabrication Console: its grid, the message to come.
    public void adoptUpgraded(RackConsoleDevice old) {
        for (int i = 0; i < grid.size(); i++) {
            grid.set(i, old.grid.get(i));
        }
        migrated = true;
    }

    public NonNullList<ItemStack> grid() {
        return grid;
    }

    public void gridChanged() {
        saveOnly();
    }

    @Override
    public double drain() {
        return Config.RACK_CONSOLE_DRAIN.getAsDouble() + (open ? Config.RACK_CONSOLE_OPEN_DRAIN.getAsDouble() : 0);
    }

    // --- Using it ---

    @Override
    public boolean use(ServerPlayer player) {
        if (player.isSecondaryUseActive()) {
            if (!open) {
                return false;
            }
            setOpen(false);
            return true;
        }
        if (!open) {
            setOpen(true);
        } else if (isOnline() && rack() != null) {
            if (migrated) {
                migrated = false;
                player.sendSystemMessage(Component.translatable("message.encodedlogistics.console.migrated"));
                saveOnly();
            }
            RackConsoleMenu.open(player, rack().getBlockPos(), u(), kind());
        } else {
            player.sendOverlayMessage(Component.translatable("gui.encodedlogistics.terminal.offline"));
        }
        return true;
    }

    public void setOpen(boolean open) {
        if (this.open == open || rack() == null || !(rack().getLevel() instanceof ServerLevel level)) {
            return;
        }
        this.open = open;
        // Opening: the drawer slides out, then the screen clicks up. Closing: the screen clicks down, then it slides in.
        play(level, open ? ModSounds.RACK_CONSOLE_SLIDE_OUT : ModSounds.RACK_CONSOLE_HINGE);
        sound = open ? ModSounds.RACK_CONSOLE_HINGE : ModSounds.RACK_CONSOLE_SLIDE_IN;
        soundIn = open ? DRAWER_TICKS + LID_TICKS : LID_TICKS;
        changed(true);
    }

    private void play(ServerLevel level, Holder<SoundEvent> event) {
        level.playSound(null, rack().getBlockPos(), event.value(), SoundSource.BLOCKS, 0.7F, 0.95F + level.getRandom().nextFloat() * 0.1F);
    }

    @Override
    public void frontDoorChanged(boolean open) {
        if (!open && this.open) {
            setOpen(false);
            // Snapped shut with the door: the slide straight after the hinge.
            soundIn = 2;
        }
    }

    @Override
    public void tick(ServerLevel level) {
        if (soundIn >= 0 && soundIn-- == 0 && rack() != null) {
            play(level, sound);
        }
        if (open && ++checkTimer >= CHECK_INTERVAL) {
            checkTimer = 0;
            Vec3 where = rack() != null ? where() : null;
            if (where != null && level.players().stream().noneMatch(player -> !player.isSpectator() && player.distanceToSqr(where) <= REACH * REACH)) {
                setOpen(false);
            }
        }
    }

    // The console, in the world.
    private Vec3 where() {
        return RackGeometry.toWorld(RackGeometry.deviceBox(u(), 1), rack().getBlockPos(), rack().facing()).getCenter();
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        NetworkStorage storage = rack() != null && isOnline() ? ControllerStructures.sharedStorageOf(viewer.level().getServer(), rack().network(this), false) : null;
        long items = storage != null ? storage.list().values().stream().mapToLong(Long::longValue).sum() : 0;
        return List.of(
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.console.network"),
                        Component.translatable(storage != null ? "gui.encodedlogistics.status.online" : "gui.encodedlogistics.status.offline")),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.console.items"),
                        Component.literal(String.format(Locale.ROOT, "%,d", items))),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.console.drawer"),
                        Component.translatable(open ? "hud.encodedlogistics.console.open" : "hud.encodedlogistics.console.closed")));
    }

    // The grid's items come out with it.
    @Override
    public List<ItemStack> contents() {
        List<ItemStack> stacks = new ArrayList<>(super.contents());
        grid.stream().filter(stack -> !stack.isEmpty()).forEach(stack -> stacks.add(stack.copy()));
        return stacks;
    }

    @Override
    public void clearContents() {
        super.clearContents();
        grid.replaceAll(stack -> ItemStack.EMPTY);
    }

    // --- Saving ---

    // Whether a saved Rack Console had the Memory Die upgrade (its old "fabrication" setting): RackBlockEntity converts it.
    public static boolean upgraded(ValueInput data) {
        return data.getBooleanOr("fabrication", false);
    }

    @Override
    public void save(ValueOutput output) {
        output.putBoolean("open", open);
        if (migrated) {
            output.putBoolean("migrated", true);
        }
        ContainerHelper.saveAllItems(output.child("grid"), grid);
    }

    @Override
    public void load(ValueInput input) {
        open = input.getBooleanOr("open", false);
        migrated = input.getBooleanOr("migrated", false);
        grid.replaceAll(stack -> ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty("grid"), grid);
    }

    @Override
    public void writeClient(ValueOutput output) {
        output.putBoolean("open", open);
    }

    @Override
    public void readClient(ValueInput input) {
        boolean wasOpen = open;
        open = input.getBooleanOr("open", false);
        // A console seen for the first time open is open already, not opening.
        if (open && !wasOpen && drawer == 0 && lastDrawer == 0 && !seen) {
            drawer = lastDrawer = DRAWER_TICKS;
            lid = lastLid = LID_TICKS;
        }
        seen = true;
    }

    // --- Client animation ---

    @Override
    public void clientTick() {
        lastDrawer = drawer;
        lastLid = lid;
        if (open) {
            if (drawer < DRAWER_TICKS) {
                drawer++;
            } else if (lid < LID_TICKS) {
                lid++;
            }
        } else if (rack() != null && !rack().isFrontOpen()) {
            // The door is swinging shut: fold and slide in at once, fast, before it gets there.
            lid = Math.max(0, lid - LID_TICKS / 2);
            drawer = Math.max(0, drawer - DRAWER_TICKS / 2);
        } else if (lid > 0) {
            lid--;
        } else if (drawer > 0) {
            drawer--;
        }
    }

    // How far out the drawer is, 0-1 (ease-out cubic).
    public float drawer(float partialTick) {
        float t = (lastDrawer + (drawer - lastDrawer) * partialTick) / DRAWER_TICKS;
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    // How far up the screen is, 0-1 (ease-out back: a small overshoot).
    public float lid(float partialTick) {
        float t = (lastLid + (lid - lastLid) * partialTick) / LID_TICKS;
        float c1 = 1.70158F, c3 = c1 + 1;
        return t <= 0 ? 0 : 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }
}
