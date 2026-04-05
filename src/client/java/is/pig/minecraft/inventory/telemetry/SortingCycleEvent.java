package is.pig.minecraft.inventory.telemetry;

import is.pig.minecraft.lib.util.perf.PerfMonitor;
import is.pig.minecraft.lib.util.telemetry.StructuredEvent;
import net.minecraft.client.Minecraft;
import org.slf4j.event.Level;
import java.util.Map;

/**
 * Structured event captured when an inventory sorting cycle is planned and executed.
 */
public record SortingCycleEvent(
    long timestamp,
    long tick,
    Level level,
    double tps,
    double mspt,
    double cps,
    String pos,
    int containerId,
    int moveCount,
    boolean isCycleResolution
) implements StructuredEvent {

    public SortingCycleEvent(int containerId, int moveCount, boolean isCycleResolution) {
        this(
                System.currentTimeMillis(),
                Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0,
                Level.INFO,
                PerfMonitor.getInstance().getServerTps(),
                PerfMonitor.getInstance().getClientMspt(),
                PerfMonitor.getInstance().getCps(),
                Minecraft.getInstance().player != null ? 
                    String.format("(%.1f,%.1f,%.1f)", Minecraft.getInstance().player.getX(), Minecraft.getInstance().player.getY(), Minecraft.getInstance().player.getZ()) : "n/a",
                containerId,
                moveCount,
                isCycleResolution
        );
    }

    @Override
    public String getEventKey() {
        return "piggy.inventory.telemetry.sort_cycle";
    }

    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "containerId", containerId,
            "moveCount", moveCount,
            "isCycleResolution", isCycleResolution
        );
    }

    @Override
    public String getCategoryIcon() {
        return "✨";
    }

    @Override
    public String formatted() {
        return String.format("[%d] [Tick:%d] [SORT] Optimized %d moves for container %d (Cycle Resolution: %b)", 
                timestamp, tick, moveCount, containerId, isCycleResolution);
    }
}
