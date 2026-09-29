# dkv progress

Current stage: 0
Status: in progress   <!-- not started | in progress | design gate | checking | complete -->

## Stage 0 checklist
- [x] Scaffolding given by mentor: parent + 7 module POMs, Maven Wrapper (Maven 3.9.16), Ratis ban via Enforcer, `@Stage`/`@Seeded` harness, CI workflow, Spotless (google-java-format)
- [x] `./mvnw verify -Dstage=0` passes locally (verified 2026-09-29: 3 run, 1 skipped as intended)
- [ ] Experiment: add a Ratis dependency to `kv-core`, see the build fail, revert
- [ ] Experiment: add a deliberately failing `@Stage(0)` test, see it reported, remove it
- [ ] Repo pushed to GitHub; CI green
- [ ] Reading: JEPs 395, 409, 441, 444
- [ ] Grill

## Completed stages
<!-- stage, date, one-line summary -->

## Hints used
<!-- stage | topic | tier -->

## Decisions
<!-- date | decision | reason | stages affected -->
- 2026-09-29 | Ratis is banned (transitively) in every module except `consensus-ratis` and `acceptance` | The consensus boundary is enforced by the build. `acceptance` is exempt because it starts whole nodes and clusters. | 4–15
- 2026-09-29 | Compile with `--release 21` (local JDK is 25) | Matches CI and the agreed stack. Newer-than-21 APIs fail to compile instead of slipping in. | all
- 2026-09-29 | CI runs stages 0..N, where N is `Current stage:` in this file | Each push is checked against the current stage. CI stays red until that stage's tests pass. | all

## Skipped objectives
<!-- stage | objective | what it affects -->

## Revisit
<!-- concepts from grill sessions to come back to -->

## Resume bullet ideas
<!-- stage | raw idea (learner phrases the final version) -->
