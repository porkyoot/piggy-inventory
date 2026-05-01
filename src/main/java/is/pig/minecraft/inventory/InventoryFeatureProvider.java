package is.pig.minecraft.inventory;

import is.pig.minecraft.api.registry.PiggyServiceRegistry;
import is.pig.minecraft.api.spi.FeatureProvider;
import is.pig.minecraft.api.spi.InputAdapter;
import is.pig.minecraft.api.spi.PiggyFeature;
import is.pig.minecraft.inventory.handler.*;

import java.util.ArrayList;
import java.util.List;

public class InventoryFeatureProvider implements FeatureProvider {

    private final List<PiggyFeature> features = new ArrayList<>();

    @Override
    public void init(Object client) {
        InputAdapter input = PiggyServiceRegistry.getInputAdapter();
        input.registerKey("piggy-inventory:sort", "R", "Piggy Inventory");
        input.registerKey("piggy-inventory:lock_slot", "LEFT_ALT", "Piggy Inventory");
        input.registerKey("piggy-inventory:loot_matching", "UNKNOWN", "Piggy Inventory");
        input.registerKey("piggy-inventory:loot_all", "UNKNOWN", "Piggy Inventory");
    }

    @Override
    public List<PiggyFeature> getFeatures() {
        return features;
    }

    @Override
    public void onTick(Object client) {
        InputAdapter input = PiggyServiceRegistry.getInputAdapter();
        if (input.isKeyDown("piggy-inventory:sort")) {
            // Trigger sort logic
            // Need to refactor SortHandler to be agnostic too
        }

        SortHandler.getInstance().onTick(client);
        AutoRefillHandler.getInstance().onTick(client);
        CraftingHandler.getInstance().onTick(client);
        TradeHandler.getInstance().onTick(client);
        QuickLootHandler.getInstance().onTick(client);
    }
}
