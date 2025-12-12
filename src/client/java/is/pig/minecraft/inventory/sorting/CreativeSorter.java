package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.lib.sorting.ISorter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

public class CreativeSorter implements ISorter {

    @Override
    public void sort(List<ItemStack> items) {
        items.sort(getComparator());
    }

    @Override
    public Comparator<ItemStack> getComparator() {
        // Sort by Integer Registry ID which corresponds to Registration Order (Creative
        // Order proxy)
        return Comparator.comparingInt(stack -> BuiltInRegistries.ITEM.getId(stack.getItem()));
    }

    @Override
    public String getId() {
        return "creative";
    }

    @Override
    public String getName() {
        return "Creative Order";
    }
}
