# Arceuus RC Helper

RuneLite plugin, Java 11, Gradle. Hard fork of JamsRepos/zeah-rc-helper, kept Plugin Hub compatible but unpublished.

## Every change

1. **Speak the glossary.** `CONTEXT.md` defines the domain words (Rotation, Step, Trip, Path, Reminder, Rune). Use them in code, commits and docs; a new domain word lands in `CONTEXT.md` in the same PR.
2. **Check the hub rules first** when a change touches threading, network, files, config keys, menus, input, or anything combat-related: `docs/RUNELITE-RULES.md`. A violation means rejection at hub review; checkstyle catches only the mechanical subset.
3. **Write to `docs/STANDARDS.md`.** It covers what checkstyle cannot: tests, commit messages, PR size, docs that move with code, branching, releases.
4. **Build green.** `./gradlew build` runs compile with Error Prone, Spotless, checkstyle and tests; done means zero violations and zero failures. `./gradlew spotlessApply` fixes formatting. Needs JDK 21 (`docs/adr/0006-build-on-jdk-21-target-java-11.md`).
5. **Deploy** with `./dev.sh` (details and one-time setup, including `./gradlew installGitHooks`: `docs/DEV-LOOP.md`).
6. **Hand over for in-game verification.** Say which Steps of the Rotation, which Reminders and which edge cases to exercise. Only the user can confirm behaviour in game, and automating game input is against Jagex's rules. The task is complete when they confirm, not when the build passes.

## AI stance

- **Prohibited:** injecting game input or automating play, even for testing; anything in the feature restrictions of `docs/RUNELITE-RULES.md`; committing credentials (`credentials.properties`).
- **Guarded (ask first):** renaming a config key or group, adding a runtime dependency, changing `runelite-plugin.properties`, force-pushing, retagging a release.
- **Allowed:** everything else in the loop above, including opening PRs and merging them once CI is green.
