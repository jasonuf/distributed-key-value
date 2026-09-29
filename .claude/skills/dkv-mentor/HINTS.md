# dkv mentor hint bank

**Mentor-only.** Learner: if you're reading this, close it; it's the answer key.

For each stage: the traps learners commonly hit, what to look for when reviewing, and a stretch goal. Use these to shape tiered hints. Reveal a trap only when the learner is actually hitting it (a failing test, a code smell in review, or a question). Phrase hints in your own words at the right tier; the notes here are written at tier 3 so you know where each hint ladder ends.

---

## Stage 0: Workbench

- Traps (mostly the mentor's own, since Stage 0 is scaffolding): Failsafe not bound to `verify`, so acceptance tests never run; test classes not matching Failsafe's default `*IT` naming (either name them `*IT` or configure includes); `-Dstage` and `-Dseed` not forwarded to the forked test JVM via `systemPropertyVariables`, so stage selection silently runs everything or nothing; Enforcer rule checking only direct dependencies when Ratis could arrive transitively (use `bannedDependencies` with transitive search); CI using a different JDK than local. Always prove the suite runs by making a test fail on purpose.
- Stretch: a `./mvnw -q verify -Dstage=N` wrapper script that prints a codecrafters-style summary (stage, passed, failed requirement names).

## Stage 1: Single-node KV

- **Shared HashMap across HTTP handler threads.** With virtual threads each request runs concurrently. Races show up as lost updates in the concurrent-client test. The deeper fix, which pays off later: a single applier (one thread or a lock around apply) rather than a concurrent map, because apply order must be a total order once there's a log.
- **Handler mutates the map directly** for "simple" operations. Breaks the submit-and-wait guardrail; flag it as a Stage 4 problem.
- **Commands as lambdas or classes with behavior-bound references** instead of plain data. Stage 4 can't serialize them.
- **Nondeterminism in results**, e.g. returning `map.keySet()` order, or embedding timestamps in responses from inside apply.
- **Encoding ambiguity.** If keys/values are Strings, which charset? Unspecified charset means platform default; call it out.
- Review: is there exactly one code path that changes state? Could apply be called with bytes from another machine?
- Stretch: a `SCAN prefix` read command with deterministic ordering (sorted map), which forces thinking about ordered structures.

## Stage 2: Exactly-once commands

- **Dedup table stored outside the state machine** (e.g. in the HTTP layer). Works on one node, fails on failover because the new leader never saw it. Tier 3: "Which component does every replica run identically? The dedup data must live there."
- **Caching only a boolean "seen"** instead of the original response. The retry must receive the same result the first attempt produced (e.g. CAS failed because the value was X).
- **Sequence gaps.** If the client may have multiple outstanding requests, "keep last seq only" is wrong. Ask what assumption they make about clients (one outstanding request per client is the standard, simple choice; it's fine if stated).
- **Accepting an older seq than the latest** and applying it. Old retries arriving late must return the cached response or an error, never re-apply.
- **Session expiry by wall clock inside apply.** Breaks determinism. Deterministic options: expire by log index distance, or have the leader stamp a timestamp into the command so all replicas see the same value (thesis §6.3 discusses this).
- **CAS on a missing key**: define semantics explicitly (expected-absent).
- Review: does the Stage 1 determinism test still include dedup state in the comparison?
- Stretch: session registration as an explicit command, as in the thesis.

## Stage 3: Durable log

- **Checksum doesn't cover the length field.** A torn or corrupted length makes recovery read garbage or past EOF. Checksum should cover header and payload, or the length must be validated independently.
- **Treating any read error as "end of log".** Then mid-file corruption silently truncates acknowledged data. Distinguish: a bad record followed by nothing (or only zeros) at the tail = torn write; a bad record followed by valid records = corruption, fail loudly.
- **Not fsyncing the directory** after creating the log file (or after rename). The file itself can vanish on crash.
- **`FileChannel.force(false)` vs `force(true)`**: false may skip metadata like file length on some systems. For an appending log, length matters. Let them reason about it.
- **Truncating in memory but not on disk** after detecting a torn tail; the next append then writes after garbage.
- **Buffered output stream** (`BufferedOutputStream`) flushed but not forced. Flush moves bytes to the OS, not to disk.
- **Acknowledging before fsync returns.** Check the order in their write path.
- **Retrying after fsync failure.** Per the fsyncgate paper: after a failed fsync, the page cache state is unknown; the safe move is to crash and recover from the log.
- **Suffix truncation API** exists but is untested; ask them to add a unit test even though it's unused.
- Review: is there a separate place planned for term/vote metadata? Is the index contiguous and checked on recovery (no gaps, no duplicates)?
- Stretch: group commit (batch several appends into one fsync) and measure throughput vs latency.

## Stage 4: Three nodes with Ratis

- Design gate traps: interface that returns Ratis `CompletableFuture<RaftClientReply>` or takes `Message`; an interface that assumes the local node can always serve reads; no way to express read consistency; leader info as an address instead of a node ID.
- **Ratis state machine mechanics** (friction, fine to help at tier 3 quickly): extend `BaseStateMachine`; `applyTransaction` returns a `CompletableFuture<Message>`; call `updateLastAppliedTermIndex` after applying; reads go through `query`. Look at `ratis-examples` for the shape.
- **Every node needs the same `RaftGroupId`** and the full peer list; separate storage directories per node, or nodes will corrupt each other's storage.
- **Leader redirect**: Ratis raises `NotLeaderException` with a suggested leader; map it to your own error type in the adapter.
- **Ratis retry cache vs your dedup**: Ratis dedups by Ratis client ID and call ID, which only covers retries from the same Ratis client instance. An HTTP client that retries via a different node uses a different Ratis client, so the app-level dedup is still required.
- **Blocking inside `applyTransaction`** on anything slow stalls the apply pipeline.
- Review: grep for `org.apache.ratis` imports outside the adapter module.
- Stretch: expose Ratis's leadership-change events through your interface as generic callbacks, useful for Stage 8 metrics.

## Stage 5: Snapshots

- **Dedup state missing from snapshot.** Test: retry a request after a node restores from snapshot; it gets applied twice.
- **Snapshot content not matching its index.** Ratis may call `takeSnapshot` concurrently with applies; if they serialize the live map without holding the apply lock (or copying), the snapshot contains a mixture. The index recorded must be the last applied index at the exact moment of the copy.
- **Non-atomic write**: write to a temp file, fsync, rename, fsync directory. Otherwise a crash leaves a half snapshot that looks valid.
- **Restore not clearing old state** before loading.
- **Snapshot tied to Ratis's format** (e.g. using Ratis's `SimpleStateMachineStorage` layout as the canonical format). Fine to use its file management, but the serialized content should be their own format so Stage 13 can reuse it.
- Stretch: incremental or copy-on-write snapshots so applies don't pause.

## Stage 6: Histories and a checker

- **Timeouts recorded as failures.** A timed-out write may have happened. Model: an unknown op's completion time is infinity (it may linearize anywhere after its invocation, or not at all for writes whose effects are never observed). Treating it as failed makes the checker report false violations (or, if dropped entirely, miss real ones).
- **Wall-clock timestamps** or timestamps from multiple processes. Must be `System.nanoTime()` from one JVM.
- **Checking the whole history as one object.** Exponential. Per-key partitioning (P-compositionality) is valid because linearizability is local (Herlihy & Wing, locality theorem); it would not hold for multi-key transactions.
- **WGL without memoization** of (set of linearized ops, model state) blows up. The cache is what makes it practical. A bitset for the linearized set works well.
- **Model bugs** (CAS on absent key, read of absent key returning null vs empty) create false positives. Test the model separately.
- **Useless counterexamples.** Encourage reporting the longest linearizable prefix and the ops that couldn't be placed.
- **Too much concurrency per key** makes checking slow; the workload should use many keys and a small number of concurrent clients per key.
- Stretch: visualize a counterexample as an ASCII or HTML timeline; cross-check a few histories against Porcupine or Knossos.

## Stage 7: Faults and reads

- **Can't reproduce a stale read.** It needs: a client pinned to the old leader; the old leader isolated in the minority; a write accepted by the new majority leader; then a read from the pinned client served locally by the old leader before it steps down. Ratis with `DEFAULT` read option serves reads from the leader's state machine without confirming leadership, which is what exposes this. The window is roughly an election timeout, so the schedule must be timed.
- **Partitions via iptables on localhost** are awkward because all nodes share one IP. A small TCP proxy per directed peer link (or Toxiproxy) gives per-link control. Ratis peers must be configured to connect through the proxy addresses.
- **Proxies that only block new connections** but leave established connections alive. Partitions must affect existing connections (close them or stop forwarding bytes).
- **Unseeded nemesis** or not logging the schedule; failing runs can't be replayed.
- **Clients retrying reads forever** masks errors; reads that fail should be recorded as failed (reads have no side effects, so a failed or unknown read can be dropped from the history).
- **Lease reads and SIGSTOP**: a paused leader resumes believing its lease is valid; with clock-based leases this is the classic failure. Good grill material; don't pre-empt it.
- Stretch: asymmetric partitions (A can send to B but not receive).

## Stage 8: Go global

- **Election timeouts too low for WAN RTT.** Ratis defaults are tuned for LANs; cross-region heartbeats can arrive late and trigger elections. Symptom: term climbing steadily in metrics. Tier 3: compare measured RTT to the configured min election timeout.
- **JVM heap not capped**: OOM killer on small VMs, looks like a random crash.
- **Firewall rules that also block the proxy/nemesis control ports**, or that are applied only in memory and vanish on reboot.
- **Running the workload client from several machines** and merging histories with wall clocks.
- **Leader location**: without a preference, the leader may land far from the client and double latency; Ratis supports leadership transfer and priorities. Let them discover it through the latency table.
- **systemd restart loops hiding crashes.** Check they have crash logs.
- **Fat jar breaks Ratis at startup** (mentor-owned, since the jar is Given): the Shade plugin must merge `META-INF/services` files (`ServicesResourceTransformer`), or gRPC/Netty service loading fails only in the packaged jar, never in tests.
- Stretch: leader priority or transfer to minimize client latency, measured before and after.

## Stage 9: Raft core I

- Design gate traps: a core that calls `send()` directly (a network interface injected into the core) instead of returning messages; outputs that don't separate "persist" from "send"; time as `long millis` instead of ticks; no way for the caller to confirm persistence before sending.
- **Persist before send.** If a node sends a vote before `votedFor` is durable, a crash and restart lets it vote twice in the same term: two leaders.
- **Resetting the election timer on every message.** Should reset only on a valid AppendEntries from the current leader or when granting a vote. Resetting on any message (e.g. a rejected vote request) can prevent elections forever.
- **Not stepping down on a higher term** in *any* message or response, including responses.
- **Counting votes without checking the term** of the response, so votes from an earlier term elect a leader.
- **Candidate forgetting to vote for itself** (and persist it).
- **Nondeterminism**: `HashMap` iteration over peers, `System.nanoTime` in logging, `Math.random` somewhere. A replayed seed diverging is the tell. Tier 3: dump the event trace for the same seed twice and diff.
- **Simulator delivers messages instantly or in order** by default, hiding bugs. The network should drop, delay, reorder, and duplicate.
- Stretch: a trace viewer that prints per-node state per tick for a seed.

## Stage 10: Raft core II

- **Figure 8.** Leader commits an entry from a previous term by counting replicas. Rule: only advance commitIndex by counting replicas for entries from the leader's current term; earlier entries commit indirectly.
- **Always truncating the follower log** on AppendEntries from `prevLogIndex+1`. A delayed, stale AppendEntries (shorter than what the follower already has) then deletes entries that may be committed. Correct: truncate only at the first actual conflict (same index, different term), then append entries not already present.
- **commitIndex on follower** set to `leaderCommit` without capping at the index of the last new entry; the follower may commit entries it doesn't have or that belong to a stale suffix.
- **nextIndex/matchIndex updates from stale responses.** A delayed response for an older AppendEntries can move `matchIndex` backward or `nextIndex` wrongly. Check the term and use the response's own index information.
- **Leader not appending a no-op on election**, so nothing from the current term is ever committed and earlier entries never commit (also matters for Stage 14).
- **Applying before commit** or applying twice after restart.
- **Invariant checker** that only checks at the end of a run instead of after every step.
- Stretch: accelerated backtracking using conflict term and index.

## Stage 11: Simulation at scale

- **Crash model too kind**: persisting instantly and atomically. Model the gap between "asked to persist" and "persisted"; a crash in between loses it.
- **Restart losing persistent state or keeping volatile state** (commitIndex and lastApplied are volatile in the paper; restart must recompute).
- **Vacuous invariants**: invariant checker that never sees a leader, or sweeps where no faults actually fire. Suggest counting how often each fault fires and asserting coverage. The planted-bug exercise exists to validate the checker.
- **Planted bug suggestions** (pick one they haven't hit): remove the current-term check in commit advancement (Figure 8), or skip persisting votedFor.
- **Shrinker changing behavior**: if the message-ordering RNG consumption shifts when a fault is removed, the shrunk schedule may no longer reproduce. Separate RNG streams for network behavior and for faults, or record decisions.
- **Liveness assertions too strict** during partitions; only assert liveness in windows where a majority is healthy.
- Stretch: coverage-guided seed exploration (prefer seeds that reach new states).

## Stage 12: The swap

- **Multiple threads touching the core** (a network reader thread calling `step` directly while the timer thread calls `tick`). All inputs must go through one queue to one driving thread.
- **Sending before persisting** in the runtime, even though the core's contract says otherwise. Check the Ready-handling loop order.
- **Blocking the driver thread on fsync** stalls heartbeats. It's okay at this scale if understood; ask them what happens under a slow disk (grill).
- **Connection handling**: reconnect storms, no timeouts, unbounded send queues to a dead peer (memory growth).
- **Term/vote file not fsynced** or written non-atomically.
- **Tests needing changes**: if any Stage 4–7 test needs to change, the interface leaked. Discuss which part leaked before touching tests.
- Stretch: pipeline AppendEntries (multiple in flight per follower).

## Stage 13: Compaction

- **Off-by-one on the snapshot boundary**: `prevLogIndex == snapshotIndex` must use the snapshot's term for the consistency check.
- **Follower discarding its whole log** on InstallSnapshot even when it has entries past the snapshot that match. Per Figure 13: if an existing entry has the same index and term as the snapshot's last entry, keep the entries following it.
- **Leader compacting entries a follower still needs** without switching that follower to InstallSnapshot.
- **Invariants that index into the compacted log** and throw or silently skip.
- Stretch: chunked snapshot transfer with resume.

## Stage 14: Reads and liveness

- **ReadIndex before the leader has committed an entry in its term**: the leader may not know the true commit index yet. The no-op on election is what fixes this.
- **ReadIndex without confirming leadership** with a heartbeat round (majority ack after recording the read index).
- **Serving the read before applying up to the read index.**
- **PreVote incrementing the term or persisting a vote.** It must not change persistent state.
- **PreVote granted while the voter has heard from a leader recently**: the point is to deny when a leader is alive. Check the "leader recently seen" condition.
- **CheckQuorum leader not stepping down** when it hasn't heard from a majority within an election timeout.
- **Lease reads**: lease must be shorter than election timeout minus a clock-drift bound, and measured from when the heartbeat was *sent*, not when acks arrived.
- Stretch: follower reads via ReadIndex.

## Stage 15: Write-up

- Common weaknesses: claims without evidence ("it's linearizable"); benchmark numbers without setup details; hiding the bugs found (the bugs are the most interesting part); no explanation of what is *not* guaranteed.
- Review via questions only: "How would a reader verify this claim?", "What would a Jepsen reviewer ask here?"
- Mock interview checklist: architecture in 2 minutes; one write end to end; the stale-read story; the worst bug and how it was found; why a custom Raft and what they'd change; what breaks at 10x scale.
