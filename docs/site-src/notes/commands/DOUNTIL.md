`DOUNTIL` repeats the statements up to its {{cmd ENDDO}} until its condition holds. The condition is tested after each
pass, so the body always runs at least once, and the loop ends as soon as the condition is true at the end of a pass.
(To test first, use {{cmd DOWHILE}}.)

- `COND` is a logical expression, as for {{cmd IF}}.
- {{cmd LEAVE}} ends the loop at once. {{cmd ITERATE}} skips the rest of the pass and goes to the test, so the loop can
  still end there.
- Put a label on the `DOUNTIL` to let `LEAVE` and `ITERATE` in a loop inside it name this one.

```elcl
DCL VAR(&TRIES) TYPE(*INT)
DCL VAR(&MOVED) TYPE(*INT)
DOUNTIL COND(&MOVED *GT 0 *OR &TRIES *GE 5)
  CHGVAR VAR(&TRIES) VALUE(&TRIES + 1)
  MOVITM ITEM('minecraft:coal') QTY(64) TODEV(EGRESS01) RTNMOVED(&MOVED)
  MONMSG MSGID(ELC1200 ELC1300)
  IF COND(&MOVED *EQ 0) THEN(DLYJOB DLY(10))
ENDDO
```
