package cache;

public interface Cache<K, V> {
  void put(K key, V value);

  void put(K key, V value, long ttlMillis);

  V get(K key);

  V remove(K key);

  boolean containsKey(K key);

  int size();

  boolean isEmpty();

  void clear();
}
