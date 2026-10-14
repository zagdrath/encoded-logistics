Reports on the network's power: where it comes from, the UPS charge, the load and the energy stored. Any of the
variables can be left out. See {{page networks/power}}.

| Variable | Gets |
|---|---|
| `RTNSRC` | `*UPS` when any online {{item ups}} is running on its battery; otherwise `*NETWORK` when the network is up (online, or switching over to a standby controller); `*NONE` when it isn't |
| `RTNCHG` | The online UPSes' average charge, in percent; `0` with no UPS |
| `RTNLOAD` | What the network uses, in FE a tick, rounded |
| `RTNSTORED` | Energy in the network's whole energy pool, in FE |
| `RTNCAP` | The pool's capacity, in FE |
| `RTNDRVSTO` | The part of the pool held in Energy Storage Drives, in FE |
| `RTNDRVCAP` | Those drives' capacity, in FE |

These are the figures the Network screen shows. The command sends no completion message. A program can also start the
moment the power changes, through the `*PWRUPS` trigger event (see {{page elcl/jobs-and-scheduling}}).

```elcl
DCL VAR(&SRC) TYPE(*CHAR) LEN(8)
DCL VAR(&CHG) TYPE(*DEC) LEN(5 1)
RTVPWRSTS RTNSRC(&SRC) RTNCHG(&CHG)
IF COND(&SRC *EQ '*UPS' *AND &CHG *LT 25) THEN(DO)
  SNDMSG MSG('UPS below 25%, shutting the deployers down') TOUSR(*ALL)
  CHGDEVSTS DEV(DEPLOYER01) STATUS(*DISABLE)
ENDDO
```
