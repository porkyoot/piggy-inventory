package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.api.registry.PiggyServiceRegistry;
import is.pig.minecraft.api.spi.ItemDataAdapter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * An immutable snapshot of the inventory state, used as a "Shadow Inventory"
 * for mathematical plan verification.
 */
public record InventorySnapshot(
        int containerId,
        List<SlotState> slots,
        Object cursor
) {
    /**
     * Represents the state of a single slot.
     */
    public record SlotState(int index, Object stack) {
        public SlotState copy() {
            ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
            return new SlotState(index, adapter.copy(stack));
        }
    }

    /**
     * Predicts the state after applying abstract moves, using a custom capacity provider.
     */
    public InventorySnapshot applyMoves(List<Move> moves, ToIntFunction<Object> capacityProvider) {
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        Map<Integer, Object> workingSlots = new HashMap<>();
        for (SlotState state : slots) {
            workingSlots.put(state.index, adapter.copy(state.stack()));
        }
        Object workingCursor = adapter.copy(cursor);

        for (Move move : moves) {
            int idx = move.slotIndex();
            Object slotStack = workingSlots.getOrDefault(idx, adapter.getEmptyStack());
            if (slotStack == null) slotStack = adapter.getEmptyStack();

            int maxSlotCap = capacityProvider.applyAsInt(slotStack);
            int maxCursorCap = Math.min(64, adapter.getMaxStackSize(slotStack));
            if (maxCursorCap <= 0) maxCursorCap = 64;

            switch (move.type()) {
                case PICKUP_ALL -> {
                    int slotCount = adapter.getCount(slotStack);
                    if (adapter.getCount(workingCursor) == 0) {
                        int amount = Math.min(slotCount, maxCursorCap);
                        workingCursor = adapter.copyWithCount(slotStack, amount);
                        workingSlots.put(idx, adapter.copyWithCount(slotStack, slotCount - amount));
                    } else if (adapter.areItemsEqual(workingCursor, slotStack)) {
                        int spaceLeft = Math.max(0, maxCursorCap - adapter.getCount(workingCursor));
                        int toTransfer = Math.min(spaceLeft, slotCount);
                        adapter.grow(workingCursor, toTransfer);
                        workingSlots.put(idx, adapter.copyWithCount(slotStack, slotCount - toTransfer));
                    } else {
                        if (slotCount > maxCursorCap && adapter.getCount(workingCursor) > maxCursorCap) {
                            throw new IllegalStateException("Cannot swap two stacks where both exceed cursor capacity");
                        }
                        workingSlots.put(idx, workingCursor);
                        workingCursor = slotStack;
                    }
                }
                case PICKUP_HALF -> {
                    int slotCount = adapter.getCount(slotStack);
                    if (adapter.getCount(workingCursor) == 0 && slotCount > 0) {
                        int half = (slotCount + 1) / 2;
                        int amount = Math.min(half, maxCursorCap);
                        workingCursor = adapter.copyWithCount(slotStack, amount);
                        workingSlots.put(idx, adapter.copyWithCount(slotStack, slotCount - amount));
                    }
                }
                case DEPOSIT_ALL -> {
                    int cursorCount = adapter.getCount(workingCursor);
                    if (cursorCount == 0) break;
                    int slotCount = adapter.getCount(slotStack);
                    if (slotCount == 0) {
                        int amount = Math.min(cursorCount, maxSlotCap);
                        workingSlots.put(idx, adapter.copyWithCount(workingCursor, amount));
                        adapter.shrink(workingCursor, amount);
                    } else if (adapter.areItemsEqual(workingCursor, slotStack)) {
                        int spaceLeft = Math.max(0, maxSlotCap - slotCount);
                        int toTransfer = Math.min(spaceLeft, cursorCount);
                        workingSlots.put(idx, adapter.copyWithCount(slotStack, slotCount + toTransfer));
                        adapter.shrink(workingCursor, toTransfer);
                    } else {
                        if (slotCount > maxCursorCap && cursorCount > maxCursorCap) {
                            throw new IllegalStateException("Cannot swap two mega-stacks");
                        }
                        workingSlots.put(idx, workingCursor);
                        workingCursor = slotStack;
                    }
                }
                case DEPOSIT_ONE -> {
                    int cursorCount = adapter.getCount(workingCursor);
                    if (cursorCount == 0) break;
                    int slotCount = adapter.getCount(slotStack);
                    if (slotCount == 0 || (adapter.areItemsEqual(workingCursor, slotStack) && slotCount < maxSlotCap)) {
                        if (slotCount == 0) {
                            workingSlots.put(idx, adapter.copyWithCount(workingCursor, 1));
                        } else {
                            workingSlots.put(idx, adapter.copyWithCount(slotStack, slotCount + 1));
                        }
                        adapter.shrink(workingCursor, 1);
                    }
                }
                case SWAP -> {
                    if (adapter.getCount(slotStack) > maxCursorCap && adapter.getCount(workingCursor) > maxCursorCap) {
                        throw new IllegalStateException("Atomic swap prohibited for multiple mega-stacks.");
                    }
                    workingSlots.put(idx, workingCursor);
                    workingCursor = slotStack;
                }
            }
        }

        List<SlotState> finalSlots = workingSlots.entrySet().stream()
                .map(e -> new SlotState(e.getKey(), e.getValue()))
                .sorted(java.util.Comparator.comparingInt(SlotState::index))
                .collect(Collectors.toUnmodifiableList());

        return new InventorySnapshot(containerId, finalSlots, workingCursor);
    }
}
