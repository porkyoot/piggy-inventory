package is.pig.minecraft.inventory.sorting.layout;

import net.minecraft.world.item.ItemStack;
import java.util.Comparator;
import java.util.List;

public class RowLayout extends AbstractDepthLayout {

    public RowLayout(List<Comparator<ItemStack>> comparators) {
        super(comparators);
    }

    @Override
    protected boolean fitsLayout(List<Group> groups, List<net.minecraft.world.inventory.Slot> availableSlots, int padding) {
        int rowWidth = getRowWidth(availableSlots);
        int totalSpacesRequired = 0;
        
        for (Group group : groups) {
            totalSpacesRequired += group.size();
        }
        
        // Each group beyond the first requires us to break to the next row (worst case padding)
        int breaks = groups.size() - 1;
        int breakSpace = (rowWidth - 1) + (padding * rowWidth);
        int worstCaseSize = totalSpacesRequired + breaks * breakSpace;
        
        return worstCaseSize <= availableSlots.size();
    }

    @Override
    protected List<ItemStack> emitGroups(List<Group> groups, List<net.minecraft.world.inventory.Slot> availableSlots, int padding) {
        ItemStack[] grid = new ItemStack[availableSlots.size()];
        int cursor = 0;

        for (int g = 0; g < groups.size(); g++) {
            Group group = groups.get(g);
            for (ItemStack s : group.items) {
                if (cursor < availableSlots.size()) {
                    grid[cursor] = s;
                    cursor++;
                }
            }
            
            // Snap to the next row for the next group
            if (g < groups.size() - 1 && cursor > 0 && cursor < availableSlots.size()) {
                int currentY = availableSlots.get(cursor - 1).y;
                while (cursor < availableSlots.size() && availableSlots.get(cursor).y == currentY) {
                    cursor++;
                }
                // Advance 'padding' extra rows
                for (int p = 0; p < padding; p++) {
                    if (cursor < availableSlots.size()) {
                        int padY = availableSlots.get(cursor).y;
                        while (cursor < availableSlots.size() && availableSlots.get(cursor).y == padY) {
                            cursor++;
                        }
                    }
                }
            }
        }

        List<ItemStack> output = new java.util.ArrayList<>();
        for (int i = 0; i < availableSlots.size(); i++) {
            output.add(grid[i] != null ? grid[i] : ItemStack.EMPTY);
        }

        return fillEmpty(output, availableSlots.size());
    }

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
}
