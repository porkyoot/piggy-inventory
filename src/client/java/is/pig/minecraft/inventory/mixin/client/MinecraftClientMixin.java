package is.pig.minecraft.inventory.mixin.client;

import is.pig.minecraft.inventory.mvc.controller.InputController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    /**
     * Inject before startAttack to ensure tool swapping happens BEFORE the block is
     * hit/broken.
     */
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void piggyInventory$beforeAttack(CallbackInfoReturnable<Boolean> cir) {
        // This is the CRITICAL fix: Run tool swap logic immediately before the attack
        // happens.
        InputController.getToolSwapHandler().onTick((Minecraft) (Object) this);
    }
}