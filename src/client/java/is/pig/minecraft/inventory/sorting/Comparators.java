package is.pig.minecraft.inventory.sorting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CreativeModeTab;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Comparators {

    public static final Comparator<ItemStack> NAME = Comparator.comparing(stack -> stack.getHoverName().getString());

    public static final Comparator<ItemStack> RARITY = Comparator.comparingInt(stack -> stack.getRarity().ordinal());

    public static final Comparator<ItemStack> MOD = Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());

    public static final Comparator<ItemStack> COLOR = Comparator.comparingInt(stack -> {
        String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        for (DyeColor color : DyeColor.values()) {
            if (name.contains(color.getName())) {
                return color.getId();
            }
        }
        return Integer.MAX_VALUE; // fallback for no color
    });

    private static Map<Item, Integer> CREATIVE_ORDER_MAP = null;

    private static void initCreativeOrder() {
        if (CREATIVE_ORDER_MAP != null) return;
        CREATIVE_ORDER_MAP = new HashMap<>();
        int index = 0;
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (tab.getType() == CreativeModeTab.Type.SEARCH) continue;
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
        if (name == null || name.isEmpty()) return "";
        return name.substring(0, 1).toUpperCase();
    });

    public static final Comparator<ItemStack> ID = Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());

    public static final Comparator<ItemStack> AMOUNT = Comparator.comparingInt(ItemStack::getCount).reversed();

    /**
     * Named sort comparators that can be configured by the user.
     */
    public enum SortComparator {
        CATEGORY("Category", CREATIVE_MENU),
        MOD("Mod", Comparators.MOD),
        COLOR("Color", Comparators.COLOR),
        LETTER("Letter", Comparators.LETTER),
        RARITY("Rarity", Comparators.RARITY.reversed()),
        NAME("Name", Comparators.NAME),
        ID("Registry ID", Comparators.ID),
        AMOUNT("Amount", Comparators.AMOUNT);

        public final String displayName;
        public final Comparator<ItemStack> comparator;

        SortComparator(String displayName, Comparator<ItemStack> comparator) {
            this.displayName = displayName;
            this.comparator = comparator;
        }
    }

    /**
     * Builds a chained comparator hierarchy from an ordered list of SortComparator names.
     * Unknown names are silently ignored.
     */
    public static Comparator<ItemStack> buildHierarchy(List<String> order) {
        Comparator<ItemStack> result = null;
        for (String name : order) {
            try {
                SortComparator sc = SortComparator.valueOf(name);
                result = result == null ? sc.comparator : result.thenComparing(sc.comparator);
            } catch (IllegalArgumentException ignored) {}
        }
        return result != null ? result : DEFAULT_HIERARCHY;
    }

    // Grouping Hierarchy: Category -> Mod -> Color -> Letter -> Rarity -> Name -> ID -> Amount
    public static final Comparator<ItemStack> DEFAULT_HIERARCHY = CREATIVE_MENU
            .thenComparing(MOD)
            .thenComparing(COLOR)
            .thenComparing(LETTER)
            .thenComparing(RARITY.reversed())
            .thenComparing(NAME)
            .thenComparing(ID)
            .thenComparing(AMOUNT);
}
