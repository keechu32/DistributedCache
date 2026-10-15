package router;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashRingTest {
    @Test void deterministicBalancedAndMinimalMovement() {
        var original = new ConsistentHashRing(List.of("a", "b", "c"), 256);
        var reordered = new ConsistentHashRing(List.of("c", "a", "b"), 256);
        var expanded = new ConsistentHashRing(List.of("a", "b", "c", "d"), 256);
        var counts = new HashMap<String, Integer>();
        int moved = 0;
        for (int i = 0; i < 20000; i++) {
            String key = "key-" + i;
            String owner = original.owner(key);
            assertEquals(owner, reordered.owner(key));
            counts.merge(owner, 1, Integer::sum);
            if (!owner.equals(expanded.owner(key))) {
                moved++;
                assertEquals("d", expanded.owner(key));
            }
        }
        for (int count : counts.values()) assertTrue(count > 5000 && count < 8500);
        assertTrue(moved > 3500 && moved < 6500);
    }

    @Test void rejectsInvalidMembership() {
        assertThrows(IllegalArgumentException.class, () -> new ConsistentHashRing(List.of(), 10));
        assertThrows(IllegalArgumentException.class, () -> new ConsistentHashRing(List.of("a", "a"), 10));
        assertThrows(IllegalArgumentException.class, () -> new ConsistentHashRing(List.of("a"), 0));
    }
}
