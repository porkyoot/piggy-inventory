package is.pig.minecraft.inventory.handler;

import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SortExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger("piggy-inventory-sort");
    private static SortExecutor INSTANCE;

    private boolean isExecuting = false;
    private int tickCounter = 0;

    private List<Slot> targetSlots;
    private List<ItemStack> targetItems;
    private int currentActionIndex = 0;

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
        if (slots.size() != target.size()) {
            LOGGER.error("SortExecutor invariant failed: Slot count ({}) != Target item count ({})", slots.size(), target.size());
            return;
        }

        this.targetSlots = slots;
        this.targetItems = target;
        this.isExecuting = true;
        this.tickCounter = 0;
        this.currentActionIndex = 0;

        buildQueue();
    }

    public void stopSort() {
        this.isExecuting = false;
        this.actionQueue.clear();
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
                LOGGER.error("SortExecutor: Algorithm stuck globally. Sorting aborted.");
                return;
            }
            
            boolean logDiag = safetyGlobal > 49980;
            if (logDiag) {
                LOGGER.error("--- Diagnostic Iteration {} ---", safetyGlobal);
                LOGGER.error("Cursor holds: {}", cursor.stack);
            }

            // 1. If Cursor holds an item, its top priority is finding where it belongs and dropping it!
            if (!cursor.stack.isEmpty()) {
                int tSlot = findTargetSlotForCursor(current, cursor.stack);
                if (logDiag) LOGGER.error("Target slot for cursor: {}", tSlot);
                
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
                    
                    if (logDiag) LOGGER.error("tSlot={}, curAmt={}, tarAmt={}, cursor={}", tSlot, curAmt, tarAmt, cursor.stack.getCount());

                    if (curAmt != -1 && cursor.stack.getCount() + curAmt > tarAmt) {
                        if (logDiag) LOGGER.error("Attempting RIGHT CLICK on tSlot {}", tSlot);
                        if (click(tSlot, 1, current, cursor, logDiag)) changed = true;
                    } else {
                        if (logDiag) LOGGER.error("Attempting LEFT CLICK on tSlot {}", tSlot);
                        if (click(tSlot, 0, current, cursor, logDiag)) {
                            pickupSlot = tSlot; // Track where we might have swapped from
                            changed = true;
                        }
                    }
                    if (changed) continue;
                }
                
                // Cursor item doesn't belong anywhere (excess). Safely stash it.
                int safe = findSafeDropAny(current, cursor, pickupSlot);
                if (logDiag) LOGGER.error("Safe drop slot: {}", safe);
                if (safe != -1) {
                    if (click(safe, 0, current, cursor, logDiag)) {
                        pickupSlot = -1; // Ready to fetch a new target
                        changed = true;
                        continue;
                    }
                }
                
                // Inventory 100% full of wrong items. Force swap to keep unjumbling.
                int wrong = findFirstWrongSlot(current, pickupSlot);
                if (logDiag) LOGGER.error("First wrong slot: {}", wrong);
                if (wrong != -1) {
                    if (click(wrong, 0, current, cursor, logDiag)) {
                        pickupSlot = wrong;
                        changed = true;
                        continue;
                    }
                }
                
                if (logDiag) LOGGER.error("Attempting fallback click on slot 0");
                if (click(0, 0, current, cursor, logDiag)) {
                    pickupSlot = 0;
                    changed = true;
                    continue;
                }

                LOGGER.debug("SortExecutor: Cursor is trapped holding {}. No valid drops or swaps. Aborting logic.", cursor.stack);
                break; // Break cleanly if absolutely no operations are valid
            }
            
            // 2. Cursor is empty. Check if fully sorted. If so, break explicitly to guarantee no infinite ping-pong.
            if (isFullySorted(current, targetItems)) {
                break;
            }

            // 3. Cursor is empty. Look for the first mismatch to kick off a chain!
            
            // 3a. Prioritize picking up from "Mega Stacks" that are blocking spots because they can't be swapped:
            boolean megaStackPicked = false;
            for (int i = 0; i < targetSlots.size(); i++) {
                if (isSameAndEqual(current[i], targetItems.get(i))) continue;
                int curAmt = current[i].getCount();
                int tarAmt = targetItems.get(i).isEmpty() ? 0 : targetItems.get(i).getCount();
                
                int safeTransferMax = getVanillaStackLimit(current[i]);
                if (!current[i].isEmpty() && (!ItemStack.isSameItemSameComponents(current[i], targetItems.get(i)) || curAmt > tarAmt)) {
                    if (curAmt > safeTransferMax && curAmt > tarAmt) {
                        if (logDiag) LOGGER.error("EmptyCursor: Prioritizing Mega Stack Type A from slot {}, curAmt {}, tarAmt {}", i, curAmt, tarAmt);
                        if (click(i, 0, current, cursor, logDiag)) {
                            pickupSlot = i;
                            changed = true;
                            megaStackPicked = true;
                            break;
                        }
                    }
                }
            }
            if (megaStackPicked) continue;

            // 3b. Normal Type A and Type B
            boolean typeAPicked = false;
            // Pass 1: Ignore pickupSlot to avoid immediate ping-pong
            for (int i = 0; i < targetSlots.size(); i++) {
                if (i == pickupSlot) continue;
                if (isSameAndEqual(current[i], targetItems.get(i))) continue;

                int curAmt = current[i].getCount();
                int tarAmt = targetItems.get(i).isEmpty() ? 0 : targetItems.get(i).getCount();

                if (!current[i].isEmpty()) {
                    if (!ItemStack.isSameItemSameComponents(current[i], targetItems.get(i)) || curAmt > tarAmt) {
                        if (logDiag) LOGGER.error("EmptyCursor: Picking up Mismatch Type A from slot {}, curAmt {}, tarAmt {}", i, curAmt, tarAmt);
                        if (click(i, 0, current, cursor, logDiag)) {
                            pickupSlot = i;
                            changed = true;
                            typeAPicked = true;
                            break; 
                        }
                    }
                }
            }
            if (typeAPicked) continue;

            // Pass 2: Fallback to pickupSlot if it's the only Type A left
            if (pickupSlot != -1 && pickupSlot < targetSlots.size()) {
                int i = pickupSlot;
                if (!isSameAndEqual(current[i], targetItems.get(i))) {
                    int curAmt = current[i].getCount();
                    int tarAmt = targetItems.get(i).isEmpty() ? 0 : targetItems.get(i).getCount();
                    if (!current[i].isEmpty() && (!ItemStack.isSameItemSameComponents(current[i], targetItems.get(i)) || curAmt > tarAmt)) {
                        if (logDiag) LOGGER.error("EmptyCursor: Fallback Picking up Mismatch Type A from pickupSlot {}, curAmt {}, tarAmt {}", i, curAmt, tarAmt);
                        if (click(i, 0, current, cursor, logDiag)) {
                            changed = true;
                            // don't change pickupSlot
                            typeAPicked = true;
                        }
                    }
                }
            }
            if (typeAPicked) continue;

            // Pass 3: Mismatch Type B
            for (int i = 0; i < targetSlots.size(); i++) {
                if (isSameAndEqual(current[i], targetItems.get(i))) continue;
                
                int curAmt = current[i].getCount();
                int tarAmt = targetItems.get(i).isEmpty() ? 0 : targetItems.get(i).getCount();

                // Mismatch Type B: Slot is empty or missing items -> Fetch them!
                if (current[i].isEmpty() || (ItemStack.isSameItemSameComponents(current[i], targetItems.get(i)) && curAmt < tarAmt)) {
                    int src = findSource(current, targetItems.get(i));
                    if (logDiag) LOGGER.error("EmptyCursor: Mismatch Type B for slot {}. Looking for source... found at {}", i, src);
                    if (src != -1) {
                        if (click(src, 0, current, cursor, logDiag)) {
                            pickupSlot = src;
                            changed = true;
                            break; // break the for loop to process the new cursor!
                        }
                    }
                }
            }
            if (logDiag && !changed) {
                LOGGER.error("EmptyCursor: No changes made in this iteration!");
            }
        } // end while loop
        
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
                    int slotLimit = targetSlots.get(i).getMaxStackSize();
                    if (slotLimit <= 0) slotLimit = 64;
                    int itemCursorCapacity = getVanillaStackLimit(current[i]);
                    // Only check if slot contents fit in the cursor and cursor contents fit in the slot
                    if (current[i].getCount() > itemCursorCapacity || cursorStack.getCount() > slotLimit) {
                        continue; // Cannot swap! Skip.
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
    
    private int findFirstWrongSlot(ItemStack[] current, int ignoreSlot) {
        for (int i = 0; i < current.length; i++) {
            if (i == ignoreSlot) continue; // FIX: Don't swap back to where we just picked up from
            if (!isSameAndEqual(current[i], targetItems.get(i))) {
                return i;
            }
        }
        return -1;
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

    private boolean click(int idx, int button, ItemStack[] current, CursorState cs, boolean logDiag) {
        ItemStack slot = current[idx];
        ItemStack cursor = cs.stack;
        
        if (logDiag) LOGGER.error(" -> click(idx={}, button={}, slot={}, cursor={})", idx, button, slot, cursor);
        
        ItemStack checkStack = cursor.isEmpty() ? slot : cursor;
        int slotLimit = checkStack.isEmpty() ? targetSlots.get(idx).getMaxStackSize() : targetSlots.get(idx).getMaxStackSize(checkStack);
        if (slotLimit <= 0) slotLimit = 64; // Fallback
        
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
                // Swapping (Only valid if BOTH stacks can fit in their respective destinations)
                // If the slot has 4000 items, we CANNOT swap it into the cursor.
                if (slot.getCount() > itemCursorLimit || cursor.getCount() > slotLimit) {
                    // Invalid Swap -> Abort or fallback to incremental behavior
                    LOGGER.debug("SortExecutor: Prevented invalid swap at idx {} due to custom stack limits. Slot {}, Cursor {}", idx, slot.getCount(), cursor.getCount());
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
                // Right click swap behaves identically to left click if items differ.
                if (slot.getCount() > itemCursorLimit || cursor.getCount() > slotLimit) {
                    LOGGER.debug("SortExecutor: Prevented invalid right-click swap at idx {} due to custom stack caps. Slot {}, Cursor {}", idx, slot.getCount(), cursor.getCount());
                    return false;
                } else {
                    current[idx] = cursor;
                    cs.stack = slot;
                }
            }
        }

        if (logDiag) LOGGER.error(" -> action added. Slot after: {}, Cursor after: {}", current[idx], cs.stack);
        actionQueue.add(new Action(targetSlots.get(idx).index, button));
        return true;
    }

    private void onTick(Minecraft client) {
        if (!isExecuting) return;

        int cps = PiggyInventoryConfig.getInstance().getTickDelay(); // This value is Clicks-Per-Second
        
        if (client.screen == null || client.player == null || client.gameMode == null) {
            stopSort();
            return;
        }
        
        if (cps <= 0) {
            // Unlimited speed: dump the entire queue immediately in 1 tick
            int containerId = client.player.containerMenu.containerId;
            while (currentActionIndex < actionQueue.size()) {
                Action action = actionQueue.get(currentActionIndex);
                client.gameMode.handleInventoryMouseClick(
                        containerId,
                        action.slotId(),
                        action.button(),
                        ClickType.PICKUP,
                        client.player
                );
                currentActionIndex++;
            }
            stopSort();
            return;
        }

        int delayTicks = Math.max(1, 20 / cps); // Convert CPS to ticks (20 ticks per second)
        
        if (tickCounter < delayTicks) {
            tickCounter++;
            return;
        }

        if (currentActionIndex >= actionQueue.size()) {
            stopSort();
            return;
        }

        Action action = actionQueue.get(currentActionIndex);
        
        // Execute the single click
        int containerId = client.player.containerMenu.containerId;
        client.gameMode.handleInventoryMouseClick(
                containerId,
                action.slotId(),
                action.button(),
                ClickType.PICKUP,
                client.player
        );

        currentActionIndex++;
        tickCounter = 0; // Reset delay for next action
    }
}
