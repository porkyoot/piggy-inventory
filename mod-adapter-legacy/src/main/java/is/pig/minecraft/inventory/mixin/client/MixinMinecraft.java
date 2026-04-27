package is.pig.minecraft.inventory.mixin.client;

import is.pig.minecraft.inventory.handler.QuickLootHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (QuickLootHandler.getInstance().interceptSetScreen(screen)) {
            ci.cancel();
            return;
        }
        if (is.pig.minecraft.inventory.handler.SortHandler.getInstance().interceptSetScreen(screen)) {
            ci.cancel();
            return;
        }
    }

    @Inject(method = "startUseItem", at = @At("RETURN"))
    private void onStartUseItemReturn(CallbackInfo ci) {
        if (((Minecraft)(Object)this).player != null) {
            is.pig.minecraft.inventory.handler.AutoRefillHandler.getInstance().onTick((Minecraft)(Object)this);
        }
    }
}
