Gives a device a new name, the one programs and screens use for it from then on. Work with Devices' 2=Change runs this
command. A device's first name (its type and the lowest number free, `UPS02`) is given when it first joins a network;
it keeps whatever name it has until it's renamed, and carries it with it: a rack device on its item, a Terminal Desk or
Control Interface on its block item. See {{cmd RTVDEVSTS}} for how names work.

- **`NEWNAME`**: up to 10 characters, a letter first, then letters, digits, `_`, `@`, `#` or `$`. Anything else is
  {{msg ELC0103}}. Lower case is turned into upper case.
- Names are unique on a system: a name another device already holds is {{msg ELC1308}}. Renaming a device to the name it
  already has succeeds and changes nothing.
- A name no device has is {{msg ELC1301}}.

On success it sends {{msg ELC1309}}. Renaming doesn't change anything that still uses the old name: programs, trigger
events and schedule entries that name the device, and a Directory Server's published list, have to be updated by hand.

```elcl
RNMDEV DEV(UPS02) NEWNAME(MAINUPS)
RNMDEV DEV(EGRESS03) NEWNAME(FURNACE_IN)
```
