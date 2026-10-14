`DLTMBR` deletes a source member from a library. There's no confirmation on the command line (Work with Members
option 4 asks first).

- The library must be one you may change: `ELSYS` is {{msg ELC0205}}, someone else's `*USE` library
  {{msg ELC0401}}.
- A member someone else has open in the editor can't be deleted: {{msg ELC0208}}.
- No member of that name is {{msg ELC0202}}.

Only the source goes. A program compiled from it stays and still runs (it keeps its own copy of the source); delete it
with {{cmd DLTPGM}}. With folder sync on, the member's file moves to the `.deleted` folder beside it. Done:
{{msg ELC0217}}.

```elcl
DLTMBR MBR(ZAGLIB/RESTOCKOLD)
```
