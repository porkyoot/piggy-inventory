package is.pig.minecraft.inventory.common;

import is.pig.minecraft.inventory.api.IModAdapter;

public class ModCommon {
    private static IModAdapter adapter;

    public static void initialize(IModAdapter modAdapter) {
        adapter = modAdapter;
        System.out.println("piggy-inventory: ModCommon initialized with " + modAdapter.getClass().getSimpleName());
    }

    public static IModAdapter getAdapter() {
        return adapter;
    }
}
