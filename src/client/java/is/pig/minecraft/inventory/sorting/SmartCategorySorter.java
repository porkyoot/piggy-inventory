package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.lib.sorting.ISorter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmartCategorySorter implements ISorter {

    private static final Map<String, Integer> ID_WEIGHTS = new HashMap<>();

    static {
        // Mojang mapping paths for tabs might differ slightly in ID,
        // but typically they are like "food_and_drink", "tools", etc.
        ID_WEIGHTS.put("food_and_drink", 100);
        ID_WEIGHTS.put("tools", 200);
        ID_WEIGHTS.put("combat", 220);
        ID_WEIGHTS.put("building_blocks", 300);
        ID_WEIGHTS.put("natural_blocks", 400); // "natural" -> "natural_blocks" often
        ID_WEIGHTS.put("functional_blocks", 500);
        ID_WEIGHTS.put("redstone_blocks", 600);
        ID_WEIGHTS.put("ingredients", 900);
        ID_WEIGHTS.put("spawn_eggs", 950);
        ID_WEIGHTS.put("op_blocks", 999);
    }

    @Override
    public void sort(List<ItemStack> items) {
        items.sort(getComparator());
    }

    @Override
    public Comparator<ItemStack> getComparator() {
        return Comparator.comparingInt(SmartCategorySorter::getItemWeight)
                .thenComparing(stack -> stack.getItem().getName(stack).getString());
    }

    @Override
    public String getId() {
        return "smart_category";
    }

    @Override
    public String getName() {
        return "Category (Smart)";
    }

    public static int getItemWeight(ItemStack stack) {
        // iterate Generic/Builtin tabs
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab.contains(stack)) {
                ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
                if (id != null && ID_WEIGHTS.containsKey(id.getPath())) {
                    return ID_WEIGHTS.get(id.getPath());
                }
            }
        }
        return 1000;
    }
}
