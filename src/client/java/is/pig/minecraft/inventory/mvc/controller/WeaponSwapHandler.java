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

        for (int i = 0; i < 9; i++) { // Hotbar only for weapons usually
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
            swapToSlot(client, bestSlot);
        }
    }

    private void swapToSlot(Minecraft client, int bestSlot) {
        client.player.getInventory().selected = bestSlot;
        if (client.getConnection() != null) {
            client.getConnection().send(new ServerboundSetCarriedItemPacket(bestSlot));
        }
    }

    private double getWeaponScore(Minecraft client, ItemStack stack, Entity target, PiggyInventoryConfig config) {
        if (stack.isEmpty())
            return 0;

        double damage = 1.0;
        double speed = 4.0; // Base speed

        if (stack.getItem() instanceof SwordItem sword) {
            damage = 3.0 + 4.0; // Assume diamond level approx for scoring if unknown
            // Or better:
            // getDamage() is not public usually.
            // Let's use hardcoded approximations for scoring:
            // We can check material via simple name check if needed, or just prioritize
            // class.
            damage = 7.0;
            speed = 1.6;
        } else if (stack.getItem() instanceof AxeItem axe) {
            damage = 9.0;
            speed = 1.0; // Variable but slow
        } else if (stack.getItem() instanceof TridentItem) {
            damage = 9.0;
            speed = 1.1;
        } else if (stack.getItem() instanceof MaceItem) {
            damage = 6.0;
            speed = 0.6;
        } else if (stack.getItem() instanceof net.minecraft.world.item.PickaxeItem) {
            damage = 3.0; // Low
            speed = 1.2;
        }

        // Enchantment bonuses (Simplified)
        // If we can't easily access Enchantments, we might skip detailed enchant logic
        // or try catch loop.
        // Assuming we can use EnchantmentHelper.getItemEnchantmentLevel logic from
        // ToolSwapHandler mechanism
        // But ToolSwapHandler uses ResourceKey.
        // We will skip fine-grained enchantment logic for now unless critical.
        // The user asked "Take into account the enchant depending on the enemy".
        // We really should try.

        // Try getting Sharpness level
        int sharpness = getEnchantmentLevel(client, stack, Enchantments.SHARPNESS);
        if (sharpness > 0)
            damage += sharpness * 0.5; // 1.21 math might differ

        int smite = getEnchantmentLevel(client, stack, Enchantments.SMITE);
        if (smite > 0 && target instanceof LivingEntity living && living.isInvertedHealAndHarm()) { // Undead check
            damage += smite * 2.5;
        }

        boolean isBoat = target instanceof Boat || target instanceof AbstractMinecart || target instanceof ChestBoat;
        boolean isShielded = (target instanceof LivingEntity living) && living.isBlocking();
        boolean isFalling = client.player.fallDistance > 2.0f;

        // 1. Critical Overrides

        // Mace falling attack
        if (isFalling && stack.getItem() instanceof MaceItem) {
            return 10000.0;
        }

        // Axe vs Boat/Minecart/Shield
        if ((isBoat || isShielded) && stack.getItem() instanceof AxeItem) {
            return 10000.0;
        }

        PiggyInventoryConfig.WeaponPreference pref = config.getWeaponPreference();

        if (pref == PiggyInventoryConfig.WeaponPreference.RANGE) {
            int index = getPriorityIndex(stack, config.getRangeWeapons());
            if (index != -1) {
                // Return high score based on priority (lower index = better).
                // Max range size usually small. 5000 base.
                return 5000.0 + (100 - index);
            }
            if (stack.getItem() instanceof TridentItem)
                return 4000.0;
            return damage; // Fallback
        }

        if (pref == PiggyInventoryConfig.WeaponPreference.SPEED) {
            // Check user priority list first
            int index = getPriorityIndex(stack, config.getFastWeapons());
            if (index != -1) {
                return 2000.0 + (100 - index) + damage;
            }

            // Prefer Speed: High score for Swords
            if (stack.getItem() instanceof SwordItem)
                return 1500.0 + damage;
            if (stack.getItem() instanceof TridentItem)
                return 1400.0 + damage;
            return damage;
        }

        if (pref == PiggyInventoryConfig.WeaponPreference.DAMAGE) {
            // Check user priority list first
            int index = getPriorityIndex(stack, config.getHeavyWeapons());
            if (index != -1) {
                return 2000.0 + (100 - index) + damage;
            }

            // Prefer Damage: High score for Axes
            if (stack.getItem() instanceof AxeItem)
                return 1500.0 + damage;
            if (stack.getItem() instanceof MaceItem)
                return 1450.0 + damage;
            if (stack.getItem() instanceof SwordItem)
                return 1000.0 + damage;
            return damage;
        }

        // Default to damage
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
