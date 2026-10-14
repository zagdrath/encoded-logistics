`RSTLIB` restores a library saved with {{cmd SAVLIB}} from an {{item diskette_8in}}, on this system or any other.
`DEV` is the diskette drive (a {{item midrange_system}}, {{item integrated_midrange}} or {{item card_reader}}, by device
name): it reads the first diskette in that drive holding a library of that name. The library comes back under the same
name; there's no way to restore it under another.

- **A new library** is created, owned by you, with public authority `*USE`, and its type and text from the diskette.
- **An existing library** has its contents replaced: its members, programs and files become exactly the diskette's,
  so members that aren't on the diskette are deleted. That takes the right to change the library ({{msg ELC0401}}
  otherwise), and `ELSYS` can never be restored over ({{msg ELC0205}}).

The members and files must fit in the network's storage, counting what the replaced ones took ({{msg ELC0207}}
otherwise), and no member may be longer than {{config elcl.maxSourceLines default}} lines ({{msg ELC0004}}). Nothing
is changed when either check fails.

## Messages

- {{msg ELC0221}}: restored, naming the device and the diskette's label.
- {{msg ELC0201}}: no diskette in the drive holds that library.
- {{msg ELC1301}}: no device of that name; {{msg ELC1302}}: it's offline (a Midrange System that isn't running);
  {{msg ELC1310}}: no diskette in it.

```elcl
RSTLIB LIB(ZAGLIB) DEV(CARDRDR01)
```

See [Saving to diskette](page:elcl/libraries-and-editor#savlib).
