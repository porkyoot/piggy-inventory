package is.pig.minecraft.inventory.api;

public interface IModAdapter {
    IInventoryManager getInventoryManager();
    IPlayerTracker getPlayerTracker();
    INetworkDispatcher getNetworkDispatcher();
}
