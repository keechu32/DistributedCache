package router;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Immutable ring: membership changes create a new routing snapshot. */
public final class ConsistentHashRing {
  private final NavigableMap<Long, String> ring = new TreeMap<>();

  public ConsistentHashRing(Collection<String> nodes, int virtualNodes) {
    if (nodes.isEmpty() || virtualNodes <= 0)
      throw new IllegalArgumentException("empty ring or invalid virtual node count");
    var unique = new TreeSet<>(nodes);
    if (unique.size() != nodes.size()) throw new IllegalArgumentException("duplicate nodes");
    for (String node : unique) {
      if (node.isBlank()) throw new IllegalArgumentException("blank node");
      for (int i = 0; i < virtualNodes; i++) {
        long token = hash(node + "#" + i);
        if (ring.putIfAbsent(token, node) != null)
          throw new IllegalArgumentException("hash collision");
      }
    }
  }

  public String owner(String key) {
    var entry = ring.ceilingEntry(hash(Objects.requireNonNull(key)));
    return (entry == null ? ring.firstEntry() : entry).getValue();
  }

  private static long hash(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return ByteBuffer.wrap(digest).getLong();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
