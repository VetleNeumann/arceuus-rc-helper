# The version lives in `runelite-plugin.properties`; Gradle derives it

Upstream carried the version in three places (`build.gradle`, `runelite-plugin.properties`, and a `Changelog.VERSION` constant) with a comment asking humans to keep them in sync. The Plugin Hub reads `runelite-plugin.properties` as a plain file from the repository, so that file has to hold a literal; making it the single source and having `build.gradle` load it (rather than the more usual Gradle-generates-resources direction) keeps the hub path intact. The release workflow refuses a tag whose number differs from the properties file.

## Considered

- Gradle as source with `processResources` expansion into the properties file: breaks the hub, which never runs our Gradle script (`build=standard`).
- Keep the duplicates and add a consistency check: still three edits per release.
