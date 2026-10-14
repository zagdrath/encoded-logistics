`GOTO` sends the program to the statement with the label `CMDLBL` and carries on from there.

A label is a name of up to 10 characters followed straight away by a colon, in front of a statement (`RETRY: RTVITMCNT
...`) or alone on the line before it. One statement can have several labels. Two labels with the same name are
{{msg ELC0001}}, and so is a label longer than 10 characters.

- A label that doesn't exist is {{msg ELC0010}}.
- You can jump out of a block (a loop, a `DO` group, a `SELECT`) to a label outside it, but not into one from outside:
  that is {{msg ELC0014}}. The same goes for subroutines: a `GOTO` in the main body can't reach a label inside a
  `SUBR`.
- A label on {{cmd ENDPGM}} marks the end of the main body, so `GOTO` to it ends the program. Inside a subroutine, a
  label on its {{cmd ENDSUBR}} lets a `GOTO` leave the subroutine early (`RETURN` would end the whole program).
- To leave or restart a loop, {{cmd LEAVE}} and {{cmd ITERATE}} say more clearly what you mean.

`GOTO` is most often the `EXEC` of a {{cmd MONMSG}}, sending the program to its error handling:

```elcl
DCL VAR(&N)     TYPE(*INT)
DCL VAR(&TRIES) TYPE(*INT)
MONMSG MSGID(ELC1302) EXEC(GOTO CMDLBL(OFFLINE))

RETRY: CHGVAR VAR(&TRIES) VALUE(&TRIES + 1)
MOVITM ITEM('minecraft:iron_ingot') QTY(64) TODEV(EGRESS01) RTNMOVED(&N)
IF COND(&N *EQ 0 *AND &TRIES *LT 3) THEN(DO)
  DLYJOB DLY(5)
  GOTO CMDLBL(RETRY)
ENDDO
RETURN

OFFLINE:
  SNDMSG MSG('EGRESS01 is offline') TOUSR(*SYSOPR)
```
