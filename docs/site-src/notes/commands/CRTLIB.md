`CRTLIB` makes an empty library on the system. You become its owner and its public authority is `*USE`: everyone can
read it and call its programs, but only you (and a security officer) can change what's in it. Use {{cmd CHGLIB}}
`AUT(*CHANGE)` to let everyone change it.

- `LIB` is up to 10 characters: a letter, then letters, digits, `_`, `@`, `#` or `$`. Anything else is
  {{msg ELC0103}}. A library of that name already there is {{msg ELC0204}} (it names the library as being in `QSYS`).
- `TYPE(*PROD)` or `TYPE(*TEST)` is a label: Work with Libraries shows it, and nothing else depends on it.
- `TEXT` is the library's description, up to 50 characters.

Done: {{msg ELC0210}}. A new library isn't added to anyone's library list: name it in commands (`ZAGLIB/RESTOCK`), or
choose it as your current library on the Sign On screen.

```elcl
CRTLIB LIB(ZAGLIB) TEXT('Zag''s programs') TYPE(*TEST)
CRTMBR MBR(ZAGLIB/RESTOCK) TEXT('Restock the iron')
```

See [Libraries and the editor](page:elcl/libraries-and-editor#libs-members) and {{cmd WRKLIB}}.
