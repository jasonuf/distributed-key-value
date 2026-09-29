# dkv roadmap

A replicated, linearizable key-value store in Java, built in 16 stages: single node → Apache Ratis cluster → your own Raft, deployed across three regions and checked with your own linearizability checker.

This file is safe to read. It says what to build and why, never how. The mentor's hint bank lives in `HINTS.md`; don't open it, that's the answer key.

## How to read a stage

- **Goal**: what exists at the end of the stage.
- **Why**: the distributed systems problem the stage makes you face.
- **Given**: scaffolding the mentor may write for you. Everything not listed is yours.
- **Guardrails**: constraints that exist because a *later* stage depends on them. They say what must be true, not how to do it. Breaking one isn't forbidden, but the mentor will tell you which stage it will hurt.
- **Done when**: what the mentor's acceptance tests check. All earlier stages' tests must keep passing.
- **Read**: specific sections, each with the reason it's on the list. Read before starting, not after getting stuck.
- **Grill**: questions you should be able to answer out loud to a skeptical senior engineer. The mentor asks these (and follow-ups) before the stage is marked done.

## The arc

| Part | Stages | You end up with |
|---|---|---|
| I. One node, done right | 0–3 | A deterministic, exactly-once, crash-safe KV node |
| II. Replicated with Ratis | 4–8 | A 3-region cluster, a linearizability checker, and a fault-injection harness that has caught real stale reads |
| III. Your own Raft | 9–15 | A deterministic Raft core tested in simulation, swapped in behind the same interface, and passing the same harness |

The three load-bearing decisions, made early so nothing later forces a restart:

1. **The state machine is pure and deterministic** (Stage 1). Every replica must compute the same state from the same log.
2. **Consensus sits behind your own interface** (Stage 4). Ratis and your Raft are interchangeable, so every test you write against Ratis becomes the acceptance test for your Raft.
3. **The Raft core has no threads, clock, or I/O** (Stage 9). This is what makes bugs reproducible.

A note on testing tools: the simulation testing in Part III uses a seeded simulator you build yourself on plain JUnit 5, not a property-testing library. jqwik is in maintenance mode, and its 1.10+ releases carry an anti-AI-usage clause that conflicts with working alongside a coding agent. Building the simulator yourself is also the more valuable skill.

---

# Part I: One node, done right

## Stage 0: Workbench

**Goal.** A Maven multi-module Java 21 project that builds, runs tests, and has an `acceptance` module where stage tests live. CI (GitHub Actions) runs on every push.

**Why.** Codecrafters-style progress needs a fast, trustworthy test loop. You'll run the acceptance suite hundreds of times.

**Given.** All of it: parent POM and module POMs, the Maven Wrapper (`./mvnw`), CI workflow, formatting config, and the stage-selection mechanism: acceptance tests carry a `@Stage(N)` annotation, and `./mvnw verify -Dstage=N` runs stages 0 through N (acceptance tests run under the Failsafe plugin).

**Guardrails.**
- Modules are split so that no consensus library can leak into the state machine or API code: `kv-core` (state machine, commands), `kv-server` (HTTP), `consensus-api` (your interface), `consensus-ratis`, `raft` (later), `checker` (later), `acceptance`.
- The Maven Enforcer plugin fails the build if `kv-core`, `kv-server`, or `consensus-api` depends on Ratis. The boundary is checked by the build, not by discipline.
- Java 21 (you'll want records, sealed interfaces, pattern-matching `switch`, virtual threads).

**Done when.** `./mvnw verify -Dstage=0` passes locally and in CI, a deliberately added Ratis dependency in `kv-core` fails the build, and a deliberately failing stage-0 test is actually reported (proof the suite runs).

**Read (Java refresher, skim).**
- JEP 395 (Records), JEP 409 (Sealed Classes), JEP 441 (Pattern Matching for switch): you'll model every command and Raft message this way.
- JEP 444 (Virtual Threads), "Motivation" and "Description": why blocking-style network code is fine again in Java.

**Grill.** Why does module structure matter before any code exists?

---

## Stage 1: Single-node KV

**Goal.** An HTTP server with `GET`, `PUT`, `DELETE` on `/kv/{key}`, backed by an in-memory state machine. Every mutation goes through a single `apply(command)` path. Use the JDK's built-in `com.sun.net.httpserver.HttpServer` with a virtual-thread executor, no web framework, so nothing sits between you and the network.

**Why.** This is the thing you'll replicate. Replication only works if every replica, given the same sequence of commands, ends in the same state. That property is decided here.

**Given.** The HTTP route spec (paths, status codes, body format) and the acceptance tests.

**Guardrails.**
- Commands are **data**, not code: immutable values that can be serialized to bytes and back. (Stage 4 ships these bytes across the network.)
- `apply` is **deterministic**: no clock reads, no randomness, no I/O, no iteration over unordered collections that affects output. Same commands in, same state and same results out, on any machine.
- The HTTP layer never touches the map directly. It *submits* a command and waits for the result. (Stage 4 inserts consensus between submit and apply.)
- Reads are also expressible as commands, even if you don't route them through `apply` yet. (Stage 7 compares read paths.)

**Done when.** CRUD semantics are correct (including missing keys), concurrent clients don't corrupt state, and a determinism test passes: two fresh state machines fed the same random command sequence produce identical results and identical state.

**Read.**
- Raft paper (extended version), §2 "Replicated state machines" (1 page): the model you're building toward.
- Fred Schneider, "Implementing Fault-Tolerant Services Using the State Machine Approach: A Tutorial" (1990), §1–3 only: the original argument for why determinism is the whole game.

**Grill.**
- What exactly could go wrong if `apply` called `System.currentTimeMillis()`?
- Why submit-and-wait instead of mutating the map from the HTTP handler?
- Where does concurrency control live right now, and where will it live once there's a log?

---

## Stage 2: Exactly-once commands

**Goal.** Add a compare-and-set command (`PUT` with an expected current value) and make every mutation exactly-once: a client may retry the same request any number of times and it takes effect at most once, with the retry receiving the original response.

**Why.** In a real cluster a client's request can succeed but the response gets lost (leader crashes after committing, network drops the reply). The client retries. Without deduplication, a retried CAS silently corrupts data, and your linearizability checker in Stage 6 will catch it. Fixing this later means changing the command format, the state machine, and the snapshot format at once.

**Given.** Acceptance tests and the request header convention for client identity.

**Guardrails.**
- Every mutating command carries a client identity and a per-client sequence number.
- **Deduplication state is part of the state machine state**, not a side table. (Stage 5's snapshots and every replica must agree on it.)
- Anything that expires dedup entries must be deterministic. (No local clocks, per Stage 1.)

**Done when.** Replaying the same request returns the cached response and doesn't re-apply; CAS semantics are correct under concurrent clients; dedup state survives the determinism test from Stage 1.

**Read.**
- Ongaro's PhD thesis, "Consensus: Bridging Theory and Practice" (2014), §6.3 "Implementing linearizable semantics": the exact problem and the session-based approach.
- Pat Helland, "Idempotence Is Not a Medical Condition" (ACM Queue, 2012): short, and frames why "just retry" is a correctness question.

**Grill.**
- A client sends CAS, the server applies it, the response is lost, the client retries. Walk through what happens with and without your dedup.
- How much dedup state do you keep per client, and what assumption about clients justifies that?
- How would you expire old sessions without breaking determinism?

---

## Stage 3: Durable log

**Goal.** A write-ahead log: an append-only file of entries, each with an index, a term (always 1 for now), and a command. Mutations are logged and `fsync`ed before they're acknowledged. On restart the node rebuilds its state by replaying the log. The node survives `kill -9` at any point without losing acknowledged writes, and survives a torn write at the tail.

**Why.** Every Raft node's durability depends on this log, and you'll reuse it as your own Raft's log storage in Stage 12. Disks lie in subtle ways (partial writes, reordering, `fsync` edge cases) and consensus correctness assumes the log doesn't.

**Given.** A crash-test harness that kills the process at random points and a file-corruption helper that truncates or flips bytes. Acceptance tests.

**Guardrails.**
- The log API supports: append a batch, read a range, get last index and term, and **truncate all entries from index i onward**. (Raft needs suffix truncation in Stage 10. It's not used yet.)
- Entries are self-describing and checksummed, so a torn tail is detectable.
- A torn tail is repaired; corruption in the *middle* of the log is a loud failure, not silently skipped.
- Term and vote will need their own durable file later. Don't bake assumptions into the log that prevent storing a separate small piece of metadata.

**Done when.** After hundreds of randomized kill-and-restart cycles, every acknowledged write is present, and no unacknowledged write appears twice. Truncated-tail files recover; mid-file corruption refuses to start.

**Read.**
- Dan Luu, "Files are hard": what `fsync` does and doesn't promise, including directory `fsync`.
- Rebello et al., "Can Applications Recover from fsync Failures?" (USENIX ATC 2020), §1–2 and the summary of findings: why you must not retry a failed `fsync` and continue.
- Javadoc for `FileChannel.force(boolean)`: read it closely.
- Optional: Alagappan et al., "Protocol-Aware Recovery for Consensus-Based Storage" (FAST 2018), §1–2: why a corrupted log entry on one replica is a consensus problem, not only a disk problem.

**Grill.**
- What's the difference between `write` returning and the data being durable?
- Your node crashed mid-append. What are the possible states of the file, and how does your recovery handle each?
- Why is silently skipping a corrupted entry in the middle of the log dangerous in a replicated system?
- What throughput does your log get with `fsync` per write? What would you change, and what would it cost?

**Resume signal.** Crash-consistency testing with fault injection is a rare, concrete skill to point at.

---

# Part II: Replicated with Ratis

## Stage 4: Three nodes with Ratis

**Goal.** Define your own `Consensus` interface, implement it with Apache Ratis (latest 3.2.x), and run a 3-node cluster locally. Writes to a follower are redirected to the leader. Killing the leader causes a new election, and no acknowledged writes are lost.

**Why.** Consensus is the core of the system, but writing it first means debugging everything at once. Ratis lets you get the rest of the system correct against a trustworthy implementation. Your interface is the most important design artifact in the project: if it holds, Stage 12 is a swap, not a rewrite.

**Given.** Scripts to launch N local nodes with separate ports and data directories. Acceptance tests that talk only to your HTTP API and to a small `ClusterHandle` test fixture you implement (start/stop/kill node).

**Design gate.** Before implementing, write your `Consensus` interface and a half-page note on why each method exists. The mentor reviews it with questions.

**Guardrails.**
- No Ratis type appears outside `consensus-ratis`. Your API, state machine, and tests see only your interface and your own types.
- The interface expresses *what the application needs* (propose a command and get its result, know the leader, perform a read at a chosen consistency level), not what Ratis happens to offer.
- Nodes have stable IDs independent of network address. (Stage 8 changes addresses.)
- Your Stage 2 state machine is used unchanged. Ratis carries your command bytes.

**Done when.** Writes succeed through any node; the leader is killed mid-workload and the cluster recovers within a bounded time; all acknowledged writes are readable afterward; a follower that was down catches up on restart.

**Read.**
- Raft paper (extended), §5 "The Raft consensus algorithm", all of it, including Figure 2. Read it twice. You're using a library but must understand what it's doing for you.
- The Secret Lives of Data Raft visualization (thesecretlivesofdata.com/raft): 10 minutes, cements §5.
- Ratis `ratis-examples` module, the `arithmetic` or `counter` example: how a state machine plugs in.

**Grill.**
- Walk me through a write from HTTP request to response, naming every node and disk it touches.
- Why 3 nodes and not 2 or 4?
- Ratis has its own retry cache for client requests. Why do you still need your Stage 2 dedup?
- Show me your interface. What would break in it if the implementation were Paxos instead of Raft?

---

## Stage 5: Snapshots

**Goal.** Implement snapshot and restore in your Ratis state machine. The log is compacted after snapshots. A node that's been down long enough to miss compacted entries catches up via snapshot.

**Why.** Logs grow forever otherwise. Snapshots are where subtle bugs hide: a snapshot that doesn't exactly match the log index it claims to represent corrupts a replica silently.

**Given.** Acceptance tests, including one that forces compaction and restarts a lagging node.

**Guardrails.**
- A snapshot captures *all* state machine state, including dedup state, plus the index and term it corresponds to.
- Snapshot files are written atomically. A crash mid-snapshot leaves the previous snapshot intact.
- The snapshot format is yours, not Ratis's. (Your Raft reuses it in Stage 13.)

**Done when.** State after restore equals state from full replay; a node wiped of its log recovers from a peer's snapshot; crash-during-snapshot tests pass.

**Read.**
- Raft paper (extended), §7 "Log compaction".
- Ongaro thesis, Chapter 5, the sections on memory-based snapshots and their concurrency concerns.

**Grill.**
- What happens if the snapshot includes the effect of entry 105 but claims to be at index 100?
- How do you take a consistent snapshot while commands keep being applied?
- Why must dedup state be in the snapshot? Give the failure scenario.

---

## Stage 6: Histories and a linearizability checker

**Goal.** Two tools. (1) A workload client that runs concurrent operations against the cluster and records a history: each operation's invocation, completion, and result. (2) A linearizability checker, written by you, that decides whether a history is linearizable for a KV register model with CAS.

**Why.** "It seems to work" is not evidence. A checker turns vague correctness into a yes/no answer with a counterexample. It's also one of the most interview-worthy parts of the project: you can say precisely what your system guarantees and how you verified it.

**Given.** A set of hand-crafted histories (some linearizable, some not, some with timeouts) as test fixtures. The history file format may be suggested, but the model and checker are yours.

**Guardrails.**
- Operations can end three ways: ok, definitely failed, or **unknown** (timeout, connection reset). The history and checker must represent "unknown" correctly.
- Timestamps come from a single monotonic clock in one client process. (Stage 8 runs clients against remote nodes; wall clocks across machines are not comparable.)
- The checker is independent of the cluster code, so you can run it against any history, including ones from your own Raft and from the simulator later.

**Done when.** The checker classifies all fixture histories correctly, reports a useful counterexample for failures, and handles histories of a few thousand operations across many keys in seconds.

**Read.**
- Herlihy & Wing, "Linearizability: A Correctness Condition for Concurrent Objects" (1990), §1–3: the definition, with examples.
- Jepsen, "Consistency Models" (jepsen.io/consistency), the Linearizable and Sequential pages: where linearizability sits relative to weaker models.
- Anish Athalye, "Testing Distributed Systems for Linearizability" (blog, 2017): a practical account of building a checker, including the algorithm family and the per-key trick.
- Gavin Lowe, "Testing for Linearizability" (2017), sections on the Wing–Gong–Lowe algorithm: the search strategy you'll likely implement.
- Kleppmann's Cambridge "Distributed Systems" lecture on linearizability (free video and notes): a clear second explanation.

**Grill.**
- Define linearizability without using the word "atomic".
- Why does an operation that timed out complicate checking? What does your checker assume about it?
- Why is checking per key valid, and when would it not be?
- What's the worst-case complexity of your checker, and what keeps it tractable in practice?

**Resume signal.** "Wrote a linearizability checker" is a strong, specific line.

---

## Stage 7: Faults and the read-consistency experiment

**Goal.** A fault-injection harness (a "nemesis") that can kill nodes, pause them (`SIGSTOP`/`SIGCONT`), and partition the network between chosen nodes, all on localhost. Then run an experiment: find a non-linearizable history caused by stale reads under the fast read path, and show the linearizable read path eliminates it.

**Why.** This is where distributed systems become real: a partitioned old leader that doesn't know it's been replaced will happily serve stale data. You'll see it, catch it with your checker, fix it, and be able to explain the tradeoff in latency.

**Given.** Acceptance tests that run a fault schedule and your checker. Help with low-level process control if needed.

**Guardrails.**
- Partitions are implemented at a layer you control between nodes (for example, a proxy per peer link), not by reaching into Ratis. (The same harness must work unchanged on your Raft in Stage 12.)
- Fault schedules are seeded and logged, so a failing run can be replayed.
- Read consistency is a per-request choice exposed by your `Consensus` interface, not a global Ratis config baked into the app.

**Done when.** You've produced and saved a non-linearizable history under the fast read path with a fault schedule that reproduces it, and the same schedule with linearizable reads passes the checker over many runs. You've measured the latency difference between the two read paths.

**Read.**
- Ongaro thesis, §6.4 "Processing read-only queries more efficiently", including leases: the read-path options and their assumptions.
- Ratis docs on `raft.server.read.option` (`DEFAULT` vs `LINEARIZABLE`, the latter using ReadIndex), leader lease, and the client's non-linearizable read API.
- One Jepsen analysis end to end. Suggested: "Jepsen: etcd 3.4.3" (2020). Notice how faults, workloads, and checkers combine.
- Kleppmann, *Designing Data-Intensive Applications*, the chapter "The Trouble with Distributed Systems" (ch. 8 in the 1st edition; numbering differs in the 2nd), sections on unreliable networks and process pauses.

**Grill.**
- Describe the exact interleaving that produced your stale read.
- Why doesn't the old leader just know it's no longer leader?
- A lease-based read avoids a round trip. What assumption does it make, and how would a GC pause break it?
- Why is `SIGSTOP` a more interesting fault than `kill -9`?

**Resume signal.** "Found and fixed a stale-read anomaly using a custom fault-injection harness and linearizability checker." That's a story interviewers remember.

---

## Stage 8: Go global

**Goal.** Deploy the Ratis-backed cluster to three regions on cheap VMs. Lock it down, make it observable, and rerun the fault campaign over the real network.

**Why.** Real WAN latency changes behavior: election timeouts that were fine on localhost cause flapping across continents. Operating the system teaches what your logs and metrics actually need to contain.

**Given.** A runnable fat jar for `kv-server` (Maven Shade plugin), and infrastructure scripting help (provisioning, systemd units, firewall rules) if you want it. The ops mechanics are not the learning objective; tuning and diagnosis are.

**Guardrails.**
- The node-to-node port is reachable only from the other nodes. The client API requires authentication.
- Configuration (peers, ports, timeouts, data dirs) is external to the binary. No secrets in the repo.
- Every log line carries node ID, current term, and role. There's a metrics endpoint (leader, term, commit index, apply lag, request latency).
- Workload clients run from a single machine, per Stage 6.
- JVM heap is capped explicitly to fit the VM.

**Done when.** The cluster survives each fault type across regions, your checker passes on linearizable-read histories, and you have a latency table (p50/p99 for writes and each read mode, from your client's location).

**Read.**
- Jeff Hodges, "Notes on Distributed Systems for Young Bloods": short, practical, and every point will be relevant.
- Ratis configuration reference for RPC and election timeouts.
- Raft paper (extended), §5.6 "Timing and availability" (broadcastTime ≪ electionTimeout ≪ MTBF): check it against your measured RTTs.

**Grill.**
- Your cross-region RTT is 90 ms. How did you choose election timeouts, and what happens if you get it wrong in each direction?
- Where should the leader be for your client's latency, and can you control that?
- What in your logs let you diagnose the last incident?

---

# Part III: Your own Raft

## Stage 9: Raft core I (determinism and leader election)

**Goal.** A Raft core that is a pure state machine: it receives messages and ticks, and returns what to persist, what to send, and what to apply. No threads, no clock, no sockets, no randomness except what's injected. Plus an in-memory simulator that runs N cores with a seeded random network (drop, delay, reorder, duplicate). This stage implements leader election only.

**Why.** Real-time, multi-threaded Raft bugs are nearly impossible to reproduce. A deterministic core in a seeded simulator turns "fails once in 500 runs" into "seed 81723 fails, every time". This is how FoundationDB and TigerBeetle are tested, and it's the single biggest factor in how painful the rest of Part III is.

**Given.** Nothing in the core. The simulator's plumbing (event queue, seeded RNG utility) may be scaffolded once your core's interface is designed.

**Design gate.** Before writing code, write a one-page design note: your message types, the core's input/output contract, and the rule for the order in which outputs must be handled. The mentor reviews it with questions. Acceptance tests are written against *your* interface after the gate.

**Guardrails.**
- The core's outputs make "persist before send" enforceable by the caller.
- All time is ticks. All randomness comes from an injected source.
- No iteration-order nondeterminism (for example, over a `HashMap`) anywhere in the core or simulator.
- Messages are immutable values.

**Done when.** Across thousands of seeds with message loss and delay: at most one leader per term, a leader is eventually elected when a majority can communicate, and any failing seed replays identically.

**Read.**
- Raft paper (extended), Figure 2, again. Treat it as a specification. Every sentence is a requirement.
- Raft paper, §5.2 "Leader election".
- Jon Gjengset, "Students' Guide to Raft": written for exactly this moment. Read before coding.
- etcd-io/raft README, the section describing the `Ready` loop: a production example of this input/output design.
- Will Wilson, "Testing Distributed Systems w/ Deterministic Simulation" (Strange Loop 2014 talk): why this architecture.

**Grill.**
- Why must `votedFor` be persisted before the vote response is sent? Give the double-vote scenario.
- Why randomized election timeouts? What happens with fixed ones?
- What does your simulator guarantee that a real network doesn't, and does that hide bugs?
- A seed fails. Walk me through how you debug it.

**Resume signal.** Deterministic simulation testing is increasingly valued and rarely seen in personal projects.

---

## Stage 10: Raft core II (log replication and commitment)

**Goal.** Log replication, the consistency check, conflict resolution, commit index advancement, and applying committed entries, all in the deterministic core.

**Why.** This is where the subtle safety arguments live. The Figure 8 scenario from the paper is a real bug that a natural-looking implementation has.

**Given.** A scripted-scenario test harness where tests can specify exact message deliveries. The mentor writes a Figure 8 reproduction as a test.

**Guardrails.**
- The log interface matches your Stage 3 log (append, range read, last index/term, truncate suffix), so Stage 12 can plug it in.
- Committed entries are handed out through the core's output in order, exactly once.

**Done when.** Scripted scenarios pass (including Figure 8), and randomized simulation checks these invariants after every step: election safety, log matching, leader completeness, and state machine safety (no two nodes apply different commands at the same index).

**Read.**
- Raft paper (extended), §5.3 "Log replication" and §5.4 "Safety", including Figure 8 and the safety argument in §5.4.3.
- Students' Guide to Raft, sections on incorrect optimizations and accelerated log backtracking.

**Grill.**
- Explain Figure 8 without looking. Why can't a leader count replicas for an entry from a previous term?
- A stale, delayed AppendEntries arrives at a follower. Under what rule could it destroy committed entries, and how does your implementation avoid that?
- Which of the four invariants would a naive "always truncate on AppendEntries" violate?

---

## Stage 11: Simulation at scale

**Goal.** Add crash and restart to the simulator (a crashed node loses everything not persisted) and partitions (full and partial). Run large seed sweeps in CI. When a seed fails, automatically shrink the fault schedule to a minimal reproduction. Run your Stage 6 checker on client histories produced inside the simulator.

**Why.** Bugs in consensus code live in rare combinations of crashes and delays. You need volume to find them and minimization to understand them.

**Given.** CI configuration for long-running sweeps.

**Guardrails.**
- Crash semantics are honest: anything the core asked to persist but the "disk" hadn't confirmed is lost. Model the gap.
- The shrinker only removes or simplifies faults; failures must stay reproducible from the shrunk schedule.

**Done when.** Nightly sweeps of many thousands of seeds pass. A deliberately planted bug (the mentor names one, you plant it) is found and shrunk to a short, readable trace. You've found and fixed at least one real bug of your own (you almost certainly will).

**Read.**
- TigerBeetle's writing on its VOPR simulator: how a production system organizes simulation testing.
- Andreas Zeller, *The Debugging Book*, chapter "Reducing Failure-Inducing Inputs" (delta debugging): the shrinking idea.

**Grill.**
- What class of bug can your simulator still not find?
- How do you know your invariant checker isn't vacuously passing?
- Describe the worst bug the simulator found.

---

## Stage 12: The swap

**Goal.** Wrap your core in a runtime: real TCP transport between nodes, real ticks from a timer, your Stage 3 log for persistence plus a durable term/vote file. Implement your `Consensus` interface with it. Run the Stage 4–7 acceptance suites unchanged against your Raft.

**Why.** This is the payoff of the interface boundary. The same tests that validated Ratis now validate your implementation. A failure here is a Raft bug or a runtime bug, never a test bug.

**Given.** Nothing new in tests; the whole point is that they're reused. The mentor adds a switch to select the consensus implementation.

**Guardrails.**
- The runtime is the only place with threads. Exactly one thread (or a single-threaded executor) drives the core.
- The ordering rule from your Stage 9 design note is enforced in the runtime.
- Switching implementations is a config change. Nothing in `kv-server` changes.

**Done when.** Stage 4–7 acceptance suites pass against your Raft, including the fault campaign with the checker, over many runs.

**Read.**
- Re-read your Stage 9 design note and the etcd-io/raft `Ready` section. Most runtime bugs are violations of the contract you already wrote.

**Grill.**
- What's different about your runtime compared to the simulator, and which of those differences could hide or create bugs?
- A slow disk stalls `fsync` for 2 seconds on the leader. What does your cluster do?
- What did you have to change outside the `raft` module, and what does that say about your interface?

---

## Stage 13: Compaction and InstallSnapshot

**Goal.** Snapshotting and log compaction in your Raft, including sending snapshots to followers that are too far behind, using the Stage 5 snapshot format.

**Why.** Compaction touches every index calculation in the core. It's a classic source of off-by-one safety bugs.

**Given.** Simulator scenarios that force a follower far behind a compacted leader.

**Guardrails.**
- The simulator's invariants still hold with compaction. Extend them to account for a compacted prefix.

**Done when.** Simulation sweeps with compaction pass, lagging nodes recover via snapshot, and the Stage 5 acceptance tests pass on your Raft.

**Read.**
- Raft paper (extended), §7 again, with Figure 13 (InstallSnapshot RPC).
- Ongaro thesis, Chapter 5, the rest of the chapter.

**Grill.**
- A follower receives a snapshot covering entries it already has, plus more. What should it keep?
- Where did compaction force you to change index arithmetic?

---

## Stage 14: Reads and liveness

**Goal.** Linearizable reads via ReadIndex in your Raft, plus PreVote and CheckQuorum. Optionally, lease-based reads with an explicit clock-drift bound.

**Why.** Safety isn't enough; the cluster must stay available. A real outage at Cloudflare in 2020 came from a partially partitioned node repeatedly disrupting an etcd cluster's leadership. PreVote and CheckQuorum are the fixes.

**Given.** A simulator scenario for a partial partition (one node can reach only some peers) and a liveness check (a leader stays stable when a majority is healthy).

**Guardrails.**
- ReadIndex reads must remain linearizable under every fault your simulator can produce. Your checker runs on simulated histories too.
- PreVote must not change any persisted state.

**Done when.** The partial-partition scenario keeps a stable leader with PreVote and CheckQuorum (and demonstrably doesn't without them), and linearizable reads pass the checker under fault sweeps.

**Read.**
- Ongaro thesis, §6.4 (read-only queries) and §9.6 (preventing disruptions when a server rejoins the cluster, which covers PreVote).
- Cloudflare blog, "A Byzantine failure in the real world" (November 2020).

**Grill.**
- Why does a new leader need to commit an entry from its own term before serving ReadIndex reads?
- Explain the Cloudflare incident and which mechanism fixes which part.
- Lease reads are faster. Would you ship them? Under what deployment assumptions?

---

## Stage 15: Global campaign and write-up

**Goal.** Deploy your Raft to the three regions, run the full fault campaign, compare against the Ratis deployment, and publish a write-up.

**Why.** A system nobody can read about doesn't help your resume. The write-up forces you to state what you guarantee, how you verified it, and what you found.

**Given.** The mentor reviews your write-up with questions; it doesn't write it.

**Done when.** A README or blog post covering: architecture, the consistency guarantee and how it's verified, the bugs your tooling found (with traces), the stale-read experiment, and a latency comparison between Ratis and your Raft with an honest explanation of differences.

**Read.**
- Two or three Jepsen analyses, for the structure of a good correctness write-up.

**Grill.** The mentor runs a full mock interview on the whole project.

---

## Extensions (pick any, after Stage 15)

- **Membership changes** (single-server changes, thesis Chapter 4). Hard, and famous for a bug found after publication; excellent grill material.
- **Sharding with multiple Raft groups**: consistent hashing or range partitioning, one Raft group per shard.
- **Batching and pipelining**: measure throughput before and after, explain the tradeoffs.
- **Maelstrom `lin-kv` workload**: an independent check of your implementation against Jepsen's tooling.
