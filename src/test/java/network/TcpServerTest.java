package network;

import cache.InMemoryCache;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TcpServerTest {
    @Test void roundTripAndValidation() throws Exception {
        try (var server = new TcpServer("127.0.0.1", 0, new CacheHandler(new InMemoryCache<>(10)))) {
            var client = new CacheClient("127.0.0.1", server.port());
            assertEquals("OK", client.request("SET greeting Hello 世界"));
            assertEquals("VALUE Hello 世界", client.request("GET greeting"));
            assertEquals("DELETED", client.request("DELETE greeting"));
            assertEquals("NULL", client.request("GET greeting"));
            assertEquals("NOT_FOUND", client.request("DELETE greeting"));
            assertEquals("ERROR INVALID_REQUEST", client.request("SETTTL x -2 v"));
            assertEquals("ERROR INVALID_REQUEST", client.request("GET x extra"));
            assertEquals("OK", client.request("SETTTL x 0 v"));
            assertEquals("NULL", client.request("GET x"));
            assertThrows(IllegalArgumentException.class, () -> client.request("GET x\nDELETE y"));
        }
    }

    @Test void concurrentClients() throws Exception {
        try (var server = new TcpServer("127.0.0.1", 0, new CacheHandler(new InMemoryCache<>(1000)));
             var executor = Executors.newFixedThreadPool(16)) {
            var client = new CacheClient("127.0.0.1", server.port());
            var tasks = new java.util.ArrayList<Callable<Void>>();
            for (int i = 0; i < 200; i++) {
                int key = i;
                tasks.add(() -> {
                    assertEquals("OK", client.request("SET k" + key + " value"));
                    assertEquals("VALUE value", client.request("GET k" + key));
                    return null;
                });
            }
            for (var result : executor.invokeAll(tasks)) result.get();
        }
    }
}
