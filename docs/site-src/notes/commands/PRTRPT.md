`PRTRPT` prints a report straight to paper on a {{item line_printer}}: it comes out of the printer's front as a
{{item printout}}, one sheet of {{item minecraft:paper}} a page. Nothing is spooled. (To collect lines in a spooled
file first, use {{cmd PRTTXT}}.)

| `RPT` | Prints | Title on the pages |
|---|---|---|
| `*INV` | Every item in the network's storage: its quantity, tier (hot and cold listed apart) and where most of it is, then the total | `NETWORK INVENTORY LISTING` |
| `*DEV` | Every named device: its name, type, status, lanes and where it is | `DEVICE LIST` |
| `*JOBLOG` | This job's log: the time, the job and each event (commands, and messages with their IDs; errors in red) | `JOB LOG -` and the job |
| `*SPLF` | One of this job's spooled files, as it is | The spooled file's name |

- `*JOBLOG` and `*SPLF` use the job the command runs in: the batch job, or your interactive job on the command line.
  A job log is only ever printed this way; it's never spooled.
- With `*SPLF`, `SPLF(*LAST)` (the default) is the job's newest spooled file, and a name its newest file of that
  name, such as `QPRINT` for {{cmd PRTTXT}}'s. A file the job doesn't have is {{msg ELC0103}} for `SPLF`. Print
  another job's files from Work with Output ({{cmd WRKSPLF}}, 6=Print).
- `*INV` needs the network's storage: without it the command stops with {{msg ELC1302}} for `*NETWORK`.

## The printer

`DEV(*DFT)` (the default) is the first online Line Printer on the network; with none, {{msg ELC1301}} names `PRT01`.
`DEV(name)` picks one by its device name: not there is {{msg ELC1301}}, offline {{msg ELC1302}}, and a printer with
no paper at all {{msg ELC1306}}.

A page holds 42 lines of 56 characters, under a header with the system, the title and the date, and a page number;
a longer line carries on onto the next. A report is at most 100 pages. If the paper runs out part-way, the pages
printed so far come out and the rest wait in the printer, printing as soon as you put more paper in.

When it has printed, `PRTRPT` sends {{msg ELC1307}} with the report's title and the printer's name.

```elcl
PRTRPT RPT(*INV)
PRTRPT RPT(*DEV) DEV(PRT02)
PRTRPT RPT(*JOBLOG)
PRTRPT RPT(*SPLF) SPLF(QPRINT)
```

The Line Printer's own screen prints the same reports by hand. See {{page elcl/jobs-and-scheduling}} for spooled
output.
