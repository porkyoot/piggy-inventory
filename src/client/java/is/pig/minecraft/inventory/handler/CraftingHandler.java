package is.pig.minecraft.inventory.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ResultSlot;
import java.util.HashMap;
import java.util.Map;

public class CraftingHandler {
    private static final CraftingHandler INSTANCE = new CraftingHandler();
    private boolean isHolding = false;
    private long holdStartTime = 0;
    private Slot currentResultSlot = null;
    private Map<Integer, ItemStack> lastRecipe = new HashMap<>();

    public static CraftingHandler getInstance() {
        return INSTANCE;
    }

    public void onCraftingClick(Slot slot, Player player) {
        if (slot instanceof ResultSlot) {
            this.isHolding = true;
            this.holdStartTime = System.currentTimeMillis();
            this.currentResultSlot = slot;
            this.snapshotRecipe(player);
        }
    }

    public void onCraftingRelease() {
        this.isHolding = false;
        this.currentResultSlot = null;
        this.lastRecipe.clear();
    }

    private void snapshotRecipe(Player player) {
        lastRecipe.clear();
        // Snapshot current grid (excluding output)
        // Usually, container 0 is the player inventory/crafting screen open.
        // We need to iterate the slots of the OPEN container.
        if (player.containerMenu != null) {
            for (Slot s : player.containerMenu.slots) {
                // Skip result slot and player inventory slots
                // We want strictly crafting input slots.
                // How to identify? Usually index 1-4 (2x2) or 1-9 (3x3).
                // But ResultSlot is usually index 0.
                // Safety: Check if it's NOT ResultSlot and NOT PlayerInventory.
                if (!(s instanceof ResultSlot) && s.container != player.getInventory()) {
                    if (s.hasItem()) {
                        lastRecipe.put(s.index, s.getItem().copy());
                    }
                }
            }
        }
    }

    public void onTick(Minecraft client) {
        if (!isHolding || currentResultSlot == null || client.player == null || client.gameMode == null)
            return;

        // Check duration (300ms)
        if (System.currentTimeMillis() - holdStartTime < 300)
            return;

        // Check if output has item (meaning craftable)
        if (!currentResultSlot.hasItem()) {
            // Grid might be empty, try refill
            if (this.tryRefill(client.player, client)) {
                // Return to wait for server update
                return;
            } else {
                // Cannot refill (no items or unknown recipe), stop
                this.onCraftingRelease();
                return;
            }
        }

        // If we have an output, Simulate Shift Click
        client.gameMode.handleInventoryMouseClick(
                client.player.containerMenu.containerId,
                currentResultSlot.index,
                0,
                net.minecraft.world.inventory.ClickType.QUICK_MOVE,
                client.player);
    }

    private boolean tryRefill(Player player, Minecraft client) {
        boolean refilled = false;

        // Group destination slots by Ingredient Type
        Map<is.pig.minecraft.inventory.handler.CraftingHandler.IngredientKey, java.util.List<Integer>> ingredientsToSlots = new HashMap<>();

        for (Map.Entry<Integer, ItemStack> entry : lastRecipe.entrySet()) {
            int slotIndex = entry.getKey();
            ItemStack requiredStack = entry.getValue();

            // Check if slot needs refill (is empty or has same item but not full)
            Slot currentSlot = player.containerMenu.getSlot(slotIndex);

            // If slot is empty, we definitely need to fill it.
            // If slot has item, but count < 64, we can top it up?
            // "Continuous Crafting" implies we want to fill it up.
            // But we must assume the recipe hasn't changed.
            // For now, if slot is empty, we fill.
            // If slot is not empty, we check if it matches requiredStack. If so, we can add
            // to it.

            boolean needsItem = !currentSlot.hasItem() ||
                    (ItemStack.isSameItemSameComponents(currentSlot.getItem(), requiredStack)
                            && currentSlot.getItem().getCount() < currentSlot.getItem().getMaxStackSize());

            if (needsItem) {
                IngredientKey key = new IngredientKey(requiredStack);
                ingredientsToSlots.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(slotIndex);
            }
        }

        if (ingredientsToSlots.isEmpty())
            return false; // Full or invalid

        // Process each ingredient
        for (Map.Entry<IngredientKey, java.util.List<Integer>> entry : ingredientsToSlots.entrySet()) {
            IngredientKey key = entry.getKey();
            java.util.List<Integer> targetSlots = entry.getValue();

            // Find sources in inventory
            java.util.List<Integer> sourceSlots = findItemSlotsInInventory(player, key.stack);

            if (sourceSlots.isEmpty()) {
                // If we can't find specific ingredient, fail?
                // Or continue to try others? If recipe incomplete, craft will fail anyway.
                // We should probably fail to stop loop.
                return false;
            }

            // Distribute sources to targets using Drag
            for (int sourceSlot : sourceSlots) {
                // Pick up source
                client.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, sourceSlot, 0,
                        net.minecraft.world.inventory.ClickType.PICKUP, player);

                // Start Left Drag (Button 0)
                client.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, -999, 0,
                        net.minecraft.world.inventory.ClickType.QUICK_CRAFT, player);

                // Add Slots (Button 1)
                for (int targetSlot : targetSlots) {
                    client.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, targetSlot, 1,
                            net.minecraft.world.inventory.ClickType.QUICK_CRAFT, player);
                }

                // End Drag (Button 2)
                client.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, -999, 2,
                        net.minecraft.world.inventory.ClickType.QUICK_CRAFT, player);

                // If cursor still has items (remainder), put back to inventory?
                // The drag logic deposits evenly. Remainder stays in mouse.
                // We should put it back.
                if (!client.player.containerMenu.getCarried().isEmpty()) {
                    client.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, sourceSlot, 0,
                            net.minecraft.world.inventory.ClickType.PICKUP, player);
                }

                refilled = true;

                // Check if targets are full?
                // We simple drag all available sources until targets full.
                // Optimally we stop if targets full. But simplistic approach: drag all sources.
                // Vanilla prevents overfilling (excess stays in cursor).
            }
        }

        return refilled;
    }

    private java.util.List<Integer> findItemSlotsInInventory(Player player, ItemStack target) {
        java.util.List<Integer> slots = new java.util.ArrayList<>();
        for (Slot slot : player.containerMenu.slots) {
            // Only search Player Inventory (storage + hotbar)
            // usually checking `slot.container == player.getInventory()` is enough
            if (slot.container == player.getInventory()) {
                if (ItemStack.isSameItemSameComponents(target, slot.getItem())) {
                    slots.add(slot.index);
                }
            }
        }
        return slots;
    }

    // Helper class for grouping Map keys by Item similarity
    private static class IngredientKey {
        private final ItemStack stack;

        public IngredientKey(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            IngredientKey that = (IngredientKey) o;
            return ItemStack.isSameItemSameComponents(stack, that.stack);
        }

        @Override
        public int hashCode() {
            // Hash based on Item type + Components
            // ItemStack.hashItemAndComponents? No public method usually.
            // Use Item hash. Components might be complex.
            // For now, simple item hash is okay, collision handled by equals.
            return stack.getItem().hashCode();
        }
    }
}
