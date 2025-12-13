package is.pig.minecraft.inventory.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumDropdownControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;

import dev.isxander.yacl3.api.ListOption;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PiggyConfigScreenFactory {

        public static Screen create(Screen parent) {
                PiggyInventoryConfig config = (PiggyInventoryConfig) PiggyInventoryConfig.getInstance();

                return YetAnotherConfigLib.createBuilder()
                                .title(Component.literal("Piggy Inventory Configuration"))

                                // SAFETY CATEGORY
                                .category(ConfigCategory.createBuilder()
                                                .name(Component.literal("Safety"))
                                                .tooltip(is.pig.minecraft.lib.I18n.safetyTooltip())
                                                .option(Option.<Integer>createBuilder()
                                                                .name(Component.literal("Click Delay (cps equivalent)"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "Ticks between click actions. (1 tick = 0.05s). Higher = Slower/Safer.")))
                                                                .binding(1, config::getTickDelay, config::setTickDelay)
                                                                .controller(opt -> IntegerSliderControllerBuilder
                                                                                .create(opt).range(0, 10).step(1))
                                                                .build())
                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.literal("No Cheating Mode"))
                                                                .description(OptionDescription.of(
                                                                                Component.literal(
                                                                                                "Prevents usage of cheat features in Survival/Adventure mode."),
                                                                                Component.literal(""),
                                                                                Component.literal(
                                                                                                "When enabled, utility features are disabled unless you are in Creative mode."),
                                                                                Component.literal(
                                                                                                "This option is locked if the server enforces anti-cheat.")))
                                                                .binding(true, config::isNoCheatingMode,
                                                                                config::setNoCheatingMode)
                                                                .controller(TickBoxControllerBuilder::create)
                                                                .available(config.isGlobalCheatsEditable())
                                                                .build())
                                                .build())

                                // FEATURES CATEGORY
                                .category(ConfigCategory.createBuilder()
                                                .name(Component.literal("Features"))
                                                .tooltip(Component.literal("General feature toggles."))

                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.literal("Mouse + Shift Tweaks"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "Enable Shift+Scroll/Drag transfer.")))
                                                                .binding(true, config::isMouseTwicks,
                                                                                config::setMouseTwicks)
                                                                .controller(TickBoxControllerBuilder::create)
                                                                .build())

                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.literal("Continuous Crafting"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "Enable holding click to craft/refill continuously.")))
                                                                .binding(true, config::isContinuousCrafting,
                                                                                config::setContinuousCrafting)
                                                                .controller(TickBoxControllerBuilder::create)
                                                                .build())

                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.literal("Enable Tool Swap"))
                                                                .description(OptionDescription.of(
                                                                                Component.literal(
                                                                                                "Automatically swaps to the faster tool in your inventory when attacking a block."),
                                                                                Component.literal(
                                                                                                "If Anti-Cheat is active, this cannot be enabled.")))
                                                                .available(config.isToolSwapEditable())
                                                                .binding(true, config::isToolSwapEnabled,
                                                                                config::setToolSwapEnabled)
                                                                .controller(TickBoxControllerBuilder::create)
                                                                .build())

                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.literal("Enable Weapon Swap"))
                                                                .description(OptionDescription.of(
                                                                                Component.literal(
                                                                                                "Automatically swaps to the best weapon when attacking an entity."),
                                                                                Component.literal(
                                                                                                "If Anti-Cheat is active, this cannot be enabled.")))
                                                                .available(config.isWeaponSwitchEditable())
                                                                .binding(true, config::isWeaponSwitchBoolean,
                                                                                config::setWeaponSwitchBoolean)
                                                                .controller(TickBoxControllerBuilder::create)
                                                                .build())

                                                .group(OptionGroup.createBuilder()
                                                                .name(Component.literal("Auto Refill"))
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.literal(
                                                                                                "Enable Auto Refill"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.literal(
                                                                                                                "Automatically replaces broken tools or depleted stacks in your hand.")))
                                                                                .binding(true, config::isAutoRefill,
                                                                                                config::setAutoRefill)
                                                                                .controller(TickBoxControllerBuilder::create)
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.literal(
                                                                                                "Refill Containers (Stacks)"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.literal(
                                                                                                                "Refill stackable items (blocks, materials) when they run out.")))
                                                                                .binding(true, config::isAutoRefillContainers,
                                                                                                config::setAutoRefillContainers)
                                                                                .controller(TickBoxControllerBuilder::create)
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.literal("Refill Food"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.literal(
                                                                                                                "Automatically eat food when hungry if it's in your hotbar.")))
                                                                                .binding(true, config::isAutoRefillFood,
                                                                                                config::setAutoRefillFood)
                                                                                .controller(TickBoxControllerBuilder::create)
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.literal(
                                                                                                "Refill Weapon"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.literal(
                                                                                                                "Replace broken weapons with similar ones from inventory.")))
                                                                                .binding(true, config::isAutoRefillWeapon,
                                                                                                config::setAutoRefillWeapon)
                                                                                .controller(TickBoxControllerBuilder::create)
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.literal("Refill Tool"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.literal(
                                                                                                                "Replace broken tools with similar ones from inventory.")))
                                                                                .binding(true, config::isAutoRefillTool,
                                                                                                config::setAutoRefillTool)
                                                                                .controller(TickBoxControllerBuilder::create)
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.literal(
                                                                                                "Refill Harmful Food"))
                                                                                .description(OptionDescription
                                                                                                .of(Component.literal(
                                                                                                                "Allows refilling items like Spider Eyes.")))
                                                                                .binding(false, config::isAutoRefillHarmful,
                                                                                                config::setAutoRefillHarmful)
                                                                                .controller(TickBoxControllerBuilder::create)
                                                                                .build())
                                                                .build())

                                                // Group: Fast Loot
                                                .group(OptionGroup.createBuilder()
                                                                .name(Component.literal("Fast Loot & Deposit"))
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.literal(
                                                                                                "Enable Fast Loot"))
                                                                                .binding(true, config::isFastLoot,
                                                                                                config::setFastLoot)
                                                                                .controller(TickBoxControllerBuilder::create)
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.literal(
                                                                                                "In Container: Matching Slots"))
                                                                                .description(OptionDescription
                                                                                                .of(Component.literal(
                                                                                                                "Fast loot/depo matching items.")))
                                                                                .binding(true, config::isFastLootInContainerMatching,
                                                                                                config::setFastLootInContainerMatching)
                                                                                .controller(TickBoxControllerBuilder::create)
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.literal(
                                                                                                "In Container: Entire Inventory"))
                                                                                .description(OptionDescription
                                                                                                .of(Component.literal(
                                                                                                                "Fast loot/depo all items.")))
                                                                                .binding(true, config::isFastLootInContainerAll,
                                                                                                config::setFastLootInContainerAll)
                                                                                .controller(TickBoxControllerBuilder::create)
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.literal(
                                                                                                "Mouse Hover: Matching Slots"))
                                                                                .description(OptionDescription
                                                                                                .of(Component.literal(
                                                                                                                "Fast loot/depo matching items via hover.")))
                                                                                .binding(true, config::isFastLootLookingAtMatching,
                                                                                                config::setFastLootLookingAtMatching)
                                                                                .controller(TickBoxControllerBuilder::create)
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.literal(
                                                                                                "Mouse Hover: Entire Inventory"))
                                                                                .description(OptionDescription
                                                                                                .of(Component.literal(
                                                                                                                "Fast loot/depo all items via hover.")))
                                                                                .binding(true, config::isFastLootLookingAtAll,
                                                                                                config::setFastLootLookingAtAll)
                                                                                .controller(TickBoxControllerBuilder::create)
                                                                                .build())
                                                                .build())
                                                .build())

                                // TOOL SWAP CATEGORY
                                .category(ConfigCategory.createBuilder()
                                                .name(Component.literal("Tool Swap"))
                                                .tooltip(Component.literal("Configure automatic tool swapping."))

                                                .option(Option.<String>createBuilder()
                                                                .name(Component.literal("Hotbar Swap Slots"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "Which hotbar slots can be overwritten?")))
                                                                .binding(formatSlotList(config.getSwapHotbarSlots()),
                                                                                () -> formatSlotList(config
                                                                                                .getSwapHotbarSlots()),
                                                                                (val) -> config.setSwapHotbarSlots(
                                                                                                parseSlotString(val)))
                                                                .controller(StringControllerBuilder::create)
                                                                .build())
                                                .option(Option.<PiggyInventoryConfig.OrePreference>createBuilder()
                                                                .name(Component.literal("Ore Preference"))
                                                                .description(OptionDescription.of(
                                                                                Component.literal(
                                                                                                "Prioritize Fortune or Silk Touch when both are applicable.")))
                                                                .binding(PiggyInventoryConfig.OrePreference.FORTUNE,
                                                                                config::getOrePreference,
                                                                                config::setOrePreference)
                                                                .controller(opt -> EnumControllerBuilder.create(opt)
                                                                                .enumClass(PiggyInventoryConfig.OrePreference.class))
                                                                .build())

                                                .group(ListOption.<String>createBuilder()
                                                                .name(Component.literal(
                                                                                "Silk Touch Blocks"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "List of blocks that should be mined with Silk Touch.")))
                                                                .binding(config.getSilkTouchBlocks(),
                                                                                config::getSilkTouchBlocks,
                                                                                config::setSilkTouchBlocks)
                                                                .controller(StringControllerBuilder::create)
                                                                .initial("")
                                                                .build())
                                                .group(ListOption.<String>createBuilder()
                                                                .name(Component.literal(
                                                                                "Fortune/Ore Blocks"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "List of blocks that should be mined with Fortune.")))
                                                                .binding(config.getFortuneBlocks(),
                                                                                config::getFortuneBlocks,
                                                                                config::setFortuneBlocks)
                                                                .controller(StringControllerBuilder::create)
                                                                .initial("")
                                                                .build())
                                                .group(ListOption.<String>createBuilder()
                                                                .name(Component.literal(
                                                                                "Protected Blocks"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "Blocks that will NOT trigger a tool swap (manual mining only).")))
                                                                .binding(config.getProtectedBlocks(),
                                                                                config::getProtectedBlocks,
                                                                                config::setProtectedBlocks)
                                                                .controller(StringControllerBuilder::create)
                                                                .initial("")
                                                                .build())
                                                .group(ListOption.<String>createBuilder()
                                                                .name(Component.literal(
                                                                                "Shears Blocks"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "Blocks that specifically constitute a shearing action.")))
                                                                .binding(config.getShearsBlocks(),
                                                                                config::getShearsBlocks,
                                                                                config::setShearsBlocks)
                                                                .controller(StringControllerBuilder::create)
                                                                .initial("")
                                                                .build())
                                                .build())

                                // WEAPON SWITCH CATEGORY
                                .category(ConfigCategory.createBuilder()
                                                .name(Component.literal("Weapon Swap"))
                                                .tooltip(Component.literal("Configure automatic weapon switching."))

                                                .option(Option.<String>createBuilder()
                                                                .name(Component.literal("Weapon Hotbar Swap Slots"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "Which hotbar slots can be overwritten by weapons?")))
                                                                .binding(formatSlotList(
                                                                                config.getWeaponSwapHotbarSlots()),
                                                                                () -> formatSlotList(config
                                                                                                .getWeaponSwapHotbarSlots()),
                                                                                (val) -> config.setWeaponSwapHotbarSlots(
                                                                                                parseSlotString(val)))
                                                                .controller(StringControllerBuilder::create)
                                                                .build())
                                                .option(Option.<PiggyInventoryConfig.WeaponPreference>createBuilder()
                                                                .name(Component.literal("Weapon Preference"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "Prioritize highest damage or highest attack speed.")))
                                                                .binding(PiggyInventoryConfig.WeaponPreference.DAMAGE,
                                                                                config::getGuiWeaponPreference,
                                                                                config::setGuiWeaponPreference)
                                                                .controller(opt -> EnumControllerBuilder.create(opt)
                                                                                .enumClass(PiggyInventoryConfig.WeaponPreference.class))
                                                                .build())
                                                .group(ListOption.<String>createBuilder()
                                                                .name(Component.literal(
                                                                                "Speed Weapons Priority"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "Items considered 'Speed Weapons', in order of preference.")))
                                                                .binding(config.getFastWeapons(),
                                                                                config::getFastWeapons,
                                                                                config::setFastWeapons)
                                                                .controller(StringControllerBuilder::create)
                                                                .initial("")
                                                                .build())
                                                .group(ListOption.<String>createBuilder()
                                                                .name(Component.literal(
                                                                                "Damage Weapons Priority"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "Items considered 'Damage Weapons', in order of preference.")))
                                                                .binding(config.getHeavyWeapons(),
                                                                                config::getHeavyWeapons,
                                                                                config::setHeavyWeapons)
                                                                .controller(StringControllerBuilder::create)
                                                                .initial("")
                                                                .build())
                                                .group(ListOption.<String>createBuilder()
                                                                .name(Component.literal(
                                                                                "Range Weapons Priority"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "Items considered 'Range Weapons', in order of preference.")))
                                                                .binding(config.getRangeWeapons(),
                                                                                config::getRangeWeapons,
                                                                                config::setRangeWeapons)
                                                                .controller(StringControllerBuilder::create)
                                                                .initial("")
                                                                .build())
                                                .build())

                                // INVENTORY SORTER CATEGORY
                                .category(ConfigCategory.createBuilder()
                                                .name(Component.literal("Inventory Sorter"))
                                                .tooltip(Component.literal("Configure scanning and sorting behavior."))
                                                .option(Option.<PiggyInventoryConfig.SortingAlgorithm>createBuilder()
                                                                .name(Component.literal("Default Algorithm"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "The primary sorting logic used.")))
                                                                .binding(PiggyInventoryConfig.SortingAlgorithm.SMART,
                                                                                config::getDefaultAlgorithm,
                                                                                config::setDefaultAlgorithm)
                                                                .controller(EnumDropdownControllerBuilder::create)
                                                                .build())
                                                .option(Option.<PiggyInventoryConfig.SortingLayout>createBuilder()
                                                                .name(Component.literal("Default Layout"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "The visual arrangement of items.")))
                                                                .binding(PiggyInventoryConfig.SortingLayout.COMPACT,
                                                                                config::getDefaultLayout,
                                                                                config::setDefaultLayout)
                                                                .controller(opt -> EnumControllerBuilder.create(opt)
                                                                                .enumClass(PiggyInventoryConfig.SortingLayout.class))
                                                                .build())
                                                .group(ListOption.<String>createBuilder()
                                                                .name(Component.literal("Ignored Screen Classes"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "Screens where sorting should be disabled (Full class names).")))
                                                                .binding(new ArrayList<>(),
                                                                                config::getBlacklistedInventories,
                                                                                config::setBlacklistedInventories)
                                                                .controller(StringControllerBuilder::create)
                                                                .initial("")
                                                                .build())
                                                .group(ListOption.<String>createBuilder()
                                                                .name(Component.literal("Ignored Items"))
                                                                .description(OptionDescription.of(Component.literal(
                                                                                "Items that will not be moved by the sorter.")))
                                                                .binding(new ArrayList<>(), config::getBlacklistedItems,
                                                                                config::setBlacklistedItems)
                                                                .controller(StringControllerBuilder::create)
                                                                .initial("")
                                                                .build())
                                                .build())

                                .save(ConfigPersistence::save)
                                .build()
                                .generateScreen(parent);
        }

        private static String formatSlotList(List<Integer> slots) {
                if (slots == null || slots.isEmpty())
                        return "";
                List<Integer> sorted = new ArrayList<>(slots);
                Collections.sort(sorted);
                StringBuilder sb = new StringBuilder();
                int start = sorted.get(0);
                int end = start;
                for (int i = 1; i < sorted.size(); i++) {
                        int current = sorted.get(i);
                        if (current == end + 1) {
                                end = current;
                        } else {
                                appendRange(sb, start, end);
                                sb.append(", ");
                                start = current;
                                end = current;
                        }
                }
                appendRange(sb, start, end);
                return sb.toString();
        }

        private static void appendRange(StringBuilder sb, int start, int end) {
                if (start == end)
                        sb.append(start);
                else if (end == start + 1)
                        sb.append(start).append(", ").append(end);
                else
                        sb.append(start).append("-").append(end);
        }

        private static List<Integer> parseSlotString(String str) {
                Set<Integer> uniqueSlots = new HashSet<>();
                String[] parts = str.split(",");
                for (String part : parts) {
                        part = part.trim();
                        if (part.isEmpty())
                                continue;
                        try {
                                if (part.contains("-")) {
                                        String[] range = part.split("-");
                                        if (range.length == 2) {
                                                int start = Integer.parseInt(range[0].trim());
                                                int end = Integer.parseInt(range[1].trim());
                                                if (start > end) {
                                                        int t = start;
                                                        start = end;
                                                        end = t;
                                                }
                                                for (int i = start; i <= end; i++) {
                                                        if (i >= 0 && i <= 8)
                                                                uniqueSlots.add(i);
                                                }
                                        }
                                } else {
                                        int val = Integer.parseInt(part);
                                        if (val >= 0 && val <= 8)
                                                uniqueSlots.add(val);
                                }
                        } catch (NumberFormatException ignored) {
                        }
                }
                List<Integer> result = new ArrayList<>(uniqueSlots);
                Collections.sort(result);
                return result;
        }
}
