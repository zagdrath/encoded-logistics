Lists ended crafting jobs from the network's crafting history: their IDs (`C0042`), newest first, into a `*LIST`
variable. The history keeps the last `CRFLOGRTN` jobs (a system value; default
{{config elcl.craftLogRetention default}}); Work with Crafting Jobs' F10 shows the same records.

- **`ITEM`**: only jobs that made this item (ID forms as in {{cmd RTVITMCNT}}; an unknown ID is {{msg ELC1201}}).
  `*ALL` (the default) keeps every job.
- **`STATUS`**: `*DONE`, `*FAILED` or `*CANCELLED` keeps only jobs that ended that way.
- **`MAX`**: keeps the newest *n* that pass the other two.

No matching job gives an empty list. Pass an ID from the list to {{cmd RTVCRFSTS}} to see how far that job got. The
command sends no completion message.

```elcl
DCL VAR(&FAILED) TYPE(*LIST)
DCL VAR(&JOB) TYPE(*CHAR) LEN(10)
DCL VAR(&PCT) TYPE(*DEC) LEN(5 1)
RTVCRFLOG STATUS(*FAILED) MAX(5) RTNLST(&FAILED)
FOREACH VAR(&JOB) IN(&FAILED)
  RTVCRFSTS CRFJOB(&JOB) RTNPCT(&PCT)
  SNDPGMMSG MSG(&JOB *BCAT 'failed at' *BCAT %CHAR(&PCT) *TCAT '%')
ENDFOR
```
