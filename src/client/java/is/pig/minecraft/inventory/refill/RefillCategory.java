package is.pig.minecraft.inventory.refill;

import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;

public enum RefillCategory {
    TOOL,
    WEAPON,
    FOOD,
    BLOCK,
    OTHER;

    public static RefillCategory fromStack(ItemStack stack) {
        if (stack.isEmpty())
            return OTHER;
        Item item = stack.getItem();

        // Check Food
        if (stack.get(DataComponents.FOOD) != null) {
            return FOOD;
        }

        // Check Weapons
        if (item instanceof SwordItem || item instanceof TridentItem || item instanceof BowItem
                || item instanceof CrossbowItem) {
            return WEAPON;
        }

        // Check Tools
        if (item instanceof DiggerItem || item instanceof ShearsItem || item instanceof FlintAndSteelItem
                || item instanceof FishingRodItem) {
            return TOOL;
        }

        // Check Blocks
        if (item instanceof BlockItem) {
            return BLOCK;
        }

        return OTHER;
    }

    public boolean matches(ItemStack original, ItemStack candidate) {
        if (original.isEmpty() || candidate.isEmpty())
            return false;

        // Exact match is always valid (and preferred, but this method just checks
        // compatibility)
        if (ItemStack.isSameItemSameComponents(original, candidate)) {
            return true;
        }

        RefillCategory catA = fromStack(original);
        RefillCategory catB = fromStack(candidate);

        if (catA != catB || catA == OTHER) {
            return false;
        }

        // Specific category logic
        switch (catA) {
            case FOOD:
                // Any food matches any food? Maybe too broad, but fulfills "Food" category
                // request
                return true;
            case WEAPON:
                // Sword matches Sword
                return (original.getItem() instanceof SwordItem && candidate.getItem() instanceof SwordItem) ||
                        (original.getItem() instanceof BowItem && candidate.getItem() instanceof BowItem) ||
                        (original.getItem() instanceof CrossbowItem && candidate.getItem() instanceof CrossbowItem);
            case TOOL:
                // Pickaxe matches Pickaxe, etc.
                if (original.getItem() instanceof DiggerItem && candidate.getItem() instanceof DiggerItem) {
                    // Check if they are the same TYPE of tool (Pickaxe vs Axe)
                    // This is tricky without access to tool types directly, but usually classes
                    // differ (PickaxeItem vs AxeItem)
                    return original.getItem().getClass().isAssignableFrom(candidate.getItem().getClass()) ||
                            candidate.getItem().getClass().isAssignableFrom(original.getItem().getClass());
                }
                return false; // Strict for other tools
            case BLOCK:
                // Blocks should ideally be exact matches.
                // Replacing 'Oak Plank' with 'Cobblestone' automatically while building is
                // probably annoying.
                // Resetting to strict for blocks unless user asked otherwise.
                // Re-reading request: "Refill stacks when empty... with matching ones...
                // extends with categories for weapons, tools and food"
                // Implies Blocks -> Strict Match, Others -> Category Match.
                return false;
            default:
                return false;
        }
    }
}
