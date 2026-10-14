`CPYMBR` copies a source member, with its type, text and every line (sequence numbers and change dates included), to
a new member in the same or another library. It's the way to take a sample out of `ELSYS` and make it your own.

- `FROM` can be in any library, `ELSYS` among them: reading needs no authority.
- `TO` must be a library you may change (`ELSYS` is {{msg ELC0205}}; someone else's `*USE` library is
  {{msg ELC0401}}) and a name not taken there: copying over an existing member is {{msg ELC0204}}, so delete it first
  with {{cmd DLTMBR}} if you mean to replace it. Give both libraries: a bare name is looked for down your library
  list.
- The copy takes network storage like any member: {{msg ELC0207}} when the drives haven't room for it.

Only the source is copied. A program compiled from `FROM` stays where it is; compile the copy with
{{cmd CRTELPGM}} (or Work with Members option 14) to make one from it. Done: {{msg ELC0215}}.

```elcl
CPYMBR FROM(ELSYS/RESTOCK) TO(ZAGLIB/RESTOCK)
CRTELPGM PGM(ZAGLIB/RESTOCK)
```
