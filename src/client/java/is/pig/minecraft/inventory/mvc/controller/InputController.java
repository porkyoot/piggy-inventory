package is.pig.minecraft.inventory.mvc.controller;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class InputController {

    public static KeyMapping preferenceKey; // Mining preference
    public static KeyMapping weaponPreferenceKey;

    private static final ToolSwapHandler toolSwapHandler = new ToolSwapHandler();
    private static final ToolPreferenceHandler preferenceHandler = new ToolPreferenceHandler();

    private static final WeaponSwapHandler weaponSwapHandler = new WeaponSwapHandler();
    private static final WeaponPreferenceHandler weaponPreferenceHandler = new WeaponPreferenceHandler();

    public void initialize() {
        registerKeys();
        registerEvents();
    }

    private void registerKeys() {
        preferenceKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Tool Preference Menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "Piggy Inventory"));

        weaponPreferenceKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Weapon Preference Menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G, // Choose a default key
                "Piggy Inventory"));
    }

    private void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null)
                return;

            toolSwapHandler.onTick(client);
            preferenceHandler.onTick(client);
            weaponPreferenceHandler.onTick(client);
        });
    }

    public static ToolSwapHandler getToolSwapHandler() {
        return toolSwapHandler;
    }

    public static WeaponSwapHandler getWeaponSwapHandler() {
        return weaponSwapHandler;
    }
}