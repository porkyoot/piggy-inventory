package is.pig.minecraft.inventory.api;

/**
 * A generic wrapper for a chest or player inventory.
 * Pure Java interface.
 */
public interface IInventoryGrid {
    int getSize();
    String getItemIdentifierAt(int slot);
    int getCountAt(int slot);
    int getMaxStackSize(int slot);
    void swap(int slotA, int slotB);
}
