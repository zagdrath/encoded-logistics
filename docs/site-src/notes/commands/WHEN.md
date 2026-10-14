`WHEN` is one branch of a {{cmd SELECT}}: if its condition holds and no earlier `WHEN` in the same `SELECT` did, the
program runs its `THEN` command and then goes on after the {{cmd ENDSELECT}}. The branches don't fall through: once one
`WHEN` has run, the rest aren't even tested.

- `COND` is a logical expression, as for {{cmd IF}}.
- `THEN` holds one command, or `THEN(DO)` and a group of commands closed by {{cmd ENDDO}}. It is optional; a `WHEN`
  without one runs nothing but still stops the later branches from being tested, which is a way to make a case do
  nothing.
- A `WHEN` outside a `SELECT`, or after its {{cmd OTHERWISE}}, is {{msg ELC0009}} or {{msg ELC0001}}.
- Order the branches from the most specific to the most general: the first that holds wins.

```elcl
DCL VAR(&PCT) TYPE(*DEC) LEN(5 1)
RTVSTGSTS RTNPCT(&PCT)
SELECT
  WHEN COND(&PCT *GE 95) THEN(CHGRSOUT DEV(CTLIF01) SIDE(*ALL) LVL(15))
  WHEN COND(&PCT *GE 80) THEN(CHGRSOUT DEV(CTLIF01) SIDE(*ALL) LVL(7))
  OTHERWISE CMD(CHGRSOUT DEV(CTLIF01) SIDE(*ALL) LVL(0))
ENDSELECT
```
