package is.pig.minecraft.inventory.util;

import is.pig.minecraft.api.registry.PiggyServiceRegistry;
import is.pig.minecraft.api.spi.InputAdapter;
import is.pig.minecraft.api.spi.InventoryInteractionAdapter;
import is.pig.minecraft.api.spi.ItemDataAdapter;
import is.pig.minecraft.api.spi.ScreenAdapter;
import is.pig.minecraft.inventory.locking.SlotLockingManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Platform-agnostic inventory utilities using SPI adapters.
 */
public class InventoryUtils {

    public static boolean isLootMatchingDown() {
        InputAdapter input = PiggyServiceRegistry.getInputAdapter();
        if (input.isKeyDown("piggy-inventory:loot_matching")) {
            return true;
        }
        // Fallback to shift if unbound (handled by adapter in practice)
        return false;
    }

    public static boolean isLootAllDown() {
        InputAdapter input = PiggyServiceRegistry.getInputAdapter();
        if (input.isKeyDown("piggy-inventory:loot_all")) {
            return true;
        }
        // Fallback to control if unbound
        return false;
    }

    public static boolean isLockDown() {
        return PiggyServiceRegistry.getInputAdapter().isKeyDown("piggy-inventory:lock_slot");
    }

    public static boolean handleScrollTransfer(Object screen, double scrollDelta, boolean forceMoveAll) {
        Object client = PiggyServiceRegistry.getWorldStateAdapter().getClient(); // Context object
        ScreenAdapter screenAdapter = PiggyServiceRegistry.getScreenAdapter();
        
        if (!screenAdapter.isContainerScreenOpen(client)) return false;

        List<Integer> playerSlots = screenAdapter.getPlayerSlotIndices(client);
        List<Integer> storageSlots = screenAdapter.getStorageSlotIndices(client);

        if (storageSlots.isEmpty()) return false;

        boolean moveUp = scrollDelta > 0; // Inventory -> Storage
        List<Integer> sourceSlots = moveUp ? playerSlots : storageSlots;
        List<Integer> targetSlots = moveUp ? storageSlots : playerSlots;

        ItemDataAdapter itemAdapter = PiggyServiceRegistry.getItemDataAdapter();
        InventoryInteractionAdapter interactionAdapter = PiggyServiceRegistry.getInventoryInteractionAdapter();
        int containerId = screenAdapter.getContainerId(client);

        boolean actionTaken = false;

        for (int sourceIdx : sourceSlots) {
            Object sourceStack = screenAdapter.getStackInSlot(client, sourceIdx);
            if (itemAdapter.getCount(sourceStack) <= 0) continue;

            if (SlotLockingManager.getInstance().isLocked(sourceIdx)) {
                continue;
            }

            boolean performTransfer = false;
            if (forceMoveAll) {
                performTransfer = true;
            } else {
                for (int targetIdx : targetSlots) {
                    Object targetStack = screenAdapter.getStackInSlot(client, targetIdx);
                    if (itemAdapter.getCount(targetStack) > 0 && itemAdapter.areItemsEqual(sourceStack, targetStack)) {
                        performTransfer = true;
                        break;
                    }
                }
            }

            if (performTransfer) {
                interactionAdapter.clickSlot(client, containerId, sourceIdx, 0, "QUICK_MOVE");
                actionTaken = true;
            }
        }

        return actionTaken;
    }

    public static List<Integer> getSlotsToTransfer(Object screen, double scrollDelta, boolean forceMoveAll) {
        List<Integer> slotsToMove = new ArrayList<>();
        Object client = PiggyServiceRegistry.getWorldStateAdapter().getClient();
        ScreenAdapter screenAdapter = PiggyServiceRegistry.getScreenAdapter();
        
        if (!screenAdapter.isContainerScreenOpen(client)) return slotsToMove;

        List<Integer> playerSlots = screenAdapter.getPlayerSlotIndices(client);
        List<Integer> storageSlots = screenAdapter.getStorageSlotIndices(client);

        if (storageSlots.isEmpty()) return slotsToMove;

        boolean moveUp = scrollDelta > 0;
        List<Integer> sourceSlots = moveUp ? playerSlots : storageSlots;
        List<Integer> targetSlots = moveUp ? storageSlots : playerSlots;

        ItemDataAdapter itemAdapter = PiggyServiceRegistry.getItemDataAdapter();

        for (int sourceIdx : sourceSlots) {
            Object sourceStack = screenAdapter.getStackInSlot(client, sourceIdx);
            if (itemAdapter.getCount(sourceStack) <= 0) continue;

            if (SlotLockingManager.getInstance().isLocked(sourceIdx)) {
                continue;
            }

            boolean performTransfer = false;
            if (forceMoveAll) {
                performTransfer = true;
            } else {
                for (int targetIdx : targetSlots) {
                    Object targetStack = screenAdapter.getStackInSlot(client, targetIdx);
                    if (itemAdapter.getCount(targetStack) > 0 && itemAdapter.areItemsEqual(sourceStack, targetStack)) {
                        performTransfer = true;
                        break;
                    }
                }
            }

            if (performTransfer) {
                slotsToMove.add(sourceIdx);
            }
        }

        return slotsToMove;
    }
}
