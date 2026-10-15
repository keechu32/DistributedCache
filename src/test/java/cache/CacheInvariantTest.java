package cache;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class CacheInvariantTest {
  private static final class MutableClock extends Clock {
    long now = 1000;

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return Instant.ofEpochMilli(now);
    }

    public long millis() {
      return now;
    }
  }

  @Test
  void exactDeadlineAndExpiredMruBeforeLiveLru() {
    var clock = new MutableClock();
    var cache = new InMemoryCache<String, String>(2, clock);
    cache.put("live", "value");
    cache.put("expired", "value", 10);
    clock.now += 10;
    cache.put("new", "value");
    assertEquals("value", cache.get("live"));
    assertNull(cache.get("expired"));
    assertEquals(2, cache.size());
    cache.put("new", "replacement", 1);
    clock.now++;
    assertNull(cache.remove("new"));
    cache.put("live", "forever", -1);
    clock.now += 100000;
    assertEquals("forever", cache.get("live"));
  }

  @Test
  void expirationAccountingAndValidation() {
    var clock = new MutableClock();
    var cache = new InMemoryCache<String, String>(2, clock);
    cache.put("a", "value", 0);
    assertFalse(cache.containsKey("a"));
    assertTrue(cache.isEmpty());
    cache.put("b", "value", 1);
    clock.now++;
    cache.cleanupExpired();
    assertEquals(0, cache.size());
    assertThrows(IllegalArgumentException.class, () -> new InMemoryCache<>(0));
    assertThrows(IllegalArgumentException.class, () -> cache.put("x", "v", -2));
    assertThrows(ArithmeticException.class, () -> cache.put("x", "v", Long.MAX_VALUE));
    assertThrows(NullPointerException.class, () -> cache.put("x", null));
  }

  @Test
  void randomizedLruMatchesReferenceModel() {
    var cache = new InMemoryCache<Integer, Integer>(7);
    var model = new LinkedHashMap<Integer, Integer>(16, 0.75f, true);
    var random = new Random(42);
    for (int i = 0; i < 10000; i++) {
      int key = random.nextInt(20);
      switch (random.nextInt(4)) {
        case 0 -> {
          cache.put(key, i);
          model.put(key, i);
          if (model.size() > 7) model.remove(model.keySet().iterator().next());
        }
        case 1 -> assertEquals(model.get(key), cache.get(key));
        case 2 -> assertEquals(model.remove(key), cache.remove(key));
        case 3 -> assertEquals(model.containsKey(key), cache.containsKey(key));
      }
      assertEquals(model.size(), cache.size());
    }
  }

  @Test
  void backgroundCleanupRemovesUnaccessedEntries() throws Exception {
    var expiredScan = new CountDownLatch(1);
    var now = new java.util.concurrent.atomic.AtomicLong(1000);
    Clock clock =
        new Clock() {
          public ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          public Clock withZone(ZoneId zone) {
            return this;
          }

          public Instant instant() {
            return Instant.ofEpochMilli(millis());
          }

          public long millis() {
            long value = now.get();
            if (value == 2000) expiredScan.countDown();
            return value;
          }
        };
    var cache = new InMemoryCache<String, String>(2, clock);
    cache.put("key", "value", 10);
    now.set(2000);
    try (var maintenance = new CacheMaintenance(cache, 10)) {
      assertTrue(expiredScan.await(2, TimeUnit.SECONDS));
      synchronized (cache) {
        // Rewind time to distinguish physical cleanup from lazy expiration.
        now.set(1000);
        assertNull(cache.get("key"));
      }
    }
  }

  @Test
  void concurrentCacheOperationsPreserveCapacity() throws Exception {
    var cache = new InMemoryCache<Integer, Integer>(32);
    try (var executor = Executors.newFixedThreadPool(8)) {
      var tasks = new ArrayList<Callable<Void>>();
      for (int t = 0; t < 8; t++) {
        int seed = t;
        tasks.add(
            () -> {
              var random = new Random(seed);
              for (int i = 0; i < 2000; i++) {
                int key = random.nextInt(100);
                cache.put(key, i);
                cache.get(random.nextInt(100));
                cache.remove(random.nextInt(100));
                assertTrue(cache.size() <= 32);
              }
              return null;
            });
      }
      for (var result : executor.invokeAll(tasks)) result.get();
    }
    cache.clear();
    for (int i = 0; i < 32; i++) cache.put(i, i);
    for (int i = 0; i < 32; i++) assertEquals(i, cache.get(i));
  }
}
