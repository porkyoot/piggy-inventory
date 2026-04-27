package is.pig.minecraft.inventory.common;

import is.pig.minecraft.inventory.api.IModAdapter;
import is.pig.minecraft.inventory.api.IInventoryManager;
import java.util.UUID;

public class PiggyCore {
    private static IModAdapter adapter;

    public static void init(IModAdapter modAdapter) {
        adapter = modAdapter;
        System.out.println("piggy-inventory: PiggyCore initialized with " + modAdapter.getClass().getSimpleName());
        
        IInventoryManager inventoryManager = adapter.getInventoryManager();
        if (inventoryManager != null) {
            UUID dummyUuid = UUID.randomUUID();
            boolean hasItem = inventoryManager.hasItem(dummyUuid, "minecraft:diamond");
            System.out.println("piggy-inventory: Dependency Inversion check: hasItem returned " + hasItem);
        }
    }

    public static IModAdapter getAdapter() {
        return adapter;
    }
}
