# Distributed Cache System

A lightweight **distributed in-memory cache** inspired by Redis. This project demonstrates the core concepts behind modern distributed caching systems, including **fast key-value lookups, TTL expiration, LRU eviction, request routing, sharding, and replication**.

> **Status:** 🚧 In Development (7-Day Learning Project)

---

# Table of Contents

- [Overview](#overview)
- [Objectives](#objectives)
- [Architecture](#architecture)
- [Features](#features)
- [System Design](#system-design)
- [Project Structure](#project-structure)
- [How It Works](#how-it-works)
- [Implementation Roadmap](#implementation-roadmap)
- [Tech Stack](#tech-stack)
- [Future Improvements](#future-improvements)
- [References](#references)

---

# Overview

Modern applications frequently access the same data repeatedly. Querying a database for every request increases latency and puts unnecessary load on backend systems.

A cache stores frequently accessed data in memory, allowing applications to retrieve it much faster than reading from a database.

This project implements a simplified distributed cache from scratch to understand:

- Hash-based key-value storage
- Cache expiration (TTL)
- LRU eviction policy
- Network communication
- Data partitioning
- Replication
- Distributed system fundamentals

---

# Objectives

The goal is **not** to build another Redis.

The goal is to understand **how Redis-like systems work internally** by implementing the major components ourselves.

By the end of the project, the cache will support:

- Fast O(1) key lookup
- Automatic expiration
- Memory management
- Multiple cache nodes
- Key distribution
- Replication
- Client-server communication

---

# Architecture

```
                    +----------------+
                    |     Client     |
                    +--------+-------+
                             |
                    GET / SET / DELETE
                             |
                             v
                +------------------------+
                |      Cache Router      |
                |------------------------|
                | Hash Key               |
                | Route to Cache Node    |
                +-----------+------------+
                            |
          -----------------------------------------
          |                                       |
          v                                       v
+------------------------+             +------------------------+
|      Cache Node A      |             |      Cache Node B      |
|------------------------|             |------------------------|
| TCP Request Handler    |             | TCP Request Handler    |
| Cache Service          |             | Cache Service          |
| HashMap                |             | HashMap                |
| TTL Manager            |             | TTL Manager            |
| LRU Manager            |             | LRU Manager            |
+-----------+------------+             +-----------+------------+
            |                                        |
            | Replication                            | Replication
            v                                        v
+------------------------+             +------------------------+
|     Replica Node A     |             |     Replica Node B     |
+------------------------+             +------------------------+
```

---

# Features

## Phase 1

- In-memory key-value store
- HashMap-based storage
- O(1) average lookup
- GET
- SET
- DELETE

---

## Phase 2

- TTL (Time-To-Live)
- Automatic expiration
- Lazy expiration
- Background cleanup thread

---

## Phase 3

- LRU eviction
- Maximum cache size
- Memory management

---

## Phase 4

- TCP server
- Client requests
- Text protocol

Example:

```
SET user:1 Krishna

GET user:1

DELETE user:1
```

---

## Phase 5

Distributed cache

- Multiple cache nodes
- Key partitioning
- Consistent hashing

---

## Phase 6

Replication

- Primary node
- Replica node
- Write replication

---

# System Design

## Cache Node

Each cache node contains

```
Cache Node

│

├── TCP Request Handler

├── Cache Service

├── HashMap

├── TTL Manager

└── LRU Eviction Manager
```

---

## Cache Entry

Each cache entry stores

```
Key

↓

CacheEntry

• Value

• Expiration Time

• Last Access Time
```

---

## Request Flow

### GET

```
Client

↓

Router

↓

Cache Node

↓

HashMap Lookup

↓

Return Value
```

---

### SET

```
Client

↓

Router

↓

Cache Node

↓

Store Value

↓

Update TTL

↓

Update LRU

↓

Replicate
```

---

# Project Structure

```
distributed-cache/

│

├── README.md

├── docs/

│   ├── architecture.md

│   ├── design-decisions.md

│   └── benchmarks.md

│

├── src/

│   ├── cache/

│   ├── network/

│   ├── router/

│   ├── eviction/

│   ├── replication/

│   └── utils/

│

├── tests/

│

└── diagrams/
```

---

# How It Works

## SET

1. Client sends a SET request.
2. Router computes the hash of the key.
3. Router selects the responsible cache node.
4. Cache node stores the value.
5. TTL metadata is recorded.
6. LRU metadata is updated.
7. Data is replicated.

---

## GET

1. Client sends a GET request.
2. Router selects the cache node.
3. Cache checks if the key exists.
4. Cache checks TTL.
5. If expired:
   - Remove entry
   - Return NULL
6. Otherwise:
   - Update LRU
   - Return value

---

# Data Structures Used

| Component | Data Structure |
|------------|----------------|
| Cache | HashMap |
| LRU | Doubly Linked List |
| Expiration | Timestamp |
| Routing | Consistent Hash Ring |
| Replication | Primary-Replica |

---

# Time Complexity

| Operation | Complexity |
|------------|------------|
| GET | O(1) Average |
| SET | O(1) Average |
| DELETE | O(1) Average |
| LRU Update | O(1) |
| TTL Check | O(1) |

---

# Implementation Roadmap

## Day 1

- HashMap
- Cache API
- Unit tests

---

## Day 2

- TTL
- Expiration

---

## Day 3

- LRU
- Memory limit

---

## Day 4

- TCP server
- Client communication

---

## Day 5

- Multiple cache nodes
- Consistent hashing

---

## Day 6

- Replication

---

## Day 7

- Documentation
- Benchmarks
- Testing

---

# Tech Stack

- Java (or your chosen language)
- TCP Sockets
- Multithreading
- JUnit
- Maven / Gradle

---

# Learning Outcomes

This project demonstrates understanding of:

- Data Structures
- HashMaps
- Caching
- Networking
- Concurrency
- Distributed Systems
- Replication
- Sharding
- System Design

---

# Future Improvements

- Persistent storage
- Write-through cache
- Write-back cache
- LFU eviction
- Pub/Sub
- Metrics API
- HTTP REST API
- Compression
- Authentication
- Cluster auto-discovery
- Gossip protocol
- Raft leader election
- Monitoring dashboard

---

# References

- Redis Documentation
- Designing Data-Intensive Applications – Martin Kleppmann
- System Design Primer
- MIT Distributed Systems Notes
- Consistent Hashing Papers

---

# License

This project is created for educational purposes to understand the internals of distributed caching systems and distributed system design.

---

## Author

**Krishna Devaraj**

Learning Project • Distributed Systems • Caching • System Design
