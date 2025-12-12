package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import is.pig.minecraft.inventory.locking.SlotLockingManager;
import is.pig.minecraft.lib.sorting.ISorter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes the client-side sorting logic.
 */
public class InventorySorter {

    public static void sortInventory(AbstractContainerScreen<?> screen, boolean sortPlayerInventory) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null)
            return;

        List<Slot> allSlots = screen.getMenu().slots;
        int startSlot, endSlot; // inclusive, exclusive

        // 1. Identify Range
        if (sortPlayerInventory) {
            // Player inventory is usually the last 36 slots (27 storage + 9 hotbar) in
            // standard containers
            // But getting exact range can be tricky depending on the container.
            // A heuristic: Player slots are usually at the end.
            int totalSlots = allSlots.size();
            startSlot = totalSlots - 36;
            endSlot = totalSlots;
        } else {
            // External container
            // Usually from 0 to Size - 36
            int totalSlots = allSlots.size();
            startSlot = 0;
            endSlot = totalSlots - 36;
            if (endSlot <= 0)
                return; // No external inventory
        }

        // 2. Snapshot & Extract Items
        List<ItemStack> extractableItems = new ArrayList<>();
        List<Integer> validSlotIndices = new ArrayList<>();

        for (int i = startSlot; i < endSlot; i++) {
            Slot slot = allSlots.get(i);

            // Skip locked slots
            if (SlotLockingManager.getInstance().isLocked(screen, slot.index)) {
                continue;
            }

            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                // Check blacklist
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (((PiggyInventoryConfig) PiggyInventoryConfig.getInstance()).getBlacklistedItems()
                        .contains(itemId)) {
                    continue; // Treat as locked/immovable
                }

                extractableItems.add(stack.copy());
            }
            validSlotIndices.add(slot.index);
        }

        if (validSlotIndices.isEmpty() || extractableItems.isEmpty())
            return;

        // 3. Sort Logic
        // 3. Sort Logic
        ISorter sorter = getCurrentSorter();
        sorter.sort(extractableItems);

        // 4. Layout
        List<ItemStack> layoutItems = LayoutEngine.applyLayout(extractableItems, validSlotIndices.size(), 9);

        // 5. Diff & Execution
        if (client.player.isCreative()) {
            executeCreativeSort(client, validSlotIndices, layoutItems);
        } else {
            executeSurvivalSort(client, screen, validSlotIndices, layoutItems);
        }
    }

    private static void executeCreativeSort(Minecraft client, List<Integer> slots, List<ItemStack> sortedItems) {
        int itemIndex = 0;
        for (int slotIndex : slots) {
            ItemStack desired = itemIndex < sortedItems.size() ? sortedItems.get(itemIndex) : ItemStack.EMPTY;
            client.player.connection.send(
                    new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(slotIndex, desired));
            itemIndex++;
        }
    }

    private static void executeSurvivalSort(Minecraft client, AbstractContainerScreen<?> screen,
            List<Integer> slotIndices, List<ItemStack> sortedItems) {
        int containerId = screen.getMenu().containerId;

        // Track virtual state of items to know where things are after swaps
        List<ItemStack> currentItems = new ArrayList<>();
        for (int i = 0; i < slotIndices.size(); i++) {
            int slotIdx = slotIndices.get(i);
            // We access the original slot content.
            // Note: snapshot logic in sortInventory used copies, so we should trust the
            // current screen state or the snapshot.
            // Since we just did the snapshot, it matches.
            currentItems.add(screen.getMenu().slots.get(slotIdx).getItem().copy());
        }

        // We assume sortedItems is a permutation of currentItems (or compatible)
        // If sorting algo merges stacks, this logic might need adjustment, but for pure
        // reordering it works.
        // Assuming simple reorder for now.

        for (int i = 0; i < slotIndices.size(); i++) {
            ItemStack desired = sortedItems.get(i);
            ItemStack currentAtI = currentItems.get(i);

            // If matches, skips
            // We use matches components to be safe
            if (ItemStack.matches(currentAtI, desired)) {
                continue;
            }

            // Find where desired item is
            int foundIndex = -1;
            for (int j = i + 1; j < currentItems.size(); j++) {
                if (ItemStack.matches(currentItems.get(j), desired)) {
                    foundIndex = j;
                    break;
                }
            }

            if (foundIndex == -1) {
                // This implies the sorted list demands an item we don't have?
                // Or we merged items?
                // Fallback: Skip
                continue;
            }

            // Perform Swap
            int slotI = slotIndices.get(i);
            int slotF = slotIndices.get(foundIndex);

            // Click Sequence to swap Slot I and Slot F:
            // 1. Pick up F (cursor has Desired)
            click(client, containerId, slotF, 0, ClickType.PICKUP);
            // 2. Click I (Place Desired at I, pickup Wrong)
            click(client, containerId, slotI, 0, ClickType.PICKUP);
            // 3. Click F (Place Wrong at F)
            click(client, containerId, slotF, 0, ClickType.PICKUP);

            // Update virtual list
            java.util.Collections.swap(currentItems, i, foundIndex);
        }
    }

    private static void click(Minecraft client, int containerId, int slot, int button, ClickType type) {
        client.gameMode.handleInventoryMouseClick(containerId, slot, button, type, client.player);
    }

    private static ISorter getCurrentSorter() {
        // Return configured sorter
        PiggyInventoryConfig.SortingAlgorithm algo = ((PiggyInventoryConfig) PiggyInventoryConfig.getInstance())
                .getDefaultAlgorithm();

        switch (algo) {
            case CREATIVE:
                // This case might imply a creative-specific sorter or just a placeholder
                // For now, fall through to default or handle as needed.
                // If CREATIVE means "no sorting, just fill", then it's a different logic.
                // Assuming it's a sorting algorithm type.
                break;
            case ALPHABETICAL:
                // TODO: Implement AlphabeticalSorter
                return new is.pig.minecraft.inventory.sorting.SmartCategorySorter(); // Placeholder
            case COLOR:
                // TODO: Implement ColorSorter
                return new is.pig.minecraft.inventory.sorting.SmartCategorySorter(); // Placeholder
            case SMART:
            default:
                // existing logic
                return new is.pig.minecraft.inventory.sorting.SmartCategorySorter();
        }
        // Fallback in case a break is hit without a return
        return new is.pig.minecraft.inventory.sorting.SmartCategorySorter();
    }
}
