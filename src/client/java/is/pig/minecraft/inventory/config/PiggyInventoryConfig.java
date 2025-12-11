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

    private List<Integer> swapHotbarSlots = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8));
    
    // Current state (NONE means disabled)
    private OrePreference orePreference = OrePreference.FORTUNE;
    
    // Remember last choice for toggling (default Fortune)
    private OrePreference lastActivePreference = OrePreference.FORTUNE;

    public enum OrePreference {
        NONE,
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

    // Blocks that should NOT be broken when in Silk Touch (Safe) mode.
    // These are blocks that usually drop nothing or lose their value when broken.
    // Removed 'amethyst_cluster' because it DOES drop with Silk Touch.
    // Added 'small/medium/large_amethyst_bud' because they DO NOT drop themselves.
    private List<String> protectedBlocks = new ArrayList<>(Arrays.asList(
            "minecraft:budding_amethyst",
            "minecraft:farmland", 
            "minecraft:suspicious_sand", "minecraft:suspicious_gravel", 
            "minecraft:spawner", "minecraft:trial_spawner"));

    // --- SINGLETON ACCESS ---

    public boolean isToolSwapEnabled() {
        return orePreference != OrePreference.NONE;
    }

    public void setToolSwapEnabled(boolean enabled) {
        if (enabled) {
            boolean serverForces = !this.serverAllowCheats
                    || (this.serverFeatures != null && this.serverFeatures.containsKey("tool_swap")
                            && !this.serverFeatures.get("tool_swap"));
            
            if (serverForces) {
                AntiCheatFeedbackManager.getInstance().onFeatureBlocked("tool_swap", BlockReason.SERVER_ENFORCEMENT);
                this.orePreference = OrePreference.NONE;
                return;
            }
            
            if (lastActivePreference == OrePreference.NONE) {
                lastActivePreference = OrePreference.FORTUNE;
            }
            this.orePreference = lastActivePreference;
        } else {
            this.orePreference = OrePreference.NONE;
        }
    }

    public boolean isToolSwapEditable() {
        if (isNoCheatingMode()) return false;
        if (!this.serverAllowCheats) return false;
        if (this.serverFeatures != null && this.serverFeatures.containsKey("tool_swap") && !this.serverFeatures.get("tool_swap")) return false;
        return true;
    }

    public boolean isFeatureToolSwapEnabled() {
        boolean enabledInConfig = (orePreference != OrePreference.NONE);
        
        return is.pig.minecraft.lib.features.CheatFeatureRegistry.isFeatureEnabled(
                "tool_swap",
                serverAllowCheats,
                serverFeatures,
                isNoCheatingMode(),
                enabledInConfig);
    }

    public OrePreference getOrePreference() {
        return orePreference;
    }

    public void setOrePreference(OrePreference pref) {
        if (pref != OrePreference.NONE) {
             boolean serverForces = !this.serverAllowCheats
                    || (this.serverFeatures != null && this.serverFeatures.containsKey("tool_swap")
                            && !this.serverFeatures.get("tool_swap"));
             
             if (serverForces) {
                 AntiCheatFeedbackManager.getInstance().onFeatureBlocked("tool_swap", BlockReason.SERVER_ENFORCEMENT);
                 return;
             }
             this.lastActivePreference = pref;
        }
        this.orePreference = pref;
    }

    public List<Integer> getSwapHotbarSlots() {
        return swapHotbarSlots;
    }

    public void setSwapHotbarSlots(List<Integer> swapHotbarSlots) {
        this.swapHotbarSlots = swapHotbarSlots;
    }
    
    public List<String> getSilkTouchBlocks() { return silkTouchBlocks; }
    public void setSilkTouchBlocks(List<String> list) { this.silkTouchBlocks = list; }
    public List<String> getFortuneBlocks() { return fortuneBlocks; }
    public void setFortuneBlocks(List<String> list) { this.fortuneBlocks = list; }
    public List<String> getShearsBlocks() { return shearsBlocks; }
    public void setShearsBlocks(List<String> list) { this.shearsBlocks = list; }
    public List<String> getProtectedBlocks() { return protectedBlocks; }
    public void setProtectedBlocks(List<String> list) { this.protectedBlocks = list; }
}