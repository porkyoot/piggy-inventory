package is.pig.minecraft.inventory.handler;

import is.pig.minecraft.inventory.sorting.Comparators;
import is.pig.minecraft.inventory.sorting.StackMerger;
import is.pig.minecraft.inventory.sorting.layout.ISortingLayout;
import is.pig.minecraft.inventory.sorting.layout.RowLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

public class SortHandler {
    private static final SortHandler INSTANCE = new SortHandler();

    private SortHandler() {
    }

    private boolean awaitingContainer = false;
    private long requestTime = 0;
    private net.minecraft.client.gui.screens.Screen hiddenScreen = null;
    
    private enum State { IDLE, WAITING_FOR_ITEMS, SORTING }
    private State state = State.IDLE;
    private int waitTicks = 0;

    public void onTick(Minecraft client) {
        if (state == State.WAITING_FOR_ITEMS && this.hiddenScreen instanceof AbstractContainerScreen<?> s) {
            waitTicks++;
            boolean hasItems = false;
            
            for (Slot slot : s.getMenu().slots) {
                if (slot.container != client.player.getInventory() && !slot.getItem().isEmpty()) {
                    hasItems = true;
                    break;
                }
            }

            if (hasItems || waitTicks > 20) {
                state = State.SORTING;
                handleSort(client, null, s);
            }
        }
    }

    public static SortHandler getInstance() {
        return INSTANCE;
    }

    public net.minecraft.client.gui.screens.Screen getHiddenScreen() {
        return hiddenScreen;
    }

    public void triggerRemoteSort(Minecraft client) {
        if (client.player == null || client.level == null) return;

        net.minecraft.world.phys.HitResult hit = client.hitResult;
        if (hit == null || (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getType() != net.minecraft.world.phys.HitResult.Type.ENTITY)) return;

        boolean isContainer = false;
        
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            net.minecraft.world.phys.BlockHitResult blockHit = (net.minecraft.world.phys.BlockHitResult) hit;
            net.minecraft.world.level.block.entity.BlockEntity blockEntity = client.level.getBlockEntity(blockHit.getBlockPos());

            BlockState state = client.level.getBlockState(blockHit.getBlockPos());
            net.minecraft.world.MenuProvider menuProvider = state.getMenuProvider(client.level, blockHit.getBlockPos());

            isContainer = menuProvider != null;
            if (!isContainer && blockEntity != null) {
                if (blockEntity instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity) {
                    isContainer = true;
                } else {
                    String className = blockEntity.getClass().getName().toLowerCase();
                    if (className.contains("sophisticatedstorage") || className.contains("sophisticatedbackpacks")) {
                        isContainer = true;
                    }
                }
            }
        } else if (hit.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hit;
            net.minecraft.world.entity.Entity entity = entityHit.getEntity();
            if (entity instanceof net.minecraft.world.MenuProvider || entity instanceof net.minecraft.world.entity.vehicle.ContainerEntity) {
                isContainer = true;
            }
        }

        if (!isContainer) return;

        if (awaitingContainer && System.currentTimeMillis() - requestTime < 1000) return;

        is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager.getInstance().startSession("Sort");
        is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager.getInstance().getCurrentSession().ifPresent(s -> {
            s.info("Remote sort triggered");
            s.info("Initial Context: " + is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatPlayer(client.player));
            s.info("Player State: " + is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatFullPlayerInventory(client.player));
        });

        awaitingContainer = true;
        requestTime = System.currentTimeMillis();

        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            net.minecraft.world.phys.BlockHitResult bHit = (net.minecraft.world.phys.BlockHitResult) hit;
            is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager.getInstance().getCurrentSession().ifPresent(s -> 
                s.info("Targeted Block: " + is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatBlock(bHit.getBlockPos(), client.level.getBlockState(bHit.getBlockPos()), client.level)));
            
            client.player.connection.send(new net.minecraft.network.protocol.game.ServerboundUseItemOnPacket(
                    net.minecraft.world.InteractionHand.MAIN_HAND, bHit, 0));
        } else if (hit.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hit;
            is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager.getInstance().getCurrentSession().ifPresent(s -> 
                s.info("Targeted Entity: " + is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatEntity(entityHit.getEntity())));
            
            client.player.connection.send(net.minecraft.network.protocol.game.ServerboundInteractPacket.createInteractionPacket(
                    entityHit.getEntity(),
                    true, // Force sneak to prevent mounting vehicles like boats
                    net.minecraft.world.InteractionHand.MAIN_HAND
            ));
        }
        client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    public boolean interceptSetScreen(net.minecraft.client.gui.screens.Screen screen) {
        if (!awaitingContainer) return false;

        if (screen instanceof AbstractContainerScreen) {
            if (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
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
            Minecraft client = Minecraft.getInstance();
            screen.init(client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());

            return true;
        }

        awaitingContainer = false;
        return false;
    }

    public void cleanup() {
        if (this.hiddenScreen instanceof AbstractContainerScreen<?> containerScreen) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && client.player.connection != null) {
                client.player.connection.send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(containerScreen.getMenu().containerId));
            }
            containerScreen.removed();
        }
        this.hiddenScreen = null;
        this.awaitingContainer = false;
        this.state = State.IDLE;
    }

    public void handleSort(Minecraft client, Slot hoveredSlot) {
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        handleSort(client, hoveredSlot, screen);
    }

    public void handleSort(Minecraft client, Slot hoveredSlot, AbstractContainerScreen<?> screen) {
        if (is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager.getInstance().getCurrentSession().isEmpty()) {
             is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager.getInstance().startSession("Sort");
        }
        is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager.getInstance().getCurrentSession().ifPresent(s -> {
             s.info("Local sort triggered");
             s.info("Initial Context: " + is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatPlayer(client.player));
             s.info("Player State: " + is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatFullPlayerInventory(client.player));
        });

        // Determine the target container. Default to player inventory if nothing is hovered.
        net.minecraft.world.Container targetContainer = client.player.getInventory();
        if (hoveredSlot != null && hoveredSlot.container != null) {
            targetContainer = hoveredSlot.container;
        } else if (this.hiddenScreen == screen) {
            for (Slot slot : screen.getMenu().slots) {
                if (slot.container != client.player.getInventory()) {
                    targetContainer = slot.container;
                    break;
                }
            }
        }
        boolean isPlayerInv = (targetContainer == client.player.getInventory());

        // Extract items from appropriate slots
        List<Slot> slotsToSort = new ArrayList<>();
        List<ItemStack> items = new ArrayList<>();

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == targetContainer) {
                if (isPlayerInv && slot.getContainerSlot() >= 36) {
                    continue; // Skip armor and offhand
                }
                
                if (isPlayerInv && is.pig.minecraft.inventory.locking.SlotLockingManager.getInstance().isLocked(slot)) {
                    continue; // Skip locked player slots!
                }
                
                slotsToSort.add(slot);
                items.add(slot.getItem().copy());

                if (slot.x < minX) minX = slot.x;
                if (slot.x > maxX) maxX = slot.x;
                if (slot.y < minY) minY = slot.y;
                if (slot.y > maxY) maxY = slot.y;
            }
        }

        // Include carried item in the sortable set
        ItemStack carried = client.player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            items.add(carried.copy());
            // Implicitly, we need a "slot" for this item. 
            // We use null to represent the virtual cursor slot.
        }

        final List<Slot> finalSlotsToSort = slotsToSort;
        is.pig.minecraft.lib.util.telemetry.MetaActionSessionManager.getInstance().getCurrentSession().ifPresent(s -> {
            s.info("Initial state: " + is.pig.minecraft.lib.util.telemetry.formatter.PiggyTelemetryFormatter.formatInventory(finalSlotsToSort));
        });

        boolean isEmpty = true;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                isEmpty = false;
                break;
            }
        }

        if (isEmpty) {
            if (this.hiddenScreen != null) {
                cleanup();
            }
            return;
        }

        // Calculate approximate Grid Dimensions
        // A standard slot is 18x18 pixels usually.
        int cols = Math.max(1, (maxX - minX) / 18 + 1);
        int rows = Math.max(1, (maxY - minY) / 18 + 1);
        
        // Safety bounds
        if (slotsToSort.size() < cols * rows && isPlayerInv) {
             // Hardcode player inventory as it has a gap between storage and hotbar
             cols = 9;
             rows = 4;
        }

        // 1. Merge
        StackMerger.merge(items, slotsToSort);

        // 2. Sort using the user-configured comparator hierarchy
        is.pig.minecraft.inventory.config.PiggyInventoryConfig cfg = is.pig.minecraft.inventory.config.PiggyInventoryConfig.getInstance();
        List<String> comparatorOrder = cfg.getSortComparatorOrder();
        items.sort(Comparators.buildHierarchy(comparatorOrder));

        // 3. Layout the grid with empty spaces separating groups
        List<java.util.Comparator<ItemStack>> layoutComparators = Comparators.buildComparatorList(comparatorOrder);
        ISortingLayout layout = cfg.getSortLayout() == is.pig.minecraft.inventory.config.PiggyInventoryConfig.SortLayout.COLUMN
                ? new is.pig.minecraft.inventory.sorting.layout.ColumnLayout(layoutComparators)
                : new RowLayout(layoutComparators);
        List<ItemStack> finalPositions = layout.layout(items, slotsToSort);

        // If we have an extra item for the cursor, add it to the final positions
        if (items.size() > slotsToSort.size()) {
            finalPositions.add(items.get(items.size() - 1));
        } else {
            finalPositions.add(ItemStack.EMPTY);
        }
        
        // Add a null slot to slotsToSort to represent the virtual cursor slot index
        slotsToSort.add(null);

        // Verification: Ensure no items were dropped by layout padding running out of bounds
        List<ItemStack> trackingList = new ArrayList<>(items);
        for (ItemStack positioned : finalPositions) {
            if (!positioned.isEmpty()) {
                for (int i = 0; i < trackingList.size(); i++) {
                    if (trackingList.get(i) == positioned) {
                        trackingList.remove(i);
                        break;
                    }
                }
            }
        }
        
        // Recover any dropped items into available empty slots
        for (ItemStack missing : trackingList) {
            boolean placed = false;
            for (int i = 0; i < finalPositions.size(); i++) {
                if (finalPositions.get(i).isEmpty()) {
                    finalPositions.set(i, missing);
                    placed = true;
                    break;
                }
            }
            // Fallback (should be mathematically impossible as total items <= slots)
            if (!placed && !finalPositions.isEmpty()) {
                for (int i = finalPositions.size() - 1; i >= 0; i--) {
                    if (finalPositions.get(i).isEmpty() || i == 0) {
                        finalPositions.set(i, missing);
                        break;
                    }
                }
            }
        }

        // 4. Write back to slots using packets/actions via the Executor
        SortExecutor.getInstance().startSort(slotsToSort, finalPositions);
    }
}

