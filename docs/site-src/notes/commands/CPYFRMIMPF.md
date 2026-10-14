`CPYFRMIMPF` reads a CSV file from the world save into a physical file's records: one you exported with
{{cmd CPYTOIMPF}}, or one you made in a spreadsheet. Done, it sends {{msg ELC2237}} with how many records were added.

- **Where it's read from:** `<world>/encodedlogistics/libraries/<SYSNAME>/<name>.csv`, the system's
  [folder-sync](page:elcl/libraries-and-editor#folder-sync) folder. A file that isn't there is {{msg ELC2240}}.
- **`FROMSTMF`** is a plain file name ending in `.csv` (letters, digits, `_`, `.` and `-`, no folders); anything else is
  {{msg ELC2245}}. Folder sync must be on ({{config elcl.allowFolderSync}}), else {{msg ELC2241}}.
- **`MBROPT(*ADD)`**, the default, adds the rows to the records there; **`*REPLACE`** deletes those first.
- **Columns.** If the first row is a heading (every cell in it the name of one of the file's fields, in any case and
  any order), the columns are matched to fields by name: fields it leaves out are blank, and it's not read as a record.
  Otherwise the columns are the file's fields in order, and every row is a record. Cells past the last field are
  ignored; a short row leaves the rest blank. Blank lines are skipped.
- **Values** are taken as their field's type: a whole number for `S`, a number for `P`, `1` or `0` for `L` (blank is
  `0`), a game timestamp for `T` (`00012 06:30:15`, `12 06:30` or `Day 12 06:30`; blank takes the time it's read in).
  Text too long for an `A` field is cut. Double quotes around a cell are taken off (`""` inside is one quote).
- **All or nothing.** A value that doesn't fit is {{msg ELC2243}}, with its row (counting the heading) and the field;
  a duplicate `UNIQUE` key {{msg ELC2203}}; too many records {{msg ELC2208}}; no room on the drives {{msg ELC0207}}.
  Then nothing is added, and with `*REPLACE` the old records stay.
- The file must exist (make it with {{cmd CRTPF}} first). Without a library it's looked for down your library list.
  It needs `*CHANGE` on the library ({{msg ELC0401}}); `ELSYS` files can't be imported into ({{msg ELC0205}}).

```elcl
CPYFRMIMPF FROMSTMF('itemhist.csv') FILE(ELGPL/ITEMHIST) MBROPT(*REPLACE)
```
