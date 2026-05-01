package is.pig.minecraft.inventory.handler;

import is.pig.minecraft.api.Action;
import is.pig.minecraft.api.ActionPriority;
import is.pig.minecraft.api.registry.PiggyServiceRegistry;
import is.pig.minecraft.api.spi.InputAdapter;
import is.pig.minecraft.api.spi.InventoryInteractionAdapter;
import is.pig.minecraft.api.spi.ItemDataAdapter;
import is.pig.minecraft.api.spi.ScreenAdapter;
import is.pig.minecraft.inventory.sorting.InventoryOptimizer;
import is.pig.minecraft.inventory.sorting.InventorySnapshot;
import is.pig.minecraft.inventory.sorting.Move;
import is.pig.minecraft.inventory.sorting.TargetInventorySnapshot;
import is.pig.minecraft.inventory.util.InventorySnapshotter;
import is.pig.minecraft.lib.action.BurstBulkAction;
import is.pig.minecraft.lib.action.PiggyActionQueue;
import is.pig.minecraft.lib.action.inventory.ClickWindowSlotAction;
import is.pig.minecraft.lib.util.telemetry.MetaActionSession;
import is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Robust, state-machine based sorting orchestrator.
 * Uses library-provided BurstBulkAction for congestion-controlled sorting.
 */
public class RobustSortOrchestrator {
    private static final RobustSortOrchestrator INSTANCE = new RobustSortOrchestrator();
    private static final Logger LOGGER = LoggerFactory.getLogger(RobustSortOrchestrator.class);

    private final InventoryOptimizer optimizer = new InventoryOptimizer();
    private TargetInventorySnapshot targetSnapshot;
    private InventorySnapshot lastSnapshot;

    private RobustSortOrchestrator() {}

    public static RobustSortOrchestrator getInstance() {
        return INSTANCE;
    }

    public void startSort(TargetInventorySnapshot target) {
        if (target == null) return;
        this.targetSnapshot = target;
        this.lastSnapshot = null;
        
        MetaActionSession session = MetaActionSessionManager.getInstance().startSession("InventorySort");
        session.info("Sorting started for container: " + target.containerId());

        Supplier<List<Action>> planProvider = () -> {
            if (targetSnapshot == null) return null;
            
            Object client = PiggyServiceRegistry.getWorldStateAdapter().getClient();
            InventorySnapshot current = InventorySnapshotter.capture(client);
            
            if (current.containerId() != targetSnapshot.containerId()) {
                MetaActionSessionManager.getInstance().getSession("InventorySort").ifPresent(active -> 
                    active.error("Sort Interrupted: Container closed mid-sort."));
                return null; 
            }

            this.lastSnapshot = current;
            
            InventorySnapshot targetInvSnapshot = toInventorySnapshot(targetSnapshot);
            List<Move> plan = optimizer.consolidate(current, targetInvSnapshot);
            boolean isCycle = false;
            if (plan.isEmpty()) {
                plan = optimizer.planCycles(current, targetInvSnapshot);
                isCycle = true;
            }

            if (!plan.isEmpty()) {
                is.pig.minecraft.lib.util.telemetry.StructuredEventDispatcher.getInstance().dispatch(
                        new is.pig.minecraft.inventory.telemetry.SortingCycleEvent(current.containerId(), plan.size(), isCycle));
            }

            return plan.stream().map(this::mapMoveToAction).collect(Collectors.toList());
        };

        BooleanSupplier verifyCondition = () -> {
            if (targetSnapshot == null) return true;
            
            Object client = PiggyServiceRegistry.getWorldStateAdapter().getClient();
            InventorySnapshot current = InventorySnapshotter.capture(client);
            
            if (current.containerId() != targetSnapshot.containerId()) {
                return false; 
            }

            if (lastSnapshot != null) {
                if (isMatch(current, lastSnapshot)) {
                    MetaActionSessionManager.getInstance().getSession("InventorySort").ifPresent(active -> {
                        active.warn("No progress after burst. Performing detailed audit...");
                        logMismatches(active, current, targetSnapshot);
                    });
                    return false;
                }
            }
            
            return true;
        };

        IntSupplier latencySupplier = () -> PiggyServiceRegistry.getWorldStateAdapter().getPing();

        BurstBulkAction action = new BurstBulkAction(
                "piggy-inventory",
                ActionPriority.NORMAL,
                "InventorySort",
                planProvider,
                verifyCondition,
                latencySupplier,
                20,
                (success) -> {
                    if (success) session.succeed();
                    else session.fail("Burst action failed or timed out.");
                    this.targetSnapshot = null;
                    this.lastSnapshot = null;
                }
        );

        PiggyActionQueue.getInstance().enqueue(action);
    }

    private Action mapMoveToAction(Move move) {
        if (targetSnapshot == null) return null;
        
        int button = switch (move.type()) {
            case PICKUP_ALL, DEPOSIT_ALL, SWAP -> 0;
            case PICKUP_HALF, DEPOSIT_ONE -> 1;
        };
        
        // Note: Using ClickWindowSlotAction which will be refactored to be agnostic soon.
        // For now, we use "PICKUP" click type as string-like if we can, 
        // but ClickWindowSlotAction currently uses net.minecraft.world.inventory.ClickType.
        // I will fix ClickWindowSlotAction first or use a factory.
        
        return new ClickWindowSlotAction(
                targetSnapshot.containerId(),
                move.slotIndex(),
                button,
                "PICKUP",
                "piggy-inventory",
                ActionPriority.NORMAL
        );
    }

    private InventorySnapshot toInventorySnapshot(TargetInventorySnapshot target) {
        if (target == null) return null;
        List<InventorySnapshot.SlotState> slots = target.slotTargets().entrySet().stream()
                .map(e -> new InventorySnapshot.SlotState(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        return new InventorySnapshot(target.containerId(), slots, target.cursorTarget());
    }

    private void logMismatches(MetaActionSession session, InventorySnapshot current, TargetInventorySnapshot target) {
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        for (var sC : current.slots()) {
            int idx = sC.index();
            if (!target.slotTargets().containsKey(idx)) continue;
            Object expectedStack = target.slotTargets().get(idx);
            if (!adapter.areItemsEqual(sC.stack(), expectedStack) || adapter.getCount(sC.stack()) != adapter.getCount(expectedStack)) {
                session.error(String.format("Mismatch in Slot %d", idx));
            }
        }
        Object expectedCursor = target.cursorTarget();
        if (!adapter.areItemsEqual(current.cursor(), expectedCursor) || adapter.getCount(current.cursor()) != adapter.getCount(expectedCursor)) {
            session.error("Mismatch in Cursor");
        }
    }

    private boolean isMatch(InventorySnapshot a, InventorySnapshot b) {
        if (a == null || b == null) return false;
        if (a.containerId() != b.containerId()) return false;
        if (a.slots().size() != b.slots().size()) return false;
        
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        for (int i = 0; i < a.slots().size(); i++) {
            var sA = a.slots().get(i);
            var sB = b.slots().get(i);
            if (!adapter.areItemsEqual(sA.stack(), sB.stack()) || adapter.getCount(sA.stack()) != adapter.getCount(sB.stack())) {
                return false;
            }
        }
        return adapter.areItemsEqual(a.cursor(), b.cursor()) && adapter.getCount(a.cursor()) == adapter.getCount(b.cursor());
    }
}
