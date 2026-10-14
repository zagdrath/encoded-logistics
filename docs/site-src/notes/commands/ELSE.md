`ELSE` runs its command when the {{cmd IF}} just before it didn't. It must come straight after that `IF`, or straight
after the `ENDDO` of the `IF`'s `THEN(DO)` group; anywhere else it is {{msg ELC0009}} ("ELSE without matching IF").
A {{cmd MONMSG}} between the `IF` and the `ELSE` counts as something in between.

- `CMD` holds one command, or `CMD(DO)` and a group of commands closed by {{cmd ENDDO}}. It is optional (an `ELSE` with
  no command does nothing).
- `CMD` can hold another `IF`, so an `ELSE` after that one belongs to the inner `IF`: that is how you chain
  "else if" tests. For more than two or three, {{cmd SELECT}} reads better.

```elcl
DCL VAR(&PCT) TYPE(*DEC) LEN(5 1)
RTVSTGSTS RTNPCT(&PCT)
IF COND(&PCT *GE 95) THEN(DO)
  SNDMSG MSG('Storage critical') TOUSR(*ALL)
  CHGRSOUT DEV(CTLIF01) SIDE(*ALL) LVL(15)
ENDDO
ELSE CMD(IF COND(&PCT *GE 80) THEN(SNDMSG MSG('Storage high') TOUSR(*SYSOPR)))
ELSE CMD(SNDPGMMSG MSG('Storage OK'))
```
