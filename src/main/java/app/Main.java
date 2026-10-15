package app;

import cache.CacheMaintenance;
import cache.InMemoryCache;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Function;
import network.*;
import replication.*;
import router.CacheRouter;

public final class Main {
  private Main() {}

  public static void main(String[] args) throws Exception {
    if (args.length >= 3 && args[0].equals("router")) {
      var nodes = new LinkedHashMap<String, CacheClient>();
      for (int i = 2; i < args.length; i++) {
        String[] endpoint = args[i].split(":", 2);
        if (endpoint.length != 2
            || nodes.putIfAbsent(
                    args[i], new CacheClient(endpoint[0], Integer.parseInt(endpoint[1])))
                != null) throw new IllegalArgumentException("invalid or duplicate endpoint");
      }
      var routing = new CacheRouter(nodes);
      var router = new TcpServer("127.0.0.1", Integer.parseInt(args[1]), routing);
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    routing.close();
                    try {
                      router.close();
                    } catch (Exception e) {
                      System.err.println(e.getMessage());
                    }
                  }));
      System.out.println("Router listening on 127.0.0.1:" + router.port());
      return;
    }
    if (args.length < 2 || !(args[0].equals("node") || args[0].equals("replica"))) {
      System.err.println(
          "Usage: node <port> [capacity] [replicaHost:port...] | replica <port> [capacity] | router"
              + " <port> <host:port>...");
      System.exit(1);
    }
    var cache =
        new InMemoryCache<String, String>(args.length > 2 ? Integer.parseInt(args[2]) : 10000);
    var local = new CacheHandler(cache);
    Function<String, String> handler;
    if (args[0].equals("replica")) {
      if (args.length > 3)
        throw new IllegalArgumentException("replica accepts no downstream replicas");
      handler = new ReplicaHandler(local);
    } else {
      var replicas = new ArrayList<CacheClient>();
      for (int i = 3; i < args.length; i++) {
        String[] endpoint = args[i].split(":", 2);
        if (endpoint.length != 2) throw new IllegalArgumentException("invalid replica endpoint");
        replicas.add(new CacheClient(endpoint[0], Integer.parseInt(endpoint[1])));
      }
      handler = new ReplicatedHandler(local, replicas);
    }
    var server = new TcpServer("127.0.0.1", Integer.parseInt(args[1]), handler);
    var cleanup = new CacheMaintenance(cache, 1000);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  cleanup.close();
                  if (handler instanceof ReplicatedHandler primary) primary.close();
                  try {
                    server.close();
                  } catch (Exception e) {
                    System.err.println(e.getMessage());
                  }
                }));
    System.out.println("Cache listening on 127.0.0.1:" + server.port());
  }
}
