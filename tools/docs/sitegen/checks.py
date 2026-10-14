"""The build's checks on the finished site: every internal link and anchor resolves, the pages mention nothing that
isn't registered, and every old anchor of the single-page reference has a new home."""

import html
import os
import re
import urllib.parse

from . import paths

HREF = re.compile(r'\b(?:href|src)="([^"]*)"')
ID = re.compile(r'\bid="([^"]+)"')


def site_files():
    for root, _, files in os.walk(paths.SITE):
        for name in files:
            if name.endswith(".html"):
                yield os.path.join(root, name)


def check_links(site):
    ids = {}
    texts = {}
    for path in site_files():
        with open(path, encoding="utf-8") as f:
            text = f.read()
        texts[path] = text
        ids[os.path.normcase(os.path.abspath(path))] = set(ID.findall(text))
    broken = 0
    for path, text in texts.items():
        rel_path = os.path.relpath(path, paths.SITE).replace(os.sep, "/")
        # Generated redirect scripts and the data-cycle JSON aren't links.
        for href in HREF.findall(text):
            href = html.unescape(href)
            if not href or href.startswith(("http://", "https://", "mailto:", "data:", "javascript:")):
                continue
            target, _, anchor = href.partition("#")
            target = urllib.parse.unquote(target.split("?")[0])
            full = os.path.normpath(os.path.join(os.path.dirname(path), target)) if target else path
            key = os.path.normcase(os.path.abspath(full))
            if not os.path.exists(full):
                site.problems.append("broken link in %s: %s" % (rel_path, href))
                broken += 1
                continue
            if anchor and full.endswith(".html") and anchor not in ids.get(key, set()):
                site.problems.append("broken anchor in %s: %s" % (rel_path, href))
                broken += 1
    return broken


def check_mentions(site):
    """Hand-written text can't name what the mod doesn't have: the terms in notes/denylist.txt (features the older
    docs describe that aren't implemented), and command-like `NAME PARAM(...)` code that isn't a registered command."""
    deny = []
    path = os.path.join(paths.NOTES, "denylist.txt")
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#"):
                    deny.append(line)
    sources = []
    for page in site.pages.values():
        if page.source and page.kind == "page":
            sources.append((page.file, page.source))
        if getattr(page, "notes_source", None):
            sources.append((page.file, page.notes_source))
    for name in ("items.txt", "trigger-events.json", "builtins.json", "message-ranges.json", "data-components.json", "recipe-types.json"):
        full = os.path.join(paths.NOTES, name)
        if os.path.exists(full):
            with open(full, encoding="utf-8") as f:
                sources.append(("docs/site-src/notes/" + name, f.read()))
    for file, text in sources:
        for term in deny:
            # Whole words only: "TerminalCommand" shouldn't catch the real class TerminalCommands.
            pattern = (r"\b" if term[0].isalnum() else "") + re.escape(term) + (r"\b" if term[-1].isalnum() else "")
            if re.search(pattern, text, re.I):
                site.problems.append("%s mentions '%s', which the mod doesn't have (notes/denylist.txt)" % (file, term))
        for match in re.finditer(r"`([A-Z][A-Z0-9]{2,9}) [A-Z]+\(", text):
            if match.group(1) not in site.data.commands:
                site.problems.append("%s: `%s ...` looks like an ELCL command, but none is registered by that name" % (file, match.group(1)))
        for match in re.finditer(r"\bencodedlogistics:([a-z0-9_/*]+)", text):
            ident = "encodedlogistics:" + match.group(1)
            if "*" in ident:
                continue  # a glob: covers and the notes check their globs match something
            if ident not in site.data.entries and not any(ident.startswith(p) for p in ("encodedlogistics:block.", "encodedlogistics:storage_drives")):
                if ident not in site.data.item_tags and ident not in site.data.block_tags and not _is_other_id(site, ident):
                    site.problems.append("%s names %s, which isn't a registered block, item or tag" % (file, ident))


def _is_other_id(site, ident):
    data = site.data
    others = set(data.registry["menuTypes"]) | set(data.registry["recipeTypes"]) | set(data.registry["recipeSerializers"])
    others |= {c["id"] for c in data.registry["dataComponents"]} | {s["id"] for s in data.registry["soundEvents"]}
    others |= {t["id"] for t in data.registry["creativeTabs"]} | {e["id"] for e in data.registry["entityTypes"]}
    others |= {b["id"] for b in data.registry["blockEntityTypes"]} | {r.id for r in data.recipes}
    others |= set(data.worldgen["configuredFeatures"]) | set(data.worldgen["placedFeatures"]) | set(data.worldgen["biomeModifiers"])
    return ident in others


def legacy_anchors():
    out = []
    with open(os.path.join(paths.NOTES, "legacy-anchors.txt"), encoding="utf-8") as f:
        for line in f:
            if line.strip() and not line.startswith("#"):
                out.append(line.split("\t")[0].strip())
    return out
