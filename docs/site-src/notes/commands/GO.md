`GO` moves the terminal to a menu. You type it at a command line on any screen; it doesn't run in a program.

| `MENU` | What happens |
|---|---|
| `MAIN` (the default, so plain `GO` does it) | Back to the Main Menu, closing every screen opened on top of it. |
| `HELP` | Opens the help window for the screen you're on, as F1 does. |
| anything else | Nothing: the message line says the menu wasn't found. The Main Menu is the only menu. |

{{layout 02_main_menu}}

The Main Menu's options are numbers you type on its command line (1 for {{cmd WRKINV}} up to 13, and 90 for
{{cmd SIGNOFF}}); any command can be typed there too. See [using the terminal](page:elcl/using-the-terminal).

```elcl nocompile="a command-line command: type it at the ===> prompt"
GO
GO MENU(HELP)
```
