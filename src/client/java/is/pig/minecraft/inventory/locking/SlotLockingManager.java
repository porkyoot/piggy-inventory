package is.pig.minecraft.inventory.locking;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages locked slots for inventory screens.
 */
public class SlotLockingManager {

    private static final SlotLockingManager INSTANCE = new SlotLockingManager();

    private final Map<String, Set<Integer>> lockedSlots = new HashMap<>();

    public static SlotLockingManager getInstance() {
        return INSTANCE;
    }

    public boolean isLocked(AbstractContainerScreen<?> screen, int slotIndex) {
        String key = getScreenKey(screen);
        if (lockedSlots.containsKey(key)) {
            return lockedSlots.get(key).contains(slotIndex);
        }
        return false;
    }

    public void toggleLock(AbstractContainerScreen<?> screen, int slotIndex) {
        String key = getScreenKey(screen);
        lockedSlots.computeIfAbsent(key, k -> new HashSet<>());

        Set<Integer> locks = lockedSlots.get(key);
        if (locks.contains(slotIndex)) {
            locks.remove(slotIndex);
        } else {
            locks.add(slotIndex);
        }
    }

    private String getScreenKey(AbstractContainerScreen<?> screen) {
        return screen.getClass().getName();
    }
}
