package is.pig.minecraft.inventory.sorting;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StackMerger {
    private static final Logger LOGGER = LoggerFactory.getLogger("piggy-inventory-merger");

    public static void merge(List<ItemStack> items, List<Slot> slots) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            
            int stackLimit = 0;
            for (Slot slot : slots) {
                stackLimit = Math.max(stackLimit, slot.getMaxStackSize());
                stackLimit = Math.max(stackLimit, slot.getMaxStackSize(stack));
            }
            if (stackLimit <= 0) stackLimit = stack.getMaxStackSize();
            
            LOGGER.info("StackMerger: Processing item at index {}: {} x{}, limit={}", i, stack.getItem(), stack.getCount(), stackLimit);
            
            if (stack.isEmpty() || stack.getCount() >= stackLimit) {
                continue;
            }

            for (int j = i + 1; j < items.size(); j++) {
                ItemStack other = items.get(j);
                if (other.isEmpty()) continue;

                if (ItemStack.isSameItemSameComponents(stack, other)) {
                    int transfer = Math.min(other.getCount(), stackLimit - stack.getCount());
                    if (transfer > 0) {
                        LOGGER.info("StackMerger: Merging {} from index {} -> index {} (before: stack={}, other={})", transfer, j, i, stack.getCount(), other.getCount());
                        stack.grow(transfer);
                        other.shrink(transfer);
                        LOGGER.info("StackMerger: After merge: stack={}, other={}", stack.getCount(), other.getCount());
                        if (stack.getCount() >= stackLimit) {
                            LOGGER.info("StackMerger: Stack at index {} reached max capacity ({})", i, stackLimit);
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