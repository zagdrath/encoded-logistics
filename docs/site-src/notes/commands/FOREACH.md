`FOREACH` runs the statements up to its {{cmd ENDFOR}} once for each element of a list, first to last, with the element
in `VAR`. An empty list runs the body no times.

- `IN` must be a `*LIST` variable and `VAR` a `*CHAR` one ({{msg ELC0103}} otherwise). Each element is padded or cut to
  `VAR`'s length, so declare it long enough for what the list holds: item IDs need `LEN(64)` or so, device names 10.
- The list is read as the loop goes, by position. An element {{cmd ADDLSTE}} adds at the end during the loop gets its
  own pass; {{cmd RMVLSTE}} on an element already visited moves the rest up, so the next one is skipped. To change a
  list while going through it, a {{cmd DOFOR}} over the positions is easier to follow.
- After the loop `VAR` holds the last element it was given.
- {{cmd LEAVE}} ends the loop, {{cmd ITERATE}} goes on with the next element. Label the `FOREACH` to let a loop inside it
  name this one.

Lists come from the commands with an `RTNLST` parameter ({{cmd RTVITMLST}}, {{cmd RTVDEVLST}} and others) or from
{{cmd ADDLSTE}}.

```elcl
DCL VAR(&DEVS) TYPE(*LIST)
DCL VAR(&DEV)  TYPE(*CHAR) LEN(10)
RTVDEVLST TYPE(DEPLOYER) RTNLST(&DEVS)
FOREACH VAR(&DEV) IN(&DEVS)
  CHGDEVSTS DEV(&DEV) STATUS(*DISABLE)
  MONMSG MSGID(ELC1300)
ENDFOR
SNDPGMMSG MSG(%CHAR(%SIZE(&DEVS)) *BCAT 'Deployers disabled')
```
