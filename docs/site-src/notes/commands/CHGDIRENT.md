Publishes a device of your network on its Directory Servers, so other networks can reach it by its qualified name
(`ELNET01/CTLIF01`), or stops publishing it. {{cmd WRKDIR}}'s 2=Publish and 3=Unpublish do the same. Only published
devices can be found from other networks; see {{cmd RTVDIRENT}} for everything else a remote lookup needs.

`DEV` is a device on your network ({{msg ELC1301}} when there's none by that name). The change goes to every
{{item directory_server}} (block or rack) on the network; with none, the command fails with {{msg ELC2616}}. On success
it sends {{msg ELC2617}} (published) or {{msg ELC2618}} (no longer published).

A device is published by its name: after {{cmd RNMDEV}}, publish the new name and unpublish the old one. See
{{page networking/exchange-and-directory}}.

```elcl
CHGDIRENT DEV(CTLIF01) PUBLISH(*YES)
CHGDIRENT DEV(UPS01) PUBLISH(*NO)
```
