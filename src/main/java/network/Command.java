package network;

/** UTF-8 line protocol. Values may contain spaces; keys may not. */
public record Command(String operation, String key, String value, long expiry) {
    public static Command parse(String line) {
        String[] parts = line.split(" ", 4);
        if (parts.length < 2 || parts[1].isBlank()) throw new IllegalArgumentException("missing key");
        return switch (parts[0]) {
            case "GET", "DELETE" -> {
                if (parts.length != 2) throw new IllegalArgumentException("unexpected arguments");
                yield new Command(parts[0], parts[1], null, -1);
            }
            case "SET" -> {
                String[] set = line.split(" ", 3);
                if (set.length != 3 || set[2].isEmpty()) throw new IllegalArgumentException("missing value");
                yield new Command("SET", set[1], set[2], -1);
            }
            case "SETTTL", "SETAT" -> {
                if (parts.length != 4 || parts[3].isEmpty()) throw new IllegalArgumentException("missing TTL or value");
                long expiry = Long.parseLong(parts[2]);
                if (expiry < 0) throw new IllegalArgumentException("expiry must be nonnegative");
                yield new Command(parts[0], parts[1], parts[3], expiry);
            }
            default -> throw new IllegalArgumentException("unknown command");
        };
    }
}
