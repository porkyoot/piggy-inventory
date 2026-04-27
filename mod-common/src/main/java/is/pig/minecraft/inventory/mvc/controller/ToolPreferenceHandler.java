package is.pig.minecraft.inventory.mvc.controller;

import is.pig.minecraft.inventory.api.IInventoryGrid;
import is.pig.minecraft.inventory.api.IItemProperties;

/**
 * Pure Java tool selection logic.
 */
public class ToolPreferenceHandler {
    public int getBestToolSlot(IInventoryGrid grid, IItemProperties properties, String blockId) {
        int bestSlot = -1;
        int maxEnchant = -1;
        
        for (int i = 0; i < grid.getSize(); i++) {
            String itemId = grid.getItemIdentifierAt(i);
            if (itemId == null || itemId.equals("minecraft:air")) continue;
            
            if (properties.isTool(itemId)) {
                int enchant = properties.getEnchantmentLevel(itemId, "efficiency");
                if (enchant > maxEnchant) {
                    maxEnchant = enchant;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }
}
