package is.pig.minecraft.inventory.mvc.model;

import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import is.pig.minecraft.lib.ui.RadialMenuItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public enum WeaponSwitchMode implements RadialMenuItem {
    NONE(ResourceLocation.fromNamespaceAndPath("piggy-lib", "textures/gui/blocked_icon.png"), "Disabled",
            PiggyInventoryConfig.WeaponPreference.NONE),
    SPEED(ResourceLocation.fromNamespaceAndPath("piggy-inventory", "textures/gui/sword.png"), "Prioritize Speed",
            PiggyInventoryConfig.WeaponPreference.SPEED),
    DAMAGE(ResourceLocation.fromNamespaceAndPath("piggy-inventory", "textures/gui/axe.png"), "Prioritize Damage",
            PiggyInventoryConfig.WeaponPreference.DAMAGE),
    RANGE(ResourceLocation.fromNamespaceAndPath("piggy-inventory", "textures/gui/trident.png"), "Prioritize Range",
            PiggyInventoryConfig.WeaponPreference.RANGE);

    private final ResourceLocation icon;
    private final Component displayName;
    private final PiggyInventoryConfig.WeaponPreference configValue;

    WeaponSwitchMode(ResourceLocation icon, String name, PiggyInventoryConfig.WeaponPreference configValue) {
        this.icon = icon;
        this.displayName = Component.literal(name);
        this.configValue = configValue;
    }

    @Override
    public ResourceLocation getIconLocation(boolean isSelected) {
        return icon;
    }

    @Override
    public Component getDisplayName() {
        return displayName;
    }

    public PiggyInventoryConfig.WeaponPreference getConfigValue() {
        return configValue;
    }

    public static WeaponSwitchMode fromConfig(PiggyInventoryConfig.WeaponPreference pref) {
        for (WeaponSwitchMode mode : values()) {
            if (mode.configValue == pref)
                return mode;
        }
        return NONE;
    }
}
