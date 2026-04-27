package is.pig.minecraft.inventory.api;

import java.util.UUID;

public interface INetworkDispatcher {
    void sendPayload(UUID playerUuid, String channel, byte[] data);
    void broadcastPayload(String channel, byte[] data);
}
