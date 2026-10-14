`DSPNETSTS` opens **Display Network Status**: the network at a glance, with gauges. It's Main Menu option 4. You type
it at a command line; it doesn't run in a program.

It shows storage (hot on the drives, source members and files included, and cold on online Tape Libraries), energy
(stored, and FE/t in and out), lanes in use, crafting (jobs active and queued, schedulers) and devices (online,
offline, faulted). It refreshes every two seconds; Enter goes
back.

{{screen DSPNETSTS}}

In a program, {{cmd RTVSTGSTS}}, {{cmd RTVPWRSTS}}, {{cmd RTVLANES}} and {{cmd RTVDEVLST}} read the same figures. See
{{page elcl/screens}}.

```elcl nocompile="a command-line command: type it at the ===> prompt"
DSPNETSTS
```
