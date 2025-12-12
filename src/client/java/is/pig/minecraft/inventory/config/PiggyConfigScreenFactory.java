package is.pig.minecraft.inventory.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
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
                                .binding(
                                        true,
                                        config::isNoCheatingMode,
                                        config::setNoCheatingMode)
                                .controller(TickBoxControllerBuilder::create)
                                .available(config.isGlobalCheatsEditable()) // Gray out
                                                                            // if server
                                                                            // enforces
                                                                            // rules
                                .build())
                        .build())

                // TOOL SWAP CATEGORY
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Tool Swap"))
                        .tooltip(Component.literal("Configure automatic tool swapping."))

                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Enable Tool Swap"))
                                .description(OptionDescription.of(
                                        Component.literal(
                                                "Automatically swaps to the faster tool in your inventory when attacking a block."),
                                        Component.literal(
                                                "If Anti-Cheat is active, this cannot be enabled.")))
                                .available(config.isToolSwapEditable()) // Gray out if
                                                                        // enforced
                                .binding(
                                        true,
                                        config::isToolSwapEnabled,
                                        config::setToolSwapEnabled)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .option(Option.<String>createBuilder()
                                .name(Component.literal("Hotbar Swap Slots"))
                                .description(OptionDescription.of(
                                        Component.literal(
                                                "Which hotbar slots can be overwritten/used when swapping?"),
                                        Component.literal(
                                                "Format: '0-3, 5, 7-8'")))
                                .binding(
                                        formatSlotList(config
                                                .getSwapHotbarSlots()),
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
                                                "Prefer Silk Touch or Fortune for ores?")))
                                .binding(
                                        PiggyInventoryConfig.OrePreference.FORTUNE,
                                        config::getOrePreference,
                                        config::setOrePreference)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(PiggyInventoryConfig.OrePreference.class))
                                .build())

                        .option(ListOption.<String>createBuilder()
                                .name(Component.literal("Silk Touch Blocks"))
                                .description(OptionDescription.of(
                                        Component.literal(
                                                "Blocks that require Silk Touch to drop.")))
                                .binding(
                                        config.getSilkTouchBlocks(),
                                        config::getSilkTouchBlocks,
                                        config::setSilkTouchBlocks)
                                .controller(StringControllerBuilder::create)
                                .initial("")
                                .build())

                        .option(ListOption.<String>createBuilder()
                                .name(Component.literal("Fortune/Ore Blocks"))
                                .description(OptionDescription.of(
                                        Component.literal(
                                                "Blocks affected by Fortune preference.")))
                                .binding(
                                        config.getFortuneBlocks(),
                                        config::getFortuneBlocks,
                                        config::setFortuneBlocks)
                                .controller(StringControllerBuilder::create)
                                .initial("")
                                .build())

                        .option(ListOption.<String>createBuilder()
                                .name(Component.literal("Protected Blocks"))
                                .description(OptionDescription.of(
                                        Component.literal(
                                                "Blocks that must NOT be broken when in Silk Touch mode."),
                                        Component.literal(
                                                "Use this to prevent accidental breaking of fragile blocks.")))
                                .binding(
                                        config.getProtectedBlocks(),
                                        config::getProtectedBlocks,
                                        config::setProtectedBlocks)
                                .controller(StringControllerBuilder::create)
                                .initial("")
                                .build())

                        .option(ListOption.<String>createBuilder()
                                .name(Component.literal("Shears Blocks"))
                                .binding(
                                        config.getShearsBlocks(),
                                        config::getShearsBlocks,
                                        config::setShearsBlocks)
                                .controller(StringControllerBuilder::create)
                                .initial("")
                                .build())
                        .build())

                // WEAPON SWITCH CATEGORY
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Weapon Switch"))
                        .tooltip(Component.literal("Configure automatic weapon switching."))

                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Enable Weapon Switch"))
                                .description(OptionDescription.of(
                                        Component.literal(
                                                "Automatically swaps to the best weapon when attacking an entity."),
                                        Component.literal(
                                                "If Anti-Cheat is active, this cannot be enabled.")))
                                .available(config.isWeaponSwitchEditable())
                                .binding(
                                        true,
                                        config::isWeaponSwitchBoolean,
                                        config::setWeaponSwitchBoolean)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .option(Option.<String>createBuilder()
                                .name(Component.literal("Weapon Hotbar Swap Slots"))
                                .description(OptionDescription.of(
                                        Component.literal(
                                                "Which hotbar slots can be overwritten/used when swapping weapons?"),
                                        Component.literal(
                                                "Format: '0-3, 5, 7-8'")))
                                .binding(
                                        formatSlotList(config
                                                .getWeaponSwapHotbarSlots()),
                                        () -> formatSlotList(config
                                                .getWeaponSwapHotbarSlots()),
                                        (val) -> config.setWeaponSwapHotbarSlots(
                                                parseSlotString(val)))
                                .controller(StringControllerBuilder::create)
                                .build())

                        .option(Option.<PiggyInventoryConfig.WeaponPreference>createBuilder()
                                .name(Component.literal("Weapon Preference"))
                                .description(OptionDescription.of(
                                        Component.literal(
                                                "Which type of weapon do you prefer?"),
                                        Component.literal(
                                                "Speed (Sword), Damage (Axe), or Range.")))
                                .binding(
                                        PiggyInventoryConfig.WeaponPreference.DAMAGE,
                                        config::getGuiWeaponPreference,
                                        config::setGuiWeaponPreference)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(PiggyInventoryConfig.WeaponPreference.class))
                                .build())

                        .option(ListOption.<String>createBuilder()
                                .name(Component.literal("Speed Weapons Priority"))
                                .description(OptionDescription.of(Component.literal(
                                        "Priority list for 'Speed' preference (Top = Highest).")))
                                .binding(
                                        config.getFastWeapons(),
                                        config::getFastWeapons,
                                        config::setFastWeapons)
                                .controller(StringControllerBuilder::create)
                                .initial("")
                                .build())

                        .option(ListOption.<String>createBuilder()
                                .name(Component.literal("Damage Weapons Priority"))
                                .description(OptionDescription.of(Component.literal(
                                        "Priority list for 'Damage' preference (Top = Highest).")))
                                .binding(
                                        config.getHeavyWeapons(),
                                        config::getHeavyWeapons,
                                        config::setHeavyWeapons)
                                .controller(StringControllerBuilder::create)
                                .initial("")
                                .build())

                        .option(ListOption.<String>createBuilder()
                                .name(Component.literal("Range Weapons Priority"))
                                .description(OptionDescription.of(Component.literal(
                                        "Priority list for 'Range' preference (Top = Highest).")))
                                .binding(
                                        config.getRangeWeapons(),
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

                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Lock Hotbar Default"))
                                .description(OptionDescription
                                        .of(Component.literal("If true, hotbar slots are locked by default.")))
                                .binding(true, config::isLockHotbar, config::setLockHotbar)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<PiggyInventoryConfig.SortingAlgorithm>createBuilder()
                                .name(Component.literal("Default Algorithm"))
                                .binding(PiggyInventoryConfig.SortingAlgorithm.SMART, config::getDefaultAlgorithm,
                                        config::setDefaultAlgorithm)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(PiggyInventoryConfig.SortingAlgorithm.class))
                                .build())
                        .option(Option.<PiggyInventoryConfig.SortingLayout>createBuilder()
                                .name(Component.literal("Default Layout"))
                                .binding(PiggyInventoryConfig.SortingLayout.COMPACT, config::getDefaultLayout,
                                        config::setDefaultLayout)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(PiggyInventoryConfig.SortingLayout.class))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Sort Delay (Ticks)"))
                                .description(OptionDescription.of(Component.literal("Ticks between click actions.")))
                                .binding(1, config::getTickDelay, config::setTickDelay)
                                .controller(opt -> dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder
                                        .create(opt).range(0, 10).step(1))
                                .build())
                        // Blacklists
                        .option(ListOption.<String>createBuilder()
                                .name(Component.literal("Ignored Screen Classes"))
                                .binding(new ArrayList<>(), config::getBlacklistedInventories,
                                        config::setBlacklistedInventories)
                                .controller(StringControllerBuilder::create)
                                .initial("")
                                .build())
                        .option(ListOption.<String>createBuilder()
                                .name(Component.literal("Ignored Items"))
                                .binding(new ArrayList<>(), config::getBlacklistedItems, config::setBlacklistedItems)
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
