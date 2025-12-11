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

    /**
     * @return true if the attack should be CANCELLED (protected block).
     */
    public boolean onTick(Minecraft client) {
        PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();
        
        if (!config.isFeatureToolSwapEnabled()) {
            return false;
        }

        if (client.player == null || client.level == null) {
            return false;
        }

        if (client.screen != null) {
            return false;
        }

        if (!client.options.keyAttack.isDown()) {
            return false;
        }

        if (client.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockState state = client.level.getBlockState(blockHit.getBlockPos());
            
            PiggyInventoryConfig.OrePreference mode = config.getOrePreference();

            // 2. Safety Check (Silk Touch Mode)
            if (mode == PiggyInventoryConfig.OrePreference.SILK_TOUCH) {
                if (matchesConfigList(config.getProtectedBlocks(), state)) {
                    return true; // Cancel attack
                }
            }

            // 3. Tool Selection Logic
            int currentSlot = client.player.getInventory().selected;
            ItemStack currentStack = client.player.getMainHandItem();

            int bestSlot = currentSlot;
            float bestScore = getToolScore(client, currentStack, state, mode, config);
            boolean bestDamageable = currentStack.isDamageableItem();

            for (int i = 0; i < 36; i++) {
                if (i == currentSlot)
                    continue;

                ItemStack stack = client.player.getInventory().getItem(i);
                float score = getToolScore(client, stack, state, mode, config);
                boolean damageable = stack.isDamageableItem();

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
                swapToSlot(client, currentSlot, bestSlot, config.getSwapHotbarSlots());
            }
        }
        
        return false; // Allow attack
    }

    private void swapToSlot(Minecraft client, int currentSlot, int bestSlot, List<Integer> allowedSlots) {
        if (bestSlot < 9) {
            client.player.getInventory().selected = bestSlot;
            if (client.getConnection() != null) {
                client.getConnection().send(new ServerboundSetCarriedItemPacket(bestSlot));
            }
        } else {
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

    private float getToolScore(Minecraft client, ItemStack stack, BlockState state, 
                               PiggyInventoryConfig.OrePreference mode, PiggyInventoryConfig config) {
        if (stack.isEmpty())
            return 0f;

        float speed = stack.getDestroySpeed(state);

        if (speed > 1.0f) {
            int efficiencyLevel = getEnchantmentLevel(client, stack, Enchantments.EFFICIENCY);
            if (efficiencyLevel > 0) {
                speed += (efficiencyLevel * efficiencyLevel + 1);
            }
        }

        boolean hasSilk = getEnchantmentLevel(client, stack, Enchantments.SILK_TOUCH) > 0;
        int fortuneLevel = getEnchantmentLevel(client, stack, Enchantments.FORTUNE);

        boolean needsShears = requiresShears(state, config);
        
        // In FORTUNE mode, we IGNORE the need for shears (destruction mode).
        // In SILK_TOUCH mode, we respect it.
        if (mode == PiggyInventoryConfig.OrePreference.FORTUNE) {
            needsShears = false;
        }
        
        boolean needsSilk = (mode == PiggyInventoryConfig.OrePreference.SILK_TOUCH) 
                            && matchesConfigList(config.getSilkTouchBlocks(), state);
                            
        boolean isOre = isOreOrValuable(state, config);

        if (needsShears && stack.is(Items.SHEARS)) {
            speed += 10000.0f; // Bonus only applied if we *want* shears behavior
        } 
        else if (needsSilk && hasSilk) {
            speed += 10000.0f; 
        } 
        else if (isOre) {
            if (mode == PiggyInventoryConfig.OrePreference.SILK_TOUCH) {
                if (hasSilk)
                    speed += 5000.0f;
                else if (fortuneLevel > 0)
                    speed -= 100.0f;
            } else if (mode == PiggyInventoryConfig.OrePreference.FORTUNE) {
                if (fortuneLevel > 0)
                    speed += (1000.0f * fortuneLevel);
                else if (hasSilk)
                    speed -= 100.0f;
            }
        } 
        else if (!needsSilk && hasSilk) {
             // Slight penalty to save Silk Touch durability on things that don't need it
            speed -= 0.1f; 
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

    private boolean requiresShears(BlockState state, PiggyInventoryConfig config) {
        return matchesConfigList(config.getShearsBlocks(), state);
    }

    private boolean isOreOrValuable(BlockState state, PiggyInventoryConfig config) {
        return matchesConfigList(config.getFortuneBlocks(), state);
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