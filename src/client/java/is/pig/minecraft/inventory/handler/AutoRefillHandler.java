package is.pig.minecraft.inventory.handler;

import is.pig.minecraft.inventory.refill.RefillCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class AutoRefillHandler {
    private static final AutoRefillHandler INSTANCE = new AutoRefillHandler();
    private ItemStack lastMainHandStack = ItemStack.EMPTY;
    private ItemStack lastOffHandStack = ItemStack.EMPTY;
    private int lastSlot = -1;

    public static AutoRefillHandler getInstance() {
        return INSTANCE;
    }

    public void onTick(Minecraft client) {
        Player player = client.player;
        if (player == null || client.gameMode == null)
            return;

        ItemStack currentMainStack = player.getMainHandItem();
        ItemStack currentOffStack = player.getOffhandItem();
        int currentSlot = player.getInventory().selected;

        // Detect manual slot switch - reset tracking
        if (currentSlot != lastSlot) {
            lastMainHandStack = currentMainStack.copy();
            lastOffHandStack = currentOffStack.copy();
            lastSlot = currentSlot;
            return;
        }

        // Check Main Hand
        boolean mainWasPresent = !lastMainHandStack.isEmpty();
        boolean mainNowEmpty = currentMainStack.isEmpty();
        if (mainWasPresent && mainNowEmpty) {
            this.attemptRefill(client, player, lastMainHandStack, currentSlot); // Hotbar slot 0-8
        }

        // Check Off Hand
        boolean offWasPresent = !lastOffHandStack.isEmpty();
        boolean offNowEmpty = currentOffStack.isEmpty();
        if (offWasPresent && offNowEmpty) {
            // Offhand target slot ID is 45 in inventory menu
            this.attemptRefill(client, player, lastOffHandStack, 45); // Special ID for Offhand
        }

        // Update trackers
        lastMainHandStack = currentMainStack.copy();
        lastOffHandStack = currentOffStack.copy();
        lastSlot = currentSlot;
    }

    private void attemptRefill(Minecraft client, Player player, ItemStack previousStack, int targetSlotId) {
        int bestSlot = -1;

        // 1. Search for Exact Match first (Always prioritized)
        for (int i = 9; i < 36; i++) { // Main Inventory (9-35)
            ItemStack candidate = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(previousStack, candidate)) {
                bestSlot = i;
                break;
            }
        }

        // 2. If no exact match, try Category Match
        if (bestSlot == -1) {
            RefillCategory category = RefillCategory.fromStack(previousStack);
            // Blocks are strictly exact match only (as per typical behavior), others can be
            // fuzzy
            if (category != RefillCategory.BLOCK && category != RefillCategory.OTHER) {
                for (int i = 9; i < 36; i++) {
                    ItemStack candidate = player.getInventory().getItem(i);
                    if (category.matches(previousStack, candidate)) {
                        bestSlot = i;
                        break;
                    }
                }
            }
        }

        if (bestSlot != -1) {
            // Check if target is Hotbar (0-8) or Offhand (45)
            // Note: targetSlotId passed for MainHand was 0-8 (Hotbar index), NOT Slot ID.
            // But for Offhand I passed 45 (Slot ID).
            // Need to distinguish.

            // Standardizing targetSlotId:
            // If < 9, treat as Hotbar Index for SWAP.
            // If == 45, treat as Offhand Slot ID for PICKUP/PLACE.

            if (targetSlotId < 9) {
                // Hotbar Swap
                client.gameMode.handleInventoryMouseClick(
                        0, // Player Inventory ID
                        bestSlot, // Slot to take from
                        targetSlotId, // Target Hotbar Slot (0-8)
                        net.minecraft.world.inventory.ClickType.SWAP,
                        player);
            } else if (targetSlotId == 45) {
                // Offhand Refill using PICKUP sequence
                // 1. Pickup from Storage
                client.gameMode.handleInventoryMouseClick(
                        0,
                        bestSlot,
                        0,
                        net.minecraft.world.inventory.ClickType.PICKUP,
                        player);
                // 2. Place in Offhand
                client.gameMode.handleInventoryMouseClick(
                        0,
                        45,
                        0,
                        net.minecraft.world.inventory.ClickType.PICKUP,
                        player);
            }
        }
    }
}
