Approves a network's request to join an {{item exchange_switch}}, as {{cmd WRKIX}}'s 8=Approve does. Only the
exchange's owner can approve: the player who placed it, or (at a terminal) a server operator; in a batch job, the
job's user has to be the owner. Anyone else gets {{msg ELC2612}}, which names the owner. The command also needs the
Firewall's build permission on your own network, like every command that changes something. See
{{page networking/exchange-and-directory}}.

- **`IX`**: the exchange, by its device name on your network (`XSW01`) or by the exchange's own name, which finds it
  wherever it is. {{msg ELC2610}} when neither matches.
- **`NETWORK`**: the requesting network's system name, as {{cmd WRKIX}} lists it. A network that has neither asked nor
  joined is {{msg ELC2611}}.

When there's no request from that network (it's already a member), or the exchange has no free port for it, the command
fails with {{msg ELC2613}}. On success the network gets a port and the command sends {{msg ELC2614}}.

```elcl
APVIXMBR IX(XSW01) NETWORK(ELNET07)
MONMSG MSGID(ELC2613) EXEC(SNDPGMMSG MSG('No free port, or no request'))
```
