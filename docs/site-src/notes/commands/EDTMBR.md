`EDTMBR` opens a source member in the full-screen source editor, as Work with Members option 2 does. Give
`MBR(LIB/NAME)`. With a bare name, or with `*LIBL/` or `*CURLIB/` in front, it opens the member of that name in your
current library (it doesn't search the library list). The member must exist already: make it with {{cmd CRTMBR}} (or
F6 on Work with Members).

The editor opens read-only (Display Member) when:

- the member is in `ELSYS`;
- you may not change its library: it isn't yours, its public authority is `*USE`, and you aren't a security officer;
- someone else has it open. You see {{msg ELC0208}}: one user edits a member at a time, and the lock goes when they
  leave the editor or close their terminal.

Each Enter checks the statements you changed and shows the first error reversed. The `SAVE` and `FILE` editor
commands write the member: {{msg ELC0213}}. A save that needs more network storage than the drives have free is
{{msg ELC0207}}, and a member longer than {{config elcl.maxSourceLines default}} lines is {{msg ELC0004}}. Saving
doesn't compile: the program stays as it was until you compile the member again ({{cmd CRTELPGM}}, or option 14).

{{layout 05_source_editor}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
EDTMBR MBR(ZAGLIB/RESTOCK)
```

The line commands, editor commands and keys are on
[Libraries and the editor](page:elcl/libraries-and-editor#editor).
