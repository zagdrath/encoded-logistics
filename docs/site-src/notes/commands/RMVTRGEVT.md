`RMVTRGEVT` removes a trigger, so its event no longer runs the program. A job a firing already submitted carries on:
end that with {{cmd ENDJOB}} if you need to. To stop a trigger for a while without losing it, hold it with
{{cmd HLDTRGEVT}} instead.

No trigger of that name is {{msg ELC0103}} (naming `TRG`). You may remove your own triggers; removing another user's
takes a security officer ({{msg ELC0401}}, naming `*JOBCTL`), except on a network with no Firewall or at `SECLVL` 10.
Done: {{msg ELC0315}}. Work with Trigger Events option 4 runs this command after asking.

```elcl
RMVTRGEVT TRG(LOWIRON)
```
