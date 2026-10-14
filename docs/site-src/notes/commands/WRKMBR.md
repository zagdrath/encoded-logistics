Work with Members lists a library's source members (type `ELCLP` for a program's source, `PF` for a file's
definition), then its database files (type `*FILE`). `LIB(*CURLIB)`, the default, is your current library. Work with
Libraries option 12 opens it too. Type an option next to a member and press Enter:

| Option | What it does |
|---|---|
| `2` | Edit it in the source editor ({{cmd EDTMBR}}). |
| `3` | Copy it to another library or name, in a window ({{cmd CPYMBR}}). |
| `4` | Delete it, after you confirm ({{cmd DLTMBR}}). |
| `5` | Display it: the editor, read-only. |
| `6` | Print the source to a spooled file (see {{cmd WRKSPLF}}). |
| `7` | Rename it, in a window ({{cmd RNMMBR}}). |
| `9` | Submit its program as a batch job: {{cmd SBMJOB}}'s prompter with `CMD(CALL PGM(LIB/MEMBER))` and `JOB(MEMBER)` filled in, so you can add parameters or pick a host first. |
| `14` | Compile it. An `ELCLP` member: {{cmd CRTELPGM}}. A `PF` member: {{cmd CRTPF}}, or {{cmd CHGPF}} when its file is there already (which keeps the records). The listing goes to Work with Output. |
| `16` | Call its program in your interactive job: {{cmd CALL}}'s prompter with `PGM(LIB/MEMBER)` filled in. |

Options 9 and 16 need the member compiled since it last changed. If it isn't, the message line says so, and Enter
compiles it (as 14) and then goes on to the prompter. The **Chg** column shows `*` when the source changed after its
program (or file) was made.

On a file: 2=Change data ({{cmd UPDDTA}}), 4=Delete ({{cmd DLTF}}), 5=Display data ({{cmd DSPPFM}}), 8=Display
description ({{cmd DSPFD}}). In `ELSYS`, options 2, 4 and 7 are refused with {{msg ELC0205}}: browse with 5 and copy
with 3. F6 prompts {{cmd CRTMBR}} (`SRCTYPE(PF)` for a file's definition); F11 sorts the members by name, by changed
or by text.

{{layout 04_wrkmbr}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKMBR LIB(ELSYS)
```

See [Libraries and the editor](page:elcl/libraries-and-editor) and [database files](page:elcl/database-files).
