package is.pig.minecraft.inventory.sorting.layout;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class AbstractDepthLayout implements ISortingLayout {

    // The dynamic list of comparators defined by the user/config
    protected final List<Comparator<ItemStack>> comparators;

    public AbstractDepthLayout(List<Comparator<ItemStack>> comparators) {
        this.comparators = comparators != null ? comparators : new ArrayList<>();
    }

    @Override
    public List<ItemStack> layout(List<ItemStack> items, List<net.minecraft.world.inventory.Slot> availableSlots) {
        if (items.isEmpty()) {
            return fillEmpty(new ArrayList<>(), availableSlots.size());
        }

        // Start with every unique type in its own group (depth = max)
        List<Group> groups = new ArrayList<>();
        Group currentGroup = new Group();
        ItemStack previous = null;

        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                if (previous != null && differsAtDepth(previous, item, comparators.size())) {
                    groups.add(currentGroup);
                    currentGroup = new Group();
                }
                currentGroup.items.add(item);
                previous = item;
            }
        }
        if (!currentGroup.items.isEmpty()) {
            groups.add(currentGroup);
        }

        // Iteratively merge the most similar groups until it fits!
        while (groups.size() > 1 && !fitsLayout(groups, availableSlots, 0)) {
            // Find the adjacent pair with the HIGHEST differenceDepth (meaning they differ
            // only at a deep/unimportant comparator layer)
            int bestIndex = 0;
            int maxMatchDepth = -1;

            for (int i = 0; i < groups.size() - 1; i++) {
                int depth = findMatchDepth(groups.get(i).getRepresentative(), groups.get(i + 1).getRepresentative());
                if (depth > maxMatchDepth) {
                    maxMatchDepth = depth;
                    bestIndex = i;
                }
            }

            // Merge the two most similar groups
            Group a = groups.get(bestIndex);
            Group b = groups.remove(bestIndex + 1);
            a.items.addAll(b.items);
        }

        // Find the maximum padding we can squeeze in!
        int padding = 0;
        if (groups.size() > 1) {
            while (fitsLayout(groups, availableSlots, padding)) {
                padding++;
            }
            // padding overflowed by 1, so step back
            padding = Math.max(0, padding - 1);
        }

        return emitGroups(groups, availableSlots, padding);
    }

    /**
     * Calculates how many comparator levels match between two items.
     * Higher score means they are MORE similar.
     */
    private int findMatchDepth(ItemStack a, ItemStack b) {
        for (int i = 0; i < comparators.size(); i++) {
            if (comparators.get(i).compare(a, b) != 0) {
                return i;
            }
        }
        return comparators.size();
    }

    /**
     * Checks if two items differ at or before the given depth limit.
     */
    protected boolean differsAtDepth(ItemStack a, ItemStack b, int depthLimit) {
        int limit = Math.min(depthLimit, comparators.size());
        for (int i = 0; i < limit; i++) {
            if (comparators.get(i).compare(a, b) != 0) {
                return true;
            }
        }
        return false;
    }

    protected abstract boolean fitsLayout(List<Group> groups, List<net.minecraft.world.inventory.Slot> availableSlots, int padding);

    protected abstract List<ItemStack> emitGroups(List<Group> groups, List<net.minecraft.world.inventory.Slot> availableSlots, int padding);

    protected List<ItemStack> fillEmpty(List<ItemStack> list, int targetSize) {
        while (list.size() < targetSize) {
            list.add(ItemStack.EMPTY);
        }
        return list;
    }

    public static class Group {
        public List<ItemStack> items = new ArrayList<>();

        public ItemStack getRepresentative() {
            return !items.isEmpty() ? items.get(0) : ItemStack.EMPTY;
        }

        public int size() {
            return items.size();
        }
    }
}