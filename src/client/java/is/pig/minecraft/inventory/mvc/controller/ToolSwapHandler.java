package is.pig.minecraft.inventory.mvc.controller;

import java.util.List;
import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class ToolSwapHandler {

    public void onTick(Minecraft client) {
        // Check if feature is enabled (considers server overrides)
        if (!((PiggyInventoryConfig) PiggyInventoryConfig.getInstance()).isFeatureToolSwapEnabled()) {
            return;
        }

        if (client.player == null || client.level == null) {
            return;
        }

        // Don't swap if a screen is open
        if (client.screen != null) {
            return;
        }

        // Only trying to swap if the player is actively attacking/mining
        if (!client.options.keyAttack.isDown()) {
            return;
        }

        if (client.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockState state = client.level.getBlockState(blockHit.getBlockPos());

            int currentSlot = client.player.getInventory().selected;
            ItemStack currentStack = client.player.getMainHandItem();

            // Best candidate initialization
            int bestSlot = currentSlot;
            float bestScore = getToolScore(client, currentStack, state);
            boolean bestDamageable = currentStack.isDamageableItem();

            // Iterate main inventory (0-35)
            for (int i = 0; i < 36; i++) {
                if (i == currentSlot)
                    continue;

                ItemStack stack = client.player.getInventory().getItem(i);
                float score = getToolScore(client, stack, state);
                boolean damageable = stack.isDamageableItem();

                // Logic:
                // 1. Strictly higher score -> Always switch
                // 2. Same score -> Prefer non-damageable (save durability)
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                    bestDamageable = damageable;
                } else if (Math.abs(score - bestScore) < 0.0001f) {
                    if (bestDamageable && !damageable) {
                        bestScore = score;
                        bestSlot = i;
                        bestDamageable = damageable;
                    }
                }
            }

            if (bestSlot != currentSlot) {
                if (bestSlot < 9) {
                    // Hotbar swap
                    client.player.getInventory().selected = bestSlot;
                    if (client.getConnection() != null) {
                        client.getConnection().send(new ServerboundSetCarriedItemPacket(bestSlot));
                    }
                } else {
                    // Inventory swap
                    List<Integer> allowedSlots = ((PiggyInventoryConfig) PiggyInventoryConfig.getInstance()).getSwapHotbarSlots();

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
                            ClickType.SWAP,
                            client.player);

                    if (client.player.getInventory().selected != targetSlot) {
                        client.player.getInventory().selected = targetSlot;
                        if (client.getConnection() != null) {
                            client.getConnection().send(new ServerboundSetCarriedItemPacket(targetSlot));
                        }
                    }
                }
            }
        }
    }

    private float getToolScore(Minecraft client, ItemStack stack, BlockState state) {
        if (stack.isEmpty())
            return 0f;

        float speed = stack.getDestroySpeed(state);

        // Efficiency Bonus
        if (speed > 1.0f) {
            int efficiencyLevel = getEnchantmentLevel(client, stack, Enchantments.EFFICIENCY);
            if (efficiencyLevel > 0) {
                speed += (efficiencyLevel * efficiencyLevel + 1);
            }
        }

        // Silk Touch Logic
        boolean hasSilk = getEnchantmentLevel(client, stack, Enchantments.SILK_TOUCH) > 0;
        int fortuneLevel = getEnchantmentLevel(client, stack, Enchantments.FORTUNE);

        boolean needsSilk = requiresSilkTouch(state);
        boolean isOre = isOreOrValuable(state);
        boolean needsShears = requiresShears(state);

        if (needsShears && stack.is(Items.SHEARS)) {
            speed += 10000.0f; // Massive bonus for Shears
        } else if (needsSilk && hasSilk) {
            speed += 10000.0f; // Massive bonus for mandatory Silk Touch
        } else if (isOre) {
            // Apply preference for Ores
            PiggyInventoryConfig.OrePreference perf = ((PiggyInventoryConfig) PiggyInventoryConfig.getInstance()).getOrePreference();

            if (perf == PiggyInventoryConfig.OrePreference.SILK_TOUCH) {
                if (hasSilk)
                    speed += 5000.0f; // Big bonus for preferred Silk
                else if (fortuneLevel > 0)
                    speed -= 100.0f; // Penalty for unwanted Fortune
            } else if (perf == PiggyInventoryConfig.OrePreference.FORTUNE) {
                // Prefer Fortune
                if (fortuneLevel > 0)
                    speed += (1000.0f * fortuneLevel); // Bonus per Fortune level
                else if (hasSilk)
                    speed -= 100.0f; // Penalty for unwanted Silk
            }
            // If NONE, no special bonuses applied, just raw speed/efficiency
        } else if (!needsSilk && hasSilk) {
            speed -= 0.1f; // Slight penalty to preserve Silk Touch durability if not needed
        }

        return speed;
    }

    private int getEnchantmentLevel(Minecraft client, ItemStack stack, ResourceKey<Enchantment> key) {
        if (client.level == null)
            return 0;
        try {
            var registry = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var enchant = registry.getOrThrow(key);
            return EnchantmentHelper.getItemEnchantmentLevel(enchant, stack);
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean requiresSilkTouch(BlockState state) {
        return matchesConfigList(((PiggyInventoryConfig) PiggyInventoryConfig.getInstance()).getSilkTouchBlocks(), state);
    }

    private boolean isOreOrValuable(BlockState state) {
        return matchesConfigList(((PiggyInventoryConfig) PiggyInventoryConfig.getInstance()).getFortuneBlocks(), state);
    }

    private boolean requiresShears(BlockState state) {
        return matchesConfigList(((PiggyInventoryConfig) PiggyInventoryConfig.getInstance()).getShearsBlocks(), state);
    }

    private boolean matchesConfigList(List<String> configList, BlockState state) {
        if (configList == null || configList.isEmpty())
            return false;

        ResourceKey<Block> key = state.getBlockHolder().unwrapKey().orElse(null);
        if (key == null)
            return false;
        String id = key.location().toString();
        String path = key.location().getPath();

        for (String pattern : configList) {
            pattern = pattern.trim();
            if (pattern.isEmpty())
                continue;

            if (pattern.contains("*")) {
                String clean = pattern.replace("*", "");
                if (pattern.startsWith("*") && pattern.endsWith("*")) {
                    if (id.contains(clean) || path.contains(clean))
                        return true;
                } else if (pattern.startsWith("*")) {
                    if (id.endsWith(clean) || path.endsWith(clean))
                        return true;
                } else if (pattern.endsWith("*")) {
                    if (id.startsWith(clean) || path.startsWith(clean))
                        return true;
                } else {
                    if (id.contains(clean))
                        return true;
                }
            } else {
                if (id.equals(pattern) || path.equals(pattern))
                    return true;
            }
        }
        return false;
    }
}