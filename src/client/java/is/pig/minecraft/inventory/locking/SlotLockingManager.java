package is.pig.minecraft.inventory.locking;

import is.pig.minecraft.inventory.config.PiggyInventoryConfig;

import java.util.Set;

/**
 * Manages locked slots for inventory screens.
 */
public class SlotLockingManager {

    private static final SlotLockingManager INSTANCE = new SlotLockingManager();

    // private final Map<String, Set<Integer>> lockedSlots = new HashMap<>(); //
    // Moved to Config

    public static SlotLockingManager getInstance() {
        return INSTANCE;
    }

    public boolean isLocked(net.minecraft.world.inventory.Slot slot) {
        PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();

        // Ensure we only lock player inventory slots
        if (slot.container != net.minecraft.client.Minecraft.getInstance().player.getInventory()) {
            return false;
        }

        // Init defaults if empty (First Run)
        if (config.getLockedPlayerSlots().isEmpty()) {
            initDefaultLocks(config);
        }

        return config.getLockedPlayerSlots().contains(slot.getContainerSlot());
    }

    public void toggleLock(net.minecraft.world.inventory.Slot slot) {
        if (slot.container != net.minecraft.client.Minecraft.getInstance().player.getInventory()) {
            return;
        }

        PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();
        Set<Integer> locks = config.getLockedPlayerSlots();
        int idx = slot.getContainerSlot();

        if (locks.contains(idx)) {
            locks.remove(idx);
        } else {
            locks.add(idx);
        }

        is.pig.minecraft.inventory.config.ConfigPersistence.save();
    }

    private void initDefaultLocks(PiggyInventoryConfig config) {
        // Lock local player hotbar: Indices 0-8 in Inventory (Hotbar)
        // Wait, PlayerInventory indices:
        // 0-8: Hotbar
        // 9-35: Storage
        // 36-39: Armor
        // 40: Offhand
        // Yes, 0-8 is hotbar.
        // User wants defaults.
        for (int i = 0; i < 9; i++) {
            config.getLockedPlayerSlots().add(i);
        }
        is.pig.minecraft.inventory.config.ConfigPersistence.save();
    }
}
