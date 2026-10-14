Adds a rule to one of a Router's ACLs (access control lists): named, ordered rule lists that decide what may pass the
Router. An ACL only has an effect once it's applied, in the Router's panel (ACLs tab), to a route or to a network the
Router reaches directly. See {{page networking/routers}}. {{cmd WRKACL}} shows an ACL's rules with their hit counts.

## The ACL

`RTR` is the Router's device name ({{msg ELC2601}} when it isn't a Router on your network). `ACL` names the list; a name
the Router doesn't have yet **creates** the ACL. A Router holds up to 8 ACLs, and a name is a letter followed by up to 9
letters or digits. A new name the Router can't take (no room, or not a valid name) is {{msg ELC2606}}. The new ACL is
made before the rule is checked, so it stays (empty) even when the rule is then refused.

## The rule

- **`MATCH`**: what the rule covers.

  | Value | Matches |
  |---|---|
  | An item ID (`COAL`, `'minecraft:coal'`) | That item |
  | `'#c:ingots'` | Items in that tag |
  | `'FLUID minecraft:water'`, `'PRES arcforge:hydrogen'` | That fluid or gas |
  | `'*ITEM'`, `'*FLUID'`, `'*PRES'`, `'*ENERGY'` | Everything of that type |
  | `'*ANY'` | Everything, control requests included |
  | `'*CONTROL'` | Control requests only: using a device on the far side by its `SYSNAME/DEVICE` name (see {{cmd RTVDIRENT}}) |

  Put the `*` values in quotes, as shown: `MATCH` is text, and the compiler (or the command line) refuses them bare with
  {{msg ELC0103}}. A value that names nothing is {{msg ELC0103}} too.
- **`DIR`**: `*IN` (into the Router's own network), `*OUT` (out of it) or `*BOTH`. A control request goes out, so it's
  decided by rules covering `*OUT`.
- **`ACTION`**: `*PERMIT` or `*DENY`.
- **`RATE`** and **`QUOTA`**: limit a `*PERMIT` rule to so many a minute (1,200 ticks) or a game day (24,000 ticks),
  counted in items, mB or FE. Past the limit the rule denies until the window turns. `*NONE` is no limit.
- **`SEQ`**: the rule's number, which sets its place. `*LAST` (the default) puts it after the last rule, numbered on in
  tens (`10`, `20`...). A number another rule already has is {{msg ELC0103}}, and so is any new rule once the ACL has its
  16.

The first rule, in number order, that matches what's passing decides; when none does, {{config rack.aclDefaultPermit}}
does. A denial can start a program through the `*ACLDENY` trigger event. The command sends no completion message.

```elcl
ADDACLE RTR(ROUTER01) ACL(NOFUEL) MATCH(COAL) DIR(*OUT) ACTION(*DENY)
ADDACLE RTR(ROUTER01) ACL(NOFUEL) MATCH('#c:ingots') DIR(*OUT) ACTION(*PERMIT) +
        RATE(256)
ADDACLE RTR(ROUTER01) ACL(NOFUEL) SEQ(5) MATCH('*CONTROL') DIR(*OUT) ACTION(*DENY)
```

{{cmd CHGACLE}} changes or moves a rule, {{cmd RMVACLE}} removes one.
