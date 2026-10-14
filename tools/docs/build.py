"""Builds the Encoded Logistics documentation site (docs/site) from the exported data and the hand-written pages.

    python tools/docs/build.py build      build docs/site and run the checks (exit 1 if any fail)
    python tools/docs/build.py snippets   write every ELCL program in the pages to build/docs/snippets for
                                          DocsExamplesTest to compile
    python tools/docs/build.py check [PATH [FOLDER]]
                                          list the problems that mention PATH (a page file, say) without writing
                                          the site; with FOLDER, also write PATH's ELCL programs there to compile:
                                          ./gradlew docsExamplesTest -PdocsSnippetsDir=FOLDER

./gradlew buildDocs runs the export, the icons, both of these and the compile test in order; see
docs/site-src/PUBLISHING.md. Python 3.9 or later, standard library only.
"""

import hashlib
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from sitegen import checks, legacy, md, paths, search  # noqa: E402
from sitegen.elclref import ElclReference  # noqa: E402
from sitegen.recipes import RecipeRenderer  # noqa: E402
from sitegen.reference import Reference  # noqa: E402
from sitegen.shortcodes import Shortcodes  # noqa: E402
from sitegen.site import Page, Site  # noqa: E402


def assemble():
    """Every page, converted: the site in memory."""
    site = Site()
    site.load_hand_pages()
    recipes = RecipeRenderer(site)
    shortcodes = {}

    def sc_for(page):
        if page.id not in shortcodes:
            shortcodes[page.id] = Shortcodes(site, page, recipes)
        return shortcodes[page.id]

    ref = Reference(site, recipes, sc_for)
    ref.item_pages()
    ref.recipes_page()
    ref.tags_page()
    ref.config_page()
    device_types = ref.simple_page("reference/device-types", "Device types", "Every kind of device a network names, with its type code and first name.",
                    6, "device_types", "Work with Devices, `WRKDEV` and every ELCL `DEV()` parameter know a device by its name: its type code and a "
                    "number, given in the order devices join (`UPS01`, `UPS02`). Rename one with {{cmd RNMDEV}}.")
    device_types.legacy["device-names"] = ""
    ref.simple_page("reference/sounds", "Sounds", "Every sound event the mod registers, with its subtitle.", 7, "sounds",
                    "Resource packs can replace these through `assets/encodedlogistics/sounds.json`.")
    ref.screens_page()
    ref.data_components_page()
    changelog = ref.changelog_page()
    elcl = ElclReference(site, sc_for)
    elcl.pages()
    site.add(Page("search", {"title": "Search", "section": "search", "hidden": "true"}, body='<div id="search-page" aria-live="polite"></div>',
                  kind="search"))

    def convert(page, source):
        sc = sc_for(page)
        converter = md.Converter(sc, sc.fence, sc.link)
        body = converter.convert(source)
        page.headings.extend(converter.headings)
        for _, ident, _, attrs in converter.headings:
            for old in attrs.get("legacy", "").split():
                page.legacy[old] = ident
        return body

    for page in list(site.pages.values()):
        if page.source is not None:
            page.body = convert(page, page.source)
            legacy_meta = page.meta.get("legacy", [])
            for old in legacy_meta if isinstance(legacy_meta, list) else [legacy_meta]:
                page.legacy[old] = ""
    for page in list(site.pages.values()):
        if page.kind == "command":
            page.body = elcl.command_body(page, convert)
        elif page.kind == "commands-index":
            page.body = elcl.index_body(page)
        elif page.kind == "messages":
            page.body = elcl.messages_body(page)
    ref.fill_recipes_page()
    ref.fill_item_pages()
    for section in site.sections:
        index = section.get("index", section["id"] + "/index")
        if section.get("nav") is not False and index not in site.pages:
            site.problems.append("section %s has no index page (%s.md)" % (section["id"], index))
    return site


def write_snippets(site, folder=None, only=""):
    folder = folder or paths.SNIPPETS
    if os.path.exists(folder):
        shutil.rmtree(folder)
    os.makedirs(folder)
    manifest = {"programs": [], "files": []}
    for k, s in enumerate(site.snippets):
        if only and only not in s["page"] and s["kind"] == "program":
            continue
        name = "%03d-%s" % (k, s.get("name") or s.get("id"))
        file = name.replace("/", "-") + (".pf" if s["kind"] == "file" else ".elclp")
        with open(os.path.join(folder, file), "w", encoding="utf-8", newline="\n") as f:
            f.write(s["code"] + "\n")
        if s["kind"] == "file":
            manifest["files"].append({"name": s["name"], "file": file, "page": s["page"]})
        else:
            manifest["programs"].append({"name": s["name"], "file": file, "page": s["page"], "wrap": s["wrap"], "target": s["target"],
                                         "warnings": s["warnings"]})
    with open(os.path.join(folder, "snippets.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(manifest, f, indent=1)
    return manifest


def clean_output():
    """Empties docs/site except the rendered icons (renderDocsIcons writes those)."""
    os.makedirs(paths.SITE, exist_ok=True)
    for name in os.listdir(paths.SITE):
        full = os.path.join(paths.SITE, name)
        if name == "assets":
            for inner in os.listdir(full):
                if inner != "icons":
                    target = os.path.join(full, inner)
                    shutil.rmtree(target) if os.path.isdir(target) else os.remove(target)
            continue
        shutil.rmtree(full) if os.path.isdir(full) else os.remove(full)


def write_site(site):
    with open(os.path.join(paths.TEMPLATES, "page.html"), encoding="utf-8") as f:
        template = f.read()
    for page in site.pages.values():
        out = os.path.join(paths.SITE, page.url.replace("/", os.sep))
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "w", encoding="utf-8", newline="\n") as f:
            f.write(site.render(page, template))
    assets = os.path.join(paths.SITE, "assets")
    for name in os.listdir(paths.ASSETS):
        shutil.copyfile(os.path.join(paths.ASSETS, name), os.path.join(assets, name))
    for file in site.copy_images:
        target = os.path.join(assets, "images", file.replace("/", os.sep))
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(os.path.join(paths.IMAGES, file.replace("/", os.sep)), target)
    with open(os.path.join(paths.SITE, ".nojekyll"), "w") as f:
        f.write("")


def write_screenshot_list(site):
    shots = [s for p in sorted(site.pages.values(), key=lambda p: p.url) for s in p.screenshots]
    needed = [s for s in shots if not s["exists"]]
    lines = ["# Screenshots needed", "",
             "Generated by `tools/docs/build.py` from the `{{screenshot}}` placeholders in the pages: don't edit by hand.",
             "Save each image under `docs/site-src/images/` with the file name given; the next build uses it in place of the",
             "placeholder. PNG or JPEG, at the size given (or larger, same shape). Use the default resource pack, GUI scale 2 for",
             "screens (4 for close-ups), clear weather at noon, and the HUD hidden (F1) for world shots unless it says otherwise.",
             "",
             "%d placeholders; %d still need an image." % (len(shots), len(needed)), ""]
    current = None
    for s in shots:
        if s["page"] != current:
            current = s["page"]
            lines += ["## %s (`%s.html`)" % (s["title"], s["page"]), ""]
        lines.append("- [%s] **`images/%s`**%s" % ("x" if s["exists"] else " ", s["file"], " (%s)" % s["size"] if s.get("size") else ""))
        lines.append("  - Shows: %s" % s["show"])
        if s.get("angle"):
            lines.append("  - Angle: %s" % s["angle"])
        lines.append("  - Alt text: %s" % s["alt"])
    lines.append("")
    with open(os.path.join(paths.SRC, "SCREENSHOTS_NEEDED.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))
    return len(shots), len(needed)


def report(site, stats):
    lines = ["# Documentation build report", ""]
    lines += ["- %s: %s" % (k, v) for k, v in stats.items()]
    icons = os.path.join(paths.ICONS, "icons.json")
    if os.path.exists(icons):
        with open(icons, encoding="utf-8") as f:
            rendered = json.load(f)
        if rendered.get("failed"):
            lines += ["", "## Icons the game didn't render", ""] + ["- " + x for x in rendered["failed"]]
    if site.icon_fallbacks:
        lines += ["", "## Icons using the texture instead", ""] + ["- " + x for x in sorted(set(site.icon_fallbacks))]
    if site.icon_missing:
        lines += ["", "## No icon at all", ""] + ["- " + x for x in sorted(set(site.icon_missing))]
    if site.warnings:
        lines += ["", "## Warnings", ""] + ["- " + w for w in site.warnings]
    if site.problems:
        lines += ["", "## Problems (the build fails)", ""] + ["- " + p for p in site.problems]
    os.makedirs(paths.BUILD, exist_ok=True)
    with open(os.path.join(paths.BUILD, "build-report.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    return "\n".join(lines)


def main(argv):
    command = argv[1] if len(argv) > 1 else "build"
    if command == "check":
        # Read-only: assembles the site in memory and lists the problems whose text mentions the given path (or all),
        # without touching docs/site. Several people (or agents) can run it at once.
        only = argv[2] if len(argv) > 2 else ""
        site = assemble()
        checks.check_mentions(site)
        mapping = legacy.anchor_map(site)
        for anchor in checks.legacy_anchors():
            if anchor not in mapping:
                site.problems.append("the old page's #%s has no new home (legacy-anchors.txt)" % anchor)
        problems = [p for p in site.problems if only in p]
        for p in problems:
            print("- " + p)
        print("%d problem(s)%s" % (len(problems), " mentioning " + only if only else ""))
        if len(argv) > 3:
            write_snippets(site, argv[3], only)
        return 1 if problems else 0
    if command == "snippets":
        site = assemble()
        manifest = write_snippets(site)
        print("Wrote %d ELCL programs and %d files to %s" % (len(manifest["programs"]), len(manifest["files"]), paths.SNIPPETS))
        return 0
    if command != "build":
        print(__doc__)
        return 2
    clean_output()
    site = assemble()
    write_site(site)
    entries = search.build(site)
    mapping = legacy.write(site)
    shots, needed = write_screenshot_list(site)
    checks.check_mentions(site)
    broken = checks.check_links(site)
    stats = {
        "Mod version": site.version,
        "Pages": len(site.pages),
        "Hand-written pages": sum(1 for p in site.pages.values() if p.kind == "page"),
        "Block and item pages": sum(1 for p in site.pages.values() if p.kind == "item"),
        "Command pages": sum(1 for p in site.pages.values() if p.kind == "command"),
        "Search entries": entries,
        "Old anchors redirected": len(mapping),
        "ELCL programs compiled": len([s for s in site.snippets if s["kind"] == "program"]),
        "Screenshots": "%d placeholders, %d still needed (docs/site-src/SCREENSHOTS_NEEDED.md)" % (shots, needed),
        "Broken links": broken,
    }
    text = report(site, stats)
    print(text)
    if site.problems:
        print("\n%d problem(s): the documentation build failed. Full report: build/docs/build-report.md" % len(site.problems))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
