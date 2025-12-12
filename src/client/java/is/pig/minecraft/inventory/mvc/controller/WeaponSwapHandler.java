package is.pig.minecraft.inventory.mvc.controller;

import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public class WeaponSwapHandler {

    /**
     * Called when the player is about to attack an entity.
     * 
     * @return true if we should probably cancel? No, validation logic.
     *         Actually we just want to perform the swap.
     */
    public void onAttack(Minecraft client, Entity target) {
        PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();

        if (!config.isFeatureWeaponSwitchEnabled()) {
            return;
        }

        if (client.player == null || client.level == null) {
            return;
        }

        int currentSlot = client.player.getInventory().selected;
        ItemStack currentStack = client.player.getMainHandItem();

        int bestSlot = currentSlot;
        double bestScore = getWeaponScore(client, currentStack, target, config);

        // Scan full inventory (0-35)
        for (int i = 0; i < 36; i++) {
            if (i == currentSlot)
                continue;

            ItemStack stack = client.player.getInventory().getItem(i);
            double score = getWeaponScore(client, stack, target, config);

            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        if (bestSlot != currentSlot) {
            swapToSlot(client, currentSlot, bestSlot, config.getWeaponSwapHotbarSlots());
        }
    }

    private void swapToSlot(Minecraft client, int currentSlot, int bestSlot, java.util.List<Integer> allowedSlots) {
        if (bestSlot < 9) {
            // Simple hotbar swap
            client.player.getInventory().selected = bestSlot;
            if (client.getConnection() != null) {
                client.getConnection().send(new ServerboundSetCarriedItemPacket(bestSlot));
            }
        } else {
            // Inventory swap logic
            if (allowedSlots == null || allowedSlots.isEmpty()) {
                return;
            }

            int targetSlot;
            if (allowedSlots.contains(currentSlot)) {
                targetSlot = currentSlot;
            } else {
                targetSlot = allowedSlots.get(0);
                if (targetSlot < 0 || targetSlot > 8)
                    targetSlot = 0;
            }

            client.gameMode.handleInventoryMouseClick(
                    client.player.inventoryMenu.containerId,
                    bestSlot,
                    targetSlot,
                    net.minecraft.world.inventory.ClickType.SWAP,
                    client.player);

            if (client.player.getInventory().selected != targetSlot) {
                client.player.getInventory().selected = targetSlot;
                if (client.getConnection() != null) {
                    client.getConnection().send(new ServerboundSetCarriedItemPacket(targetSlot));
                }
            }
        }
    }

    private double getWeaponScore(Minecraft client, ItemStack stack, Entity target, PiggyInventoryConfig config) {
        if (stack.isEmpty())
            return 0;

        double damage = 1.0;
        double speed = 4.0; // Base speed (fist) is roughly 4.0 but attack cooldown logic is tricky.
        // Actually base player attack speed is 4.0.
        // Sword modifies it by -2.4 -> 1.6
        // Axe modifies it by -3.0 -> 1.0 (approx, varies by material)
        // Trident -2.9 -> 1.1
        // We will approximate speed based on item class since dynamic attribute lookup
        // is complex here.

        if (stack.getItem() instanceof SwordItem sword) {
            damage = 3.0 + 4.0; // Placeholder base, ideally use explicit material checks if needed
            // Simple material tiers check for slightly better accuracy:
            String name = stack.getItem().toString();
            if (name.contains("netherite"))
                damage = 8.0;
            else if (name.contains("diamond"))
                damage = 7.0;
            else if (name.contains("iron"))
                damage = 6.0;
            else if (name.contains("stone"))
                damage = 5.0;
            else if (name.contains("golden"))
                damage = 4.0;
            else
                damage = 4.0; // Wood/Other

            speed = 1.6;
        } else if (stack.getItem() instanceof AxeItem axe) {
            String name = stack.getItem().toString();
            if (name.contains("netherite")) {
                damage = 10.0;
                speed = 1.0;
            } else if (name.contains("diamond")) {
                damage = 9.0;
                speed = 1.0;
            } else if (name.contains("iron")) {
                damage = 9.0;
                speed = 0.9;
            } // Iron axe is slower? Actually standard assumes 1.0 usually or 0.9
            else if (name.contains("stone")) {
                damage = 9.0;
                speed = 0.8;
            } else if (name.contains("golden")) {
                damage = 7.0;
                speed = 1.0;
            } else {
                damage = 7.0;
                speed = 0.8;
            } // Wood
        } else if (stack.getItem() instanceof TridentItem) {
            damage = 9.0;
            speed = 1.1;
        } else if (stack.getItem() instanceof MaceItem) {
            damage = 6.0;
            speed = 0.6;
        } else if (stack.getItem() instanceof net.minecraft.world.item.PickaxeItem) {
            damage = 1.0 + 2.0; // Very base
            speed = 1.2;
        }

        // Enchantment bonuses
        int sharpness = getEnchantmentLevel(client, stack, Enchantments.SHARPNESS);
        if (sharpness > 0)
            damage += sharpness * 0.5;

        int smite = getEnchantmentLevel(client, stack, Enchantments.SMITE);
        if (smite > 0 && target instanceof LivingEntity living && living.isInvertedHealAndHarm()) {
            damage += smite * 2.5;
        }

        int bane = getEnchantmentLevel(client, stack, Enchantments.BANE_OF_ARTHROPODS);
        if (bane > 0) {
            boolean isArthropod = target instanceof net.minecraft.world.entity.monster.Spider
                    || target instanceof net.minecraft.world.entity.monster.CaveSpider
                    || target instanceof net.minecraft.world.entity.monster.Silverfish
                    || target instanceof net.minecraft.world.entity.monster.Endermite
                    || target instanceof net.minecraft.world.entity.animal.Bee;
            if (isArthropod) {
                damage += bane * 2.5;
            }
        }

        int impaling = getEnchantmentLevel(client, stack, Enchantments.IMPALING);
        if (impaling > 0) {
            boolean isAquatic = target instanceof net.minecraft.world.entity.animal.WaterAnimal
                    || target instanceof net.minecraft.world.entity.monster.Guardian
                    || target instanceof net.minecraft.world.entity.animal.Turtle;
            if (isAquatic) {
                damage += impaling * 2.5;
            }
        }

        int fireAspect = getEnchantmentLevel(client, stack, Enchantments.FIRE_ASPECT);
        if (fireAspect > 0) {
            damage += fireAspect * 1.5;
        }

        int sweeping = getEnchantmentLevel(client, stack, Enchantments.SWEEPING_EDGE);
        if (sweeping > 0) {
            damage += sweeping * 1.0;
        }

        // Calculate DPS
        double dps = damage * speed;

        boolean isBoat = target instanceof Boat || target instanceof AbstractMinecart || target instanceof ChestBoat;
        boolean isShielded = (target instanceof LivingEntity living) && living.isBlocking();
        boolean isFalling = client.player.fallDistance > 2.0f;

        // 1. Critical Overrides
        if (isFalling && stack.getItem() instanceof MaceItem)
            return 10000.0;
        if ((isBoat || isShielded) && stack.getItem() instanceof AxeItem)
            return 10000.0;

        PiggyInventoryConfig.WeaponPreference pref = config.getWeaponPreference();

        // 2. Range Preference
        if (pref == PiggyInventoryConfig.WeaponPreference.RANGE) {
            int index = getPriorityIndex(stack, config.getRangeWeapons());
            if (index != -1)
                return 5000.0 + (100 - index);

            // Only fall back to Trident, ignore Bow/Crossbow for melee
            if (stack.getItem() instanceof TridentItem)
                return 4000.0;

            return damage; // Fallback to raw damage if not a range weapon
        }

        // 3. Speed Preference (DPS)
        if (pref == PiggyInventoryConfig.WeaponPreference.SPEED) {
            int index = getPriorityIndex(stack, config.getFastWeapons());
            if (index != -1)
                return 2000.0 + (100 - index) + dps;

            // Otherwise purely prioritize DPS
            return dps;
        }

        // 4. Damage Preference (Single Hit)
        if (pref == PiggyInventoryConfig.WeaponPreference.DAMAGE) {
            int index = getPriorityIndex(stack, config.getHeavyWeapons());
            if (index != -1)
                return 2000.0 + (100 - index) + damage;

            // Otherwise prioritize raw Damage
            return damage;
        }

        // Default
        return damage;
    }

    private int getPriorityIndex(ItemStack stack, java.util.List<String> list) {
        if (list == null || list.isEmpty() || stack.isEmpty())
            return -1;

        net.minecraft.resources.ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem());
        String id = key.toString(); // e.g. minecraft:diamond_sword

        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private int getEnchantmentLevel(Minecraft client, ItemStack stack,
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key) {
        if (client.level == null)
            return 0;
        try {
            var registry = client.level.registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            var enchant = registry.getOrThrow(key);
            return EnchantmentHelper.getItemEnchantmentLevel(enchant, stack);
        } catch (Exception e) {
            return 0;
        }
    }
}
