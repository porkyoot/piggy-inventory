package is.pig.minecraft.inventory.mvc.controller;

import is.pig.minecraft.inventory.config.ConfigPersistence;
import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import is.pig.minecraft.inventory.mvc.model.ToolPreference;
import is.pig.minecraft.lib.ui.AntiCheatFeedbackManager;
import is.pig.minecraft.lib.ui.BlockReason;
import is.pig.minecraft.lib.ui.GenericRadialMenuScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

public class ToolPreferenceHandler {

    private boolean wasKeyDown = false;

    public void onTick(Minecraft client) {
        boolean isKeyDown = InputController.preferenceKey.isDown();

        if (isKeyDown && !wasKeyDown && client.screen == null) {
            openMenu(client);
        }

        wasKeyDown = isKeyDown;
    }

    private void openMenu(Minecraft client) {
        PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();
        
        ToolPreference current = ToolPreference.fromConfig(config.getOrePreference());
        
        ToolPreference center = ToolPreference.NONE;
        List<ToolPreference> radials = Arrays.asList(ToolPreference.FORTUNE, ToolPreference.SILK_TOUCH);

        client.setScreen(new GenericRadialMenuScreen<>(
            Component.literal("Tool Preference"),
            center,
            radials,
            current, 
            KeyBindingHelper.getBoundKeyOf(InputController.preferenceKey),
            (newSelection) -> {
                config.setOrePreference(newSelection.getConfigValue());
                ConfigPersistence.save();
            },
            () -> {}, 
            (item) -> null, 
            null,
            
            (item) -> item == ToolPreference.NONE || config.isToolSwapEditable(),
            
            (item) -> {
                BlockReason reason = (config.serverAllowCheats && 
                    (config.serverFeatures == null || !config.serverFeatures.containsKey("tool_swap") || config.serverFeatures.get("tool_swap")))
                    ? BlockReason.LOCAL_CONFIG
                    : BlockReason.SERVER_ENFORCEMENT;
                AntiCheatFeedbackManager.getInstance().onFeatureBlocked("tool_swap", reason);
            }
        ));
    }
}