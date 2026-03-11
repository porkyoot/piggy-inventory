package is.pig.minecraft.inventory.sorting;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public class StackMerger {

    public static void merge(List<ItemStack> items, List<Slot> slots) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            
            int stackLimit = 0;
            for (Slot slot : slots) {
                stackLimit = Math.max(stackLimit, slot.getMaxStackSize(stack));
            }
            if (stackLimit <= 0) stackLimit = stack.getMaxStackSize();
            
            if (stack.isEmpty() || stack.getCount() >= stackLimit) {
                continue;
            }

            for (int j = i + 1; j < items.size(); j++) {
                ItemStack other = items.get(j);
                if (other.isEmpty()) continue;

                if (ItemStack.isSameItemSameComponents(stack, other)) {
                    int transfer = Math.min(other.getCount(), stackLimit - stack.getCount());
                    if (transfer > 0) {
                        stack.grow(transfer);
                        other.shrink(transfer);
                        if (stack.getCount() >= stackLimit) {
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