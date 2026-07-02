package cache;
import java.util.Map;
import java.util.HashMap;
public class InMemoryCache<K,V> implements Cache<K,V>{
    private final int capacity;
    private final Map<K, Node<K, V>> storage = new HashMap<>();

    private Node<K, V> head; // most recently used
    private Node<K, V> tail; // least recently used

    public InMemoryCache(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public void put(K key, V value){
        put(key, value, -1);
    }

    @Override
    public void put(K key, V value, long ttlMillis) {
        long expiresAt = ttlMillis == -1 ? -1 : System.currentTimeMillis() + ttlMillis;

        if (storage.containsKey(key)) {
            Node<K, V> node = storage.get(key);
            node.entry = new CacheEntry<>(value, expiresAt);
            moveToFront(node);
            return;
        }

        if (storage.size() >= capacity) {
            evictLRU();
        }

        Node<K, V> node = new Node<>(key, new CacheEntry<>(value, expiresAt));
        storage.put(key, node);
        addToFront(node);
    }

    @Override
    public V get(K key){
        Node<K, V> node = storage.get(key);

        if (node == null) {
            return null;
        }

        if (node.entry.isExpired()) {
            remove(key);
            return null;
        }

        moveToFront(node);
        return node.entry.getValue();
    }

    @Override
    public V remove(K key){
        Node<K, V> node = storage.remove(key);

        if (node == null) {
            return null;
        }

        removeNode(node);
        return node.entry.getValue();

    }

    @Override
    public boolean containsKey(K key){
        Node<K, V> node = storage.get(key);
        return node != null && !node.entry.isExpired();
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
        head = null;
        tail = null;
    }

    private void moveToFront(Node<K, V> node){
        removeNode(node);
        addToFront(node);
    }

    private void addToFront(Node<K,V> node){
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

    private void removeNode(Node<K,V> node){
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

    private void evictLRU(){
        if(tail == null) return;
        storage.remove(tail.key);
        removeNode(tail);
    }
}