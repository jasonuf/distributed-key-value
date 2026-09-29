# dkv mentor kit

A Claude Code mentor for building a replicated key-value store in Java 21 with Maven, codecrafters-style.

## Install

Copy the contents of this folder into the root of your (new, empty) project repo:

```
your-repo/
├── CLAUDE.md                              # repo rules for every Claude session
└── .claude/skills/dkv-mentor/
    ├── SKILL.md                           # mentor workflow, rules, agreed assumptions
    ├── ROADMAP.md                         # 16 stages; read freely
    └── HINTS.md                           # mentor's answer key; don't open
```

If you already have a `CLAUDE.md`, merge the two.

Requirements: Linux or macOS (or WSL), JDK 21, Git, a GitHub repo for CI. Maven itself comes via the wrapper that Stage 0 sets up.

## Use

Start Claude Code in the repo and run:

- `/dkv-mentor next`: start the next stage (begins with Stage 0, the workbench)
- `/dkv-mentor hint`: the next hint tier for what you're stuck on (nudge → concept → specific → pseudocode on request; never code)
- `/dkv-mentor check`: run `./mvnw verify -Dstage=N`, review your code against future stages, then get grilled
- `/dkv-mentor grill`: practice explaining your design (`grill all` for a mock interview)
- `/dkv-mentor review <path>`: mentor-style code review
- `/dkv-mentor why <topic>`: background on a concept
- `/dkv-mentor decide <text>`: record a design decision
- `/dkv-mentor`: status and the next thing to do

The mentor keeps your state in `.mentor/progress.md`, and asks you to keep short notes per stage in `docs/journal/` and design notes in `docs/design/`. All of it is committed and public, so the repo shows how you worked, not just the final code. Those files become the raw material for your write-up and your interview answers.
