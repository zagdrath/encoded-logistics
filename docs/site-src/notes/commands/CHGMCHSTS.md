Switches an Arcforge machine off or on through Arcforge's remote switch. A machine switched off stops as a redstone stop
would, keeps what's in it, and shows `*DISABLED` in {{cmd RTVMCHSTS}} and Work with Machines. `*ENABLE` lets it run
again.

`MCH` names the machine as for {{cmd RTVMCHSTS}} ({{msg ELC1301}}, {{msg ELC1318}}). A machine that's gone or not
loaded is {{msg ELC1302}}; an unformed multiblock is {{msg ELC1319}}; a machine without the switch is {{msg ELC1320}};
one that refuses is {{msg ELC1321}}. Switching it to the state it's already in is fine.

The command sends no completion message, even at the command line. See {{page integrations/arcforge}}.

```elcl
DCL VAR(&MCHS) TYPE(*LIST)
DCL VAR(&MCH) TYPE(*CHAR) LEN(10)
RTVMCHLST TYPE(ARCCRU) RTNLST(&MCHS)
FOREACH VAR(&MCH) IN(&MCHS)
  CHGMCHSTS MCH(&MCH) STATUS(*DISABLE)
ENDFOR
```
