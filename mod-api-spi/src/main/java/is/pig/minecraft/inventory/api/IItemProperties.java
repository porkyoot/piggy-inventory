package is.pig.minecraft.inventory.api;

/**
 * Interface to query generic item metadata.
 * Pure Java interface.
 */
public interface IItemProperties {
    boolean isTool(String identifier);
    int getDamage(String identifier);
    int getEnchantmentLevel(String identifier, String enchantName);
}
