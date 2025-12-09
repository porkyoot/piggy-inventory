package is.pig.minecraft.inventory.mvc.controller;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class InputController {

    // Static keys accessible by handlers/views
    public static KeyMapping toggleToolSwapKey;

    // Handlers (Logic separation)
    private static final ToolSwapHandler toolSwapHandler = new ToolSwapHandler();

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
    }

    private void registerEvents() {
        // Client Tick -> Delegated to handlers
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null)
                return;

            // Handle Toggles
            while (toggleToolSwapKey.consumeClick()) {
                boolean newState = !is.pig.minecraft.inventory.config.PiggyConfig.getInstance().isToolSwapEnabled();
                is.pig.minecraft.inventory.config.PiggyConfig.getInstance().setToolSwapEnabled(newState);
                is.pig.minecraft.inventory.config.ConfigPersistence.save();

                if (newState) {
                    boolean restricted = false;
                    // Check anti-cheat conditions
                    if (is.pig.minecraft.inventory.config.PiggyConfig.getInstance().isNoCheatingMode()
                            && !is.pig.minecraft.inventory.config.PiggyConfig.getInstance().serverAllowCheats) {
                        if (!client.player.isCreative() && !client.player.isSpectator()) {
                            restricted = true;
                        }
                    }

                    if (restricted) {
                        client.player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("Tool Swap: ON (Restricted by Anti-Cheat)")
                                        .withStyle(net.minecraft.ChatFormatting.RED),
                                true);
                    } else {
                        client.player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("Tool Swap: ON")
                                        .withStyle(net.minecraft.ChatFormatting.YELLOW),
                                true);
                    }
                } else {
                    client.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("Tool Swap: OFF")
                                    .withStyle(net.minecraft.ChatFormatting.YELLOW),
                            true);
                }
            }

            toolSwapHandler.onTick(client);
        });
    }

    public static ToolSwapHandler getToolSwapHandler() {
        return toolSwapHandler;
    }
}