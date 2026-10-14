Changes one rule of a Router's ACL, or moves it up or down the list. {{cmd WRKACL}}'s 2=Change, 6=Move up and 7=Move
down run it. `RTR`, `ACL` and `SEQ` pick the rule as for {{cmd RMVACLE}} ({{msg ELC2601}}, {{msg ELC2606}},
{{msg ELC2607}}).

`MATCH`, `DIR`, `ACTION`, `RATE` and `QUOTA` take the values {{cmd ADDACLE}} describes; each one left at `*SAME` keeps the
rule's current value (a `MATCH` value starting with `*` goes in quotes, `MATCH('*FLUID')`, except `*SAME`).
`RATE(*NONE)` and `QUOTA(*NONE)` take a limit off. A `MATCH` that names nothing is
{{msg ELC0103}}. Changing a rule keeps its hit count.

`MOVE(*UP)` swaps the rule with the one before it, `MOVE(*DOWN)` with the one after; the two swap their numbers, so the
rule you named has a new `SEQ` afterwards. Moving the first rule up or the last one down does nothing. The changes are
made first, then the move.

The command sends no completion message.

```elcl
CHGACLE RTR(ROUTER01) ACL(NOFUEL) SEQ(20) RATE(512)
CHGACLE RTR(ROUTER01) ACL(NOFUEL) SEQ(10) ACTION(*PERMIT) QUOTA(10000)
CHGACLE RTR(ROUTER01) ACL(NOFUEL) SEQ(20) MOVE(*UP)
```
