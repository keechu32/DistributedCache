# Distributed Cache System

A Java 21 distributed in-memory cache built from scratch: HashMap storage, a doubly linked LRU list, TTL expiration, a TCP text protocol, consistent-hash sharding, and synchronous primary-to-replica writes.

**Status:** All six implementation phases and the final testing, benchmark, and documentation phase are complete. This is an educational distributed-systems project with explicit failure semantics, not a production Redis replacement.

## Build and test

Requirements: JDK 21 and Maven 3.9+. Python 3 is used only for the optional multi-process smoke test. No runtime libraries are required by the application.

```sh
mvn clean verify
python3 scripts/smoke.py
```

The test suite covers cache invariants, deterministic expiration, concurrent access, randomized LRU operations, protocol validation, real TCP clients, sharding, replica ordering and failure handling. The smoke test launches an actual router, two primaries and two replicas from the packaged JAR, verifies 100 write/read/delete cycles, and terminates all five processes. GitHub Actions runs both checks and builds/smoke-tests the JMH benchmarks.

## Run a cluster

Run each command in its own terminal, starting replicas first:

```sh
java -jar target/distributed-cache-1.0-SNAPSHOT.jar replica 7101 10000
java -jar target/distributed-cache-1.0-SNAPSHOT.jar replica 7102 10000
java -jar target/distributed-cache-1.0-SNAPSHOT.jar node 7001 10000 127.0.0.1:7101
java -jar target/distributed-cache-1.0-SNAPSHOT.jar node 7002 10000 127.0.0.1:7102
java -jar target/distributed-cache-1.0-SNAPSHOT.jar router 7000 127.0.0.1:7001 127.0.0.1:7002
```

Connect with `nc 127.0.0.1 7000` and enter:

```text
SET user:1 Krishna
GET user:1
SETTTL session:1 30000 signed in
DELETE user:1
```

Responses are `OK`, `VALUE Krishna`, `OK`, and `DELETED`. A missing or expired key returns `NULL`. `SETTTL` uses milliseconds; zero expires immediately. `SET` replaces any previous TTL with no expiration. Values can contain spaces and Unicode, but cannot contain newlines. See the complete [protocol](docs/protocol.md).

A standalone server needs only `node 7001`; its default capacity is 10,000 entries. CLI syntax:

```text
node <port> [capacity] [replicaHost:port...]
replica <port> [capacity]
router <port> <primaryHost:port>...
```

All listeners bind to loopback. Stop with Ctrl+C to close connections and expiration workers. The Java `CacheClient` API can also send protocol requests with bounded connection/read timeouts.

## Architecture

```text
                      TCP client
                          |
                   consistent-hash router
                    /                 \
             primary A              primary B
             HashMap + LRU          HashMap + LRU
             TTL cleanup            TTL cleanup
                 |                      |
          synchronous writes     synchronous writes
                 |                      |
             replica A              replica B
```

The immutable hash ring uses SHA-256 tokens and 256 virtual nodes per primary. Replicas are configured on their primary and are not ring members. Each cache retains the original custom HashMap/doubly linked list implementation from phases 1–3. All cache mutations and LRU accesses are synchronized. An expiration worker runs every second on each node; reads also check expiration lazily.

| Phase | Implementation |
|---|---|
| 1 | Generic in-memory cache API, GET/SET/DELETE |
| 2 | TTL, lazy expiration, periodic physical cleanup |
| 3 | LRU eviction, bounded entry count |
| 4 | Concurrent TCP server, bounded frames/connections, Java client |
| 5 | Deterministic consistent hashing and TCP router |
| 6 | Ordered synchronous replication with explicit partial-write errors |
| Final | Unit/integration tests, five-process smoke test, JMH, CI, documentation |

## Benchmarks

```sh
mvn -Pbenchmarks clean package
java -jar target/jmh/distributed-cache-1.0-SNAPSHOT-benchmarks.jar
```

JMH benchmarks local GET/SET, ring lookup, direct TCP GET, routed GET, and replicated SET. See [methodology and measured results](docs/benchmarks.md), including raw JSON. Results describe the specified local workload; they are not a distributed production capacity claim.

## Design and limitations

- Capacity bounds **entries**, not heap bytes. Values and network frames have separate size constraints.
- GET, DELETE and LRU updates are O(1) on average. Full-capacity inserts may scan O(n) expired entries before eviction; size and periodic cleanup also scan O(n). Routing is O(log(nodes × virtual nodes)) plus hashing the key.
- A successful replicated write means all configured replicas acknowledged it. A failed acknowledgement returns `ERROR REPLICATION_FAILED local_write_applied`; some replicas may already contain the write. A transport failure can leave the write outcome unknown. There is no rollback or automatic retry.
- Replicas are read-only for ordinary client commands. Independent LRU state can diverge because reads are not replicated; replicas are additional cache copies, not guaranteed identical snapshots.
- Membership is static. Changing membership remaps keys without migrating existing data; refill the cache after reconfiguration. Node outages return explicit errors rather than changing ownership.
- Data is volatile. Restarted replicas start empty and receive only subsequent writes. There is no snapshot transfer, backlog, durable log, automatic promotion, quorum, or consensus.
- TTL deadlines use wall-clock time; clocks must agree. No authentication or TLS is implemented. The replication prefix is a protocol convention, not an access-control boundary. Keep listeners on a trusted local network; the CLI uses loopback.

See [architecture](docs/architecture.md) and [design decisions](docs/design-decisions.md) for concurrency, lifecycle, and consistency details.

## Repository layout

```text
src/main/java/cache/        Storage, TTL, linked-list LRU and cleanup
src/main/java/network/      Protocol, TCP server and client
src/main/java/router/       Hash ring and forwarding
src/main/java/replication/  Primary and replica handlers
src/main/java/app/          Executable entry point
src/test/java/             Unit and TCP integration tests
src/jmh/java/              JMH benchmark workloads
scripts/smoke.py           Packaged multi-process integration check
docs/                      Architecture, tradeoffs, protocol and benchmarks
.github/workflows/ci.yml   Automated verification
```

## Future work

Persistence, replica catch-up, online migration, connection pooling, byte-based memory budgets, authentication/TLS, metrics, and consensus-based failover are intentionally beyond the completed roadmap. Each changes the system's operational or consistency guarantees and deserves a separate design.

**Author:** Krishna Devaraj. Created to explore caching, data structures, concurrency and distributed systems.
