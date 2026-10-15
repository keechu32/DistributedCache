#!/usr/bin/env python3
"""Exercise the packaged CLI as five processes; requires Java 21 and Python 3."""
from pathlib import Path
import selectors
import socket
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "target/distributed-cache-1.0-SNAPSHOT.jar"
processes = []


def start(*args):
    process = subprocess.Popen(
        ["java", "-jar", str(JAR), *map(str, args)],
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
    )
    processes.append(process)
    with selectors.DefaultSelector() as selector:
        selector.register(process.stdout, selectors.EVENT_READ)
        if not selector.select(timeout=10):
            raise RuntimeError("Server startup timed out")
        line = process.stdout.readline().strip()
    if "listening on 127.0.0.1:" not in line:
        raise RuntimeError(f"Server startup failed: {line}")
    return int(line.rsplit(":", 1)[1])


def request(port, command):
    with socket.create_connection(("127.0.0.1", port), timeout=5) as connection:
        connection.sendall((command + "\n").encode())
        with connection.makefile("r", encoding="utf-8") as reader:
            return reader.readline().rstrip("\n")


def check(actual, expected):
    if actual != expected:
        raise AssertionError(f"Expected {expected!r}, got {actual!r}")


try:
    replica_a = start("replica", 0, 1000)
    replica_b = start("replica", 0, 1000)
    primary_a = start("node", 0, 1000, f"127.0.0.1:{replica_a}")
    primary_b = start("node", 0, 1000, f"127.0.0.1:{replica_b}")
    router = start("router", 0, f"127.0.0.1:{primary_a}", f"127.0.0.1:{primary_b}")
    for i in range(100):
        check(request(router, f"SET key{i} value{i}"), "OK")
        check(request(router, f"GET key{i}"), f"VALUE value{i}")
        replicas = [request(replica_a, f"GET key{i}"), request(replica_b, f"GET key{i}")]
        check(sorted(replicas), sorted(["NULL", f"VALUE value{i}"]))
        check(request(router, f"DELETE key{i}"), "DELETED")
        check(request(replica_a, f"GET key{i}"), "NULL")
        check(request(replica_b, f"GET key{i}"), "NULL")
    print("PASS: packaged router, two primaries and two replicas; 100 write/read/delete cycles")
finally:
    for process in reversed(processes):
        process.terminate()
    for process in processes:
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
