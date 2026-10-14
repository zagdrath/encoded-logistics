`CRTPF` compiles a `PF` source member (a file definition, see [Defining a file](page:elcl/database-files#db-define))
and makes the physical file from it, with no records. Option 14=Compile on a `PF` member in Work with Members runs it
for you, and F6 on Work with Files prompts it.

- **`FILE`** is the file to make. Without a library it goes in your current library. A file of that name there already
  is {{msg ELC0204}}: to remake a file from a changed definition and keep its records, use {{cmd CHGPF}}.
- **`SRCMBR(*FILE)`**, the default, compiles the member of the same name in the file's library. Name another member as
  `LIB/MEMBER` (`SRCMBR(ELSYS/ITEMHIST)`). A member that isn't there is {{msg ELC0202}}; one that isn't of type `PF` is
  {{msg ELC2247}}.
- **`TEXT(*SRCMBRTXT)`**, the default, takes the member's text, or, if it has none, the `TEXT` on the record format's
  `R` line. `*BLANK` leaves the file without text.

**The listing** always goes to Work with Output ({{cmd WRKSPLF}}): a spooled file named after the file, in your
interactive job, with the source and its sequence numbers, the record format (fields, record length, key) and each
message. Then:

- {{msg ELC2230}}: the file is made.
- {{msg ELC2239}}: the definition has errors, and there's no file. Its messages are in the listing.

Making a file needs `*CHANGE` on the library ({{msg ELC0401}}); nothing can be made in `ELSYS` ({{msg ELC0205}}). An
invalid file name is {{msg ELC0103}}.

```elcl
CRTPF FILE(ELGPL/ITEMHIST) SRCMBR(ELSYS/ITEMHIST) TEXT('Item count history')
MONMSG MSGID(ELC0204)   /* there already: its records stay */
```
