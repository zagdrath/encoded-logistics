`CPYTOIMPF` writes a physical file's records to a CSV file in the world save, for a spreadsheet or another program to
read. Done, it sends {{msg ELC2236}} with how many records were written.

- **Where:** `<world>/encodedlogistics/libraries/<SYSNAME>/<name>.csv`, the system's
  [folder-sync](page:elcl/libraries-and-editor#folder-sync) folder (`SYSNAME` is the system value). A file of that name
  there is replaced.
- **`TOSTMF`** is a plain file name ending in `.csv` (letters, digits, `_`, `.` and `-`, no folders): `'stock.csv'`.
  Anything else is {{msg ELC2245}}, as is a file that can't be written.
- **Folder sync must be on** ({{config elcl.allowFolderSync}}: on in single-player, off on dedicated servers unless the
  admin turns it on), else {{msg ELC2241}}. See {{page admin/folders}}.
- **The CSV:** a heading row of the field names, then a row a record, in the file's order (key order, or as written).
  Numbers are plain (`1234`, `-5.20`), logicals `1` or `0`, timestamps as text (`00012 06:30:15`). A value is quoted when
  it holds a comma, a double quote or a line break, or starts or ends with a blank.
- Without a library, the file is looked for down your library list. `ELSYS` system files can be exported too (the
  network's data as it is now; that needs the Firewall's view permission, {{msg ELC0401}}).

{{cmd CPYFRMIMPF}} reads a CSV back into a file.

```elcl
CPYTOIMPF FILE(ELSYS/INVITEMS) TOSTMF('inventory.csv')
CPYTOIMPF FILE(ELGPL/ITEMHIST) TOSTMF('itemhist.csv')
```
