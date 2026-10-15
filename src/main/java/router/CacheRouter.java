package router;

import network.*;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

public final class CacheRouter implements Function<String, String> {
    private final Map<String, CacheClient> nodes;
    private final ConsistentHashRing ring;

    public CacheRouter(Map<String, CacheClient> nodes) {
        this.nodes = Map.copyOf(nodes);
        ring = new ConsistentHashRing(this.nodes.keySet(), 256);
    }

    public String owner(String key) { return ring.owner(key); }

    @Override
    public String apply(String request) {
        try {
            Command command = Command.parse(request);
            return nodes.get(ring.owner(command.key())).request(request);
        } catch (IllegalArgumentException e) { return "ERROR INVALID_REQUEST"; }
        catch (IOException e) { return "ERROR NODE_UNAVAILABLE"; }
    }
}
