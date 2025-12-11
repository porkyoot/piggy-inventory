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

public class ConfigScreenFactory {

        public static Screen create(Screen parent) {
                PiggyConfig config = PiggyConfig.getInstance();

                return YetAnotherConfigLib.createBuilder()
                                .title(Component.literal("Piggy Inventory Configuration"))

                                // SAFETY CATEGORY
                                .category(ConfigCategory.createBuilder()
                                                .name(Component.literal("Safety"))
                                                .tooltip(is.pig.minecraft.lib.I18n.safetyTooltip())
                                                .option(is.pig.minecraft.lib.ui.NoCheatingModeOption.create(
                                                                parent,
                                                                config::isNoCheatingMode,
                                                                config::setNoCheatingMode,
                                                                ConfigPersistence::save))
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
                                                                .available(config.isToolSwapEditable()) // Gray out if enforced
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
                                                                                                "Format: '0-3, 5, 7-8'"),
                                                                                Component.literal(""),
                                                                                Component.literal(
                                                                                                "Slots are 0-indexed (0 is the first slot).")))
                                                                .binding(
                                                                                formatSlotList(config
                                                                                                .getSwapHotbarSlots()),
                                                                                () -> formatSlotList(config
                                                                                                .getSwapHotbarSlots()),
                                                                                (val) -> config.setSwapHotbarSlots(
                                                                                                parseSlotString(val)))
                                                                .controller(StringControllerBuilder::create)
                                                                .build())

                                                .option(Option.<PiggyConfig.OrePreference>createBuilder()
                                                                .name(Component.literal("Ore Preference"))
                                                                .description(OptionDescription.of(
                                                                                Component.literal(
                                                                                                "Prefer Silk Touch or Fortune for ores?")))
                                                                .binding(
                                                                                PiggyConfig.OrePreference.FORTUNE,
                                                                                config::getOrePreference,
                                                                                config::setOrePreference)
                                                                .controller(opt -> EnumControllerBuilder.create(opt)
                                                                                .enumClass(PiggyConfig.OrePreference.class))
                                                                .build())

                                                .option(ListOption.<String>createBuilder()
                                                                .name(Component.literal("Silk Touch Blocks"))
                                                                .description(OptionDescription.of(
                                                                                Component.literal(
                                                                                                "Blocks that ALWAYS require Silk Touch."),
                                                                                Component.literal(
                                                                                                "Supports IDs (minecraft:glass) or Wildcards (*glass*).")))
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
                                                                                                "Blocks affected by Ore Preference (Fortune vs Silk)."),
                                                                                Component.literal(
                                                                                                "Supports IDs (minecraft:coal_ore) or Wildcards (*_ore).")))
                                                                .binding(
                                                                                config.getFortuneBlocks(),
                                                                                config::getFortuneBlocks,
                                                                                config::setFortuneBlocks)
                                                                .controller(StringControllerBuilder::create)
                                                                .initial("")
                                                                .build())

                                                .option(ListOption.<String>createBuilder()
                                                                .name(Component.literal("Shears Blocks"))
                                                                .description(OptionDescription.of(
                                                                                Component.literal(
                                                                                                "Blocks that require Shears to drop."),
                                                                                Component.literal(
                                                                                                "e.g. Vines, Leaves, Grass.")))
                                                                .binding(
                                                                                config.getShearsBlocks(),
                                                                                config::getShearsBlocks,
                                                                                config::setShearsBlocks)
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
                if (start == end) {
                        sb.append(start);
                } else if (end == start + 1) {
                        sb.append(start).append(", ").append(end);
                } else {
                        sb.append(start).append("-").append(end);
                }
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

                                                // Handle reverse range 5-2 -> 2-5
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
                                // Ignore bad inputs
                        }
                }

                List<Integer> result = new ArrayList<>(uniqueSlots);
                Collections.sort(result);
                return result;
        }
}