package is.pig.minecraft.inventory;

import is.pig.minecraft.inventory.config.ConfigPersistence;
import is.pig.minecraft.inventory.telemetry.InventoryHistoryManager;
import is.pig.minecraft.inventory.telemetry.SortingCycleEvent;
import is.pig.minecraft.lib.util.telemetry.EventTranslatorRegistry;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PiggyInventoryClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("piggy-inventory");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Piggy Inventory...");

        // Initialize Telemetry & History
        InventoryHistoryManager.init();

        // Load Config
        ConfigPersistence.load();

        // Register telemetry translators
        EventTranslatorRegistry.getInstance().register(
                SortingCycleEvent.class,
                (event, i18n) -> {
                    var e = (SortingCycleEvent) event;
                    return i18n.translate("piggy.inventory.telemetry.sort_cycle",
                            e.containerId(), e.isCycleResolution(), e.moveCount());
                });

        // The FeatureOrchestrator in piggy-lib will discover InventoryFeatureProvider
        // and call its init() and onTick() methods.
    }
}