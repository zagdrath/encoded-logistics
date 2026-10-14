`DLTLIB` deletes a library and everything in it: its source members, its programs, and its database files with their
records. There's no confirmation on the command line (Work with Libraries option 4 asks first) and no undo, so save the
library to a diskette with {{cmd SAVLIB}} first if you may want it back.

- Only the library's owner or a security officer may delete it, whatever its public authority: anyone else gets
  {{msg ELC0401}} (naming `*OWNER`).
- `ELSYS` and `ELGPL` can't be deleted: {{msg ELC0205}}.
- With folder sync on, the members' files move to the `.deleted` folder beside them.

Done: {{msg ELC0211}}. A program already running from the library carries on to its end; a later {{cmd CALL}} of it
is {{msg ELC0203}}.

```elcl
DLTLIB LIB(ZAGTEST)
```
