`UPDDTA` opens **Update Data**: a physical file's records one at a time, to change, delete or add, as a midrange data
file utility does. It's 2=Change data on Work with Files ({{cmd WRKF}}). Without a library, the file is looked for down
your library list.

{{layout 21_upddta}}

Each field is a row: its name, its text or column heading, its value in an input field, and its definition (`64A`,
`9P 2`). Fields that don't fit one screen go on further pages (PageUp / PageDown).

- **Change mode** shows a record. Change its values and press Enter to save them; Enter with nothing changed goes on to
  the next record. **F7 / F8** show the record before or after. **F11** deletes the record, after you confirm. On a
  keyed file, type a key in **Position to** and press Enter to go to the first record at or after it.
- **Entry mode** (**F6**; an empty file opens in it) is for new records: type the values and press Enter to add one;
  the fields clear for the next. **F10** goes back to change mode.

Values are checked by type before they're sent: a whole number for `S`, a number for `P`, `1` or `0` for `L`, and for
`T` a game timestamp (`Day 12 06:30`) or blank for the moment it's written. The server checks them again: a value that
doesn't fit ({{msg ELC2209}}) or a duplicate `UNIQUE` key ({{msg ELC2203}}) shows on the message line, with the cursor
on the field it names. A record whose values come to more than about 8,000 characters in all can't be sent from the screen.

Changing records needs `*CHANGE` on the file's library ({{msg ELC0401}}). `ELSYS`'s files are the network's own data
and read-only: `UPDDTA` on one is refused with {{msg ELC0205}} (use {{cmd DSPPFM}} to look at them). See
{{page elcl/screens}} and [the database screens](page:elcl/database-files#db-screens).

```elcl nocompile="a command-line command: type it at the ===> prompt"
UPDDTA FILE(ELGPL/ITEMHIST)
```
