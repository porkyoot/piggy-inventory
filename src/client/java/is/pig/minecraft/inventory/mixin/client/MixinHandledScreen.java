
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
        boolean altHeld = InputConstants.isKeyDown(window,
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

            // Only allow locking for player inventory slots (Storage + Hotbar)
            // Typically these are the last 36 slots in any container.
            // Or use slot.container == client.player.getInventory() check?
            if (slot.container != Minecraft.getInstance().player.getInventory()) {
                continue;
            }

            // Exclude Armor (36-39) and Offhand (40)
            // PlayerInventory size is 41 total? 36 Main + 4 Armor + 1 Offhand.
            // Indices 0-35 are Main (Hotbar 0-8, Storage 9-35 or vice versa depending on
            // mapping, but all < 36)
            if (slot.getContainerSlot() >= 36) {
                continue;
            }

            if (SlotLockingManager.getInstance().isLocked(slot)) {
                int x = slot.x + this.leftPos;
                int y = slot.y + this.topPos;

                context.pose().pushPose();
                context.pose().translate(0, 0, 200); // High Z to render on top of items

                // Draw a dark overlay (darken background)
                context.fill(x, y, x + 16, y + 16, 0x80000000); // 50% Opacity Black

                // Draw Lock Texture at Top Right
                net.minecraft.resources.ResourceLocation lockTexture = net.minecraft.resources.ResourceLocation
                        .fromNamespaceAndPath("piggy-inventory", "textures/gui/lock.png");
                context.blit(lockTexture, x + 8, y, 0, 0, 8, 8, 8, 8);

                context.pose().popPose();
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean altHeld = InputConstants.isKeyDown(window,
                KeyBindingHelper.getBoundKeyOf(PiggyInventoryClient.lockKey).getValue());

        if (altHeld) {
            PiggyInventoryClient.LOGGER.info("Click with Lock Key Mod! Toggling lock."); // DEBUG
            Slot slot = this.piggy_getSlotUnderMouse(mouseX, mouseY);
            if (slot != null) {
                // Only allow locking for player inventory slots (Logic now centralised in
                // Manager, but we check logic here too)
                // Actually manager checks container, but we must check index range here too if
                // we want to be safe,
                // but manager handles logic nicely.
                if (slot.container == Minecraft.getInstance().player.getInventory() && slot.getContainerSlot() < 36) {
                    SlotLockingManager.getInstance().toggleLock(slot);
                    cir.setReturnValue(true);
                }
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
