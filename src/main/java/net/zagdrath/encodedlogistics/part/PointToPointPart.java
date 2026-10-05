/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.menu.PointToPointMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex;
import net.zagdrath.encodedlogistics.network.NetworkGraph;
import net.zagdrath.encodedlogistics.network.RemoteLink;
import net.zagdrath.encodedlogistics.storage.ResourceIO;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// A Point-to-Point Link endpoint: an input paired (Link Card) with one or more outputs, passing one thing straight
// across, bypassing storage. Items: every ITEM_OPERATION ticks up to p2pItemsPerOperation items from the inventory the
// input faces into the ones its outputs face, round-robin. Energy: up to p2pEnergyPerTick FE a tick, the same way.
// Redstone: the outputs send out the signal the input reads from the block it faces. Lanes: the outputs' cables get
// p2pLanes lanes (split evenly between them) from the input's network, as if a cable ran there (a remote link). Fluids
// and pressurized gases: every ITEM_OPERATION ticks up to p2pFluidPerOperation mB from the tanks the input faces into the
// outputs' (ResourceIO), round-robin. Items, fluids, gases, energy and redstone only cross while both ends are on the same
// running network.
//
// What it carries and which way are set in its screen, and locked while it's paired. Lit (its ring glows) while
// paired. Every CHECK_INTERVAL ticks it drops partners that no longer point back (broken or re-paired while this end was
// unloaded). Endpoints use no lanes; they drain p2pDrain FE/t.
public class PointToPointPart extends CablePart {
    public static final int ITEM_OPERATION = 10, CHECK_INTERVAL = 40;

    // Another endpoint: the block it's on and its side (same dimension).
    public record Endpoint(BlockPos pos, Direction side) {
        public static final Codec<Endpoint> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Endpoint::pos),
                Direction.CODEC.fieldOf("side").forGetter(Endpoint::side))
                .apply(i, Endpoint::new));
    }

    // Why two endpoints couldn't be paired.
    public enum PairResult {
        PAIRED, SAME_ENDPOINT, OTHER_TYPE, SAME_DIRECTION
    }

    private LinkType linkType = LinkType.ITEMS;
    private boolean output;
    private final List<Endpoint> partners = new ArrayList<>();
    private int timer, roundRobin, signal;

    public PointToPointPart(PartType type, CableBlockEntity host, Direction side) {
        super(type, host, side);
    }

    public LinkType linkType() {
        return linkType;
    }

    public boolean output() {
        return output;
    }

    public boolean paired() {
        return !partners.isEmpty();
    }

    public List<Endpoint> partners() {
        return List.copyOf(partners);
    }

    public Endpoint self() {
        return new Endpoint(host.getBlockPos(), side);
    }

    // The endpoint on that side of the block at pos, or null.
    public static @Nullable PointToPointPart at(Level level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof CableBlockEntity host && host.part(side) instanceof PointToPointPart part ? part : null;
    }

    // --- Settings (unpaired only) ---

    public void setLinkType(LinkType linkType) {
        if (!paired() && this.linkType != linkType) {
            this.linkType = linkType;
            changed();
        }
    }

    public void toggleDirection() {
        if (!paired()) {
            output = !output;
            changed();
        }
    }

    // --- Pairing ---

    // Pairs an input with an output (in either order). An output already paired leaves its old input; an input keeps
    // its other outputs.
    public static PairResult pair(PointToPointPart a, PointToPointPart b) {
        if (a == b) {
            return PairResult.SAME_ENDPOINT;
        }
        if (a.linkType != b.linkType) {
            return PairResult.OTHER_TYPE;
        }
        if (a.output == b.output) {
            return PairResult.SAME_DIRECTION;
        }
        PointToPointPart input = a.output ? b : a, output = a.output ? a : b;
        if (!output.partners.isEmpty() && !output.partners.getFirst().equals(input.self())) {
            output.unpairAll();
        }
        if (!input.partners.contains(output.self())) {
            input.partners.add(output.self());
            input.linksChanged();
        }
        if (!output.partners.contains(input.self())) {
            output.partners.clear();
            output.partners.add(input.self());
            output.linksChanged();
        }
        return PairResult.PAIRED;
    }

    // Unpairs from every partner (the loaded ones forget this end; the rest do when they next check).
    public void unpairAll() {
        if (host.getLevel() == null) {
            return;
        }
        for (Endpoint partner : List.copyOf(partners)) {
            if (host.getLevel().isLoaded(partner.pos())) {
                PointToPointPart other = at(host.getLevel(), partner.pos(), partner.side());
                if (other != null) {
                    other.removePartner(self());
                }
            }
        }
        if (!partners.isEmpty()) {
            partners.clear();
            linksChanged();
        }
    }

    private void removePartner(Endpoint endpoint) {
        if (partners.remove(endpoint)) {
            linksChanged();
        }
    }

    // Pairing changed: save, relight, re-solve the network (lanes) and drop a signal no longer fed.
    private void linksChanged() {
        if (partners.isEmpty() && signal != 0) {
            signal = 0;
            host.signalChanged(side);
        }
        changed();
        if (host.getLevel() instanceof ServerLevel level) {
            ControllerStructures.get(level).markTopologyChanged();
        }
    }

    @Override
    public void removed(ServerLevel level) {
        unpairAll();
    }

    // --- Network ---

    @Override
    public List<RemoteLink> remoteLinks(ResourceKey<Level> dimension) {
        if (linkType != LinkType.LANES || partners.isEmpty()) {
            return List.of();
        }
        int lanes = Config.P2P_LANES.getAsInt();
        List<RemoteLink> links = new ArrayList<>(partners.size());
        for (Endpoint partner : partners) {
            links.add(new RemoteLink(NetworkGraph.at(dimension, partner.pos()), output ? lanes : Math.max(1, lanes / partners.size())));
        }
        return links;
    }

    // --- Look ---

    // Which of its eight models: what it carries, in or out.
    @Override
    public int look() {
        return linkType.ordinal() * 2 + (output ? 1 : 0);
    }

    @Override
    public boolean lit() {
        return paired();
    }

    @Override
    public int signal() {
        return output ? signal : 0;
    }

    @Override
    public boolean emitsRedstone() {
        return output && linkType == LinkType.REDSTONE;
    }

    @Override
    public boolean openMenu(ServerPlayer player) {
        PointToPointMenu.open(player, this);
        return true;
    }

    // --- Working ---

    @Override
    public void tick(ServerLevel level) {
        timer++;
        if (timer % CHECK_INTERVAL == 0) {
            check(level);
        }
        if (output || partners.isEmpty()) {
            return;
        }
        switch (linkType) {
            case ITEMS -> {
                if (timer % ITEM_OPERATION == 0) {
                    moveItems(level);
                }
            }
            case FLUIDS, PRESSURIZED -> {
                if (timer % ITEM_OPERATION == 0) {
                    moveFluids(level, linkType == LinkType.FLUIDS ? ResourceType.FLUID : ResourceType.PRESSURIZED);
                }
            }
            case ENERGY -> moveEnergy(level);
            case REDSTONE -> sendSignal(level);
            case LANES -> {}
        }
    }

    // Drops partners that are loaded but no longer an endpoint pointing back.
    private void check(ServerLevel level) {
        boolean dropped = false;
        for (Endpoint partner : List.copyOf(partners)) {
            if (!level.isLoaded(partner.pos())) {
                continue;
            }
            PointToPointPart other = at(level, partner.pos(), partner.side());
            if (other == null || other.linkType != linkType || other.output == output || !other.partners.contains(self())) {
                partners.remove(partner);
                dropped = true;
            }
        }
        if (dropped) {
            linksChanged();
        }
    }

    // The outputs that are loaded, on the same running network as this input.
    private List<PointToPointPart> liveOutputs(ServerLevel level) {
        List<PointToPointPart> outputs = new ArrayList<>();
        NetworkIndex.NetworkRef network = ControllerStructures.networkOf(level, host.getBlockPos());
        if (!ControllerStructures.isOnline(level.getServer(), network)) {
            return outputs;
        }
        for (Endpoint partner : partners) {
            if (level.isLoaded(partner.pos()) && network.equals(ControllerStructures.networkOf(level, partner.pos()))) {
                PointToPointPart other = at(level, partner.pos(), partner.side());
                if (other != null) {
                    outputs.add(other);
                }
            }
        }
        return outputs;
    }

    private void moveItems(ServerLevel level) {
        List<PointToPointPart> outputs = liveOutputs(level);
        ResourceHandler<ItemResource> source = level.getCapability(Capabilities.Item.BLOCK, facing(), side.getOpposite());
        if (outputs.isEmpty() || source == null) {
            return;
        }
        int left = Config.P2P_ITEMS_PER_OPERATION.getAsInt();
        for (int slot = 0; slot < source.size() && left > 0; slot++) {
            ItemResource resource = source.getResource(slot);
            if (resource.isEmpty()) {
                continue;
            }
            for (int tried = 0; tried < outputs.size() && left > 0; tried++) {
                PointToPointPart out = outputs.get(Math.floorMod(roundRobin + tried, outputs.size()));
                ResourceHandler<ItemResource> target = level.getCapability(Capabilities.Item.BLOCK, out.facing(), out.side.getOpposite());
                if (target == null) {
                    continue;
                }
                int amount = Math.min(left, source.getAmountAsInt(slot));
                int room;
                try (Transaction transaction = Transaction.openRoot()) {
                    room = target.insert(resource, amount, transaction);
                }
                if (room <= 0) {
                    continue;
                }
                try (Transaction transaction = Transaction.openRoot()) {
                    int taken = source.extract(slot, resource, room, transaction);
                    int put = target.insert(resource, taken, transaction);
                    if (taken > 0 && put == taken) {
                        transaction.commit();
                        left -= put;
                        roundRobin = Math.floorMod(roundRobin + tried + 1, outputs.size());
                    }
                }
                if (source.getResource(slot).isEmpty()) {
                    break;
                }
            }
        }
    }

    private void moveFluids(ServerLevel level, ResourceType type) {
        List<PointToPointPart> outputs = liveOutputs(level);
        ResourceIO source = ResourceIO.at(level, facing(), side.getOpposite(), type);
        if (outputs.isEmpty() || source == null) {
            return;
        }
        long left = Config.P2P_FLUID_PER_OPERATION.getAsInt();
        for (Map.Entry<StorageKey, Long> there : source.list().entrySet()) {
            StorageKey key = there.getKey();
            for (int tried = 0; tried < outputs.size() && left > 0; tried++) {
                PointToPointPart out = outputs.get(Math.floorMod(roundRobin + tried, outputs.size()));
                ResourceIO target = ResourceIO.at(level, out.facing(), out.side.getOpposite(), type);
                long room = target != null ? target.insert(key, Math.min(left, there.getValue()), true) : 0;
                long taken = room > 0 ? source.extract(key, room, false) : 0;
                if (taken <= 0) {
                    continue;
                }
                long put = target.insert(key, taken, false);
                if (put < taken) {
                    source.insert(key, taken - put, false);
                }
                left -= put;
                roundRobin = Math.floorMod(roundRobin + tried + 1, outputs.size());
            }
            if (left <= 0) {
                break;
            }
        }
    }

    private void moveEnergy(ServerLevel level) {
        List<PointToPointPart> outputs = liveOutputs(level);
        EnergyHandler source = level.getCapability(Capabilities.Energy.BLOCK, facing(), side.getOpposite());
        if (outputs.isEmpty() || source == null) {
            return;
        }
        int left = Config.P2P_ENERGY_PER_TICK.getAsInt();
        for (int tried = 0; tried < outputs.size() && left > 0; tried++) {
            PointToPointPart out = outputs.get(Math.floorMod(roundRobin + tried, outputs.size()));
            EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, out.facing(), out.side.getOpposite());
            if (target == null) {
                continue;
            }
            int room;
            try (Transaction transaction = Transaction.openRoot()) {
                room = target.insert(left, transaction);
            }
            if (room <= 0) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int taken = source.extract(room, transaction);
                int put = target.insert(taken, transaction);
                if (taken > 0 && put == taken) {
                    transaction.commit();
                    left -= put;
                }
            }
        }
        roundRobin = Math.floorMod(roundRobin + 1, outputs.size());
    }

    // Outputs on the same running network send what this input reads; the rest send nothing.
    private void sendSignal(ServerLevel level) {
        int read = level.getSignal(facing(), side);
        List<PointToPointPart> live = liveOutputs(level);
        for (Endpoint partner : partners) {
            if (!level.isLoaded(partner.pos())) {
                continue;
            }
            PointToPointPart other = at(level, partner.pos(), partner.side());
            if (other != null) {
                other.setSignal(live.contains(other) ? read : 0);
            }
        }
    }

    private void setSignal(int value) {
        if (signal != value) {
            signal = value;
            host.setChanged();
            host.signalChanged(side);
        }
    }

    // --- Saving ---

    @Override
    public void load(ValueInput input) {
        linkType = input.read("link_type", LinkType.CODEC).orElse(LinkType.ITEMS);
        output = input.getBooleanOr("output", false);
        partners.clear();
        partners.addAll(input.read("partners", Endpoint.CODEC.listOf()).orElse(List.of()));
        signal = input.getIntOr("signal", 0);
    }

    @Override
    public void save(ValueOutput output) {
        output.store("link_type", LinkType.CODEC, linkType);
        output.putBoolean("output", this.output);
        if (!partners.isEmpty()) {
            output.store("partners", Endpoint.CODEC.listOf(), List.copyOf(partners));
        }
        if (signal != 0) {
            output.putInt("signal", signal);
        }
    }
}
