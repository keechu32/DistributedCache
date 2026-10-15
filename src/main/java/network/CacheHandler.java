package network;

import cache.InMemoryCache;
import java.util.function.Function;

public class CacheHandler implements Function<String, String> {
  private final InMemoryCache<String, String> cache;

  public CacheHandler(InMemoryCache<String, String> cache) {
    this.cache = cache;
  }

  @Override
  public synchronized String apply(String line) {
    try {
      Command command = Command.parse(line);
      return switch (command.operation()) {
        case "GET" -> {
          String value = cache.get(command.key());
          yield value == null ? "NULL" : "VALUE " + value;
        }
        case "DELETE" -> cache.remove(command.key()) == null ? "NOT_FOUND" : "DELETED";
        case "SET", "SETTTL", "SETAT" -> {
          long ttl = command.expiry();
          if (command.operation().equals("SETAT"))
            ttl = Math.max(0, ttl - System.currentTimeMillis());
          cache.put(command.key(), command.value(), ttl);
          yield "OK";
        }
        default -> throw new IllegalArgumentException("unknown command");
      };
    } catch (IllegalArgumentException | ArithmeticException e) {
      return "ERROR INVALID_REQUEST";
    }
  }
}
