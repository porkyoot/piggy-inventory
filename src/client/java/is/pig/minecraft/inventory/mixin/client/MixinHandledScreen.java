
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
import com.mojang.blaze3d.systems.RenderSystem;

@Environment(EnvType.CLIENT)
@Mixin(AbstractContainerScreen.class)
public abstract class MixinHandledScreen implements is.pig.minecraft.inventory.duck.IHandledScreen {

    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;

    private static final net.minecraft.resources.ResourceLocation LOCK_TEXTURE = net.minecraft.resources.ResourceLocation
            .fromNamespaceAndPath("piggy-inventory", "textures/gui/lock.png");

    private Slot piggy_lastShiftClickedSlot;

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void renderSlotLock(GuiGraphics context, Slot slot, CallbackInfo ci) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean altHeld = InputConstants.isKeyDown(window,
                KeyBindingHelper.getBoundKeyOf(PiggyInventoryClient.lockKey).getValue());

        if (!altHeld)
            return;

        // Skip Blacklisted Items
        if (((PiggyInventoryConfig) PiggyInventoryConfig.getInstance()).getBlacklistedItems()
                .contains(slot.getItem().getItem().getDescriptionId())) {
            return;
        }

        // Only allow locking for player inventory slots (Storage + Hotbar)
        if (slot.container != Minecraft.getInstance().player.getInventory()) {
            return;
        }

        // Exclude Armor (36-39) and Offhand (40)
        if (slot.getContainerSlot() >= 36) {
            return;
        }

        if (SlotLockingManager.getInstance().isLocked(slot)) {

            int x = slot.x;
            int y = slot.y;

            // Draw a dark overlay (darken background)
            context.fill(x, y, x + 16, y + 16, 0x80000000); // 50% Opacity Black

            // Draw Lock Texture at Top Right
            context.pose().pushPose();
            context.pose().translate(0, 0, 300);
            RenderSystem.disableDepthTest();
            context.blit(LOCK_TEXTURE, x, y, 0, 0, 8, 8, 8, 8);
            context.pose().popPose();
            RenderSystem.enableDepthTest();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean altHeld = InputConstants.isKeyDown(window,
                KeyBindingHelper.getBoundKeyOf(PiggyInventoryClient.lockKey).getValue());
        boolean shiftHeld = InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);

        if (button == 0) {
            this.piggy_lastShiftClickedSlot = null;
        }

        if (altHeld) {
            Slot slot = this.piggy_getSlotUnderMouse(mouseX, mouseY);
            if (slot != null) {
                // Only allow locking for player inventory slots
                if (slot.container == Minecraft.getInstance().player.getInventory() && slot.getContainerSlot() < 36) {
                    SlotLockingManager.getInstance().toggleLock(slot);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        if (shiftHeld && button == 0) {
            Slot slot = this.piggy_getSlotUnderMouse(mouseX, mouseY);
            if (slot != null) {
                this.piggy_lastShiftClickedSlot = slot;
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == 0) {
            this.piggy_lastShiftClickedSlot = null;
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY,
            CallbackInfoReturnable<Boolean> cir) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean shiftHeld = InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);

        if (shiftHeld && button == 0) {
            Slot slot = this.piggy_getSlotUnderMouse(mouseX, mouseY);

            if (slot != null && slot.hasItem() && slot != this.piggy_lastShiftClickedSlot) {
                if (SlotLockingManager.getInstance().isLocked(slot)) {
                    return;
                }

                this.piggy_lastShiftClickedSlot = slot;

                // Perform quick move (shift-click)
                Minecraft client = Minecraft.getInstance();
                AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

                client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 0,
                        net.minecraft.world.inventory.ClickType.QUICK_MOVE, client.player);
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (PiggyInventoryClient.sortKey.matches(keyCode, scanCode)) {
            PiggyInventoryClient.handleSort(Minecraft.getInstance());
            cir.setReturnValue(true);
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
