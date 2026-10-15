package network;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** One reusable, serialized connection per endpoint; ambiguous writes are never retried. */
public final class CacheClient implements AutoCloseable {
  private final String host;
  private final int port;
  private Socket socket;
  private BufferedReader input;
  private BufferedWriter output;
  private long lastResponse;
  private boolean closed;

  public CacheClient(String host, int port) {
    if (host == null || host.isBlank() || port < 1 || port > 65535)
      throw new IllegalArgumentException("invalid endpoint");
    this.host = host;
    this.port = port;
  }

  public synchronized String request(String command) throws IOException {
    if (closed) throw new IOException("client closed");
    if (command.length() > LineIO.MAX_LINE || command.contains("\n") || command.contains("\r"))
      throw new IllegalArgumentException("invalid frame");
    if (socket != null && System.nanoTime() - lastResponse >= TimeUnit.SECONDS.toNanos(4))
      disconnect();
    try {
      if (socket == null) {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 1000);
        socket.setSoTimeout(3000);
        socket.setTcpNoDelay(true);
        input =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        output =
            new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
      }
      output.write(command + "\n");
      output.flush();
      String response = LineIO.read(input);
      if (response == null) throw new EOFException("missing response");
      lastResponse = System.nanoTime();
      return response;
    } catch (IOException e) {
      disconnect();
      throw e;
    }
  }

  private void disconnect() {
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException ignored) {
        /* Already unusable. */
      }
      socket = null;
      input = null;
      output = null;
    }
  }

  @Override
  public synchronized void close() {
    closed = true;
    disconnect();
  }
}
