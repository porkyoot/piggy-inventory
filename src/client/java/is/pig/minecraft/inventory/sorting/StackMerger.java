package is.pig.minecraft.inventory.sorting;

import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class StackMerger {

    public record Click(int slotIndex, int button, ClickType type) {}

    // Overloaded method to maintain backward compatibility for tests and unrefactored code
    public static void merge(List<ItemStack> items, List<Slot> slots) {
        merge(items, slots, false);
    }

    public static List<Click> merge(List<ItemStack> items, List<Slot> slots, boolean generateClicks) {
        return merge(items, slots, null, generateClicks);
    }

    public static List<Click> merge(List<ItemStack> items, List<Slot> slots, List<Boolean> protectedSlots, boolean generateClicks) {
        List<Click> actions = new ArrayList<>();
        
        for (int i = 0; i < items.size(); i++) {
            if (protectedSlots != null && i < protectedSlots.size() && protectedSlots.get(i)) continue;
            
            ItemStack stack = items.get(i);
            
            int stackLimit = stack.getMaxStackSize(); // Vanilla default is usually 64
            for (Slot slot : slots) {
                // Determine if slot explicitly supports larger stacks
                if (slot.getMaxStackSize(stack) > stackLimit) {
                    stackLimit = slot.getMaxStackSize(stack);
                } else if (slot.getMaxStackSize() > 99 && slot.getMaxStackSize() > stackLimit) {
                    // Fallback for deep storage mods overriding no-arg version
                    // Note: vanilla default for Container.getMaxStackSize() is 99 in 1.20.5+
                    stackLimit = slot.getMaxStackSize();
                }
            }
            
            if (stack.isEmpty() || stack.getCount() >= stackLimit) {
                continue;
            }

            for (int j = i + 1; j < items.size(); j++) {
                if (protectedSlots != null && j < protectedSlots.size() && protectedSlots.get(j)) continue;
                
                ItemStack other = items.get(j);
                if (other.isEmpty()) continue;

                if (ItemStack.isSameItemSameComponents(stack, other)) {
                    // Vanilla cursor limit is 64, or the item's max vanilla stack size
                    int cursorLimit = Math.min(64, stack.getMaxStackSize());
                    if (cursorLimit <= 0) cursorLimit = 64;
                    
                    while (!other.isEmpty() && stack.getCount() < stackLimit) {
                        int pickUp = Math.min(other.getCount(), cursorLimit);
                        if (pickUp <= 0) break;
                        
                        if (generateClicks) {
                            // Action 1: Left-click source slot (picks up max 64 items)
                            actions.add(new Click(slots.get(j).index, 0, ClickType.PICKUP));
                            
                            // Action 2: Left-click target slot (drops as much as target can hold)
                            actions.add(new Click(slots.get(i).index, 0, ClickType.PICKUP));
                        }
                        
                        int dropped = Math.min(pickUp, stackLimit - stack.getCount());
                        stack.grow(dropped);
                        other.shrink(dropped);
                        
                        int returned = pickUp - dropped;
                        if (returned > 0 && generateClicks) {
                            // Action 3: Left-click source slot to return remainder from cursor
                            actions.add(new Click(slots.get(j).index, 0, ClickType.PICKUP));
                        }
                        
                        if (other.getCount() <= 0) {
                            items.set(j, ItemStack.EMPTY);
                            break;
                        }
                    }
                    
                    if (stack.getCount() >= stackLimit) {
                        break;
                    }
                }
            }
        }
        
        return actions;
    }
}