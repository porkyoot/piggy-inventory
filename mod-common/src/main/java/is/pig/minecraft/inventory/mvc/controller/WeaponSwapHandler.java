package is.pig.minecraft.inventory.mvc.controller;

import is.pig.minecraft.inventory.api.IInventoryGrid;
import is.pig.minecraft.inventory.api.IItemProperties;

/**
 * Pure Java weapon selection logic.
 */
public class WeaponSwapHandler {
    public int getBestWeaponSlot(IInventoryGrid grid, IItemProperties properties, String entityId) {
        int bestSlot = -1;
        int maxDamage = -1;
        
        for (int i = 0; i < grid.getSize(); i++) {
            String itemId = grid.getItemIdentifierAt(i);
            if (itemId == null || itemId.equals("minecraft:air")) continue;
            
            int damage = properties.getDamage(itemId);
            if (damage > maxDamage) {
                maxDamage = damage;
                bestSlot = i;
            }
        }
        return bestSlot;
    }
}
