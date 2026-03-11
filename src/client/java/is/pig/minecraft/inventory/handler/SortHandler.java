package is.pig.minecraft.inventory.handler;

import is.pig.minecraft.inventory.sorting.Comparators;
import is.pig.minecraft.inventory.sorting.StackMerger;
import is.pig.minecraft.inventory.sorting.layout.ISortingLayout;
import is.pig.minecraft.inventory.sorting.layout.RowLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SortHandler {
    private static final SortHandler INSTANCE = new SortHandler();

    private SortHandler() {
    }

    public static SortHandler getInstance() {
        return INSTANCE;
    }

    public void handleSort(Minecraft client, Slot hoveredSlot) {
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
            return;
        }

        // Determine the target container. Default to player inventory if nothing is hovered.
        net.minecraft.world.Container targetContainer = client.player.getInventory();
        if (hoveredSlot != null && hoveredSlot.container != null) {
            targetContainer = hoveredSlot.container;
        }
        boolean isPlayerInv = (targetContainer == client.player.getInventory());

        // Extract items from appropriate slots
        List<Slot> slotsToSort = new ArrayList<>();
        List<ItemStack> items = new ArrayList<>();

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == targetContainer) {
                if (isPlayerInv && slot.getContainerSlot() >= 36) {
                    continue; // Skip armor and offhand
                }
                
                if (isPlayerInv && is.pig.minecraft.inventory.locking.SlotLockingManager.getInstance().isLocked(slot)) {
                    continue; // Skip locked player slots!
                }
                
                slotsToSort.add(slot);
                items.add(slot.getItem().copy());

                if (slot.x < minX) minX = slot.x;
                if (slot.x > maxX) maxX = slot.x;
                if (slot.y < minY) minY = slot.y;
                if (slot.y > maxY) maxY = slot.y;
            }
        }

        if (items.isEmpty()) return;

        // Calculate approximate Grid Dimensions
        // A standard slot is 18x18 pixels usually.
        int cols = Math.max(1, (maxX - minX) / 18 + 1);
        int rows = Math.max(1, (maxY - minY) / 18 + 1);
        
        // Safety bounds
        if (slotsToSort.size() < cols * rows && isPlayerInv) {
             // Hardcode player inventory as it has a gap between storage and hotbar
             cols = 9;
             rows = 4;
        }

        // 1. Merge
        StackMerger.merge(items, slotsToSort);

        // 2. Sort using the user-configured comparator hierarchy
        is.pig.minecraft.inventory.config.PiggyInventoryConfig cfg = is.pig.minecraft.inventory.config.PiggyInventoryConfig.getInstance();
        List<String> comparatorOrder = cfg.getSortComparatorOrder();
        items.sort(Comparators.buildHierarchy(comparatorOrder));

        // 3. Layout the grid with empty spaces separating groups
        List<java.util.Comparator<ItemStack>> layoutComparators = Comparators.buildComparatorList(comparatorOrder);
        ISortingLayout layout = cfg.getSortLayout() == is.pig.minecraft.inventory.config.PiggyInventoryConfig.SortLayout.COLUMN
                ? new is.pig.minecraft.inventory.sorting.layout.ColumnLayout(layoutComparators)
                : new RowLayout(layoutComparators);
        List<ItemStack> finalPositions = layout.layout(items, slotsToSort);

        // Verification: Ensure no items were dropped by layout padding running out of bounds
        List<ItemStack> trackingList = new ArrayList<>(items);
        for (ItemStack positioned : finalPositions) {
            if (!positioned.isEmpty()) {
                for (int i = 0; i < trackingList.size(); i++) {
                    if (trackingList.get(i) == positioned) {
                        trackingList.remove(i);
                        break;
                    }
                }
            }
        }
        
        // Recover any dropped items into available empty slots
        for (ItemStack missing : trackingList) {
            boolean placed = false;
            for (int i = 0; i < finalPositions.size(); i++) {
                if (finalPositions.get(i).isEmpty()) {
                    finalPositions.set(i, missing);
                    placed = true;
                    break;
                }
            }
            // Fallback (should be mathematically impossible as total items <= slots)
            if (!placed && !finalPositions.isEmpty()) {
                for (int i = finalPositions.size() - 1; i >= 0; i--) {
                    if (finalPositions.get(i).isEmpty() || i == 0) {
                        finalPositions.set(i, missing);
                        break;
                    }
                }
            }
        }

        // 4. Write back to slots using packets/actions via the Executor
        SortExecutor.getInstance().startSort(slotsToSort, finalPositions);
    }
}

