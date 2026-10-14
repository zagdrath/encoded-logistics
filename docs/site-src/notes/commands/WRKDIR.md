`WRKDIR` opens **Work with Directory** on a {{item directory_server}}: its network's devices by their qualified names
(`SYSNAME/DEVICE`), each with its type, whether it's `*PUBLISHED` or `*PRIVATE`, and how many lookups it had in the
last hour. Under them come the latest lookups made from this network, and the top line names the server, its network,
how many names are published and cached, and how long a lookup is cached. You type it at a command line; it doesn't
run in a program.

- **`DIR(*FIRST)`**, the default, uses the first Directory Server on your network. Name one (its device name) when
  there are several. With none on the network, or none of that name, it's {{msg ELC2616}}.
- A published device can be reached from other networks: any command that takes a device takes its qualified name,
  when a Router on the caller's network has a Control policy to this network and the caller has the Firewall's
  permission here.

| Option | What it does |
|---|---|
| 2=Publish | Runs {{cmd CHGDIRENT}} `PUBLISH(*YES)` for the device. |
| 3=Unpublish | Runs {{cmd CHGDIRENT}} `PUBLISH(*NO)`. |
| 5=Display | Looks the name up as {{cmd RTVDIRENT}} does; the answer shows on the message line. |

Publishing needs the Firewall's build permission, as {{cmd CHGDIRENT}} does. **F10** shows only the lookups, a page of
them; F10 again goes back. See {{page networking/exchange-and-directory}} and {{page elcl/screens}}.

{{layout 27_wrkdir}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKDIR
WRKDIR DIR(DIRSVR01)
```
