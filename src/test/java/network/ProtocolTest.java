package network;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProtocolTest {
  @Test
  void malformedRequestsAndBoundedLines() throws Exception {
    var handler = new CacheHandler(new cache.InMemoryCache<>(10));
    for (String request :
        new String[] {"", "GET", "GET ", "SET k", "SETTTL k x v", "SETTTL k 1", "BOGUS k"})
      assertEquals("ERROR INVALID_REQUEST", handler.apply(request), request);
    assertEquals("GET k", LineIO.read(new StringReader("GET k\r\n")));
    assertNull(LineIO.read(new StringReader("")));
    assertThrows(IOException.class, () -> LineIO.read(new StringReader("GET\r k\n")));
    assertThrows(IOException.class, () -> LineIO.read(new StringReader("GET k")));
    assertThrows(
        IOException.class,
        () -> LineIO.read(new StringReader("x".repeat(LineIO.MAX_LINE + 1) + "\n")));
  }

  @Test
  void clientReusesConnectionAndRejectsUseAfterClose() throws Exception {
    try (var listener = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
        var client = new CacheClient("127.0.0.1", listener.getLocalPort());
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      var responder =
          executor.submit(
              () -> {
                try (var socket = listener.accept()) {
                  socket.setSoTimeout(2000);
                  var input =
                      new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
                  for (int i = 0; i < 2; i++) {
                    assertEquals("GET k", LineIO.read(input));
                    socket.getOutputStream().write("NULL\n".getBytes(StandardCharsets.UTF_8));
                  }
                }
                return null;
              });
      assertEquals("NULL", client.request("GET k"));
      assertEquals("NULL", client.request("GET k"));
      responder.get(3, java.util.concurrent.TimeUnit.SECONDS);
      client.close();
      assertThrows(IOException.class, () -> client.request("GET k"));
    }
  }

  @Test
  void persistentConnectionPipeliningAndShutdown() throws Exception {
    var server = new TcpServer("127.0.0.1", 0, new CacheHandler(new cache.InMemoryCache<>(10)));
    try (server;
        var socket = new Socket("127.0.0.1", server.port())) {
      socket.setSoTimeout(2000);
      socket
          .getOutputStream()
          .write("SET a hello\nGET a\nDELETE a\nGET a\n".getBytes(StandardCharsets.UTF_8));
      var reader = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
      assertEquals("OK", LineIO.read(reader));
      assertEquals("VALUE hello", LineIO.read(reader));
      assertEquals("DELETED", LineIO.read(reader));
      assertEquals("NULL", LineIO.read(reader));
      server.close();
      assertNull(LineIO.read(reader));
    }
  }
}
