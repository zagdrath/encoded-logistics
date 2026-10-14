---
title: Building this site
section: developers
order: 40
summary: How this documentation is generated from the mod's own data and the hand-written pages, and how to rebuild it.
---
This site is generated: the reference pages come from the mod's real data, exported by the mod itself, and the guide
pages are Markdown checked against that data. Everything lives in the repository:

| Where | What |
|---|---|
| `docs/site-src/pages/` | The hand-written pages (Markdown with shortcodes) |
| `docs/site-src/data/` | The exported data: blocks, items, recipes, tags, config, ELCL commands and messages, screens |
| `docs/site-src/notes/` | Text the code doesn't hold: item descriptions, command categories, the terms the build refuses |
| `tools/docs/` | The builder (Python 3.9 or later, standard library only) |
| `docs/site/` | The built site: plain static files, committed |

## Rebuilding {#rebuild}

```text
./gradlew buildDocs
```

runs, in order:

1. **`exportDocsData`**: starts a headless server with the docs exporter (in its own source set, `src/docs`, so none of
   it ships in the mod's jar), which writes the mod's data to `docs/site-src/data/*.json`, then composes every Terminal
   OS screen as the layout tests do.
2. **`renderDocsIcons`**: starts the game client for about a minute and renders every block and item icon through the
   game's own renderer. `-PskipDocsIcons` leaves it out and reuses the committed icons.
3. **`docsSnippets`** and **`docsExamplesTest`**: every ELCL program and DDS file on the pages is compiled with the
   mod's own compiler (`DocsExamplesTest`). One that doesn't compile fails the build.
4. **`tools/docs/build.py build`**: builds `docs/site/` from the data and the pages, writes the search index and the
   redirect page for the old single-page reference, lists the screenshots still needed, and runs the checks.

The build fails if a registered block, item, command, message or config option has no documentation, if a link or
anchor is broken, if an example doesn't compile, or if a page names something that isn't registered. With the data
already exported, `python tools/docs/build.py build` rebuilds just the site in seconds.

## Writing and checking a page {#writing}

`docs/site-src/AUTHORING.md` explains the page format and the shortcodes. To check one page or section without
writing the site:

```text
python tools/docs/build.py check pages/control
```

Publishing the built site, and the old URL's redirect, are covered in
[docs/site-src/PUBLISHING.md](docs/site-src/PUBLISHING.md).
