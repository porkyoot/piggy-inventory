package is.pig.minecraft.inventory.handler;

import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import is.pig.minecraft.inventory.util.InventoryUtils;
import net.minecraft.world.level.block.state.BlockState;

public class QuickLootHandler {
    private static final QuickLootHandler INSTANCE = new QuickLootHandler();
    private boolean awaitingContainer = false;
    private long requestTime = 0;
    private Screen hiddenScreen = null;

    private enum State { IDLE, WAITING_FOR_ITEMS, TRANSFERRING }
    private State state = State.IDLE;
    private int waitTicks = 0;

    // Sneak suppression
    private boolean sneakingSuppressed = false;
    private long suppressSneakUntil = 0;

    // Transfer settings
    // Transfer settings
    private boolean lastTransferWasUp = false; // Move to Player?
    private boolean lastTransferWasAll = false; // Ctrl?

    public static QuickLootHandler getInstance() {
        return INSTANCE;
    }

    public boolean onScroll(Minecraft client, double delta, boolean lootAllPressed) {
        PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();
        if (!config.isFastLoot())
            return false;

        boolean lootMatchingPressed = InventoryUtils.isLootMatchingDown();

        // Check if feature is enabled in config
        boolean doAll = lootAllPressed && config.isFastLootLookingAtAll();
        boolean doMatching = lootMatchingPressed && config.isFastLootLookingAtMatching();

        // If neither valid action is triggered, exit (allow vanilla behavior)
        if (!doAll && !doMatching)
            return false;

        if (client.player == null || client.level == null)
            return false;

        // Check Raycast
        HitResult hit = client.hitResult;
        if (hit == null || (hit.getType() != HitResult.Type.BLOCK && hit.getType() != HitResult.Type.ENTITY))
            return false;

        boolean isContainer = false;

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockEntity blockEntity = client.level.getBlockEntity(blockHit.getBlockPos());

            BlockState state = client.level.getBlockState(blockHit.getBlockPos());
            net.minecraft.world.MenuProvider menuProvider = state.getMenuProvider(client.level, blockHit.getBlockPos());
            
            isContainer = menuProvider != null;

            // Fallback for modded storages that don't implement MenuProvider properly on client side
            // and Ender Chests which hardcode their menu opening
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
        } else if (hit.getType() == HitResult.Type.ENTITY) {
            net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hit;
            net.minecraft.world.entity.Entity entity = entityHit.getEntity();
            
            if (entity instanceof net.minecraft.world.MenuProvider || entity instanceof net.minecraft.world.entity.vehicle.ContainerEntity) {
                isContainer = true;
            }
        }

        if (!isContainer) {
            return false; // Not a container, let vanilla handle it (Sneak, etc)
        }

        // Prevent spamming
        if (awaitingContainer && System.currentTimeMillis() - requestTime < 1000)
            return true;

        // Store intent
        awaitingContainer = true;
        requestTime = System.currentTimeMillis();
        lastTransferWasUp = delta > 0;
        lastTransferWasAll = lootAllPressed; // Prioritize All or use what triggered it?
        // Logic: If both pressed, what happens? 'All' usually overrides 'Matching' in
        // transfer logic.
        if (doAll) {
            lastTransferWasAll = true;
        } else {
            lastTransferWasAll = false;
        }

        // Sneak Bypass logic:
        boolean wasSneaking = client.player.isShiftKeyDown();

        if (wasSneaking) {
            // 1. Send Release Packet
            client.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(
                    client.player,
                    net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
            // 2. Client Side state
            client.player.setShiftKeyDown(false);

            // 3. Mark for delayed restoration
            sneakingSuppressed = true;
            suppressSneakUntil = System.currentTimeMillis() + 60; // 60ms delay (approx 1-3 ticks)
        }

        // 4. Trigger interaction via direct packet to prevent local ghost block placement
        if (hit.getType() == HitResult.Type.BLOCK) {
            client.player.connection.send(new net.minecraft.network.protocol.game.ServerboundUseItemOnPacket(
                    InteractionHand.MAIN_HAND, (BlockHitResult) hit, 0));
        } else if (hit.getType() == HitResult.Type.ENTITY) {
            net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hit;
            client.player.connection.send(net.minecraft.network.protocol.game.ServerboundInteractPacket.createInteractionPacket(
                    entityHit.getEntity(),
                    true, // Force sneak to prevent mounting vehicles like boats
                    InteractionHand.MAIN_HAND
            ));
        }
        client.player.swing(InteractionHand.MAIN_HAND);

        return true;
    }

    /**
     * Called by MixinMinecraft when setScreen is called.
     * Returns true if we should CANCEL the screen opening (hide it).
     */
    public boolean interceptSetScreen(Screen screen) {
        if (!awaitingContainer)
            return false;

        if (screen instanceof AbstractContainerScreen) {
            if (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
                awaitingContainer = false;
                return false; // Don't intercept the local player inventory!
            }

            // Safety Check: If too much time passed, maybe it's a legitimate opening
            if (System.currentTimeMillis() - requestTime > 2000) {
                awaitingContainer = false;
                return false;
            }

            this.hiddenScreen = screen;
            this.state = State.WAITING_FOR_ITEMS;
            this.waitTicks = 0;
            Minecraft client = Minecraft.getInstance();
            screen.init(client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());

            return true; // Suppress display
        }

        // If it's not a container screen (e.g. Sign Edit, Book), let it show?
        // Or if we opened something else, cancel our wait.
        awaitingContainer = false;
        return false;
    }

    private boolean transferInProgress = false;

    public void onTick(Minecraft client) {
        // Handle Delayed Sneak Restoration
        if (sneakingSuppressed) {
            if (System.currentTimeMillis() > suppressSneakUntil) {
                sneakingSuppressed = false;
                if (client.player != null && client.options.keyShift.isDown()) {
                    // Check if they are STILL holding it properly
                    // Restore state
                    client.player.setShiftKeyDown(true);
                    client.player.connection
                            .send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(
                                    client.player,
                                    net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
                }
            }
        }

        // Timeout
        if (awaitingContainer && state != State.TRANSFERRING && System.currentTimeMillis() - requestTime > 2000) {
            cleanup();
        }

        if (hiddenScreen != null) {
            if (client.player == null) {
                cleanup();
                return;
            }

            if (state == State.WAITING_FOR_ITEMS) {
                waitTicks++;
                boolean hasItems = false;
                
                if (hiddenScreen instanceof AbstractContainerScreen<?> s) {
                    net.minecraft.world.Container targetContainer = client.player.getInventory();
                    for (net.minecraft.world.inventory.Slot slot : s.getMenu().slots) {
                        if (slot.container != client.player.getInventory()) {
                            targetContainer = slot.container;
                            break;
                        }
                    }
                    
                    for (net.minecraft.world.inventory.Slot slot : s.getMenu().slots) {
                        if (slot.container == targetContainer && !slot.getItem().isEmpty()) {
                            hasItems = true;
                            break;
                        }
                    }
                }

                if (hasItems || waitTicks > 20) {
                    state = State.TRANSFERRING;
                } else {
                    return; // Wait for items
                }
            }

            if (state == State.TRANSFERRING && !transferInProgress) {
                // Feature Check
                if (!PiggyInventoryConfig.getInstance().isFeatureQuickLootEnabled()) {
                    is.pig.minecraft.lib.ui.AntiCheatFeedbackManager.getInstance().onFeatureBlocked("quick_loot", is.pig.minecraft.lib.ui.BlockReason.SERVER_ENFORCEMENT);
                    client.player.closeContainer();
                    cleanup();
                    return;
                }

                double delta = lastTransferWasUp ? 1.0 : -1.0;
                if (hiddenScreen instanceof AbstractContainerScreen) {
                    java.util.List<Integer> slots = InventoryUtils.getSlotsToTransfer(
                            (AbstractContainerScreen<?>) hiddenScreen, delta, lastTransferWasAll);
                    
                    int cps = PiggyInventoryConfig.getInstance().getTickDelay();
                    java.util.List<is.pig.minecraft.lib.action.IAction> clicks = new java.util.ArrayList<>();
                    
                    for (int slotIndex : slots) {
                        var slotAction = new is.pig.minecraft.lib.action.inventory.ClickWindowSlotAction(
                                ((AbstractContainerScreen<?>) hiddenScreen).getMenu().containerId,
                                slotIndex,
                                0, // button
                                net.minecraft.world.inventory.ClickType.QUICK_MOVE,
                                "piggy-inventory-quickloot",
                                is.pig.minecraft.lib.action.ActionPriority.NORMAL
                        );
                        if (cps <= 0) slotAction.setIgnoreGlobalCps(true);
                        clicks.add(slotAction);
                        is.pig.minecraft.lib.ui.IconQueueOverlay.queueIcon(lastTransferWasUp ? DEPO_ICON : LOOT_ICON, 1000, false);
                    }
                    
                    var bulkAction = new is.pig.minecraft.lib.action.BulkAction(
                            "piggy-inventory-quickloot",
                            "Quick Loot Transfer",
                            clicks
                    );
                    if (cps <= 0) bulkAction.setIgnoreGlobalCps(true);
                    is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().enqueue(bulkAction);
                }
                transferInProgress = true;
            }

            if (transferInProgress) {
                if (!is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().hasActions("piggy-inventory-quickloot")) {
                    client.player.closeContainer();
                    cleanup();
                }
            }
        }
    }

    private void cleanup() {
        if (this.hiddenScreen instanceof AbstractContainerScreen<?> containerScreen) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && client.player.connection != null) {
                client.player.connection.send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(containerScreen.getMenu().containerId));
            }
            containerScreen.removed();
        }
        hiddenScreen = null;
        awaitingContainer = false;
        transferInProgress = false;
        state = State.IDLE;
    }

    private static final net.minecraft.resources.ResourceLocation LOOT_ICON = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("piggy", "textures/gui/icons/quick_loot.png");
    private static final net.minecraft.resources.ResourceLocation DEPO_ICON = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("piggy", "textures/gui/icons/quick_depo.png");
}
