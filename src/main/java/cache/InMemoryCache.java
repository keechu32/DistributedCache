package cache;
import java.util.Map;
import java.util.HashMap;
public class InMemoryCache<K,V> implements Cache<K,V>{
    private final Map<K, CacheEntry<V>> storage = new HashMap<>();

    @Override
    public void put(K key, V value){
        storage.put(key, new CacheEntry<>(value, -1));
    }

    @Override
    public void put(K key, V value, long ttlMillis) {
        long expiresAt = System.currentTimeMillis() + ttlMillis;
        storage.put(key, new CacheEntry<>(value, expiresAt));
    }

    @Override
    public V get(K key){
        CacheEntry<V> entry = storage.get(key);

        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            storage.remove(key);
            return null;
        }

        return entry.getValue();
    }

    @Override
    public V remove(K key){
        CacheEntry<V> entry = storage.remove(key);
        return entry == null ? null : entry.getValue();
    }

    @Override
    public boolean containsKey(K key){
        return storage.containsKey(key);
    }

    @Override
    public int size() {
        return storage.size();
    }

    @Override
    public boolean isEmpty() {
        return storage.isEmpty();
    }

    @Override
    public void clear() {
        storage.clear();
    }

}