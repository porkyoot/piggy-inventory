package is.pig.minecraft.inventory.mvc.controller;

import java.util.List;
import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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

    private net.minecraft.core.BlockPos lastTargetedBlock = null;
    private int ticksHovered = 0;
    private int ticksWantingToSwap = 0;
    private int targetSwapSlot = -1;

    /**
     * @return true if the attack should be CANCELLED (protected block).
     */
    public boolean onTick(Minecraft client) {
        PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();

        if (!config.isToolSwapEnabled() || client.player == null || client.level == null || client.screen != null) {
            this.lastTargetedBlock = null;
            this.ticksHovered = 0;
            this.ticksWantingToSwap = 0;
            this.targetSwapSlot = -1;
            return false;
        }

        if (!client.options.keyAttack.isDown()) {
            this.lastTargetedBlock = null;
            this.ticksHovered = 0;
            this.ticksWantingToSwap = 0;
            this.targetSwapSlot = -1;
            return false;
        }

        if (client.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            net.minecraft.core.BlockPos currentPos = blockHit.getBlockPos();
            BlockState state = client.level.getBlockState(currentPos);

            if (currentPos.equals(this.lastTargetedBlock)) {
                this.ticksHovered++;
            } else {
                this.lastTargetedBlock = currentPos;
                this.ticksHovered = 0;
            }

            int currentSlot = client.player.getInventory().selected;
            ItemStack currentStack = client.player.getMainHandItem();

            PiggyInventoryConfig.OrePreference mode = config.getOrePreference();

            // 2. Safety Check (Silk Touch Mode)
            if (mode == PiggyInventoryConfig.OrePreference.SILK_TOUCH) {
                if (matchesConfigList(config.getProtectedBlocks(), state)) {
                    this.ticksWantingToSwap = 0;
                    this.targetSwapSlot = -1;
                    return true; // Cancel attack
                }
            }

            // 3. Tool Selection Logic
            boolean currentBreakingSoon = isToolBreakingSoon(currentStack, config);

            int bestSlot = currentSlot;
            float currentScore = getToolScore(client, currentStack, state, mode, config);
            if (currentBreakingSoon) {
                currentScore = -10000.0f; // Penalize deeply
            }

            float bestScore = currentScore;
            boolean bestDamageable = currentStack.isDamageableItem();

            for (int i = 0; i < 36; i++) {
                if (i == currentSlot)
                    continue;

                ItemStack stack = client.player.getInventory().getItem(i);
                if (isToolBreakingSoon(stack, config)) {
                    continue; // Never swap to a breaking tool
                }
                float score = getToolScore(client, stack, state, mode, config);
                boolean damageable = stack.isDamageableItem();

                // Logic fix:
                // If we are in "bonus territory" (score > 1000), we don't use percentage based
                // improvement
                // because the base speed is small compared to the bonus.
                boolean isBetter;

                if (bestScore > 1000.0f) {
                    // With large bonuses, just check if it's strictly better by a noticeable margin
                    // (e.g. 1.0 speed unit)
                    isBetter = score > bestScore + 1.0f;
                } else {
                    // Standard behavior for normal tools
                    isBetter = score > bestScore * 1.05f;
                }

                if (isBetter) {
                    bestScore = score;
                    bestSlot = i;
                    bestDamageable = damageable;
                } else if (Math.abs(score - bestScore) < 0.0001f) {
                    // Only switch to a non-damageable item to save durability if we've hovered long
                    // enough (0.5 seconds).
                    // Prevents frantic no-tool -> tool -> no-tool swapping when Veinmining.
                    if (bestDamageable && !damageable && this.ticksHovered >= 10) {
                        bestScore = score;
                        bestSlot = i;
                        bestDamageable = damageable;
                    }
                }
            }

            if (bestSlot != currentSlot) {
                if (currentBreakingSoon) {
                    swapToSlot(client, currentSlot, bestSlot, config.getSwapHotbarSlots());
                    client.player.displayClientMessage(Component.literal("§c[Piggy] Tool swap: Saved your tool!"),
                            true);
                    this.ticksWantingToSwap = 0;
                    this.targetSwapSlot = -1;
                } else {
                    if (this.targetSwapSlot == bestSlot) {
                        this.ticksWantingToSwap++;
                    } else {
                        this.targetSwapSlot = bestSlot;
                        this.ticksWantingToSwap = 1;
                    }

                    int requiredTicks = hasVeinminerEnchantment(currentStack) ? 10
                            : (state.getDestroySpeed(client.level, currentPos) == 0.0F ? 0 : 2);

                    if (this.ticksWantingToSwap >= requiredTicks) {
                        swapToSlot(client, currentSlot, bestSlot, config.getSwapHotbarSlots());
                        this.ticksWantingToSwap = 0;
                        this.targetSwapSlot = -1;
                    }
                }
            } else {
                if (currentBreakingSoon) {
                    client.player.displayClientMessage(
                            Component.literal("§c[Piggy] Tool breaking soon! Mining prevented."), true);
                    this.ticksWantingToSwap = 0;
                    this.targetSwapSlot = -1;
                    return true; // Cancel attack
                }
                this.ticksWantingToSwap = 0;
                this.targetSwapSlot = -1;
            }
        } else {
            this.lastTargetedBlock = null;
            this.ticksHovered = 0;
            this.ticksWantingToSwap = 0;
            this.targetSwapSlot = -1;
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

        // NEW: Check if the tool works for drops
        // If the block *requires* a specific tool for drops (like Stone requires a
        // pickaxe),
        // and this stack is NOT that tool, we penalize it heavily so we don't switch to
        // it.
        // We do allow it if the block doesn't require a tool (leaves, dirt, etc).
        if (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state)) {
            return -1.0f;
        }

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
        } else if (needsSilk && hasSilk) {
            speed += 10000.0f;
        } else if (isOre) {
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
        } else if (!needsSilk && hasSilk) {
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

    private boolean hasVeinminerEnchantment(ItemStack stack) {
        if (stack.isEmpty())
            return false;
        try {
            var enchantments = stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
            if (enchantments != null) {
                for (var enchantHolder : enchantments.keySet()) {
                    var key = enchantHolder.unwrapKey().orElse(null);
                    if (key != null) {
                        String path = key.location().getPath().toLowerCase();
                        if (path.contains("veinminer") || path.contains("timber") || path.contains("treecapitator")
                                || path.contains("lumberjack") || path.contains("magnetic") || path.contains("smelting")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Safe fallback
        }
        return false;
    }

    private boolean isToolBreakingSoon(ItemStack stack, PiggyInventoryConfig config) {
        if (!config.isPreventToolBreak() || !stack.isDamageableItem()) {
            return false;
        }

        int remainingDurability = stack.getMaxDamage() - stack.getDamageValue();
        if (remainingDurability > 10) {
            return false;
        }

        if (config.isAllowUnenchantedToolsToBreak() && !hasAnyEnchantments(stack)) {
            return false;
        }

        return true;
    }

    private boolean hasAnyEnchantments(ItemStack stack) {
        try {
            var enchantments = stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
            return enchantments != null && !enchantments.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}