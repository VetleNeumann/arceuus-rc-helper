# Dev loop

RuneLite needs a display and a GPU, and this repo is developed from WSL, so `./gradlew run` is not the loop. Instead `dev.sh` builds a shadow jar (full client plus plugin), copies it to a folder on the Windows side and drives the client there through `tools/dev.ps1` using RuneLite's bundled JRE. Rationale: `docs/adr/0001-dev-loop-sideloaded-jar.md`.

```bash
./dev.sh            # build, copy, restart the dev client
./dev.sh build      # build and copy only
./dev.sh log        # tail the dev client's stdout/stderr
./dev.sh status     # start | stop | restart | status
```

The Windows folder defaults to `C:\Users\zantox\runelite-dev`; override with `RL_DEV_DIR=/mnt/c/...`. Logs land there as `dev-client.log` and `dev-client.err.log`. Enable the plugin in the client under Configuration → Arceuus RC Helper.

## One-time setup (git hooks)

```bash
./gradlew installGitHooks   # sets core.hooksPath to .githooks for this clone
```

`pre-commit` runs `spotlessCheck` and refuses the commit on a formatting violation; it never rewrites files, so run `./gradlew spotlessApply`, review the diff and commit again. `pre-push` runs the full `./gradlew build`. Both are plain bash in `.githooks/`, versioned with the code; `--no-verify` bypasses them when CI is meant to see a failure.

## One-time setup (Jagex account login)

The dev client cannot log in through the Jagex Launcher, so it reuses the credentials the normal client writes:

1. In `%LOCALAPPDATA%\RuneLite\settings.json` set the client argument `--insecure-write-credentials` (the "RuneLite (configure)" GUI does the same).
2. Launch RuneLite once through the Jagex Launcher and log in. That writes `%USERPROFILE%\.runelite\credentials.properties`.
3. From then on the dev client logs in with that file.

Full background: [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

**Security:** `credentials.properties` holds Jagex session tokens. Never share it, copy it into the repo, or leave it around after a development stint. When done, remove the argument from `settings.json`, delete the file, and use "End sessions" in the RuneScape account settings to invalidate it.

## Verifying a change

Only a human can confirm behaviour in game, and automating game input is against Jagex's rules. After deploying, the person testing needs to know which Steps of the Rotation, which Reminders, and which edge cases the change touches. Typical exercise:

- Stand at the Mine with an empty inventory: Step reads `Mine`, the next Dense Runestone is highlighted.
- Full inventory of Dense Blocks: Path to the Dark Altar draws; Venerate highlights the altar.
- Log out and back in mid-Rotation: Step re-infers correctly, Trip count unchanged.
- Toggle Helper off: highlights and panel vanish, Reminders keep firing.
