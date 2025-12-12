package is.pig.minecraft.inventory.sorting;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class LayoutEngine {

    public static List<ItemStack> applyLayout(List<ItemStack> sortedItems, int totalSlots, int rowSize) {
        List<ItemStack> layout = new ArrayList<>();
        if (sortedItems.isEmpty())
            return layout;

        int currentWeight = SmartCategorySorter.getItemWeight(sortedItems.get(0));

        for (ItemStack stack : sortedItems) {
            int weight = SmartCategorySorter.getItemWeight(stack);

            // If weight changes significantly (different category group), pad to new row
            // We use a threshold or exact match? Given weights are 100, 200, etc., exact
            // match is grouping.
            // But some weights might be identical for slightly different items.
            // The mapping uses broad categories (hundreds).
            // Let's assume ANY change in the 100-level weight means a new visual group.

            // To be robust: If floor(weight/100) changes, it's a new major category.
            // e.g. 100 (Food) vs 200 (Tools).
            // But if we have 220 (Combat), that's close to Tools.
            // User wants "separated by rows".

            if (Math.abs(weight - currentWeight) >= 50) { // Threshold for "New Group"
                // Fill remainder of row with empty
                while (layout.size() % rowSize != 0) {
                    // Check if we effectively fill the container, if so stop padding
                    if (layout.size() >= totalSlots)
                        break;
                    layout.add(ItemStack.EMPTY);
                }
                currentWeight = weight;
            }

            // Check if we are full
            if (layout.size() >= totalSlots) {
                // If full, force overwrite or stop?
                // We shouldn't drop items.
                // If we ran out of space due to padding, we verify at the end.
            }
            layout.add(stack);
        }

        // Final overflow check
        if (layout.size() > totalSlots) {
            // Padding caused overflow. We MUST fallback to compact sorting for safety.
            // Or remove padding from the end?
            // Removing padding is tricky if it's in the middle.
            // Simple approach: If it doesn't fit, return original sorted list (compact).
            return sortedItems;
        }

        return layout;
    }
}
