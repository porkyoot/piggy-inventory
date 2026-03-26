package is.pig.minecraft.inventory.handler;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SortExecutor {

    private static SortExecutor INSTANCE;

    private boolean isExecuting = false;
    private is.pig.minecraft.lib.util.telemetry.MetaActionSession currentSession;

    private List<Slot> targetSlots;
    private List<ItemStack> targetItems;

    private static final net.minecraft.resources.ResourceLocation SORT_ICON = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("piggy", "textures/gui/icons/quick_sort.png");

    // Helper to abstract slot IDs
    private record Action(int slotId, int button) {}
    private List<Action> actionQueue = new ArrayList<>();



    private SortExecutor() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    public static SortExecutor getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SortExecutor();
        }
        return INSTANCE;
    }

    public void startSort(List<Slot> slots, List<ItemStack> target) {
        if (is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager.getInstance().getCurrentSession().isEmpty()) {
            this.currentSession = is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager.getInstance().startSession("Sort");
        } else {
            this.currentSession = is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager.getInstance().getCurrentSession().get();
        }
        
        if (slots.size() != target.size()) {
            String err = String.format("SortExecutor invariant failed: Slot count (%d) != Target item count (%d)", slots.size(), target.size());
            if (currentSession != null) currentSession.fail(err);
            return;
        }

        this.targetSlots = slots;
        this.targetItems = target;
        this.isExecuting = true;

        if (currentSession != null) {
            currentSession.info("SortExecutor processing " + slots.size() + " slots");
            currentSession.info("Initial Context: " + is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatPlayer(Minecraft.getInstance().player));
            currentSession.info("Player State: " + is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatFullPlayerInventory(Minecraft.getInstance().player));
            currentSession.info("Target Layout: " + is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatItemStacks(target));
        }

        buildQueue();
    }

    public void stopSort(boolean notifySuccess) {
        this.isExecuting = false;
        this.actionQueue.clear();
        
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            if (currentSession != null) currentSession.discard();
            this.currentSession = null;
            return;
        }
        
        if (notifySuccess) {
            if (verifySortState(client, true)) {
                if (currentSession != null) currentSession.succeed();
            } else {
                if (currentSession != null) {
                    currentSession.fail("Verification mismatch after execution");
                }
            }
        } else if (currentSession != null) {
            currentSession.discard();
        }
        
        this.currentSession = null;
        is.pig.minecraft.inventory.handler.SortHandler.getInstance().cleanup();
    }
    
    public void abortSort(String reason) {
        if (currentSession != null) {
            currentSession.fail("Sort Aborted: " + reason);
        }
        
        is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().clear("piggy-inventory-sort");
        stopSort(false);
    }

    private boolean verifySortState(Minecraft client, boolean logErrors) {
        if (client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> || is.pig.minecraft.inventory.handler.SortHandler.getInstance().getHiddenScreen() != null) {
            boolean success = true;
            for (int i = 0; i < targetSlots.size(); i++) {
                ItemStack current = targetSlots.get(i).getItem();
                ItemStack target = targetItems.get(i);
                if (!isSameAndEqual(current, target)) {
                    if (currentSession != null && logErrors) {
                        currentSession.error(is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatValidationFailure(i, target, current));
                    }
                    success = false;
                }
            }
            return success;
        }
        return false;
    }

    private void buildQueue() {
        actionQueue.clear();
        
        ItemStack[] current = new ItemStack[targetSlots.size()];
        for (int i = 0; i < targetSlots.size(); i++) {
            current[i] = targetSlots.get(i).getItem().copy();
        }

        // Phase 1: Aggressively consolidate partial stacks to free up buffer slots
        // Maintenance Protection: Do not consolidate items that are already in their correct target slots!
        List<Boolean> protectedSlots = new ArrayList<>();
        for (int i = 0; i < current.length; i++) {
            boolean isPerfect = isSameAndEqual(current[i], targetItems.get(i)) && !targetItems.get(i).isEmpty();
            protectedSlots.add(isPerfect);
        }

        List<ItemStack> currentList = new ArrayList<>(java.util.Arrays.asList(current));
        List<is.pig.minecraft.inventory.sorting.StackMerger.Click> mergeClicks = 
            is.pig.minecraft.inventory.sorting.StackMerger.merge(currentList, targetSlots, protectedSlots, true);
        
        for (var mc : mergeClicks) {
            actionQueue.add(new Action(mc.slotIndex(), mc.button()));
        }
        // Update the current array with consolidated state
        for (int i = 0; i < current.length; i++) {
            current[i] = currentList.get(i);
        }
        
        // New Evacuate and Ferry algorithm main execution loop
        isExecuting = true; // Optimization: mark as executing to allow abortSort state checks
        for (int i = 0; i < current.length; i++) {
            if (!isExecuting) break; // Check if abortSort was called by a helper
            ItemStack targetItem = targetItems.get(i);
            int targetQuantity = targetItem.getCount();
            
            ItemStack currentItem = current[i];

            if (isSameAndEqual(currentItem, targetItem)) {
                continue;
            }

            // It does not match perfectly.
            // completely clear whatever is currently sitting there
            evacuateSlot(i, current, actionQueue);

            // pull the correct items into this slot
            if (!targetItem.isEmpty()) {
                ferryItems(i, targetItem, targetQuantity, current, actionQueue);
            }
        }
        
        // At the end of build queue, push everything into a BulkAction
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        int containerId = client.player.containerMenu.containerId;
        int cps = is.pig.minecraft.inventory.config.PiggyInventoryConfig.getInstance().getTickDelay();
        
        List<is.pig.minecraft.lib.action.IAction> clicks = new ArrayList<>();
        for (Action action : actionQueue) {
            var slotAction = new is.pig.minecraft.lib.action.inventory.ClickWindowSlotAction(
                    containerId,
                    action.slotId(),
                    action.button(),
                    ClickType.PICKUP,
                    "piggy-inventory-sort",
                    is.pig.minecraft.lib.action.ActionPriority.NORMAL
            );
            if (cps <= 0) slotAction.setIgnoreGlobalCps(true);
            clicks.add(slotAction);
            is.pig.minecraft.lib.ui.IconQueueOverlay.queueIcon(SORT_ICON, 1000, false);
        }
        
        var bulkAction = new is.pig.minecraft.lib.action.BulkAction(
                "piggy-inventory-sort", 
                "Sort Inventory", 
                clicks, 
                () -> this.verifySortState(Minecraft.getInstance(), false)
        );
        if (cps <= 0) bulkAction.setIgnoreGlobalCps(true);
        
        is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().enqueue(bulkAction);
    }

    private boolean isSameAndEqual(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return ItemStack.isSameItemSameComponents(a, b) && a.getCount() == b.getCount();
    }
    


    private int getSlotStackLimit(int idx, ItemStack stack) {
        if (stack.isEmpty()) return 64;
        net.minecraft.world.inventory.Slot slot = targetSlots.get(idx);
        int limit = slot.getMaxStackSize(stack);
        if (limit <= 0) limit = Math.max(64, slot.getMaxStackSize());
        return limit;
    }


    private void evacuateSlot(int targetSlotIndex, ItemStack[] currentLayout, List<Action> actions) {
        if (currentLayout[targetSlotIndex].isEmpty()) {
            return;
        }

        ItemStack evacutingStack = currentLayout[targetSlotIndex].copy();

        while (!currentLayout[targetSlotIndex].isEmpty()) {
            // Slot Protection: ONLY look for buffers ahead of the current resolution index (j > targetSlotIndex)
            int bufferSlot = -1;
            
            // 1. Try to merge into a compatible existing buffer ahead
            for (int i = targetSlotIndex + 1; i < currentLayout.length; i++) {
                // Enhanced Slot Protection: Never borrow a slot that is already correctly sorted!
                if (isSameAndEqual(currentLayout[i], targetItems.get(i)) && !targetItems.get(i).isEmpty()) continue;

                ItemStack stack = currentLayout[i];
                if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, evacutingStack)) {
                    if (stack.getCount() < getSlotStackLimit(i, stack)) {
                        bufferSlot = i;
                        break;
                    }
                }
            }

            // 2. Fall back to an empty slot ahead
            if (bufferSlot == -1) {
                for (int i = targetSlotIndex + 1; i < currentLayout.length; i++) {
                    if (currentLayout[i].isEmpty()) {
                        bufferSlot = i;
                        break;
                    }
                }
            }
            
            if (bufferSlot == -1) {
                abortSort("Evacuate failed: No buffer space available ahead of slot " + targetSlotIndex);
                return;
            }
            
            ItemStack stackToMove = currentLayout[targetSlotIndex];
            int takeAmt = Math.min(stackToMove.getCount(), getVanillaStackLimit(stackToMove));
            if (takeAmt <= 0) break; // Defensive
            
            // Generate click action to pick up items (button 0 = left click)
            actions.add(new Action(targetSlots.get(targetSlotIndex).index, 0));
            
            // Generate click action to place those items into the buffer slot
            actions.add(new Action(targetSlots.get(bufferSlot).index, 0));
            
            // Update the currentLayout heavily
            ItemStack moved = stackToMove.copyWithCount(takeAmt);
            stackToMove.shrink(takeAmt);
            
            if (stackToMove.isEmpty()) {
                currentLayout[targetSlotIndex] = ItemStack.EMPTY;
            }
            
            if (currentLayout[bufferSlot].isEmpty()) {
                currentLayout[bufferSlot] = moved;
            } else {
                currentLayout[bufferSlot].grow(takeAmt);
            }
            
            if (currentSession != null) {
                currentSession.logAction("Evacuate Slot", 
                    is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatInventoryContext(
                        moved, -1, targetSlots.get(bufferSlot).index, ItemStack.EMPTY, ItemStack.EMPTY),
                    "Evacuated chunk to buffer (merged: " + !currentLayout[bufferSlot].isEmpty() + ")");
            }
        }
    }

    private void ferryItems(int targetSlotIndex, ItemStack targetItem, int requiredAmount, ItemStack[] currentLayout, List<Action> actions) {
        int currentQuantityInTarget = currentLayout[targetSlotIndex].isEmpty() ? 0 : currentLayout[targetSlotIndex].getCount();

        int safetyGlobal = 0;
        while (currentQuantityInTarget < requiredAmount) {
            if (safetyGlobal++ > 50000) {
                abortSort("Ferry items stuck in infinite loop (missing items)");
                break;
            }

            // Slot Protection: ONLY fetch from slots ahead (j > targetSlotIndex)
            int sourceSlot = -1;
            for (int i = targetSlotIndex + 1; i < currentLayout.length; i++) {
                // Enhanced Slot Protection: Do not steal items from a slot that's already correct!
                if (isSameAndEqual(currentLayout[i], targetItems.get(i)) && !targetItems.get(i).isEmpty()) continue;

                ItemStack currentItem = currentLayout[i];
                if (!currentItem.isEmpty() && ItemStack.isSameItemSameComponents(currentItem, targetItem)) {
                    sourceSlot = i;
                    break;
                }
            }

            if (sourceSlot == -1) {
                abortSort("Ferry failed: Could not find required items " + targetItem.getItem() + " ahead of slot " + targetSlotIndex);
                break;
            }

            ItemStack sourceStack = currentLayout[sourceSlot];
            int vanillaLimit = getVanillaStackLimit(sourceStack);
            
            int pickUp = Math.min(sourceStack.getCount(), vanillaLimit);
            if (pickUp <= 0) break;
            
            // Left click to pick up items from the found source slot
            actions.add(new Action(targetSlots.get(sourceSlot).index, 0));
            // Left click to place them into the targetSlotIndex
            actions.add(new Action(targetSlots.get(targetSlotIndex).index, 0));
            
            int dropped = Math.min(pickUp, requiredAmount - currentQuantityInTarget);
            int returned = pickUp - dropped;
            
            if (returned > 0) {
                // If we grabbed more than required, drop the remainder back to the source slot
                actions.add(new Action(targetSlots.get(sourceSlot).index, 0));
            }
            
            // Update currentLayout accurately for the source slot
            sourceStack.shrink(dropped);
            if (sourceStack.isEmpty()) {
                currentLayout[sourceSlot] = ItemStack.EMPTY;
            }
            
            // Update currentLayout accurately for the target slot
            if (currentLayout[targetSlotIndex].isEmpty()) {
                currentLayout[targetSlotIndex] = targetItem.copyWithCount(currentQuantityInTarget + dropped);
            } else {
                currentLayout[targetSlotIndex].grow(dropped);
            }
            
            currentQuantityInTarget += dropped;
            
            if (currentSession != null) {
                currentSession.logAction("Ferry Slot", 
                    is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatInventoryContext(
                        targetItem.copyWithCount(dropped), sourceSlot, targetSlots.get(targetSlotIndex).index, ItemStack.EMPTY, ItemStack.EMPTY),
                    "Ferried chunk from " + sourceSlot + " to " + targetSlotIndex);
            }
        }
    }


    private int getVanillaStackLimit(ItemStack stack) {
        if (stack.isEmpty()) return 64;
        return stack.getMaxStackSize();
    }


    private void onTick(Minecraft client) {
        if (!isExecuting) return;
        
        if (client.player == null || client.gameMode == null) {
            abortSort("Screen closed or player disconnected");
            return;
        }

        boolean hasVisibleScreen = client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
        boolean hasHiddenScreen = is.pig.minecraft.inventory.handler.SortHandler.getInstance().getHiddenScreen() != null;
        
        if (!hasVisibleScreen && !hasHiddenScreen) {
            abortSort("Unexpected screen type");
            return;
        }

        // Just wait for the bulk action to clear
        if (!is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().hasActions("piggy-inventory-sort")) {
            stopSort(true);
        }
    }
}
