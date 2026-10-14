`CRTELPGM` compiles an `ELCLP` source member into a program you can run with {{cmd CALL}} or submit with
{{cmd SBMJOB}}. Work with Members option 14 runs it for the member on that row.

- `PGM(LIB/NAME)` is the program to make, in a library you may change (`ELSYS` is {{msg ELC0205}}, someone else's
  `*USE` library {{msg ELC0401}}). A program of that name already there is replaced. With a bare name, the library is
  the first on your library list holding a member of that name, else your current library.
- `SRCMBR(*PGM)`, the default, compiles the member with the program's own name in the same library. Name another
  member (in any library, `ELSYS` included) to compile that instead. A `PF` member is a file's definition, compiled
  with {{cmd CRTPF}}: here it's {{msg ELC2247}}.
- `TGT(*JOB)`, the default, compiles a program for jobs. `TGT(*PLC)` compiles it for a PLC instead, with the PLC's
  rules; only a `*PLC` program can be loaded with {{cmd SNDPLCPGM}}. See {{page elcl/control-and-plcs}}.

Files the program declares with `DCLF` must exist when you compile (they're looked for down your library list), and
the program keeps their record formats as they were.

## Messages

- {{msg ELC0218}}: the program was made (warnings, severity 10, don't stop it).
- {{msg ELC0206}}: a message of severity 20 or more, so no program. An older program of that name is left as it was.

Either way a **compile listing** goes to your spooled files, named after the program, in your interactive job: open it
from Work with Output ({{cmd WRKSPLF}}) to see each error with its line. What the compiler checks, and how the listing
reads, are on [Libraries and the editor](page:elcl/libraries-and-editor#compiling).

```elcl
CPYMBR FROM(ELSYS/NOCWALL) TO(ZAGLIB/NOCWALL)
CRTELPGM PGM(ZAGLIB/NOCWALL)
CRTELPGM PGM(ZAGLIB/WALLTEST) SRCMBR(ZAGLIB/NOCWALL)
```
