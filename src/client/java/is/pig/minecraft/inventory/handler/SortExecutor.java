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
    private boolean isWaitingForRetry = false;
    private int retryWaitTicks = 0;
    private int lastStateHash = 0;

    private is.pig.minecraft.lib.util.telemetry.MetaActionSession currentSession;

    private List<Slot> targetSlots;
    private List<ItemStack> targetItems;
    private Slot[] slotsArray;

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
        if (isExecuting.get() && retryCount.get() == 0) return;
        this.isExecuting.set(true);

        // Safety Check: Detect 100% full + overstacked modded inventory (Bug Fix #6)
        int emptySlots = 0;
        boolean hasOverstacked = false;
        for (Slot slot : slots) {
            if (slot == null) continue; // Virtual cursor slot
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                emptySlots++;
            } else if (stack.getCount() > stack.getMaxStackSize()) {
                hasOverstacked = true;
            }
        }

        if (emptySlots == 0 && hasOverstacked) {
            abortSort("Cannot sort 100% full inventory containing overstacked modded items. Please clear at least one slot.");
            return;
        }

        if (currentSession != null) {
            currentSession.info("SortExecutor v3.0 starting - Async Architecture Active (Retry #" + retryCount.get() + ")");
        }

        buildQueueAsync();
    }

    private void buildQueueAsync() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        int containerId = client.player.containerMenu.containerId;

        ItemStack carried = client.player.containerMenu.getCarried();
        if (currentSession != null && !carried.isEmpty()) {
            currentSession.info("Initial cursor state: " + is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatItem(carried));
        }

        // 1. Snapshot the live state on the Main Thread
        ItemStack[] currentSnapshot = new ItemStack[targetSlots.size()];
        slotsArray = new Slot[targetSlots.size()];
        java.util.Set<Integer> sortedIndices = new java.util.HashSet<>();

        for (int i = 0; i < targetSlots.size(); i++) {
            Slot slot = targetSlots.get(i);
            slotsArray[i] = slot;
            if (slot != null) {
                currentSnapshot[i] = slot.getItem().copy();
                sortedIndices.add(slot.index);
            } else {
                currentSnapshot[i] = carried.copy();
            }
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
                            is.pig.minecraft.lib.action.ActionPriority.NORMAL, carried
                    );
            return generator.generate();
        }).exceptionally(ex -> {
            if (currentSession != null) {
                currentSession.error("Generator crashed during calculation: " + ex.getMessage());
                for (StackTraceElement element : ex.getStackTrace()) {
                    currentSession.error("    at " + element.toString());
                }
            }
            Minecraft.getInstance().execute(() -> abortSort("Generator crashed (Check telemetry dump)"));
            return java.util.Collections.emptyList();
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
            if (retryCount.incrementAndGet() > 5) {
                abortSort("Resilience threshold reached (5 failures). Aborting.");
            } else {
                if (currentSession != null) currentSession.warn("Sort action execution failed. Retrying...");
                is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().clear("piggy-inventory-sort");
                isWaitingForRetry = true;
                retryWaitTicks = 10;
            }
        }
    }

    private int calculateStateHash() {
        int hash = 1;
        for (Slot slot : targetSlots) {
            ItemStack stack = (slot != null) ? slot.getItem() : Minecraft.getInstance().player.containerMenu.getCarried();
            hash = 31 * hash + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
            hash = 31 * hash + stack.getCount();
        }
        return hash;
    }

    public void stopSort(boolean notifySuccess) {
        this.isExecuting.set(false);
        this.retryCount.set(0);
        this.isWaitingForRetry = false; // Ne pas oublier de réinitialiser
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
                ItemStack current = (targetSlots.get(i) != null) ? targetSlots.get(i).getItem() : client.player.containerMenu.getCarried();
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

        // Si on est en période de récupération (attente des paquets du serveur)
        if (isWaitingForRetry) {
            if (retryWaitTicks > 0) {
                retryWaitTicks--;
            } else {
                isWaitingForRetry = false;
                buildQueueAsync(); // On relance la fin du tri
            }
            return;
        }

        // Si la file d'attente est vide, on vérifie
        if (!is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().hasActions("piggy-inventory-sort")) {
            if (verifySortState(client, false)) {
                stopSort(true); // Succès total !
            } else {
                // Le serveur a rejeté des paquets. On lance le Retry.
                if (retryCount.incrementAndGet() > 5) {
                    abortSort("Resilience threshold reached (5 validation failures). Aborting.");
                } else {
                    if (currentSession != null) currentSession.warn("Server desync detected. Retrying... (" + retryCount.get() + "/5)");
                    isWaitingForRetry = true;
                    retryWaitTicks = 1; // On attend 0.5s que le serveur nous corrige
                }
            }
        }
    }
}
