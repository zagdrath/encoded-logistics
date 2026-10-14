`DSPFD` opens **Display File Description**: everything about a physical file except its records. It's 8=Display
description on Work with Files ({{cmd WRKF}}). Without a library, the file is looked for down your library list.

{{layout 20_dspfd}}

It shows the file's library, attribute and text; its record format; the source member it was made from (or `*NONE`
for a file made by {{cmd RUNQRY}} or {{cmd CPYF}}, `*SYSTEM` for `ELSYS`'s system files) and whether that member
changed since; when it was made; how many records it holds of the {{config elcl.maxRecordsPerFile}} limit; its record
length and the storage the records take; and its key. Then a line for each field: name, type (`A` character, `S`
integer, `P` decimal, `L` logical, `T` game timestamp), length, decimals, its place in the key, and its text or column
heading.

PageUp / PageDown roll it; Enter or F12 goes back. Use it to check a field's exact name and type before writing a
program ({{cmd DCLF}}) or a query. See {{page elcl/screens}}.

```elcl nocompile="a command-line command: type it at the ===> prompt"
DSPFD FILE(ELSYS/INVITEMS)
```
