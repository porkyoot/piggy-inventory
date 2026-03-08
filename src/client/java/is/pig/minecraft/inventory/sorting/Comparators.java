package is.pig.minecraft.inventory.sorting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CreativeModeTab;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import is.pig.minecraft.lib.util.ColorHelper;

public class Comparators {

    public static final Comparator<ItemStack> NAME = Comparator.comparing(stack -> stack.getHoverName().getString());

    public static final Comparator<ItemStack> RARITY = Comparator.comparingInt(stack -> stack.getRarity().ordinal());

    public static final Comparator<ItemStack> MOD = Comparator
            .comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());

    private static final Map<Item, Float> ITEM_HUE_CACHE = new ConcurrentHashMap<>();

    public static float getHue(ItemStack stack) {
        if (stack.isEmpty()) return 0.0f;
        Item item = stack.getItem();
        
        return ITEM_HUE_CACHE.computeIfAbsent(item, k -> {
            int color = ColorHelper.getDominantColor(stack);
            if (color == 0 || color == 0xFFFFFF) {
                // Return a default or a very specific fallback for colorless/white items
                return -1.0f; 
            }
            float[] hsb = ColorHelper.colorToHSB(color);
            return hsb[0]; // Hue is the first element, ranging from 0.0 to 1.0
        });
    }

    // High precision sort for the continuous seamless gradient
    public static final Comparator<ItemStack> COLOR = Comparator.comparingDouble(Comparators::getHue);

    // Grouping sort buckets hues into 12 chunks (Math.round(hue * 12)). Ensures items with visually similar
    // colors are kept together by the dynamically spacing layout algorithm!
    public static final Comparator<ItemStack> COLOR_GROUPING = Comparator.comparingInt(stack -> {
        float hue = getHue(stack);
        if (hue < 0) return -1;
        return Math.round(hue * 12.0f);
    });

    public static final Comparator<ItemStack> TAG = Comparator.comparing(stack -> {
        return stack.getTags()
                .map(tag -> tag.location().toString())
                .sorted()
                .findFirst()
                .orElse("\uFFFF");
    });

    private static final String[] MATERIAL_AFFIXES = {
            "raw_", "deepslate_", "stripped_", "weathered_", "exposed_", "oxidized_", "waxed_", "cut_", "chiseled_", "polished_", "smooth_", "cracked_",
            "_block", "_ingot", "_nugget", "_ore", "_dust", "_scrap",
            "_pickaxe", "_sword", "_axe", "_shovel", "_hoe",
            "_helmet", "_chestplate", "_leggings", "_boots",
            "_log", "_wood", "_planks", "_stairs", "_slab",
            "_door", "_trapdoor", "_fence_gate", "_fence",
            "_pressure_plate", "_button", "_chest_boat", "_boat",
            "_leaves", "_sapling", "_hanging_sign", "_sign", "_wall",
            "block_of_"
    };

    public static String getMaterial(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        path = path.replace("golden_", "gold_");
        path = path.replace("wooden_", "wood_");
        if (path.equals("lapis_lazuli")) {
            path = "lapis";
        }
        
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String affix : MATERIAL_AFFIXES) {
                if (path.startsWith(affix) && affix.endsWith("_")) {
                    path = path.substring(affix.length());
                    changed = true;
                } else if (path.endsWith(affix) && affix.startsWith("_")) {
                    path = path.substring(0, path.length() - affix.length());
                    changed = true;
                }
            }
        }
        return path;
    }

    public static final Comparator<ItemStack> MATERIAL = Comparator.comparing(Comparators::getMaterial);

    private static Map<Item, Integer> CREATIVE_ORDER_MAP = null;

    private static void initCreativeOrder() {
        if (CREATIVE_ORDER_MAP != null)
            return;
        CREATIVE_ORDER_MAP = new HashMap<>();
        int index = 0;
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (tab.getType() == CreativeModeTab.Type.SEARCH)
                continue;
            for (ItemStack stack : tab.getDisplayItems()) {
                if (!CREATIVE_ORDER_MAP.containsKey(stack.getItem())) {
                    CREATIVE_ORDER_MAP.put(stack.getItem(), index++);
                }
            }
        }
    }

    public static final Comparator<ItemStack> CREATIVE_MENU = Comparator.comparingInt(stack -> {
        try {
            initCreativeOrder();
            return CREATIVE_ORDER_MAP.getOrDefault(stack.getItem(), Integer.MAX_VALUE);
        } catch (Exception e) {
            return BuiltInRegistries.ITEM.getId(stack.getItem()); // Fallback to raw ID
        }
    });

    public static final Comparator<ItemStack> LETTER = Comparator.comparing(stack -> {
        String name = stack.getHoverName().getString();
        if (name == null || name.isEmpty())
            return "";
        return name.substring(0, 1).toUpperCase();
    });

    public static final Comparator<ItemStack> ID = Comparator
            .comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());

    public static final Comparator<ItemStack> AMOUNT = Comparator.comparingInt(ItemStack::getCount).reversed();

    /**
     * Named sort comparators that can be configured by the user.
     */
    public enum SortComparator {
        CATEGORY("Category", CREATIVE_MENU, CREATIVE_MENU),
        MOD("Mod", Comparators.MOD, Comparators.MOD),
        MATERIAL("Material", Comparators.MATERIAL, Comparators.MATERIAL),
        TAG("Tag", Comparators.TAG, Comparators.TAG),
        COLOR("Color", Comparators.COLOR, Comparators.COLOR_GROUPING),
        RARITY("Rarity", Comparators.RARITY.reversed(), Comparators.RARITY.reversed()),
        NAME("Name", Comparators.NAME, Comparators.LETTER),
        ID("Registry ID", Comparators.ID, Comparators.ID),
        AMOUNT("Amount", Comparators.AMOUNT, Comparators.AMOUNT);

        public final String displayName;
        public final Comparator<ItemStack> comparator;
        public final Comparator<ItemStack> groupingComparator;

        SortComparator(String displayName, Comparator<ItemStack> comparator, Comparator<ItemStack> groupingComparator) {
            this.displayName = displayName;
            this.comparator = comparator;
            this.groupingComparator = groupingComparator;
        }
    }

    /**
     * Builds a chained comparator hierarchy from an ordered list of SortComparator
     * names.
     * Unknown names are silently ignored.
     */
    public static Comparator<ItemStack> buildHierarchy(List<String> order) {
        Comparator<ItemStack> result = null;
        for (String name : order) {
            try {
                SortComparator sc = SortComparator.valueOf(name);
                result = result == null ? sc.comparator : result.thenComparing(sc.comparator);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result != null ? result : DEFAULT_HIERARCHY;
    }

    /**
     * Builds a list of comparators for layout depth grouping.
     */
    public static List<Comparator<ItemStack>> buildComparatorList(List<String> order) {
        List<Comparator<ItemStack>> result = new java.util.ArrayList<>();
        for (String name : order) {
            try {
                SortComparator sc = SortComparator.valueOf(name);
                result.add(sc.groupingComparator);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (result.isEmpty()) {
            result.add(CREATIVE_MENU);
            result.add(MOD);
            result.add(MATERIAL);
            result.add(TAG);
            result.add(COLOR);
            result.add(RARITY.reversed());
            result.add(LETTER);
            result.add(ID);
            result.add(AMOUNT);
        }
        return result;
    }

    // Grouping Hierarchy: Category -> Mod -> Material -> Tag -> Color -> Rarity -> Name ->
    // ID -> Amount
    public static final Comparator<ItemStack> DEFAULT_HIERARCHY = CREATIVE_MENU
            .thenComparing(TAG)
            .thenComparing(MOD)
            .thenComparing(MATERIAL)
            .thenComparing(COLOR)
            .thenComparing(RARITY.reversed())
            .thenComparing(NAME)
            .thenComparing(ID)
            .thenComparing(AMOUNT);
}
