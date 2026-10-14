Reports on one of a Router's neighbours: another Dynamic-mode Router it exchanges routes with, over a link or at an
Exchange Switch. The neighbours are the ones {{cmd DSPNBR}} lists. `RTR` is the Router's device name ({{msg ELC2601}}
when it isn't a Router on your network); `NBR` is the neighbour's network name (its `SYSNAME`), as {{cmd DSPNBR}} shows
it. A network that isn't one of the Router's neighbours is {{msg ELC2605}}.

| Variable | Gets |
|---|---|
| `RTNSTS` | `*FULL` (exchanging routes), `*INIT` (seen, not yet exchanging) or `*DOWN` |
| `RTNCOST` | The cost of the hop to it: the override set with {{cmd CHGNBR}}, or the one worked out from the link |
| `RTNUPTIME` | Seconds it has been `*FULL`; `0` in any other state |

The command sends no completion message. See {{page networking/routers}}.

```elcl
DCL VAR(&STS) TYPE(*CHAR) LEN(6)
RTVNBRSTS RTR(ROUTER01) NBR(ELNET03) RTNSTS(&STS)
IF COND(&STS *EQ '*DOWN') THEN(SNDMSG MSG('Lost ELNET03') TOUSR(*SYSOPR))
```
