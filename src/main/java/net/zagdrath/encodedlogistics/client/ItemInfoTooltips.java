/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.item.CableFacadeItem;
import net.zagdrath.encodedlogistics.item.HandheldTerminalItem;
import net.zagdrath.encodedlogistics.item.LinkAddress;
import net.zagdrath.encodedlogistics.item.LinkCardItem;
import net.zagdrath.encodedlogistics.item.LtoTapeItem;
import net.zagdrath.encodedlogistics.item.PartItem;
import net.zagdrath.encodedlogistics.item.PrintoutItem;
import net.zagdrath.encodedlogistics.item.SchematicItem;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.item.StorageTierItem;
import net.zagdrath.encodedlogistics.item.TapeReelItem;
import net.zagdrath.encodedlogistics.midrange.DisketteData;
import net.zagdrath.encodedlogistics.midrange.DisketteMagazineItem;
import net.zagdrath.encodedlogistics.midrange.Printout;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.device.MemoryServerDevice;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.plc.PlcProgram;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.DriveStats;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// A short description of what each Encoded Logistics item does, under its name, the same way as Arcforge: "Hold
// [Shift] for info", and the description while Shift is held. Descriptions live in the lang file as
// tooltip.encodedlogistics.info.<item>; coloured items (cables) share one without the colour, e.g.
// tooltip.encodedlogistics.info.network_cable. A cable's Shift info ends with its lanes and the colours it joins; a
// facade always shows the block it copies.
@EventBusSubscriber(modid = EncodedLogistics.MODID, value = Dist.CLIENT)
public final class ItemInfoTooltips {
    private static final String PREFIX = "tooltip.encodedlogistics.info.";
    // Descriptions wrap at this width (the vanilla tooltip font is 9 px tall; ~40 characters a line).
    private static final int WRAP_WIDTH = 200;

    private ItemInfoTooltips() {}

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        Identifier id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (!EncodedLogistics.MODID.equals(id.getNamespace())) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        String key = descriptionKey(id.getPath());
        // Right under the name, before the item's own lines.
        int at = Math.min(1, tooltip.size());
        // Drives, dies and photomasks always say their tier, fill or that they're reusable.
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof StorageDriveItem) {
            DriveStats stats = StorageDriveItem.stats(stack);
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.drive.bytes", String.format(Locale.ROOT, "%,d", stats.bytesUsed()),
                    String.format(Locale.ROOT, "%,d", stats.bytesTotal())).withStyle(ChatFormatting.GRAY));
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.drive.types", stats.typesUsed(), Config.DRIVE_TYPE_LIMIT.getAsInt())
                    .withStyle(ChatFormatting.GRAY));
        } else if (stack.getItem() instanceof LtoTapeItem tape) {
            DriveStats stats = LtoTapeItem.stats(stack);
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.lto_tape.capacity", StorageDevice.bytes(tape.generation().bytes()))
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.lto_tape.used", StorageDevice.bytes(stats.bytesUsed()),
                    StorageDevice.bytes(stats.bytesTotal())).withStyle(ChatFormatting.GRAY));
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.drive.types", stats.typesUsed(), Config.DRIVE_TYPE_LIMIT.getAsInt())
                    .withStyle(ChatFormatting.GRAY));
        } else if (stack.getItem() instanceof PrintoutItem && PrintoutItem.printout(stack) != null) {
            Printout printout = PrintoutItem.printout(stack);
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.printout", printout.title(), printout.pages().size())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.printout.from", printout.printer(), printout.printed())
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (stack.getItem() instanceof TapeReelItem) {
            DriveStats stats = TapeReelItem.stats(stack);
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.tape_reel.contents", String.format(Locale.ROOT, "%,d", stats.bytesUsed() * 8),
                    String.format(Locale.ROOT, "%,d", Config.TAPE_REEL_ITEMS.getAsInt())).withStyle(ChatFormatting.GRAY));
            if (TapeReelItem.id(stack) != null) {
                tooltip.add(at++, Component.literal(TapeReelItem.volume(stack)).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (stack.getItem() instanceof StorageTierItem die) {
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.die.tier",
                    Component.literal(die.getTier().label()).withColor(die.getTier().light())).withStyle(ChatFormatting.GRAY));
        } else if (stack.is(ModItems.FILTER_MODULE.get()) || stack.is(ModItems.THROUGHPUT_MODULE.get()) || stack.is(ModItems.FUZZY_MATCH_MODULE.get())
                || stack.is(ModItems.REDSTONE_CONTROL_MODULE.get())) {
            String module = stack.is(ModItems.FILTER_MODULE.get()) ? "filter" : stack.is(ModItems.THROUGHPUT_MODULE.get()) ? "throughput"
                    : stack.is(ModItems.FUZZY_MATCH_MODULE.get()) ? "fuzzy" : "redstone";
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.module." + module).withStyle(ChatFormatting.GRAY));
        } else if (stack.getItem() instanceof LinkCardItem) {
            // A written card says what it holds.
            LinkAddress address = LinkCardItem.address(stack);
            if (address != null) {
                Component kind = Component.translatable(switch (address.kind()) {
                    case BRIDGE -> "block.encodedlogistics.network_bridge";
                    case P2P -> "item.encodedlogistics.point_to_point_link";
                    case SEGMENT -> "tooltip.encodedlogistics.link_card.segment";
                    case NETWORK -> "tooltip.encodedlogistics.link_card.network";
                    case WIRELESS -> "item.encodedlogistics.wireless_controller";
                });
                tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.link_card.address", kind, address.pos().pos().getX(),
                        address.pos().pos().getY(), address.pos().pos().getZ()).withStyle(ChatFormatting.GRAY));
                tooltip.add(at++, Component.literal(address.pos().dimension().identifier().toString()).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (stack.is(ModItems.EEPROM_CARTRIDGE.get()) || stack.is(ModItems.PLC.get())) {
            // A PLC's program: on a written cartridge, or carried by a PLC's item; a blank cartridge says so.
            PlcProgram program = stack.get(ModDataComponents.PLC_PROGRAM.get());
            if (program != null) {
                tooltip.add(at++, Component.translatable(stack.is(ModItems.PLC.get()) ? "tooltip.encodedlogistics.plc.program" : "tooltip.encodedlogistics.eeprom.program",
                        program.name(), String.format(Locale.ROOT, "%,d", program.size())).withStyle(ChatFormatting.GRAY));
            } else if (stack.is(ModItems.EEPROM_CARTRIDGE.get())) {
                tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.eeprom.blank").withStyle(ChatFormatting.GRAY));
            }
        } else if (stack.getItem() instanceof HandheldTerminalItem) {
            // Its network and battery.
            NetworkIndex.NetworkRef network = HandheldTerminalItem.network(stack);
            tooltip.add(at++, (network != null
                    ? Component.translatable("tooltip.encodedlogistics.handheld.network", network.id(), network.dimension().identifier().toString())
                    : Component.translatable("tooltip.encodedlogistics.handheld.hint")).withStyle(ChatFormatting.GRAY));
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.handheld.energy",
                    String.format(Locale.ROOT, "%,d", HandheldTerminalItem.energy(stack)), String.format(Locale.ROOT, "%,d", HandheldTerminalItem.capacity()))
                    .withStyle(ChatFormatting.GRAY));
        } else if (stack.getItem() instanceof PartItem part && !part.getPartType().isTerminal() && part.getPartType().lanes() > 0) {
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.part.lane").withStyle(ChatFormatting.DARK_GRAY));
        } else if (SchematicItem.schematic(stack) != null) {
            // An encoded schematic: what it makes and takes.
            Schematic schematic = SchematicItem.schematic(stack);
            ItemStack output = schematic.output();
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.schematic.output",
                    Component.literal(output.getCount() + " x ").append(output.getHoverName())).withStyle(ChatFormatting.GRAY));
            for (Map.Entry<ItemKey, Long> input : schematic.inputTotals().entrySet()) {
                tooltip.add(at++, Component.literal("  " + input.getValue() + " x ").append(input.getKey().stack().getHoverName())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            if (!Minecraft.getInstance().hasShiftDown()) {
                tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.schematic.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (stack.is(ModItems.PUNCH_CARD.get())) {
            // A punched card: what its recipe makes, and (Shift) what it takes; a blank one says how to punch it.
            Schematic recipe = stack.get(ModDataComponents.PUNCHED_RECIPE.get());
            if (recipe == null) {
                tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.punch_card.blank").withStyle(ChatFormatting.GRAY));
            } else {
                ItemStack output = recipe.output();
                tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.punch_card.recipe", output.getHoverName(), output.getCount())
                        .withStyle(ChatFormatting.GRAY));
                if (Minecraft.getInstance().hasShiftDown()) {
                    for (Map.Entry<ItemKey, Long> input : recipe.inputTotals().entrySet()) {
                        tooltip.add(at++, Component.literal("  " + input.getValue() + " x ").append(input.getKey().stack().getHoverName())
                                .withStyle(ChatFormatting.DARK_GRAY));
                    }
                } else {
                    tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.punch_card.shift").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        } else if (stack.is(ModItems.DISKETTE_8IN.get())) {
            // A written diskette: its library and the recipes on it.
            DisketteData data = stack.get(ModDataComponents.DISKETTE_RECIPES.get());
            if (data == null) {
                tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.diskette.blank").withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.diskette.library", data.label().isEmpty() ? "-" : data.label(),
                        data.recipes().size()).withStyle(ChatFormatting.GRAY));
                for (int i = 0; i < data.recipes().size(); i++) {
                    ItemStack output = data.recipes().get(i).output();
                    tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.diskette.entry", i + 1, output.getHoverName(), output.getCount())
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        } else if (stack.getItem() instanceof DisketteMagazineItem) {
            // How many diskettes it holds, and their libraries.
            List<ItemStack> diskettes = DisketteMagazineItem.diskettes(stack);
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.magazine.contents", diskettes.size()).withStyle(ChatFormatting.GRAY));
            for (ItemStack diskette : diskettes) {
                DisketteData data = diskette.get(ModDataComponents.DISKETTE_RECIPES.get());
                tooltip.add(at++, Component.literal("  ").append(data == null ? Component.translatable("tooltip.encodedlogistics.diskette.blank")
                        : Component.translatable("tooltip.encodedlogistics.diskette.library", data.label().isEmpty() ? "-" : data.label(), data.recipes().size()))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (stack.is(ModItems.LOGIC_PHOTOMASK.get()) || stack.is(ModItems.STORAGE_PHOTOMASK.get()) || stack.is(ModItems.MEMORY_PHOTOMASK.get())
                || stack.is(ModItems.PROCESSOR_PHOTOMASK.get())) {
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.photomask.reusable").withStyle(ChatFormatting.GRAY));
        }
        // A facade always says what it looks like.
        if (event.getItemStack().getItem() instanceof CableFacadeItem) {
            BlockState target = CableFacadeItem.target(event.getItemStack());
            tooltip.add(at++, (target != null
                    ? Component.translatable("tooltip.encodedlogistics.cable_facade.target", target.getBlock().getName())
                    : Component.translatable("tooltip.encodedlogistics.cable_facade.blank")).withStyle(ChatFormatting.GRAY));
        }
        if (key == null) {
            return;
        }
        if (!Minecraft.getInstance().hasShiftDown()) {
            tooltip.add(at, Component.translatable("tooltip.encodedlogistics.hold_shift",
                    Component.translatable("tooltip.encodedlogistics.shift").withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        List<FormattedText> lines = Minecraft.getInstance().font.getSplitter()
                .splitLines(Component.translatable(key, descriptionArguments(id.getPath())), WRAP_WIDTH, Style.EMPTY);
        for (int i = 0; i < lines.size(); i++) {
            tooltip.add(at + i, Component.literal(lines.get(i).getString()).withStyle(ChatFormatting.GRAY));
        }
        if (event.getItemStack().getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof NetworkCableBlock cable) {
            at += lines.size();
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.cable.lanes", cable.getTier().lanes()).withStyle(ChatFormatting.GRAY));
            DyeColor dye = cable.getColor().dye();
            tooltip.add(at, (dye == null ? Component.translatable("tooltip.encodedlogistics.cable.neutral")
                    : Component.translatable("tooltip.encodedlogistics.cable.dyed", Component.translatable("color.minecraft." + dye.getSerializedName())))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // Numbers a description shows that come from the config.
    private static Object[] descriptionArguments(String path) {
        return switch (path) {
            case "capacitor_bank" -> new Object[] { String.format(Locale.ROOT, "%,d", Config.CAPACITOR_CAPACITY.getAsInt()) };
            case "optical_transceiver" -> new Object[] { Config.RELAY_RANGE_PER_TRANSCEIVER.getAsInt(), Config.ROUTER_RATE_PER_TRANSCEIVER.getAsInt() };
            case "router" -> new Object[] { Config.ROUTER_BASE_RATE.getAsInt(), Config.ROUTER_RATE_PER_TRANSCEIVER.getAsInt() };
            case "ups" -> new Object[] { String.format(Locale.ROOT, "%,d", Config.UPS_CAPACITY.getAsInt()) };
            case "l2_switch_24" -> new Object[] { Config.L2_SWITCH_24_LANES.getAsInt() };
            case "l2_switch_48" -> new Object[] { Config.L2_SWITCH_48_LANES.getAsInt() };
            case "l3_switch" -> new Object[] { Config.L3_SWITCH_LANES.getAsInt() };
            case "compute_server" -> new Object[] { Config.COMPUTE_SERVER_THREADS.getAsInt() };
            case "memory_server" -> new Object[] { MemoryServerDevice.memory(Config.MEMORY_SERVER_MEMORY.getAsInt()) };
            case "relay_antenna" -> new Object[] { Config.RELAY_BASE_RANGE.getAsInt() };
            case "network_bridge" -> new Object[] { Config.BRIDGE_LANES.getAsInt() };
            case "access_point" -> new Object[] { Config.WIRELESS_AP_CLIENTS.getAsInt() };
            case "wireless_bridge" -> new Object[] { Config.WIRELESS_BRIDGE_LANES.getAsInt() };
            default -> new Object[0];
        };
    }

    // The item's description key, or the shared one without its colour, or null if it has none.
    private static @Nullable String descriptionKey(String path) {
        Language language = Language.getInstance();
        if (language.has(PREFIX + path)) {
            return PREFIX + path;
        }
        for (DyeColor dye : DyeColor.values()) {
            String colour = dye.getSerializedName() + "_";
            if (path.startsWith(colour) && language.has(PREFIX + path.substring(colour.length()))) {
                return PREFIX + path.substring(colour.length());
            }
        }
        for (StorageTier tier : StorageTier.values()) {
            String suffix = "_" + tier.id();
            if (path.endsWith(suffix) && language.has(PREFIX + path.substring(0, path.length() - suffix.length()))) {
                return PREFIX + path.substring(0, path.length() - suffix.length());
            }
        }
        return null;
    }
}
