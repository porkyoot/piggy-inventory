package is.pig.minecraft.inventory.mvc.controller;

import is.pig.minecraft.inventory.config.ConfigPersistence;
import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import is.pig.minecraft.inventory.mvc.model.ToolPreference;
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
        
        // Current selected preference
        ToolPreference current = ToolPreference.fromConfig(config.getOrePreference());
        
        // Center item is always NONE (Disabling)
        ToolPreference center = ToolPreference.NONE;
        
        // Radial items are the active options
        List<ToolPreference> radials = Arrays.asList(ToolPreference.FORTUNE, ToolPreference.SILK_TOUCH);

        client.setScreen(new GenericRadialMenuScreen<>(
            Component.literal("Tool Preference"),
            center,
            radials,
            current, // Initially selected item (could be center or one of the radials)
            KeyBindingHelper.getBoundKeyOf(InputController.preferenceKey),
            (newSelection) -> {
                // Update config on selection change
                config.setOrePreference(newSelection.getConfigValue());
                ConfigPersistence.save();
            },
            () -> {}, // Close callback
            (item) -> null, // No extra info text
            null // No scroll handling needed
        ));
    }
}