/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.arcforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.api.machine.MachineCapabilities;
import net.zagdrath.arcforge.api.machine.MachineControl;
import net.zagdrath.arcforge.api.machine.MachineOwner;
import net.zagdrath.arcforge.api.machine.resource.MachineFluids;
import net.zagdrath.arcforge.api.machine.settings.MachineSettings;
import net.zagdrath.arcforge.api.machine.settings.MachineSide;
import net.zagdrath.arcforge.api.machine.settings.RedstoneMode;
import net.zagdrath.arcforge.api.machine.settings.SettingResult;
import net.zagdrath.arcforge.api.machine.status.CompletedOperation;
import net.zagdrath.arcforge.api.machine.status.MachineListener;
import net.zagdrath.arcforge.api.machine.status.MachineStatistics;
import net.zagdrath.arcforge.api.machine.status.MachineStatus;
import net.zagdrath.encodedlogistics.machine.MachineAccess;
import net.zagdrath.encodedlogistics.machine.MachineInfo;

// Arcforge's machines through its machine control API (Arcforge's docs/API.md): the MachineAccess behind the Small
// Wireless Bridge. Loaded only by ArcforgeCompat, once Arcforge and a compatible API are there. A bridged machine is
// followed with a capability cache, so a machine unloaded and loaded again, or a multiblock re-formed, is found again
// and its listener moved to the new instance.
final class ArcforgeMachines implements MachineAccess {
    // The machines being watched, by level and block.
    private final Map<ServerLevel, Map<BlockPos, Watched>> watched = new WeakHashMap<>();

    // A watched machine: its cache, and the instance its listener is on.
    private static final class Watched implements MachineListener {
        final BlockCapabilityCache<MachineControl, @Nullable Void> cache;
        @Nullable MachineControl listening;
        @Nullable Listener listener;

        Watched(ServerLevel level, BlockPos pos) {
            cache = BlockCapabilityCache.create(MachineCapabilities.MACHINE_CONTROL, level, pos, null);
        }

        @Override
        public void onStatusChanged(MachineControl machine, MachineStatus previous, MachineStatus current) {
            if (listener != null) {
                listener.statusChanged(state(previous), state(current), machine.statusReason());
            }
        }

        @Override
        public void onOperationCompleted(MachineControl machine, CompletedOperation operation) {
            if (listener != null) {
                listener.operationCompleted(operation.itemsProduced());
            }
        }
    }

    ArcforgeMachines() {}

    private @Nullable MachineControl control(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Watched> here = watched.get(level);
        Watched watching = here != null ? here.get(pos) : null;
        MachineControl machine = watching != null ? watching.cache.getCapability() : level.getCapability(MachineCapabilities.MACHINE_CONTROL, pos, null);
        return machine != null && machine.isValid() ? machine : null;
    }

    @Override
    public boolean isMachine(ServerLevel level, BlockPos pos) {
        return control(level, pos) != null;
    }

    @Override
    public @Nullable MachineInfo info(ServerLevel level, BlockPos pos) {
        MachineControl machine = control(level, pos);
        if (machine == null) {
            return null;
        }
        MachineStatistics stats = machine.statistics();
        return new MachineInfo(machine.machineType().toString(), machine.displayName(), machine.isMultiblock(), machine.isFormed(),
                machine.position(), machine.owner().map(MachineOwner::id), state(machine.status()), machine.statusReason(), machine.progress(),
                machine.ticksRemaining(), machine.currentOutputs(),
                machine.energy().map(energy -> new MachineInfo.Energy(lower(energy.role().name()), energy.stored(), energy.capacity(), energy.perTick())),
                machine.heat().map(heat -> new MachineInfo.Heat(lower(heat.role().name()), heat.stored(), heat.capacity(), heat.temperature(),
                        heat.maxTemperature(), heat.perTick())),
                machine.fluids().map(ArcforgeMachines::tanks).orElse(List.of()),
                new MachineInfo.Statistics(stats.operationsCompleted(), stats.itemsProduced(), stats.itemsConsumed(), stats.fluidProduced(),
                        stats.fluidConsumed(), stats.uptimeTicks(), stats.loadedTicks(), stats.operationsPerMinute()),
                settings(machine.settings()));
    }

    private static List<MachineInfo.Tank> tanks(MachineFluids fluids) {
        List<MachineInfo.Tank> tanks = new ArrayList<>(fluids.tankCount());
        for (int tank = 0; tank < fluids.tankCount(); tank++) {
            FluidStack fluid = fluids.fluid(tank);
            tanks.add(new MachineInfo.Tank(lower(fluids.role(tank).name()), fluid.isEmpty() ? Component.empty() : fluid.getHoverName(), fluid.getAmount(),
                    fluids.capacity(tank)));
        }
        return tanks;
    }

    private static MachineInfo.Settings settings(MachineSettings settings) {
        List<String> redstoneModes = new ArrayList<>();
        settings.allowedRedstoneModes().forEach(mode -> redstoneModes.add(lower(mode.name())));
        Map<String, String> sides = new LinkedHashMap<>();
        if (settings.hasSideConfiguration()) {
            for (MachineSide side : MachineSide.values()) {
                sides.put(lower(side.name()), settings.sideMode(side));
            }
        }
        return new MachineInfo.Settings(settings.isEnabled(), settings.redstoneMode().map(mode -> lower(mode.name())), List.copyOf(redstoneModes),
                sides, List.copyOf(settings.allowedSideModes()), settings.ports().size(), settings.supportsAutoEject(), settings.isAutoEject());
    }

    static MachineInfo.State state(MachineStatus status) {
        return switch (status) {
            case IDLE -> MachineInfo.State.IDLE;
            case RUNNING -> MachineInfo.State.RUNNING;
            case NO_POWER -> MachineInfo.State.NO_POWER;
            case NO_INPUT -> MachineInfo.State.NO_INPUT;
            case OUTPUT_BLOCKED -> MachineInfo.State.OUTPUT_BLOCKED;
            case DISABLED -> MachineInfo.State.DISABLED;
            case NOT_FORMED -> MachineInfo.State.NOT_FORMED;
            case FAULT -> MachineInfo.State.FAULT;
            // One added to the API after 1.0.
            default -> MachineInfo.State.UNKNOWN;
        };
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // --- Settings ---

    private interface Change {
        SettingResult apply(MachineSettings settings);
    }

    private Result change(ServerLevel level, BlockPos pos, Change change) {
        MachineControl machine = control(level, pos);
        if (machine == null) {
            return Result.NO_MACHINE;
        }
        if (!machine.isFormed()) {
            return Result.NOT_FORMED;
        }
        return switch (change.apply(machine.settings())) {
            case APPLIED -> Result.APPLIED;
            case UNCHANGED -> Result.UNCHANGED;
            case UNSUPPORTED -> Result.UNSUPPORTED;
            case INVALID -> Result.INVALID;
            case REJECTED -> Result.REJECTED;
            default -> Result.REJECTED;
        };
    }

    @Override
    public Result setEnabled(ServerLevel level, BlockPos pos, boolean enabled) {
        return change(level, pos, settings -> settings.setEnabled(enabled));
    }

    @Override
    public Result setRedstoneMode(ServerLevel level, BlockPos pos, String mode) {
        RedstoneMode value = parse(RedstoneMode.class, mode);
        return value == null ? Result.INVALID : change(level, pos, settings -> settings.setRedstoneMode(value));
    }

    @Override
    public Result setSideMode(ServerLevel level, BlockPos pos, String side, String mode) {
        MachineSide value = parse(MachineSide.class, side);
        return value == null ? Result.INVALID : change(level, pos, settings -> settings.setSideMode(value, mode.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Result setAutoEject(ServerLevel level, BlockPos pos, boolean autoEject) {
        return change(level, pos, settings -> settings.setAutoEject(autoEject));
    }

    private static <E extends Enum<E>> @Nullable E parse(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // --- Items ---

    @Override
    public @Nullable ResourceHandler<ItemResource> items(ServerLevel level, BlockPos pos) {
        MachineControl machine = control(level, pos);
        return machine == null ? null : machine.items().map(items -> items.handler()).orElse(null);
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> fluids(ServerLevel level, BlockPos pos) {
        MachineControl machine = control(level, pos);
        return machine == null ? null : machine.fluids().map(fluids -> fluids.handler()).orElse(null);
    }

    // --- Events ---

    @Override
    public void watch(ServerLevel level, BlockPos pos, Listener listener) {
        Watched watching = watched.computeIfAbsent(level, key -> new HashMap<>()).computeIfAbsent(pos.immutable(), key -> new Watched(level, key));
        watching.listener = listener;
        MachineControl machine = watching.cache.getCapability();
        if (machine != null && !machine.isValid()) {
            machine = null;
        }
        if (machine != watching.listening) {
            if (watching.listening != null) {
                watching.listening.removeListener(watching);
            }
            if (machine != null) {
                machine.addListener(watching);
            }
            watching.listening = machine;
        }
    }

    @Override
    public void unwatch(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Watched> here = watched.get(level);
        Watched watching = here != null ? here.remove(pos) : null;
        if (watching != null && watching.listening != null) {
            watching.listening.removeListener(watching);
        }
    }
}
