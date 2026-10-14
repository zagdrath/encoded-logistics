`CHGPF` makes a physical file again from its definition, **keeping its records**. Change the file's `PF` member
(add a field, lengthen one, change the key), then run `CHGPF`. Option 14=Compile on a `PF` member whose file exists
already runs it for you; Work with Members and Work with Files show `*` in Chg while a file's definition has changed
since it was made.

- **`FILE`** must exist. Without a library it's looked for down your library list.
- **`SRCMBR(*FILE)`**, the default, uses the member the file was made from. Name another (`LIB/MEMBER`) to make the
  file from that one instead; it becomes the file's member from then on. A file made by {{cmd RUNQRY}}
  `OUTPUT(*OUTFILE)` or {{cmd CPYF}} `CRTFILE(*YES)` has no member, so it needs `SRCMBR`, else {{msg ELC2247}}.
- **`TEXT(*SAME)`**, the default, keeps the file's text; `*BLANK` clears it.

**What happens to the records.** Each record keeps its relative record number and its value in every field whose name
and type are both still there. A text field made shorter is cut. A field gone, or of another type now, loses its
values: `CHGPF` sends {{msg ELC2232}} (a warning) for each. New fields start blank. Then {{msg ELC2231}} says how many
records were kept.

**When nothing changes:**

| Message | Why |
|---|---|
| {{msg ELC2246}} | The definition has errors (see its listing in Work with Output). |
| {{msg ELC2209}} | A record's value no longer fits its field (a number with more digits than the field takes now). |
| {{msg ELC2203}} | The key is `UNIQUE` now, and two records have the same key. |
| {{msg ELC0207}} | The records would take more storage than the drives have left. |

The listing goes to Work with Output as {{cmd CRTPF}}'s does, with a line for each field dropped. Changing a file needs
`*CHANGE` on its library ({{msg ELC0401}}); `ELSYS` files can't be changed ({{msg ELC0205}}).

> [!WARNING] Compile the programs that use it again
> A program keeps the record format it was compiled with. If `CHGPF` drops or retypes a field the program declares,
> its file operations fail with {{msg ELC2207}} until you compile it again.

```elcl
CHGPF FILE(ELGPL/ITEMHIST)
CHGPF FILE(ELGPL/ITEMHIST) SRCMBR(ZAGLIB/ITEMHIST2) TEXT('Item counts, with mod')
```
