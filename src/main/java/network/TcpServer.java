package network;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Function;

/** Bounded connections, idle read timeout, and explicit lifecycle ownership. */
public final class TcpServer implements AutoCloseable {
  private final ServerSocket server;
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
  private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
  private final Semaphore slots = new Semaphore(256);
  private final Function<String, String> handler;
  private final Thread acceptor;
  private volatile boolean closed;

  public TcpServer(String host, int port, Function<String, String> handler) throws IOException {
    this.handler = handler;
    server = new ServerSocket();
    try {
      server.bind(new InetSocketAddress(host, port));
    } catch (IOException | RuntimeException e) {
      server.close();
      workers.shutdownNow();
      throw e;
    }
    acceptor = Thread.ofPlatform().name("cache-acceptor").start(this::accept);
  }

  public int port() {
    return server.getLocalPort();
  }

  private void accept() {
    while (!closed) {
      try {
        Socket socket = server.accept();
        if (!slots.tryAcquire()) {
          socket.close();
          continue;
        }
        clients.add(socket);
        if (closed) {
          clients.remove(socket);
          slots.release();
          socket.close();
          break;
        }
        try {
          workers.submit(() -> serve(socket));
        } catch (RejectedExecutionException e) {
          clients.remove(socket);
          slots.release();
          socket.close();
        }
      } catch (IOException e) {
        if (!closed) System.err.println("Accept failed: " + e.getMessage());
      }
    }
  }

  private void serve(Socket socket) {
    try (socket;
        var input =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        var output =
            new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
      socket.setSoTimeout(5000);
      socket.setTcpNoDelay(true);
      String line;
      while ((line = LineIO.read(input)) != null) {
        output.write(handler.apply(line));
        output.newLine();
        output.flush();
      }
    } catch (IOException ignored) {
      // Disconnects, oversized frames and idle clients terminate this connection only.
    } finally {
      clients.remove(socket);
      slots.release();
    }
  }

  @Override
  public void close() throws IOException {
    closed = true;
    server.close();
    for (Socket client : clients) client.close();
    workers.shutdownNow();
    try {
      acceptor.join(5000);
      workers.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
