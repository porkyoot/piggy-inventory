package is.pig.minecraft.inventory.mixin.client;

import is.pig.minecraft.inventory.handler.AutoRefillHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void onUseItemOnReturn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        AutoRefillHandler.getInstance().onTick(Minecraft.getInstance());
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void onUseItemReturn(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        AutoRefillHandler.getInstance().onTick(Minecraft.getInstance());
    }
}
