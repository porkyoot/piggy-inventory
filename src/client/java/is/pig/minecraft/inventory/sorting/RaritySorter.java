package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.lib.sorting.ISorter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.Comparator;
import java.util.List;

public class RaritySorter implements ISorter {

    @Override
    public void sort(List<ItemStack> items) {
        items.sort(getComparator());
    }

    @Override
    public Comparator<ItemStack> getComparator() {
        return (stack1, stack2) -> {
            Rarity r1 = stack1.getRarity();
            Rarity r2 = stack2.getRarity();
            // Compare rarity ordinal (COMMON=0, UNCOMMON=1, etc.)
            // We want higher rarity first? Or lower? Standard seems to be high rarity first
            // or grouped.
            // Let's sort by ordinal ascending (Common -> Epic) or descending?
            // Usually "better" items at the top. Epic > Common.
            int cmp = r2.compareTo(r1);
            if (cmp != 0)
                return cmp;

            // Fallback to alphabetical
            return stack1.getHoverName().getString().compareTo(stack2.getHoverName().getString());
        };
    }

    @Override
    public String getId() {
        return "rarity";
    }

    @Override
    public String getName() {
        return "Rarity";
    }
}
