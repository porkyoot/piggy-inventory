package is.pig.minecraft.inventory.mvc.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import is.pig.minecraft.inventory.config.PiggyInventoryConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private int ticksWantingToSwap = 0;
    private int targetSwapSlot = -1;
    private long lastSwapTime = 0;

    /**
     * @return true if the attack should be CANCELLED (protected block).
     */
    public boolean onTick(Minecraft client) {
        PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();

        if (client.player == null || client.level == null || client.screen != null) {
            this.lastTargetedBlock = null;
            this.ticksWantingToSwap = 0;
            this.targetSwapSlot = -1;
            return false;
        }

        if (!client.options.keyAttack.isDown()) {
            this.lastTargetedBlock = null;
            this.ticksWantingToSwap = 0;
            this.targetSwapSlot = -1;
            return false;
        }
        
        ItemStack currentStackOriginal = client.player.getMainHandItem();
        boolean breakProtectionActive = isToolBreakingSoon(currentStackOriginal, config);

        if (!config.isFeatureToolSwapEnabled()) {
            // Tool swap disabled, but we might still need break protection
            if (breakProtectionActive) {
                // Break protection triggered!
                client.player.displayClientMessage(Component.literal("§c[Piggy] Break Protection: Your tool is about to break!"), true);
                this.lastTargetedBlock = null;
                this.ticksWantingToSwap = 0;
                this.targetSwapSlot = -1;
                return true; // Cancel attack
            }
            
            boolean currentIsHammer = isHammer(currentStackOriginal);
            PiggyInventoryConfig.OrePreference currentMode = config.getOrePreference();
            if (currentIsHammer && currentMode != PiggyInventoryConfig.OrePreference.FORTUNE_STRICT) {
                this.lastTargetedBlock = null;
                this.ticksWantingToSwap = 0;
                this.targetSwapSlot = -1;
                return true;
            }
            
            this.lastTargetedBlock = null;
            this.ticksWantingToSwap = 0;
            this.targetSwapSlot = -1;
            return false;
        }

        if (client.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            net.minecraft.core.BlockPos currentPos = blockHit.getBlockPos();
            BlockState state = client.level.getBlockState(currentPos);

            if (currentPos.equals(this.lastTargetedBlock)) {
                // Keep swapping progress
            } else {
                this.lastTargetedBlock = currentPos;
                this.ticksWantingToSwap = 0;
            }

            int currentSlot = client.player.getInventory().selected;
            ItemStack currentStack = client.player.getMainHandItem();

            PiggyInventoryConfig.OrePreference mode = config.getOrePreference();

            if (mode == PiggyInventoryConfig.OrePreference.SILK_TOUCH_STRICT) {
                if (isNonSilktouchable(state)) {
                    //client.player.displayClientMessage(Component.literal("§c[Piggy] Block protected: Cannot be Silk Touched!"), true);
                    this.ticksWantingToSwap = 0;
                    this.targetSwapSlot = -1;
                    return true; // Cancel attack
                }
            }

            // 1. Gather all valid tools from inventory (including main hand)
            List<Integer> validSlots = new ArrayList<>();
            boolean currentBreakingSoon = isToolBreakingSoon(currentStack, config);
            boolean currentIsHammer = isHammer(currentStack);
            boolean isHammerAllowed = mode == PiggyInventoryConfig.OrePreference.FORTUNE_STRICT;
            
            for (int i = 0; i < 36; i++) {
                ItemStack stack = client.player.getInventory().getItem(i);
                
                // FILTER: Don't use a tool that is about to break
                if (isToolBreakingSoon(stack, config)) continue;

                // FILTER: Don't swap to a hammer unless in Fortune+ mode
                if (isHammer(stack) && !isHammerAllowed) continue;

                // FILTER: Strict modes prevent selecting tools that don't have the required enchant
                if (isOreOrValuable(state, config)) {
                    if (mode == PiggyInventoryConfig.OrePreference.FORTUNE_STRICT && getEnchantmentLevel(client, stack, Enchantments.FORTUNE) == 0) continue;
                    if (mode == PiggyInventoryConfig.OrePreference.SILK_TOUCH_STRICT && getEnchantmentLevel(client, stack, Enchantments.SILK_TOUCH) == 0) continue;
                }

                validSlots.add(i);
            }

            // Safety Check for protected blocks / strict mode exclusions
            if (isOreOrValuable(state, config)) {
                if (validSlots.isEmpty() && (mode == PiggyInventoryConfig.OrePreference.FORTUNE_STRICT || mode == PiggyInventoryConfig.OrePreference.SILK_TOUCH_STRICT)) {
                    client.player.displayClientMessage(Component.literal("§c[Piggy] Block protected: No valid tool found!"), true);
                    this.ticksWantingToSwap = 0;
                    this.targetSwapSlot = -1;
                    return true; // Cancel attack
                }
            } else if (mode == PiggyInventoryConfig.OrePreference.SILK_TOUCH_STRICT || mode == PiggyInventoryConfig.OrePreference.SILK_TOUCH_PREFERRED) {
                // Legacy strict silk touch protection on general blocks
                if (matchesConfigList(config.getProtectedBlocks(), state)) {
                    if (validSlots.isEmpty() || getEnchantmentLevel(client, client.player.getInventory().getItem(validSlots.get(0)), Enchantments.SILK_TOUCH) == 0) {
                         this.ticksWantingToSwap = 0;
                         this.targetSwapSlot = -1;
                         return true; // Cancel attack
                    }
                }
            }

            if (validSlots.isEmpty()) {
                if (currentBreakingSoon) {
                    client.player.displayClientMessage(Component.literal("§c[Piggy] Break Protection: Your tool is about to break!"), true);
                    this.ticksWantingToSwap = 0;
                    this.targetSwapSlot = -1;
                    return true;
                }
                if (currentIsHammer && !isHammerAllowed) {
                    this.ticksWantingToSwap = 0;
                    this.targetSwapSlot = -1;
                    return true;
                }
                return false;
            }

            // 2. Sort the tools
            int bestSlot = validSlots.stream().max(Comparator
                // Tier 1: Is it the correct tool for drops? (True > False)
                .comparing((Integer slot) -> {
                    if (!state.requiresCorrectToolForDrops()) return true; // everything is "correct"
                    return client.player.getInventory().getItem(slot).isCorrectToolForDrops(state);
                })
                
                // Tier 2: Does it meet our preferred enchantment mode?
                .thenComparing(slot -> matchesPreference(client, client.player.getInventory().getItem(slot), state, mode, config))
                
                // Tier 2.5: In Fortune+ mode, prefer hammers over regular tools
                .thenComparing(slot -> mode == PiggyInventoryConfig.OrePreference.FORTUNE_STRICT && isHammer(client.player.getInventory().getItem(slot)))
                
                // Tier 3: Is it Shears for a shearable block?
                .thenComparing(slot -> requiresShears(state, config) && client.player.getInventory().getItem(slot).is(Items.SHEARS))
                
                // Tier 4: Raw mining speed (including Efficiency)
                .thenComparingDouble(slot -> calculateMiningSpeed(client, client.player.getInventory().getItem(slot), state, currentPos))
                
                // Tier 5: Prefer current slot to avoid unnecessary swaps
                .thenComparing(slot -> slot == currentSlot)
                
                // Tier 6: Prefer non-damageable items (save durability on fallback)
                .thenComparing(slot -> !client.player.getInventory().getItem(slot).isDamageableItem())
            ).orElse(currentSlot);

            // 3. Swap logic
            if (bestSlot != currentSlot) {
                int cps = config.getTickDelay();
                long minDelay = cps > 0 ? 1000L / cps : 0;
                long currentTime = System.currentTimeMillis();

                if (currentBreakingSoon || (currentIsHammer && !isHammerAllowed)) {
                    if (currentTime - lastSwapTime >= minDelay) {
                        swapToSlot(client, currentSlot, bestSlot, config.getSwapHotbarSlots());
                        if (currentBreakingSoon) {
                            client.player.displayClientMessage(Component.literal("§c[Piggy] Break Protection: Saved your tool by swapping!"), true);
                        }
                        this.ticksWantingToSwap = 0;
                        this.targetSwapSlot = -1;
                        lastSwapTime = currentTime;
                        return true; // Cancel attack immediately after swapping to prevent using the wrong tool for one tick
                    } else {
                        // Cancel attack during swap cooldown to prevent breaking with protected/banned tools
                        this.ticksWantingToSwap = 0;
                        this.targetSwapSlot = -1;
                        return true;
                    }
                } else {
                    if (this.targetSwapSlot == bestSlot) {
                        this.ticksWantingToSwap++;
                    } else {
                        this.targetSwapSlot = bestSlot;
                        this.ticksWantingToSwap = 1;
                    }

                    int requiredTicks = state.getDestroySpeed(client.level, currentPos) == 0.0F ? 0 : 2;

                    if (this.ticksWantingToSwap >= requiredTicks) {
                        if (currentTime - lastSwapTime >= minDelay) {
                            swapToSlot(client, currentSlot, bestSlot, config.getSwapHotbarSlots());
                            this.ticksWantingToSwap = 0;
                            this.targetSwapSlot = -1;
                            lastSwapTime = currentTime;
                        }
                    }
                }
            } else {
                this.ticksWantingToSwap = 0;
                this.targetSwapSlot = -1;
            }
        } else {
            this.lastTargetedBlock = null;
            this.ticksWantingToSwap = 0;
            this.targetSwapSlot = -1;
        }

        return false; // Allow attack
    }

    private boolean matchesPreference(Minecraft client, ItemStack stack, BlockState state, PiggyInventoryConfig.OrePreference mode, PiggyInventoryConfig config) {
        if (!isOreOrValuable(state, config)) return false;
        if (mode == PiggyInventoryConfig.OrePreference.FORTUNE_PREFERRED || mode == PiggyInventoryConfig.OrePreference.FORTUNE_STRICT) {
            return getEnchantmentLevel(client, stack, Enchantments.FORTUNE) > 0;
        }
        if (mode == PiggyInventoryConfig.OrePreference.SILK_TOUCH_PREFERRED || mode == PiggyInventoryConfig.OrePreference.SILK_TOUCH_STRICT) {
            return getEnchantmentLevel(client, stack, Enchantments.SILK_TOUCH) > 0;
        }
        return false;
    }

    private double calculateMiningSpeed(Minecraft client, ItemStack stack, BlockState state, net.minecraft.core.BlockPos pos) {
        if (state.getDestroySpeed(client.level, pos) == 0.0F) {
            return 1.0; // Speed doesn't matter for instant-break blocks
        }
        float baseSpeed = stack.getDestroySpeed(state);
        if (baseSpeed > 1.0f) {
            int eff = getEnchantmentLevel(client, stack, Enchantments.EFFICIENCY);
            if (eff > 0) baseSpeed += (eff * eff + 1);
        }
        return baseSpeed;
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

    private boolean isNonSilktouchable(BlockState state) {
        if (state == null) return false;
        ResourceKey<Block> key = state.getBlockHolder().unwrapKey().orElse(null);
        if (key == null) return false;
        String id = key.location().toString();
        return id.equals("minecraft:budding_amethyst") || 
               id.equals("minecraft:suspicious_sand") || 
               id.equals("minecraft:suspicious_gravel") ||
               id.equals("minecraft:spawner") ||
               id.equals("minecraft:trial_spawner");
    }

    private boolean isOreOrValuable(BlockState state, PiggyInventoryConfig config) {
        // Includes both Fortune and Silk Touch lists acting as "Valuables"
        return matchesConfigList(config.getFortuneBlocks(), state) || matchesConfigList(config.getSilkTouchBlocks(), state);
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

    private boolean isHammer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("hammer");
        } catch (Exception e) {
            return false;
        }
    }
}