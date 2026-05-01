package is.pig.minecraft.inventory.locking;

import is.pig.minecraft.api.registry.PiggyServiceRegistry;
import is.pig.minecraft.api.spi.ScreenAdapter;
import is.pig.minecraft.inventory.config.PiggyInventoryConfig;

import java.util.Set;

/**
 * Manages locked slots for inventory screens.
 */
public class SlotLockingManager {

    private static final SlotLockingManager INSTANCE = new SlotLockingManager();

    public static SlotLockingManager getInstance() {
        return INSTANCE;
    }

    public boolean isLocked(Object slot) {
        ScreenAdapter adapter = PiggyServiceRegistry.getScreenAdapter();
        if (!adapter.isPlayerInventorySlot(slot)) {
            return false;
        }

        PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();

        // Init defaults if empty (First Run)
        if (config.getLockedPlayerSlots().isEmpty()) {
            initDefaultLocks(config);
        }

        return config.getLockedPlayerSlots().contains(adapter.getSlotIndex(slot));
    }

    public void toggleLock(Object slot) {
        ScreenAdapter adapter = PiggyServiceRegistry.getScreenAdapter();
        if (!adapter.isPlayerInventorySlot(slot)) {
            return;
        }

        PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();
        Set<Integer> locks = config.getLockedPlayerSlots();
        int idx = adapter.getSlotIndex(slot);

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
