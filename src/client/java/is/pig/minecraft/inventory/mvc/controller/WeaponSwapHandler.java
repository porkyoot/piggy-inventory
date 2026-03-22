package is.pig.minecraft.inventory.mvc.controller;

import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public class WeaponSwapHandler {

    private long lastSwapTime = 0;

    public int getBestWeaponSlot(Minecraft client, Entity target) {
        PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();
        if (client.player == null || client.level == null) return client.player.getInventory().selected;
        
        int currentSlot = client.player.getInventory().selected;
        ItemStack currentStack = client.player.getMainHandItem();
        int bestSlot = currentSlot;
        double bestScore = getWeaponScore(client, currentStack, target, config);
        
        is.pig.minecraft.lib.inventory.search.ItemCondition condition = stack -> !stack.isEmpty();
        java.util.List<Integer> allSlots = is.pig.minecraft.lib.inventory.search.InventorySearcher.findAllSlots(client.player.getInventory(), condition);
        for (int i : allSlots) {
            if (i >= 36 || i == currentSlot) continue;
            ItemStack stack = client.player.getInventory().getItem(i);
            double score = getWeaponScore(client, stack, target, config);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /**
     * Called when the player is about to attack an entity.
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
        int bestSlot = getBestWeaponSlot(client, target);

        if (bestSlot != currentSlot) {
            int cps = config.getTickDelay();
            long minDelay = cps > 0 ? 1000L / cps : 0;
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastSwapTime >= minDelay) {
                swapToSlot(client, currentSlot, bestSlot, config.getWeaponSwapHotbarSlots());
                lastSwapTime = currentTime;
            }
        }
    }

    private void swapToSlot(Minecraft client, int currentSlot, int bestSlot, java.util.List<Integer> allowedSlots) {
        if (bestSlot < 9) {
            // Simple hotbar swap
            is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().enqueue(
                new is.pig.minecraft.lib.action.inventory.SelectHotbarSlotAction(bestSlot, "piggy-inventory")
            );
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

            is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().enqueue(
                new is.pig.minecraft.lib.action.inventory.ClickWindowSlotAction(
                        client.player.inventoryMenu.containerId,
                        bestSlot,
                        targetSlot,
                        net.minecraft.world.inventory.ClickType.SWAP,
                        "piggy-inventory"
                )
            );

            if (client.player.getInventory().selected != targetSlot) {
                is.pig.minecraft.lib.action.PiggyActionQueue.getInstance().enqueue(
                    new is.pig.minecraft.lib.action.inventory.SelectHotbarSlotAction(targetSlot, "piggy-inventory")
                );
            }
        }
    }

    private double getWeaponScore(Minecraft client, ItemStack stack, Entity target, PiggyInventoryConfig config) {
        if (stack.isEmpty())
            return 0;

        // 1. DYNAMIC ATTRIBUTE EXTRACTION (No more string matching!)
        double baseDamage = 1.0; // Base player fist damage
        double baseSpeed = 4.0;  // Base player attack speed

        net.minecraft.world.item.component.ItemAttributeModifiers modifiers = stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        
        final double[] dmg = {baseDamage};
        final double[] spd = {baseSpeed};
        final double initDmg = baseDamage;
        final double initSpd = baseSpeed;
        
        modifiers.forEach(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) {
                if (modifier.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
                    dmg[0] += modifier.amount();
                } else if (modifier.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                    dmg[0] += initDmg * modifier.amount();
                }
            } else if (attribute.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)) {
                if (modifier.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
                    spd[0] += modifier.amount();
                } else if (modifier.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                    spd[0] += initSpd * modifier.amount();
                }
            }
        });
        
        baseDamage = dmg[0];
        baseSpeed = spd[0];

        // Enchantment bonuses
        int sharpness = getEnchantmentLevel(client, stack, Enchantments.SHARPNESS);
        if (sharpness > 0)
            baseDamage += sharpness * 0.5;

        int smite = getEnchantmentLevel(client, stack, Enchantments.SMITE);
        if (smite > 0 && target instanceof LivingEntity living && living.isInvertedHealAndHarm()) {
            baseDamage += smite * 2.5;
        }

        int bane = getEnchantmentLevel(client, stack, Enchantments.BANE_OF_ARTHROPODS);
        if (bane > 0) {
            boolean isArthropod = target instanceof net.minecraft.world.entity.monster.Spider
                    || target instanceof net.minecraft.world.entity.monster.CaveSpider
                    || target instanceof net.minecraft.world.entity.monster.Silverfish
                    || target instanceof net.minecraft.world.entity.monster.Endermite
                    || target instanceof net.minecraft.world.entity.animal.Bee;
            if (isArthropod) {
                baseDamage += bane * 2.5;
            }
        }

        int impaling = getEnchantmentLevel(client, stack, Enchantments.IMPALING);
        if (impaling > 0) {
            boolean isAquatic = target instanceof net.minecraft.world.entity.animal.WaterAnimal
                    || target instanceof net.minecraft.world.entity.monster.Guardian
                    || target instanceof net.minecraft.world.entity.animal.Turtle;
            if (isAquatic) {
                baseDamage += impaling * 2.5;
            }
        }

        int fireAspect = getEnchantmentLevel(client, stack, Enchantments.FIRE_ASPECT);
        if (fireAspect > 0) {
            baseDamage += fireAspect * 1.5;
        }

        int sweeping = getEnchantmentLevel(client, stack, Enchantments.SWEEPING_EDGE);
        if (sweeping > 0) {
            baseDamage += sweeping * 1.0;
        }

        // Calculate DPS
        double dps = baseDamage * baseSpeed;

        boolean isBoat = target instanceof Boat || target instanceof AbstractMinecart || target instanceof ChestBoat;
        boolean isShielded = (target instanceof LivingEntity living) && living.isBlocking();
        boolean isFalling = client.player.fallDistance > 2.0f;

        // 1. Critical Overrides
        if (isFalling && stack.getItem() instanceof MaceItem) return 10000.0;
        if ((isBoat || isShielded) && stack.getItem() instanceof AxeItem) return 10000.0;

        PiggyInventoryConfig.WeaponPreference pref = config.getWeaponPreference();

        // 2. Mode Evaluation
        if (pref == PiggyInventoryConfig.WeaponPreference.SPEED) {
            int index = getPriorityIndex(stack, config.getFastWeapons());
            return index != -1 ? (5000.0 - index) : dps;
        } 
        else if (pref == PiggyInventoryConfig.WeaponPreference.DAMAGE) {
            int index = getPriorityIndex(stack, config.getHeavyWeapons());
            return index != -1 ? (5000.0 - index) : baseDamage;
        }
        else if (pref == PiggyInventoryConfig.WeaponPreference.RANGE) {
            int index = getPriorityIndex(stack, config.getRangeWeapons());
            return index != -1 ? (5000.0 - index) : (stack.getItem() instanceof TridentItem ? 4000.0 : baseDamage);
        }

        return baseDamage;
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
