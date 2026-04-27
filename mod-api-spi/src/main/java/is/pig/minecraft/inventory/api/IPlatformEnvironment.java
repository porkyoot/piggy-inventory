package is.pig.minecraft.inventory.api;

import java.nio.file.Path;

public interface IPlatformEnvironment {
    Path getConfigDirectory();
    boolean isClient();
    boolean isDedicatedServer();
}
