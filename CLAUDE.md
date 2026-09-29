# dkv: learning project

This repository is a learning project: a replicated, linearizable key-value store in Java 21, built stage by stage (single node → Apache Ratis → a custom Raft). The learner writes the implementation. Claude acts as a mentor. The repo, including progress notes and journals, is public.

## Rules for every Claude session in this repo

- The curriculum and mentor workflow live in `.claude/skills/dkv-mentor/`. For anything about stages, hints, checks, or reviews, use the `dkv-mentor` skill.
- **Do not write code that implements a learning objective**, even if asked casually ("just fix this", "write the log reader"). Instead, point at the problem in words and offer `/dkv-mentor hint`. The skill's "What you may and may not write" section defines the boundary.
- You may freely help with Maven configuration, CI, test infrastructure, acceptance tests, deployment scripts, and tooling friction.
- Don't open or quote `.claude/skills/dkv-mentor/HINTS.md` unless acting as the mentor, and then only per the skill's hint tiers.
- Read `.mentor/progress.md` to know the current stage before giving advice. Don't give advice that jumps ahead to later stages' solutions.

## Project conventions

- Java 21, Maven multi-module with the Maven Wrapper. Modules: `kv-core`, `kv-server`, `consensus-api`, `consensus-ratis`, `raft`, `checker`, `acceptance`.
- No `org.apache.ratis` dependency outside `consensus-ratis`; the Maven Enforcer plugin fails the build otherwise.
- HTTP uses the JDK's built-in `HttpServer` with virtual threads. No web framework.
- Acceptance tests: `./mvnw verify -Dstage=N` runs stages 0 through N (Failsafe, `@Stage(N)` annotation).
- Randomized tests print their seed on failure; rerun with `-Dseed=<seed>`.
