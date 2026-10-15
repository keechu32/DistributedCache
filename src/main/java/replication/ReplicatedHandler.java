package replication;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import network.*;

/** Serializes primary writes and synchronous replica acknowledgements. */
public final class ReplicatedHandler implements Function<String, String>, AutoCloseable {
  private final CacheHandler local;
  private final List<CacheClient> replicas;

  public ReplicatedHandler(CacheHandler local, List<CacheClient> replicas) {
    this.local = local;
    this.replicas = List.copyOf(replicas);
  }

  @Override
  public void close() {
    replicas.forEach(CacheClient::close);
  }

  @Override
  public synchronized String apply(String request) {
    final Command command;
    try {
      command = Command.parse(request);
      if (command.operation().equals("SETTTL")) {
        request =
            "SETAT "
                + command.key()
                + " "
                + Math.addExact(System.currentTimeMillis(), command.expiry())
                + " "
                + command.value();
      }
    } catch (IllegalArgumentException | ArithmeticException e) {
      return "ERROR INVALID_REQUEST";
    }
    if (!replicas.isEmpty()
        && !command.operation().equals("GET")
        && request.length() > LineIO.MAX_LINE - 5) return "ERROR INVALID_REQUEST";
    String response = local.apply(request);
    if (command.operation().equals("GET") || response.startsWith("ERROR")) return response;
    boolean failed = false;
    for (CacheClient replica : replicas) {
      try {
        String acknowledgement = replica.request("REPL " + request);
        boolean valid =
            command.operation().equals("DELETE")
                ? acknowledgement.equals("DELETED") || acknowledgement.equals("NOT_FOUND")
                : acknowledgement.equals("OK");
        if (!valid) failed = true;
      } catch (IOException | IllegalArgumentException e) {
        failed = true;
      }
    }
    return failed ? "ERROR REPLICATION_FAILED local_write_applied" : response;
  }
}
