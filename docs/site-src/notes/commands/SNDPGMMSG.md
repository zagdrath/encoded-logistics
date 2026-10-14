`SNDPGMMSG` sends a message of your own from a program: either a note for whoever is watching the job, or an escape
message that fails the program the way a failing command does. Unlike {{cmd SNDMSG}}, it doesn't go to a user's message
queue (unless it ends the program, below).

## `MSGTYPE(*INFO)` (the default)

An informational message. Where it shows:

| Running | The message goes to |
|---|---|
| A program called from a command line | the job log, and Command Entry when the program ends |
| A batch job | the job log ({{cmd DSPJOBLOG}}) |
| A [PLC](page:elcl/control-and-plcs) program | the PLC's own log |
| Typed on a command line | the message line, at once |

It is also the last message {{cmd RCVMSG}} `MSGTYPE(*LAST)` reads.

## `MSGTYPE(*ESCAPE)`

An escape message (severity 30). It acts exactly like a command failing at that point:

- A {{cmd MONMSG}} covering the `SNDPGMMSG` and naming its ID takes it, and the program goes on (or runs the `EXEC`).
  That makes it a way to jump to your own error handling.
- If nothing takes it, the program ends: the message goes to the job log and to the job user's message queue, a
  terminal shows it on the message line, and a program that called this one gets {{msg ELC0013}} at its `CALL` (not
  your ID: the caller monitors `ELC0013`). On a PLC, the PLC goes into FAULT with this message.
- Typed on a command line, it simply fails with that message.

## The message ID and text

`MSGID` is the ID the message carries, `USR0001` unless you give one. It must be three letters and four digits
({{msg ELC0103}} otherwise). Use your own prefix (`USR`, or letters of your choosing) so a `MONMSG` can tell your
messages from the system's: `USR0100` to `USR0199` can be monitored as `MONMSG MSGID(USR0100)`. With an `ELC` ID the
text shown is that system message's, with your text as its first value, which is rarely what you want.

`MSG` is the text, any `*CHAR` expression. Its trailing blanks are dropped. For your own IDs it is also the message
data that `MONMSG CMPDTA` compares and {{cmd RCVMSG}} `RTNMSGDTA` returns.

## Examples

```elcl
DCL VAR(&N) TYPE(*INT)
RTVITMCNT ITEM('minecraft:iron_ingot') RTNCOUNT(&N) NOTFND(*ZERO)
SNDPGMMSG MSG('Iron in stock:' *BCAT %CHAR(&N))
```

Raising your own error and handling it in the same program:

```elcl
PGM PARM(&ITEM)
  DCL VAR(&ITEM) TYPE(*CHAR) LEN(64)
  DCL VAR(&N)    TYPE(*INT)
  DCL VAR(&WHY)  TYPE(*CHAR) LEN(64)
  MONMSG MSGID(USR0100) EXEC(GOTO CMDLBL(FAILED))

  RTVITMCNT ITEM(&ITEM) RTNCOUNT(&N) NOTFND(*ZERO)
  IF COND(&N *EQ 0) THEN(SNDPGMMSG MSG('None left:' *BCAT &ITEM) MSGID(USR0101) MSGTYPE(*ESCAPE))
  MOVITM ITEM(&ITEM) QTY(&N) TODEV(EGRESS01)
  RETURN

FAILED:
  RCVMSG RTNMSGDTA(&WHY)
  SNDMSG MSG(&WHY) TOUSR(*SYSOPR)
ENDPGM
```
