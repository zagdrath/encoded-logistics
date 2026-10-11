/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.schematic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.block.ServerRackBlock;
import net.zagdrath.encodedlogistics.blockentity.FabricatorBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.GatewayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.CraftingProvider;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.item.SchematicItem;
import net.zagdrath.encodedlogistics.machine.MachineBridges;
import net.zagdrath.encodedlogistics.midrange.MidrangeSystemBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.device.FabricationServerDevice;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// The schematic holders on a network, as the Schematic Terminal and WRKSCH show and edit them: every crafting provider
// on it, online or not (Fabricators, Gateways, Fabrication Servers and Midrange Systems; any future CraftingProvider
// block or rack device shows up too, read-only unless it's one of these). Each has a name - its device name (GATEWAY01,
// FABSRV01, MIDRANGE01; a Gateway with " > " and the machine it feeds), or FAB01, FAB02... for Fabricators, which carry
// none - and where it is ("212 64 -88", or its rack and unit: "RACK01 U14"). A Midrange System's are the recipes on
// its diskettes, read-only (the Card Reader edits them). Holders come in the network's provider order, the order the
// planner meets their schematics in.
public final class SchematicHolders {
    // A holder and how to get at its schematics.
    public abstract static class Holder {
        final String name, location;
        final NodePos pos;
        final @Nullable RackDevice rackDevice;
        final CraftingProvider provider;
        final String deviceName;

        Holder(String name, String location, NodePos pos, @Nullable RackDevice rackDevice, CraftingProvider provider, String deviceName) {
            this.name = name;
            this.location = location;
            this.pos = pos;
            this.rackDevice = rackDevice;
            this.provider = provider;
            this.deviceName = deviceName;
        }

        // Which holder this is: its block, and a rack device's unit.
        public String key() {
            return key(pos, rackDevice);
        }

        static String key(NodePos pos, @Nullable RackDevice device) {
            return pos.dimension().identifier() + "@" + pos.pos().asLong() + (device != null ? "/U" + device.u() : "");
        }

        public String name() {
            return name;
        }

        public String location() {
            return location;
        }

        public NodePos pos() {
            return pos;
        }

        public CraftingProvider provider() {
            return provider;
        }

        // Its device name, for renaming ("" when it has none: a Fabricator).
        public String deviceName() {
            return deviceName;
        }

        public abstract ItemStack icon();

        public abstract int kind();

        public boolean readOnly() {
            return false;
        }

        public abstract int slots();

        public abstract ItemStack get(int slot);

        // Whether a schematic can go in that slot.
        public abstract boolean accepts(int slot, ItemStack stack);

        public abstract void set(int slot, ItemStack stack);

        // What Locate outlines, in world coordinates.
        public AABB box(MinecraftServer server) {
            BlockPos at = pos.pos();
            if (rackDevice != null && rackDevice.rack() != null) {
                RackBlockEntity rack = rackDevice.rack();
                Direction facing = rack.getBlockState().getValue(ServerRackBlock.FACING);
                return RackGeometry.toWorld(RackGeometry.deviceBox(rackDevice.u(), rackDevice.size()), rack.getBlockPos(), facing);
            }
            BlockEntity entity = ControllerStructures.blockEntity(server, pos);
            if (entity != null && entity.getLevel() != null) {
                VoxelShape shape = entity.getBlockState().getShape(entity.getLevel(), at);
                if (!shape.isEmpty()) {
                    return shape.bounds().move(at);
                }
            }
            return new AABB(at);
        }

        public List<Schematic> schematics() {
            List<Schematic> list = new ArrayList<>();
            for (int slot = 0; slot < slots(); slot++) {
                Schematic schematic = SchematicItem.schematic(get(slot));
                if (schematic != null) {
                    list.add(schematic);
                }
            }
            return list;
        }

        // Whether it holds a schematic (the step tree's Locate).
        public boolean holds(Schematic schematic) {
            return schematics().contains(schematic);
        }
    }

    private static final class Fabricator extends Holder {
        private final FabricatorBlockEntity fabricator;

        Fabricator(String name, NodePos pos, FabricatorBlockEntity fabricator) {
            super(name, at(pos), pos, null, fabricator, "");
            this.fabricator = fabricator;
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(fabricator.getBlockState().getBlock());
        }

        @Override
        public int kind() {
            return SchematicData.FABRICATOR;
        }

        @Override
        public int slots() {
            return FabricatorBlockEntity.SCHEMATIC_SLOTS;
        }

        @Override
        public ItemStack get(int slot) {
            return fabricator.getItem(slot);
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            return fabricator.canPlaceItem(slot, stack);
        }

        @Override
        public void set(int slot, ItemStack stack) {
            fabricator.setItem(slot, stack);
            fabricator.setChanged();
        }
    }

    private static final class Gateway extends Holder {
        private final GatewayBlockEntity gateway;
        private final boolean bridged;

        Gateway(String name, NodePos pos, GatewayBlockEntity gateway, String deviceName, boolean bridged) {
            super(name, at(pos), pos, null, gateway, deviceName);
            this.gateway = gateway;
            this.bridged = bridged;
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(gateway.getBlockState().getBlock());
        }

        // A Gateway feeding only a bridged machine (an Arcforge machine over a Small Wireless Bridge) counts as a machine.
        @Override
        public int kind() {
            return bridged ? SchematicData.MACHINE : SchematicData.GATEWAY;
        }

        @Override
        public int slots() {
            return GatewayBlockEntity.SCHEMATIC_SLOTS;
        }

        @Override
        public ItemStack get(int slot) {
            return gateway.schematicSlots().get(slot);
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            return GatewayBlockEntity.acceptsSchematic(stack);
        }

        @Override
        public void set(int slot, ItemStack stack) {
            gateway.schematicSlots().set(slot, stack);
            gateway.settingsChanged();
        }
    }

    private static final class Server extends Holder {
        private final FabricationServerDevice server;

        Server(String name, String location, NodePos pos, FabricationServerDevice server) {
            super(name, location, pos, server, server, name);
            this.server = server;
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(server.type().item());
        }

        @Override
        public int kind() {
            return SchematicData.SERVER;
        }

        @Override
        public int slots() {
            return FabricationServerDevice.SCHEMATIC_SLOTS;
        }

        @Override
        public ItemStack get(int slot) {
            return server.items().get(slot);
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            return stack.is(ModItems.ENCODED_SCHEMATIC_CRAFTING.get());
        }

        @Override
        public void set(int slot, ItemStack stack) {
            server.items().set(slot, stack);
            server.itemsChanged();
        }
    }

    // A Midrange System: its diskettes' recipes, shown as schematics, read-only.
    private static final class Midrange extends Holder {
        private final MidrangeSystemBlockEntity midrange;
        private final List<ItemStack> recipes = new ArrayList<>();

        Midrange(String name, NodePos pos, MidrangeSystemBlockEntity midrange) {
            super(name, at(pos), pos, null, midrange, name);
            this.midrange = midrange;
            for (Schematic recipe : midrange.recipes()) {
                recipes.add(stack(recipe));
            }
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(midrange.getBlockState().getBlock());
        }

        @Override
        public int kind() {
            return SchematicData.MIDRANGE;
        }

        @Override
        public boolean readOnly() {
            return true;
        }

        @Override
        public int slots() {
            return recipes.size();
        }

        @Override
        public ItemStack get(int slot) {
            return recipes.get(slot);
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            return false;
        }

        @Override
        public void set(int slot, ItemStack stack) {}
    }

    // Any other provider: its schematics, read-only.
    private static final class Other extends Holder {
        private final List<ItemStack> held = new ArrayList<>();
        private final ItemStack icon;

        Other(String name, String location, NodePos pos, @Nullable RackDevice device, CraftingProvider provider, ItemStack icon) {
            super(name, location, pos, device, provider, "");
            this.icon = icon;
            provider.schematics().forEach(schematic -> held.add(stack(schematic)));
        }

        @Override
        public ItemStack icon() {
            return icon;
        }

        @Override
        public int kind() {
            return SchematicData.MACHINE;
        }

        @Override
        public boolean readOnly() {
            return true;
        }

        @Override
        public int slots() {
            return held.size();
        }

        @Override
        public ItemStack get(int slot) {
            return held.get(slot);
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            return false;
        }

        @Override
        public void set(int slot, ItemStack stack) {}
    }

    private SchematicHolders() {}

    // An encoded schematic item for a schematic (a Midrange recipe, shown as one).
    public static ItemStack stack(Schematic schematic) {
        ItemStack stack = new ItemStack(schematic.kind() == Schematic.Kind.PROCESSING ? ModItems.ENCODED_SCHEMATIC_PROCESSING.get()
                : ModItems.ENCODED_SCHEMATIC_CRAFTING.get());
        stack.set(ModDataComponents.SCHEMATIC.get(), schematic);
        return stack;
    }

    private static String at(NodePos pos) {
        BlockPos at = pos.pos();
        return at.getX() + " " + at.getY() + " " + at.getZ();
    }

    // The network's holders.
    public static List<Holder> of(MinecraftServer server, @Nullable NetworkRef network) {
        List<Holder> holders = new ArrayList<>();
        if (network == null) {
            return holders;
        }
        List<ElclDevices.Device> devices = ElclDevices.list(server, network);
        List<RackBlockEntity> racks = ControllerStructures.racks(server, network);
        int fabricators = 0;
        for (CraftingProvider provider : ControllerStructures.allProvidersOf(server, network)) {
            if (provider instanceof RackDevice device && device.rack() != null) {
                RackBlockEntity rack = device.rack();
                NodePos pos = NodePos.of(rack.getLevel().dimension(), rack.getBlockPos());
                int rackNumber = racks.indexOf(rack) + 1;
                String location = String.format("RACK%02d U%d", Math.max(1, rackNumber), device.u());
                String name = ElclDevices.nameAt(devices, pos, device);
                if (device instanceof FabricationServerDevice server1) {
                    holders.add(new Server(name.isEmpty() ? "FABSRV" : name, location, pos, server1));
                } else {
                    holders.add(new Other(name.isEmpty() ? ElclDevices.code(device) : name, location, pos, device, provider,
                            new ItemStack(device.type().item())));
                }
                continue;
            }
            if (!(provider instanceof BlockEntity entity) || !(entity.getLevel() instanceof ServerLevel level)) {
                continue;
            }
            NodePos pos = NodePos.of(level.dimension(), entity.getBlockPos());
            String name = ElclDevices.nameAt(devices, pos, null);
            if (provider instanceof FabricatorBlockEntity fabricator) {
                holders.add(new Fabricator(String.format("FAB%02d", ++fabricators), pos, fabricator));
            } else if (provider instanceof GatewayBlockEntity gateway) {
                String device = name.isEmpty() ? gateway.deviceName() : name;
                String machine = gateway.machineName(level);
                boolean bridged = !MachineBridges.gatewayMachines(level, entity.getBlockPos()).isEmpty();
                boolean neighbour = false;
                for (Direction side : Direction.values()) {
                    neighbour |= level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, entity.getBlockPos().relative(side),
                            side.getOpposite()) != null && !(level.getBlockEntity(entity.getBlockPos().relative(side)) instanceof GatewayBlockEntity);
                }
                String shown = (device.isEmpty() ? GatewayBlockEntity.TYPE : device) + (machine.isEmpty() ? "" : " > " + machine);
                holders.add(new Gateway(shown, pos, gateway, device, bridged && !neighbour));
            } else if (provider instanceof MidrangeSystemBlockEntity midrange) {
                holders.add(new Midrange(name.isEmpty() ? MidrangeSystemBlockEntity.TYPE : name, pos, midrange));
            } else {
                holders.add(new Other(name.isEmpty() ? entity.getBlockState().getBlock().getName().getString() : name, at(pos), pos, null, provider,
                        new ItemStack(entity.getBlockState().getBlock())));
            }
        }
        return holders;
    }

    public static @Nullable Holder find(List<Holder> holders, String key) {
        for (Holder holder : holders) {
            if (holder.key().equals(key)) {
                return holder;
            }
        }
        return null;
    }

    // The holders as the terminal shows them, with their badges: parallel (another holder makes one of its outputs too),
    // conflict (the network has different recipes for one of its outputs: the one the planner uses named) and a Gateway
    // whose machine is missing or takes none of a schematic's inputs.
    public static List<SchematicData.Holder> views(MinecraftServer server, @Nullable NetworkRef network, List<Holder> holders) {
        List<Set<StorageKey>> outputs = new ArrayList<>();
        for (Holder holder : holders) {
            Set<StorageKey> made = new LinkedHashSet<>();
            holder.schematics().forEach(schematic -> made.addAll(schematic.outputTotals().keySet()));
            outputs.add(made);
        }
        Map<StorageKey, List<Schematic>> recipes = CraftPlanner.byOutput(CraftRequests.schematics(server, network));
        List<SchematicData.Holder> views = new ArrayList<>(holders.size());
        for (int i = 0; i < holders.size(); i++) {
            Holder holder = holders.get(i);
            int badges = 0, parallel = 0;
            for (int j = 0; j < holders.size(); j++) {
                if (j != i && outputs.get(j).stream().anyMatch(outputs.get(i)::contains)) {
                    parallel++;
                }
            }
            if (parallel > 0) {
                badges |= SchematicData.PARALLEL;
            }
            String conflictItem = "", conflictPreferred = "";
            for (StorageKey output : outputs.get(i)) {
                List<Schematic> options = recipes.get(output);
                if (options != null && new HashSet<>(options).size() > 1) {
                    badges |= SchematicData.CONFLICT;
                    conflictItem = output.displayName().getString();
                    conflictPreferred = holderOf(holders, options.getFirst());
                    break;
                }
            }
            if (holder.provider() instanceof GatewayBlockEntity gateway && gateway.getLevel() instanceof ServerLevel level && gateway.machineProblem(level)) {
                badges |= SchematicData.GATEWAY_PROBLEM;
            }
            List<ItemStack> slots = new ArrayList<>(holder.slots());
            for (int slot = 0; slot < holder.slots(); slot++) {
                slots.add(holder.get(slot).copy());
            }
            views.add(new SchematicData.Holder(holder.key(), holder.name(), holder.location(), holder.icon(), holder.kind(), holder.readOnly(),
                    !holder.deviceName().isEmpty(), slots, badges, parallel, conflictItem, conflictPreferred));
        }
        return views;
    }

    // The name of the first holder with a schematic, or "a recipe library".
    private static String holderOf(List<Holder> holders, Schematic schematic) {
        for (Holder holder : holders) {
            if (holder.holds(schematic)) {
                return holder.name();
            }
        }
        return net.minecraft.network.chat.Component.translatable("gui.encodedlogistics.schematic.recipe_library").getString();
    }
}
