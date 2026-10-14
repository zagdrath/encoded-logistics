`DLTF` deletes a physical file and all its records, at once and for good: it doesn't ask. (4=Delete on Work with
Files asks first, then runs it.) Done, it sends {{msg ELC2233}}.

- Without a library, the file is looked for down your library list, and the first one found is deleted. Qualify the
  name to be sure which.
- The file's definition member isn't touched: {{cmd CRTPF}} makes the file again, empty.
- A program that declares the file fails at its next file operation ({{msg ELC2205}}) until the file is made again.
- A file that isn't there is {{msg ELC2205}} ({{msg ELC0201}} for a library that isn't). It needs `*CHANGE` on the
  library ({{msg ELC0401}}); `ELSYS`'s files can't be deleted ({{msg ELC0205}}).

To empty a file and keep it, use {{cmd CLRPFM}}.

```elcl
DLTF FILE(ZAGLIB/OLDSTOCK)
MONMSG MSGID(ELC2205)   /* gone already */
```
