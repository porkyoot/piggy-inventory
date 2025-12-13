package is.pig.minecraft.inventory.sorting;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import is.pig.minecraft.inventory.config.PiggyInventoryConfig;

public class LayoutEngine {

    /**
     * Calculates the layout of items into slots.
     * 
     * @param sortedItems Sorted list of items to place.
     * @param validSlots  List of absolute slot indices that are available for
     *                    placement.
     * @param rowSize     Width of the container (usually 9).
     * @param layoutType  The layout strategy (COMPACT, ROWS, COLUMNS).
     * @return A map of SlotIndex -> ItemStack for the desired state. Slots not in
     *         map mean EMPTY.
     */
    public static Map<Integer, ItemStack> calculateLayout(List<ItemStack> sortedItems, List<Integer> validSlots,
            int rowSize, PiggyInventoryConfig.SortingLayout layoutType,
            PiggyInventoryConfig.SortingAlgorithm algorithm) {

        if (validSlots.isEmpty())
            return new HashMap<>();

        Collections.sort(validSlots);
        int minSlot = validSlots.get(0);
        int maxSlot = validSlots.get(validSlots.size() - 1);

        // Determine grid dimensions relative to the first slot
        // This assumes slots are roughly contiguous or chunked.
        // For accurate grid mapping, we assume standard container logic: index /
        // rowSize
        // But since we have a subset of slots, we should map them based on their
        // absolute index.

        // We need to know the 'start' of the container to align columns correctly (e.g.
        // correct modulus)
        // Usually minSlot is the start, but if the first few slots are locked, minSlot
        // might be 3.
        // We'll trust relative row/col to the container start.
        // Wait, standard containers are 9 wide.
        // Col = index % 9. Row = index / 9.

        // Optimization: Pre-compute available slots as a fast lookup set
        Set<Integer> availableSet = new HashSet<>(validSlots);
        Map<Integer, ItemStack> result = new HashMap<>();

        // Group items if necessary
        if (layoutType == PiggyInventoryConfig.SortingLayout.ROWS
                || layoutType == PiggyInventoryConfig.SortingLayout.COLUMNS
                || layoutType == PiggyInventoryConfig.SortingLayout.GRID) {
            return planWithGroups(sortedItems, availableSet, minSlot, maxSlot, rowSize, layoutType, algorithm);
        }

        // COMPACT: Just fill available slots in order
        int itemIndex = 0;
        for (Integer slotIdx : validSlots) {
            if (itemIndex < sortedItems.size()) {
                result.put(slotIdx, sortedItems.get(itemIndex));
                itemIndex++;
            } else {
                result.put(slotIdx, ItemStack.EMPTY);
            }
        }
        return result;
    }

    private static Map<Integer, ItemStack> planWithGroups(List<ItemStack> sortedItems, Set<Integer> availableSlots,
            int firstSlotIndex, int lastSlotIndex, int rowSize, PiggyInventoryConfig.SortingLayout layoutType,
            PiggyInventoryConfig.SortingAlgorithm algorithm) {

        Map<Integer, ItemStack> result = new HashMap<>();

        // 1. Split items into groups based on category weight
        List<List<ItemStack>> groups = new ArrayList<>();
        if (sortedItems.isEmpty())
            return result;

        List<ItemStack> currentGroup = new ArrayList<>();
        currentGroup.add(sortedItems.get(0));
        long currentGroupKey = getGroupKey(sortedItems.get(0), algorithm);

        for (int i = 1; i < sortedItems.size(); i++) {
            ItemStack stack = sortedItems.get(i);
            long key = getGroupKey(stack, algorithm);

            boolean changed = false;
            // Define change condition
            if (algorithm == PiggyInventoryConfig.SortingAlgorithm.SMART) {
                changed = Math.abs(key - currentGroupKey) >= 50;
            } else {
                changed = key != currentGroupKey;
            }

            if (changed) {
                groups.add(currentGroup);
                currentGroup = new ArrayList<>();
                currentGroupKey = key;
            }
            currentGroup.add(stack);
        }
        groups.add(currentGroup); // Add last group

        // 2. Calculate Environment
        // We iterate Logical Rows.
        // Start Row = firstSlotIndex / rowSize
        // End Row = lastSlotIndex / rowSize
        int startRow = firstSlotIndex / rowSize;
        int endRow = lastSlotIndex / rowSize;
        int totalRows = endRow - startRow + 1;

        // 3. Spreading Logic
        // Calculate minimal rows needed
        // For ROWS layout: each group needs ceil(size / 9) rows.
        // For COLUMNS layout: ... distinct columns? Maybe spreading columns is better?
        // Let's stick to ROWS spreading for now as it's more requested "Rows and
        // Columns layout".

        if (layoutType == PiggyInventoryConfig.SortingLayout.ROWS) {
            // Calculate needed rows per group
            // Improved calculation accounting for locked slots (effective capacity)
            double effectiveRowSize = Math.max(1.0, (double) availableSlots.size() / totalRows);
            List<Integer> groupRows = new ArrayList<>();
            for (List<ItemStack> g : groups) {
                int needed = (int) Math.ceil(g.size() / effectiveRowSize);
                groupRows.add(needed);
            }

            // Allow for at least 1 row gap if possible

            // Distribute spare rows
            // Strategy: Ensure at least 1 gap between groups, then distribute remainder
            // nicely?
            // Or just spread evenly.

            // 4. Place items
            int currentRowOffset = 0; // Relative to startRow

            for (int gIdx = 0; gIdx < groups.size(); gIdx++) {
                List<ItemStack> group = groups.get(gIdx);

                // Place group
                int itemsPlaced = 0;
                while (itemsPlaced < group.size()) {

                    // Safety break
                    if (startRow + currentRowOffset > endRow)
                        break;

                    for (int c = 0; c < rowSize; c++) {
                        if (itemsPlaced >= group.size())
                            break;

                        int absSlot = (startRow + currentRowOffset) * rowSize + c;

                        // Check if slot is available (not locked)
                        if (availableSlots.contains(absSlot)) {
                            result.put(absSlot, group.get(itemsPlaced));
                            itemsPlaced++;
                        }
                    }
                    currentRowOffset++;
                }

                // Dynamic Gap Calculation
                int groupsRemaining = groups.size() - 1 - gIdx;
                if (groupsRemaining > 0) {
                    // How many rows do future groups need?
                    int rowsNeededForRest = 0;
                    for (int k = gIdx + 1; k < groupRows.size(); k++)
                        rowsNeededForRest += groupRows.get(k);

                    int rowsLeftInGrid = (endRow - startRow + 1) - currentRowOffset;
                    int spareRows = Math.max(0, rowsLeftInGrid - rowsNeededForRest);

                    // Distribute spare rows evenly
                    int gap = spareRows / groupsRemaining;

                    // Apply Gap
                    currentRowOffset += gap;
                }
            }

        } else if (layoutType == PiggyInventoryConfig.SortingLayout.COLUMNS) {
            // Spreading columns
            // Simple approach: Each group starts at a new column index if possible.
            // Distribute columns?
            // "Spread columns apart and regularly"

            // Implementation:
            // Calculate total columns needed: 1 per group minimum? No, a group might wrap.
            // But COLUMNS layout implies Vertical filling.
            // Group 1 fills Col 0, then Col 1...
            // Group 2 starts at Next Col.

            // Calculate total cols needed
            // Improved calculation
            double effectiveRowsPerCol = Math.max(1.0, (double) availableSlots.size() / rowSize);
            List<Integer> groupCols = new ArrayList<>();
            int totalColsNeeded = 0;
            for (List<ItemStack> g : groups) {
                int needed = (int) Math.ceil(g.size() / effectiveRowsPerCol);
                groupCols.add(needed);
                totalColsNeeded += needed;
            }

            int gapsNeeded = groups.size() - 1;
            int spareCols = rowSize - totalColsNeeded;
            // Distribute spare columns as gaps
            // Distribute spare cols
            int[] gapSizes = new int[Math.max(1, gapsNeeded)];

            if (gapsNeeded > 0 && spareCols > 0) {
                int baseGap = spareCols / gapsNeeded;
                int remainder = spareCols % gapsNeeded;
                for (int i = 0; i < gapsNeeded; i++)
                    gapSizes[i] = baseGap + (i < remainder ? 1 : 0);
            }

            int currentCol = 0;
            for (int gIdx = 0; gIdx < groups.size(); gIdx++) {
                List<ItemStack> group = groups.get(gIdx);

                // Place group vertically
                int itemsPlaced = 0;
                // While group has items, fill current column then advance
                // But respect "Cols Needed" logic?
                // Just fill until done.

                // Starting new group? Check if we need to skip cols for locking?
                // No, we use `gapSizes` to skip COLUMNS.

                while (itemsPlaced < group.size()) {
                    if (currentCol >= rowSize)
                        break; // Overflow

                    // Fill this column top-to-bottom
                    for (int r = startRow; r <= endRow; r++) {
                        if (itemsPlaced >= group.size())
                            break;

                        int absSlot = r * rowSize + currentCol;
                        if (availableSlots.contains(absSlot)) {
                            result.put(absSlot, group.get(itemsPlaced));
                            itemsPlaced++;
                        }
                    }

                    if (itemsPlaced < group.size()) {
                        currentCol++; // Continue to next column for SAME group
                    }
                }

                // Group finished. Apply Gap.
                if (gIdx < gapsNeeded) {
                    currentCol += gapSizes[gIdx] + 1; // +1 to move pass the last used column
                } else {
                    currentCol++;
                }
            }
        } else if (layoutType == PiggyInventoryConfig.SortingLayout.GRID) {
            // Grid Layout: Flow with Gaps
            int currentRow = startRow;
            int currentCol = 0;
            int gapSize = 2;

            for (List<ItemStack> group : groups) {
                int itemsPlaced = 0;
                while (itemsPlaced < group.size()) {
                    // Safety
                    if (currentRow > endRow)
                        break;

                    int absSlot = currentRow * rowSize + currentCol;

                    if (availableSlots.contains(absSlot)) {
                        result.put(absSlot, group.get(itemsPlaced));
                        itemsPlaced++;
                    }

                    // Advance
                    currentCol++;
                    if (currentCol >= rowSize) {
                        currentCol = 0;
                        currentRow++;
                    }
                }

                // Apply Gap between groups
                for (int k = 0; k < gapSize; k++) {
                    currentCol++;
                    if (currentCol >= rowSize) {
                        currentCol = 0;
                        currentRow++;
                    }
                }
            }
        }

        return result;
    }

    private static long getGroupKey(ItemStack stack, PiggyInventoryConfig.SortingAlgorithm algorithm) {
        switch (algorithm) {
            case SMART:
                return SmartCategorySorter.getItemWeight(stack);
            case TAG:
                return TagSorter.getTagGroupIndex(stack);
            case RARITY:
                return stack.getRarity().ordinal();
            case COLOR:
                // Bucket hue into 8 groups (45 degrees each)
                return ColorSorter.getItemHue(stack) / 45;
            case TYPE:
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                return is.pig.minecraft.lib.util.NameAnalysisUtils.extractType(id).hashCode();
            case MATERIAL:
                // Group by Item Class (BlockItem vs SwordItem etc)
                return stack.getItem().getClass().hashCode();
            case ALPHABETICAL:
                String name = stack.getHoverName().getString();
                return name.isEmpty() ? 0 : Character.toUpperCase(name.charAt(0));
            default:
                return 0;
        }
    }
}
