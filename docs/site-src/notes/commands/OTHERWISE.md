`OTHERWISE` is the last branch of a {{cmd SELECT}}: it runs its `CMD` when none of the {{cmd WHEN}}s before it held. It
is optional; without it a `SELECT` whose conditions all fail does nothing.

- It comes after all the `WHEN`s, at most once per `SELECT`. A `WHEN` or another `OTHERWISE` after it is
  {{msg ELC0001}}; an `OTHERWISE` outside a `SELECT` is {{msg ELC0009}}.
- `CMD` holds one command, or `CMD(DO)` and a group of commands closed by {{cmd ENDDO}}.

```elcl
DCL VAR(&TYPE) TYPE(*CHAR) LEN(10)
DCL VAR(&USER) TYPE(*CHAR) LEN(16)
RTVJOBA RTNUSR(&USER) RTNTYPE(&TYPE)
SELECT
  WHEN COND(&TYPE *EQ '*INTER') THEN(SNDPGMMSG MSG('Running at a terminal'))
  OTHERWISE CMD(DO)
    SNDMSG MSG('Batch run finished') TOUSR(&USER)
    PRTTXT TEXT('Batch run finished')
  ENDDO
ENDSELECT
```
