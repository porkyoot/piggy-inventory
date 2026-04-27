package is.pig.minecraft.inventory.api;

public interface IPlayerController {
    void clickInventorySlot(int syncId, int slotId, int button, String clickType);
    void attackEntity(int entityId);
    void startBreakingBlock(int x, int y, int z, String face);
    void setCameraRotation(float yaw, float pitch);
}
