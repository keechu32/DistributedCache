package replication;

import network.*;
import java.util.function.Function;

/** Trusted-network replication endpoint; ordinary client writes are rejected. */
public final class ReplicaHandler implements Function<String, String> {
    private final CacheHandler local;
    public ReplicaHandler(CacheHandler local) { this.local = local; }

    @Override
    public synchronized String apply(String request) {
        if (request.startsWith("REPL ")) return local.apply(request.substring(5));
        try {
            return Command.parse(request).operation().equals("GET") ? local.apply(request) : "ERROR READ_ONLY";
        } catch (IllegalArgumentException e) { return "ERROR INVALID_REQUEST"; }
    }
}
