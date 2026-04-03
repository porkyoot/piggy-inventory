package is.pig.minecraft.inventory.handler;

import is.pig.minecraft.lib.action.ActionPriority;
import is.pig.minecraft.lib.action.BurstBulkAction;
import is.pig.minecraft.lib.action.IAction;
import is.pig.minecraft.lib.action.PiggyActionQueue;
import is.pig.minecraft.lib.action.inventory.ClickWindowSlotAction;
import is.pig.minecraft.lib.inventory.sort.InventoryOptimizer;
import is.pig.minecraft.lib.inventory.sort.InventorySnapshot;
import is.pig.minecraft.lib.inventory.sort.Move;
import is.pig.minecraft.lib.inventory.sort.TargetInventorySnapshot;
import is.pig.minecraft.lib.util.telemetry.MetaActionSession;
import is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
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
        session.info("Sorting started for: " + target.containerId());

        Supplier<List<IAction>> planProvider = () -> {
            if (targetSnapshot == null) return null;
            
            Minecraft client = Minecraft.getInstance();
            InventorySnapshot current = InventorySnapshot.capture(client);
            
            // Safety: ensure we are still in the same container
            if (current.containerId() != targetSnapshot.containerId()) {
                return null; // This will trigger completion/failure in BurstBulkAction
            }

            // Record the state BEFORE this burst for the verifyCondition to check progress
            this.lastSnapshot = current;
            
            // Re-plan based on current real state
            InventorySnapshot targetInvSnapshot = toInventorySnapshot(targetSnapshot);
            List<Move> plan = optimizer.consolidate(current, targetInvSnapshot);
            if (plan.isEmpty()) {
                plan = optimizer.planCycles(current, targetInvSnapshot);
            }

            return plan.stream().map(this::mapMoveToAction).collect(Collectors.toList());
        };

        BooleanSupplier verifyCondition = () -> {
            if (targetSnapshot == null) return true;
            
            Minecraft client = Minecraft.getInstance();
            InventorySnapshot current = InventorySnapshot.capture(client);
            
            // 1. Connection/Container Safety
            if (client.player == null || current.containerId() != targetSnapshot.containerId()) {
                return false; 
            }

            // 2. Progress Verification
            // If we have a previous snapshot, ensure the state has actually changed.
            // If the state is identical to lastSnapshot after a burst, it means the server/mod ignored us.
            if (lastSnapshot != null && isMatch(current, lastSnapshot)) {
                return false; // Desync detected: state didn't change!
            }
            
            return true; // We moved something! (or it was correctly applied)
        };

        IntSupplier latencySupplier = () -> {
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() != null && client.player != null) {
                var entry = client.getConnection().getPlayerInfo(client.player.getUUID());
                return entry != null ? entry.getLatency() : 0;
            }
            return 0;
        };

        BurstBulkAction action = new BurstBulkAction(
                "piggy-inventory",
                ActionPriority.NORMAL,
                "InventorySort",
                planProvider,
                verifyCondition,
                latencySupplier,
                20, // timeout ticks per burst
                (success) -> {
                    if (success) session.succeed();
                    else session.fail("Burst action failed or timed out. Possibly stuck/locked slots.");
                    this.targetSnapshot = null;
                    this.lastSnapshot = null;
                }
        );

        PiggyActionQueue.getInstance().enqueue(action);
    }

    private IAction mapMoveToAction(Move move) {
        if (targetSnapshot == null) return null;
        
        int button = switch (move.type()) {
            case PICKUP_ALL, DEPOSIT_ALL, SWAP -> 0;
            case PICKUP_HALF, DEPOSIT_ONE -> 1;
        };
        
        return new ClickWindowSlotAction(
                targetSnapshot.containerId(),
                move.slotIndex(),
                button,
                ClickType.PICKUP,
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

    private boolean isMatch(InventorySnapshot a, InventorySnapshot b) {
        if (a == null || b == null) return false;
        if (a.containerId() != b.containerId()) return false;
        if (a.slots().size() != b.slots().size()) return false;
        
        for (int i = 0; i < a.slots().size(); i++) {
            var sA = a.slots().get(i);
            var sB = b.slots().get(i);
            if (!ItemStack.isSameItemSameComponents(sA.stack(), sB.stack()) || sA.stack().getCount() != sB.stack().getCount()) {
                return false;
            }
        }
        return ItemStack.isSameItemSameComponents(a.cursor(), b.cursor()) && a.cursor().getCount() == b.cursor().getCount();
    }
}
