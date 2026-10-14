`SELECT` opens a choice between several branches, closed by {{cmd ENDSELECT}}. The program tests each {{cmd WHEN}}'s
condition in order and runs the command of the first one that holds, and only that one; then it carries on after the
`ENDSELECT`. If none holds, it runs the {{cmd OTHERWISE}}, if there is one, or nothing.

Inside a `SELECT` there may be only `WHEN`s, then at most one `OTHERWISE`, and the `ENDSELECT`:

- Any other statement directly inside it is {{msg ELC0009}} ("... without matching WHEN"). Several commands for one
  branch go in a `THEN(DO)` or `CMD(DO)` group.
- A `WHEN` or a second `OTHERWISE` after the `OTHERWISE` is {{msg ELC0001}}.
- A `SELECT` left without its `ENDSELECT` is {{msg ELC0009}}.

A `SELECT` isn't a loop: a {{cmd LEAVE}} or {{cmd ITERATE}} in one of its branches acts on the loop around it. To
monitor a command in a branch, put it in a `THEN(DO)` group with the {{cmd MONMSG}} straight after it, inside the group;
don't put a `MONMSG` straight after a `WHEN` line.

```elcl
DCL VAR(&STS) TYPE(*CHAR) LEN(10)
RTVDEVSTS DEV(CMPSRV01) RTNSTS(&STS)
SELECT
  WHEN COND(&STS *EQ '*ONLINE') THEN(SNDPGMMSG MSG('CMPSRV01 is online'))
  WHEN COND(&STS *EQ '*DISABLED') THEN(DO)
    CHGDEVSTS DEV(CMPSRV01) STATUS(*ENABLE)
    MONMSG MSGID(ELC0401)
  ENDDO
  WHEN COND(&STS *EQ '*FAULT') THEN(SNDMSG MSG('CMPSRV01 has a fault') TOUSR(*SYSOPR))
  OTHERWISE CMD(SNDMSG MSG('CMPSRV01 is offline') TOUSR(*SYSOPR))
ENDSELECT
```
