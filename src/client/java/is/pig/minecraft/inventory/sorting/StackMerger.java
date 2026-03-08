package is.pig.minecraft.inventory.sorting;

import net.minecraft.world.item.ItemStack;
import java.util.List;

public class StackMerger {

    public static void merge(List<ItemStack> items) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty() || stack.getCount() >= stack.getMaxStackSize()) {
                continue;
            }

            for (int j = i + 1; j < items.size(); j++) {
                ItemStack other = items.get(j);
                if (other.isEmpty()) continue;

                if (ItemStack.isSameItemSameComponents(stack, other)) {
                    int transfer = Math.min(other.getCount(), stack.getMaxStackSize() - stack.getCount());
                    if (transfer > 0) {
                        stack.grow(transfer);
                        other.shrink(transfer);
                        if (stack.getCount() >= stack.getMaxStackSize()) {
                            break;
                        }
                    }
                }
            }
        }
        
        // CRITICAL FIX: Strip all empty stacks so they don't break layout calculations!
        items.removeIf(ItemStack::isEmpty); 
    }
}