`RCVMSG` reads a message the program has had, into variables, so you can report it or decide what to do about it. It
is most useful inside a {{cmd MONMSG}}'s `EXEC(DO)` group, right after the escape message was caught. Reading a message
doesn't remove it: `RCVMSG` again gives the same one until a newer one comes.

`MSGTYPE` picks which message:

| `MSGTYPE` | Reads |
|---|---|
| `*EXCP` (the default) | the last escape message a `MONMSG` in this program caught |
| `*LAST` | the last message of any kind in this program: a caught escape message, a command's completion message, or a {{cmd SNDPGMMSG}} `*INFO` message |

Each program keeps its own: a called program's messages aren't seen by its caller, which gets {{msg ELC0013}} at its
`CALL` instead.

| Parameter | Receives | With no message |
|---|---|---|
| `RTNMSGID` | the message ID, such as `ELC1201` or `USR0001` (declare it `LEN(7)`) | blanks |
| `RTNMSG` | the full message text, with its values filled in: "Item minecraft:dirt not found in network." | blanks |
| `RTNMSGDTA` | just the values in it, joined by blanks: `minecraft:dirt`. For a `SNDPGMMSG` message, its text. This is what `MONMSG CMPDTA` compares. | blanks |

All three are optional and take `*CHAR` variables; text longer than the variable is cut. `RCVMSG` sends no message
itself.

```elcl
DCL VAR(&ID)   TYPE(*CHAR) LEN(7)
DCL VAR(&TEXT) TYPE(*CHAR) LEN(132)
DCL VAR(&DTA)  TYPE(*CHAR) LEN(64)
DCL VAR(&N)    TYPE(*INT)
RTVITMCNT ITEM('minecraft:netherite_ingot') RTNCOUNT(&N)
MONMSG MSGID(ELC1200) EXEC(DO)
  RCVMSG MSGTYPE(*EXCP) RTNMSGID(&ID) RTNMSG(&TEXT) RTNMSGDTA(&DTA)
  PRTTXT TEXT(&ID *BCAT &TEXT)
  IF COND(&ID *EQ 'ELC1201') THEN(SNDMSG MSG('Nobody has made any' *BCAT &DTA) TOUSR(*SYSOPR))
ENDDO
```
