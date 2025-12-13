package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import is.pig.minecraft.inventory.locking.SlotLockingManager;
import is.pig.minecraft.lib.sorting.ISorter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

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

        // 2. Snapshot & Extract Items
        List<ItemStack> extractableItems = new ArrayList<>();
        List<Integer> validSlotIndices = new ArrayList<>();

        // 1. Identify Range & Extract Items
        if (sortPlayerInventory) {
            // Strict Player Inventory (Storage + Hotbar)
            // Iterate all slots and find those belonging to PlayerInventory < 36
            for (Slot slot : allSlots) {
                if (slot.container == client.player.getInventory() && slot.getContainerSlot() < 36) {
                    if (SlotLockingManager.getInstance().isLocked(slot))
                        continue;

                    ItemStack stack = slot.getItem();
                    if (!stack.isEmpty()) {
                        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                                .toString();
                        if (((PiggyInventoryConfig) PiggyInventoryConfig.getInstance()).getBlacklistedItems()
                                .contains(itemId)) {
                            continue;
                        }
                        extractableItems.add(stack.copy());
                    }
                    validSlotIndices.add(slot.index);
                }
            }
        } else {
            // External Container
            for (Slot slot : allSlots) {
                // Must NOT be player inventory
                if (slot.container != client.player.getInventory()) {
                    if (SlotLockingManager.getInstance().isLocked(slot))
                        continue;

                    ItemStack stack = slot.getItem();
                    if (!stack.isEmpty()) {
                        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                                .toString();
                        if (((PiggyInventoryConfig) PiggyInventoryConfig.getInstance()).getBlacklistedItems()
                                .contains(itemId)) {
                            continue;
                        }
                        extractableItems.add(stack.copy());
                    }
                    validSlotIndices.add(slot.index);
                }
            }
        }

        if (validSlotIndices.isEmpty() || extractableItems.isEmpty())
            return;

        // 3. Sort Logic
        // 3. Sort Logic
        ISorter sorter = getCurrentSorter();
        sorter.sort(extractableItems);

        // 4. Layout
        PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();
        java.util.Map<Integer, ItemStack> layoutMap = LayoutEngine.calculateLayout(extractableItems, validSlotIndices,
                9,
                config.getDefaultLayout(), config.getDefaultAlgorithm());

        // 5. Diff & Execution
        boolean safeForCreativePacket = false;
        if (client.player.isCreative()) {
            if (screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
                safeForCreativePacket = true;
            } else if (client.player.containerMenu == client.player.inventoryMenu) {
                safeForCreativePacket = true;
            }
        }

        if (safeForCreativePacket) {
            executeCreativeSort(client, validSlotIndices, layoutMap);
        } else {
            try {
                executeSurvivalSort(client, screen, validSlotIndices, layoutMap);
            } catch (Exception e) {
                is.pig.minecraft.inventory.PiggyInventoryClient.LOGGER.error("Sort Failed Detected", e);
            }
        }
    }

    private static void executeCreativeSort(Minecraft client, List<Integer> slots,
            java.util.Map<Integer, ItemStack> layoutMap) {
        for (int slotIndex : slots) {
            ItemStack desired = layoutMap.getOrDefault(slotIndex, ItemStack.EMPTY);
            client.player.connection.send(
                    new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(slotIndex, desired));
        }
    }

    private static void executeSurvivalSort(Minecraft client, AbstractContainerScreen<?> screen,
            List<Integer> slotIndices, java.util.Map<Integer, ItemStack> layoutMap) {
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

        // 4a. Execute Sort (Naive Swap)
        for (int i = 0; i < slotIndices.size(); i++) {
            int currentSlotIndex = slotIndices.get(i);
            ItemStack desired = layoutMap.getOrDefault(currentSlotIndex, ItemStack.EMPTY);
            ItemStack currentAtI = currentItems.get(i); // Virtual state tracker

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
                return new is.pig.minecraft.inventory.sorting.CreativeSorter();
            case ALPHABETICAL:
                return new is.pig.minecraft.inventory.sorting.AlphabeticalSorter();
            case COLOR:
                return new is.pig.minecraft.inventory.sorting.ColorSorter();
            case RARITY:
                return new is.pig.minecraft.inventory.sorting.RaritySorter();
            case MATERIAL:
                return new is.pig.minecraft.inventory.sorting.MaterialSorter();
            case TYPE:
                return new is.pig.minecraft.inventory.sorting.TypeSorter();
            case TAG:
                return new is.pig.minecraft.inventory.sorting.TagSorter();
            case JSON:
                return new is.pig.minecraft.inventory.sorting.JsonListSorter();
            case SMART:
            default:
                // existing logic
                return new is.pig.minecraft.inventory.sorting.SmartCategorySorter();
        }
    }
}
