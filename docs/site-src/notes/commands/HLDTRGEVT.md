`HLDTRGEVT` holds a trigger: its status becomes `*HELD` and it doesn't fire until you release it with
{{cmd RLSTRGEVT}}. Events that happen while it's held are dropped, not saved up, and a firing that was waiting out
the one-second gap between firings is dropped too. A job a firing already submitted isn't affected (hold that with
{{cmd HLDJOB}}).

No trigger of that name is {{msg ELC0103}} (naming `TRG`). You may hold your own triggers; holding another user's
takes a security officer ({{msg ELC0401}}, naming `*JOBCTL`), except on a network with no Firewall or at `SECLVL` 10.

`HLDTRGEVT` sends no completion message when it works: the trigger's status on {{cmd WRKTRGEVT}} shows it (option 3
there holds a trigger, and releases a held one).

```elcl
HLDTRGEVT TRG(LOWIRON)
```
