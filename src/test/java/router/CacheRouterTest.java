package router;

import cache.InMemoryCache;
import network.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CacheRouterTest {
    @Test void routesToExactlyOneOwnerAndReportsOutage() throws Exception {
        var a = new InMemoryCache<String, String>(1000);
        var b = new InMemoryCache<String, String>(1000);
        try (var first = new TcpServer("127.0.0.1", 0, new CacheHandler(a));
             var second = new TcpServer("127.0.0.1", 0, new CacheHandler(b))) {
            var router = new CacheRouter(Map.of("a", new CacheClient("127.0.0.1", first.port()),
                    "b", new CacheClient("127.0.0.1", second.port())));
            try (var gateway = new TcpServer("127.0.0.1", 0, router)) {
                var client = new CacheClient("127.0.0.1", gateway.port());
                for (int i = 0; i < 100; i++) {
                    String key = "key" + i;
                    assertEquals("OK", client.request("SET " + key + " value"));
                    assertEquals("VALUE value", client.request("GET " + key));
                    assertEquals("value", (router.owner(key).equals("a") ? a : b).get(key));
                    assertNull((router.owner(key).equals("a") ? b : a).get(key));
                }
                assertEquals(100, a.size() + b.size());
                assertEquals("DELETED", client.request("DELETE key0"));
                first.close();
                String key = java.util.stream.IntStream.range(0, 100).mapToObj(i -> "key" + i)
                        .filter(k -> router.owner(k).equals("a")).findFirst().orElseThrow();
                assertEquals("ERROR NODE_UNAVAILABLE", client.request("GET " + key));
            }
        }
    }
}
