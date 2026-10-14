Lists the names of the network's devices into a `*LIST` variable, in the order Work with Devices numbers them (rack by
rack, each rack from its lowest unit up, then the other devices).

- **`TYPE`**: a type code, the start of a device's name without its number: `UPS`, `EGRESS`, `DEPLOYER`, `TAPELIB`,
  `CTLIF`... `ELDESK` and `DESK` both mean Terminal Desks. {{page reference/device-types}} lists them all. `*ALL` (the
  default) keeps every type. A code no device has gives an empty list, not an error.
- **`STATUS`**: keeps devices whose status, as {{cmd RTVDEVSTS}} returns it, is `*ONLINE`, `*OFFLINE`, `*FAULT` or
  `*DISABLED`.

The command sends no completion message. Use the names with any command that takes a device, as here, where every
Deployer Plane is switched off:

```elcl
DCL VAR(&DEVS) TYPE(*LIST)
DCL VAR(&DEV) TYPE(*CHAR) LEN(10)
RTVDEVLST TYPE(DEPLOYER) STATUS(*ONLINE) RTNLST(&DEVS)
FOREACH VAR(&DEV) IN(&DEVS)
  CHGDEVSTS DEV(&DEV) STATUS(*DISABLE)
ENDFOR
```
