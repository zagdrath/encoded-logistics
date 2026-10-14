`CRTMBR` makes an empty source member in a library. Open it with {{cmd EDTMBR}} to write it.

- `MBR(LIB/NAME)`: give the library. A bare name goes in the first library on your library list that already has a
  member of that name (which is then {{msg ELC0204}}), else in your current library. The name is up to 10
  characters, a letter then letters, digits, `_`, `@`, `#` or `$` ({{msg ELC0103}} otherwise).
- `SRCTYPE(ELCLP)`, the default, is a program's source (compile it with {{cmd CRTELPGM}}); `SRCTYPE(PF)` is a
  physical file's definition (compile it with {{cmd CRTPF}}). Any other type is {{msg ELC0103}}.
- `TEXT` is its description, up to 48 characters, shown on Work with Members.

The library must be one you may change: `ELSYS` is {{msg ELC0205}}, and a library that isn't yours, with public
authority `*USE`, is {{msg ELC0401}} unless you're a security officer. A member of that name already there is
{{msg ELC0204}}. An empty member takes no storage. Done: {{msg ELC0214}}.

```elcl
CRTMBR MBR(ELGPL/MYLOG) TEXT('Item log definition') SRCTYPE(PF)
CRTMBR MBR(ELGPL/LOGIT) TEXT('Writes the item log')
```

See [Libraries and the editor](page:elcl/libraries-and-editor#libs-members).
