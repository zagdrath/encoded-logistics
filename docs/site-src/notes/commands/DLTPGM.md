`DLTPGM` deletes a compiled program from a library. Its source member stays, so you can compile it again with
{{cmd CRTELPGM}} whenever you like.

The library must be one you may change (`ELSYS` is {{msg ELC0205}}, someone else's `*USE` library
{{msg ELC0401}}). No program of that name in the library is {{msg ELC0203}}. A job already running the program
carries on; later calls of it are {{msg ELC0203}}, and a trigger that calls it fails the same way. Done:
{{msg ELC0219}}.

```elcl
DLTPGM PGM(ZAGLIB/WALLTEST)
```
