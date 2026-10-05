/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.display.DisplayContent;
import net.zagdrath.encodedlogistics.display.DisplayImages;
import net.zagdrath.encodedlogistics.display.DisplayPanelBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.exec.ElclItems;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// A Display Panel screen's configuration (HANDOFF 4; client: DisplayPanelScreen): no slots; the screen shows the master's
// synced content and sends changes (DisplayConfigPayload), each needing the Firewall's build permission and checked
// here: the mode, the device name (as RNMDEV), the background, the regions' layout (inside the screen, not overlapping,
// at most MAX_REGIONS; widgets kept by name) and a region's widget (its data source and image as the commands check
// them).
public class DisplayPanelMenu extends AbstractContainerMenu {
    public static final int MAX_REGIONS = 16;

    private final BlockPos master;
    private final Player player;

    public static void open(ServerPlayer player, BlockPos master) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new DisplayPanelMenu(id, inventory, master),
                Component.translatable("block.encodedlogistics.display_panel")), buf -> buf.writeBlockPos(master));
    }

    // Client constructor.
    public DisplayPanelMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos());
    }

    public DisplayPanelMenu(int containerId, Inventory inventory, BlockPos master) {
        super(ModMenuTypes.DISPLAY_PANEL.get(), containerId);
        this.master = master;
        this.player = inventory.player;
    }

    public BlockPos master() {
        return master;
    }

    public @Nullable DisplayPanelBlockEntity display() {
        return player.level().getBlockEntity(master) instanceof DisplayPanelBlockEntity display && display.isMaster() ? display : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return display() != null && player.isWithinBlockInteractionRange(master, 6.0);
    }

    // --- Changes from the screen ---

    public void apply(ServerPlayer player, String action, CompoundTag data) {
        DisplayPanelBlockEntity display = display();
        if (display == null || !(player.level() instanceof ServerLevel level) || !NetworkAccess.check(level, master, player, RackPermission.BUILD)) {
            return;
        }
        DisplayContent content = display.displayContent();
        int cw = display.canvasWidth(), ch = display.canvasHeight();
        try {
            switch (action) {
                case "mode" -> content.mode = DisplayContent.Mode.byName(data.getStringOr("mode", "TEXT"));
                case "background" -> content.background = data.getIntOr("color", 0);
                case "name" -> rename(level, display, data.getStringOr("name", ""));
                case "regions" -> regions(display, data.get("regions") == null ? null
                        : DisplayContent.Region.CODEC.listOf().parse(NbtOps.INSTANCE, data.get("regions")).result().orElse(null));
                case "widget" -> {
                    String name = data.getStringOr("region", "");
                    DisplayContent.Region region = content.region(name, cw, ch);
                    DisplayContent.Widget widget = data.get("widget") == null ? null
                            : DisplayContent.Widget.CODEC.parse(NbtOps.INSTANCE, data.get("widget")).result().orElse(null);
                    if (region == null || widget == null) {
                        return;
                    }
                    widget(level, display, region, widget);
                }
                default -> {
                    return;
                }
            }
        } catch (ElclException e) {
            player.sendOverlayMessage(Component.literal(e.elclMessage().id() + "  " + e.elclMessage().text()));
            return;
        }
        display.changed();
    }

    private static void rename(ServerLevel level, DisplayPanelBlockEntity display, String name) throws ElclException {
        String wanted = name.trim().toUpperCase(Locale.ROOT);
        NetworkRef network = display.network();
        if (wanted.isEmpty() || network == null || wanted.equals(display.name())) {
            return;
        }
        ElclDevices.list(level.getServer(), network);
        ElclDevices.rename(level.getServer(), network, display.name(), wanted);
    }

    // A new layout: each region inside the screen, none overlapping, names unique; widgets stay with their names.
    private static void regions(DisplayPanelBlockEntity display, @Nullable List<DisplayContent.Region> wanted) throws ElclException {
        if (wanted == null || wanted.isEmpty() || wanted.size() > MAX_REGIONS) {
            return;
        }
        DisplayContent content = display.displayContent();
        int cw = display.canvasWidth(), ch = display.canvasHeight();
        List<DisplayContent.Region> regions = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (DisplayContent.Region region : wanted) {
            String name = region.name().toUpperCase(Locale.ROOT);
            if (!name.matches("[A-Z0-9]{1,8}") || !names.add(name) || region.w() <= 0 || region.h() <= 0 || region.x() < 0 || region.y() < 0
                    || region.x() + region.w() > cw || region.y() + region.h() > ch) {
                throw new ElclException("ELC1314", name);
            }
            for (DisplayContent.Region other : regions) {
                if (other.overlaps(region)) {
                    throw new ElclException("ELC1314", name);
                }
            }
            DisplayContent.Region old = content.region(name, cw, ch);
            regions.add(new DisplayContent.Region(name, region.x(), region.y(), region.w(), region.h(), region.bg(),
                    old != null ? old.widget() : DisplayContent.Widget.NONE));
        }
        for (DisplayContent.Region region : regions) {
            DisplayContent.Region old = content.region(region.name(), cw, ch);
            if (old == null || old.w() != region.w() || old.h() != region.h()) {
                display.forgetImage(region.name());
            }
        }
        content.regions.clear();
        content.regions.addAll(regions);
    }

    private static void widget(ServerLevel level, DisplayPanelBlockEntity display, DisplayContent.Region region, DisplayContent.Widget wanted)
            throws ElclException {
        String kind = wanted.kind();
        if (!DisplayContent.Widget.KINDS.contains(kind)) {
            return;
        }
        DisplayContent.Widget widget = wanted;
        boolean needsItem = kind.equals("*ITEM") || kind.equals("*GRAPH") && wanted.stat().equals("*ITEM");
        if (needsItem) {
            if (wanted.item().isBlank()) {
                throw new ElclException("ELC1316", "*NONE", kind);
            }
            String id = BuiltInRegistries.ITEM.getKey(ElclItems.resolve(wanted.item())).toString();
            widget = new DisplayContent.Widget(kind, id, wanted.devType(), wanted.color(), wanted.stat(), wanted.range(), wanted.graph(), wanted.file(),
                    wanted.scale(), wanted.colors());
        }
        display.displayContent().put(region.with(widget), display.canvasWidth(), display.canvasHeight());
        display.forgetImage(region.name());
        if (kind.equals("*IMAGE")) {
            NetworkRef network = display.network();
            if (network == null) {
                throw new ElclException("ELC1302", display.name());
            }
            ElclSystem system = new ElclSystem(level.getServer(), network);
            Path path = DisplayImages.check(level.getServer(), system.name(), wanted.file());
            DisplayImages.Colors colors = DisplayImages.capped(DisplayImages.Colors.of(wanted.colors()));
            DisplayContent.Region placed = region.with(new DisplayContent.Widget(kind, "", "*ALL", 0, "", "*10M", "*LINE", path.getFileName().toString(),
                    wanted.scale(), colors.label()));
            display.displayContent().put(placed, display.canvasWidth(), display.canvasHeight());
            display.renderImage(level.getServer(), placed, path);
        }
    }
}
