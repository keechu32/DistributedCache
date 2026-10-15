package cache;

import java.util.concurrent.*;

/** Owns the periodic expiration worker; callers close it alongside the server. */
public final class CacheMaintenance implements AutoCloseable {
  private final ScheduledExecutorService worker;

  public CacheMaintenance(InMemoryCache<?, ?> cache, long intervalMillis) {
    if (intervalMillis <= 0)
      throw new IllegalArgumentException("cleanup interval must be positive");
    java.util.Objects.requireNonNull(cache);
    worker =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("cache-expiration").factory());
    worker.scheduleAtFixedRate(
        cache::cleanupExpired, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() {
    worker.shutdownNow();
  }
}
