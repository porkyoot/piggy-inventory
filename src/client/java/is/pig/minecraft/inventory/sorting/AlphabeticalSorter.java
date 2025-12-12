package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.lib.sorting.ISorter;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

public class AlphabeticalSorter implements ISorter {

    @Override
    public void sort(List<ItemStack> items) {
        items.sort(getComparator());
    }

    @Override
    public Comparator<ItemStack> getComparator() {
        return Comparator.comparing(stack -> stack.getItem().getName(stack).getString());
    }

    @Override
    public String getId() {
        return "alphabetical";
    }

    @Override
    public String getName() {
        return "Alphabetical";
    }
}
