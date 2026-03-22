package is.pig.minecraft.inventory.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;

public class TradeHandler {
    private static final TradeHandler INSTANCE = new TradeHandler();
    private boolean isActive = false;
    private long lastActionTime = 0;
    private long holdStartTime = 0;
    private Slot currentResultSlot = null;
    private int lastContainerId = -1;
    private ItemStack[] snapshot = new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY};

    public static TradeHandler getInstance() {
        return INSTANCE;
    }

    public void onTradeClick(Slot slot, Player player) {
        if (slot instanceof MerchantResultSlot) {
            this.isActive = true;
            this.holdStartTime = System.currentTimeMillis();
            this.lastActionTime = this.holdStartTime;

            this.currentResultSlot = slot;
            this.lastContainerId = player.containerMenu.containerId;
            
            this.snapshot[0] = player.containerMenu.slots.get(0).getItem().copy();
            this.snapshot[1] = player.containerMenu.slots.get(1).getItem().copy();
        }
    }

    public void onTradeRelease() {
        this.isActive = false;
        this.currentResultSlot = null;
    }

    public void onTick(Minecraft client) {
        if (client.player == null || client.gameMode == null)
            return;

        if (client.player.containerMenu.containerId != lastContainerId) {
            this.isActive = false;
            this.currentResultSlot = null;
            this.lastContainerId = client.player.containerMenu.containerId;
            this.snapshot[0] = ItemStack.EMPTY;
            this.snapshot[1] = ItemStack.EMPTY;
            return;
        }

        boolean isShiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();

        if (!isActive || currentResultSlot == null)
            return;

        if (!isShiftDown) {
            this.onTradeRelease();
            return;
        }

        is.pig.minecraft.inventory.config.PiggyInventoryConfig config = is.pig.minecraft.inventory.config.PiggyInventoryConfig.getInstance();
        if (!config.isFastTrade()) return;

        long now = System.currentTimeMillis();
        int cps = config.getTickDelay();
        if (cps > 0) {
            long delayMs = 1000L / cps;
            if (now - lastActionTime < delayMs)
                return;
        }

        if (now - holdStartTime < 300) return;

        if (is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().hasActions("piggy-inventory-trade")) return;

        // Queue operations; the central queue will enforce absolute limits
        int limit = (cps <= 0) ? 64 : 1;
        boolean unlimited = (cps <= 0);
        
        for (int i = 0; i < limit; i++) {
            if (!performOperation(client, unlimited)) break;
        }
    }

    private boolean performOperation(Minecraft client, boolean unlimited) {
        if (!isActive || currentResultSlot == null || client.player == null || client.gameMode == null)
            return false;

        if (!currentResultSlot.hasItem()) {
            boolean isShiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();
            if (isShiftDown && performSnapshotRefill(client, unlimited)) {
                lastActionTime = System.currentTimeMillis();
                return true;
            }
            this.onTradeRelease();
            return false;
        }

        var slotAction = new is.pig.minecraft.lib.action.inventory.ClickWindowSlotAction(
                        client.player.containerMenu.containerId,
                        currentResultSlot.index,
                        0,
                        net.minecraft.world.inventory.ClickType.QUICK_MOVE,
                        "piggy-inventory-trade",
                        is.pig.minecraft.lib.action.ActionPriority.NORMAL
        );
        if (unlimited) slotAction.setIgnoreGlobalCps(true);
        is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().enqueue(slotAction);
        lastActionTime = System.currentTimeMillis();
        return true;
    }

    private boolean performSnapshotRefill(Minecraft client, boolean unlimited) {
        if (snapshot[0].isEmpty() && snapshot[1].isEmpty()) return false;
        if (client.player == null || client.player.containerMenu == null) return false;
        
        boolean foundAny = false;
        int containerId = client.player.containerMenu.containerId;
        
        // Loop over player inventory (usually starts at index 3 in MerchantMenu)
        for (int i = 3; i < client.player.containerMenu.slots.size(); i++) {
            Slot slot = client.player.containerMenu.slots.get(i);
            if (!slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            
            boolean match0 = !snapshot[0].isEmpty() && ItemStack.isSameItemSameComponents(stack, snapshot[0]);
            boolean match1 = !snapshot[1].isEmpty() && ItemStack.isSameItemSameComponents(stack, snapshot[1]);
            
            if (match0 || match1) {
                var slotAction = new is.pig.minecraft.lib.action.inventory.ClickWindowSlotAction(
                                containerId,
                                i,
                                0,
                                net.minecraft.world.inventory.ClickType.QUICK_MOVE,
                                "piggy-inventory-trade",
                                is.pig.minecraft.lib.action.ActionPriority.NORMAL
                );
                if (unlimited) slotAction.setIgnoreGlobalCps(true);
                is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().enqueue(slotAction);
                foundAny = true;
                break; // Just one click per tick to refill safely
            }
        }
        return foundAny;
    }
}
