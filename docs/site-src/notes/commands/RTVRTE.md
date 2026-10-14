Looks a network up in a Router's route table, the table {{cmd DSPROUTE}} shows. `RTR` is the Router's device name
(`ROUTER01`); a name that isn't a Router on your network is {{msg ELC2601}}. `NETWORK` is the destination's system name,
its `SYSNAME` (`ELNET02`), as the route table lists it.

The table holds the networks the Router links to itself and, for a Router in Dynamic mode, every network it has learned a
path to from its neighbours. A Router in Static mode knows only its own links.

| Variable | Gets |
|---|---|
| `RTNNEXT` | Where the path goes first: `DIRECT` for a network the Router links to or one hop away, `IX:NAME` through an Exchange Switch, or the name of the next network on the way |
| `RTNCOST` | The path's cost: lower is better. Each hop costs more the less it carries (see {{config rack.routingBaseCost}}) |
| `RTNHOPS` | Hops to get there (`1` for a direct link) |

A network that isn't in the table (not linked, not learned, or unreachable now) is {{msg ELC2602}}. The command sends no
completion message. See {{page networking/routers}}.

```elcl
DCL VAR(&NEXT) TYPE(*CHAR) LEN(24)
DCL VAR(&HOPS) TYPE(*INT)
RTVRTE RTR(ROUTER01) NETWORK(ELNET04) RTNNEXT(&NEXT) RTNHOPS(&HOPS)
MONMSG MSGID(ELC2602) EXEC(DO)
  SNDMSG MSG('No route to ELNET04') TOUSR(*SYSOPR)
  RETURN
ENDDO
SNDPGMMSG MSG('ELNET04 via' *BCAT &NEXT *BCAT 'in' *BCAT %CHAR(&HOPS) *BCAT 'hops')
```
