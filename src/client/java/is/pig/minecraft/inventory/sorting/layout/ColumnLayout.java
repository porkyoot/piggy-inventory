package is.pig.minecraft.inventory.sorting.layout;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class ColumnLayout extends AbstractDepthLayout {

    /** Returns the height of a column (number of distinct y-values in the first column). */
    private int getColHeight(List<net.minecraft.world.inventory.Slot> slots) {
        if (slots.isEmpty()) return 1;
        // Build column-major order first to find the first column's x.
        List<Integer> colMajor = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) colMajor.add(i);
        colMajor.sort((a, b) -> {
            net.minecraft.world.inventory.Slot sa = slots.get(a);
            net.minecraft.world.inventory.Slot sb = slots.get(b);
            return sa.x != sb.x ? Integer.compare(sa.x, sb.x) : Integer.compare(sa.y, sb.y);
        });
        int firstX = slots.get(colMajor.get(0)).x;
        int height = 0;
        for (int idx : colMajor) {
            if (slots.get(idx).x == firstX) height++;
            else break;
        }
        return Math.max(1, height);
    }

    /**
     * Override to use column-based overhead: each break wastes at most (colHeight - 1) slots,
     * not (rowWidth - 1) slots. Without this, a 9-wide inventory makes every depth > 0
     * fail the fit check, and the sort falls back to depth=0 (no group separation).
     */
    @Override
    protected boolean fitsAtDepth(List<ItemStack> items, List<net.minecraft.world.inventory.Slot> availableSlots, int depth) {
        if (depth == 0) return items.size() <= availableSlots.size();
        int colHeight = getColHeight(availableSlots);
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
        // Each break wastes at most (colHeight - 1) slots to align to the next column boundary.
        int worstCaseSize = items.size() + breaks * (colHeight - 1);
        return worstCaseSize <= availableSlots.size();
    }

    @Override
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

        // removed duplicate groups.add(current)

        // Build column-major traversal order of available slot indices (x first, then y within col)
        List<Integer> colMajorIndices = new ArrayList<>();
        for (int i = 0; i < availableSlots.size(); i++) colMajorIndices.add(i);
        colMajorIndices.sort((a, b) -> {
            net.minecraft.world.inventory.Slot sa = availableSlots.get(a);
            net.minecraft.world.inventory.Slot sb = availableSlots.get(b);
            return sa.x != sb.x ? Integer.compare(sa.x, sb.x) : Integer.compare(sa.y, sb.y);
        });

        // Determine column height (number of distinct y values per column in first column)
        int firstColX = availableSlots.get(colMajorIndices.get(0)).x;
        int colHeight = 0;
        for (int idx : colMajorIndices) {
            if (availableSlots.get(idx).x == firstColX) colHeight++;
            else break;
        }
        colHeight = Math.max(1, colHeight);

        // Total columns in the container
        int totalCols = (int) Math.ceil((double) availableSlots.size() / colHeight);

        // How many columns each group needs (ceil to column boundary)
        int usedCols = 0;
        for (List<ItemStack> g : groups) usedCols += (int) Math.ceil((double) g.size() / colHeight);

        // Distribute free columns between groups
        int gapCount = groups.size() - 1;
        int freeCols = Math.max(0, totalCols - usedCols);
        int baseGap = gapCount > 0 ? freeCols / gapCount : 0;
        int remainder = gapCount > 0 ? freeCols % gapCount : 0;

        // Fill in a column-major grid, then read out in slot-major order
        ItemStack[] grid = new ItemStack[availableSlots.size()];
        int cursor = 0; // position in colMajorIndices

        for (int g = 0; g < groups.size(); g++) {
            // Place group items
            for (ItemStack s : groups.get(g)) {
                if (cursor < colMajorIndices.size()) {
                    grid[colMajorIndices.get(cursor)] = s;
                    cursor++;
                }
            }
            // Column-align (advance to top of next column)
            if (cursor > 0 && cursor < colMajorIndices.size()) {
                net.minecraft.world.inventory.Slot prevSlot = availableSlots.get(colMajorIndices.get(cursor - 1));
                while (cursor < colMajorIndices.size()) {
                    net.minecraft.world.inventory.Slot nextSlot = availableSlots.get(colMajorIndices.get(cursor));
                    if (nextSlot.x > prevSlot.x) break; // moved to a new column
                    cursor++;
                }
            }
            // Add proportional gap columns
            if (g < gapCount) {
                int gap = baseGap + (g < remainder ? 1 : 0);
                // Advance cursor by full columns
                for (int i = 0; i < gap; i++) {
                    if (cursor < colMajorIndices.size()) {
                        net.minecraft.world.inventory.Slot prevSlot = availableSlots.get(colMajorIndices.get(cursor - 1));
                        while (cursor < colMajorIndices.size()) {
                            net.minecraft.world.inventory.Slot nextSlot = availableSlots.get(colMajorIndices.get(cursor));
                            if (nextSlot.x > prevSlot.x) break; // moved to next column
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

    @Override
    protected List<ItemStack> simulateLayout(List<ItemStack> items, List<net.minecraft.world.inventory.Slot> availableSlots, int depth) {
        // Not used by the two-phase algorithm; fitsAtDepth covers feasibility checking for columns too
        // since column overhead <= row overhead. Return a dummy list sized to items to signal OK.
        return new ArrayList<>(items);
    }
}
