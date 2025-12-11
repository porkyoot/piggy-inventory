package is.pig.minecraft.inventory.mvc.model;

import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import is.pig.minecraft.lib.ui.RadialMenuItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Enum representing the tool preferences (Silk Touch vs Fortune),
 * adapted for the Radial Menu.
 */
public enum ToolPreference implements RadialMenuItem {
    NONE(ResourceLocation.fromNamespaceAndPath("piggy-lib", "textures/gui/blocked_icon.png"), "No Preference", PiggyInventoryConfig.OrePreference.NONE),
    SILK_TOUCH(ResourceLocation.fromNamespaceAndPath("piggy-inventory", "textures/gui/silk_touch.png"), "Silk Touch", PiggyInventoryConfig.OrePreference.SILK_TOUCH),
    FORTUNE(ResourceLocation.fromNamespaceAndPath("piggy-inventory", "textures/gui/fortune.png"), "Fortune", PiggyInventoryConfig.OrePreference.FORTUNE);

    private final ResourceLocation icon;
    private final Component displayName;
    private final PiggyInventoryConfig.OrePreference configValue;

    ToolPreference(ResourceLocation icon, String name, PiggyInventoryConfig.OrePreference configValue) {
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
    
    public PiggyInventoryConfig.OrePreference getConfigValue() {
        return configValue;
    }

    public static ToolPreference fromConfig(PiggyInventoryConfig.OrePreference pref) {
        for (ToolPreference tp : values()) {
            if (tp.configValue == pref) return tp;
        }
        return NONE;
    }
}