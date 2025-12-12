package is.pig.minecraft.inventory;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PiggyInventory implements ModInitializer {
	public static final String MOD_ID = "piggy-inventory";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Piggy Inventory initialized (server-side)");
	}
}