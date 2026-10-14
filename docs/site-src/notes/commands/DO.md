`DO` opens a group of statements that run together, closed by {{cmd ENDDO}}. You nearly always write it inside another
statement's command parameter, where only one command would otherwise fit:

| Written as | Runs the group |
|---|---|
| `IF COND(...) THEN(DO)` | when the condition holds |
| `ELSE CMD(DO)` | when the `IF` before it didn't |
| `WHEN COND(...) THEN(DO)`, `OTHERWISE CMD(DO)` | for that branch of a {{cmd SELECT}} |
| `MONMSG MSGID(...) EXEC(DO)` | when the monitored message arrives |

A `DO` on a line of its own is allowed too; it just runs its statements in order.

A `DO` group isn't a loop: it runs once. A {{cmd LEAVE}} or {{cmd ITERATE}} inside one acts on the loop around it, and
a {{cmd GOTO}} from outside to a label inside the group is {{msg ELC0014}}. A `DO` left without its `ENDDO` is
{{msg ELC0009}}. Groups nest as deep as you need.

```elcl
DCL VAR(&ID)   TYPE(*CHAR) LEN(7)
DCL VAR(&TEXT) TYPE(*CHAR) LEN(132)
DCL VAR(&JOB)  TYPE(*CHAR) LEN(10)
STRCRAFT ITEM('encodedlogistics:logic_die') QTY(16) RTNCRFJOB(&JOB)
MONMSG MSGID(ELC1400) EXEC(DO)
  RCVMSG RTNMSGID(&ID) RTNMSG(&TEXT)
  SNDMSG MSG(&ID *BCAT &TEXT) TOUSR(*SYSOPR)
  RETURN
ENDDO
SNDPGMMSG MSG('Crafting job' *BCAT &JOB *BCAT 'started')
```
