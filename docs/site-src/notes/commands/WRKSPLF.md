`WRKSPLF` opens **Work with Output**: your spooled files, newest first, with the job that made each one
(`nnnnnn/JOBNAME`), its user, pages (60 lines each), status (`*RDY`, `*PRT` once printed, `*HLD`) and when it was
made. It's Main Menu option 8. You type it at a command line; it doesn't run in a program.

{{layout 13_wrksplf}}

What makes a spooled file: compile listings ({{cmd CRTELPGM}}, {{cmd CRTPF}}, {{cmd CHGPF}}, 14=Compile), printed
members, {{cmd PRTTXT}}, and {{cmd RUNQRY}} `OUTPUT(*PRINT)` (file `QPQUPRFIL`). A system keeps
{{config elcl.spooledFileCap default}} spooled files; past that the oldest goes.

- **`JOB(*ALL)`**, the default, lists all of yours. Give a job's name (or number) to see only that job's files; Work
  with Active Jobs' 8=Spooled files opens it that way.

| Option | What it does |
|---|---|
| 4=Delete | Deletes it, after you confirm. |
| 5=Display | Shows it a page at a time (Display Spooled File): PageUp / PageDown, F19 / F20 shift 40 columns. |
| 6=Print | Prints it on the {{item line_printer}} (`PRT01`): {{msg ELC1301}} when there's none, {{msg ELC1306}} when it's out of paper. |

F11 sorts by created, file or status. See {{page elcl/screens}}.

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKSPLF
WRKSPLF JOB(LOGITEMS)
```
