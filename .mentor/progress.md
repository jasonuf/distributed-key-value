# dkv progress

Current stage: 0
Status: complete   <!-- not started | in progress | design gate | checking | complete -->

## Stage 0 checklist
- [x] Scaffolding given by mentor: parent + 7 module POMs, Maven Wrapper (Maven 3.9.16), Ratis ban via Enforcer, `@Stage`/`@Seeded` harness, CI workflow, Spotless (google-java-format)
- [x] `./mvnw verify -Dstage=0` passes locally (verified 2026-09-29: 3 run, 1 skipped as intended)
- [x] Experiment: add a Ratis dependency to `kv-core`, see the build fail, revert (efced55 failed CI at Verify via the Enforcer ban; reverted in 868288f)
- [x] Experiment: add a deliberately failing `@Stage(0)` test, see it reported, remove it
- [x] Repo pushed to GitHub; CI green (868288f passed on 2026-09-29)
- [x] Reading: JEPs 395, 409, 441, 444
- [x] Grill (2026-09-29): passed. Explained the consensus boundary as dependence on an owned interface so Stage 12 is a swap; identified transitive dependencies as the leak path the build must catch.

## Completed stages
<!-- stage, date, one-line summary -->
- 0 | 2026-09-29 | Workbench: Maven multi-module build with a build-enforced Ratis boundary, stage-selected acceptance tests under Failsafe, and CI. Verified by deliberately breaking the boundary and the test suite.

## Hints used
<!-- stage | topic | tier -->

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
