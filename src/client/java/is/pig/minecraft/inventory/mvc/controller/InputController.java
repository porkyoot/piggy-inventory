package is.pig.minecraft.inventory.mvc.controller;

import com.mojang.blaze3d.platform.InputConstants;
import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import is.pig.minecraft.lib.ui.AntiCheatFeedbackManager;
import is.pig.minecraft.lib.ui.BlockReason;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class InputController {

    public static KeyMapping toggleToolSwapKey;
    public static KeyMapping preferenceKey;

    private static final ToolSwapHandler toolSwapHandler = new ToolSwapHandler();
    private static final ToolPreferenceHandler preferenceHandler = new ToolPreferenceHandler();

    public void initialize() {
        registerKeys();
        registerEvents();
    }

    private void registerKeys() {
        toggleToolSwapKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Toggle Tool Swap",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "Piggy Inventory"));

        preferenceKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Tool Preference Menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "Piggy Inventory"));
    }

    private void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null)
                return;

            // Handle Toggles
            while (toggleToolSwapKey.consumeClick()) {
                PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();
                
                boolean isCurrentlyEnabled = config.isToolSwapEnabled();
                
                if (isCurrentlyEnabled) {
                    config.setToolSwapEnabled(false);
                } else {
                    if (!config.isToolSwapEditable()) {
                        BlockReason reason = (config.serverAllowCheats && 
                            (config.serverFeatures == null || !config.serverFeatures.containsKey("tool_swap") || config.serverFeatures.get("tool_swap")))
                            ? BlockReason.LOCAL_CONFIG
                            : BlockReason.SERVER_ENFORCEMENT;
                        AntiCheatFeedbackManager.getInstance().onFeatureBlocked("tool_swap", reason);
                    } else {
                        config.setToolSwapEnabled(true);
                    }
                }
                is.pig.minecraft.inventory.config.ConfigPersistence.save();
            }

            toolSwapHandler.onTick(client);
            preferenceHandler.onTick(client);
        });
    }

    public static ToolSwapHandler getToolSwapHandler() {
        return toolSwapHandler;
    }
}