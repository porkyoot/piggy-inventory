package is.pig.minecraft.inventory.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
// import net.minecraft.world.inventory.Slot; // Unused in this file now?
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        is.pig.minecraft.inventory.config.PiggyInventoryConfig config = (is.pig.minecraft.inventory.config.PiggyInventoryConfig) is.pig.minecraft.inventory.config.PiggyInventoryConfig
                .getInstance();

        if (client.screen instanceof AbstractContainerScreen) {
            if (!config.isMouseTwicks())
                return;

            boolean shiftHeld = is.pig.minecraft.inventory.util.InventoryUtils.isShiftDown();
            boolean ctrlHeld = is.pig.minecraft.inventory.util.InventoryUtils.isFastLootDown();

            if ((shiftHeld || ctrlHeld) && vertical != 0) {
                // Return value of helper indicates if we should cancel the event
                if (this.piggy_handleScrollTransfer((AbstractContainerScreen<?>) client.screen, vertical, ctrlHeld)) {
                    ci.cancel();
                }
            }
        } else {
            // Not in screen, but scrolling? Check for Looking At Container
            boolean ctrlHeld = is.pig.minecraft.inventory.util.InventoryUtils.isFastLootDown();
            if (is.pig.minecraft.inventory.handler.QuickLootHandler.getInstance().onScroll(client, vertical,
                    ctrlHeld)) {
                ci.cancel();
            }
        }
    }

    private boolean piggy_handleScrollTransfer(AbstractContainerScreen<?> screen, double scrollDelta,
            boolean forceMoveAll) {
        return is.pig.minecraft.inventory.util.InventoryUtils.handleScrollTransfer(screen, scrollDelta, forceMoveAll);
    }
}
