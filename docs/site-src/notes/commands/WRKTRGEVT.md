Work with Trigger Events lists the system's triggers: name, event, the item or device it watches, its value, the
program it runs and its status (`*ACTIVE` or `*HELD`). It's also option 12 on the main menu. Type an option next to
a trigger:

| Option | What it does |
|---|---|
| `2` | Change it: {{cmd ADDTRGEVT}}'s prompter with its values. Enter removes the trigger and adds it again as you changed it, so it comes back `*ACTIVE` and takes a fresh first look at its condition. |
| `3` | Hold it ({{cmd HLDTRGEVT}}); on a held trigger, release it ({{cmd RLSTRGEVT}}). |
| `4` | Remove it, after you confirm ({{cmd RMVTRGEVT}}). |

F6 prompts {{cmd ADDTRGEVT}} to add a trigger. Hold and release send no message: the status column shows the change.

{{layout 11_wrktrgevt}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKTRGEVT
```

The 24 events, what each passes in `&DATA` and how often a trigger may fire are on {{cmd ADDTRGEVT}}; see also
{{page elcl/jobs-and-scheduling}}.
