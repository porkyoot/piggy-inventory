package is.pig.minecraft.inventory.refill;

import is.pig.minecraft.api.registry.PiggyServiceRegistry;
import is.pig.minecraft.api.spi.ItemDataAdapter;

public enum RefillCategory {
    TOOL,
    WEAPON,
    FOOD,
    BLOCK,
    OTHER;

    public static RefillCategory fromStack(Object stack) {
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        if (adapter.getCount(stack) == 0) return OTHER;

        String cat = adapter.getItemCategory(stack);
        return switch (cat) {
            case "tool" -> TOOL;
            case "weapon" -> WEAPON;
            case "food" -> FOOD;
            case "block" -> BLOCK;
            default -> OTHER;
        };
    }

    public boolean matches(Object original, Object candidate) {
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        if (adapter.getCount(original) == 0 || adapter.getCount(candidate) == 0) return false;

        if (adapter.areItemsEqual(original, candidate)) return true;

        RefillCategory catA = fromStack(original);
        RefillCategory catB = fromStack(candidate);

        if (catA != catB || catA == OTHER) return false;

        switch (catA) {
            case FOOD:
                return true;
            case WEAPON:
                // Lenient check based on ID keywords or more adapter helpers
                String idA = adapter.getItemId(original);
                String idB = adapter.getItemId(candidate);
                return (idA.contains("sword") && idB.contains("sword")) ||
                       (idA.contains("bow") && idB.contains("bow"));
            case TOOL:
                String toolA = adapter.getItemId(original);
                String toolB = adapter.getItemId(candidate);
                return (toolA.contains("pickaxe") && toolB.contains("pickaxe")) ||
                       (toolA.contains("axe") && toolB.contains("axe")) ||
                       (toolA.contains("shovel") && toolB.contains("shovel")) ||
                       (toolA.contains("hoe") && toolB.contains("hoe"));
            default:
                return false;
        }
    }
}
