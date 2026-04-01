package is.pig.minecraft.inventory.handler;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import is.pig.minecraft.lib.action.ActionCallback;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes a deterministic sort plan via a single-phase Permutation Cycle Decomposition.
 * Now featuring asychronous planning, cursor-awareness, and fail-fast recovery.
 * 
 * <p>Version 3.0: 100% Robust Thread-Safe Orchestration Engaged.
 */
public class SortExecutor implements ActionCallback {

    private static SortExecutor INSTANCE;

    private final AtomicBoolean isExecuting = new AtomicBoolean(false);
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final AtomicBoolean isRescuing = new AtomicBoolean(false);
    private int lastStateHash = 0;

    private is.pig.minecraft.lib.util.telemetry.MetaActionSession currentSession;

    private List<Slot> targetSlots;
    private List<ItemStack> targetItems;
    private Slot[] slotsArray;
    
    private static final net.minecraft.resources.ResourceLocation SORT_ICON =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("piggy", "textures/gui/icons/quick_sort.png");

    private SortExecutor() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    public static SortExecutor getInstance() {
        if (INSTANCE == null) INSTANCE = new SortExecutor();
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Execution API
    // -------------------------------------------------------------------------

    public void startSort(List<Slot> slots, List<ItemStack> target) {
        if (is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager.getInstance().getCurrentSession().isEmpty()) {
            this.currentSession = is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager.getInstance().startSession("Sort");
        } else {
            this.currentSession = is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager.getInstance().getCurrentSession().get();
        }

        if (slots.size() != target.size()) {
            abortSort("Invariant failed: Slot count mismatched with target count.");
            return;
        }

        this.targetSlots = slots;
        this.targetItems = target;
        this.isExecuting.set(true);

        if (currentSession != null) {
            currentSession.info("SortExecutor v3.0 starting - Async Architecture Active (Retry #" + retryCount.get() + ")");
        }

        buildQueueAsync();
    }

    private void buildQueueAsync() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        // Verify the current inventory state hasn't devolved into an infinite loop of the same state
        int currentHash = calculateStateHash();
        if (retryCount.get() > 0 && currentHash == lastStateHash && !isRescuing.get()) {
            abortSort("Recovery cycle detected - State hash identity check failed.");
            return;
        }
        this.lastStateHash = currentHash;

        int containerId = client.player.containerMenu.containerId;

        // 0. Cursor Rescue (Invariant Guard)
        if (!client.player.containerMenu.getCarried().isEmpty()) {
            isRescuing.set(true);
            int emptySlotId = -1;
            for (Slot slot : client.player.containerMenu.slots) {
                if (slot.getItem().isEmpty()) {
                    emptySlotId = slot.index;
                    break;
                }
            }

            if (emptySlotId != -1) {
                if (currentSession != null) currentSession.info("Cursor not empty - Parking item before re-scan.");
                var rescueAction = new is.pig.minecraft.lib.action.inventory.ClickWindowSlotAction(
                        containerId, emptySlotId, 0, net.minecraft.world.inventory.ClickType.PICKUP, "piggy-inventory-sort")
                        .withExpectedCursorAfter(ItemStack::isEmpty);
                
                rescueAction.setCallback(success -> {
                    isRescuing.set(false);
                    if (success) Minecraft.getInstance().execute(this::buildQueueAsync);
                    else abortSort("Failed to park cursor item during recovery.");
                });
                
                is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().enqueue(rescueAction);
                return;
            } else {
                abortSort("Inventory 100% full (no rescue slots found). Cannot recover cursor.");
                return;
            }
        }

        // 1. Snapshot the live state on the Main Thread
        ItemStack[] currentSnapshot = new ItemStack[targetSlots.size()];
        slotsArray = new Slot[targetSlots.size()];
        java.util.Set<Integer> sortedIndices = new java.util.HashSet<>();

        for (int i = 0; i < targetSlots.size(); i++) {
            slotsArray[i] = targetSlots.get(i);
            currentSnapshot[i] = slotsArray[i].getItem().copy();
            sortedIndices.add(slotsArray[i].index);
        }

        int externalBuffer = is.pig.minecraft.lib.inventory.sort.SortingClickGenerator.NO_SLOT;
        for (Slot slot : client.player.containerMenu.slots) {
            if (!sortedIndices.contains(slot.index) && slot.getItem().isEmpty()) {
                externalBuffer = slot.index;
                break;
            }
        }

        ItemStack[] desiredStateArray = new ItemStack[targetItems.size()];
        for (int i = 0; i < targetItems.size(); i++) {
            desiredStateArray[i] = targetItems.get(i).copy();
        }

        // 2. Off-thread generation
        int finalExternalBuffer = externalBuffer;
        CompletableFuture.supplyAsync(() -> {
            is.pig.minecraft.lib.inventory.sort.SortingClickGenerator generator =
                    new is.pig.minecraft.lib.inventory.sort.SortingClickGenerator(
                            currentSnapshot, desiredStateArray, slotsArray,
                            finalExternalBuffer, containerId, "piggy-inventory-sort",
                            is.pig.minecraft.lib.action.ActionPriority.NORMAL
                    );
            return generator.generate();
        }).thenAcceptAsync(clicks -> {
            // 3. Back to Main Thread to enqueue
            if (!isExecuting.get()) return;

            if (clicks.size() > 5000) {
                abortSort("Safety runaway: Generation plan exceeded 5000 steps.");
                return;
            }

            if (currentSession != null) currentSession.info("Sort Plan: " + clicks.size() + " atomic verified steps.");

            int cps = is.pig.minecraft.inventory.config.PiggyInventoryConfig.getInstance().getTickDelay();
            for (is.pig.minecraft.lib.action.inventory.ClickWindowSlotAction action : clicks) {
                action.setCallback(this); // Register for fail-fast recovery
                if (cps <= 0) action.setIgnoreGlobalCps(true);
                is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().enqueue(action);
            }
        }, client::execute);
    }

    @Override
    public void onResult(boolean success) {
        if (!success && isExecuting.get()) {
            if (retryCount.incrementAndGet() > 3) {
                abortSort("Resilience threshold reached (3 failures). Aborting.");
            } else {
                if (currentSession != null) currentSession.warn("Sort action verification failed. Attempting recovery...");
                
                // Clear the rest of the current plan to prevent scrambled inventory 
                is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().clear("piggy-inventory-sort");
                
                // Wait 1 tick for state to settle, then re-spawn
                Minecraft.getInstance().execute(this::buildQueueAsync);
            }
        }
    }

    private int calculateStateHash() {
        int hash = 1;
        for (Slot slot : targetSlots) {
            ItemStack stack = slot.getItem();
            hash = 31 * hash + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
            hash = 31 * hash + stack.getCount();
        }
        return hash;
    }

    public void stopSort(boolean notifySuccess) {
        this.isExecuting.set(false);
        this.retryCount.set(0);
        is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().clear("piggy-inventory-sort");

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
                if (currentSession != null) currentSession.fail("Final state validation mismatch.");
            }
        } else if (currentSession != null) {
            currentSession.discard();
        }

        this.currentSession = null;
        SortHandler.getInstance().cleanup();
    }

    public void abortSort(String reason) {
        if (currentSession != null) currentSession.fail("Sort Aborted: " + reason);
        is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().clear("piggy-inventory-sort");
        stopSort(false);
    }

    private boolean verifySortState(Minecraft client, boolean logErrors) {
        if (client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>
                || SortHandler.getInstance().getHiddenScreen() != null) {
            boolean success = true;
            for (int i = 0; i < targetSlots.size(); i++) {
                ItemStack current = targetSlots.get(i).getItem();
                ItemStack target = targetItems.get(i);
                if (!isSameAndEqual(current, target)) {
                    if (currentSession != null && logErrors) {
                        currentSession.error(is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter
                                .formatValidationFailure(i, target, current));
                    }
                    success = false;
                }
            }
            return success;
        }
        return false;
    }

    private boolean isSameAndEqual(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return ItemStack.isSameItemSameComponents(a, b) && a.getCount() == b.getCount();
    }

    private void onTick(Minecraft client) {
        if (!isExecuting.get()) return;
        if (client.player == null) { abortSort("Disconnected"); return; }

        if (!is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().hasActions("piggy-inventory-sort")) {
            stopSort(true);
        }
    }
}
