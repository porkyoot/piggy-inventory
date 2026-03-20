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
    NONE(ResourceLocation.fromNamespaceAndPath("piggy", "textures/gui/icons/cheating_cancel.png"), "Disabled", PiggyInventoryConfig.OrePreference.NONE),
    SILK_TOUCH_PREFERRED(ResourceLocation.fromNamespaceAndPath("piggy", "textures/gui/icons/mode_silk_touch.png"), "Silk Touch", PiggyInventoryConfig.OrePreference.SILK_TOUCH_PREFERRED),
    SILK_TOUCH_STRICT(ResourceLocation.fromNamespaceAndPath("piggy", "textures/gui/icons/mode_preservation.png"), "Silk Touch (Strict)", PiggyInventoryConfig.OrePreference.SILK_TOUCH_STRICT),
    FORTUNE_PREFERRED(ResourceLocation.fromNamespaceAndPath("piggy", "textures/gui/icons/mode_fortune.png"), "Fortune", PiggyInventoryConfig.OrePreference.FORTUNE_PREFERRED),
    FORTUNE_STRICT(ResourceLocation.fromNamespaceAndPath("piggy", "textures/gui/icons/mode_destruction.png"), "Fortune (Strict)", PiggyInventoryConfig.OrePreference.FORTUNE_STRICT);

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

    @Override
    public java.util.List<? extends RadialMenuItem> getSubMenuItems() {
        if (this == SILK_TOUCH_PREFERRED) {
            return java.util.Collections.singletonList(SILK_TOUCH_STRICT);
        } else if (this == FORTUNE_PREFERRED) {
            return java.util.Collections.singletonList(FORTUNE_STRICT);
        }
        return java.util.Collections.emptyList();
    }
}