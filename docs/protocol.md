# TCP protocol

UTF-8, one request and one response per line. LF and CRLF are accepted. Keys are nonempty tokens separated by ASCII spaces. Commands are case sensitive. Values are nonempty text and may contain spaces. No binary payloads, embedded CR/LF, escaping, or Redis wire compatibility is provided.

| Request | Response |
|---|---|
| `SET key value` | `OK` |
| `SETTTL key ttlMillis value` | `OK` |
| `GET key` | `VALUE value` or `NULL` |
| `DELETE key` | `DELETED` or `NOT_FOUND` |

`SETTTL` takes a nonnegative signed 64-bit millisecond duration. Overflow is rejected. `SET` removes an existing expiration. Expiration occurs at `now >= deadline`. An expired DELETE reports `NOT_FOUND`. Overwrites update LRU recency; GET also updates recency; containsKey in the in-process API does not.

| Error | Meaning |
|---|---|
| `ERROR INVALID_REQUEST` | Invalid syntax, TTL, command or argument count |
| `ERROR NODE_UNAVAILABLE` | Router could not obtain its owner's response; a write may have reached that node |
| `ERROR READ_ONLY` | Ordinary write sent to a replica |
| `ERROR REPLICATION_FAILED local_write_applied` | Primary applied the write, but at least one replica did not acknowledge it |

No client or router retries are automatic. Retrying a failed SETTTL starts a new deadline. Applications must decide how to handle ambiguous writes or refill from their source of truth.

The server supports multiple sequential requests and pipelining on a connection; responses remain in connection order. The supplied AutoCloseable Java client serializes requests over one reusable connection per endpoint. It closes idle connections after four seconds on the next request, ahead of the server idle timeout. An I/O failure discards the connection and is returned without retry; a later call may establish a new connection. Call close when finished. Routers and primaries own and close their downstream clients. Clients must supply a newline; an incomplete frame at EOF is discarded. Frames over 65,536 Java UTF-16 code units, malformed CR endings, and idle reads over five seconds close the connection. The client uses one-second connect and three-second response read timeouts. There are at most 256 admitted connections per server; excess connections are closed. These are per-read timeouts, not total request deadlines.

Replication uses `REPL <mutation>` and `SETAT key epochMillis value`. The primary converts SETTTL to SETAT once and forwards that same deadline. Replica endpoints accept GET and REPL; REPL has no authentication. SETAT is also understood by ordinary nodes for administrative use. Replica framing adds five characters; primaries with replicas reject oversized internal frames before applying the local write. These endpoints are for a trusted environment only.
