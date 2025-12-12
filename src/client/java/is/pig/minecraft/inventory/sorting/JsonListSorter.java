package is.pig.minecraft.inventory.sorting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import is.pig.minecraft.lib.sorting.ISorter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class JsonListSorter implements ISorter {

    private static final Logger LOGGER = LoggerFactory.getLogger("PiggyInventory");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("piggy-inventory/custom_sort.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Map of ItemID -> Index
    private static Map<String, Integer> sortIndexMap = null;

    public JsonListSorter() {
        if (sortIndexMap == null) {
            loadConfig();
        }
    }

    private static void loadConfig() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                List<String> list = GSON.fromJson(reader, new TypeToken<List<String>>() {
                }.getType());
                buildIndexMap(list);
            } catch (IOException e) {
                LOGGER.error("Failed to load custom_sort.json", e);
                sortIndexMap = new HashMap<>();
            }
        } else {
            createDefaultConfig();
        }
    }

    private static void createDefaultConfig() {
        List<String> defaultList = Arrays.asList(
                "minecraft:torch",
                "minecraft:coal",
                "minecraft:diamond",
                "minecraft:iron_ingot",
                "minecraft:gold_ingot",
                "minecraft:cobblestone",
                "minecraft:dirt");

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(defaultList, writer);
            }
            buildIndexMap(defaultList);
        } catch (IOException e) {
            LOGGER.error("Failed to create default custom_sort.json", e);
            sortIndexMap = new HashMap<>();
        }
    }

    private static void buildIndexMap(List<String> list) {
        sortIndexMap = new HashMap<>();
        if (list == null)
            return;
        for (int i = 0; i < list.size(); i++) {
            // We store the index. Lower index = Higher priority (comes first)
            sortIndexMap.putIfAbsent(list.get(i), i);
        }
    }

    /* allow reloading explicitly if needed later */
    public static void reload() {
        loadConfig();
    }

    @Override
    public void sort(List<ItemStack> items) {
        items.sort(getComparator());
    }

    @Override
    public Comparator<ItemStack> getComparator() {
        return (stack1, stack2) -> {
            String id1 = BuiltInRegistries.ITEM.getKey(stack1.getItem()).toString();
            String id2 = BuiltInRegistries.ITEM.getKey(stack2.getItem()).toString();

            Integer idx1 = sortIndexMap.get(id1);
            Integer idx2 = sortIndexMap.get(id2);

            boolean has1 = idx1 != null;
            boolean has2 = idx2 != null;

            if (has1 && has2) {
                return Integer.compare(idx1, idx2);
            } else if (has1) {
                return -1; // 1 comes first
            } else if (has2) {
                return 1; // 2 comes first
            } else {
                // Alphabetical if neither in list
                return id1.compareTo(id2);
            }
        };
    }

    @Override
    public String getId() {
        return "json_list";
    }

    @Override
    public String getName() {
        return "Custom List (JSON)";
    }
}
