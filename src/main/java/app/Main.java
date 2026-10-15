package app;

import cache.InMemoryCache;
import network.*;
import java.util.concurrent.*;

public final class Main {
    private Main() { }
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !args[0].equals("node")) {
            System.err.println("Usage: node <port> [capacity]");
            System.exit(1);
        }
        var cache = new InMemoryCache<String, String>(args.length > 2 ? Integer.parseInt(args[2]) : 10000);
        var cleanup = Executors.newSingleThreadScheduledExecutor();
        cleanup.scheduleAtFixedRate(cache::cleanupExpired, 1, 1, TimeUnit.SECONDS);
        var server = new TcpServer("127.0.0.1", Integer.parseInt(args[1]), new CacheHandler(cache));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cleanup.shutdownNow();
            try { server.close(); } catch (Exception e) { System.err.println(e.getMessage()); }
        }));
        System.out.println("Cache listening on 127.0.0.1:" + server.port());
    }
}
