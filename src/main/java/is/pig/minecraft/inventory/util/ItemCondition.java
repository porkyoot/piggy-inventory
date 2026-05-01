package is.pig.minecraft.inventory.util;

/**
 * Functional interface for matching items against specific criteria.
 */
@FunctionalInterface
public interface ItemCondition {
    /**
     * @param stack The agnostic item stack object.
     * @return true if the item matches the condition.
     */
    boolean matches(Object stack);
}
