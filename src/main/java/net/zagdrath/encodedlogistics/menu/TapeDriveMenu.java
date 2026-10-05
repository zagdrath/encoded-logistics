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
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.item.TapeReelItem;
import net.zagdrath.encodedlogistics.midrange.PeripheralBlockEntity;
import net.zagdrath.encodedlogistics.midrange.TapeDriveBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// TAPDRV (HANDOFF 3; layout tapdrv): the Tape Drive's state and reel. Lines, tab-separated: S state loadPoint (its State,
// 1 at the load point), R volume used capacity percent lastAccess (its reel; the last access as the overworld's clock,
// -1 never), and while 5=Display contents shows them, V and C itemKey count (most first). Option (row 0): 4=Unload,
// 5=Display contents, 7=Rewind. Button: back from the contents (F12).
public class TapeDriveMenu extends PeripheralMenu {
    public static final int BUTTON_BACK = 0, CONTENTS_SHOWN = 9;

    private final @Nullable TapeDriveBlockEntity tape;
    private boolean showing;

    // Client constructor.
    public TapeDriveMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, new SimpleContainer(1), null, Opening.read(buf));
    }

    public TapeDriveMenu(int containerId, Inventory inventory, Container slots, @Nullable PeripheralBlockEntity peripheral, Opening opening) {
        super(ModMenuTypes.TAPE_DRIVE.get(), containerId, inventory, slots, peripheral, opening);
        this.tape = peripheral instanceof TapeDriveBlockEntity t ? t : null;
    }

    @Override
    protected void refresh() {
        if (tape == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add("S\t" + tape.state().name() + "\t" + (tape.atLoadPoint() ? 1 : 0));
        if (!tape.reel().isEmpty()) {
            long used = tape.used(), capacity = Config.TAPE_REEL_ITEMS.getAsInt();
            lines.add(String.join("\t", "R", TapeReelItem.volume(tape.reel()), Long.toString(used), Long.toString(capacity),
                    Long.toString(capacity <= 0 ? 0 : used * 100 / capacity), Long.toString(tape.lastAccess())));
            if (showing) {
                lines.add("V");
                List<Map.Entry<ItemKey, Long>> contents = tape.contents();
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
        if (tape == null || row != 0) {
            return null;
        }
        boolean reel = !tape.reel().isEmpty();
        return switch (option) {
            case "4" -> tape.ejectLater(player) ? Component.translatable("crt.encodedlogistics.tapdrv.unloading")
                    : Component.translatable("crt.encodedlogistics.tapdrv.no_reel");
            case "5" -> {
                showing = reel;
                yield reel ? Component.empty() : Component.translatable("crt.encodedlogistics.tapdrv.no_reel");
            }
            case "7" -> {
                if (!reel) {
                    yield Component.translatable("crt.encodedlogistics.tapdrv.no_reel");
                }
                tape.startRewind(false);
                yield Component.translatable("crt.encodedlogistics.tapdrv.rewinding");
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
