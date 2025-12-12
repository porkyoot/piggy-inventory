
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
            // Render relative to slot position. renderSlot renders at (slot.x, slot.y).
            // But wait, renderSlot uses context.
            // The context might already be translated?
            // AbstractContainerScreen.renderSlot does: guiGraphics.renderItem(item, slot.x,
            // slot.y);
            // So we should draw at slot.x, slot.y.

            // Note: In renderSlot, coordinates are relative to the GuiGraphics current
            // pose...?
            // Usually renderSlot is called inside renderBg or a loop where pose is at 0,0
            // usually (screen root).
            // Slot.x and Slot.y are relative to guiLeft/guiTop usually? No, relative to
            // Screen 0,0?
            // AbstractContainerScreen.renderSlot impl:
            // int i = slot.x; int j = slot.y;
            // guiGraphics.renderItem(itemStack, i, j);

            // Slot.x/y are relative to the Container's 0,0 (inside the GUI texture).
            // But renderSlot often happens inside loop with translations.
            // Let's use the same coordinates as the slot object, but add
            // this.leftPos/topPos?
            // The original renderLocks used `slot.x + this.leftPos` and `slot.y +
            // this.topPos`.
            // AbstractContainerScreen.renderSlot implementation typically uses absolute
            // coordinates if it just does `this.leftPos + slot.x`.
            // Let's look at `AbstractContainerScreen.renderSlot`:
            // It runs: `guiGraphics.renderItem(itemStack, slot.x, slot.y);`
            // Wait, does it add leftPos/topPos?
            // Actually `render` sets up translation?
            // No, standard loop calculates x/y: `int slotX = slot.x; int slotY = slot.y;`
            // But wait, `render` usually translates by leftPos, topPos?
            // Actually, usually `render` calls `renderBg` (background) then iterates slots.
            // Slots usually are drawn relative to the screen, so `slot.x` is relative to
            // container, need `leftPos`.

            int x = slot.x;
            int y = slot.y;

            // Draw a dark overlay (darken background)
            context.fill(x, y, x + 16, y + 16, 0x80000000); // 50% Opacity Black

            // Draw Lock Texture at Top Right
            net.minecraft.resources.ResourceLocation lockTexture = net.minecraft.resources.ResourceLocation
                    .fromNamespaceAndPath("piggy-inventory", "textures/gui/lock.png");
            context.blit(lockTexture, x + 8, y, 0, 0, 8, 8, 8, 8);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean altHeld = InputConstants.isKeyDown(window,
                KeyBindingHelper.getBoundKeyOf(PiggyInventoryClient.lockKey).getValue());

        if (altHeld) {
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
