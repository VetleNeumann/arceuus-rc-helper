# Coding standards

What `./gradlew build` cannot check. Style itself is enforced by `config/checkstyle/checkstyle.xml` (RuneLite's rules: tabs, braces on their own line, no trailing whitespace, no unused or wildcard imports) and the build fails on any violation, so this file only carries the conventions a tool cannot see.

## Java

- Match the surrounding code: RuneLite core conventions, one flat import block, static wildcard imports allowed, `final` on fields set once.
- Lombok is in: `@Getter`, `@Value`, `@Slf4j`, `@RequiredArgsConstructor`. Constructor injection for services and overlays; field injection only in the plugin class.
- Name things with the vocabulary in `CONTEXT.md`. A name that needs the glossary changed goes with a glossary edit in the same PR.
- `log.debug` for diagnostics. `log.info` only for one-off lifecycle messages: RuneLite ships at INFO and per-tick info lines pollute every user's log.
- Anything the Plugin Hub restricts is in `docs/RUNELITE-RULES.md`; checkstyle covers the mechanical subset.

## Tests

- JUnit 4 + Mockito, same as RuneLite core (JUnit 5 was considered and rejected to stay aligned with upstream and the hub's build tooling).
- New or changed logic gets a unit test in the same PR. Overlays are exempt; everything they draw comes from services that are testable.
- A flaky test is fixed or deleted the day it flakes. The whole suite runs in well under a minute; keep it there.
- In-game behaviour is verified by a human in the dev client (`docs/DEV-LOOP.md`). A green build is a precondition, not a pass.

## Commits and PRs

- Conventional Commits: `feat:`, `fix:`, `refactor:`, `build:`, `ci:`, `docs:`, `test:`, `chore:`. Subject in the imperative, body explains why.
- One concern per PR. Soft cap around 300 changed lines; a bigger diff gets split, with the logic-layer change landing first and the overlay change second.
- One open PR at a time. Finish it before opening the next.
- Docs move with the code: `README.md`, `CONTEXT.md`, `docs/` and the `Unreleased` section of `CHANGELOG.md` (for `feat` and `fix`) are updated in the same PR.

## Branching

- Trunk-based: branch from `master`, live less than a day, squash-merge back. `master` is protected: CI must be green, history stays linear, no force pushes.
- CI is the approval. Review happens on the PR when a second person is around; solo, the checklist in the PR template is the review.

## Dependencies

- `net.runelite:client` floats on `latest.release`, matching the hub. The weekly snapshot workflow builds against `latest.integration` so upstream API changes show up early.
- Everything else is pinned and bumped by Dependabot in grouped weekly PRs.
- No new runtime dependencies. RuneLite's transitive libraries (gson, okhttp, guice) are used through injection, not declared.

## Releases

1. Set `version=` in `runelite-plugin.properties` (the only place the version lives; Gradle reads it).
2. In `CHANGELOG.md`, rename `## [Unreleased]` to `## [x.y.z] - YYYY-MM-DD` and add a fresh empty `Unreleased` above it.
3. Commit as `chore: release x.y.z`, merge, then tag: `git tag vx.y.z && git push origin vx.y.z`.
4. The release workflow checks the tag against the properties file, builds the jar and publishes a GitHub Release with the changelog section as its body.

Publishing to the Plugin Hub, when that day comes, is one more step: a manifest PR pointing at the tagged commit.
