`CLEAR` empties Command Entry's history: the list of commands you ran and the messages they sent back. The message
line then says `Command history cleared.` It's caught by the terminal itself, so it works typed on any command line,
in any case (`clear` too), and it doesn't run in a program. **F13** on Command Entry does the same.

It only clears what Command Entry shows. The commands F9 brings back stay (sign off with {{cmd SIGNOFF}} to clear
those too), and nothing on the server changes: your job log ({{cmd DSPJOBLOG}}) and messages ({{cmd DSPMSG}}) are
untouched.

{{layout 16_cmdent}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
CLEAR
```
