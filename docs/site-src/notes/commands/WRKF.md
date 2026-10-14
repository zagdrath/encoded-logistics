`WRKF` opens **Work with Files** on your terminal: the physical files in one library, with their attribute (`PF`),
record counts and text. It's Main Menu option 10. `LIB(*CURLIB)`, the default, shows your current library. Like every
screen command it's typed at a command line, not run in a program.

{{layout 17_wrkf}}

Type an option next to one or more files and press Enter:

| Option | What it does |
|---|---|
| 2=Change data | Opens Update Data ({{cmd UPDDTA}}) on the file. |
| 4=Delete | Deletes the file and its records ({{cmd DLTF}}), after you confirm. |
| 5=Display data | Shows the records in columns ({{cmd DSPPFM}}). |
| 8=Display description | Shows its fields, key and size ({{cmd DSPFD}}). |

- **Chg `*`** marks a file whose definition (the `PF` member it was made from) changed after the file was made. Option
  14=Compile on that member in Work with Members makes it again, keeping its records ({{cmd CHGPF}}).
- **F6** prompts {{cmd CRTPF}} with the library already in its File field.
- **`ELSYS`'s files** are the network's own data, made as they're read: 5 and 8 work, 2 and 4 are refused with
  {{msg ELC0205}}.

Work with Members ({{cmd WRKMBR}}) lists a library's files too, after its members (Type `*FILE`). See
{{page elcl/screens}} and [the database screens](page:elcl/database-files#db-screens).

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKF LIB(ZAGLIB)
WRKF LIB(ELSYS)
```
