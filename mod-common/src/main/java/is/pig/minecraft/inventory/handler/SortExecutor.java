package is.pig.minecraft.inventory.handler;

/**
 * @deprecated Migrated to RobustSortOrchestrator
 */
@Deprecated
public class SortExecutor {
    private static final SortExecutor INSTANCE = new SortExecutor();

    private SortExecutor() {}

    public static SortExecutor getInstance() {
        return INSTANCE;
    }
}
