package is.pig.minecraft.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import is.pig.minecraft.inventory.config.ConfigPersistence;
import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import is.pig.minecraft.lib.config.PiggyClientConfig;
import is.pig.minecraft.inventory.mvc.controller.InputController;
import is.pig.minecraft.inventory.sorting.InventorySorter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;

public class PiggyInventoryClient implements ClientModInitializer {

        public static final Logger LOGGER = LoggerFactory.getLogger("piggy-inventory");
        private final InputController controller = new InputController();

        public static KeyMapping sortKey;
        public static KeyMapping lockKey;

        @Override
        public void onInitializeClient() {
                LOGGER.info("Ehlo from Piggy Inventory!");

                // Register Sorting Key
                sortKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                                "Sort Inventory",
                                InputConstants.Type.KEYSYM,
                                GLFW.GLFW_KEY_R,
                                "Piggy Inventory"));

                // Register Lock Key
                lockKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                                "Lock Slot Modifier",
                                InputConstants.Type.KEYSYM,
                                GLFW.GLFW_KEY_LEFT_ALT,
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
                is.pig.minecraft.lib.network.SyncConfigPayload.registerPacket();

                PiggyClientConfig.getInstance().registerConfigSyncListener((allowCheats, features) -> {
                        PiggyInventoryConfig inv = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();
                        inv.serverAllowCheats = allowCheats;
                        inv.serverFeatures = features;
                        if (!allowCheats) {
                                inv.setToolSwapEnabled(false);
                                inv.setWeaponPreference(PiggyInventoryConfig.WeaponPreference.NONE);
                        }
                        if (features != null) {
                                if (features.containsKey("tool_swap") && !features.get("tool_swap"))
                                        inv.setToolSwapEnabled(false);
                                if (features.containsKey("weapon_switch") && !features.get("weapon_switch"))
                                        inv.setWeaponPreference(PiggyInventoryConfig.WeaponPreference.NONE);
                        }
                });

                // Input Handling (Backup/Global)
                ClientTickEvents.END_CLIENT_TICK.register(client -> {
                        if (sortKey.consumeClick()) {
                                handleSort(client);
                        }
                        is.pig.minecraft.inventory.handler.AutoRefillHandler.getInstance().onTick(client);
                });
        }

        public static void handleSort(Minecraft client) {
                if (client.screen instanceof AbstractContainerScreen<?> screen) {
                        try {
                                // Target Detection Logic
                                boolean sortPlayer = false;

                                // If mouse is over player inventory part, sort player.

                                // Calculate scaled mouse position for slot detection
                                double mouseX = client.mouseHandler.xpos() * client.getWindow().getGuiScaledWidth()
                                                / client.getWindow().getScreenWidth();
                                double mouseY = client.mouseHandler.ypos() * client.getWindow().getGuiScaledHeight()
                                                / client.getWindow().getScreenHeight();

                                net.minecraft.world.inventory.Slot slot = ((is.pig.minecraft.inventory.duck.IHandledScreen) screen)
                                                .piggy_getSlotUnderMouse(mouseX, mouseY);

                                if (slot != null) {
                                        // If hovering a slot, check if it is player inventory
                                        if (slot.container == client.player.getInventory()) {
                                                sortPlayer = true;
                                        }
                                } else {
                                        // Not hovering slot.
                                        // Default: Sort External if available.
                                        // If no external (e.g. Player Inventory Screen), sort Player.
                                        if (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
                                                sortPlayer = true;
                                        }
                                }

                                InventorySorter.sortInventory(screen, sortPlayer);
                        } catch (Exception e) {
                                LOGGER.error("Error sorting inventory", e);
                        }
                }
        }
}