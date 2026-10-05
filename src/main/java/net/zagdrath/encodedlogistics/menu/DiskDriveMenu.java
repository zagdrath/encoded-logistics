/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.midrange.DiskDriveBlockEntity;
import net.zagdrath.encodedlogistics.midrange.PeripheralBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.storage.DriveStats;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// DSKDRV (HANDOFF 3; layout dskdrv): the Disk Drive's state and pack. Lines, tab-separated: S state (its State), P itemKey
// items capacity percent types typeLimit (its pack), and while 5=Display contents shows them, V and C itemKey count (most
// first). Option (row 0): 4=Unload, 5=Display contents. Button: back from the contents (F12).
public class DiskDriveMenu extends PeripheralMenu {
    public static final int BUTTON_BACK = 0, CONTENTS_SHOWN = 9;

    private final @Nullable DiskDriveBlockEntity drive;
    private boolean showing;

    // Client constructor.
    public DiskDriveMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, new SimpleContainer(1), null, Opening.read(buf));
    }

    public DiskDriveMenu(int containerId, Inventory inventory, Container slots, @Nullable PeripheralBlockEntity peripheral, Opening opening) {
        super(ModMenuTypes.DISK_DRIVE.get(), containerId, inventory, slots, peripheral, opening);
        this.drive = peripheral instanceof DiskDriveBlockEntity d ? d : null;
    }

    @Override
    protected void refresh() {
        if (drive == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add("S\t" + drive.state().name());
        ItemStack pack = drive.pack();
        if (pack.getItem() instanceof StorageDriveItem item) {
            DriveStats stats = StorageDriveItem.stats(pack);
            List<Map.Entry<ItemKey, Long>> contents = drive.contents();
            long items = contents.stream().mapToLong(Map.Entry::getValue).sum();
            long percent = stats.bytesTotal() <= 0 ? 0 : stats.bytesUsed() * 100 / stats.bytesTotal();
            lines.add(String.join("\t", "P", pack.getItem().getDescriptionId(), Long.toString(items), Long.toString(item.getTier().bytes() * 8),
                    Long.toString(percent), Integer.toString(stats.typesUsed()), Integer.toString(item.getTier().typeLimit())));
            if (showing) {
                lines.add("V");
                for (int i = 0; i < Math.min(CONTENTS_SHOWN, contents.size()); i++) {
                    lines.add(String.join("\t", "C", contents.get(i).getKey().stack().getItem().getDescriptionId(), Long.toString(contents.get(i).getValue())));
                }
            }
        } else {
            showing = false;
        }
        send(Component.empty(), lines, List.of(lines.size()));
    }

    @Override
    protected @Nullable Component option(int row, String option) {
        if (drive == null || row != 0) {
            return null;
        }
        return switch (option) {
            case "4" -> drive.ejectLater(player) ? Component.translatable("crt.encodedlogistics.dskdrv.unloading")
                    : Component.translatable("crt.encodedlogistics.dskdrv.no_pack");
            case "5" -> {
                showing = !drive.pack().isEmpty();
                yield showing ? Component.empty() : Component.translatable("crt.encodedlogistics.dskdrv.no_pack");
            }
            default -> null;
        };
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_BACK) {
            return false;
        }
        showing = false;
        refresh();
        return true;
    }
}
