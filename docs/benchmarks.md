# Benchmarks

The benchmark profile builds an executable JMH 1.37 JAR in `target/jmh/`, separate from ordinary compiler output. Java 21 is required. Benchmark dependencies are absent from the normal application build.

## Reproduce

```sh
mvn -Pbenchmarks clean verify
java -jar target/jmh/distributed-cache-1.0-SNAPSHOT-benchmarks.jar -foe true
```

Defaults: two forks, three one-second warmup iterations, five one-second measurement iterations, one benchmark thread. `-foe true` makes an unsuccessful benchmark fail the command. To inspect contention, add `-t 8`; the local cache and each endpoint client use a monitor, so throughput need not scale with threads. Use `-prof gc` to investigate allocation costs. Do not run competing benchmarks simultaneously.

The checked-in baseline uses shorter exploratory runs:

```sh
java -jar target/jmh/distributed-cache-1.0-SNAPSHOT-benchmarks.jar \
  -foe true -wi 2 -i 3 -w 1s -r 1s -f 1 \
  -jvmArgs '-Xms256m -Xmx256m' \
  -rf json -rff docs/benchmark-results/throughput.json

java -jar target/jmh/distributed-cache-1.0-SNAPSHOT-benchmarks.jar \
  '.*(tcpGet|routedGet|replicatedSet)' \
  -foe true -bm sample -tu us -wi 2 -i 3 -w 1s -r 1s -f 1 \
  -jvmArgs '-Xms256m -Xmx256m' \
  -rf json -rff docs/benchmark-results/latency.json
```

## Workloads and interpretation

- `localGet` and `localSet`: one hot key, value `value`, capacity 10,000. SET overwrites; it does not exercise eviction or new-key allocation.
- `hashLookup`: one key on a three-primary ring, 256 virtual nodes each; includes SHA-256 hashing.
- `tcpGet`: one client and one node over loopback.
- `routedGet`: client → router → one of two nodes over loopback.
- `replicatedSet`: client → primary → one synchronous replica over loopback.

Network workloads reuse connections after setup, check the expected response, and include protocol parsing and round trips. Every trial owns its servers and closes them. All servers run in the benchmark JVM; these are not cross-machine measurements. Maintenance is not scheduled in the benchmark fixtures. No miss-heavy, large-value, expiration-scan, full-capacity eviction or failure workload is represented by these numbers. Use those workloads before making sizing claims.

The original per-request socket client exhausted local ephemeral ports under sustained JMH load. The completed client reuses one serialized connection per endpoint, reconnects proactively after idle periods, and discards failed connections without retrying ambiguous requests. This was a benchmark-driven transport fix, not a claimed comparative speedup.

The short baseline has only one fork and three measurements. Treat it as a reproducibility example; inspect raw confidence intervals and repeat with longer runs on an idle machine before publishing performance claims. Sample-time percentiles describe this closed-loop single-client workload, not open-loop service latency under saturation. They do not compensate for coordinated omission.

## Recorded baseline

Measured on 15 October 2026, macOS 26.7.1, aarch64, Oracle JDK 21.0.10+8-LTS-217, JMH 1.37, one thread, 256 MiB initial and maximum JVM heap. CPU model and physical RAM were not available through the execution sandbox. Other desktop activity was not controlled.

Raw results: [throughput JSON](benchmark-results/throughput.json), [sample latency JSON](benchmark-results/latency.json).

| Workload | Throughput (ops/s) |
|---|---:|
| `hashLookup` | 16,486,180 |
| `localGet` | 46,999,573 |
| `localSet` | 23,817,872 |
| `replicatedSet` | 8,092 |
| `routedGet` | 7,972 |
| `tcpGet` | 16,827 |

| Network workload | p50 (µs) | p95 (µs) | p99 (µs) |
|---|---:|---:|---:|
| `replicatedSet` | 122.2 | 134.9 | 154.4 |
| `routedGet` | 123.3 | 138.0 | 155.4 |
| `tcpGet` | 58.6 | 64.9 | 70.9 |

These results show the cost of additional loopback round trips in this workload. They do not establish a performance comparison with Redis or a production service-level objective.
