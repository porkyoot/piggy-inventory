package is.pig.minecraft.inventory.sorting;

/**
 * Represents an abstract inventory interaction move.
 */
public record Move(int slotIndex, MoveType type) {
    /**
     * Types of interaction moves that can be performed.
     */
    public enum MoveType {
        /** Pick up all items from the slot. */
        PICKUP_ALL,
        /** Pick up half of the items from the slot. */
        PICKUP_HALF,
        /** Deposit all items from the cursor into the slot. */
        DEPOSIT_ALL,
        /** Deposit a single item from the cursor into the slot. */
        DEPOSIT_ONE,
        /** Swap the items in the cursor with those in the slot. */
        SWAP
    }
}
