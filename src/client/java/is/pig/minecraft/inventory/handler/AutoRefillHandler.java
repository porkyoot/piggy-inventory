package is.pig.minecraft.inventory.handler;

import is.pig.minecraft.api.*;
import is.pig.minecraft.api.registry.PiggyServiceRegistry;
import is.pig.minecraft.api.spi.*;
import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import is.pig.minecraft.inventory.refill.RefillCategory;
import is.pig.minecraft.lib.action.PiggyActionQueue;
import is.pig.minecraft.lib.action.inventory.ClickWindowSlotAction;

/**
 * Platform-agnostic auto-refill handler.
 * ZERO net.minecraft imports.
 */
public class AutoRefillHandler {
    private static final AutoRefillHandler INSTANCE = new AutoRefillHandler();
    private long lastActionTime = 0;
    private Object lastMainHandStack = null;
    private Object lastOffHandStack = null;
    private int lastSlot = -1;

    public static AutoRefillHandler getInstance() {
        return INSTANCE;
    }

    public void onTick(Object client) {
        WorldStateAdapter worldState = PiggyServiceRegistry.getWorldStateAdapter();
        ItemDataAdapter itemAdapter = PiggyServiceRegistry.getItemDataAdapter();
        
        if (worldState.isPlayerDeadOrDying(client)) return;

        if (lastMainHandStack == null) lastMainHandStack = itemAdapter.getEmptyStack();
        if (lastOffHandStack == null) lastOffHandStack = itemAdapter.getEmptyStack();

        Object currentMainStack = worldState.getPlayerMainHandItem(client);
        Object currentOffStack = worldState.getPlayerOffhandItem(client);
        int currentSlot = worldState.getSelectedSlot(client);

        if (currentSlot != lastSlot) {
            lastMainHandStack = itemAdapter.copy(currentMainStack);
            lastOffHandStack = itemAdapter.copy(currentOffStack);
            lastSlot = currentSlot;
            return;
        }

        if (PiggyActionQueue.getInstance().hasActions("piggy-inventory-refill") || 
            PiggyActionQueue.getInstance().isSuppressAutoRefill()) {
            lastMainHandStack = itemAdapter.copy(currentMainStack);
            lastOffHandStack = itemAdapter.copy(currentOffStack);
            return;
        }

        PiggyInventoryConfig config = PiggyInventoryConfig.getInstance();
        int cps = config.getTickDelay();
        long minDelay = cps > 0 ? 1000L / cps : 0;
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastActionTime < minDelay) return;

        boolean unlimited = (cps <= 0);
        boolean actionTaken = false;

        if (shouldTriggerRefill(lastMainHandStack, currentMainStack, itemAdapter, config)) {
            if (this.attemptRefill(client, lastMainHandStack, currentSlot, unlimited)) {
                lastMainHandStack = itemAdapter.copy(lastMainHandStack);
                actionTaken = true;
                lastActionTime = currentTime;
            }
        }

        if (!actionTaken && shouldTriggerRefill(lastOffHandStack, currentOffStack, itemAdapter, config)) {
            if (this.attemptRefill(client, lastOffHandStack, 45, unlimited)) {
                lastOffHandStack = itemAdapter.copy(lastOffHandStack);
                actionTaken = true;
                lastActionTime = currentTime;
            }
        }

        if (!actionTaken) {
            lastMainHandStack = itemAdapter.copy(currentMainStack);
            lastOffHandStack = itemAdapter.copy(currentOffStack);
        }

        lastSlot = currentSlot;
    }

    private boolean attemptRefill(Object client, Object previousStack, int targetSlotId, boolean unlimited) {
        WorldStateAdapter worldState = PiggyServiceRegistry.getWorldStateAdapter();
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        Object inventory = worldState.getPlayerInventory(client);
        int bestSlot = -1;

        // 1. Exact Match
        for (int i = 9; i < 36; i++) {
            Object candidate = adapter.getStackInSlot(inventory, i);
            if (adapter.getCount(candidate) > 0 && adapter.areItemsEqual(previousStack, candidate)) {
                bestSlot = i;
                break;
            }
        }

        if (bestSlot != -1) {
            if (targetSlotId < 9) {
                var slotAction = new ClickWindowSlotAction(0, bestSlot, targetSlotId, ClickType.SWAP, "piggy-inventory-refill");
                if (unlimited) slotAction.setIgnoreGlobalCps(true);
                PiggyActionQueue.getInstance().enqueue(slotAction);
            } else if (targetSlotId == 45) {
                var slotAction1 = new ClickWindowSlotAction(0, bestSlot, 0, ClickType.PICKUP, "piggy-inventory-refill");
                var slotAction2 = new ClickWindowSlotAction(0, 45, 0, ClickType.PICKUP, "piggy-inventory-refill");
                if (unlimited) {
                    slotAction1.setIgnoreGlobalCps(true);
                    slotAction2.setIgnoreGlobalCps(true);
                }
                PiggyActionQueue.getInstance().enqueue(slotAction1);
                PiggyActionQueue.getInstance().enqueue(slotAction2);
            }
            return true;
        }
        return false;
    }

    private boolean shouldTriggerRefill(Object oldStack, Object newStack, ItemDataAdapter adapter, PiggyInventoryConfig config) {
        if (adapter.getCount(oldStack) == 0) return false;
        if (!config.isAutoRefill()) return false;

        if (adapter.getCount(newStack) == 0) {
            RefillCategory cat = RefillCategory.fromStack(oldStack);
            if (cat == RefillCategory.FOOD && !config.isAutoRefillFood()) return false;
            if (cat == RefillCategory.WEAPON && !config.isAutoRefillWeapon()) return false;
            if (cat == RefillCategory.TOOL && !config.isAutoRefillTool()) return false;
            return true;
        }

        String oldId = adapter.getItemId(oldStack);
        String newId = adapter.getItemId(newStack);
        if (oldId.equals(newId)) return false;

        if (!config.isAutoRefillContainers()) return false;

        if (isPotionLike(oldId) && adapter.isBaseContainer(newStack, "bottle")) return true;
        if (isStewLike(oldId) && adapter.isBaseContainer(newStack, "bowl")) return true;
        if (isBucketLike(oldId) && adapter.isBaseContainer(newStack, "bucket")) return true;

        return false;
    }

    private boolean isPotionLike(String id) {
        return id.contains("potion") || id.contains("honey_bottle") || id.contains("dragon_breath");
    }

    private boolean isStewLike(String id) {
        return id.contains("stew") || id.contains("soup");
    }

    private boolean isBucketLike(String id) {
        return id.contains("bucket") && !id.equals("minecraft:bucket");
    }
}
