package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.lib.sorting.ISorter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.awt.Color;

public class ColorSorter implements ISorter {

    @Override
    public void sort(List<ItemStack> items) {
        items.sort(getComparator());
    }

    @Override
    public Comparator<ItemStack> getComparator() {
        return Comparator.comparingInt(this::getItemHue)
                .thenComparing(stack -> stack.getDisplayName().getString());
    }

    private int getItemHue(ItemStack stack) {
        int color = getItemColor(stack);
        // Extract Hue from RGB
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        // Return Hue as int bits (0-360 mapped to integer range approx)
        return (int) (hsb[0] * 1000);
    }

    private int getItemColor(ItemStack stack) {
        // 1. Try ItemColors (Tint) - DISABLED due to mapping issues
        // Usually layer 0 is the main body
        /*
         * try {
         * int tint = Minecraft.getInstance().getItemColors().getColor(stack, 0);
         * if (tint != -1) return tint;
         * } catch (Exception e) {
         * // Ignore context errors
         * }
         */

        // 2. Try MapColor (Blocks)
        if (stack.getItem() instanceof BlockItem) {
            try {
                // MapColor requires BlockState. Default BlockState?
                net.minecraft.world.level.block.state.BlockState state = ((BlockItem) stack.getItem()).getBlock()
                        .defaultBlockState();
                return state.getMapColor(null, null).col;
                // Problem: getMapColor needs BlockGetter and Pos. Passing null might NPE
                // locally.
                // MapColor usually has a constant raw id? No, checks state.
                // Safe check: default state MapColor often accessible?
                // Actually `MapColor` field on Material is better but deprecated logic.
            } catch (Exception e) {
            }
        }

        // 3. Fallback: Hash of item ID seeded to a color
        // Returns a somewhat stable "color" int based on item name
        return stack.getItem().getDescriptionId().hashCode() & 0xFFFFFF;
    }

    @Override
    public String getId() {
        return "color";
    }

    @Override
    public String getName() {
        return "Color";
    }
}
