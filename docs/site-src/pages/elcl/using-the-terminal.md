---
title: Using the terminal
section: elcl
order: 10
summary: The green screen's layout, the keys and the mouse, the command line, the F4 prompter, F1 help windows and phosphor colours.
legacy: [using]
---
Every Terminal OS screen works the same way: a header, a body, a command line, a message line and a row of function
keys. Learn them once and every screen follows. This page covers the screen itself; {{page elcl/screens}} goes
through the screens one by one.

{{screenshot file="elcl/terminal-desk-main-menu.png" alt="The Terminal OS Main Menu open at a Terminal Desk" show="A player seated at a Terminal Desk with the Terminal OS open on the Main Menu, green phosphor, the GUI panel centred with its title and the 80 by 24 screen in its well" angle="Sit in a Swivel Chair at the desk and open it; GUI scale 2, window 1920x1080" size="1280x720"}}

## Screen layout {#screen-layout legacy="screen-layout"}

Every green screen (the Terminal Desk, the Integrated Midrange System's console, and the machines: the Midrange control
panels, Card Punch, Card Reader, Line Printer, Disk and Tape Drives, PLCs) sits in the same panel as the mod's other
machine GUIs: the block's name at the top, and the 80 by 24 screen in a recessed well below it, one screen pixel to a
GUI pixel, crisp, with a 6-pixel margin of the screen's background round the text. The panel is drawn at the largest
whole scale that keeps it within about 60% of the window's width and 70% of its height, centred. The game's HUD
(crosshair, hotbar, health and the rest) is hidden while it's open. The screen stays blank for a moment when it opens,
until the system's details arrive from the server.

{{layout 02_main_menu caption="The Main Menu's first page, showing every part of the frame."}}

| Row | What's there |
|---|---|
| 0 | The screen's id at the left (`MAIN`, `WRKLIB`...), its title centred and bright, and `System:` and the system's name (the `SYSNAME` system value) at the right; `*OFFLINE` without a network. |
| 1 | The game clock at the right: `Day 2  07:14:22` (the game day, and the time of day with day start at 06:00). |
| 2–19 | The screen's body. Lists show *More...* or *Bottom* on row 20 at the right; the Main Menu on row 18. |
| 20 | The prompt over the command line: *Selection or command* on menus, *Parameters or command* on lists. |
| 21 | The command line, `===>` and a 72-character field (it holds up to 216 characters, scrolling as you type). Screens without a command line (the prompter, displays, Sign On) leave it out. Command Entry has a three-row command line on rows 19–21, under its prompt *Type command, press Enter.* on row 18. |
| 22 | The message line (bright). `MW` (messages waiting) appears at its right end, column 76, while you have unread messages in your queue; it goes once you display them. |
| 23 | The function keys this screen takes, bright. Clicking a key's label presses it. |

Input fields are bright and underlined. Pop-up windows (help, confirmations, value lists, small forms) are drawn in a
box over the screen; while one is open only its fields take the cursor.

## Keys {#keys legacy="keys"}

Function keys act when the key is released. <kbd>Shift</kbd> adds 12 to F1–F12, so F13 is <kbd>Shift</kbd>+<kbd>F1</kbd>
and F24 is <kbd>Shift</kbd>+<kbd>F12</kbd>; real F13–F24 keys work too. While the terminal is open, the game's
fullscreen and screenshot bindings are switched off if they're bound to function keys, and given back when it closes.

| Key | What it does |
|---|---|
| <kbd>Enter</kbd> | If the command line has text: a menu option number on a menu, an editor command in the editor, otherwise the text runs as a command. If it's empty: the screen's own action (take the options typed in a list, apply the editor's line commands, sign on...). In a window, the window's action. |
| <kbd>Esc</kbd> | Closes the top window, else goes back one screen. On the Main Menu or Sign On it closes the terminal. |
| <kbd>F1</kbd> Help | Help for the field the cursor is in, or for the screen, in a window. See [help windows](#help-windows). |
| <kbd>F3</kbd> Exit | Closes the terminal from most screens. In the prompter it returns without the command; in the editor it leaves the editor (asking to save if there are changes); in a window it closes the window. |
| <kbd>F4</kbd> Prompt | On the command line with a command typed: opens the [prompter](#prompter) for it. On a prompter field: its value list, or the nested command's prompter. On an editor source line: prompts that statement. Elsewhere: a hint for the field on the message line. |
| <kbd>F5</kbd> Refresh | Clears the message line and fetches the screen's data again (on the prompter: resets the values to their defaults; in Update Data's entry mode: clears the fields). |
| <kbd>F6</kbd> Create / Add | Prompts the screen's create command: {{cmd CRTLIB}} on Work with Libraries, {{cmd CRTMBR}} on Work with Members, {{cmd CRTPF}} on Work with Files, {{cmd ADDJOBSCDE}} and {{cmd ADDTRGEVT}} on the schedule and trigger lists, {{cmd ADDACLE}} on Work with ACLs. On Work with Inventory it opens Deposit Items (and there deposits everything); in Update Data it starts entry mode. |
| <kbd>F7</kbd> / <kbd>F8</kbd> | Update Data: the record before / after. |
| <kbd>F9</kbd> | Opens Command Entry. On Command Entry: *Retrieve*, bringing back the previous command (press again for the one before). |
| <kbd>F10</kbd> | Prompter: show or hide *Additional parameters*. Editor: cursor to the command line. Display Job Log: detailed messages. On other screens a second view: Work with Jobs' history, Display Neighbours' route table, Display Optical Status's channels, Work with Directory's lookups, Work with Exchange's sessions, Update Data's change mode. |
| <kbd>F11</kbd> | Lists: sort (cycles through the screen's orders). Editor: full screen. Display Messages: remove all. Display Route Table: neighbours. Work with Hardware: empty bays too. Work with Exchange: requests only. Update Data: delete the record. In the End confirmation of Work with Active Jobs: end immediately. In the editor's exit confirmation: exit without saving. |
| <kbd>F12</kbd> Cancel | Back one screen (closes the top window if one is open). In the editor, first drops a pending block command. |
| <kbd>F13</kbd> Clear | Command Entry: clears the commands and messages shown (F9 can still bring back earlier commands). |
| <kbd>F15</kbd> | Editor: column ruler on or off. |
| <kbd>F16</kbd> / <kbd>F17</kbd> | Editor: repeat the last FIND / the last CHANGE. |
| <kbd>F19</kbd> / <kbd>F20</kbd> | Shift the columns shown left / right: the editor (columns 1–72 or 9–80), and Display Spooled File, Display Physical File Member and Display Report (40 columns at a time). |
| <kbd>F24</kbd> More keys | Opens [More Keys and Settings](page:elcl/screens#scr-morekeys) (the key list and the phosphor choice). |
| <kbd>Tab</kbd> / <kbd>Shift</kbd>+<kbd>Tab</kbd> | Next / previous field. On Command Entry with text on the command line, <kbd>Tab</kbd> completes the last word instead (see [below](#command-line)). |
| <kbd>Up</kbd> / <kbd>Down</kbd> | The cursor a row up / down, keeping its column, into the field there (or the nearest one on the nearest row that has any; past the last row it goes round to the first). |
| <kbd>Left</kbd> <kbd>Right</kbd> <kbd>Home</kbd> <kbd>End</kbd> | Move the cursor in the field. |
| <kbd>Backspace</kbd> / <kbd>Delete</kbd> | Delete before / at the cursor; the rest of the field closes up. |
| <kbd>Insert</kbd> | Switches between overwrite (the default: typing replaces the character at the cursor) and insert (typing pushes the rest along). Applies to every field. |
| Field Exit: <kbd>Ctrl</kbd>+<kbd>Enter</kbd> or keypad <kbd>Enter</kbd> | Clears the field from the cursor to its end and moves to the next field. |
| Field Advance | Typing into a field's last position moves on to the next field. |
| <kbd>Page Up</kbd> / <kbd>Page Down</kbd>, mouse wheel | Roll lists, pages, windows and Command Entry's history. |
| Paste (<kbd>Ctrl</kbd>+<kbd>V</kbd>) | Pastes the clipboard into the field, as if typed in insert mode (in the editor, several lines go in as lines). |

Not every screen takes every key: row 23 lists the ones it does. On Sign On, F9 and F24 do nothing. Only printable
ASCII characters (space to `~`) can be typed. Some fields fold letters to upper case (names) or take only digits,
signs, a decimal point and commas (numbers).

## Mouse {#mouse legacy="mouse"}

- Clicking a function key's label on row 23 presses that key.
- Clicking a field puts the cursor there.
- Menus (Main Menu, Work with Job): clicking an option types its number on the command line; double-clicking goes
  there.
- Lists: clicking a row types the screen's default option in its Opt column; double-clicking does it.
- The wheel rolls like Page Up / Page Down.

## The command line {#command-line legacy="command-line"}

Every screen with `===>` takes commands. What you type is handled in this order:

1. On a menu, a number is the menu option (one that isn't there says *Option N is not on this menu.*). In the
   editor, editor commands (`FIND`, `SAVE`...) come first.
2. `clear` (any case) clears Command Entry's history (*Command history cleared.*).
3. A command that opens a screen ({{cmd WRKLIB}}, `WRKMBR LIB(ZAGLIB)`, {{cmd DSPMSG}}, `GO MAIN`, {{cmd SIGNOFF}}...;
   see {{page elcl/screens}}) is checked against its definition and opens the screen on this desk.
4. Anything else goes to the server and runs as an ELCL command in your interactive job.

An ELCL command on the command line is parsed, checked that it may run there ({{msg ELC0106}} for a statement only a
program can use), checked against its definition and for Firewall authority, then run. Its messages are shown as
`ID  text` (escape and completion messages bright), the values of any `RTN*` parameters are listed as `RTNLVL = 7`,
and the last message goes to the message line. If the command produced output and you're not on Command Entry,
Command Entry opens to show it. The command and its messages also go in your interactive job's log (screen commands,
which are handled at the desk, don't).

{{cmd CALL}} on the command line starts the program in your interactive job and answers at once ({{msg ELC0108}},
`Program LIB/NAME running in job ...`); its messages come to Command Entry when it ends. {{cmd DLYJOB}} typed on a
command line has nothing to hold up and completes at once.

There are no variables on the command line (an `&name` is {{msg ELC0002}}), but expressions of literals work, for
example `SNDMSG MSG('Shift' *BCAT 'change')`. `RTN*` parameters are optional on the command line even when a program
would have to give them. Language statements such as `DCL`, `IF` or `CHGVAR` answer {{msg ELC0106}} there. A
{{cmd CHGSYSVAL}} typed at a desk takes effect on that desk at once (the header's `SYSNAME`, `PHOSPHOR`, `SECLVL`);
other desks pick it up when they're next opened.

**Tab completion** (Command Entry only) completes the first word from the ELCL command names and later words from
the network's item names. One match fills in the word; several are listed on the message line.

## The prompter (F4) {#prompter legacy="prompter"}

Type a command name (with or without some parameters) on a command line and press F4. The prompter shows one row per
parameter, built from the command's definition: the same parameters its page in the
[command reference](page:elcl/commands/index) lists.

{{layout 06_prompter caption="The prompter for STRCRAFT."}}

- The title is the command's description and name. Each row has the label with dot leaders (bright when the parameter
  is required), the keyword (dim), the input field (the parameter's length, at most 24 shown; longer values scroll)
  and a hint (dim) such as `1-999999`, `*FAIL, *PARTIAL`, `Name, F4 for list` or `*CHAR variable`. A long hint wraps
  onto the next row.
- Required parameters come first. The others sit under **Additional Parameters**, shown with F10 or as soon as any of
  them has a value.
- A list parameter (hint *+ for more values*) takes several values separated by blanks; typing just `+` and pressing
  Enter opens a window with four value fields. Values that go together can be grouped in brackets, as
  `PLYMID MAP((1 SPK01) (10 SPK04))`: a group's values count as if listed one by one.
- **F4 on a field** opens its value list, *Select Value - label*: special values first, then what the server lists
  for that kind of parameter. Items in the network match what's typed in the field by name or id, sorted by name, up
  to 200; there are lists of devices, libraries, jobs and system values, and of members, programs and files (from the
  library typed in the field, or down your library list). Type `1` next to one and press Enter (or double-click it).
  On a field with no list, F4 shows its hint on the message line. On a command parameter (`SBMJOB CMD`,
  `ADDJOBSCDE CMD`, or `IF THEN` in the editor) F4 opens the prompter for the nested command instead.
- **Enter** checks the values: a missing required value ({{msg ELC0102}}), a value not valid for a parameter
  ({{msg ELC0103}}) or out of range ({{msg ELC0004}}) is shown on the message line and the cursor goes to that field.
  When it's clean the prompter builds the command (keywords in order, values left at their defaults left out, text
  quoted where needed).
- From Command Entry, the built command is put back on the command line for you to press Enter. From any other
  screen's command line it runs straight away. From the editor it replaces the statement in the source.
- F5 resets every value to its default. F3 and F12 return without a command. F1 on a field explains that parameter:
  what it takes, its special values, its default and whether it's required; F1 elsewhere lists all the command's
  parameters (required ones marked `*`).

## Help windows (F1) {#help-windows legacy="help-windows"}

F1 opens a window titled *Screen title - Help* over the current screen with help for the field under the cursor, or
the screen's help if the field has none. In lists, the Opt column has its own help listing the options. In the
prompter, help comes from the command's definition. `GO HELP` on a command line opens the same window. The window's
keys are *F3=Exit help   F12=Cancel*; Page Up / Page Down scroll it (*More...* and *Bottom* say where you are). A
screen without help text says *No help is available for this screen. Press F12 to return.*

The screen help on {{page elcl/screens}} follows these texts.

## Phosphor colours {#phosphor legacy="phosphor"}

The screen can be drawn in green, amber or white phosphor. Each player chooses on
[More Keys and Settings](page:elcl/screens#scr-morekeys) (F24): `Green`, `Amber`, `White` or `*SYSVAL` (type the word,
its start, or 1–4). The choice is kept in the client config as {{config terminal.phosphor default}}. `*SYSVAL` follows
the system value `PHOSPHOR` (`*GREEN`, `*AMBER` or `*WHITE`; default `*GREEN`), so a server admin can set every
terminal's colour on a system at once. A change made at a desk takes effect on that desk straight away.

{{screenshot file="elcl/phosphor-colours.png" alt="The same Terminal OS screen in green, amber and white phosphor" show="Three captures of Work with Libraries side by side, one each in Green, Amber and White phosphor (set on F24 More Keys)" angle="Seated at a Terminal Desk; GUI scale 2; crop each to the screen well" size="1600x500"}}
