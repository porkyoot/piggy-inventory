package is.pig.minecraft.inventory.telemetry;

import is.pig.minecraft.lib.util.telemetry.JsonHistoryStore;

/**
 * Manages the persistence of inventory operations and sorting cycles to the piggy-inventory.json history store.
 */
public class InventoryHistoryManager {
    private static JsonHistoryStore inventoryStore;

    public static void init() {
        inventoryStore = new JsonHistoryStore("piggy-inventory.json", event -> 
            event instanceof SortingCycleEvent || event.getEventKey().contains("inventory") || event.getEventKey().contains("sort")
        );
        inventoryStore.register();
    }

    public static JsonHistoryStore getStore() {
        return inventoryStore;
    }
}
