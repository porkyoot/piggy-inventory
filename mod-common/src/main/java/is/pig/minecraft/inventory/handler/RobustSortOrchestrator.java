package is.pig.minecraft.inventory.handler;

import is.pig.minecraft.inventory.api.IInventoryGrid;
import is.pig.minecraft.inventory.api.IItemProperties;

import java.util.Comparator;

/**
 * Pure Java sorting orchestrator.
 */
public class RobustSortOrchestrator {
    private static final RobustSortOrchestrator INSTANCE = new RobustSortOrchestrator();

    private RobustSortOrchestrator() {}

    public static RobustSortOrchestrator getInstance() {
        return INSTANCE;
    }

    public void sort(IInventoryGrid grid, IItemProperties properties, Comparator<String> itemComparator) {
        int size = grid.getSize();
        
        for (int i = 0; i < size - 1; i++) {
            int bestIdx = i;
            String bestItem = grid.getItemIdentifierAt(i);
            
            for (int j = i + 1; j < size; j++) {
                String currentItem = grid.getItemIdentifierAt(j);
                
                if (currentItem != null && !currentItem.equals("minecraft:air")) {
                    if (bestItem == null || bestItem.equals("minecraft:air") || itemComparator.compare(currentItem, bestItem) < 0) {
                        bestIdx = j;
                        bestItem = currentItem;
                    }
                }
            }
            
            if (bestIdx != i) {
                grid.swap(i, bestIdx);
            }
        }
    }
}
