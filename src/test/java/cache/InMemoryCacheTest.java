package cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryCacheTest {

    private InMemoryCache<String, String> cache;

    @BeforeEach
    void setUp() {
        cache = new InMemoryCache<>();
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
}