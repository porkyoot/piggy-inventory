package is.pig.minecraft.inventory.config;

/**
 * Backwards-compatible wrapper used by inventory code expecting a `PiggyConfig` singleton.
 * Delegates implementation to `PiggyInventoryConfig` via inheritance.
 */
public class PiggyConfig extends PiggyInventoryConfig {

    private static PiggyConfig INSTANCE = new PiggyConfig();

    public static PiggyConfig getInstance() {
        return INSTANCE;
    }

    /**
     * Package-private so `ConfigPersistence` in the same package can set it when loading.
     */
    static void setInstance(PiggyConfig instance) {
        INSTANCE = instance;
    }
}
