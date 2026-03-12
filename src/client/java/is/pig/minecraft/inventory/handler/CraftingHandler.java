package is.pig.minecraft.inventory.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.StonecutterMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class CraftingHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("piggy-inventory-crafting");
    private static final CraftingHandler INSTANCE = new CraftingHandler();
    
    private boolean isActive = false;
    private Slot currentResultSlot = null;
    private ItemStack expectedResult = ItemStack.EMPTY;
    private Map<Integer, ItemStack> lastRecipe = new HashMap<>();
    private int lastStonecutterSelection = -1;
    private long lastActionTime = 0;
    private long holdStartTime = 0;
    private int lastContainerId = -1;
    private boolean wasShiftDown = false;

    public static CraftingHandler getInstance() {
        return INSTANCE;
    }

    public void onCraftingClick(Slot slot, Player player) {
        if (isResultSlot(slot)) {
            LOGGER.debug("Continuous Crafting START on slot {}", slot.index);
            this.isActive = true;
            this.currentResultSlot = slot;
            this.expectedResult = slot.getItem().copy();
            this.holdStartTime = System.currentTimeMillis();
            this.lastActionTime = this.holdStartTime;
            this.lastContainerId = player.containerMenu.containerId;
            this.snapshotRecipe(player);
            
            if (player.containerMenu instanceof StonecutterMenu menu) {
                try {
                    this.lastStonecutterSelection = menu.getSelectedRecipeIndex();
                    LOGGER.debug("Stonecutter recipe: {}", lastStonecutterSelection);
                } catch (Throwable t) {
                    LOGGER.error("Failed to get stonecutter recipe index", t);
                    this.lastStonecutterSelection = -1;
                }
            } else {
                this.lastStonecutterSelection = -1;
            }

            if (net.minecraft.client.gui.screens.Screen.hasShiftDown() && !(player.containerMenu instanceof StonecutterMenu)) {
                this.balanceRecipe(player);
            }
        }
    }

    private boolean isResultSlot(Slot slot) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;

        // 1. Stonecutter specific handling
        if (player.containerMenu instanceof StonecutterMenu) {
            return slot.index == 1;
        }
        
        // 2. Generic Result Slot classes
        if (slot instanceof ResultSlot) return true;
        
        // 3. Fallback for container class names
        try {
            if (slot.container instanceof net.minecraft.world.inventory.ResultContainer) return true;
            String containerName = slot.container.getClass().getName();
            if (containerName.contains("ResultContainer") || containerName.contains("CraftingResult")) {
                return true;
            }
        } catch (Exception e) {}

        return false;
    }

    public void onCraftingRelease() {
        if (isActive) LOGGER.debug("Continuous Crafting STOP");
        this.isActive = false;
        this.currentResultSlot = null;
        // lastRecipe is NOT cleared here so shift-refill can work after craft
        // lastStonecutterSelection is NOT cleared here so it can re-select after refill
    }

    private void snapshotRecipe(Player player) {
        if (player.containerMenu == null) return;
        Map<Integer, ItemStack> newRecipe = new HashMap<>();
        for (Slot s : player.containerMenu.slots) {
            if (!isResultSlot(s) && s.container != player.getInventory()) {
                if (s.hasItem()) {
                    newRecipe.put(s.index, s.getItem().copy());
                }
            }
        }
        if (!newRecipe.isEmpty()) {
            this.lastRecipe = newRecipe;
            for (Map.Entry<Integer, ItemStack> entry : lastRecipe.entrySet()) {
                LOGGER.debug("Snapshot: slot {} -> {}", entry.getKey(), entry.getValue());
            }
        }
    }

    public void onTick(Minecraft client) {
        if (client.player == null || client.gameMode == null) return;

        // Handle container change/closure
        if (client.player.containerMenu.containerId != lastContainerId) {
            this.isActive = false;
            this.currentResultSlot = null;
            this.lastRecipe.clear();
            this.lastStonecutterSelection = -1;
            this.wasShiftDown = false;
            this.lastContainerId = client.player.containerMenu.containerId;
            return;
        }

        boolean isShiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        if (isShiftDown && !wasShiftDown) {
            wasShiftDown = true;
            if (!(client.player.containerMenu instanceof StonecutterMenu)) {
                // Ensure it's a crafting-like container
                boolean isCraftingContainer = false;
                for (Slot s : client.player.containerMenu.slots) {
                    if (isResultSlot(s)) {
                        isCraftingContainer = true;
                        break;
                    }
                }
                if (isCraftingContainer) {
                    this.balanceRecipe(client.player);
                }
            }
        } else if (!isShiftDown) {
            wasShiftDown = false;
        }

        // Shift-Refill Logic: If shift is held, grid needs items, and we have a snapshot
        if (isShiftDown && !isActive && !lastRecipe.isEmpty()) {
            boolean needsRefill = false;
            for (int idx : lastRecipe.keySet()) {
                if (!client.player.containerMenu.getSlot(idx).hasItem()) {
                    needsRefill = true;
                    break;
                }
            }
            if (needsRefill) {
                // Obey CPS for inactive refill too
                is.pig.minecraft.inventory.config.PiggyInventoryConfig config = is.pig.minecraft.inventory.config.PiggyInventoryConfig.getInstance();
                int cps = config.getTickDelay();
                long now = System.currentTimeMillis();
                if (cps > 0) {
                    long delayMs = 1000L / cps;
                    if (now - lastActionTime < delayMs) return;
                }

                int result = this.tryRefill(client.player, client);
                if (result == 1) {
                    lastActionTime = now;
                    // If CPS=0, we could loop here, but usually one-shot refill per tick is enough for inactive
                }
                return;
            } else {
                // No Refill needed - check if Stonecutter lost its selection
                if (client.player.containerMenu instanceof StonecutterMenu menu && lastStonecutterSelection != -1) {
                    try {
                        if (menu.getSelectedRecipeIndex() == -1) {
                            LOGGER.debug("Inactive re-selecting recipe: {}", lastStonecutterSelection);
                            client.gameMode.handleInventoryButtonClick(menu.containerId, lastStonecutterSelection);
                            lastActionTime = System.currentTimeMillis();
                            return;
                        }
                    } catch (Throwable t) {
                        LOGGER.error("Failed to re-select stonecutter recipe (inactive)", t);
                    }
                }
            }
        }

        if (!isActive) return;

        long now = System.currentTimeMillis();
        is.pig.minecraft.inventory.config.PiggyInventoryConfig config = is.pig.minecraft.inventory.config.PiggyInventoryConfig.getInstance();
        boolean isStonecutter = client.player.containerMenu instanceof StonecutterMenu;
        
        // CPS Check (Obeying the setting)
        int cps = config.getTickDelay();
        if (cps > 0) {
            long delayMs = 1000L / cps;
            if (now - lastActionTime < delayMs) return;
        }

        // Hold threshold (300ms) to distinguish click from hold
        if (now - holdStartTime < 300) return;

        // Reset Stonecutter selection tracking if it was lost
        if (isStonecutter && lastStonecutterSelection == -1) {
            try {
                int selected = ((StonecutterMenu)client.player.containerMenu).getSelectedRecipeIndex();
                if (selected != -1) lastStonecutterSelection = selected;
            } catch (Exception e) {
                LOGGER.error("Failed to recover stonecutter recipe index", e);
            }
        }

        // Unlimited logic: if CPS <= 0, loop up to 64 times in one tick
        int limit = (cps <= 0) ? 64 : 1;
        for (int i = 0; i < limit; i++) {
            if (!performOperation(client)) break;
        }
    }

    private boolean performOperation(Minecraft client) {
        if (client.player == null || client.gameMode == null || !isActive) return false;
        is.pig.minecraft.inventory.config.PiggyInventoryConfig config = is.pig.minecraft.inventory.config.PiggyInventoryConfig.getInstance();
        boolean isStonecutter = client.player.containerMenu instanceof StonecutterMenu;
        boolean enabled = isStonecutter ? config.isContinuousOperations() : config.isContinuousCrafting();

        if (!enabled || !net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            this.onCraftingRelease();
            return false;
        }

        if (!currentResultSlot.hasItem()) {
            int refillResult = this.tryRefill(client.player, client);
            if (refillResult == 0) {
                if (isStonecutter && lastStonecutterSelection != -1) {
                    StonecutterMenu menu = (StonecutterMenu) client.player.containerMenu;
                    try {
                        if (menu.getSelectedRecipeIndex() == -1) {
                            LOGGER.debug("Re-selecting recipe: {}", lastStonecutterSelection);
                            client.gameMode.handleInventoryButtonClick(menu.containerId, lastStonecutterSelection);
                            lastActionTime = System.currentTimeMillis();
                            return true;
                        }
                    } catch (Throwable t) {
                        LOGGER.error("Failed to re-select stonecutter recipe", t);
                    }
                }
                return false;
            } else if (refillResult == 1) {
                LOGGER.debug("Refilled grid.");
                lastActionTime = System.currentTimeMillis();
                return true;
            } else {
                LOGGER.debug("Out of items, stopping.");
                this.onCraftingRelease();
                return false;
            }
        }

        if (currentResultSlot.hasItem()) {
            if (!isStonecutter && !expectedResult.isEmpty()) {
                if (!ItemStack.isSameItemSameComponents(currentResultSlot.getItem(), expectedResult)) {
                    LOGGER.warn("Bad recipe detected! Slot: {}, Expected: {}", currentResultSlot.getItem(), expectedResult);
                    this.onCraftingRelease();
                    return false;
                }
            }

            // Keep the recipe updated but don't clear if it temporarily flickers empty
            this.snapshotRecipe(client.player);
            this.expectedResult = currentResultSlot.getItem().copy();

            LOGGER.debug("Clicking result slot {}", currentResultSlot.index);
            client.gameMode.handleInventoryMouseClick(
                    client.player.containerMenu.containerId,
                    currentResultSlot.index,
                    0,
                    net.minecraft.world.inventory.ClickType.QUICK_MOVE,
                    client.player);
            
            lastActionTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private void balanceRecipe(Player player) {
        if (player.containerMenu == null) return;
        if (!player.containerMenu.getCarried().isEmpty()) return;
        Minecraft client = Minecraft.getInstance();

        // Snapshot current to know exactly where to balance
        this.snapshotRecipe(player);
        if (lastRecipe.isEmpty()) return;

        Map<ItemKey, List<Integer>> ingredientSlots = new HashMap<>();
        for (Map.Entry<Integer, ItemStack> entry : lastRecipe.entrySet()) {
            ingredientSlots.computeIfAbsent(new ItemKey(entry.getValue()), k -> new ArrayList<>()).add(entry.getKey());
        }

        for (Map.Entry<ItemKey, List<Integer>> entry : ingredientSlots.entrySet()) {
            List<Integer> slots = entry.getValue();
            if (slots.size() <= 1) continue;

            int[] currents = new int[slots.size()];
            int totalCount = 0;
            for (int i = 0; i < slots.size(); i++) {
                currents[i] = player.containerMenu.getSlot(slots.get(i)).getItem().getCount();
                totalCount += currents[i];
            }

            int countPerSlot = totalCount / slots.size();
            int remainder = totalCount % slots.size();
            int[] targets = new int[slots.size()];
            for (int i = 0; i < slots.size(); i++) {
                targets[i] = countPerSlot + (i < remainder ? 1 : 0);
            }

            for (int i = 0; i < slots.size(); i++) {
                if (currents[i] > targets[i]) {
                    // Pick up entire stack
                    client.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, slots.get(i), 0,
                            net.minecraft.world.inventory.ClickType.PICKUP, player);
                    
                    // Put back target amount
                    for(int k=0; k<targets[i]; k++) {
                        client.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, slots.get(i), 1,
                                net.minecraft.world.inventory.ClickType.PICKUP, player);
                    }
                    
                    int excess = currents[i] - targets[i];
                    
                    for (int j = 0; j < slots.size(); j++) {
                        if (excess == 0) break;
                        if (currents[j] < targets[j]) {
                            int needed = targets[j] - currents[j];
                            int toDrop = Math.min(excess, needed);
                            for (int k = 0; k < toDrop; k++) {
                                client.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, slots.get(j), 1,
                                        net.minecraft.world.inventory.ClickType.PICKUP, player);
                            }
                            currents[j] += toDrop;
                            excess -= toDrop;
                        }
                    }
                }
            }
        }
    }

    private static class ItemKey {
        private final ItemStack stack;
        public ItemKey(ItemStack stack) { this.stack = stack; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return ItemStack.isSameItemSameComponents(stack, ((ItemKey) o).stack);
        }
        @Override
        public int hashCode() { return stack.getItem().hashCode(); }
    }

    private int tryRefill(Player player, Minecraft client) {
        if (lastRecipe.isEmpty()) return -1;
        
        Map<IngredientKey, List<Integer>> ingredientsToSlots = new HashMap<>();
        boolean anyMissingInGrid = false;

        for (Map.Entry<Integer, ItemStack> entry : lastRecipe.entrySet()) {
            int slotIndex = entry.getKey();
            ItemStack requiredStack = entry.getValue();
            Slot currentSlot = player.containerMenu.getSlot(slotIndex);

            boolean needsItem = !currentSlot.hasItem() ||
                    (ItemStack.isSameItemSameComponents(currentSlot.getItem(), requiredStack)
                            && currentSlot.getItem().getCount() < currentSlot.getItem().getMaxStackSize());

            if (needsItem) {
                IngredientKey key = new IngredientKey(requiredStack);
                ingredientsToSlots.computeIfAbsent(key, k -> new ArrayList<>()).add(slotIndex);
                anyMissingInGrid = true;
            }
        }

        if (!anyMissingInGrid) return 0;

        boolean refilledAny = false;
        for (Map.Entry<IngredientKey, List<Integer>> entry : ingredientsToSlots.entrySet()) {
            IngredientKey key = entry.getKey();
            List<Integer> targetSlots = entry.getValue();
            List<Integer> sourceSlots = findItemSlotsInInventory(player, key.stack);

            if (sourceSlots.isEmpty()) {
                LOGGER.debug("Missing ingredient: {}", key.stack.getItem());
                return -1;
            }

            for (int sourceSlot : sourceSlots) {
                client.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, sourceSlot, 0,
                        net.minecraft.world.inventory.ClickType.PICKUP, player);

                if (targetSlots.size() == 1) {
                    client.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, targetSlots.get(0), 0,
                            net.minecraft.world.inventory.ClickType.PICKUP, player);
                } else {
                    client.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, -999, 0,
                            net.minecraft.world.inventory.ClickType.QUICK_CRAFT, player);
                    for (int targetSlot : targetSlots) {
                        client.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, targetSlot, 1,
                                net.minecraft.world.inventory.ClickType.QUICK_CRAFT, player);
                    }
                    client.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, -999, 2,
                            net.minecraft.world.inventory.ClickType.QUICK_CRAFT, player);
                }

                if (!client.player.containerMenu.getCarried().isEmpty()) {
                    client.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, sourceSlot, 0,
                            net.minecraft.world.inventory.ClickType.PICKUP, player);
                }
                refilledAny = true;
                break;
            }
        }

        return refilledAny ? 1 : -1;
    }

    private List<Integer> findItemSlotsInInventory(Player player, ItemStack target) {
        List<Integer> slots = new ArrayList<>();
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container == player.getInventory()) {
                if (ItemStack.isSameItemSameComponents(target, slot.getItem())) {
                    slots.add(slot.index);
                }
            }
        }
        return slots;
    }

    private static class IngredientKey {
        private final ItemStack stack;

        public IngredientKey(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IngredientKey that = (IngredientKey) o;
            return ItemStack.isSameItemSameComponents(stack, that.stack);
        }

        @Override
        public int hashCode() {
            return stack.getItem().hashCode();
        }
    }
}
