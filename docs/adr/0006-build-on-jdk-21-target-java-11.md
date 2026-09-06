# Build on JDK 21, target Java 11

The plugin must stay Java 11 bytecode: RuneLite ships a Java 11 runtime to players and the Plugin Hub builds with JDK 11. The build tooling we want, however, has moved on. Error Prone requires JDK 21 to run since 2.43 (the last JDK 11 release, 2.31, is from 2024) and Spotless requires a JDK 17+ Gradle JVM since 8.0. Pinning both to their last JDK 11 versions would mean a two-year-old check set with no upgrade path and Dependabot ignores to keep it there.

So Gradle runs on JDK 21 (CI installs it, `build.gradle` declares a JDK 21 toolchain and refuses to configure on anything older than 17) while every `JavaCompile` task keeps `options.release = 11`. Both Error Prone and Spotless document this cross-compilation as the supported way to build older targets. The Plugin Hub is unaffected: `runelite-plugin.properties` uses `build=standard`, so the hub builds with its own script and ignores the extra Gradle plugins.

## Consequences

- Developers need a JDK 21 installed to build. JDK 11 is no longer required locally; the dev loop runs the shadow jar on RuneLite's own bundled Java 11 JRE (see ADR 0001), which is also the closest match to what players run.
- `./gradlew run` launches the dev client on the toolchain JDK 21. That is fine for RuneLite but is not the player runtime; the shadow jar loop remains the documented one.
- The `--release 11` flag, not the compiler JDK, is what keeps the API surface at Java 11. A newer API slipping in fails at compile time, not on a player's machine.
- Bumping the toolchain again later is a one-line change plus a CI edit; nothing in the plugin depends on the build JDK.
