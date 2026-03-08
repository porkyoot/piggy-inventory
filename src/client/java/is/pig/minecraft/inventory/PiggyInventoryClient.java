package is.pig.minecraft.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import is.pig.minecraft.inventory.config.ConfigPersistence;

import is.pig.minecraft.inventory.mvc.controller.InputController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;

public class PiggyInventoryClient implements ClientModInitializer {

        public static final Logger LOGGER = LoggerFactory.getLogger("piggy-inventory");
        private final InputController controller = new InputController();

        public static KeyMapping sortKey;
        public static KeyMapping lockKey;
        public static KeyMapping lootMatchingKey;
        public static KeyMapping lootAllKey;

        @Override
        public void onInitializeClient() {
                LOGGER.info("Ehlo from Piggy Inventory!");

                // Register Sorting Key
                sortKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                                "Sort Inventory",
                                InputConstants.Type.KEYSYM,
                                GLFW.GLFW_KEY_R,
                                "Piggy Inventory"));


                // Register Lock Key (Default: Alt)
                lockKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                                "Lock Slot Modifier",
                                InputConstants.Type.KEYSYM,
                                GLFW.GLFW_KEY_LEFT_ALT,
                                "Piggy Inventory"));

                // Register Loot Matching Key (Default: Unbound / Shift)
                lootMatchingKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                                "Loot Matching Modifier",
                                InputConstants.Type.KEYSYM,
                                InputConstants.UNKNOWN.getValue(),
                                "Piggy Inventory"));

                // Register Loot All Key (Default: Unbound / Control)
                lootAllKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                                "Loot All Modifier",
                                InputConstants.Type.KEYSYM,
                                InputConstants.UNKNOWN.getValue(),
                                "Piggy Inventory"));

                // existing registrations...
                if (!is.pig.minecraft.lib.features.CheatFeatureRegistry.hasFeature("tool_swap")) {
                        is.pig.minecraft.lib.features.CheatFeatureRegistry.register(
                                        new is.pig.minecraft.lib.features.CheatFeature("tool_swap", "Tool Swap",
                                                        "Auto-swap tool", true));
                }
                if (!is.pig.minecraft.lib.features.CheatFeatureRegistry.hasFeature("weapon_switch")) {
                        is.pig.minecraft.lib.features.CheatFeatureRegistry.register(
                                        new is.pig.minecraft.lib.features.CheatFeature("weapon_switch", "Weapon Switch",
                                                        "Auto-swap weapon", true));
                }

                ConfigPersistence.load();
                controller.initialize();
                is.pig.minecraft.lib.ui.AntiCheatHudOverlay.register();

                // Listener registration moved to PiggyInventoryConfig self-registration.

                // Input Handling (Backup/Global)
                ClientTickEvents.END_CLIENT_TICK.register(client -> {
                        if (sortKey.consumeClick()) {
                                is.pig.minecraft.inventory.handler.SortHandler.getInstance().handleSort(client, null);
                        }
                        is.pig.minecraft.inventory.handler.AutoRefillHandler.getInstance().onTick(client);
                        is.pig.minecraft.inventory.handler.CraftingHandler.getInstance().onTick(client);
                        is.pig.minecraft.inventory.handler.QuickLootHandler.getInstance().onTick(client);
                });

                // HUD Overlay
                net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT
                                .register((guiGraphics, tickDelta) -> {
                                        is.pig.minecraft.inventory.handler.QuickLootHandler.getInstance()
                                                        .renderOverlay(guiGraphics);
                                });
        }
}