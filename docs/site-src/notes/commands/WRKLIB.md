Work with Libraries lists every library on the system you're signed on to, with its type (`*PROD`, `*TEST`, or `*SYS`
for `ELSYS`) and its text. It's also option 5 on the main menu. Type an option next to a library and press Enter:

| Option | What it does |
|---|---|
| `2` | Change its text and public authority (`*USE` or `*CHANGE`) in a window. Runs {{cmd CHGLIB}}. |
| `4` | Delete it, after you confirm. Runs {{cmd DLTLIB}}. |
| `5` | Display its attributes: type, text, owner, public authority, how many members it holds, their size in characters, and when it was made. |
| `12` | Work with its members, then its files ({{cmd WRKMBR}}). |

F6 prompts {{cmd CRTLIB}} to make a new library. `ELSYS` holds the samples and the system files and is read-only
(changing or deleting it is {{msg ELC0205}}); `ELGPL` is the general purpose library, open to everyone. To work with
a library's database files alone, use {{cmd WRKF}}.

{{layout 03_wrklib}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKLIB
```

Libraries, members, programs and who may change them are explained in
[Libraries and the editor](page:elcl/libraries-and-editor#libs-members).
