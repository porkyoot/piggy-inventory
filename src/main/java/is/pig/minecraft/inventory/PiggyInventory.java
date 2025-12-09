package is.pig.minecraft.inventory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import is.pig.minecraft.inventory.network.SyncConfigPayload;

public class PiggyInventory implements ModInitializer {
	public static final String MOD_ID = "piggy-inventory";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(SyncConfigPayload.TYPE, SyncConfigPayload.CODEC);
	}
}