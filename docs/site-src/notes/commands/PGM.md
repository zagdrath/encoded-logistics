`PGM` is the first statement of every program. It marks where the program starts, and its `PARM` list names the
variables that receive the values a caller passes, in order. A program with no parameters is a bare `PGM`.

- Every variable in `PARM` must be declared with {{cmd DCL}} like any other ({{msg ELC0002}} otherwise). Any type
  works, `*LIST` too. Up to 255 parameters.
- The caller must pass exactly as many values as `PARM` names: a different number is {{msg ELC0012}}, raised at the
  caller's {{cmd CALL}} (or, for a program started from a command line or a batch job, when it starts).
- Each value is converted to the parameter's declared type as the program starts, so text that isn't a number going
  into an `*INT` parameter is {{msg ELC0003}}. A `*CHAR` parameter is padded or cut to its length.
- A variable the caller passes comes back changed: when this program ends normally, each parameter's value is copied
  into the caller's variable. See {{cmd CALL}}.

The source member must start with `PGM` (anything else first is {{msg ELC0009}}, "... without matching PGM"), it may
have only one (a second is {{msg ELC0001}}), and it ends with {{cmd ENDPGM}}. The order of everything in between is on
[the language page](page:elcl/language#structure).

A program a trigger starts gets two parameters: the event name and its data. Declare the event `LEN(12)` so the longest
names arrive whole.

```elcl
PGM PARM(&EVENT &DATA)
  DCL VAR(&EVENT) TYPE(*CHAR) LEN(12)
  DCL VAR(&DATA)  TYPE(*CHAR) LEN(256)
  SNDMSG MSG('Event' *BCAT &EVENT *BCAT &DATA) TOUSR(*SYSOPR)
ENDPGM
```
