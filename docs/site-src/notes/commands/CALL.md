`CALL` runs a compiled program. It works in two places, and behaves a little differently in each.

## In a program

The called program runs in the same job, as the same user, and the calling program waits until it ends, then carries
on with the statement after the `CALL`. More on [calling programs](page:elcl/language#calling).

- `PGM` is `LIB/NAME`, or a name alone (or `*LIBL/NAME`) to look down the job user's library list. It can be a `*CHAR`
  variable holding the name. A program that isn't there is {{msg ELC0203}} (or {{msg ELC0201}} for a library that
  doesn't exist), raised when the `CALL` runs: the compiler
  doesn't look programs up, so a program can be compiled before the ones it calls exist.
- `PARM` passes up to 255 values, matched in order to the called program's {{cmd PGM}} `PARM` list. A different number
  is {{msg ELC0012}}. Each value is converted to the type the called program declares ({{msg ELC0003}} if it can't be).
- A variable passed on its own, `PARM(&COUNT)`, goes **by reference**: when the called program ends normally, the value
  its parameter ends with is copied back into `&COUNT`. A literal or an expression goes by value. To pass a variable's
  value without letting it change, put it in brackets: `PARM((&COUNT))`.
- If the called program ends on an escape message nothing caught, the caller gets {{msg ELC0013}} ("Called program
  LIB/NAME ended abnormally") at its `CALL`, which it can monitor; the original message is in the job log. Nothing is
  copied back then.
- Programs can call each other, and themselves, up to 16 deep, counting the job's own program; one more is
  {{msg ELC0011}}.
- `CALL` sends no message of its own when the called program ends normally.

On a [PLC](page:elcl/control-and-plcs) cabled to a network, `CALL` looks programs up with the authority and library list
of the player who last loaded the PLC's program. A PLC with no network can't call programs ({{msg ELC1502}}).

## On a command line

`CALL PGM(LIB/NAME) PARM('IRON' 500)` typed at the `===>` prompt runs the program in your session's interactive job,
over as many ticks as it needs. The command line answers at once with {{msg ELC0108}} and you can go on using the
terminal. When the program ends, the messages it sent appear on Command Entry and the message line shows how it ended:
{{msg ELC0110}}, or the escape message that ended it.

- There are no variables on a command line: every `PARM` value is passed as text and converted to the type the
  program declares.
- Your interactive job runs one program at a time: a second `CALL` while one is running is {{msg ELC0109}}. End the
  running one with {{cmd ENDJOB}} if it is stuck.
- To run a program without tying up your session, submit it with `SBMJOB CMD(CALL PGM(...))` instead
  (see {{page elcl/jobs-and-scheduling}}).

```elcl
DCL VAR(&COUNT) TYPE(*INT)
DCL VAR(&PGM)   TYPE(*CHAR) LEN(21) VALUE('ELGPL/COUNTER')
CALL PGM(ELGPL/RESTOCK) PARM('minecraft:iron_ingot' 500)
MONMSG MSGID(ELC0013 ELC0203) EXEC(SNDMSG MSG('RESTOCK failed') TOUSR(*SYSOPR))
CALL PGM(&PGM) PARM(&COUNT)
SNDPGMMSG MSG('COUNTER set the count to' *BCAT %CHAR(&COUNT))
```
