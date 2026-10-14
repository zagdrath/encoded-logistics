`SAVLIB` writes a whole library onto an {{item diskette_8in}}: its source members (with their types, texts, sequence
numbers and change dates), its programs, and its database files with their records. The diskette is an item, so you
can carry the library to another network, keep it as a backup, or hand it to another player, and bring it back with
{{cmd RSTLIB}}.

`DEV` is the diskette drive, by device name:

| Drive | Diskettes | `SAVLIB` writes to |
|---|---|---|
| {{item midrange_system}} (`MIDRANGE01`...) | Its diskette slot, and a second with an {{item expansion_cabinet}} | The diskette in its default drive, else the first one in it |
| {{item integrated_midrange}} | Every diskette in the {{item diskette_magazine}} in it | The same: its default drive's diskette first |
| {{item card_reader}} (`CARDRDR01`...) | One | That one |

A Midrange System's drives are online only while it's running. No device of that name is {{msg ELC1301}}, an offline
one {{msg ELC1302}}, a drive with no diskette {{msg ELC1310}}. The device is checked first, so these come before any
problem with the library ({{msg ELC0201}} when there's no such library).

## What fits

A diskette holds {{config elcl.disketteBytes default}} bytes, and can hold several libraries. A library of the same
name already on it is replaced, so its space counts as free. What a library takes: each member's characters plus one
a line, each program's saved source counted the same way again, and each file's records. A library that won't fit in
what's free is {{msg ELC1311}}, which gives both numbers; nothing is written.

Anyone may save any library, `ELSYS` included (its system files aren't saved: they're the network's own data). Done:
{{msg ELC0220}}, naming the device and the diskette's label.

```elcl
SAVLIB LIB(ZAGLIB) DEV(MIDRANGE01)
```

See [Saving to diskette](page:elcl/libraries-and-editor#savlib) and {{page crafting/midrange}}.
