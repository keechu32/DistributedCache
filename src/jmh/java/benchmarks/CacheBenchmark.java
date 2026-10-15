package benchmarks;

import cache.InMemoryCache;
import java.util.*;
import java.util.concurrent.TimeUnit;
import network.*;
import org.openjdk.jmh.annotations.*;
import replication.*;
import router.*;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class CacheBenchmark {
  private InMemoryCache<String, String> cache;
  private ConsistentHashRing ring;
  private CacheClient direct;
  private CacheClient routed;
  private CacheClient replicated;
  private final List<CacheClient> clients = new ArrayList<>();
  private final List<TcpServer> servers = new ArrayList<>();

  @Setup(Level.Trial)
  public void setup() throws Exception {
    cache = new InMemoryCache<>(10000);
    cache.put("hot", "value");
    ring = new ConsistentHashRing(List.of("a", "b", "c"), 256);
    direct = serve(new CacheHandler(new InMemoryCache<>(10000)));
    direct.request("SET hot value");
    CacheClient second = serve(new CacheHandler(new InMemoryCache<>(10000)));
    routed = serve(new CacheRouter(Map.of("a", direct, "b", second)));
    routed.request("SET hot value");
    CacheClient replica = serve(new ReplicaHandler(new CacheHandler(new InMemoryCache<>(10000))));
    replicated =
        serve(
            new ReplicatedHandler(new CacheHandler(new InMemoryCache<>(10000)), List.of(replica)));
  }

  private CacheClient serve(java.util.function.Function<String, String> handler) throws Exception {
    var server = new TcpServer("127.0.0.1", 0, handler);
    servers.add(server);
    var client = new CacheClient("127.0.0.1", server.port());
    clients.add(client);
    return client;
  }

  @TearDown(Level.Trial)
  public void teardown() throws Exception {
    for (CacheClient client : clients) client.close();
    for (TcpServer server : servers.reversed()) server.close();
  }

  private static String checked(String actual, String expected) {
    if (!expected.equals(actual)) throw new IllegalStateException("Unexpected response: " + actual);
    return actual;
  }

  @Benchmark
  public String localGet() {
    return cache.get("hot");
  }

  @Benchmark
  public void localSet() {
    cache.put("hot", "value");
  }

  @Benchmark
  public String hashLookup() {
    return ring.owner("hot");
  }

  @Benchmark
  public String tcpGet() throws Exception {
    return checked(direct.request("GET hot"), "VALUE value");
  }

  @Benchmark
  public String routedGet() throws Exception {
    return checked(routed.request("GET hot"), "VALUE value");
  }

  @Benchmark
  public String replicatedSet() throws Exception {
    return checked(replicated.request("SET hot value"), "OK");
  }
}
