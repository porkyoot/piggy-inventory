package is.pig.minecraft.inventory.legacy;

import is.pig.minecraft.inventory.api.IModAdapter;
import is.pig.minecraft.inventory.api.IInventoryManager;
import is.pig.minecraft.inventory.api.IPlayerTracker;
import is.pig.minecraft.inventory.api.INetworkDispatcher;

public class LegacyModAdapter implements IModAdapter {
    private final IInventoryManager inventoryManager = new LegacyInventoryManager();

    @Override
    public IInventoryManager getInventoryManager() {
        return inventoryManager;
    }

    @Override
    public IPlayerTracker getPlayerTracker() {
        return null;
    }

    @Override
    public INetworkDispatcher getNetworkDispatcher() {
        return null;
    }
}
