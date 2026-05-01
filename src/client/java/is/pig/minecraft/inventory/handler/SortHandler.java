package is.pig.minecraft.inventory.handler;

import is.pig.minecraft.api.*;
import is.pig.minecraft.api.registry.PiggyServiceRegistry;
import is.pig.minecraft.api.spi.*;
import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import is.pig.minecraft.inventory.locking.SlotLockingManager;
import is.pig.minecraft.inventory.sorting.Comparators;
import is.pig.minecraft.inventory.sorting.StackMerger;
import is.pig.minecraft.inventory.sorting.TargetInventorySnapshot;
import is.pig.minecraft.inventory.sorting.layout.SortingLayout;
import is.pig.minecraft.inventory.sorting.layout.RowLayout;
import is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-agnostic sorting handler using SPI adapters.
 */
public class SortHandler {
    private static final SortHandler INSTANCE = new SortHandler();

    private SortHandler() {}

    private boolean awaitingContainer = false;
    private long requestTime = 0;
    private Object hiddenScreen = null;
    
    private enum State { IDLE, WAITING_FOR_ITEMS, SORTING }
    private State state = State.IDLE;
    private int waitTicks = 0;

    public void onTick(Object client) {
        ScreenAdapter screenAdapter = PiggyServiceRegistry.getScreenAdapter();
        if (state == State.WAITING_FOR_ITEMS && screenAdapter.isContainerScreen(this.hiddenScreen)) {
            waitTicks++;
            boolean hasItems = false;
            
            List<Integer> storageSlots = screenAdapter.getStorageSlotIndices(client);
            ItemDataAdapter itemAdapter = PiggyServiceRegistry.getItemDataAdapter();

            for (int slotIdx : storageSlots) {
                Object stack = screenAdapter.getStackInSlot(client, slotIdx);
                if (itemAdapter.getCount(stack) > 0) {
                    hasItems = true;
                    break;
                }
            }

            if (hasItems || waitTicks > 20) {
                state = State.SORTING;
                handleSort(client, -1, this.hiddenScreen);
            }
        }
    }

    public static SortHandler getInstance() {
        return INSTANCE;
    }

    public Object getHiddenScreen() {
        return hiddenScreen;
    }

    public void triggerRemoteSort(Object client) {
        WorldStateAdapter worldState = PiggyServiceRegistry.getWorldStateAdapter();
        HitResult hit = worldState.getCrosshairTarget(client);
        
        if (hit == null || (hit.getType() != HitResult.Type.BLOCK && hit.getType() != HitResult.Type.ENTITY)) return;

        boolean isContainer = false;
        String worldId = worldState.getCurrentWorldId();

        if (hit instanceof BlockHitResult blockHit) {
            isContainer = worldState.isContainer(worldId, blockHit.getBlockPos());
        } else if (hit.getType() == HitResult.Type.ENTITY) {
            // Assume we can get the entity object from HitResult if we refactor it, 
            // for now let's assume it's available or we can query by ID.
            // In a real refactor, HitResult would hold the Object entity.
        }

        if (!isContainer) return;

        if (awaitingContainer && System.currentTimeMillis() - requestTime < 1000) return;

        MetaActionSessionManager.getInstance().startSession("Sort");
        
        awaitingContainer = true;
        requestTime = System.currentTimeMillis();

        WorldInteractionAdapter interaction = PiggyServiceRegistry.getWorldInteractionAdapter();
        if (hit instanceof BlockHitResult blockHit) {
            interaction.useItemOn(client, InteractionHand.MAIN_HAND, blockHit);
        } else {
            // Handle entity interaction
        }
        worldState.swingHand(client, InteractionHand.MAIN_HAND);
    }

    public boolean interceptSetScreen(Object screen) {
        if (!awaitingContainer) return false;

        ScreenAdapter screenAdapter = PiggyServiceRegistry.getScreenAdapter();
        if (screenAdapter.isContainerScreen(screen)) {
            if (screenAdapter.isInventoryScreen(screen)) {
                awaitingContainer = false;
                return false;
            }

            if (System.currentTimeMillis() - requestTime > 2000) {
                awaitingContainer = false;
                return false;
            }

            this.hiddenScreen = screen;
            this.state = State.WAITING_FOR_ITEMS;
            this.waitTicks = 0;
            Object client = PiggyServiceRegistry.getWorldStateAdapter().getClient();
            // We need width/height, could get from ScreenAdapter too
            screenAdapter.initScreen(screen, client, 800, 600); // Placeholder sizes

            return true;
        }

        awaitingContainer = false;
        return false;
    }

    public void cleanup() {
        if (this.hiddenScreen != null) {
            Object client = PiggyServiceRegistry.getWorldStateAdapter().getClient();
            PiggyServiceRegistry.getInventoryInteractionAdapter().closeScreen(client);
        }
        this.hiddenScreen = null;
        this.awaitingContainer = false;
        this.state = State.IDLE;
    }

    public void handleSort(Object client, int hoveredSlotIndex) {
        ScreenAdapter screenAdapter = PiggyServiceRegistry.getScreenAdapter();
        if (!screenAdapter.isContainerScreenOpen(client)) return;
        
        // This is a bit tricky since we need the actual screen object for handleSort(client, slot, screen)
        // But if it's the current screen, we can just pass it.
        // For now, let's assume we can get it.
    }

    public void handleSort(Object client, int hoveredSlotIndex, Object screen) {
        if (MetaActionSessionManager.getInstance().getCurrentSession().isEmpty()) {
             MetaActionSessionManager.getInstance().startSession("Sort");
        }

        ScreenAdapter screenAdapter = PiggyServiceRegistry.getScreenAdapter();
        ItemDataAdapter itemAdapter = PiggyServiceRegistry.getItemDataAdapter();

        List<Integer> allSlots = screenAdapter.getAllSlotIndices(client);
        List<Integer> slotsToSortIndices = new ArrayList<>();
        List<Object> items = new ArrayList<>();

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        // Determine target slots (either player inv or storage inv)
        boolean targetPlayerInv = false;
        if (hoveredSlotIndex != -1) {
            targetPlayerInv = screenAdapter.isPlayerInventorySlot(client, hoveredSlotIndex);
        }

        List<Integer> targetIndices = targetPlayerInv ? screenAdapter.getPlayerSlotIndices(client) : screenAdapter.getStorageSlotIndices(client);

        for (int idx : targetIndices) {
            if (targetPlayerInv && SlotLockingManager.getInstance().isLocked(idx)) {
                continue;
            }
            
            slotsToSortIndices.add(idx);
            items.add(itemAdapter.copy(screenAdapter.getStackInSlot(client, idx)));

            int x = screenAdapter.getSlotX(client, idx);
            int y = screenAdapter.getSlotY(client, idx);
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }

        Object carried = screenAdapter.getCursorStack(client);
        if (itemAdapter.getCount(carried) > 0) {
            items.add(itemAdapter.copy(carried));
        }

        if (items.isEmpty()) {
            if (this.hiddenScreen != null) cleanup();
            return;
        }

        int cols = Math.max(1, (maxX - minX) / 18 + 1);
        int rows = Math.max(1, (maxY - minY) / 18 + 1);

        StackMerger.merge(items, null, false);

        PiggyInventoryConfig cfg = PiggyInventoryConfig.getInstance();
        List<String> comparatorOrder = cfg.getSortComparatorOrder();
        items.sort(Comparators.buildHierarchy(comparatorOrder));

        List<java.util.Comparator<Object>> layoutComparators = Comparators.buildComparatorList(comparatorOrder);
        SortingLayout layout = cfg.getSortLayout() == PiggyInventoryConfig.SortLayout.COLUMN
                ? new is.pig.minecraft.inventory.sorting.layout.ColumnLayout(layoutComparators)
                : new RowLayout(layoutComparators);
        
        List<Object> finalPositions = layout.layout(items, slotsToSortIndices);

        if (items.size() > slotsToSortIndices.size()) {
            finalPositions.add(items.get(items.size() - 1));
        } else {
            finalPositions.add(null); // Empty cursor
        }
        
        slotsToSortIndices.add(-1); // Virtual cursor slot

        Map<Integer, Object> slotTargets = new HashMap<>();
        Object cursorTarget = null;

        for (int i = 0; i < slotsToSortIndices.size(); i++) {
            int idx = slotsToSortIndices.get(i);
            Object targetStack = finalPositions.get(i);
            if (idx != -1) {
                slotTargets.put(idx, targetStack);
            } else {
                cursorTarget = targetStack;
            }
        }

        TargetInventorySnapshot snapshot = new TargetInventorySnapshot(
                screenAdapter.getContainerId(client),
                slotTargets,
                cursorTarget,
                "piggy-inventory-sort"
        );

        RobustSortOrchestrator.getInstance().startSort(snapshot);
    }
}
