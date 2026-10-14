Removes one rule from a Router's ACL, as {{cmd WRKACL}}'s 4=Remove does. `RTR` is the Router's device name
({{msg ELC2601}} when it isn't a Router on your network), `ACL` the list's name ({{msg ELC2606}} when the Router has no
such ACL) and `SEQ` the rule's number as {{cmd WRKACL}} shows it ({{msg ELC2607}} when there's no rule with that number).

The other rules keep their numbers. Removing the last rule leaves the ACL empty, still applied wherever it was: with no
rules, everything goes by the default, {{config rack.aclDefaultPermit}}. The command doesn't delete the ACL itself (the
Router's panel does) and sends no completion message.

```elcl
RMVACLE RTR(ROUTER01) ACL(NOFUEL) SEQ(10)
MONMSG MSGID(ELC2607)
```
