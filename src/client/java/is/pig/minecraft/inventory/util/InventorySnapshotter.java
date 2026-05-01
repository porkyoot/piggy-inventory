package is.pig.minecraft.inventory.util;

import is.pig.minecraft.api.registry.PiggyServiceRegistry;
import is.pig.minecraft.api.spi.ScreenAdapter;
import is.pig.minecraft.inventory.sorting.InventorySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridge between Minecraft inventory and the pure InventorySnapshot model.
 */
public class InventorySnapshotter {

    public static InventorySnapshot capture(Object client) {
        ScreenAdapter screenAdapter = PiggyServiceRegistry.getScreenAdapter();
        if (!screenAdapter.isContainerScreenOpen(client)) {
            return new InventorySnapshot(-1, List.of(), null);
        }
        
        int containerId = screenAdapter.getContainerId(client);
        List<Integer> allSlots = screenAdapter.getAllSlotIndices(client);
        
        List<InventorySnapshot.SlotState> slots = new ArrayList<>();
        for (int index : allSlots) {
            slots.add(new InventorySnapshot.SlotState(index, screenAdapter.getStackInSlot(client, index)));
        }
        
        return new InventorySnapshot(containerId, slots, screenAdapter.getCursorStack(client));
    }
}
