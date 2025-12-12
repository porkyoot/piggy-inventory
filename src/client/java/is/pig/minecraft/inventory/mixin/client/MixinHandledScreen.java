
package is.pig.minecraft.inventory.mixin.client;

import is.pig.minecraft.inventory.PiggyInventoryClient;
import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import is.pig.minecraft.inventory.locking.SlotLockingManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.inventory.Slot;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(AbstractContainerScreen.class)
public abstract class MixinHandledScreen implements is.pig.minecraft.inventory.duck.IHandledScreen {

    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;

    @Inject(method = "render", at = @At("TAIL"))
    private void renderLocks(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean altHeld = ((PiggyInventoryConfig) PiggyInventoryConfig.getInstance()).isLockHotbar()
                && InputConstants.isKeyDown(window,
                        KeyBindingHelper.getBoundKeyOf(PiggyInventoryClient.lockKey).getValue());

        // Debugging altHeld state every frame might be too spammy, but useful if
        // limited.
        // Let's rely on click log for now to confirm input.
        // Or if I really want to check render state:
        if (altHeld)
            PiggyInventoryClient.LOGGER.info("Rendering locks... Alt detected.");

        if (!altHeld)
            return;

        for (Slot slot : screen.getMenu().slots) {
            // Check for blacklist
            if (((PiggyInventoryConfig) PiggyInventoryConfig.getInstance()).getBlacklistedItems()
                    .contains(slot.getItem().getItem().getDescriptionId())) {
                continue; // Don't draw lock for blacklisted items
            }
            if (SlotLockingManager.getInstance().isLocked(screen, slot.index)) {
                int x = slot.x + this.leftPos;
                int y = slot.y + this.topPos;

                // Draw a faint Red Overlay
                context.fill(x, y, x + 16, y + 16, 0x40FF0000); // 25% Opacity Red

                // Draw Lock Icon (simulated geometry for "small lock on top right")
                // A 5x7 lock
                // Top-Right corner of slot is (x+15, y)
                // Let's place it at x+10, y+0 (top right 6x6 area)

                int lx = x + 10;
                int ly = y + 1;

                // Body (Gold/Yellow): 5x4 rect
                int bodyColor = 0xFFD4AF37; // Gold
                context.fill(lx, ly + 3, lx + 5, ly + 7, bodyColor);

                // Shackle (Silver/White): Loop on top
                int shackleColor = 0xFFC0C0C0;
                context.fill(lx + 1, ly, lx + 2, ly + 3, shackleColor); // Left leg
                context.fill(lx + 3, ly, lx + 4, ly + 3, shackleColor); // Right leg
                context.fill(lx + 1, ly, lx + 4, ly + 1, shackleColor); // Top bar
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean altHeld = ((PiggyInventoryConfig) PiggyInventoryConfig.getInstance()).isLockHotbar()
                && InputConstants.isKeyDown(window,
                        KeyBindingHelper.getBoundKeyOf(PiggyInventoryClient.lockKey).getValue());

        if (altHeld) {
            PiggyInventoryClient.LOGGER.info("Click with Lock Key Mod! Toggling lock."); // DEBUG
            Slot slot = this.piggy_getSlotUnderMouse(mouseX, mouseY);
            if (slot != null) {
                SlotLockingManager.getInstance().toggleLock(screen, slot.index);
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (PiggyInventoryClient.sortKey.matches(keyCode, scanCode)) {
            PiggyInventoryClient.LOGGER.info("Sort Key Pressed in GUI! Executing sort."); // DEBUG
            PiggyInventoryClient.handleSort(Minecraft.getInstance());
            cir.setReturnValue(true);
        } else {
            // Debug log to spy on what keys are being pressed
            // PiggyInventoryClient.LOGGER.info("Key Pressed: " + keyCode + " Scan: " +
            // scanCode);
        }
    }

    @Override
    public Slot piggy_getSlotUnderMouse(double mouseX, double mouseY) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        // Manual implementation to avoid mapping issues with getSlotUnderMouse
        for (Slot slot : screen.getMenu().slots) {
            if (this.piggy_isHovering(slot, mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    private boolean piggy_isHovering(Slot slot, double mouseX, double mouseY) {
        // Standard Minecraft slot hovering logic
        return this.piggy_isPointWithinBounds(slot.x, slot.y, 16, 16, mouseX, mouseY);
    }

    private boolean piggy_isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
        int i = this.leftPos;
        int j = this.topPos;
        pointX -= (double) i;
        pointY -= (double) j;
        return pointX >= (double) (x - 1) && pointX < (double) (x + width + 1) && pointY >= (double) (y - 1)
                && pointY < (double) (y + height + 1);
    }
}
