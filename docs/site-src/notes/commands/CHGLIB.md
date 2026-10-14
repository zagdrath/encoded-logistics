`CHGLIB` changes a library's text, its public authority, or both. Leave a parameter at `*SAME` to keep it as it is.

- `AUT(*USE)`: anyone can read the library and call its programs, but only its owner and a security officer can
  change what's in it. `AUT(*CHANGE)`: anyone can also create, edit, copy into, rename, delete and compile members
  in it.
- Who may run it: the library's owner, a security officer, or anyone at all for a library the system owns (`QSYS`):
  `ELGPL`, and the libraries folder sync makes from new folders. Anyone else gets {{msg ELC0401}} (naming `*OWNER`).
- `ELSYS` can't be changed: {{msg ELC0205}}.

Done: {{msg ELC0212}}. Work with Libraries option 2 runs this command from a window.

```elcl
CHGLIB LIB(ZAGLIB) AUT(*CHANGE)
CHGLIB LIB(ZAGLIB) TEXT('Shared with the base crew')
```

See [Libraries and the editor](page:elcl/libraries-and-editor#libs-members).
