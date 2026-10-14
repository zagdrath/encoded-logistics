`RMVLSTE` removes the element at position `POS` (from 1) from a `*LIST` variable; the elements after it move up one
place, so the list is one shorter. A position below 1 or past the last element is {{msg ELC0006}} ("List index n is
out of range (size s)") when it runs; a literal below 1 or above {{config elcl.maxListSize}} is {{msg ELC0004}} when you
compile.

It removes by position only. To remove an element by its value, find its position first, as below. To empty the whole
list, use {{cmd CLRLST}}.

When you remove elements in a loop, go from the end of the list towards the start, so removing one doesn't move the
elements you haven't looked at yet. Removing elements from a list a {{cmd FOREACH}} is going through makes it skip one.

`RMVLSTE` sends no message.

```elcl
DCL VAR(&DEVS) TYPE(*LIST)
DCL VAR(&I)    TYPE(*INT)
RTVDEVLST TYPE(*ALL) RTNLST(&DEVS)
/* Drop every Terminal Desk from the list */
DOFOR VAR(&I) FROM(%SIZE(&DEVS)) TO(1) BY(-1)
  IF COND(%SCAN('ELDESK' %ELEM(&DEVS &I)) *EQ 1) THEN(RMVLSTE LIST(&DEVS) POS(&I))
ENDDO
SNDPGMMSG MSG(%CHAR(%SIZE(&DEVS)) *BCAT 'devices besides the desks')
```
