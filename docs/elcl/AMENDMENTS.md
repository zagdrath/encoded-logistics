# ELCL spec amendments (from the Control Interface / Terminal OS screens handoff)

1. **Screen id**: the existing Terminal Desk crafting-jobs screen (JobsPanel, Main Menu option 2) changes its id and
   command from `WRKJOB` to `WRKCRFJOB`. `WRKJOB JOB()` is ELCL's Work with Job (OS.md 8, screen 9). The existing
   screen's layout, title and menu option are unchanged; `show jobs` still maps to `WRKACTJOB` (COMMANDS.md 10).
2. **Completion messages** (MESSAGES.md, severity 00): ELC0210-ELC0222 (libraries, members, programs, save/restore,
   system values) and ELC0307-ELC0309, ELC0311-ELC0315 (jobs, schedule entries, triggers). Every successful OS command
   returns its completion message; Command Entry and job logs show it with its ID.
3. **Offline Control Interface** (COMMANDS.md 7): `RTVRSIN` and `CHGRSOUT` raise `ELC1302` (device offline) when the
   Control Interface is offline. `CHGRSOUT` does not store the level in that case.
