`WRKCRFJOB` opens **Work with Jobs**: the network's crafting jobs, active and queued, with each one's item, quantity,
status (Active, Waiting, Recall), progress bar and scheduler. It refreshes every two seconds. It's Main Menu option 2.
You type it at a command line; it doesn't run in a program. (Script jobs are on Work with Active Jobs,
{{cmd WRKACTJOB}}.)

{{screen WRKCRFJOB}}

| Option | What it does |
|---|---|
| 4=Cancel | Press Enter again, with no other option typed, to confirm; F5 or another option forgets it. Needs the Firewall's craft permission. |
| 5=Display | The job: its item and quantity, status, progress, scheduler, and what it's waiting for. |
| 8=Display steps | The job's step tree: each step's state, and a stalled step's reason. A job not planned yet has none ({{msg ELC2503}}). |

**F10** switches to the jobs that ended (newest first; the network keeps the last `CRFLOGRTN` of them, a system
value: see {{cmd WRKSYSVAL}}), with their status (Done, Failed, Cancelled), when they ended, how long they
took and who asked. There, 4=Remove deletes records (confirmed), 5=Display shows a record (what it made, why it failed,
who asked, the ingredients used) and 7=Craft again opens Craft Item with the same item and quantity. F11 sorts by ended
time, item or status, and Position to keeps the items that start with what you type. F10 again goes back.

In a program, {{cmd RTVCRFSTS}} and {{cmd ENDCRAFT}} work on jobs, and `ELSYS/CRFHIST` holds the history as records.
See {{page crafting/jobs}} and {{page elcl/screens}}.

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKCRFJOB
```
