`DSPPFM` opens **Display Physical File Member**: a file's records in columns, a field to a column under its heading
(the field's `COLHDG` lines, or its name). A keyed file is shown in key order, any other in the order its records were
written. It's 5=Display data on Work with Files ({{cmd WRKF}}). Without a library, the file is looked for down your
library list; a file that isn't there is {{msg ELC2205}} on the message line.

{{layout 18_dsppfm}}

- **PageUp / PageDown** roll the records. The screen fetches them from the server a window at a time, so a large file
  opens quickly.
- **Position to** (row 3): on a keyed file, type a key (its fields' values separated by blanks; the first fields alone
  will do) and the first record at or after it comes to the top. On a file without a key, type a record number.
- **F19 / F20** shift the columns 40 to the left or right, for records wider than the screen.

It only shows records: to change them use {{cmd UPDDTA}}; to pick and sort them, {{cmd RUNQRY}}. `ELSYS` system files
show the network's data as it is when the screen reads it, and need the Firewall's view permission
({{msg ELC0401}}). See {{page elcl/screens}}.

```elcl nocompile="a command-line command: type it at the ===> prompt"
DSPPFM FILE(ELGPL/ITEMHIST)
DSPPFM FILE(ELSYS/DEVICES)
```
