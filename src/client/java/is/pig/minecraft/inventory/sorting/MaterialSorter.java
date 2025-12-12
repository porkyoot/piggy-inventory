package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.lib.sorting.ISorter;
import is.pig.minecraft.lib.util.NameAnalysisUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

public class MaterialSorter implements ISorter {

    @Override
    public void sort(List<ItemStack> items) {
        items.sort(getComparator());
    }

    @Override
    public Comparator<ItemStack> getComparator() {
        return (stack1, stack2) -> {
            String id1 = BuiltInRegistries.ITEM.getKey(stack1.getItem()).toString();
            String id2 = BuiltInRegistries.ITEM.getKey(stack2.getItem()).toString();

            String mat1 = NameAnalysisUtils.extractMaterial(id1);
            String mat2 = NameAnalysisUtils.extractMaterial(id2);

            int cmp = mat1.compareTo(mat2);
            if (cmp != 0)
                return cmp;

            // Fallback to Type/Alphabetical
            return id1.compareTo(id2);
        };
    }

    @Override
    public String getId() {
        return "material";
    }

    @Override
    public String getName() {
        return "Material";
    }
}
