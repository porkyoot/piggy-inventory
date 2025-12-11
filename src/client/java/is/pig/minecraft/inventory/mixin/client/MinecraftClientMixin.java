package is.pig.minecraft.inventory.mixin.client;

import is.pig.minecraft.inventory.mvc.controller.InputController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    /**
     * Inject before startAttack (initial click).
     */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void piggyInventory$beforeAttack(CallbackInfoReturnable<Boolean> cir) {
        boolean shouldCancel = InputController.getToolSwapHandler().onTick((Minecraft) (Object) this);
        if (shouldCancel) {
            cir.setReturnValue(false); 
        }
    }

    /**
     * Inject before continueAttack (holding button).
     * This prevents breaking protected blocks even if the player holds the button
     * and moves the crosshair over them.
     */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void piggyInventory$continueAttack(boolean leftClick, CallbackInfo ci) {
        boolean shouldCancel = InputController.getToolSwapHandler().onTick((Minecraft) (Object) this);
        if (shouldCancel) {
            // Stop the mining progress
            ci.cancel();
        }
    }
}