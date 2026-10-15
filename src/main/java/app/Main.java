package app;

import cache.InMemoryCache;
import network.*;
import router.CacheRouter;
import java.util.LinkedHashMap;
import java.util.concurrent.*;

public final class Main {
    private Main() { }
    public static void main(String[] args) throws Exception {
        if (args.length >= 3 && args[0].equals("router")) {
            var nodes = new LinkedHashMap<String, CacheClient>();
            for (int i = 2; i < args.length; i++) {
                String[] endpoint = args[i].split(":", 2);
                if (endpoint.length != 2 || nodes.putIfAbsent(args[i],
                        new CacheClient(endpoint[0], Integer.parseInt(endpoint[1]))) != null)
                    throw new IllegalArgumentException("invalid or duplicate endpoint");
            }
            var router = new TcpServer("127.0.0.1", Integer.parseInt(args[1]), new CacheRouter(nodes));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { router.close(); } catch (Exception e) { System.err.println(e.getMessage()); }
            }));
            System.out.println("Router listening on 127.0.0.1:" + router.port());
            return;
        }
        if (args.length < 2 || !args[0].equals("node")) {
            System.err.println("Usage: node <port> [capacity] | router <port> <host:port>...");
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
