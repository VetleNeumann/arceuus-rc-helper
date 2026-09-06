# Release notes live in `CHANGELOG.md`, not in game chat

Upstream announced update notes in the game chat on first login after an upgrade, backed by a `Changelog` class, a `ChangelogService` and a hidden `seenChangelogVersion` config item. That is a good feature for hub users who never visit GitHub, but for an unpublished single-user fork it is 200 lines that duplicate the version and the notes. The fork keeps a Keep-a-Changelog `CHANGELOG.md` as the single source; the release workflow copies the matching section into the GitHub Release body.

If the plugin is published to the hub, re-add an in-game announcement that reads its notes from a resource generated out of `CHANGELOG.md`, so the notes still have one source.
