# dkv progress

Current stage: 1
Status: in progress   <!-- not started | in progress | design gate | checking | complete -->

## Stage 1 checklist
- [x] Given by mentor: HTTP spec (`docs/http-api.md`), node launcher (`NodeProcess`, `KvClient`), acceptance tests (`Stage01CrudIT`, `Stage01ConcurrencyIT`, `Stage01DeterminismIT`)
- 2026-10-05 mentor test fix: added `Stage01CrudIT.differentEncodingsOfTheSameKeyNameTheSameKey`; the original suite didn't check that keys are percent-decoded, as the spec requires
- [ ] Reading: Raft paper §2; Schneider (1990) §1–3
- [x] `dev.jason.dkv.server.Main` starts and serves `/kv/{key}` per the spec
- [ ] `./mvnw verify -Dstage=1` passes locally and in CI (local: 21 run, 0 failed on 2026-10-05; CI pending, needs `spotless:apply`)
- [ ] Guardrail review (commands as data, deterministic apply, submit-and-wait, reads expressible as commands). 2026-10-05 first pass: not yet met; state lives in the HTTP handler, no commands or `apply` in `kv-core`
- [ ] Grill

## Completed stages
<!-- stage, date, one-line summary -->
- 0 | 2026-09-29 | Workbench: Maven multi-module build with a build-enforced Ratis boundary, stage-selected acceptance tests under Failsafe, and CI. Verified by deliberately breaking the boundary and the test suite.

## Hints used
<!-- stage | topic | tier -->
- 1 | Separating commands and `apply` (kv-core) from HTTP handling (kv-server) | 1

## Decisions
<!-- date | decision | reason | stages affected -->
- 2026-09-29 | Ratis is banned (transitively) in every module except `consensus-ratis` and `acceptance` | The consensus boundary is enforced by the build. `acceptance` is exempt because it starts whole nodes and clusters. | 4–15
- 2026-09-29 | Compile with `--release 21` (local JDK is 25) | Matches CI and the agreed stack. Newer-than-21 APIs fail to compile instead of slipping in. | all
- 2026-09-29 | CI runs stages 0..N, where N is `Current stage:` in this file | Each push is checked against the current stage. CI stays red until that stage's tests pass. | all
- 2026-09-29 | Journals are free-form: notable design points and decisions only, not the three-question template | Learner's preference; keeps journaling lightweight | all

## Skipped objectives
<!-- stage | objective | what it affects -->

## Revisit
<!-- concepts from grill sessions to come back to -->
- Stage 0 | Build rules constrain the dependency graph (which modules may know about which), not stages or imports; why a leaf module like `acceptance` can be exempt without weakening the guarantee. Revisit at the Stage 4 design gate.

## Resume bullet ideas
<!-- stage | raw idea (learner phrases the final version) -->
