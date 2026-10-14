# Rebuilding and publishing the documentation

The site lives in `docs/site/`: plain static files, committed and ready to upload. It needs no server-side code, no
CDN and no build on the host, and it works opened straight from disk.

## Rebuilding

```
./gradlew buildDocs
```

does everything, in this order:

1. **`exportDocsData`** starts a headless server with the docs exporter (`src/docs/java/.../DocsExport.java`, in its own
   source set, so none of it ships in the mod's jar). The exporter writes the mod's real data to
   `docs/site-src/data/*.json` and stops the server: blocks, items, names, tags, every recipe, worldgen and loot, both
   config specs, the ELCL commands, messages, system values and built-ins, device types and sounds. Then
   `exportDocsScreens` composes every Terminal OS screen headless, as the layout tests do (`screens.json`).
2. **`renderDocsIcons`** starts the game client, opens a small test world (`run/docsIcons/saves/docs-icons`), renders
   every block and item icon through the game's own inventory renderer into `docs/site/assets/icons/`, writes the
   client-side tooltips, and quits. It opens a game window for about a minute. Leave it out with `-PskipDocsIcons`
   (CI always does) to reuse the committed icons.
3. **`docsSnippets`** and **`docsExamplesTest`** pull every ELCL program and DDS file out of the pages and compile each
   one with the mod's compiler (`DocsExamplesTest`). One that doesn't compile fails the build.
4. **`tools/docs/build.py build`** (Python 3.9+, standard library only) builds `docs/site/` from the data and the
   pages in `docs/site-src/pages/`, writes the search index, the old-page redirect and
   `docs/site-src/SCREENSHOTS_NEEDED.md`, and runs the checks.

The build fails if a registered block, item, command, message or config option has no documentation, if a link or
anchor is broken, if an ELCL example doesn't compile, if a page names a block, item or command that isn't registered (or
a term in `notes/denylist.txt`), or if an old anchor of the single-page reference has nowhere to go. The full report,
including icons that fell back to their texture, is `build/docs/build-report.md`.

Python is `python` on Windows and `python3` elsewhere; pass `-Pdocs_python=...` to use another. With the data already
exported, `python tools/docs/build.py build` rebuilds just the site in a few seconds.

How to write and check pages is in [AUTHORING.md](AUTHORING.md). Commit `docs/site/`, `docs/site-src/data/` and
`docs/terminal-os-elcl.html` with the change that updates them. The **Docs** workflow (`.github/workflows/docs.yml`)
runs the same build and checks on every push, warns when the committed site is out of date, and keeps the built site
as a downloadable artifact.

### Screenshots

Pages hold "Screenshot needed" placeholders where an in-game image would help. `SCREENSHOTS_NEEDED.md` lists each one
with its page, file name, what to capture and the size. Save the image as `docs/site-src/images/<file name>` and
rebuild: the page shows it in place of the placeholder, and the list ticks it off.

## Publishing to zagdrath.dev/encoded-logistics/

Upload the **contents** of `docs/site/` (not the folder itself) to the folder the site serves at
`https://zagdrath.dev/encoded-logistics/`, replacing what's there:

```
rsync -av --delete docs/site/ user@zagdrath.dev:/path/to/webroot/encoded-logistics/
```

(or the host's file manager, FTP or SFTP: the same files to the same place).

- `index.html` at the top of `docs/site/` is the home page, so `https://zagdrath.dev/encoded-logistics/` opens it as
  long as the host serves `index.html` for a folder, which every common host does by default.
- Every link in the site is relative, so it works under `/encoded-logistics/` (or any other folder) without
  configuration, and from disk.
- **The old URL keeps working.** `docs/site/terminal-os-elcl.html` is a redirect page:
  `https://zagdrath.dev/encoded-logistics/terminal-os-elcl.html#printing` (or any of the old page's anchors, including
  `#cmd-plc` and `#cmd-STRCRAFT`) goes to the page and section that now covers it. Without JavaScript it shows a list
  of links instead. Keep it in the upload.
- `docs/terminal-os-elcl.html` in the repository is the same redirect pointing into `docs/site/`, so links to the old
  file on GitHub keep working too.
- Serve `.js` as `text/javascript` and `.css` as `text/css` (hosts do by default). The site sets no cookies and stores
  only the colour theme choice in the browser.

### GitHub Pages instead

If the site is ever served from GitHub Pages, publish the `docs/site` folder (for example with
`actions/upload-pages-artifact` pointed at `docs/site`). It already contains a `.nojekyll` file so Pages serves it as
it is.
