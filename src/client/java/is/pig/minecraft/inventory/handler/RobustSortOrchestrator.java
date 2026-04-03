package is.pig.minecraft.inventory.handler;

import is.pig.minecraft.lib.action.ActionPriority;
import is.pig.minecraft.lib.action.BulkAction;
import is.pig.minecraft.lib.action.IAction;
import is.pig.minecraft.lib.action.PiggyActionQueue;
import is.pig.minecraft.lib.action.inventory.ClickWindowSlotAction;
import is.pig.minecraft.lib.inventory.sort.BurstController;
import is.pig.minecraft.lib.inventory.sort.InventoryOptimizer;
import is.pig.minecraft.lib.inventory.sort.InventorySnapshot;
import is.pig.minecraft.lib.inventory.sort.Move;
import is.pig.minecraft.lib.inventory.sort.TargetInventorySnapshot;
import is.pig.minecraft.lib.util.telemetry.MetaActionSession;
import is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Robust, state-machine based sorting orchestrator.
 * Dynamically reconciles client-side state with a target layout.
 */
public class RobustSortOrchestrator {
    private static final RobustSortOrchestrator INSTANCE = new RobustSortOrchestrator();

    private final InventoryOptimizer optimizer = new InventoryOptimizer();
    private final BurstController burstController = new BurstController();

    private TargetInventorySnapshot targetSnapshot;
    private MetaActionSession session;
    private int verificationDelayTicks = 0;
    private InventorySnapshot predictedSnapshot;

    private RobustSortOrchestrator() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    public static RobustSortOrchestrator getInstance() {
        return INSTANCE;
    }

    /**
     * Starts the sorting process toward the given target snapshot.
     */
    public void startSort(TargetInventorySnapshot target) {
        this.targetSnapshot = target;
        this.session = MetaActionSessionManager.getInstance().startSession("InventorySort");
        session.info("Sorting started for container: " + target.containerId());
        
        executeIteration();
    }

    private void executeIteration() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            abort("Player disconnected");
            return;
        }

        InventorySnapshot current = InventorySnapshot.capture(client);
        if (current.containerId() != targetSnapshot.containerId()) {
            abort("Container ID mismatch! Expected " + targetSnapshot.containerId() + ", got " + current.containerId());
            return;
        }

        // Goal Check
        if (isGoalReached(current)) {
            session.info("Target layout achieved successfully.");
            session.succeed();
            this.targetSnapshot = null;
            return;
        }

        // Planning
        List<Move> plan = optimizer.consolidate(current);
        if (plan.isEmpty()) {
            plan = optimizer.planCycles(current, toInventorySnapshot(targetSnapshot));
        }

        if (plan.isEmpty()) {
            session.error("Plan empty but target not reached. Divergence detected.");
            session.fail("Unresolvable discrepancy");
            this.targetSnapshot = null;
            return;
        }

        // Burst & Prediction
        List<Move> burst = burstController.getNextBurst(plan);
        this.predictedSnapshot = current.applyMoves(burst);
        
        session.logAction("Sorting Burst", "Size: " + burst.size() + " of Plan: " + plan.size(), "Window: " + burstController.getCurrentWindow());

        // Map to Actions
        List<IAction> actions = burst.stream()
                .map(this::mapMoveToAction)
                .collect(Collectors.toList());

        BulkAction batch = new BulkAction(
                "piggy-inventory",
                ActionPriority.NORMAL,
                actions,
                () -> true, // Individual actions in BulkAction already verify themselves
                100,
                "SortBatch",
                (fullySuccessful, failedActions) -> {
                    // Start verification delay
                    this.verificationDelayTicks = 3; 
                }
        );

        PiggyActionQueue.getInstance().enqueue(batch);
    }

    private void onTick(Minecraft client) {
        if (verificationDelayTicks > 0) {
            verificationDelayTicks--;
            if (verificationDelayTicks == 0) {
                verifyAndContinue();
            }
        }
    }

    private void verifyAndContinue() {
        if (targetSnapshot == null) return;
        
        Minecraft client = Minecraft.getInstance();
        InventorySnapshot actual = InventorySnapshot.capture(client);

        if (isMatch(actual, predictedSnapshot)) {
            session.debug("Burst verified correctly.");
            burstController.reportSuccess();
        } else {
            session.warn("Burst verification FAILED. Predicted state drift.");
            burstController.reportDesync();
        }

        // Loop next
        executeIteration();
    }

    private IAction mapMoveToAction(Move move) {
        ClickType type = ClickType.PICKUP;
        int button = (move instanceof Move.LeftClick) ? 0 : 1;
        
        return new ClickWindowSlotAction(
                targetSnapshot.containerId(),
                move.slotIndex(),
                button,
                type,
                "piggy-inventory",
                ActionPriority.NORMAL
        );
    }

    private InventorySnapshot toInventorySnapshot(TargetInventorySnapshot target) {
        List<InventorySnapshot.SlotState> slots = target.slotTargets().entrySet().stream()
                .map(e -> new InventorySnapshot.SlotState(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        return new InventorySnapshot(target.containerId(), slots, target.cursorTarget());
    }

    private boolean isGoalReached(InventorySnapshot current) {
        Map<Integer, ItemStack> actual = toMap(current.slots());
        for (var entry : targetSnapshot.slotTargets().entrySet()) {
            ItemStack cur = actual.getOrDefault(entry.getKey(), ItemStack.EMPTY);
            if (!isSame(cur, entry.getValue())) return false;
        }
        return isSame(current.cursor(), targetSnapshot.cursorTarget());
    }

    private boolean isMatch(InventorySnapshot a, InventorySnapshot b) {
        if (a.slots().size() != b.slots().size()) return false;
        Map<Integer, ItemStack> mapA = toMap(a.slots());
        Map<Integer, ItemStack> mapB = toMap(b.slots());
        
        for (var entry : mapA.entrySet()) {
            if (!isSame(entry.getValue(), mapB.getOrDefault(entry.getKey(), ItemStack.EMPTY))) return false;
        }
        return isSame(a.cursor(), b.cursor());
    }

    private boolean isSame(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        // Strict MojMap equality for components and count
        return ItemStack.isSameItemSameComponents(a, b) && a.getCount() == b.getCount();
    }

    private Map<Integer, ItemStack> toMap(List<InventorySnapshot.SlotState> slots) {
        Map<Integer, ItemStack> map = new HashMap<>();
        for (InventorySnapshot.SlotState state : slots) {
            map.put(state.index(), state.stack());
        }
        return map;
    }

    private void abort(String reason) {
        if (session != null) session.fail(reason);
        this.targetSnapshot = null;
    }
}
