package is.pig.minecraft.inventory.sorting;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import is.pig.minecraft.inventory.config.PiggyInventoryConfig;

public class LayoutEngine {

    public static List<ItemStack> applyLayout(List<ItemStack> sortedItems, int totalSlots, int rowSize,
            PiggyInventoryConfig.SortingLayout layoutType) {
        if (sortedItems.isEmpty())
            return new ArrayList<>();

        // COMPACT: Just standard sorting, no gaps.
        if (layoutType == PiggyInventoryConfig.SortingLayout.COMPACT) {
            return new ArrayList<>(sortedItems);
        }

        // ROWS: Add gaps between categories
        if (layoutType == PiggyInventoryConfig.SortingLayout.ROWS) {
            List<ItemStack> layout = new ArrayList<>();
            int currentWeight = SmartCategorySorter.getItemWeight(sortedItems.get(0));

            for (ItemStack stack : sortedItems) {
                int weight = SmartCategorySorter.getItemWeight(stack);

                // Start new row + optional padding if group changes
                if (Math.abs(weight - currentWeight) >= 50) {
                    // Finish current row
                    while (layout.size() % rowSize != 0) {
                        if (layout.size() >= totalSlots)
                            break;
                        layout.add(ItemStack.EMPTY);
                    }

                    // Add an extra empty row if we have plenty of space?
                    // "empty rows in between when possible"
                    // Let's check remaining space vs remaining items.
                    // Remaining items:
                    // int remainingItems = sortedItems.size() - sortedItems.indexOf(stack); //
                    // rough check (unsafe if dupes)

                    // Safer: just add 1 row of empty if we aren't near the end.
                    if (layout.size() + rowSize < totalSlots) {
                        // Check if adding empty row fits
                        for (int k = 0; k < rowSize; k++) {
                            if (layout.size() >= totalSlots)
                                break;
                            layout.add(ItemStack.EMPTY);
                        }
                    }

                    currentWeight = weight;
                }

                if (layout.size() >= totalSlots)
                    break;
                layout.add(stack);
            }

            // Validate count
            long count = layout.stream().filter(s -> !s.isEmpty()).count();
            if (count < sortedItems.size())
                return new ArrayList<>(sortedItems);
            return layout;
        }

        // COLUMNS: Sort vertically, grouping by category
        if (layoutType == PiggyInventoryConfig.SortingLayout.COLUMNS) {
            // We map (r, c) to slot index.
            // When category changes, we must advance 'c' (Column) and reset 'r' (Row).

            ItemStack[] grid = new ItemStack[totalSlots];
            for (int i = 0; i < totalSlots; i++)
                grid[i] = ItemStack.EMPTY;

            int rows = (totalSlots + rowSize - 1) / rowSize;
            int c = 0;
            int r = 0;

            int currentWeight = SmartCategorySorter.getItemWeight(sortedItems.get(0));

            for (ItemStack stack : sortedItems) {
                int weight = SmartCategorySorter.getItemWeight(stack);

                if (Math.abs(weight - currentWeight) >= 50) {
                    // Category Change: Move to Next Column
                    if (r > 0) { // If currently in middle of column, finish it
                        c++;
                        r = 0;
                    }
                    // If we were already at r=0 (fresh column), we don't necessarily skip unless we
                    // want empty columns between?
                    // "Same for columns" -> "empty rows in between" equivalent is "empty columns in
                    // between".
                    // If possible.

                    // Let's just ensure we are in a fresh column.
                    if (r != 0) {
                        c++;
                        r = 0;
                    }
                    currentWeight = weight;
                }

                // Overflow check logic for columns
                if (c >= rowSize) {
                    // Out of columns! Fallback to compact?
                    // Or wrap to next "Page"? (Not possible in single inventory)
                    // Fallback to compact.
                    return new ArrayList<>(sortedItems);
                }

                // Place item
                // Slot Index = r * rowSize + c
                int slotIdx = r * rowSize + c;
                if (slotIdx < totalSlots) {
                    grid[slotIdx] = stack;
                }

                // Advance
                r++;
                if (r >= rows) {
                    r = 0;
                    c++;
                }
            }

            List<ItemStack> layout = new ArrayList<>();
            for (ItemStack s : grid)
                layout.add(s);
            return layout;
        }

        return new ArrayList<>(sortedItems);
    }
}
