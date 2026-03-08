package is.pig.minecraft.inventory.sorting.layout;

import net.minecraft.world.item.ItemStack;
import java.util.List;

public interface ISortingLayout {
    
    /**
     * Attempts to layout the given sorted items into the provided constraints,
     * utilizing empty padding for group separation.
     * 
     * @param items The pre-sorted list of items to place.
     * @param items The pre-sorted list of items to place.
     * @param availableSlots The sequential list of strictly unlocked physical slots available in the container.
     * @return A flat list of exact size availableSlots.size() containing the items spaced out with ItemStack.EMPTY.
     */
    List<ItemStack> layout(List<ItemStack> items, List<net.minecraft.world.inventory.Slot> availableSlots);
}
