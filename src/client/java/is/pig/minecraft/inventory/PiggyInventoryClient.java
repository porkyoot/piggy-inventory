package is.pig.minecraft.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import is.pig.minecraft.inventory.config.ConfigPersistence;
import is.pig.minecraft.inventory.mvc.controller.InputController;
import net.fabricmc.api.ClientModInitializer;

public class PiggyInventoryClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("piggy-inventory");

    // The controller manages input (keyboard, mouse, tick)
    private final InputController controller = new InputController();

    @Override
    public void onInitializeClient() {

        LOGGER.info("Ehlo from Piggy Inventory!");

        // 0. Register features
        is.pig.minecraft.lib.features.CheatFeatureRegistry.register(
                new is.pig.minecraft.lib.features.CheatFeature(
                        "tool_swap",
                        "Tool Swap",
                        "Automatically swap to the best tool for the targeted block",
                        true));

        // 1. Load configuration
        ConfigPersistence.load();

        // 2. Initialize controller
        controller.initialize();

        // 3. Register Config Sync Receiver
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                is.pig.minecraft.lib.network.SyncConfigPayload.TYPE,
                (payload, context) -> {
                    is.pig.minecraft.inventory.config.PiggyConfig config = is.pig.minecraft.inventory.config.PiggyConfig
                            .getInstance();
                    config.serverAllowCheats = payload.allowCheats();
                    config.serverFeatures = payload.features();
                    PiggyInventoryClient.LOGGER.info("Received server config: allowCheats=" + payload.allowCheats()
                            + ", features=" + payload.features());
                });
    }
}