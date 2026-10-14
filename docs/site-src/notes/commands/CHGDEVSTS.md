Switches a cable part off or back on. Only the parts on cables can be switched (ports, Inventory Taps, Threshold Sensors,
planes, Point-to-Point Links, terminals and the rest of {{page networks/cable-parts}}, wireless ports included); any other
device is {{msg ELC1303}}, and a name no device has is {{msg ELC1301}}.

A disabled part behaves as if it were offline: it moves nothing, and an Inventory Tap's inventory drops out of the
network's storage until it's enabled again. {{cmd RTVDEVSTS}} and Work with Devices show it as `*DISABLED`. The setting
stays with the part. Enabling a part that's already enabled (or the reverse) does nothing.

The command sends no completion message, even at the command line. Disabling a part that a program then names in
{{cmd MOVITM}} or {{cmd IMPITM}} makes those fail with {{msg ELC1302}}.

```elcl
CHGDEVSTS DEV(COLLECTOR01) STATUS(*DISABLE)
DLYJOB DLY(300)
CHGDEVSTS DEV(COLLECTOR01) STATUS(*ENABLE)
```
