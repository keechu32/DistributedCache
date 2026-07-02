package cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryCacheTest {

    private InMemoryCache<String, String> cache;

    @BeforeEach
    void setUp() {
        cache = new InMemoryCache<>(1000);
    }

    @Test
    void testPutAndGet() {
        cache.put("name", "Krishna");
        assertEquals("Krishna", cache.get("name"));
    }

    @Test
    void testContainsKey() {
        cache.put("city", "Hyderabad");

        assertTrue(cache.containsKey("city"));
        assertFalse(cache.containsKey("country"));
    }

    @Test
    void testRemove() {
        cache.put("company", "Oracle");

        String removed = cache.remove("company");

        assertEquals("Oracle", removed);
        assertFalse(cache.containsKey("company"));
    }

    @Test
    void testSize() {
        assertEquals(0, cache.size());

        cache.put("one", "1");
        cache.put("two", "2");

        assertEquals(2, cache.size());
    }

    @Test
    void testIsEmpty() {
        assertTrue(cache.isEmpty());

        cache.put("key", "value");

        assertFalse(cache.isEmpty());
    }

    @Test
    void testClear() {
        cache.put("a", "A");
        cache.put("b", "B");

        cache.clear();

        assertTrue(cache.isEmpty());
        assertEquals(0, cache.size());
    }

    @Test
    void testOverwriteExistingKey() {
        cache.put("name", "Krishna");
        cache.put("name", "John");

        assertEquals("John", cache.get("name"));
        assertEquals(1, cache.size());
    }

    @Test
    void testGetMissingKey() {
        assertNull(cache.get("does_not_exist"));
    }

    @Test
    void testRemoveMissingKey() {
        assertNull(cache.remove("does_not_exist"));
    }

    @Test
    void testValueBeforeExpiry() {
        cache.put("name", "Krishna", 1000);
        assertEquals("Krishna", cache.get("name"));
    }

    @Test
    void testValueAfterExpiry() throws InterruptedException {
        cache.put("name", "Krishna", 50);
        Thread.sleep(100);

        assertNull(cache.get("name"));
    }

    @Test
    void basicEvictionRemovesLeastRecentlyUsedEntry() {
        InMemoryCache<String, String> cache = new InMemoryCache<>(3);

        cache.put("A", "Apple");
        cache.put("B", "Banana");
        cache.put("C", "Cherry");

        cache.put("D", "Date");

        assertNull(cache.get("A"));
        assertEquals("Banana", cache.get("B"));
        assertEquals("Cherry", cache.get("C"));
        assertEquals("Date", cache.get("D"));
        assertEquals(3, cache.size());
    }

    @Test
    void accessUpdatesOrderAndProtectsRecentlyUsedEntry() {
        InMemoryCache<String, String> cache = new InMemoryCache<>(3);

        cache.put("A", "Apple");
        cache.put("B", "Banana");
        cache.put("C", "Cherry");

        assertEquals("Apple", cache.get("A")); // A becomes most recently used

        cache.put("D", "Date"); // should evict B

        assertNull(cache.get("B"));
        assertEquals("Apple", cache.get("A"));
        assertEquals("Cherry", cache.get("C"));
        assertEquals("Date", cache.get("D"));
        assertEquals(3, cache.size());
    }

    @Test
    void updatingExistingKeyDoesNotIncreaseSizeAndMakesItMostRecentlyUsed() {
        InMemoryCache<String, String> cache = new InMemoryCache<>(2);

        cache.put("A", "Apple");
        cache.put("B", "Banana");

        cache.put("A", "Apricot"); // update existing key

        assertEquals(2, cache.size());
        assertEquals("Apricot", cache.get("A"));

        cache.put("C", "Cherry"); // should evict B, not A

        assertNull(cache.get("B"));
        assertEquals("Apricot", cache.get("A"));
        assertEquals("Cherry", cache.get("C"));
        assertEquals(2, cache.size());
    }

    @Test
    void ttlAndLruTogetherExpiredEntriesDoNotBlockNewInserts() throws InterruptedException {
        InMemoryCache<String, String> cache = new InMemoryCache<>(2);

        cache.put("A", "Apple", 50);
        cache.put("B", "Banana", 1000);

        Thread.sleep(100);

        assertNull(cache.get("A"));     // expires and should be removed
        assertEquals(1, cache.size());  // expired entry should no longer count

        cache.put("C", "Cherry");

        assertEquals("Banana", cache.get("B"));
        assertEquals("Cherry", cache.get("C"));
        assertEquals(2, cache.size());

        cache.put("D", "Date");

        assertNull(cache.get("B"));     // B was the LRU among live entries
        assertEquals("Cherry", cache.get("C"));
        assertEquals("Date", cache.get("D"));
        assertEquals(2, cache.size());
    }
}