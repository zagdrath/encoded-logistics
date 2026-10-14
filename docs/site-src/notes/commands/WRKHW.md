`WRKHW` opens **Work with Hardware**: every chassis on the network ({{item chassis_switch}}es,
{{item blade_enclosure}}s, {{item optical_transport_shelf}}s), a row for each bay with a part in it (or a failed one):
the chassis, the slot, the part (cards, blades, power supplies, modules), its status and a detail. The top line counts
the network's chassis, cards and faults. You type it at a command line; it doesn't run in a program.

- **`DEV(*ALL)`**, the default, shows every chassis. Name one (its device name) to see only its bays; a name that isn't
  a chassis on the network shows no rows.
- **F11** shows every bay, the empty ones too; F11 again goes back.

| Option | What it does |
|---|---|
| 5=Display | The part's details, as its popup in the world shows them. |
| 8=Locate | Outlines the part's faceplate in the world for ten seconds; its coordinates show on the message line. |

See {{page racks/chassis}} and {{page elcl/screens}}.

{{layout 28_wrkhw}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKHW
WRKHW DEV(CHSW01)
```
