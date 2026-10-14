`WRKACL` opens **Work with ACLs** on one of a {{item router}}'s access control lists: its rules in the order they're
looked at, each with its sequence number, what it matches, its direction, permit or deny, its rate or quota, how many
times it matched (hits) and when it last did. The default (`*DFT`) comes last. You type it at a command line; it
doesn't run in a program.

How the rules work: the first rule that matches a resource (an item, a `#tag`, `*ITEM`, `*FLUID`, `*PRES`, `*ENERGY`
or `*ANY`) going its way (`*IN` to the Router's network, `*OUT` from it, `*BOTH`) permits or denies it, up to its rate
(a minute) or quota (a game day); past those it denies until the window turns. What no rule matches gets the default,
set by {{config rack.aclDefaultPermit default}}.

- **`RTR`** is the Router's device name: {{msg ELC2601}} if it isn't a Router on your network. **`ACL`** is the list's
  name on that Router: {{msg ELC2606}} if it has none of that name.

| Option | What it does |
|---|---|
| 2=Change | Prompts {{cmd CHGACLE}} with the rule's values. |
| 4=Remove | Removes the rule ({{cmd RMVACLE}}), after you confirm. |
| 6=Move up, 7=Move down | Moves the rule one place ({{cmd CHGACLE}} `MOVE(*UP)` or `MOVE(*DOWN)`). |

**F6** adds a rule ({{cmd ADDACLE}}). Changing rules needs the Firewall's build permission, as those commands do. See
{{page networking/routers}} and {{page elcl/screens}}.

{{layout 25_wrkacl}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKACL RTR(RTR01) ACL(INBOUND)
```
