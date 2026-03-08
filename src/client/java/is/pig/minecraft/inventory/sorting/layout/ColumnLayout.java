package is.pig.minecraft.inventory.sorting.layout;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ColumnLayout extends AbstractDepthLayout {

    public ColumnLayout(List<Comparator<ItemStack>> comparators) {
        super(comparators);
    }

    private int getColHeight(List<net.minecraft.world.inventory.Slot> slots) {
        if (slots.isEmpty()) return 1;
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

    @Override
    protected boolean fitsLayout(List<Group> groups, List<net.minecraft.world.inventory.Slot> availableSlots, int padding) {
        int colHeight = getColHeight(availableSlots);
        int totalSpacesRequired = 0;

        for (Group group : groups) {
            totalSpacesRequired += group.size();
        }

        int breaks = groups.size() - 1;
        int breakSpace = (colHeight - 1) + (padding * colHeight);
        int worstCaseSize = totalSpacesRequired + breaks * breakSpace;
        
        return worstCaseSize <= availableSlots.size();
    }

    @Override
    protected List<ItemStack> emitGroups(List<Group> groups, List<net.minecraft.world.inventory.Slot> availableSlots, int padding) {
        List<Integer> colMajorIndices = new ArrayList<>();
        for (int i = 0; i < availableSlots.size(); i++) colMajorIndices.add(i);
        colMajorIndices.sort((a, b) -> {
            net.minecraft.world.inventory.Slot sa = availableSlots.get(a);
            net.minecraft.world.inventory.Slot sb = availableSlots.get(b);
            return sa.x != sb.x ? Integer.compare(sa.x, sb.x) : Integer.compare(sa.y, sb.y);
        });

        ItemStack[] grid = new ItemStack[availableSlots.size()];
        int cursor = 0;

        for (int g = 0; g < groups.size(); g++) {
            Group group = groups.get(g);
            for (ItemStack s : group.items) {
                if (cursor < colMajorIndices.size()) {
                    grid[colMajorIndices.get(cursor)] = s;
                    cursor++;
                }
            }
            
            // Align to start of the next COLUMN
            if (g < groups.size() - 1 && cursor > 0 && cursor < colMajorIndices.size()) {
                int currentX = availableSlots.get(colMajorIndices.get(cursor - 1)).x;
                while (cursor < colMajorIndices.size() && availableSlots.get(colMajorIndices.get(cursor)).x == currentX) {
                    cursor++;
                }
                // Advance 'padding' extra columns
                for (int p = 0; p < padding; p++) {
                    if (cursor < colMajorIndices.size()) {
                        int padX = availableSlots.get(colMajorIndices.get(cursor)).x;
                        while (cursor < colMajorIndices.size() && availableSlots.get(colMajorIndices.get(cursor)).x == padX) {
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

        return fillEmpty(output, availableSlots.size());
    }
}