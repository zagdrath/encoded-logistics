`CLRPFM` deletes every record in a physical file and keeps the file, its format and its definition. Done, it sends
{{msg ELC2234}} with how many records went. The storage they took is freed.

- Without a library, the file is looked for down your library list.
- Relative record numbers carry on from where they were: the next record written doesn't get number 1 again.
- It needs `*CHANGE` on the library ({{msg ELC0401}}); `ELSYS`'s files can't be cleared ({{msg ELC0205}}).

To replace a file's records with others in one go, {{cmd CPYF}} and {{cmd CPYFRMIMPF}} take `MBROPT(*REPLACE)`. To
delete the file itself, use {{cmd DLTF}}.

```elcl
CLRPFM FILE(ELGPL/ITEMHIST)
```
