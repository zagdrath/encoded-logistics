`SIGNOFF` ends your session on the terminal. It's Main Menu option 90, and you type it at a command line (it doesn't
run in a program). What it does depends on whether the network asks people to sign on:

- **With a Firewall on the network and the system value `SECLVL` at `30`** (the default), the session signs off on the
  server: Command Entry's history and the commands F9 would bring back are cleared, the current library goes back to
  `ELGPL`, and the **Sign On** screen shows for the next user, with a message that you signed off. Nothing on the
  terminal works until someone signs on again.
- **Otherwise** (no Firewall, or `SECLVL` `10`), there's no sign-on to go back to: the terminal simply closes, as F3
  does.

See
[using the terminal](page:elcl/using-the-terminal) and {{page racks/security}} for the Firewall.

{{layout 01_signon}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
SIGNOFF
```
