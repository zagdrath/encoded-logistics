`ADDLSTE` adds one element to a `*LIST` variable: at the end, or at a position you give, moving the elements from there
on one place down. Elements are text; to add a number, convert it with `%CHAR` (`VALUE(%CHAR(&N))`), since a numeric
variable is {{msg ELC0103}} here (a literal number is taken as text).

- `VALUE`'s trailing blanks are dropped, so adding a `*CHAR(64)` variable holding `'minecraft:stone'` adds just
  `minecraft:stone`.
- `POS(*END)`, the default, adds after the last element.
- `POS(n)` puts the new element at position *n* (from 1): `POS(1)` adds it first, `POS(%SIZE(&LIST) + 1)` is the same
  as `*END`. A position past that, or below 1, is {{msg ELC0006}} ("List index n is out of range (size s)") when it
  runs; a literal below 1 or above {{config elcl.maxListSize}} is {{msg ELC0004}} when you compile.
- A list holds at most {{config elcl.maxListSize default}} elements. Adding to a full list is {{msg ELC0015}}.
- Duplicates are allowed; `ADDLSTE` doesn't check what is already there.

`ADDLSTE` sends no message. Read a list with `%SIZE`, `%ELEM` and {{cmd FOREACH}}; take elements out with
{{cmd RMVLSTE}} and {{cmd CLRLST}}. More on the [types](page:elcl/language#types).

```elcl
DCL VAR(&WATCH) TYPE(*LIST)
DCL VAR(&ITEM)  TYPE(*CHAR) LEN(64)
DCL VAR(&N)     TYPE(*INT)
ADDLSTE LIST(&WATCH) VALUE('minecraft:iron_ingot')
ADDLSTE LIST(&WATCH) VALUE('minecraft:gold_ingot')
ADDLSTE LIST(&WATCH) VALUE('minecraft:diamond') POS(1)
FOREACH VAR(&ITEM) IN(&WATCH)
  RTVITMCNT ITEM(&ITEM) RTNCOUNT(&N) NOTFND(*ZERO)
  PRTTXT TEXT(&ITEM *BCAT %CHAR(&N))
ENDFOR
```
