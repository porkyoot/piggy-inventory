package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.api.registry.PiggyServiceRegistry;
import is.pig.minecraft.api.spi.ItemDataAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for consolidating item stacks effectively using pure Java and SPI adapters.
 */
public class StackMerger {

    public record Click(int slotIndex, int button, String clickType) {}

    public static List<Click> merge(List<Object> items, List<Boolean> protectedSlots, boolean generateClicks) {
        List<Click> actions = new ArrayList<>();
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        
        for (int i = 0; i < items.size(); i++) {
            if (protectedSlots != null && i < protectedSlots.size() && protectedSlots.get(i)) continue;
            
            Object stack = items.get(i);
            if (stack == null) continue;

            int stackLimit = adapter.getMaxStackSize(stack);
            
            if (adapter.getCount(stack) <= 0 || adapter.getCount(stack) >= stackLimit) {
                continue;
            }

            for (int j = i + 1; j < items.size(); j++) {
                if (protectedSlots != null && j < protectedSlots.size() && protectedSlots.get(j)) continue;
                
                Object other = items.get(j);
                if (other == null || adapter.getCount(other) <= 0) continue;

                if (adapter.areItemsEqual(stack, other)) {
                    int cursorLimit = Math.min(64, adapter.getMaxStackSize(stack));
                    if (cursorLimit <= 0) cursorLimit = 64;
                    
                    while (adapter.getCount(other) > 0 && adapter.getCount(stack) < stackLimit) {
                        int pickUp = Math.min(adapter.getCount(other), cursorLimit);
                        if (pickUp <= 0) break;
                        
                        if (generateClicks) {
                            actions.add(new Click(j, 0, "PICKUP"));
                            actions.add(new Click(i, 0, "PICKUP"));
                        }
                        
                        int dropped = Math.min(pickUp, stackLimit - adapter.getCount(stack));
                        adapter.grow(stack, dropped);
                        adapter.shrink(other, dropped);
                        
                        int returned = pickUp - dropped;
                        if (returned > 0 && generateClicks) {
                            actions.add(new Click(j, 0, "PICKUP"));
                        }
                        
                        if (adapter.getCount(other) <= 0) {
                            break;
                        }
                    }
                    
                    if (adapter.getCount(stack) >= stackLimit) {
                        break;
                    }
                }
            }
        }
        
        return actions;
    }
}