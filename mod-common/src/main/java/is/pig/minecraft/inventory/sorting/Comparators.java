package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.inventory.api.IItemProperties;

import java.util.Comparator;

/**
 * Pure Java comparators for sorting.
 */
public class Comparators {
    public static Comparator<String> createIdComparator() {
        return String::compareTo;
    }

    public static Comparator<String> createDamageComparator(IItemProperties properties) {
        return Comparator.comparingInt(properties::getDamage);
    }

    public static Comparator<String> createToolComparator(IItemProperties properties) {
        return Comparator.comparing(properties::isTool);
    }
}
