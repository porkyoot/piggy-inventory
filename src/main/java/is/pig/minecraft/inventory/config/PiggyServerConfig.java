package is.pig.minecraft.inventory.config;

import java.util.HashMap;
import java.util.Map;

public class PiggyServerConfig {
    private static PiggyServerConfig INSTANCE;

    public boolean allowCheats = true;
    public Map<String, Boolean> features = new HashMap<>();

    public static PiggyServerConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PiggyServerConfig();
        }
        return INSTANCE;
    }
}
