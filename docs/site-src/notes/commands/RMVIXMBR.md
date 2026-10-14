Removes a member network from an {{item exchange_switch}}, or turns down a request to join, as {{cmd WRKIX}}'s
4=Remove does. Only the exchange's owner can do it from a command (who counts as the owner is as for
{{cmd APVIXMBR}}; anyone else gets {{msg ELC2612}}); a member can also leave from its own side, in the panel. `IX` and
`NETWORK` are as {{cmd APVIXMBR}} takes them ({{msg ELC2610}}, {{msg ELC2611}}).

The member's port is freed and its sessions with the other members end. On success the command sends {{msg ELC2615}}. See {{page networking/exchange-and-directory}}.

```elcl
RMVIXMBR IX(XSW01) NETWORK(ELNET07)
```
