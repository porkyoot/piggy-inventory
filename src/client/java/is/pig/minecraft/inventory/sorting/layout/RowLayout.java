package is.pig.minecraft.inventory.sorting.layout;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class RowLayout extends AbstractDepthLayout {

    @Override
    protected List<ItemStack> simulateLayout(List<ItemStack> items, List<net.minecraft.world.inventory.Slot> availableSlots, int depth) {
        List<ItemStack> output = new ArrayList<>();
        
        ItemStack previous = null;
        for (ItemStack item : items) {
            if (previous != null && shouldBreakGroup(previous, item, depth)) {
                // Determine how many empty spaces we need to reach the next physical row
                if (!output.isEmpty() && output.size() < availableSlots.size()) {
                    int currentY = availableSlots.get(output.size() - 1).y;
                    while (output.size() < availableSlots.size()) {
                        net.minecraft.world.inventory.Slot nextSlot = availableSlots.get(output.size());
                        if (nextSlot.y > currentY) {
                            break; // Reached a slot on a new physical row
                        }
                        output.add(ItemStack.EMPTY);
                    }
                }
            }
            
            output.add(item);
            previous = item;
        }

        return output;
    }
}
