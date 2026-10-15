# Architecture

## Cache

`InMemoryCache<K,V>` retains a HashMap from key to a custom doubly linked node. The head is most recently used and the tail least recently used. Every public cache operation holds the same monitor, protecting map/list invariants. A write to an existing key replaces its value and deadline and moves it to the head. On capacity pressure, expired entries are removed before a live LRU entry is evicted.

`CacheEntry` holds an immutable value and absolute expiration time. The cache accepts an injectable Clock so expiration boundary tests need no sleeps. `CacheMaintenance` owns a named daemon scheduler and runs a scan every second in the CLI. Explicit close stops scheduling; cache reads remain safe without maintenance because they also expire entries lazily. Size reports live entries after a scan. There is no byte accounting.

## Transport and lifecycle

`TcpServer` owns a ServerSocket, a platform accept thread, a virtual-thread executor, and the set of connected sockets. A semaphore admits up to 256 connections. Each accepted connection reads bounded lines, invokes the supplied handler, then writes its response. Closing the server closes active sockets, interrupts workers, and waits for termination. Shutdown hooks close the server and maintenance worker.

`CacheHandler` parses and executes local requests. `CacheRouter` parses a key, selects one owner and forwards the original request. Its ring and node map are immutable, so reads require no routing lock. Configuration order does not affect placement. A ring snapshot cannot be changed in place.

The CLI starts independent JVMs and binds to 127.0.0.1. The five-process smoke test verifies the same packaged entry point used in the README. The Java server constructor accepts a host, so embedding applications can choose a different bind address with appropriate surrounding network controls.

## Replication and failures

`ReplicatedHandler` holds a primary-wide monitor through local execution and all replica acknowledgements. This deliberately serializes writes and reads on a primary; it makes write order straightforward, at the cost of head-of-line blocking during slow replication. Replicas are contacted sequentially, and all are attempted even if one fails. GET requests stay local and do not trigger replication.

For a SETTTL, the primary computes a single absolute deadline. Both local storage and replicas receive SETAT. A delayed replica write whose deadline is in the past expires immediately, rather than extending the original TTL. Clock skew can still cause different expiration observations.

Successful responses acknowledge in-memory application only. Primary crashes lose data. Network response loss can make a successful mutation appear failed. A replica outage causes a partial-write error and has no retry queue. Restarted replicas need an application-controlled cache refill; adding replicas does not backfill existing entries. Ordinary replica reads alter their own LRU order, so eviction can differ from the primary even without failures. The router never reads replicas or promotes them.

## Complexity

| Operation | Expected cost |
|---|---|
| Local GET, DELETE, containsKey | O(1) map access and linked-list work |
| Local SET with free capacity or existing key | O(1) |
| New SET at capacity | O(n) expiration scan, then O(1) eviction |
| size, cleanup | O(n) |
| Routing | O(key length + log(N × V)) |
| Ring construction | O(N × V × log(N × V)) |
| Replicated mutation | Local work + sequential network round trips to R replicas |

N is primary count, V is virtual nodes per primary (256), R is replica count, and n is local entries. The entry limit does not bound all process memory: keys/values, threads, buffers and routing state also consume heap.
