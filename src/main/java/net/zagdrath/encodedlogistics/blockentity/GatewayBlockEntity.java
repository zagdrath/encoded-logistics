/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.encodedlogistics.block.GatewayBlock;
import net.zagdrath.encodedlogistics.crafting.CraftTask;
import net.zagdrath.encodedlogistics.crafting.CraftingProvider;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.item.SchematicItem;
import net.zagdrath.encodedlogistics.menu.GatewayMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// A Gateway's nine Processing Schematics, its stock settings (nine ghost items, each with the amount to keep) and its
// item handler (every face): nine buffer slots holding the stocked items, which neighbours can take but not fill, and
// nine intake slots that take only the outputs its runs are waiting for.
//
// A run (one per schematic use, offered by a Scheduler while the previous run's inputs are all in a machine) pushes its
// inputs into whichever neighbours accept them (through the face touching the Gateway), then waits for its outputs:
// machines pushing them in, or the Gateway pulling them out of its neighbours (every PULL_INTERVAL ticks, through any
// face). Outputs go back to the job as they arrive; the run is
// done once they all have. Every STOCK_INTERVAL ticks each buffer slot is topped up from (or trimmed back into) the
// network to its stock amount.
public class GatewayBlockEntity extends BlockEntity implements MenuProvider, NetworkDevice, CraftingProvider {
    public static final int SCHEMATIC_SLOTS = 9, STOCK_SLOTS = 9, BUFFER = 9, INTAKE = 9;
    private static final int MAX_RUNS = 64, PULL_INTERVAL = 10, STOCK_INTERVAL = 20, ACTIVE_TICKS = 20;

    // A run: its task, the inputs not yet pushed, and the outputs still expected.
    private static final class Run {
        private record Expected(ItemKey key, long count) {
            static final Codec<Expected> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ItemKey.CODEC.fieldOf("item").forGetter(Expected::key),
                    Codec.LONG.fieldOf("count").forGetter(Expected::count))
                    .apply(i, Expected::new));
        }

        static final Codec<Run> CODEC = RecordCodecBuilder.create(i -> i.group(
                CraftTask.CODEC.fieldOf("task").forGetter(run -> run.task),
                ItemStack.CODEC.listOf().fieldOf("to_push").forGetter(run -> run.toPush),
                Expected.CODEC.listOf().fieldOf("expected").forGetter(run -> run.expected.entrySet().stream()
                        .map(e -> new Expected(e.getKey(), e.getValue())).toList()))
                .apply(i, (task, toPush, expected) -> {
                    Run run = new Run(task, toPush);
                    run.expected.clear();
                    expected.forEach(e -> run.expected.put(e.key(), e.count()));
                    return run;
                }));

        final CraftTask task;
        final List<ItemStack> toPush = new ArrayList<>();
        final Map<ItemKey, Long> expected = new LinkedHashMap<>();

        Run(CraftTask task, List<ItemStack> toPush) {
            this.task = task;
            toPush.forEach(stack -> this.toPush.add(stack.copy()));
            expected.putAll(task.schematic().outputTotals());
        }
    }

    private final NonNullList<ItemStack> schematics = NonNullList.withSize(SCHEMATIC_SLOTS, ItemStack.EMPTY);
    private final NonNullList<ItemStack> stock = NonNullList.withSize(STOCK_SLOTS, ItemStack.EMPTY);
    private final Handler handler = new Handler();
    private final List<Run> runs = new ArrayList<>();
    private boolean online;
    private long lastMoved = Long.MIN_VALUE / 2;
    private int timer;

    public GatewayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.GATEWAY.get(), pos, state);
    }

    @Override
    public void setNetworkOnline(boolean online) {
        this.online = online;
    }

    public NonNullList<ItemStack> schematicSlots() {
        return schematics;
    }

    public NonNullList<ItemStack> stock() {
        return stock;
    }

    public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        return handler;
    }

    // The buffer slots as a read-only Container for the screen.
    public Container bufferView() {
        return new BufferView();
    }

    public void settingsChanged() {
        setChanged();
    }

    // --- Runs ---

    @Override
    public List<Schematic> schematics() {
        List<Schematic> list = new ArrayList<>();
        for (ItemStack stack : schematics) {
            Schematic schematic = SchematicItem.schematic(stack);
            if (schematic != null && schematic.kind() == Schematic.Kind.PROCESSING) {
                list.add(schematic);
            }
        }
        return list;
    }

    @Override
    public boolean offer(ServerLevel level, CraftTask task) {
        if (!online || task.schematic().kind() != Schematic.Kind.PROCESSING || runs.size() >= MAX_RUNS || !schematics().contains(task.schematic())) {
            return false;
        }
        for (Run run : runs) {
            if (!run.toPush.isEmpty()) {
                return false;
            }
        }
        runs.add(new Run(task, task.inputs()));
        setChanged();
        return true;
    }

    public void serverTick(ServerLevel level) {
        timer++;
        boolean moved = false;
        if (online) {
            moved |= push(level);
            moved |= takeIntake(level);
            if (timer % PULL_INTERVAL == 0) {
                moved |= pull(level);
                dropOrphans(level);
            }
            if (timer % STOCK_INTERVAL == 0) {
                moved |= restock(level);
            }
        }
        if (moved) {
            lastMoved = level.getGameTime();
            setChanged();
        }
        boolean active = level.getGameTime() - lastMoved < ACTIVE_TICKS;
        if (getBlockState().getValue(GatewayBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, getBlockState().setValue(GatewayBlock.ACTIVE, active), Block.UPDATE_ALL);
        }
    }

    // Pushes the oldest run's remaining inputs into whichever neighbours take them.
    private boolean push(ServerLevel level) {
        for (Run run : runs) {
            if (run.toPush.isEmpty()) {
                continue;
            }
            boolean moved = false;
            for (ItemStack stack : run.toPush) {
                for (Direction side : Direction.values()) {
                    if (stack.isEmpty()) {
                        break;
                    }
                    ResourceHandler<ItemResource> target = neighbour(level, side);
                    if (target == null) {
                        continue;
                    }
                    try (Transaction transaction = Transaction.openRoot()) {
                        int inserted = target.insert(ItemResource.of(stack), stack.getCount(), transaction);
                        transaction.commit();
                        if (inserted > 0) {
                            stack.shrink(inserted);
                            moved = true;
                        }
                    }
                }
            }
            run.toPush.removeIf(ItemStack::isEmpty);
            finishIfDone(level, run);
            return moved;
        }
        return false;
    }

    // Outputs machines put into the intake slots go to the runs waiting for them.
    private boolean takeIntake(ServerLevel level) {
        boolean moved = false;
        for (int slot = BUFFER; slot < BUFFER + INTAKE; slot++) {
            ItemResource resource = handler.getResource(slot);
            int amount = handler.getAmountAsInt(slot);
            if (resource.isEmpty() || amount <= 0) {
                continue;
            }
            handler.set(slot, ItemResource.EMPTY, 0);
            int left = receive(level, ItemKey.of(resource.toStack(1)), amount);
            if (left > 0) {
                SchedulerCoreBlockEntity.returnToNetwork(level, worldPosition, List.of(resource.toStack(left)));
            }
            moved = true;
        }
        return moved;
    }

    // Takes the outputs runs are waiting for out of the neighbours: through the face touching the Gateway, then their
    // other faces (a furnace gives up its result only from below).
    private boolean pull(ServerLevel level) {
        boolean moved = false;
        for (Map.Entry<ItemKey, Long> want : expectedTotals().entrySet()) {
            long left = want.getValue();
            ItemResource resource = ItemResource.of(want.getKey().stack());
            for (Direction side : Direction.values()) {
                for (Direction face : faces(side.getOpposite())) {
                    if (left <= 0) {
                        break;
                    }
                    ResourceHandler<ItemResource> source = neighbour(level, side, face);
                    if (source == null) {
                        continue;
                    }
                    int extracted;
                    try (Transaction transaction = Transaction.openRoot()) {
                        extracted = source.extract(resource, (int) Math.min(Integer.MAX_VALUE, left), transaction);
                        transaction.commit();
                    }
                    if (extracted > 0) {
                        receive(level, want.getKey(), extracted);
                        left -= extracted;
                        moved = true;
                    }
                }
            }
        }
        return moved;
    }

    // The touching face first, then the rest.
    private static List<Direction> faces(Direction touching) {
        List<Direction> faces = new ArrayList<>(List.of(Direction.values()));
        faces.remove(touching);
        faces.addFirst(touching);
        return faces;
    }

    // Hands arriving outputs to the oldest runs expecting them; returns how many nobody wanted.
    private int receive(ServerLevel level, ItemKey key, int amount) {
        int left = amount;
        for (Run run : List.copyOf(runs)) {
            long expected = run.expected.getOrDefault(key, 0L);
            if (expected <= 0 || left <= 0) {
                continue;
            }
            int give = (int) Math.min(left, expected);
            run.expected.put(key, expected - give);
            left -= give;
            SchedulerCoreBlockEntity.deliver(level, run.task, List.of(key.toStack(give)), false, worldPosition);
            finishIfDone(level, run);
        }
        return left;
    }

    private void finishIfDone(ServerLevel level, Run run) {
        if (run.toPush.isEmpty() && run.expected.values().stream().allMatch(count -> count <= 0) && runs.remove(run)) {
            SchedulerCoreBlockEntity.deliver(level, run.task, List.of(), true, worldPosition);
        }
    }

    // Runs whose job is gone (cancelled) stop waiting; inputs not yet pushed go back to the network.
    private void dropOrphans(ServerLevel level) {
        Iterator<Run> iterator = runs.iterator();
        while (iterator.hasNext()) {
            Run run = iterator.next();
            BlockPos core = run.task.core();
            if (level.isLoaded(core) && !(level.getBlockEntity(core) instanceof SchedulerCoreBlockEntity entity && entity.job(run.task.job()) != null)) {
                SchedulerCoreBlockEntity.returnToNetwork(level, worldPosition, run.toPush);
                iterator.remove();
            }
        }
    }

    private Map<ItemKey, Long> expectedTotals() {
        Map<ItemKey, Long> totals = new LinkedHashMap<>();
        for (Run run : runs) {
            run.expected.forEach((key, count) -> {
                if (count > 0) {
                    totals.merge(key, count, Long::sum);
                }
            });
        }
        return totals;
    }

    // Each buffer slot to its stock setting: topped up from the network, the rest (or anything else) back into it.
    private boolean restock(ServerLevel level) {
        NetworkStorage storage = ControllerStructures.get(level).storageAt(level, worldPosition);
        if (storage == null) {
            return false;
        }
        boolean moved = false;
        for (int slot = 0; slot < BUFFER; slot++) {
            ItemStack want = stock.get(slot);
            ItemResource held = handler.getResource(slot);
            int count = handler.getAmountAsInt(slot);
            if (!held.isEmpty() && (want.isEmpty() || !held.matches(want))) {
                int stored = (int) storage.insert(ItemKey.of(held.toStack(1)), count, false);
                handler.set(slot, stored >= count ? ItemResource.EMPTY : held, count - stored);
                moved |= stored > 0;
                continue;
            }
            if (want.isEmpty()) {
                continue;
            }
            int target = Math.min(want.getCount(), want.getMaxStackSize());
            ItemKey key = ItemKey.of(want);
            if (count < target) {
                int taken = (int) storage.extract(key, target - count, false);
                if (taken > 0) {
                    handler.set(slot, ItemResource.of(want), count + taken);
                    moved = true;
                }
            } else if (count > target) {
                int stored = (int) storage.insert(key, count - target, false);
                handler.set(slot, ItemResource.of(want), count - stored);
                moved |= stored > 0;
            }
        }
        return moved;
    }

    // The item handler of the block on that side, through the face touching the Gateway.
    private @Nullable ResourceHandler<ItemResource> neighbour(ServerLevel level, Direction side) {
        return neighbour(level, side, side.getOpposite());
    }

    private @Nullable ResourceHandler<ItemResource> neighbour(ServerLevel level, Direction side, Direction face) {
        BlockPos pos = worldPosition.relative(side);
        if (!level.isLoaded(pos) || level.getBlockEntity(pos) instanceof GatewayBlockEntity) {
            return null;
        }
        return level.getCapability(Capabilities.Item.BLOCK, pos, face);
    }

    // --- Handler ---

    // Buffer slots (0-8): neighbours take stocked items out, nothing goes in. Intake slots (9-17): only outputs runs are
    // waiting for go in (no more than they still expect), and nothing comes out.
    private final class Handler extends ItemStacksResourceHandler {
        Handler() {
            super(BUFFER + INTAKE);
        }

        private long room(ItemResource resource) {
            ItemKey key = ItemKey.of(resource.toStack(1));
            long room = expectedTotals().getOrDefault(key, 0L);
            for (int slot = BUFFER; slot < BUFFER + INTAKE; slot++) {
                if (getResource(slot).equals(resource)) {
                    room -= getAmountAsLong(slot);
                }
            }
            return room;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return index >= BUFFER && room(resource) > 0;
        }

        @Override
        protected int getCapacity(int index, ItemResource resource) {
            if (index < BUFFER || resource.isEmpty()) {
                return super.getCapacity(index, resource);
            }
            long capacity = getAmountAsLong(index) + Math.max(0, room(resource));
            return (int) Math.min(capacity, super.getCapacity(index, resource));
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return index >= BUFFER ? 0 : super.extract(index, resource, amount, transaction);
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    }

    private final class BufferView implements Container {
        @Override
        public int getContainerSize() {
            return BUFFER;
        }

        @Override
        public boolean isEmpty() {
            for (int slot = 0; slot < BUFFER; slot++) {
                if (handler.getAmountAsInt(slot) > 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return handler.getResource(slot).toStack(handler.getAmountAsInt(slot));
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {}

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {}
    }

    // --- Menu ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.encodedlogistics.gateway");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GatewayMenu(containerId, inventory, this);
    }

    public boolean stillValid(Player player) {
        return !isRemoved() && player.isWithinBlockInteractionRange(worldPosition, 4.0);
    }

    public static boolean acceptsSchematic(ItemStack stack) {
        return stack.is(ModItems.ENCODED_SCHEMATIC_PROCESSING.get());
    }

    // --- Saving ---

    // Breaking it drops its schematics, buffer and intake; runs' unpushed inputs go back to their jobs.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (ItemStack stack : schematics) {
            Block.popResource(level, pos, stack.copy());
        }
        for (int slot = 0; slot < BUFFER + INTAKE; slot++) {
            if (handler.getAmountAsInt(slot) > 0) {
                Block.popResource(level, pos, handler.getResource(slot).toStack(handler.getAmountAsInt(slot)));
            }
        }
        if (level instanceof ServerLevel serverLevel) {
            for (Run run : runs) {
                if (!run.toPush.isEmpty()) {
                    SchedulerCoreBlockEntity.refund(serverLevel, run.task, run.toPush, pos);
                }
            }
        }
        runs.clear();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < SCHEMATIC_SLOTS; i++) {
            schematics.set(i, ItemStack.EMPTY);
            stock.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(input.childOrEmpty("schematics"), schematics);
        ContainerHelper.loadAllItems(input.childOrEmpty("stock"), stock);
        handler.deserialize(input.childOrEmpty("handler"));
        runs.clear();
        input.read("runs", Run.CODEC.listOf()).ifPresent(runs::addAll);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output.child("schematics"), schematics);
        ContainerHelper.saveAllItems(output.child("stock"), stock);
        handler.serialize(output.child("handler"));
        output.store("runs", Run.CODEC.listOf(), runs);
    }
}
