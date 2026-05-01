package is.pig.minecraft.inventory.util;

import is.pig.minecraft.api.registry.PiggyServiceRegistry;
import is.pig.minecraft.api.spi.ItemDataAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for searching items within a Minecraft inventory using pure-Java conditions.
 */
public class InventorySearcher {

    private InventorySearcher() {
        // Utility class
    }

    /**
     * Finds the first slot in the hotbar matching the condition.
     */
    public static int findSlotInHotbar(Object inv, ItemCondition condition) {
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        for (int i = 0; i < 9; i++) {
            if (condition.matches(adapter.getStackInSlot(inv, i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the first slot in the main inventory matching the condition.
     */
    public static int findSlotInMain(Object inv, ItemCondition condition) {
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        for (int i = 9; i < 36; i++) {
            if (condition.matches(adapter.getStackInSlot(inv, i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds all slots matching the condition.
     */
    public static List<Integer> findAllSlots(Object inv, ItemCondition condition) {
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        List<Integer> slots = new ArrayList<>();
        int size = adapter.getContainerSize(inv);
        for (int i = 0; i < size; i++) {
            if (condition.matches(adapter.getStackInSlot(inv, i))) {
                slots.add(i);
            }
        }
        return slots;
    }

    /**
     * Helper to create a condition based on item tags.
     */
    public static ItemCondition hasTag(String tagId) {
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        return stack -> adapter.hasTag(stack, tagId);
    }
}
