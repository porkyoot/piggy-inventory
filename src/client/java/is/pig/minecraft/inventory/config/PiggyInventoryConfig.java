package is.pig.minecraft.inventory.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import is.pig.minecraft.lib.ui.AntiCheatFeedbackManager;
import is.pig.minecraft.lib.ui.BlockReason;

/**
 * Configuration data model for Piggy Inventory.
 * Holds the state of user settings.
 */
public class PiggyInventoryConfig extends is.pig.minecraft.lib.config.PiggyClientConfig {

    // --- CONFIG FIELDS ---

    // Tool swap settings
    private boolean toolSwapEnabled = true;
    private List<Integer> swapHotbarSlots = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8));
    private OrePreference orePreference = OrePreference.FORTUNE;

    public enum OrePreference {
        FORTUNE,
        SILK_TOUCH
    }

    // Default lists
    private List<String> silkTouchBlocks = new ArrayList<>(Arrays.asList(
            "minecraft:glass", "minecraft:glass_pane", "minecraft:ice", "minecraft:packed_ice",
            "minecraft:blue_ice", "minecraft:ender_chest", "minecraft:turtle_egg", "minecraft:bee_nest",
            "minecraft:beehive", "minecraft:sculk", "minecraft:sculk_catalyst", "minecraft:sculk_sensor",
            "minecraft:sculk_shrieker", "*stained_glass*"));

    private List<String> fortuneBlocks = new ArrayList<>(Arrays.asList(
            "*_ore", "*ancient_debris*", "*amethyst_cluster*", "minecraft:clay",
            "minecraft:gravel", "minecraft:glowstone", "minecraft:melon", "minecraft:sea_lantern"));

    private List<String> shearsBlocks = new ArrayList<>(Arrays.asList(
            "minecraft:vine", "minecraft:dead_bush", "minecraft:short_grass", "minecraft:tall_grass",
            "minecraft:fern", "minecraft:large_fern", "*leaves*", "minecraft:cobweb",
            "minecraft:seagrass", "minecraft:hanging_roots", "minecraft:glow_lichen"));

    // --- SINGLETON ACCESS ---

    public boolean isToolSwapEnabled() {
        return toolSwapEnabled;
    }

    public void setToolSwapEnabled(boolean toolSwapEnabled) {
        if (toolSwapEnabled) {
            boolean serverForces = !this.serverAllowCheats
                    || (this.serverFeatures != null && this.serverFeatures.containsKey("tool_swap")
                            && !this.serverFeatures.get("tool_swap"));
            
            if (serverForces) {
                AntiCheatFeedbackManager.getInstance().onFeatureBlocked("tool_swap", BlockReason.SERVER_ENFORCEMENT);
                this.toolSwapEnabled = false;
                return;
            }
        }
        this.toolSwapEnabled = toolSwapEnabled;
    }

    // --- HELPERS FOR GUI AVAILABILITY ---

    public boolean isGlobalCheatsEditable() {
        // Gray out if Server forces cheats off
        return this.serverAllowCheats;
    }

    public boolean isToolSwapEditable() {
        if (isNoCheatingMode()) return false;
        if (!this.serverAllowCheats) return false;
        if (this.serverFeatures != null && this.serverFeatures.containsKey("tool_swap") && !this.serverFeatures.get("tool_swap")) return false;
        return true;
    }

    // --- LOGIC CHECKS ---

    public boolean isFeatureToolSwapEnabled() {
        return is.pig.minecraft.lib.features.CheatFeatureRegistry.isFeatureEnabled(
                "tool_swap",
                serverAllowCheats,
                serverFeatures,
                isNoCheatingMode(),
                toolSwapEnabled);
    }

    public List<Integer> getSwapHotbarSlots() {
        return swapHotbarSlots;
    }

    public void setSwapHotbarSlots(List<Integer> swapHotbarSlots) {
        this.swapHotbarSlots = swapHotbarSlots;
    }

    public OrePreference getOrePreference() {
        return orePreference;
    }

    public void setOrePreference(OrePreference orePreference) {
        this.orePreference = orePreference;
    }

    public List<String> getSilkTouchBlocks() {
        return silkTouchBlocks;
    }

    public void setSilkTouchBlocks(List<String> silkTouchBlocks) {
        this.silkTouchBlocks = silkTouchBlocks;
    }

    public List<String> getFortuneBlocks() {
        return fortuneBlocks;
    }

    public void setFortuneBlocks(List<String> fortuneBlocks) {
        this.fortuneBlocks = fortuneBlocks;
    }

    public List<String> getShearsBlocks() {
        return shearsBlocks;
    }

    public void setShearsBlocks(List<String> shearsBlocks) {
        this.shearsBlocks = shearsBlocks;
    }
}