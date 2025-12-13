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

        // Only refill if we are playing (no screen open) to avoid issues when moving
        // items in inventory
        if (client.screen != null) {
            // Update trackers but do not trigger actions to prevent "refill while moving"
            // bugs
            lastMainHandStack = currentMainStack.copy();
            lastOffHandStack = currentOffStack.copy();
            return;
        }

        boolean actionTaken = false;

        // Check Main Hand
        if (shouldTriggerRefill(lastMainHandStack, currentMainStack)) {
            if (this.attemptRefill(client, player, lastMainHandStack, currentSlot)) { // Hotbar slot 0-8
                // We swapped 'lastMainHandStack' back into the slot.
                // To prevent the next tick from seeing (SwappedItem -> OriginalItem) as a
                // transition,
                // we FORCE the lastState to match what we just put there.
                lastMainHandStack = lastMainHandStack.copy(); // It is what we wanted
                actionTaken = true;
            }
        }

        // Check Off Hand
        if (!actionTaken && shouldTriggerRefill(lastOffHandStack, currentOffStack)) {
            // Offhand target slot ID is 45 in inventory menu
            if (this.attemptRefill(client, player, lastOffHandStack, 45)) { // Special ID for Offhand
                lastOffHandStack = lastOffHandStack.copy();
                actionTaken = true;
            }
        }

        if (!actionTaken) {
            // Update trackers normally
            lastMainHandStack = currentMainStack.copy();
            lastOffHandStack = currentOffStack.copy();
        }

        lastSlot = currentSlot;
    }

    private boolean attemptRefill(Minecraft client, Player player, ItemStack previousStack, int targetSlotId) {
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
                client.gameMode.handleInventoryMouseClick(0, bestSlot, 0,
                        net.minecraft.world.inventory.ClickType.PICKUP, player);
                client.gameMode.handleInventoryMouseClick(0, 45, 0, net.minecraft.world.inventory.ClickType.PICKUP,
                        player);
            }
            return true;
        }
        return false;
    }

    private boolean shouldTriggerRefill(ItemStack oldStack, ItemStack newStack) {
        if (oldStack.isEmpty())
            return false;

        // standard case: stack depleted
        if (newStack.isEmpty())
            return true;

        net.minecraft.world.item.Item oldItem = oldStack.getItem();
        net.minecraft.world.item.Item newItem = newStack.getItem();

        if (oldItem == newItem)
            return false;

        // --- Full -> Empty (Consuming/Placing) ---

        // Potions / Honey -> Bottle
        if ((oldItem instanceof net.minecraft.world.item.PotionItem
                || oldItem instanceof net.minecraft.world.item.HoneyBottleItem)
                && newItem == net.minecraft.world.item.Items.GLASS_BOTTLE) {
            return true;
        }

        // Soups / Stews -> Bowl
        if ((oldItem == net.minecraft.world.item.Items.MUSHROOM_STEW
                || oldItem == net.minecraft.world.item.Items.RABBIT_STEW
                || oldItem == net.minecraft.world.item.Items.BEETROOT_SOUP
                || oldItem == net.minecraft.world.item.Items.SUSPICIOUS_STEW)
                && newItem == net.minecraft.world.item.Items.BOWL) {
            return true;
        }

        // Full Bucket -> Empty Bucket
        if ((oldItem instanceof net.minecraft.world.item.BucketItem
                || oldItem instanceof net.minecraft.world.item.MilkBucketItem
                || oldItem instanceof net.minecraft.world.item.SolidBucketItem)
                && newItem == net.minecraft.world.item.Items.BUCKET) {
            // Ensure old was NOT empty bucket
            return oldItem != net.minecraft.world.item.Items.BUCKET;
        }

        // --- Empty -> Full (Filling Containers) ---

        // Bottle -> Potion/Honey/DragonBreath
        if (oldItem == net.minecraft.world.item.Items.GLASS_BOTTLE &&
                (newItem instanceof net.minecraft.world.item.PotionItem
                        || newItem instanceof net.minecraft.world.item.HoneyBottleItem
                        || newItem == net.minecraft.world.item.Items.DRAGON_BREATH)) {
            return true;
        }

        // Bowl -> Soup/Stew
        if (oldItem == net.minecraft.world.item.Items.BOWL &&
                (newItem == net.minecraft.world.item.Items.MUSHROOM_STEW
                        || newItem == net.minecraft.world.item.Items.RABBIT_STEW
                        || newItem == net.minecraft.world.item.Items.BEETROOT_SOUP
                        || newItem == net.minecraft.world.item.Items.SUSPICIOUS_STEW)) {
            return true;
        }

        // Empty Bucket -> Full Bucket
        if (oldItem == net.minecraft.world.item.Items.BUCKET &&
                (newItem instanceof net.minecraft.world.item.BucketItem
                        || newItem instanceof net.minecraft.world.item.MilkBucketItem
                        || newItem instanceof net.minecraft.world.item.SolidBucketItem)) {
            return true;
        }

        return false;
    }
}
