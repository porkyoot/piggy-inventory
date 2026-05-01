package is.pig.minecraft.inventory.sorting;

import is.pig.minecraft.api.registry.PiggyServiceRegistry;
import is.pig.minecraft.api.spi.ItemDataAdapter;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Core mathematical engine for inventory sorting.
 * Operates on InventorySnapshots and generates abstract Move sequences.
 */
public class InventoryOptimizer {

    private ToIntFunction<Object> getCapacityProvider(InventorySnapshot currentSnapshot, InventorySnapshot targetSnapshot) {
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        if (targetSnapshot == null) return adapter::getMaxStackSize;
        
        Map<ItemKey, Integer> capacities = new HashMap<>();
        List<InventorySnapshot.SlotState> allSlots = new ArrayList<>(targetSnapshot.slots());
        if (currentSnapshot != null) allSlots.addAll(currentSnapshot.slots());

        for (InventorySnapshot.SlotState slot : allSlots) {
            Object stack = slot.stack();
            if (adapter.getCount(stack) == 0) continue;
            ItemKey key = new ItemKey(stack);
            
            int maxVanilla = adapter.getMaxStackSize(stack);
            if (adapter.getCount(stack) > maxVanilla) {
                capacities.put(key, Integer.MAX_VALUE);
            } else {
                capacities.put(key, Math.max(capacities.getOrDefault(key, maxVanilla), adapter.getCount(stack)));
            }
        }
        return stack -> capacities.getOrDefault(new ItemKey(stack), adapter.getMaxStackSize(stack));
    }

    private boolean isItemMatch(Object a, Object b) {
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        if (adapter.getCount(a) == 0 && adapter.getCount(b) == 0) return true;
        if (adapter.getCount(a) == 0 || adapter.getCount(b) == 0) return false;
        return adapter.areItemsEqual(a, b);
    }

    public List<Move> consolidate(InventorySnapshot currentState, InventorySnapshot targetSnapshot) {
        if (targetSnapshot == null) return new ArrayList<>(); 
        
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        ToIntFunction<Object> capacityProvider = getCapacityProvider(currentState, targetSnapshot);
        List<Move> moves = new ArrayList<>();
        InventorySnapshot virtual = currentState;
        Map<Integer, Object> targetMap = toMap(targetSnapshot.slots());
        Map<Integer, Object> slotMap = toMap(virtual.slots());

        Map<ItemKey, List<Integer>> itemLocations = new HashMap<>();
        for (InventorySnapshot.SlotState slot : virtual.slots()) {
            if (adapter.getCount(slot.stack()) == 0) continue;
            ItemKey key = new ItemKey(slot.stack());
            itemLocations.computeIfAbsent(key, k -> new ArrayList<>()).add(slot.index());
        }

        for (Map.Entry<ItemKey, List<Integer>> entry : itemLocations.entrySet()) {
            List<Integer> indices = entry.getValue();
            if (indices.size() < 2) continue;

            indices.sort((idx1, idx2) -> {
                boolean home1 = isHome(idx1, slotMap.get(idx1), targetMap);
                boolean home2 = isHome(idx2, slotMap.get(idx2), targetMap);
                
                if (home1 != home2) return home1 ? 1 : -1; 
                
                int count1 = adapter.getCount(slotMap.get(idx1));
                int count2 = adapter.getCount(slotMap.get(idx2));
                
                if (!home1) return Integer.compare(count1, count2);
                else return Integer.compare(count2, count1); 
            });

            for (int i = 0; i < indices.size() - 1; i++) {
                int sourceIdx = indices.get(i);
                if (isHome(sourceIdx, slotMap.get(sourceIdx), targetMap)) {
                    int cap = capacityProvider.applyAsInt(slotMap.get(sourceIdx));
                    if (adapter.getCount(slotMap.get(sourceIdx)) >= cap) continue;
                }

                for (int j = indices.size() - 1; j > i; j--) {
                    int targetIdx = indices.get(j);
                    Object source = slotMap.get(sourceIdx);
                    Object target = slotMap.get(targetIdx);
                    if (adapter.getCount(source) == 0) break;

                    int max = capacityProvider.applyAsInt(target);
                    int space = max - adapter.getCount(target);
                    int toMove = Math.min(space, adapter.getCount(source));

                    if (space <= 0) continue; 

                    List<Move> transferSteps = generateTransfer(sourceIdx, targetIdx, toMove, virtual, capacityProvider);
                    if (!transferSteps.isEmpty()) {
                        moves.addAll(transferSteps);
                        virtual = virtual.applyMoves(transferSteps, capacityProvider);
                        slotMap.put(sourceIdx, getStack(virtual, sourceIdx));
                        slotMap.put(targetIdx, getStack(virtual, targetIdx));
                    }
                }
            }
        }
        return moves;
    }

    public List<Move> planCycles(InventorySnapshot current, InventorySnapshot targetSnapshot) {
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        ToIntFunction<Object> capacityProvider = getCapacityProvider(current, targetSnapshot);
        List<Move> moves = new ArrayList<>();
        InventorySnapshot virtual = current;
        Map<Integer, Object> targetMap = toMap(targetSnapshot.slots());
        
        Set<Integer> visited = new HashSet<>();
        Set<Long> moveFlows = new HashSet<>(); 
        List<Integer> allIndices = targetSnapshot.slots().stream().map(InventorySnapshot.SlotState::index).toList();

        for (int i : allIndices) {
            if (visited.contains(i)) continue;
            if (adapter.getCount(virtual.cursor()) > 0) break;
            
            Object cur = getStack(virtual, i);
            Object tar = targetMap.getOrDefault(i, null);

            if (isSame(cur, tar)) {
                visited.add(i);
                continue;
            }

            if (isItemMatch(cur, tar) && findEmptySlot(virtual, allIndices) == -1) {
                visited.add(i);
                continue;
            }

            if (adapter.getCount(cur) > 64 || adapter.getCount(tar) > 64) {
               int dest = findDestination(cur, targetMap, visited, allIndices, virtual);
               if (dest != -1) {
                   Object destStack = getStack(virtual, dest);
                   if (adapter.getCount(destStack) > 0 && !isItemMatch(cur, destStack)) continue; 
                   if (moveFlows.contains(((long)dest << 32) | i)) continue;
                   
                   int amount = Math.min(adapter.getCount(cur), capacityProvider.applyAsInt(cur) - adapter.getCount(getStack(virtual, dest)));
                   if (amount > 0) {
                       List<Move> drain = generateTransfer(i, dest, amount, virtual, capacityProvider);
                       moves.addAll(drain);
                       virtual = virtual.applyMoves(drain, capacityProvider);
                       moveFlows.add(((long)i << 32) | dest);
                   }
                   if (isSame(getStack(virtual, i), targetMap.get(i))) visited.add(i);
                   if (isSame(getStack(virtual, dest), targetMap.get(dest))) visited.add(dest);
                   continue;
               }
            }

            int potentialNext = findDestination(cur, targetMap, visited, allIndices, virtual);
            if (potentialNext != -1) {
                if (adapter.getCount(getStack(virtual, potentialNext)) > 64 && !isItemMatch(cur, getStack(virtual, potentialNext))) {
                    continue;
                }
            }

            List<Move> pickup = generatePickup(i, virtual);
            moves.addAll(pickup);
            virtual = virtual.applyMoves(pickup, capacityProvider);
            visited.add(i); 

            int safety = 0;
            while (adapter.getCount(virtual.cursor()) > 0 && safety++ < 1000) {
                int nextSlot = findDestination(virtual.cursor(), targetMap, visited, allIndices, virtual);
                if (nextSlot == -1) {
                    nextSlot = findEmptySlot(virtual, allIndices);
                    if (nextSlot == -1) break;
                }

                if (adapter.getCount(getStack(virtual, nextSlot)) > 64) {
                    int dumpSlot = findEmptySlot(virtual, allIndices);
                    if (dumpSlot != -1) {
                        List<Move> dump = List.of(new Move(dumpSlot, Move.MoveType.DEPOSIT_ALL));
                        moves.addAll(dump);
                        virtual = virtual.applyMoves(dump, capacityProvider);
                    }
                    break; 
                }

                List<Move> swap = generateSwap(nextSlot, virtual);
                moves.addAll(swap);
                virtual = virtual.applyMoves(swap, capacityProvider);
                visited.add(nextSlot);

                if (isSame(virtual.cursor(), targetMap.getOrDefault(i, null))) {
                     List<Move> close = generateSwap(i, virtual);
                     moves.addAll(close);
                     virtual = virtual.applyMoves(close, capacityProvider);
                     break;
                }
            }
        }

        if (adapter.getCount(virtual.cursor()) > 0) {
            int dumpSlot = findEmptySlot(virtual, allIndices);
            if (dumpSlot == -1) {
                for (int idx : allIndices) {
                    if (adapter.getCount(getStack(virtual, idx)) <= 64) {
                        dumpSlot = idx;
                        break;
                    }
                }
            }
            if (dumpSlot != -1) {
                List<Move> dump = generateSwap(dumpSlot, virtual);
                moves.addAll(dump);
                virtual = virtual.applyMoves(dump, capacityProvider);
            }
        }
        return moves;
    }

    private List<Move> generateTransfer(int from, int to, int amount, InventorySnapshot state, ToIntFunction<Object> capacityProvider) {
        List<Move> moves = new ArrayList<>();
        int remaining = amount;
        int cursorLimit = 64; 
        while (remaining > 0) {
            int chunk = Math.min(remaining, cursorLimit);
            moves.add(new Move(from, Move.MoveType.PICKUP_ALL));
            moves.add(new Move(to, Move.MoveType.DEPOSIT_ALL));
            remaining -= chunk;
        }
        return moves;
    }

    private List<Move> generatePickup(int slot, InventorySnapshot state) {
        return List.of(new Move(slot, Move.MoveType.PICKUP_ALL));
    }

    private List<Move> generateSwap(int slot, InventorySnapshot state) {
        return List.of(new Move(slot, Move.MoveType.SWAP));
    }

    private int findDestination(Object cursor, Map<Integer, Object> targetMap, Set<Integer> visited, List<Integer> allIndices, InventorySnapshot virtual) {
        for (int i : allIndices) {
            if (visited.contains(i)) continue;
            Object targetStack = targetMap.getOrDefault(i, null);
            if (isItemMatch(cursor, targetStack)) {
                Object curSlotStack = getStack(virtual, i);
                if (PiggyServiceRegistry.getItemDataAdapter().getCount(curSlotStack) > 0 && isItemMatch(cursor, curSlotStack)) continue;
                return i;
            }
        }
        return -1;
    }

    private int findEmptySlot(InventorySnapshot state, List<Integer> allIndices) {
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        for (int i : allIndices) {
            if (adapter.getCount(getStack(state, i)) == 0) return i;
        }
        return -1;
    }

    private Map<Integer, Object> toMap(List<InventorySnapshot.SlotState> states) {
        return states.stream().collect(Collectors.toMap(InventorySnapshot.SlotState::index, InventorySnapshot.SlotState::stack));
    }

    private Object getStack(InventorySnapshot state, int index) {
        return state.slots().stream().filter(s -> s.index() == index).findFirst().map(InventorySnapshot.SlotState::stack).orElse(null);
    }

    private boolean isSame(Object a, Object b) {
        ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
        if (adapter.getCount(a) == 0 && adapter.getCount(b) == 0) return true;
        if (adapter.getCount(a) == 0 || adapter.getCount(b) == 0) return false;
        return adapter.areItemsEqual(a, b) && adapter.getCount(a) == adapter.getCount(b);
    }

    private boolean isHome(int slotIdx, Object stack, Map<Integer, Object> targetMap) {
        return isItemMatch(stack, targetMap.getOrDefault(slotIdx, null));
    }

    private static class ItemKey {
        private final String id;
        private final Object stack;

        ItemKey(Object stack) {
            ItemDataAdapter adapter = PiggyServiceRegistry.getItemDataAdapter();
            this.id = adapter.getItemId(stack);
            this.stack = stack;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ItemKey other) {
                return PiggyServiceRegistry.getItemDataAdapter().areItemsEqual(this.stack, other.stack);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
