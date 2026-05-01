package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.api.registry.PiggyServiceRegistry;
import is.pig.minecraft.api.spi.ItemDataAdapter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A immutable snapshot of the desired state of a Minecraft inventory after a sort.
 */
public record TargetInventorySnapshot(
        int containerId,
        Map<Integer, Object> slotTargets,
        Object cursorTarget,
        String sourceMod
) {
    public TargetInventorySnapshot copy() {
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        return new TargetInventorySnapshot(
                containerId,
                slotTargets.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> adapter.copy(e.getValue())
                        )),
                adapter.copy(cursorTarget),
                sourceMod
        );
    }
}
