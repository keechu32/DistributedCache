package cache;

public class CacheEntry<V> {
    private final V value;
    private final long expiresAtMillis; // use -1 for "never expires"

    public CacheEntry(V value, long expiresAtMillis) {
        this.value = value;
        this.expiresAtMillis = expiresAtMillis;
    }

    public V getValue() {
        return value;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isExpired() {
        return expiresAtMillis != -1 && System.currentTimeMillis() > expiresAtMillis;
    }
}