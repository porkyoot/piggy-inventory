package is.pig.minecraft.inventory.handler;

import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.InteractionHand;

public class QuickLootHandler {
    private static final QuickLootHandler INSTANCE = new QuickLootHandler();
    private boolean awaitingContainer = false;
    private long requestTime = 0;
    private Screen hiddenScreen = null;

    // Sneak suppression
    private boolean sneakingSuppressed = false;
    private long suppressSneakUntil = 0;

    // Transfer settings
    private boolean lastTransferWasUp = false; // Move to Player?
    private boolean lastTransferWasAll = false; // Ctrl?

    // Feedback
    private long lastActionTime = 0;

    public static QuickLootHandler getInstance() {
        return INSTANCE;
    }

    public boolean onScroll(Minecraft client, double delta, boolean ctrlHeld) {
        PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();
        if (!config.isFastLoot())
            return false;

        // Granular check
        if (ctrlHeld) {
            if (!config.isFastLootLookingAtAll())
                return false;
        } else {
            if (!config.isFastLootLookingAtMatching())
                return false;
        }

        if (client.player == null || client.level == null)
            return false;

        // Check Raycast
        HitResult hit = client.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK)
            return false;

        // Prevent spamming
        if (awaitingContainer && System.currentTimeMillis() - requestTime < 1000)
            return true;

        // Store intent
        awaitingContainer = true;
        requestTime = System.currentTimeMillis();
        lastTransferWasUp = delta > 0;
        lastTransferWasAll = ctrlHeld;

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

        // 4. Trigger interaction
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, (BlockHitResult) hit);

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
            // Safety Check: If too much time passed, maybe it's a legitimate opening
            if (System.currentTimeMillis() - requestTime > 2000) {
                awaitingContainer = false;
                return false;
            }

            // Capture and Hide
            this.hiddenScreen = screen;
            // Initialize the screen so it has a menu etc?
            // Screen.init(client, width, height) needs to be called normally.
            // setScreen usually calls init.
            // If we cancel setScreen, init is NOT called.
            // We must manually init it to ensure container mappings are set up.
            Minecraft client = Minecraft.getInstance();
            screen.init(client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());

            return true; // Suppress display
        }

        // If it's not a container screen (e.g. Sign Edit, Book), let it show?
        // Or if we opened something else, cancel our wait.
        awaitingContainer = false;
        return false;
    }

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
        if (awaitingContainer && System.currentTimeMillis() - requestTime > 2000) {
            awaitingContainer = false;
            hiddenScreen = null;
        }

        if (hiddenScreen != null) {
            if (client.player == null) {
                hiddenScreen = null;
                awaitingContainer = false;
                return;
            }

            // Perform Transfer logic
            // We can reuse the logic from MixinMouseHandler technically, but method is
            // private.
            // Copy logic here or make public?
            // Making `piggy_handleScrollTransfer` public is cleaner.
            // Assume we made it public (I will modify MixinMouseHandler to be public).

            // Invoke transfer
            // Note: handleScroll used scrollDelta +/- 1.
            double delta = lastTransferWasUp ? 1.0 : -1.0;

            // We need to cast because hiddenScreen is Screen
            if (hiddenScreen instanceof AbstractContainerScreen) {
                boolean result = is.pig.minecraft.inventory.util.InventoryUtils.handleScrollTransfer(
                        (AbstractContainerScreen<?>) hiddenScreen, delta,
                        lastTransferWasAll);

                if (result) {
                    lastActionTime = System.currentTimeMillis();
                }
            }

            // Close screen
            client.player.closeContainer();
            // Also set client.screen to null if it was set (but we suppressed it, so it
            // should be null)

            // Cleanup
            hiddenScreen = null;
            awaitingContainer = false;
        }
    }

    public void renderOverlay(GuiGraphics context) {
        if (System.currentTimeMillis() - lastActionTime > 1000)
            return;

        Minecraft client = Minecraft.getInstance();
        int cx = client.getWindow().getGuiScaledWidth() / 2;
        int cy = client.getWindow().getGuiScaledHeight() / 2;

        // Fade out
        float age = (System.currentTimeMillis() - lastActionTime) / 1000f;
        int alpha = (int) ((1.0f - age) * 255);
        if (alpha < 0)
            alpha = 0;
        if (alpha < 0)
            alpha = 0;
        // int color = (alpha << 24) | 0xFFFFFF; // Unused

        // Render Icon below crosshair
        // ResourceLocation icon = lastTransferWasUp ? ICON_DEPO : ICON_LOOT; // Depo
        // (Up) vs Loot (Down)

        // Draw (Icon rendering is tricky with standard blit if texture not 256x256,
        // simplified rect?)
        // Just draw a colored box for now or item?
        // Using item icon is better.
        // Chest for Loot, Hopper for Depo?

        net.minecraft.world.item.Item itemIcon = lastTransferWasUp ? net.minecraft.world.item.Items.HOPPER
                : net.minecraft.world.item.Items.CHEST_MINECART;

        context.pose().pushPose();
        context.pose().translate(cx - 8, cy + 10, 0); // Below crosshair
        // context.blit(...) requires binding texture.
        // renderItem is easier.

        // Render item does not support alpha easily.
        // We can render item then render a semi-transparent box over it to simulate
        // fade?
        // Or just disappear.

        context.renderItem(new net.minecraft.world.item.ItemStack(itemIcon), 0, 0);

        // Fade overlay
        // context.fill(0, 0, 16, 16, (255 - alpha) << 24); // Darken as it effectively
        // fades IN darkness? No.
        // This is hard.
        // Standard icons don't fade easily.
        // Just show it for 0.5s is fine.

        context.pose().popPose();
    }
}
