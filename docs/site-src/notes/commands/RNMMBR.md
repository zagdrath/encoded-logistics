`RNMMBR` gives a source member a new name in the same library, keeping its type, text and lines. The library must be
one you may change (`ELSYS` is {{msg ELC0205}}, someone else's `*USE` library {{msg ELC0401}}); a member already
called `NEWNAME` is {{msg ELC0204}}, and a name that isn't a letter followed by up to nine letters, digits, `_`, `@`,
`#` or `$` is {{msg ELC0103}}.

Programs and files made from the member aren't renamed. The old program keeps its name (and still runs), and the
renamed member shows as not compiled: compile it with {{cmd CRTELPGM}} to get a program of the new name, and delete
the old one with {{cmd DLTPGM}} if you don't want it. Done: {{msg ELC0216}}.

```elcl
RNMMBR MBR(ZAGLIB/RESTOCK) NEWNAME(RESTOCKOLD)
```
