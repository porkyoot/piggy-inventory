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

    private static class CursorState {
        ItemStack stack = ItemStack.EMPTY;
    }

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
        
        CursorState cursor = new CursorState();

        int safetyGlobal = 0;
        boolean changed = true;
        
        int pickupSlot = -1;
        while (changed) {
            changed = false;
            
            if (safetyGlobal++ > 50000) { // Failsafe augmented for massive custom stacks (64x transfers)
                if (currentSession != null) currentSession.fail("Algorithm stuck globally (50k iterations)");
                actionQueue.clear();
                return;
            }
            
            // 1. If Cursor holds an item, its top priority is finding where it belongs and dropping it!
            if (!cursor.stack.isEmpty()) {
                int tSlot = findTargetSlotForCursor(current, cursor.stack);
                
                if (tSlot != -1) {
                    int curAmt = 0;
                    if (!current[tSlot].isEmpty()) {
                        if (ItemStack.isSameItemSameComponents(current[tSlot], cursor.stack)) {
                            curAmt = current[tSlot].getCount();
                        } else {
                            // Wrong item present, must left-click to swap
                            curAmt = -1;
                        }
                    }

                    int tarAmt = targetItems.get(tSlot).getCount();

                    if (curAmt != -1 && cursor.stack.getCount() + curAmt > tarAmt) {
                        if (click(tSlot, 1, current, cursor)) changed = true;
                    } else {
                        if (click(tSlot, 0, current, cursor)) {
                            pickupSlot = tSlot; // Track where we might have swapped from
                            changed = true;
                            
                            final int finalTSlot = tSlot;
                            if (currentSession != null) {
                                currentSession.logAction("Cursor placement/swap", 
                                    is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatInventoryContext(
                                        cursor.stack, -1, targetSlots.get(finalTSlot).index, ItemStack.EMPTY, targetSlots.get(finalTSlot).getItem()),
                                    "Slot updated");
                            }
                        }
                    }
                    if (changed) continue;
                }
                
                // Cursor item doesn't belong anywhere (excess). Safely stash it.
                int safe = findSafeDropAny(current, cursor, pickupSlot);
                if (safe != -1) {
                    if (click(safe, 0, current, cursor)) {
                        pickupSlot = -1; // Ready to fetch a new target
                        changed = true;
                        continue;
                    }
                }
                
                // Inventory 100% full of wrong items. Force swap to keep unjumbling.
                int wrong = findFirstWrongSlot(current, pickupSlot, cursor.stack);
                if (wrong != -1) {
                    if (click(wrong, 0, current, cursor)) {
                        pickupSlot = wrong;
                        changed = true;
                        continue;
                    }
                }
                
                // If wrong is -1 and all safe drops are -1, the algorithm is mathematically deadlocked
                // by full slots containing blocking megastacks. It will break and cleanly execute whatever
                // progress was made before halting with a verification mismatch.
                break; // Break cleanly if absolutely no operations are valid
            }
            
            // 2. Cursor is empty. Check if fully sorted. If so, break explicitly to guarantee no infinite ping-pong.
            if (isFullySorted(current, targetItems)) {
                break;
            }

            // 3. Cursor is empty. Look for the first mismatch to kick off a chain!
            
            // Pass 0: "Perfect Match"
            boolean perfectPicked = false;
            for (int i = 0; i < targetSlots.size(); i++) {
                if (i == pickupSlot) continue; 
                if (isSameAndEqual(current[i], targetItems.get(i))) continue;
                if (current[i].isEmpty()) continue;
                
                int curAmt = current[i].getCount();
                int tarAmt = ItemStack.isSameItemSameComponents(current[i], targetItems.get(i)) ? targetItems.get(i).getCount() : 0;
                
                if (curAmt > tarAmt) {
                    int take = Math.min(curAmt, getVanillaStackLimit(current[i]));
                    ItemStack simCursor = current[i].copyWithCount(take);
                    int tSlot = findTargetSlotForCursor(current, simCursor);
                    if (tSlot != -1) {
                         if (click(i, 0, current, cursor)) {
                             pickupSlot = i;
                             changed = true;
                             perfectPicked = true;
                             break;
                         }
                    }
                }
            }
            if (perfectPicked) continue;

            // Pass 1 fallback for perfect match: allow using pickupSlot
            if (pickupSlot != -1 && pickupSlot < targetSlots.size() && !current[pickupSlot].isEmpty()) {
                int i = pickupSlot;
                if (!isSameAndEqual(current[i], targetItems.get(i))) {
                    int curAmt = current[i].getCount();
                    int tarAmt = ItemStack.isSameItemSameComponents(current[i], targetItems.get(i)) ? targetItems.get(i).getCount() : 0;
                    if (curAmt > tarAmt) {
                        int take = Math.min(curAmt, getVanillaStackLimit(current[i]));
                        ItemStack simCursor = current[i].copyWithCount(take);
                        int tSlot = findTargetSlotForCursor(current, simCursor);
                        if (tSlot != -1) {
                             if (click(i, 0, current, cursor)) {
                                 changed = true;
                                 perfectPicked = true;
                             }
                        }
                    }
                }
            }
            if (perfectPicked) continue;

            // Pass 2: Pick up from Mega Stacks that are blocking
            boolean megaStackPicked = false;
            for (int i = 0; i < targetSlots.size(); i++) {
                if (isSameAndEqual(current[i], targetItems.get(i))) continue;
                if (current[i].isEmpty()) continue;
                
                int curAmt = current[i].getCount();
                int tarAmt = ItemStack.isSameItemSameComponents(current[i], targetItems.get(i)) ? targetItems.get(i).getCount() : 0;
                
                int safeTransferMax = getVanillaStackLimit(current[i]);
                if (curAmt > tarAmt && curAmt > safeTransferMax) {
                    if (click(i, 0, current, cursor)) {
                        pickupSlot = i;
                        changed = true;
                        megaStackPicked = true;
                        break;
                    }
                }
            }
            if (megaStackPicked) continue;

            // Pass 3: Normal Type A (Wrong item or excess amount)
            boolean typeAPicked = false;
            for (int i = 0; i < targetSlots.size(); i++) {
                if (i == pickupSlot) continue;
                if (isSameAndEqual(current[i], targetItems.get(i))) continue;
                if (current[i].isEmpty()) continue;

                int curAmt = current[i].getCount();
                int tarAmt = ItemStack.isSameItemSameComponents(current[i], targetItems.get(i)) ? targetItems.get(i).getCount() : 0;

                if (curAmt > tarAmt) {
                    if (click(i, 0, current, cursor)) {
                        pickupSlot = i;
                        changed = true;
                        typeAPicked = true;
                        break; 
                    }
                }
            }
            if (typeAPicked) continue;

            // Pass 4: Fallback normal Type A on pickupSlot
            if (pickupSlot != -1 && pickupSlot < targetSlots.size() && !current[pickupSlot].isEmpty()) {
                int i = pickupSlot;
                if (!isSameAndEqual(current[i], targetItems.get(i))) {
                    int curAmt = current[i].getCount();
                    int tarAmt = ItemStack.isSameItemSameComponents(current[i], targetItems.get(i)) ? targetItems.get(i).getCount() : 0;
                    if (curAmt > tarAmt) {
                        if (click(i, 0, current, cursor)) {
                            changed = true;
                            typeAPicked = true;
                        }
                    }
                }
            }
            if (typeAPicked) continue;

            // Pass 5: Mismatch Type B (Slot is empty or needs more, so we fetch it!)
            for (int i = 0; i < targetSlots.size(); i++) {
                if (isSameAndEqual(current[i], targetItems.get(i))) continue;
                
                int curAmt = current[i].getCount();
                int tarAmt = targetItems.get(i).isEmpty() ? 0 : targetItems.get(i).getCount();

                // Slot needs items!
                if (current[i].isEmpty() || (ItemStack.isSameItemSameComponents(current[i], targetItems.get(i)) && curAmt < tarAmt)) {
                    int src = findSource(current, targetItems.get(i));
                    if (src != -1) {
                        if (click(src, 0, current, cursor)) {
                            pickupSlot = src;
                            changed = true;
                            break;
                        }
                    }
                }
            }
        } // end while loop
        
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
    
    private boolean isFullySorted(ItemStack[] current, List<ItemStack> target) {
        for (int i = 0; i < current.length; i++) {
            if (!isSameAndEqual(current[i], target.get(i))) return false;
        }
        return true;
    }

    private int findTargetSlotForCursor(ItemStack[] current, ItemStack cursorStack) {
        int bestSlot = -1;
        int largestNeed = -1;

        for (int i = 0; i < current.length; i++) {
            ItemStack target = targetItems.get(i);
            if (!target.isEmpty() && ItemStack.isSameItemSameComponents(target, cursorStack)) {
                if (current[i].isEmpty()) {
                    if (target.getCount() > largestNeed) {
                        largestNeed = target.getCount();
                        bestSlot = i;
                    }
                } else if (!ItemStack.isSameItemSameComponents(current[i], target)) {
                    int slotLimit = targetSlots.get(i).getMaxStackSize(cursorStack);
                    if (slotLimit <= 0) slotLimit = Math.max(64, targetSlots.get(i).getMaxStackSize());
                    int itemCursorCapacity = getVanillaStackLimit(current[i]);
                    // Only check if slot contents fit in the cursor!
                    // If cursor contents exceed the slot, it's fine; the excess just stays in the cursor.
                    if (current[i].getCount() > itemCursorCapacity) {
                        continue; // Cannot pick up the target item! Skip.
                    }

                    if (target.getCount() > largestNeed) {
                        largestNeed = target.getCount();
                        bestSlot = i;
                    }
                } else {
                    if (current[i].getCount() < target.getCount()) {
                        int need = target.getCount() - current[i].getCount();
                        if (need > largestNeed) {
                            largestNeed = need;
                            bestSlot = i;
                        }
                    }
                }
                if (largestNeed == cursorStack.getCount())
                    return i; // Perfect match
            }
        }
        return bestSlot;
    }
    
    private int findFirstWrongSlot(ItemStack[] current, int ignoreSlot, ItemStack cursorStack) {
        int bestSlot = -1;
        int bestScore = -1;

        for (int i = 0; i < current.length; i++) {
            if (i == ignoreSlot) continue; 
            
            // Empty slots must be used by findSafeDropAny. ForceSwap is only for occupied slots.
            if (current[i].isEmpty()) continue;
            
            // Do not disturb an item that is already exactly what the target needs!
            if (!targetItems.get(i).isEmpty() && ItemStack.isSameItemSameComponents(current[i], targetItems.get(i))) {
                continue; 
            }
            
            // Do not swap if it's the identical item we are holding (endless 64 dirt <-> 64 dirt trade)
            if (ItemStack.isSameItemSameComponents(current[i], cursorStack)) continue;
            
            // We can only swap if the slot's contents fit into our vanilla cursor!
            if (current[i].getCount() > getVanillaStackLimit(current[i])) continue;
            
            // This is a VALID stash point. Now score what we pick up!
            int score = 0;
            
            // Priority 1: Can the item we pick up be IMMEDIATELY placed in its target slot?
            int testTarget = findTargetSlotForCursor(current, current[i]);
            if (testTarget != -1 && testTarget != i) {
                score += 100; // Unblocked path to target!
                
                // Will putting it there COMPLETELY empty our cursor?
                int slotLimit = targetSlots.get(testTarget).getMaxStackSize(current[i]);
                if (slotLimit <= 0) slotLimit = Math.max(64, targetSlots.get(testTarget).getMaxStackSize());
                if (current[testTarget].isEmpty() || (current[testTarget].getCount() + current[i].getCount() <= slotLimit)) {
                    score += 50; // Guaranteed to empty cursor, allowing us to pick up Mega Stacks!
                }
            } else {
                // Priority 2: If we can't put it in its target, can we at least cleanly MERGE it into another slot?
                for (int j = 0; j < current.length; j++) {
                    if (j == i || j == ignoreSlot) continue;
                    if (!current[j].isEmpty() && ItemStack.isSameItemSameComponents(current[j], current[i])) {
                        int mergedAmt = current[j].getCount() + current[i].getCount();
                        int slotLimit = targetSlots.get(j).getMaxStackSize(current[i]);
                        if (slotLimit <= 0) slotLimit = Math.max(64, targetSlots.get(j).getMaxStackSize());
                        if (mergedAmt <= slotLimit) {
                            score += 10; // Can cleanly disappear!
                            break;
                        }
                    }
                }
            }
            
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        
        if (bestScore <= 0) {
            // No swap will lead to progress. Prevent infinite juggling.
            return -1; 
        }
        
        return bestSlot;
    }

    private int findSafeDropAny(ItemStack[] current, CursorState cs, int pickupSlot) {
        // Preferred: An empty slot whose target is ALSO empty (truly blank space)
        for (int i = 0; i < current.length; i++) {
            if (i == pickupSlot) continue;
            if (current[i].isEmpty() && targetItems.get(i).isEmpty()) {
                return i;
            }
        }
        // Fallback: ANY empty slot
        for (int i = 0; i < current.length; i++) {
            if (i == pickupSlot) continue;
            if (current[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private int findSource(ItemStack[] current, ItemStack targetType) {
        // Prefer a slot that has the item, but is NOT the target slot for it (it's misplaced)
        for (int i = 0; i < current.length; i++) {
            if (!current[i].isEmpty() && ItemStack.isSameItemSameComponents(current[i], targetType)) {
                ItemStack target = targetItems.get(i);
                if (target.isEmpty() || !ItemStack.isSameItemSameComponents(target, targetType) || current[i].getCount() > target.getCount()) {
                    return i;
                }
            }
        }
        // Fallback: any slot that has it
        for (int i = 0; i < current.length; i++) {
            if (!current[i].isEmpty() && ItemStack.isSameItemSameComponents(current[i], targetType)) {
                return i;
            }
        }
        return -1;
    }

    private int getVanillaStackLimit(ItemStack stack) {
        if (stack.isEmpty()) return 64;
        return stack.getMaxStackSize();
    }

    private boolean click(int idx, int button, ItemStack[] current, CursorState cs) {
        ItemStack slot = current[idx];
        ItemStack cursor = cs.stack;
        
        ItemStack checkStack = cursor.isEmpty() ? slot : cursor;
        int slotLimit = checkStack.isEmpty() ? targetSlots.get(idx).getMaxStackSize() : targetSlots.get(idx).getMaxStackSize(checkStack);
        if (slotLimit <= 0) slotLimit = Math.max(64, targetSlots.get(idx).getMaxStackSize()); // Fallback
        
        int itemCursorLimit = getVanillaStackLimit(slot);

        if (button == 0) {
            if (cursor.isEmpty()) {
                // Taking entire stack, but limited by what the cursor can hold!
                int take = Math.min(slot.getCount(), itemCursorLimit);
                if (take <= 0) return false; // Prevent empty loops
                cs.stack = slot.copyWithCount(take);
                slot.shrink(take);
                if (slot.isEmpty()) current[idx] = ItemStack.EMPTY;
            } else if (slot.isEmpty()) {
                // Putting down entire cursor stack (already limited)
                int put = Math.min(cursor.getCount(), slotLimit);
                if (put <= 0) return false;
                current[idx] = cursor.copyWithCount(put);
                cursor.shrink(put);
                if (cursor.isEmpty()) cs.stack = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(cursor, slot)) {
                // Merging
                int trans = Math.min(cursor.getCount(), slotLimit - slot.getCount());
                if (trans <= 0) return false; // Full
                slot.grow(trans);
                cursor.shrink(trans);
                if (cursor.isEmpty()) cs.stack = ItemStack.EMPTY;
            } else {
                if (slot.getCount() > itemCursorLimit || cursor.getCount() > slotLimit) {
                    return false;
                } else {
                    current[idx] = cursor;
                    cs.stack = slot;
                }
            }
        } else if (button == 1) {
            if (cursor.isEmpty()) {
                if (!slot.isEmpty()) {
                    int take = Math.min((slot.getCount() + 1) / 2, itemCursorLimit);
                    if (take <= 0) return false;
                    cs.stack = slot.copyWithCount(take);
                    slot.shrink(take);
                    if (slot.isEmpty()) current[idx] = ItemStack.EMPTY;
                }
            } else if (slot.isEmpty() || ItemStack.isSameItemSameComponents(cursor, slot)) {
                if (slotLimit <= slot.getCount()) return false;
                
                if (slot.isEmpty()) {
                    current[idx] = cursor.copyWithCount(1);
                } else if (slot.getCount() < slotLimit) {
                    slot.grow(1);
                }
                cursor.shrink(1);
                if (cursor.isEmpty()) cs.stack = ItemStack.EMPTY;
            } else {
                if (slot.getCount() > itemCursorLimit || cursor.getCount() > slotLimit) {
                    return false;
                } else {
                    current[idx] = cursor;
                    cs.stack = slot;
                }
            }
        }

        final int finalIdx = idx;
        final int finalButton = button;
        final ItemStack finalCursor = cs.stack.copy();
        final ItemStack finalSlotItem = slot.copy();
        
        if (currentSession != null) {
            currentSession.logAction("Slot Click", 
                is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatInventoryContext(
                    finalCursor, -1, targetSlots.get(finalIdx).index, ItemStack.EMPTY, finalSlotItem),
                "Button: " + finalButton);
        }

        actionQueue.add(new Action(targetSlots.get(idx).index, button));
        return true;
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
