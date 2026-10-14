`RLSTRGEVT` releases a trigger held with {{cmd HLDTRGEVT}}: its status goes back to `*ACTIVE` and it fires on its
event again. Events that happened while it was held don't fire now. A count, storage or power condition that started
to hold while the trigger was held, and still holds, fires at the next look (within half a second), because the
trigger never fired for that crossing.

No trigger of that name is {{msg ELC0103}} (naming `TRG`). You may release your own triggers; releasing another user's
takes a security officer ({{msg ELC0401}}, naming `*JOBCTL`), except on a network with no Firewall or at `SECLVL` 10.

`RLSTRGEVT` sends no completion message when it works: the trigger's status on {{cmd WRKTRGEVT}} shows it.

```elcl
RLSTRGEVT TRG(LOWIRON)
```
