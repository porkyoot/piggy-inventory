package is.pig.minecraft.inventory;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PiggyInventory implements ModInitializer {
	public static final String MOD_ID = "piggy-inventory";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Register the tool_swap feature (server-side)
		// This ensures the feature appears in the registry when the mod is on the
		// server
		is.pig.minecraft.lib.features.CheatFeatureRegistry.register(
				new is.pig.minecraft.lib.features.CheatFeature(
						"tool_swap",
						"Tool Swap",
						"Automatically swap to the best tool for the targeted block",
						true));

		LOGGER.info("Piggy Inventory initialized (server-side)");
	}
}