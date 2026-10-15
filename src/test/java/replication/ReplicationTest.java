package replication;

import static org.junit.jupiter.api.Assertions.*;

import cache.InMemoryCache;
import java.util.List;
import java.util.concurrent.*;
import network.*;
import org.junit.jupiter.api.Test;

class ReplicationTest {
  @Test
  void replicatedWritesDeletesAndFailureSemantics() throws Exception {
    var primaryCache = new InMemoryCache<String, String>(100);
    var replicaCache = new InMemoryCache<String, String>(100);
    try (var replica =
        new TcpServer("127.0.0.1", 0, new ReplicaHandler(new CacheHandler(replicaCache)))) {
      var replicaClient = new CacheClient("127.0.0.1", replica.port());
      var handler = new ReplicatedHandler(new CacheHandler(primaryCache), List.of(replicaClient));
      try (var primary = new TcpServer("127.0.0.1", 0, handler)) {
        var client = new CacheClient("127.0.0.1", primary.port());
        assertEquals("OK", client.request("SET k value"));
        assertEquals("VALUE value", replicaClient.request("GET k"));
        assertEquals("ERROR READ_ONLY", replicaClient.request("SET k wrong"));
        assertEquals("DELETED", client.request("DELETE k"));
        assertEquals("NULL", replicaClient.request("GET k"));
        assertEquals("OK", client.request("SETTTL k 0 expired"));
        assertNull(primaryCache.get("k"));
        assertNull(replicaCache.get("k"));
        replica.close();
        assertEquals(
            "ERROR REPLICATION_FAILED local_write_applied", client.request("SET k retained"));
        assertEquals("VALUE retained", client.request("GET k"));
      }
    }
  }

  @Test
  void delayedReplicationDoesNotRestartTtl() throws Exception {
    var replicaCache = new InMemoryCache<String, String>(10);
    var replicaHandler = new ReplicaHandler(new CacheHandler(replicaCache));
    try (var replica =
        new TcpServer(
            "127.0.0.1",
            0,
            request -> {
              try {
                Thread.sleep(50);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return replicaHandler.apply(request);
            })) {
      var primaryCache = new InMemoryCache<String, String>(10);
      var primary =
          new ReplicatedHandler(
              new CacheHandler(primaryCache),
              List.of(new CacheClient("127.0.0.1", replica.port())));
      assertEquals("OK", primary.apply("SETTTL k 1 value"));
      assertNull(primaryCache.get("k"));
      assertNull(replicaCache.get("k"));
      assertEquals(
          "ERROR INVALID_REQUEST", primary.apply("SET k " + "v".repeat(LineIO.MAX_LINE - 6)));
      assertNull(primaryCache.get("k"));
    }
  }

  @Test
  void concurrentUpdatesKeepReplicaInPrimaryOrder() throws Exception {
    var primaryCache = new InMemoryCache<String, String>(10);
    var replicaCache = new InMemoryCache<String, String>(10);
    try (var replica =
            new TcpServer("127.0.0.1", 0, new ReplicaHandler(new CacheHandler(replicaCache)));
        var pool = Executors.newFixedThreadPool(8)) {
      var handler =
          new ReplicatedHandler(
              new CacheHandler(primaryCache),
              List.of(new CacheClient("127.0.0.1", replica.port())));
      var tasks = new java.util.ArrayList<Callable<String>>();
      for (int i = 0; i < 100; i++) {
        int value = i;
        tasks.add(() -> handler.apply("SET k " + value));
      }
      for (var result : pool.invokeAll(tasks)) assertEquals("OK", result.get());
      assertEquals(primaryCache.get("k"), replicaCache.get("k"));
    }
  }

  @Test
  void ttlUsesOneAbsoluteDeadlineAndAttemptsEveryReplica() throws Exception {
    var observed = new java.util.concurrent.CopyOnWriteArrayList<String>();
    try (var first = new TcpServer("127.0.0.1", 0, request -> "ERROR unavailable");
        var second =
            new TcpServer(
                "127.0.0.1",
                0,
                request -> {
                  observed.add(request);
                  return "OK";
                })) {
      var handler =
          new ReplicatedHandler(
              new CacheHandler(new InMemoryCache<>(10)),
              List.of(
                  new CacheClient("127.0.0.1", first.port()),
                  new CacheClient("127.0.0.1", second.port())));
      long before = System.currentTimeMillis();
      assertEquals(
          "ERROR REPLICATION_FAILED local_write_applied", handler.apply("SETTTL key 5000 value"));
      String[] parts = observed.getFirst().split(" ");
      assertEquals("REPL", parts[0]);
      assertEquals("SETAT", parts[1]);
      long expiry = Long.parseLong(parts[3]);
      assertTrue(expiry >= before + 5000 && expiry <= System.currentTimeMillis() + 5000);
      assertEquals("ERROR INVALID_REQUEST", handler.apply("SETTTL key 9223372036854775807 value"));
    }
  }
}
