package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.lib.sorting.ISorter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

public class TagSorter implements ISorter {

    private static final List<String> TAG_PRIORITY = List.of(
            "c:tools",
            "c:weapons",
            "c:armors",
            "c:food",
            "c:potions",
            "c:ores",
            "c:ingots",
            "c:gems",
            "c:storage_blocks",
            "minecraft:logs",
            "minecraft:planks",
            "minecraft:leaves",
            "minecraft:wool",
            "minecraft:saplings",
            "minecraft:flowers",
            "minecraft:crops",
            "minecraft:doors",
            "minecraft:trapdoors",
            "minecraft:signs",
            "minecraft:boats");

    @Override
    public void sort(List<ItemStack> items) {
        items.sort(getComparator());
    }

    @Override
    public Comparator<ItemStack> getComparator() {
        return (stack1, stack2) -> {
            int p1 = getPriority(stack1);
            int p2 = getPriority(stack2);

            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }

            // Fallback
            return stack1.getHoverName().getString().compareTo(stack2.getHoverName().getString());
        };
    }

    private int getPriority(ItemStack stack) {
        // Lower index is higher priority
        // If no match, return MAX_INT

        // Native tags check
        // We iterate our string list, convert to TagKey, and check

        for (int i = 0; i < TAG_PRIORITY.size(); i++) {
            String tagStr = TAG_PRIORITY.get(i);
            ResourceLocation loc = ResourceLocation.tryParse(tagStr);
            if (loc == null)
                continue;
            TagKey<Item> tagKey = TagKey.create(BuiltInRegistries.ITEM.key(), loc);
            if (stack.is(tagKey)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public String getId() {
        return "tag_priority";
    }

    @Override
    public String getName() {
        return "Tag Priority";
    }
}
