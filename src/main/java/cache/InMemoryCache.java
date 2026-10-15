package cache;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class InMemoryCache<K, V> implements Cache<K, V> {
  private final int capacity;
  private final Clock clock;
  private final Map<K, Node<K, V>> storage = new HashMap<>();

  private Node<K, V> head; // most recently used
  private Node<K, V> tail; // least recently used

  public InMemoryCache(int capacity) {
    this(capacity, Clock.systemUTC());
  }

  public InMemoryCache(int capacity, Clock clock) {
    if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
    this.capacity = capacity;
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public synchronized void put(K key, V value) {
    put(key, value, -1);
  }

  @Override
  public synchronized void put(K key, V value, long ttlMillis) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);
    if (ttlMillis < -1) throw new IllegalArgumentException("invalid TTL");
    long expiresAt = ttlMillis == -1 ? -1 : Math.addExact(clock.millis(), ttlMillis);

    if (storage.containsKey(key)) {
      Node<K, V> node = storage.get(key);
      node.entry = new CacheEntry<>(value, expiresAt);
      moveToFront(node);
      return;
    }

    if (storage.size() >= capacity) {
      cleanupExpired();
      if (storage.size() >= capacity) evictLRU();
    }

    Node<K, V> node = new Node<>(key, new CacheEntry<>(value, expiresAt));
    storage.put(key, node);
    addToFront(node);
  }

  @Override
  public synchronized V get(K key) {
    Node<K, V> node = storage.get(key);

    if (node == null) {
      return null;
    }

    if (node.entry.isExpired(clock.millis())) {
      remove(key);
      return null;
    }

    moveToFront(node);
    return node.entry.getValue();
  }

  @Override
  public synchronized V remove(K key) {
    Node<K, V> node = storage.remove(key);

    if (node == null) {
      return null;
    }

    removeNode(node);
    return node.entry.isExpired(clock.millis()) ? null : node.entry.getValue();
  }

  @Override
  public synchronized boolean containsKey(K key) {
    Node<K, V> node = storage.get(key);
    if (node == null) return false;
    if (node.entry.isExpired(clock.millis())) {
      storage.remove(key);
      removeNode(node);
      return false;
    }
    return true;
  }

  @Override
  public synchronized int size() {
    cleanupExpired();
    return storage.size();
  }

  @Override
  public synchronized boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public synchronized void clear() {
    storage.clear();
    head = null;
    tail = null;
  }

  public synchronized void cleanupExpired() {
    var iterator = storage.values().iterator();
    long now = clock.millis();
    while (iterator.hasNext()) {
      var node = iterator.next();
      if (node.entry.isExpired(now)) {
        iterator.remove();
        removeNode(node);
      }
    }
  }

  private void moveToFront(Node<K, V> node) {
    removeNode(node);
    addToFront(node);
  }

  private void addToFront(Node<K, V> node) {
    node.prev = null;
    node.next = head;

    if (head != null) {
      head.prev = node;
    }

    head = node;

    if (tail == null) {
      tail = node;
    }
  }

  private void removeNode(Node<K, V> node) {
    if (node.prev != null) {
      node.prev.next = node.next;
    } else {
      head = node.next;
    }

    if (node.next != null) {
      node.next.prev = node.prev;
    } else {
      tail = node.prev;
    }

    node.prev = null;
    node.next = null;
  }

  private void evictLRU() {
    if (tail == null) return;
    storage.remove(tail.key);
    removeNode(tail);
  }
}
