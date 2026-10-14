`RTVSYSVAL` puts a system value's current setting (its default, if nobody has changed it) into a `*CHAR` variable, as
text: `QMAXJOB` gives `16`, `PHOSPHOR` gives `*GREEN`. The longest values are eight characters, so `LEN(10)` is
enough. Use `%INT` to turn a number into an `*INT`.

`SYSVAL` is one of `SYSNAME`, `DATFMT`, `SECLVL`, `QMAXJOB`, `LOGRTN`, `CRFLOGRTN` and `PHOSPHOR` (see
{{cmd WRKSYSVAL}} for what each means); any other name is {{msg ELC0103}}. Anyone may read them.

```elcl
DCL VAR(&NAME) TYPE(*CHAR) LEN(10)
DCL VAR(&MAX)  TYPE(*CHAR) LEN(10)
DCL VAR(&JOBS) TYPE(*INT)
RTVSYSVAL SYSVAL(SYSNAME) RTNVAR(&NAME)
RTVSYSVAL SYSVAL(QMAXJOB) RTNVAR(&MAX)
CHGVAR VAR(&JOBS) VALUE(%INT(&MAX))
SNDPGMMSG MSG(&NAME *BCAT 'runs up to' *BCAT %CHAR(&JOBS) *BCAT 'batch jobs')
```

To change a value, use {{cmd CHGSYSVAL}}.
