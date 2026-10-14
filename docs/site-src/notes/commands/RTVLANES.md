Reports how many of the network's lanes are in use, as the Network screen and {{cmd DSPNETSTS}} show them.

| Variable | Gets |
|---|---|
| `RTNUSED` | Lanes the network's devices use now |
| `RTNTOTAL` | The lanes the network has in all |

A device that doesn't get a lane is offline; see {{page networks/lanes}} for what uses lanes and
how to get more. The command sends no completion message.

```elcl
DCL VAR(&USED) TYPE(*INT)
DCL VAR(&TOTAL) TYPE(*INT)
RTVLANES RTNUSED(&USED) RTNTOTAL(&TOTAL)
IF COND(&TOTAL - &USED *LT 4) THEN(SNDMSG MSG('Fewer than 4 lanes free') TOUSR(*SYSOPR))
```
