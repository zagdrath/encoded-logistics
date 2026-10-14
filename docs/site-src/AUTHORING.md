# Writing the documentation pages

How the hand-written pages in `docs/site-src/pages/` are written. The build (`./gradlew buildDocs`, or
`python tools/docs/build.py build` once the data is exported) turns them into `docs/site/`. See `PUBLISHING.md` for
the build itself.

## Rules

- **Only what the code does.** Every statement is checked against the code (`src/main/java`), the data the export
  writes (`docs/site-src/data/`) or a game test. If it isn't registered and working, it isn't documented, not even
  as "planned" or "not yet". `INVENTORY.md` lists what the older docs describe that doesn't exist. The terms in
  `notes/denylist.txt` fail the build if a page uses them.
- **The review notes are a starting point, not a source.** `review-notes/` has file references for its claims. Read
  the code they point to before you write a number or a rule down.
- **Numbers that are config options come from the config.** Write `{{config rack.firewallDrain default}}` (it prints
  the option, linked, and its default) or `{{default rack.firewallDrain}}`. Don't write the default as plain text: it
  goes stale when the default changes.
- **Name things through shortcodes** so the build can check they exist: `{{item drive_bay}}`, `{{cmd STRCRAFT}}`,
  `{{msg ELC1201}}`. A misspelt or unregistered name fails the build.
- Players and server admins first. Say what something does and how to use it, then the details. Second person ("you"),
  plain and direct, British spelling as in the mod's own text ("colour", "neighbour").
- Use the mod's in-game names exactly as `en_us.json` has them ("Card Punch", not "Keypunch"; "Alarm Strobe", not
  "siren").

## A page

```markdown
---
title: Lanes
section: networks
order: 30
summary: How a network shares its lanes out among its devices, and what to do when it runs out.
covers: [encodedlogistics:segment_isolator]
---
Body text in Markdown.
```

- `title` (required): the page heading and the sidebar label. `nav:` gives a shorter sidebar label.
- `section`: one of the ids in `sections.json`. Each section's landing page is `pages/<section>/index.md`.
- `order`: the position in the section's sidebar and its previous/next links (low first).
- `summary`: one sentence, shown under the title, in search results and the page's meta description.
- `covers`: blocks and items the page documents, by id or glob (`encodedlogistics:cage_light_*`), besides the ones
  it names with shortcodes. **Every block and item has to be documented on at least one page** (named with a
  shortcode or listed in `covers`), or the build fails.
- `legacy`: old anchors of the single-page reference (`notes/legacy-anchors.txt`) this whole page replaces.
- `toc: false` turns off the "On this page" box (shown when a page has four or more `##`/`###` headings).

## Markdown

Headings `##` to `####` (the page title is the `#`); `{#id}` sets a heading's anchor, and `legacy="old-a old-b"` in
the same braces records which old single-page anchors this heading replaces:

```markdown
## The prompter (F4) {#prompter legacy="prompter"}
```

Paragraphs, `-` and `1.` lists (indent two spaces to nest), `| pipe | tables |`, `**bold**`, `*italic*`, `` `code` ``,
`[links](page:networks/lanes#budgets)`, `<kbd>F4</kbd>` and other inline HTML. Callouts:

```markdown
> [!NOTE]
> Text.

> [!WARNING] Breaking a rack drops everything in it
> Text.
```

`> [!TIP]` too. A line starting with an HTML block tag (`<div>`, `<table>`) passes through as it is.

### Links

- `[text](page:storage/drives)` and `[text](page:storage/drives#tiers)`: another page, by its path under `pages/`
  without `.md`. A missing page or anchor fails the build.
- `[text](item:drive_bay)`, `[text](cmd:STRCRAFT)`: an item's or a command's reference page.
- `{{page storage/drives}}` links with the page's own title.
- Anything else relative is a file in the repository, linked on GitHub.

## Shortcodes

| Shortcode | What it gives |
|---|---|
| `{{item drive_bay}}` | The item's icon and name, linked to its page. `label="..."` changes the text, `icon=no` drops the icon. Vanilla and other mods' items: `{{item minecraft:chest}}` (not linked). |
| `{{items encodedlogistics:cage_light_*}}` | A list of every matching block and item. `style=inline` for a comma-separated run. |
| `{{icon drive_bay}}` | The icon alone (`size=64`). |
| `{{recipe drive_bay}}` | Every recipe that makes it, drawn as grids. `{{recipe-id encodedlogistics:some_recipe}}` for one recipe. |
| `{{cmd STRCRAFT}}` | The command, linked to its page. `{{cmd STRCRAFT ITEM(...)}}` shows more text. |
| `{{msg ELC1201}}` | A message ID, linked to the message reference. |
| `{{config rack.firewallDrain}}`, `{{config rack.firewallDrain default}}`, `{{default rack.firewallDrain}}` | A config option linked to its reference entry; with its default; or just the default. |
| `{{table NAME}}` | A generated table: `config section=rack` or `config keys=a.b,c.d`, `rack-devices`, `device-types`, `sysvals`, `triggers`, `builtins`, `chassis-parts`, `sounds`, `main-menu`, `screens`, `creative-tabs`, `commands CATEGORY`. |
| `{{screen WRKLIB}}` | The Terminal OS screen as the code draws it with no data (`screens.json`). |
| `{{layout 03_wrklib}}` | A screen's design layout with sample data (`src/test/resources/layouts`, checked by LayoutTest). |
| `{{example RESTOCK.elclp}}` | A program from `docs/site-src/examples/` (compiled like any other). |
| `{{screenshot file="racks/front.png" alt="..." show="..." angle="..." size="1600x900" caption="..."}}` | A screenshot: the image if `docs/site-src/images/<file>` exists, otherwise a "Screenshot needed" placeholder. `file`, `alt` and `show` are required. Every one is listed in `SCREENSHOTS_NEEDED.md`. |
| `{{version}}` | The mod version the site was built from. |

Use a screenshot only where an image is much clearer than the icons and text: multiblocks and racks in the world,
GUIs, the Terminal OS screens in the game, Display Panels, setups in guides. `show` says what to capture, `angle`
where to stand. Paths under `images/` follow the page (`racks/server-rack-front.png`).

## Code

````markdown
```elcl
SNDMSG MSG('Restock done') TOUSR(*SYSOPR)
```
````

Every `elcl` block is **compiled by the mod's own compiler** (`DocsExamplesTest`), and the build fails if one doesn't
compile cleanly. A block without `PGM` is wrapped in `PGM` ... `ENDPGM` first. Options after `elcl`:

- `target=PLC` or `target=PLC_LOCAL`: compile it for a PLC (cabled to a network, or on its own).
- `warnings`: a warning (severity 10) is all right; errors still fail.
- `nocompile="why"`: not compiled, with the reason (a fragment showing only syntax, a command typed at the `===>`
  prompt rather than in a program). Use it sparingly.
- `name=RESTOCK`: the example's name in build reports.

` ```dds file=MYLIB/ITEMLOG ` is a physical file definition: it's compiled, and programs in the pages can `DCLF` it.
` ```term ` is a green-screen block (text as it is). Any other language is a plain code block.

## Checking your pages

```
python tools/docs/build.py check pages/networks
```

lists the problems mentioning that path, without writing the site (several people can run it at once). To compile a
page's ELCL too:

```
python tools/docs/build.py check pages/elcl/language build/docs/snippets-language
./gradlew docsExamplesTest -PdocsSnippetsDir=build/docs/snippets-language
```

`./gradlew buildDocs` runs everything.

## Notes files (`notes/`)

Data the generated pages need that the code doesn't hold as text:

- `items.txt`: one description per block and item (globs allowed, `{name}` for the entry's name), with optional
  `works:` (what it goes with) and `page:` lines. The build fails if an item has none.
- `command-categories.json`: every ELCL command in a category (the build fails if a command is in none).
- `commands/NAME.md`: optional extra text for a command's page (what it does in detail, errors, examples).
- `trigger-events.json`, `builtins.json`, `message-ranges.json`, `data-components.json`, `recipe-types.json`.
- `denylist.txt`: names of things older docs describe that the mod doesn't have.
- `legacy-anchors.txt`: the old single page's anchors, each of which has to be claimed by a heading (`legacy=`).
