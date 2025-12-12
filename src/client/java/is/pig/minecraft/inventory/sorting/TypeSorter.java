package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.lib.sorting.ISorter;
import is.pig.minecraft.lib.util.NameAnalysisUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

public class TypeSorter implements ISorter {

    @Override
    public void sort(List<ItemStack> items) {
        items.sort(getComparator());
    }

    @Override
    public Comparator<ItemStack> getComparator() {
        return (stack1, stack2) -> {
            String id1 = BuiltInRegistries.ITEM.getKey(stack1.getItem()).toString();
            String id2 = BuiltInRegistries.ITEM.getKey(stack2.getItem()).toString();

            String type1 = NameAnalysisUtils.extractType(id1);
            String type2 = NameAnalysisUtils.extractType(id2);

            int cmp = type1.compareTo(type2);
            if (cmp != 0)
                return cmp;

            // Fallback to Material/Alphabetical
            return id1.compareTo(id2);
        };
    }

    @Override
    public String getId() {
        return "type";
    }

    @Override
    public String getName() {
        return "Type";
    }
}
