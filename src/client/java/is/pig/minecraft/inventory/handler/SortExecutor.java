package is.pig.minecraft.inventory.handler;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import is.pig.minecraft.lib.action.ActionCallback;

import java.util.List;

/**
 * Executes a deterministic sort plan via a single-phase Permutation Cycle Decomposition.
 * 
 * <p>Version 3.0: Legacy Execution Engine.
 * @deprecated Migrated to {@link RobustSortOrchestrator}
 */
@Deprecated
public class SortExecutor implements ActionCallback {

    private static SortExecutor INSTANCE;

    private final java.util.concurrent.atomic.AtomicBoolean isExecuting = new java.util.concurrent.atomic.AtomicBoolean(false);

    private is.pig.minecraft.lib.util.telemetry.MetaActionSession currentSession;

    private SortExecutor() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    public static SortExecutor getInstance() {
        if (INSTANCE == null) INSTANCE = new SortExecutor();
        return INSTANCE;
    }

    /**
     * @deprecated Migrated to {@link RobustSortOrchestrator#startSort(is.pig.minecraft.lib.inventory.sort.TargetInventorySnapshot)}
     */
    @Deprecated
    public void startSort(List<Slot> slots, List<ItemStack> target) {
        if (currentSession != null) {
            currentSession.warn("SortExecutor.startSort called but logic is DEPRECATED and REMOVED.");
        }
        // No-op: Legacy execution path closed.
    }

    @Override
    public void onResult(boolean success) {
        // No-op
    }

    public void stopSort(boolean notifySuccess) {
        this.isExecuting.set(false);
        is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().clear("piggy-inventory-sort");

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            if (currentSession != null) currentSession.discard();
            this.currentSession = null;
            return;
        }

        if (notifySuccess) {
            if (currentSession != null) currentSession.succeed();
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

    private void onTick(Minecraft client) {
        if (!isExecuting.get()) return;
        // Legacy tick handler - doing nothing now.
    }
}
