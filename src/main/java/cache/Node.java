package cache;

public class Node<K, V> {
  final K key;
  CacheEntry<V> entry;
  Node<K, V> prev;
  Node<K, V> next;

  public Node(K key, CacheEntry<V> entry) {
    this.key = key;
    this.entry = entry;
  }
}
