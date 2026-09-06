# Dev loop runs a sideloaded shadow jar on Windows, not `./gradlew run`

Development happens in WSL, which has no usable display or GPU for RuneLite, while the game client runs fine on the Windows host. Rather than set up WSLg or a second JDK on Windows, `dev.sh` builds the example-plugin `shadowJar` (full client plus plugin) in WSL, copies it to a Windows folder and starts it with RuneLite's own bundled JRE via `tools/dev.ps1`. `./gradlew run` still exists for anyone developing on a machine with a display; it is simply not the documented loop here.

## Consequences

- Both scripts are tracked so the loop is visible to agents and reproducible; the Windows path is an env override, not a hard-coded assumption.
- Jagex login for the dev client depends on `--insecure-write-credentials` in the normal client (see `docs/DEV-LOOP.md`), which is a security trade-off the developer accepts only while actively developing.
