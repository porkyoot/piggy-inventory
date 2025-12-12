package is.pig.minecraft.inventory.mvc.controller;

import is.pig.minecraft.inventory.config.ConfigPersistence;
import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import is.pig.minecraft.inventory.mvc.model.WeaponSwitchMode;
import is.pig.minecraft.lib.ui.AntiCheatFeedbackManager;
import is.pig.minecraft.lib.ui.BlockReason;
import is.pig.minecraft.lib.ui.GenericRadialMenuScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

public class WeaponPreferenceHandler {

    private boolean wasKeyDown = false;

    public void onTick(Minecraft client) {
        boolean isKeyDown = InputController.weaponPreferenceKey.isDown();

        if (isKeyDown && !wasKeyDown && client.screen == null) {
            openMenu(client);
        }

        wasKeyDown = isKeyDown;
    }

    private void openMenu(Minecraft client) {
        PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();

        WeaponSwitchMode current = WeaponSwitchMode.fromConfig(config.getWeaponPreference());

        WeaponSwitchMode center = WeaponSwitchMode.NONE;
        List<WeaponSwitchMode> radials = Arrays.asList(
                WeaponSwitchMode.SPEED,
                WeaponSwitchMode.DAMAGE,
                WeaponSwitchMode.RANGE);

        client.setScreen(new GenericRadialMenuScreen<>(
                Component.literal("Weapon Preference"),
                center,
                radials,
                current,
                KeyBindingHelper.getBoundKeyOf(InputController.weaponPreferenceKey),
                (newSelection) -> {
                    config.setWeaponPreference(newSelection.getConfigValue());
                    ConfigPersistence.save();
                },
                () -> {
                },
                (item) -> null,
                null,

                (item) -> item == WeaponSwitchMode.NONE || config.isWeaponSwitchEditable(),

                (item) -> {
                    BlockReason reason = (config.serverAllowCheats &&
                            (config.serverFeatures == null || !config.serverFeatures.containsKey("weapon_switch")
                                    || config.serverFeatures.get("weapon_switch")))
                                            ? BlockReason.LOCAL_CONFIG
                                            : BlockReason.SERVER_ENFORCEMENT;
                    AntiCheatFeedbackManager.getInstance().onFeatureBlocked("weapon_switch", reason);
                }));
    }
}
