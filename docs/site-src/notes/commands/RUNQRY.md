`RUNQRY` picks records out of a physical file, sorts them, and shows, prints or saves the result. The file isn't
changed. It works on any file, the `ELSYS` system files included (the network's data as it is now; they need the
Firewall's view permission, {{msg ELC0401}}). Without a library, the file is looked for down your library list.

## Selecting and sorting

- **`QRYSLT`** is an ELCL condition over the file's field names, up to 64 characters: every operator and built-in
  function of the [language](page:elcl/language) works (`*EQ`, `*LT`, `*AND`, `*OR`, `*NOT`, `%SST`, `*CAT`...). Write a
  field bare (`QTY`) or as `&QTY`, and a literal in double quotes (`"IRON"`), since the whole condition is already in
  single quotes. A record is selected when the condition is true for it. A name that isn't a field of the file is
  {{msg ELC2242}}; a condition that doesn't parse fails with the parser's message. `*ALL`, the default, selects every
  record.
- **`SORT`** lists up to four fields, most important first, each followed by `*DESCEND` to put the highest first
  (`*ASCEND`, lowest first, is the default and may be written too). Records the sort can't tell apart keep the file's
  order. `*NONE`, the default, keeps the file's order: key order, or as written. A fifth field is {{msg ELC2224}}; a
  `*DESCEND` with no field before it {{msg ELC0103}}.

## Where the result goes

| `OUTPUT` | What you get |
|---|---|
| `*DISPLAY` (default) | Typed on a command line: the **Display Report** screen, laid out as {{cmd DSPPFM}}'s, with how many records were selected of how many. PageUp / PageDown roll it, F19 / F20 shift 40 columns. In a program or a batch job, where there's no screen to show it on, it's printed as with `*PRINT`. |
| `*PRINT` | The report (file, selection, date, system, column headings, the records, the count) as the spooled file `QPQUPRFIL` in your job, in Work with Output ({{cmd WRKSPLF}}), 60 lines a page. 6=Print there puts it on the {{item line_printer}}. |
| `*OUTFILE` | The selected records, in the query's order, saved as the file named in `OUTFILE` (required with `*OUTFILE`: {{msg ELC0102}}). It's made with the queried file's fields but no key, and the text `Query of LIB/FILE`, in the library named or your current library. A file of that name there is **replaced**, whatever it held. |

Run on the server (every output but the Display Report screen), `RUNQRY` sends {{msg ELC2238}}: how many records the
query selected, of how many in the file. Writing an `OUTFILE` needs `*CHANGE` on its library ({{msg ELC0401}}), and
can't be in `ELSYS` ({{msg ELC0205}}); it can't hold more than {{config elcl.maxRecordsPerFile}} records
({{msg ELC2208}}).

{{layout 19_runqry}}

## Examples

```elcl
/* Items under a stack, fewest on the drives first, to Work with Output */
RUNQRY FILE(ELSYS/INVITEMS) QRYSLT('HOT + COLD *LT 64 *AND TYPE *EQ "ITEM"') +
       SORT(HOT *ASCEND ITEM) OUTPUT(*PRINT)
/* The devices that aren't online, saved as a file of their own */
RUNQRY FILE(ELSYS/DEVICES) QRYSLT('STATUS *NE "*ONLINE"') OUTPUT(*OUTFILE) +
       OUTFILE(ZAGLIB/DOWNDEV)
/* The crafting jobs that failed, newest first */
RUNQRY FILE(ELSYS/CRFHIST) QRYSLT('STATUS *EQ "*FAILED"') SORT(ENDED *DESCEND) OUTPUT(*PRINT)
```

A Display Panel can show a query's result as a live table: see {{cmd SNDDSPWDG}} `WDG(*TABLE)`. More on queries:
[Queries (RUNQRY)](page:elcl/database-files#db-query).
