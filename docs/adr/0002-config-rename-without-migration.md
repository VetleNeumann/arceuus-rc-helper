# Config group and keys renamed without a migration

When the fork was renamed from Jam's Arceuus Runecrafting to Arceuus RC Helper, the config group changed from `zeah-rc-helper` to `arceuus-rc-helper` and the `lanternReminder` key became `gearReminder`, with no migration. The hub rule (`docs/RUNELITE-RULES.md`) says renames need migrations because they reset users' settings; it was deliberately skipped because the fork is unpublished and has exactly one user, whose settings were re-entered by hand. The previous `PathDisplayMigration` was deleted for the same reason: with a fresh group there is nothing for it to read.

If this plugin is ever published to the Plugin Hub, the rule applies in full from that release onward.
