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
        LOGGER.info("Sorting execution stopped.");
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
            
            if (safetyGlobal++ > 2000) { // Failsafe
                LOGGER.error("SortExecutor: Algorithm stuck globally. Sorting aborted.");
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
                        LOGGER.info("Cursor routing {} to {}. Diff too large, Right Clicking.", cursor.stack, tSlot);
                        click(tSlot, 1, current, cursor);
                    } else {
                        LOGGER.info("Cursor routing {} to {}. Left Clicking.", cursor.stack, tSlot);
                        click(tSlot, 0, current, cursor);
                        pickupSlot = tSlot; // Track where we might have swapped from
                    }
                    changed = true;
                    continue;
                }
                
                // Cursor item doesn't belong anywhere (excess). Safely stash it.
                int safe = findSafeDropAny(current, cursor, pickupSlot);
                if (safe != -1) {
                    LOGGER.info("Cursor has excess {}. Stashing safely at {} (ignoring pickup slot {}).", cursor.stack, safe, pickupSlot);
                    click(safe, 0, current, cursor);
                    pickupSlot = -1; // Ready to fetch a new target
                    changed = true;
                    continue;
                }
                
                // Inventory 100% full of wrong items. Force swap to keep unjumbling.
                int wrong = findFirstWrongSlot(current, pickupSlot);
                if (wrong != -1) {
                    LOGGER.info("Inventory full, forced swap at wrong slot {}.", wrong);
                    click(wrong, 0, current, cursor);
                    pickupSlot = wrong;
                    changed = true;
                    continue;
                }
                
                LOGGER.info("Fallback click 0.");
                click(0, 0, current, cursor);
                pickupSlot = 0;
                changed = true;
                continue;
            }

            // 2. Cursor is empty. Look for the first mismatch to kick off a chain!
            for (int i = 0; i < targetSlots.size(); i++) {
                if (isSameAndEqual(current[i], targetItems.get(i))) continue;

                int curAmt = current[i].getCount();
                int tarAmt = targetItems.get(i).isEmpty() ? 0 : targetItems.get(i).getCount();

                // Mismatch Type A: Slot shouldn't have this item, or has too much of it -> Pick it up!
                if (!current[i].isEmpty()) {
                    if (!ItemStack.isSameItemSameComponents(current[i], targetItems.get(i)) || curAmt > tarAmt) {
                        LOGGER.info("Slot {} holds wrong item or excess ({}). Picking up.", i, current[i]);
                        click(i, 0, current, cursor);
                        pickupSlot = i;
                        changed = true;
                        break; // break the for loop to process the new cursor!
                    }
                }
                
                // Mismatch Type B: Slot is empty or missing items -> Fetch them!
                if (current[i].isEmpty() || (ItemStack.isSameItemSameComponents(current[i], targetItems.get(i)) && curAmt < tarAmt)) {
                    int src = findSource(current, targetItems.get(i));
                    if (src != -1) {
                        LOGGER.info("Slot {} missing {}. Fetching from {}.", i, targetItems.get(i), src);
                        click(src, 0, current, cursor);
                        pickupSlot = src;
                        changed = true;
                        break; // break the for loop to process the new cursor!
                    }
                }
            }
        } // end while loop
        
        LOGGER.info("Queued {} exact inventory clicks for sorting globally optimized.", actionQueue.size());
    }

    private boolean isSameAndEqual(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return ItemStack.isSameItemSameComponents(a, b) && a.getCount() == b.getCount();
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
                LOGGER.debug("findSafeDropAny: Found truly blank space at {}", i);
                return i;
            }
        }
        // Fallback: ANY empty slot
        for (int i = 0; i < current.length; i++) {
            if (i == pickupSlot) continue; // Don't drop it right back where we just panicked and picked it up!
            if (current[i].isEmpty()) {
                LOGGER.debug("findSafeDropAny: Found any empty slot at {}", i);
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

    private void click(int idx, int button, ItemStack[] current, CursorState cs) {
        ItemStack slot = current[idx];
        ItemStack cursor = cs.stack;

        if (button == 0) {
            if (cursor.isEmpty()) {
                cs.stack = slot;
                current[idx] = ItemStack.EMPTY;
            } else if (slot.isEmpty()) {
                current[idx] = cursor;
                cs.stack = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(cursor, slot)) {
                int trans = Math.min(cursor.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(trans);
                cursor.shrink(trans);
                if (cursor.isEmpty()) cs.stack = ItemStack.EMPTY;
            } else {
                current[idx] = cursor;
                cs.stack = slot;
            }
        } else if (button == 1) {
            if (cursor.isEmpty()) {
                if (!slot.isEmpty()) {
                    int take = (slot.getCount() + 1) / 2;
                    cs.stack = slot.copyWithCount(take);
                    slot.shrink(take);
                    if (slot.isEmpty()) current[idx] = ItemStack.EMPTY;
                }
            } else if (slot.isEmpty() || ItemStack.isSameItemSameComponents(cursor, slot)) {
                if (slot.isEmpty()) {
                    current[idx] = cursor.copyWithCount(1);
                } else if (slot.getCount() < slot.getMaxStackSize()) {
                    slot.grow(1);
                }
                cursor.shrink(1);
                if (cursor.isEmpty()) cs.stack = ItemStack.EMPTY;
            } else {
                current[idx] = cursor;
                cs.stack = slot;
            }
        }

        actionQueue.add(new Action(targetSlots.get(idx).index, button));
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
            LOGGER.info("Sorting execution completed instantly.");
            stopSort();
            return;
        }

        int delayTicks = Math.max(1, 20 / cps); // Convert CPS to ticks (20 ticks per second)
        
        if (tickCounter < delayTicks) {
            tickCounter++;
            return;
        }

        if (currentActionIndex >= actionQueue.size()) {
            LOGGER.info("Sorting execution completed.");
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
