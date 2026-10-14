`CLRLST` removes every element from a `*LIST` variable, leaving it empty (`%SIZE` is 0), as it was when declared. It
never fails on an empty list, and it sends no message.

You don't need it before a command that returns a list: an `RTNLST` parameter replaces the whole list. Use it when a
loop builds a list afresh on each pass with {{cmd ADDLSTE}}. To remove single elements, use {{cmd RMVLSTE}}.

```elcl
DCL VAR(&LOW)   TYPE(*LIST)
DCL VAR(&ITEMS) TYPE(*LIST)
DCL VAR(&ITEM)  TYPE(*CHAR) LEN(64)
DCL VAR(&N)     TYPE(*INT)
DOWHILE COND('1')
  CLRLST LIST(&LOW)
  RTVITMLST FILTER('*_ingot') RTNLST(&ITEMS)
  FOREACH VAR(&ITEM) IN(&ITEMS)
    RTVITMCNT ITEM(&ITEM) RTNCOUNT(&N) NOTFND(*ZERO)
    IF COND(&N *LT 64) THEN(ADDLSTE LIST(&LOW) VALUE(&ITEM))
  ENDFOR
  IF COND(%SIZE(&LOW) *GT 0) THEN(SNDMSG MSG(%CHAR(%SIZE(&LOW)) *BCAT 'ingots are low') TOUSR(*SYSOPR))
  DLYJOB DLY(300)
ENDDO
```
