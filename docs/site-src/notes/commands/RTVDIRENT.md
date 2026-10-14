Looks up a qualified device name, `SYSNAME/DEVICE`, the way any command that's given one does: `ELNET02/UPS01` is the
device `UPS01` on the network whose system name is `ELNET02`. Use it to check that a name works, and from where, before
your program relies on it. See {{page networking/exchange-and-directory}}.

A name on your own network (your own `SYSNAME` before the slash) resolves to that device directly, with `0` hops. For
another network, every one of these has to hold, or the lookup fails:

1. One of your network's Routers has a route to that network ({{msg ELC2603}} otherwise).
2. The device is published by a Directory Server on that network ({{cmd CHGDIRENT}}, or {{cmd WRKDIR}}'s 2=Publish), and
   it's there ({{msg ELC2603}} otherwise: an unpublished device looks just like a missing one).
3. A Router on your network has a Control route to that network that's reachable now, and the ACL on that route, if it
   has one, lets the control request out (`*CONTROL` and `*ANY` rules; see {{cmd ADDACLE}}). Otherwise
   {{msg ELC2604}}.
4. You have the Firewall's view permission on that network ({{msg ELC2604}} otherwise). A command that changes something
   checks its own permission there instead: {{cmd CHGRSOUT}} on `ELNET02/CTLIF01` needs build.

A name without a slash is {{msg ELC2603}}.

| Variable | Gets |
|---|---|
| `RTNTYPE` | The device's type code (`UPS`, `CTLIF`...) |
| `RTNNETWORK` | The system name of its network |
| `RTNHOPS` | Router hops to that network (`0` on your own) |

A remote answer is kept for {{config rack.directoryCacheSeconds default}} seconds (less if routes or networks change).
An answer from that cache sends the warning {{msg ELC2608}}; the publishing, the control route and your permission are
checked again even then. Every lookup goes into your Directory Servers' list of recent lookups, and a successful one
counts towards the device's lookups on the other side. There's no other completion message.

```elcl
DCL VAR(&TYPE) TYPE(*CHAR) LEN(10)
DCL VAR(&HOPS) TYPE(*INT)
RTVDIRENT NAME('ELNET02/CTLIF01') RTNTYPE(&TYPE) RTNHOPS(&HOPS)
MONMSG MSGID(ELC2603 ELC2604) EXEC(RETURN)
CHGRSOUT DEV(ELNET02/CTLIF01) SIDE(*UP) LVL(15)
```
