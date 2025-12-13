package is.pig.minecraft.inventory.mixin.client;

import is.pig.minecraft.inventory.PiggyInventoryClient; // Kept if needed later, but removed unused warning earlier
import is.pig.minecraft.inventory.locking.SlotLockingManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof AbstractContainerScreen) {
            boolean shiftHeld = InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
                    || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
            boolean ctrlHeld = InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL)
                    || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL);

            if ((shiftHeld || ctrlHeld) && vertical != 0) {
                // Return value of helper indicates if we should cancel the event
                if (this.piggy_handleScrollTransfer((AbstractContainerScreen<?>) client.screen, vertical, ctrlHeld)) {
                    ci.cancel();
                }
            }
        }
    }

    private boolean piggy_handleScrollTransfer(AbstractContainerScreen<?> screen, double scrollDelta,
            boolean forceMoveAll) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null)
            return false;

        net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
        net.minecraft.world.entity.player.Inventory playerInventory = client.player.getInventory();

        // Separate slots
        java.util.List<Slot> storageSlots = new java.util.ArrayList<>();
        java.util.List<Slot> playerSlots = new java.util.ArrayList<>();

        for (Slot slot : menu.slots) {
            if (slot.container == playerInventory) {
                playerSlots.add(slot);
            } else {
                storageSlots.add(slot);
            }
        }

        // If no storage (e.g. inventory screen), do nothing
        if (storageSlots.isEmpty())
            return false;

        boolean moveUp = scrollDelta > 0; // Inventory -> Storage

        java.util.List<Slot> sourceSlots = moveUp ? playerSlots : storageSlots;
        java.util.List<Slot> targetSlots = moveUp ? storageSlots : playerSlots;

        boolean actionTaken = false;

        for (Slot sourceSlot : sourceSlots) {
            if (!sourceSlot.hasItem())
                continue;

            // Skip locked slots
            if (SlotLockingManager.getInstance().isLocked(sourceSlot)) {
                continue;
            }

            net.minecraft.world.item.ItemStack sourceStack = sourceSlot.getItem();
            boolean performTransfer = false;

            if (forceMoveAll) {
                // Ctrl+Scroll: Move everything
                performTransfer = true;
            } else {
                // Shift+Scroll: Smart Stack (match only)
                // Check if target has matching item
                for (Slot targetSlot : targetSlots) {
                    if (targetSlot.hasItem()) {
                        net.minecraft.world.item.ItemStack targetStack = targetSlot.getItem();
                        // Using ItemStack.isSameItemSameComponents for modern versions
                        if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(sourceStack, targetStack)) {
                            performTransfer = true;
                            break;
                        }
                    }
                }
            }

            if (performTransfer) {
                client.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot.index, 0,
                        net.minecraft.world.inventory.ClickType.QUICK_MOVE, client.player);
                actionTaken = true;
            }
        }

        return actionTaken;
    }
}
