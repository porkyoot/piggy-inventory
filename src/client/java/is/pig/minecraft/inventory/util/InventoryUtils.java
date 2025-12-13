package is.pig.minecraft.inventory.util;

import is.pig.minecraft.inventory.locking.SlotLockingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

public class InventoryUtils {

    public static boolean isLootMatchingDown() {
        if (!is.pig.minecraft.inventory.PiggyInventoryClient.lootMatchingKey.isUnbound()) {
            return is.pig.minecraft.inventory.PiggyInventoryClient.lootMatchingKey.isDown();
        }
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }

    public static boolean isLootAllDown() {
        if (!is.pig.minecraft.inventory.PiggyInventoryClient.lootAllKey.isUnbound()) {
            return is.pig.minecraft.inventory.PiggyInventoryClient.lootAllKey.isDown();
        }
        return net.minecraft.client.gui.screens.Screen.hasControlDown();
    }

    // Legacy method redirection for compatibility with existing mixins until
    // updated
    public static boolean isShiftDown() {
        return isLootMatchingDown();
    }

    public static boolean isFastLootDown() {
        return isLootAllDown();
    }

    // Using legacy alt key mapping which we will restore
    public static boolean isLockDown() {
        return is.pig.minecraft.inventory.PiggyInventoryClient.lockKey.isDown();
    }

    /**
     * Handles scroll-based item transfer logic.
     * Moved from MixinMouseHandler to avoid Mixin restriction on public static
     * methods.
     */
    public static boolean handleScrollTransfer(AbstractContainerScreen<?> screen, double scrollDelta,
            boolean forceMoveAll) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null)
            return false;

        net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
        net.minecraft.world.entity.player.Inventory playerInventory = client.player.getInventory();

        // Separate slots
        java.util.List<Slot> storageSlots = new java.util.ArrayList<>();
        java.util.List<Slot> playerSlots = new java.util.ArrayList<>();

        for (Slot slot : menu.slots) {
            if (slot.container == playerInventory) {
                playerSlots.add(slot);
            } else {
                storageSlots.add(slot);
            }
        }

        // If no storage (e.g. inventory screen), do nothing
        if (storageSlots.isEmpty())
            return false;

        boolean moveUp = scrollDelta > 0; // Inventory -> Storage

        java.util.List<Slot> sourceSlots = moveUp ? playerSlots : storageSlots;
        java.util.List<Slot> targetSlots = moveUp ? storageSlots : playerSlots;

        boolean actionTaken = false;

        for (Slot sourceSlot : sourceSlots) {
            if (!sourceSlot.hasItem())
                continue;

            // Skip locked slots
            if (SlotLockingManager.getInstance().isLocked(sourceSlot)) {
                continue;
            }

            net.minecraft.world.item.ItemStack sourceStack = sourceSlot.getItem();
            boolean performTransfer = false;

            if (forceMoveAll) {
                // Ctrl+Scroll: Move everything
                performTransfer = true;
            } else {
                // Shift+Scroll: Smart Stack (match only)
                // Check if target has matching item
                for (Slot targetSlot : targetSlots) {
                    if (targetSlot.hasItem()) {
                        net.minecraft.world.item.ItemStack targetStack = targetSlot.getItem();
                        // Using ItemStack.isSameItemSameComponents for modern versions
                        if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(sourceStack, targetStack)) {
                            performTransfer = true;
                            break;
                        }
                    }
                }
            }

            if (performTransfer) {
                client.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot.index, 0,
                        net.minecraft.world.inventory.ClickType.QUICK_MOVE, client.player);
                actionTaken = true;
            }
        }

        return actionTaken;
    }
}
