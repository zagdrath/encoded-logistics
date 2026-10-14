`WRKIX` opens **Work with Exchange** on an {{item exchange_switch}}: its members, the Routers that joined it with a
{{item cross_connect}}, with each one's port, whether it's up, how it peers (`*RS` through the route server, `*BILAT`
with chosen peers), the routes it learns through the exchange and its traffic. Join requests waiting for the owner show
`*REQUEST`. The top line names the exchange, its owner, its members and ports, and how it takes members. You type it
at a command line; it doesn't run in a program.

**`IX`** is the exchange's name, or its device name on your network. One that isn't found is {{msg ELC2610}}.

| Option | What it does |
|---|---|
| 4=Remove | Removes the member ({{cmd RMVIXMBR}}), after you confirm. |
| 5=Display | The member's details. |
| 8=Approve | Approves a join request ({{cmd APVIXMBR}}). |

Removing and approving are the exchange owner's ({{msg ELC2612}} for anyone else). **F10** shows the peering sessions
instead, **F11** only the join requests; the same key again goes back. See
{{page networking/exchange-and-directory}} and {{page elcl/screens}}.

{{layout 26_wrkix}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKIX IX(CENTRAL)
```
