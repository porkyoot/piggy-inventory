package is.pig.minecraft.inventory.modern;

import is.pig.minecraft.inventory.api.IModAdapter;
import is.pig.minecraft.inventory.api.IInventoryManager;
import is.pig.minecraft.inventory.api.IPlayerTracker;
import is.pig.minecraft.inventory.api.INetworkDispatcher;

public class ModernModAdapter implements IModAdapter {
    private final IInventoryManager inventoryManager = new ModernInventoryManager();

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
