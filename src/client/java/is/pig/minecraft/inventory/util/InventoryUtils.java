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

    // Use KeyMapping for Alt key detection to respect user binds
    // MODIFIED: Use direct polling because some mods break KeyMapping event
    // propagation
    public static boolean isLockDown() {
        if (is.pig.minecraft.inventory.PiggyInventoryClient.lockKey.isUnbound()) {
            return false;
        }

        com.mojang.blaze3d.platform.InputConstants.Key key = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
                .getBoundKeyOf(is.pig.minecraft.inventory.PiggyInventoryClient.lockKey);
        long window = Minecraft.getInstance().getWindow().getWindow();

        try {
            if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) {
                return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, key.getValue());
            } else if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
                return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            }
        } catch (Exception e) {
            // Fallback to vanilla
            return is.pig.minecraft.inventory.PiggyInventoryClient.lockKey.isDown();
        }

        return false;
    }

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
        boolean foundPlayerSlots = false;

        for (Slot slot : menu.slots) {
            if (slot.container == playerInventory) {
                playerSlots.add(slot);
                foundPlayerSlots = true;
            }
        }

        if (foundPlayerSlots) {
            for (Slot slot : menu.slots) {
                if (slot.container != playerInventory) {
                    storageSlots.add(slot);
                }
            }
        } else {
            // Fallback for modded screens (like Sophisticated Storage) that wrap the player inventory.
            // In typical chest GUIs, the last 36 slots (27 main inventory + 9 hotbar) belong to the player.
            int totalSlots = menu.slots.size();
            for (int i = 0; i < totalSlots; i++) {
                Slot slot = menu.slots.get(i);
                if (totalSlots >= 36 && i >= totalSlots - 36) {
                    playerSlots.add(slot);
                } else {
                    storageSlots.add(slot);
                }
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

    public static java.util.List<Integer> getSlotsToTransfer(AbstractContainerScreen<?> screen, double scrollDelta,
            boolean forceMoveAll) {
        java.util.List<Integer> slotsToMove = new java.util.ArrayList<>();
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null)
            return slotsToMove;

        net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
        net.minecraft.world.entity.player.Inventory playerInventory = client.player.getInventory();

        java.util.List<Slot> storageSlots = new java.util.ArrayList<>();
        java.util.List<Slot> playerSlots = new java.util.ArrayList<>();
        boolean foundPlayerSlots = false;

        for (Slot slot : menu.slots) {
            if (slot.container == playerInventory) {
                playerSlots.add(slot);
                foundPlayerSlots = true;
            }
        }

        if (foundPlayerSlots) {
            for (Slot slot : menu.slots) {
                if (slot.container != playerInventory) {
                    storageSlots.add(slot);
                }
            }
        } else {
            int totalSlots = menu.slots.size();
            for (int i = 0; i < totalSlots; i++) {
                Slot slot = menu.slots.get(i);
                if (totalSlots >= 36 && i >= totalSlots - 36) {
                    playerSlots.add(slot);
                } else {
                    storageSlots.add(slot);
                }
            }
        }

        if (storageSlots.isEmpty())
            return slotsToMove;

        boolean moveUp = scrollDelta > 0;

        java.util.List<Slot> sourceSlots = moveUp ? playerSlots : storageSlots;
        java.util.List<Slot> targetSlots = moveUp ? storageSlots : playerSlots;

        for (Slot sourceSlot : sourceSlots) {
            if (!sourceSlot.hasItem())
                continue;

            if (SlotLockingManager.getInstance().isLocked(sourceSlot)) {
                continue;
            }

            net.minecraft.world.item.ItemStack sourceStack = sourceSlot.getItem();
            boolean performTransfer = false;

            if (forceMoveAll) {
                performTransfer = true;
            } else {
                for (Slot targetSlot : targetSlots) {
                    if (targetSlot.hasItem()) {
                        net.minecraft.world.item.ItemStack targetStack = targetSlot.getItem();
                        if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(sourceStack, targetStack)) {
                            performTransfer = true;
                            break;
                        }
                    }
                }
            }

            if (performTransfer) {
                slotsToMove.add(sourceSlot.index);
            }
        }

        return slotsToMove;
    }
}
