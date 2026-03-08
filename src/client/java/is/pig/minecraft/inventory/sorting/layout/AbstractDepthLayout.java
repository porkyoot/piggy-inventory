package is.pig.minecraft.inventory.sorting.layout;

import is.pig.minecraft.inventory.sorting.Comparators;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractDepthLayout implements ISortingLayout {

    @Override
    public List<ItemStack> layout(List<ItemStack> items, List<net.minecraft.world.inventory.Slot> availableSlots) {
        if (items.isEmpty()) {
            return fillEmpty(new ArrayList<>(), availableSlots.size());
        }

        // We compute this cheaply: just count how many group-start rows would be needed.
        for (int depth = 8; depth >= 0; depth--) {
            if (fitsAtDepth(items, availableSlots, depth)) {
                return emitGroups(items, availableSlots, depth);
            }
        }

        // Ultimate fallback: contiguous, no breaks.
        return emitGroups(items, availableSlots, 0);
    }

    /**
     * Estimates if the items can fit inside the available slots at the given group split depth.
     * Computes the minimum overhead: each group break wastes at most (rowWidth - 1) slots.
     */
    protected boolean fitsAtDepth(List<ItemStack> items, List<net.minecraft.world.inventory.Slot> availableSlots, int depth) {
        if (depth == 0) return items.size() <= availableSlots.size();
        int rowWidth = getRowWidth(availableSlots);
        // Count groups
        int breaks = 0;
        ItemStack previous = null;
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                if (previous != null && shouldBreakGroup(previous, item, depth)) {
                    breaks++;
                }
                previous = item;
            }
        }
        // Worst case: each break wastes (rowWidth - 1) slots for row alignment + nothing at start.
        int worstCaseSize = items.size() + breaks * (rowWidth - 1);
        return worstCaseSize <= availableSlots.size();
    }

    /**
     * Emits the final grid by splitting items into groups, distributing proportional empty padding
     * between groups so that the full container capacity is evenly utilised.
     */
    protected List<ItemStack> emitGroups(List<ItemStack> items, List<net.minecraft.world.inventory.Slot> availableSlots, int depth) {
        // Build group list
        List<List<ItemStack>> groups = new ArrayList<>();
        List<ItemStack> current = new ArrayList<>();
        ItemStack previous = null;
        for (ItemStack item : items) {
            if (previous != null && shouldBreakGroup(previous, item, depth)) {
                groups.add(current);
                current = new ArrayList<>();
            }
            current.add(item);
            previous = item;
        }
        groups.add(current);
        
        is.pig.minecraft.inventory.PiggyInventory.LOGGER.debug("emitGroups: Split into {} groups at depth {}", groups.size(), depth);

        if (groups.size() <= 1) {
            return fillEmpty(new ArrayList<>(items), availableSlots.size());
        }
        
        int rowWidth = getRowWidth(availableSlots);
        
        // Figure out what each group would occupy after rounding up to row boundary
        // Total item slots used (aligned to row ends)
        int itemSlotsUsed = 0;
        for (List<ItemStack> group : groups) {
            itemSlotsUsed += ceilToRow(group.size(), rowWidth);
        }
        
        // Remaining free slots to distribute proportionally between groups (not after last group)
        int gapCount = groups.size() - 1;
        int freeSlots = availableSlots.size() - itemSlotsUsed;
        int baseGap = freeSlots < 0 ? 0 : freeSlots / gapCount;
        int remainder = freeSlots < 0 ? 0 : freeSlots % gapCount;

        // Flatten: build row-major output from grid based on coordinate alignments
        ItemStack[] grid = new ItemStack[availableSlots.size()];
        int cursor = 0; // position in availableSlots

        for (int g = 0; g < groups.size(); g++) {
            // Place group items
            for (ItemStack s : groups.get(g)) {
                if (cursor < availableSlots.size()) {
                    grid[cursor] = s;
                    cursor++;
                }
            }
            
            // Row-align (advance to start of next row)
            if (cursor > 0 && cursor < availableSlots.size()) {
                net.minecraft.world.inventory.Slot prevSlot = availableSlots.get(cursor - 1);
                while (cursor < availableSlots.size()) {
                    net.minecraft.world.inventory.Slot nextSlot = availableSlots.get(cursor);
                    if (nextSlot.y > prevSlot.y) break; // moved to a new row
                    cursor++;
                }
            }
            
            // Add proportional gap rows
            if (g < gapCount) {
                int gap = baseGap + (g < remainder ? 1 : 0);
                // Advance cursor by full rows
                for (int i = 0; i < gap; i++) {
                    if (cursor < availableSlots.size()) {
                        net.minecraft.world.inventory.Slot prevSlot = availableSlots.get(cursor - 1);
                        while (cursor < availableSlots.size()) {
                            net.minecraft.world.inventory.Slot nextSlot = availableSlots.get(cursor);
                            if (nextSlot.y > prevSlot.y) break; // moved to next row
                            cursor++;
                        }
                    }
                }
            }
        }

        List<ItemStack> output = new ArrayList<>();
        for (int i = 0; i < availableSlots.size(); i++) {
            output.add(grid[i] != null ? grid[i] : ItemStack.EMPTY);
        }

        // Just in case padding went over, strictly cap sizes
        if (output.size() > availableSlots.size()) {
            output = output.subList(0, availableSlots.size());
        }

        return fillEmpty(output, availableSlots.size());
    }

    /** Returns the width of a row by counting slots with the same Y coordinate as the first slot. */
    private int getRowWidth(List<net.minecraft.world.inventory.Slot> slots) {
        if (slots.isEmpty()) return 1;
        int firstY = slots.get(0).y;
        int count = 0;
        for (net.minecraft.world.inventory.Slot s : slots) {
            if (s.y == firstY) count++;
            else break;
        }
        return Math.max(1, count);
    }

    private int ceilToRow(int size, int rowWidth) {
        return ((size + rowWidth - 1) / rowWidth) * rowWidth;
    }

    protected abstract List<ItemStack> simulateLayout(List<ItemStack> items, List<net.minecraft.world.inventory.Slot> availableSlots, int depth);

    /**
     * Checks if two items are in different groups at the given break depth.
     * Depth 8: Category, Mod, Color, Letter, Rarity, Name, ID, Amount
     * Depth 7: Category, Mod, Color, Letter, Rarity, Name, ID
     * Depth 6: Category, Mod, Color, Letter, Rarity, Name
     * Depth 5: Category, Mod, Color, Letter, Rarity
     * Depth 4: Category, Mod, Color, Letter
     * Depth 3: Category, Mod, Color
     * Depth 2: Category, Mod
     * Depth 1: Category
     * Depth 0: No breaks
     */
    protected boolean shouldBreakGroup(ItemStack a, ItemStack b, int depth) {
        if (depth == 0) return false;
        
        if (depth >= 1 && Comparators.CREATIVE_MENU.compare(a, b) != 0) return true;
        if (depth >= 2 && Comparators.MOD.compare(a, b) != 0) return true;
        if (depth >= 3 && Comparators.COLOR.compare(a, b) != 0) return true;
        if (depth >= 4 && Comparators.LETTER.compare(a, b) != 0) return true;
        if (depth >= 5 && Comparators.RARITY.compare(a, b) != 0) return true;
        if (depth >= 6 && Comparators.NAME.compare(a, b) != 0) return true;
        if (depth >= 7 && Comparators.ID.compare(a, b) != 0) return true;
        if (depth >= 8 && Comparators.AMOUNT.compare(a, b) != 0) return true;

        return false;
    }

    protected List<ItemStack> fillEmpty(List<ItemStack> list, int targetSize) {
        while (list.size() < targetSize) {
            list.add(ItemStack.EMPTY);
        }
        return list;
    }
}
