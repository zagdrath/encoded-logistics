`PRTTXT` adds a line of text to a spooled file. Nothing goes to paper: the file waits in Work with Output
({{cmd WRKSPLF}}) until you look at it (5=Display), print it on the {{item line_printer}} (6=Print, or
{{cmd PRTRPT}} `RPT(*SPLF)` from the same job) or delete it.

- `SPLF(*JOB)` (the default) writes to the job's `QPRINT` file. A name writes to a spooled file of that name instead,
  so one job can build several reports side by side.
- The first line creates the file (status `*RDY`); every later `PRTTXT` to the same name from the same job adds a line
  to the end of it. A different job, or a later run of the same program in a new job, starts a new file.
- The job is the batch job the program runs in, or, typed on the command line, your interactive job.
- A line is at most 64 characters. Work with Output counts 60 lines a page; on paper a page holds 42 lines of 56
  characters, and a longer line carries on onto the next line.

A system keeps up to {{config elcl.spooledFileCap default}} spooled files, of all kinds; past that the oldest is
removed. `PRTTXT` sends no completion message.

```elcl
DCL VAR(&COUNT) TYPE(*INT)
RTVITMCNT ITEM(minecraft:iron_ingot) RTNCOUNT(&COUNT)
PRTTXT TEXT('NIGHTLY STOCK REPORT')
PRTTXT TEXT('Iron ingots:' *BCAT %CHAR(&COUNT))
PRTTXT TEXT('Done.') SPLF(STOCKRPT)
PRTRPT RPT(*SPLF) SPLF(QPRINT)
```

See {{page elcl/jobs-and-scheduling}} for spooled output.
