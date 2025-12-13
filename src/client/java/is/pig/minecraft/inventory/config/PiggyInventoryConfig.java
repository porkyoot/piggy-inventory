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

    // --- SORTING CONFIG ---
    // private boolean lockHotbar = true; // Removed per user request
    private SortingAlgorithm defaultAlgorithm = SortingAlgorithm.SMART;
    private SortingLayout defaultLayout = SortingLayout.COMPACT;
    private int tickDelay = 1;
    private List<String> blacklistedInventories = new ArrayList<>();
    private List<String> blacklistedItems = new ArrayList<>();

    // Persistent Locked Slots: ScreenClass -> Set of SlotIndices
    // private java.util.Map<String, java.util.Set<Integer>> savedLocks = new
    // java.util.HashMap<>();

    public enum SortingAlgorithm {
        ALPHABETICAL("Alphabetical", "Sorts items alphabetically (A-Z)."),
        CREATIVE("Creative", "Sorts items based on the Creative Inventory order."),
        SMART("Smart Category", "Groups items by heuristic categories."),
        COLOR("Color", "Sorts items by their visual color/hue."),
        RARITY("Rarity", "Sorts items by rarity (Epic -> Common)."),
        MATERIAL("Material", "Groups items by their material (e.g. Acacia, Iron)."),
        TYPE("Type", "Groups items by their type (e.g. Boats, Swords)."),
        TAG("Tag Priority", "Sorts based on a hardcoded priority list of Tags."),
        JSON("Custom List", "Sorts based on 'config/piggy-inventory/custom_sort.json'.");

        public final String name;
        public final String description;

        SortingAlgorithm(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum SortingLayout {
        COMPACT("Compact"),
        COLUMNS("Columns"),
        ROWS("Rows"),
        GRID("Grid");

        public final String name;

        SortingLayout(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

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
            "minecraft:sculk_shrieker", "*stained_glass*", "minecraft:sculk_vein",
            "minecraft:budding_amethyst", "minecraft:small_amethyst_bud", "minecraft:medium_amethyst_bud",
            "minecraft:large_amethyst_bud"));

    private List<String> fortuneBlocks = new ArrayList<>(Arrays.asList(
            "*_ore", "*ancient_debris*", "*amethyst_cluster*", "minecraft:clay",
            "minecraft:gravel", "minecraft:glowstone", "minecraft:melon", "minecraft:sea_lantern"));

    private List<String> shearsBlocks = new ArrayList<>(Arrays.asList(
            "minecraft:vine", "minecraft:dead_bush", "minecraft:short_grass", "minecraft:tall_grass",
            "minecraft:fern", "minecraft:large_fern", "*leaves*", "minecraft:cobweb",
            "minecraft:seagrass", "minecraft:hanging_roots", "minecraft:glow_lichen"));

    // Blocks that should NOT be broken when in Silk Touch (Safe) mode.
    // These are blocks that usually drop nothing or lose their value when broken.
    // Removed 'budding_amethyst' per user request to allow tool swapping/breaking.
    private List<String> protectedBlocks = new ArrayList<>(Arrays.asList(
            "minecraft:farmland",
            "minecraft:suspicious_sand", "minecraft:suspicious_gravel",
            "minecraft:spawner", "minecraft:trial_spawner"));

    // --- WEAPON SWITCH CONFIG ---
    // If true, the feature is active and will use weaponPreference.
    // However, original logic tied "active" to "preference != NONE".
    // We should keep that for simplicity or split it?
    // User asked "Add a setting ... for both enabling/disabling and weapon
    // preference".
    // This implies boolean toggle AND preference.
    // Currently isFeatureWeaponSwitchEnabled checks preference != NONE.
    // Let's refactor:
    // 1. Boolean field `weaponSwitchEnabled`.
    // 2. WeaponPreference field (defaulting to DAMAGE or SPEED, not NONE).
    // But original code (and ToolSwap) uses Enum NONE as disabled.
    // If we want separate list config, maybe we should keep simple toggle logic.
    // Let's stick to current logic: NONE = Disabled.
    // But user wants "enabling/disabling AND weapon preference".
    // If I select "Speed", it is enabled. If I select "None", it is disabled.
    // The previous prompt said: "Add a key to enable/disable the feature
    // [toggleWeaponSwitch]".
    // This flips between NONE and lastActivePreference.
    // To satisfy user desire for "Setting ... for enabling/disabling", we can
    // expose a Boolean Option in GUI that toggles between NONE and LastActive.

    private WeaponPreference weaponPreference = WeaponPreference.NONE;
    private WeaponPreference lastActiveWeaponPreference = WeaponPreference.DAMAGE;

    private List<Integer> weaponSwapHotbarSlots = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8));

    // Prioritized Lists
    private List<String> fastWeapons = new ArrayList<>(Arrays.asList(
            "minecraft:netherite_sword", "minecraft:diamond_sword",
            "minecraft:iron_sword", "minecraft:golden_sword",
            "minecraft:stone_sword", "minecraft:wooden_sword",
            "minecraft:trident"));

    private List<String> heavyWeapons = new ArrayList<>(Arrays.asList(
            "minecraft:mace",
            "minecraft:netherite_axe", "minecraft:diamond_axe",
            "minecraft:iron_axe", "minecraft:golden_axe",
            "minecraft:stone_axe", "minecraft:wooden_axe"));

    private List<String> rangeWeapons = new ArrayList<>(Arrays.asList(
            "minecraft:trident",
            "minecraft:bow",
            "minecraft:crossbow"));

    public enum WeaponPreference {
        NONE,
        SPEED, // Sword
        DAMAGE, // Axe
        RANGE // Trident, Bow, Crossbow
    }

    // GUI Helper for boolean binding
    public boolean isWeaponSwitchBoolean() {
        return weaponPreference != WeaponPreference.NONE;
    }

    public void setWeaponSwitchBoolean(boolean enabled) {
        if (enabled) {
            if (lastActiveWeaponPreference == WeaponPreference.NONE) {
                lastActiveWeaponPreference = WeaponPreference.DAMAGE;
            }
            setWeaponPreference(lastActiveWeaponPreference);
        } else {
            setWeaponPreference(WeaponPreference.NONE);
        }
    }

    public void setGuiWeaponPreference(WeaponPreference pref) {
        // This setter is for the selector. If user selects NONE, it disables.
        // If user selects SPEED, it enables and sets preference.
        // But if currently disabled (NONE), and user changes preference to SPEED in
        // GUI, it should enable.
        // If currently enabled (SPEED), and user changes to DAMAGE, it updates.
        // If user selects NONE, it disables.
        setWeaponPreference(pref);
    }

    public WeaponPreference getGuiWeaponPreference() {
        if (weaponPreference == WeaponPreference.NONE) {
            return lastActiveWeaponPreference;
        }
        return weaponPreference;
    }

    public boolean isFeatureWeaponSwitchEnabled() {
        boolean enabledInConfig = (weaponPreference != WeaponPreference.NONE);

        return is.pig.minecraft.lib.features.CheatFeatureRegistry.isFeatureEnabled(
                "weapon_switch",
                serverAllowCheats,
                serverFeatures,
                isNoCheatingMode(),
                enabledInConfig);
    }

    public WeaponPreference getWeaponPreference() {
        return weaponPreference;
    }

    public void setWeaponPreference(WeaponPreference pref) {
        if (pref != WeaponPreference.NONE) {
            boolean serverForces = !this.serverAllowCheats
                    || (this.serverFeatures != null && this.serverFeatures.containsKey("weapon_switch")
                            && !this.serverFeatures.get("weapon_switch"));

            if (serverForces) {
                AntiCheatFeedbackManager.getInstance().onFeatureBlocked("weapon_switch",
                        BlockReason.SERVER_ENFORCEMENT);
                return;
            }
            this.lastActiveWeaponPreference = pref;
        }
        this.weaponPreference = pref;
    }

    public void toggleWeaponSwitch() {
        if (weaponPreference == WeaponPreference.NONE) {
            setWeaponPreference(lastActiveWeaponPreference);
        } else {
            setWeaponPreference(WeaponPreference.NONE);
        }
    }

    public boolean isWeaponSwitchEditable() {
        if (isNoCheatingMode())
            return false;
        if (!this.serverAllowCheats)
            return false;
        if (this.serverFeatures != null && this.serverFeatures.containsKey("weapon_switch")
                && !this.serverFeatures.get("weapon_switch"))
            return false;
        return true;
    }

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
        if (isNoCheatingMode())
            return false;
        if (!this.serverAllowCheats)
            return false;
        if (this.serverFeatures != null && this.serverFeatures.containsKey("tool_swap")
                && !this.serverFeatures.get("tool_swap"))
            return false;
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

    public List<String> getSilkTouchBlocks() {
        return silkTouchBlocks;
    }

    public void setSilkTouchBlocks(List<String> list) {
        this.silkTouchBlocks = list;
    }

    public List<String> getFortuneBlocks() {
        return fortuneBlocks;
    }

    public void setFortuneBlocks(List<String> list) {
        this.fortuneBlocks = list;
    }

    public List<String> getShearsBlocks() {
        return shearsBlocks;
    }

    public void setShearsBlocks(List<String> list) {
        this.shearsBlocks = list;
    }

    public List<String> getFastWeapons() {
        return fastWeapons;
    }

    public void setFastWeapons(List<String> list) {
        this.fastWeapons = list;
    }

    public List<String> getHeavyWeapons() {
        return heavyWeapons;
    }

    public void setHeavyWeapons(List<String> list) {
        this.heavyWeapons = list;
    }

    public List<String> getRangeWeapons() {
        return rangeWeapons;
    }

    public void setRangeWeapons(List<String> list) {
        this.rangeWeapons = list;
    }

    public List<Integer> getWeaponSwapHotbarSlots() {
        return weaponSwapHotbarSlots;
    }

    public void setWeaponSwapHotbarSlots(List<Integer> list) {
        this.weaponSwapHotbarSlots = list;
    }

    public List<String> getProtectedBlocks() {
        return protectedBlocks;
    }

    public void setProtectedBlocks(List<String> list) {
        this.protectedBlocks = list;
    }

    // --- SORTING GETTERS/SETTERS ---
    // Lock Hotbar methods removed

    public SortingAlgorithm getDefaultAlgorithm() {
        return defaultAlgorithm;
    }

    public void setDefaultAlgorithm(SortingAlgorithm val) {
        this.defaultAlgorithm = val;
    }

    public SortingLayout getDefaultLayout() {
        return defaultLayout;
    }

    public void setDefaultLayout(SortingLayout val) {
        this.defaultLayout = val;
    }

    public int getTickDelay() {
        return tickDelay;
    }

    public void setTickDelay(int val) {
        this.tickDelay = val;
    }

    public List<String> getBlacklistedInventories() {
        return blacklistedInventories;
    }

    public void setBlacklistedInventories(List<String> val) {
        this.blacklistedInventories = val;
    }

    public List<String> getBlacklistedItems() {
        return blacklistedItems;
    }

    public void setBlacklistedItems(List<String> val) {
        this.blacklistedItems = val;
    }

    // --- AUTO-REFILL CONFIG ---
    private boolean autoRefill = true;
    private boolean autoRefillContainers = true;
    private boolean autoRefillFood = true;
    private boolean autoRefillWeapon = true;
    private boolean autoRefillTool = true;
    private boolean autoRefillHarmful = false; // "Auto refill with harmfull food" - assumed default false safety

    // --- FAST LOOT CONFIG ---
    private boolean fastLootingInContainer = true;
    private boolean fastLootingLookingAt = true;

    // Getters and Setters for new configs
    public boolean isAutoRefill() {
        return autoRefill;
    }

    public void setAutoRefill(boolean v) {
        this.autoRefill = v;
    }

    public boolean isAutoRefillContainers() {
        return autoRefillContainers;
    }

    public void setAutoRefillContainers(boolean v) {
        this.autoRefillContainers = v;
    }

    public boolean isAutoRefillFood() {
        return autoRefillFood;
    }

    public void setAutoRefillFood(boolean v) {
        this.autoRefillFood = v;
    }

    public boolean isAutoRefillWeapon() {
        return autoRefillWeapon;
    }

    public void setAutoRefillWeapon(boolean v) {
        this.autoRefillWeapon = v;
    }

    public boolean isAutoRefillTool() {
        return autoRefillTool;
    }

    public void setAutoRefillTool(boolean v) {
        this.autoRefillTool = v;
    }

    public boolean isAutoRefillHarmful() {
        return autoRefillHarmful;
    }

    public void setAutoRefillHarmful(boolean v) {
        this.autoRefillHarmful = v;
    }

    public boolean isFastLootingInContainer() {
        return fastLootingInContainer;
    }

    public void setFastLootingInContainer(boolean v) {
        this.fastLootingInContainer = v;
    }

    public boolean isFastLootingLookingAt() {
        return fastLootingLookingAt;
    }

    public void setFastLootingLookingAt(boolean v) {
        this.fastLootingLookingAt = v;
    }

    // Persistent Locked Slots: ScreenClass -> Set of SlotIndices
    // private java.util.Map<String, java.util.Set<Integer>> savedLocks = new
    // java.util.HashMap<>(); // Deprecated

    // Global Player Inventory Locks (Indices 0-35)
    private java.util.Set<Integer> lockedPlayerSlots = new java.util.HashSet<>();

    public java.util.Set<Integer> getLockedPlayerSlots() {
        return lockedPlayerSlots;
    }

    public void setLockedPlayerSlots(java.util.Set<Integer> val) {
        this.lockedPlayerSlots = val;
    }
}