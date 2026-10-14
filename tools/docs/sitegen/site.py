"""The site: its pages (hand-written and generated), their URLs and links, the layout around them, and the build."""

import fnmatch
import html
import json
import os
import posixpath
import re
import shutil

from . import md, paths
from .data import MOD, Data, full_id, short_id


class Page:
    def __init__(self, page_id, meta, source=None, body=None, kind="page"):
        self.id = page_id                      # "networks/lanes", "index", "reference/items/drive_bay"
        self.meta = meta
        self.source = source                   # Markdown (hand-written pages)
        self.body = body                       # HTML (generated pages, or after conversion)
        self.kind = kind                       # page, item, command, generated
        self.title = meta.get("title", page_id)
        self.section = meta.get("section", page_id.split("/")[0])
        self.order = float(meta.get("order", 100))
        self.summary = meta.get("summary", "")
        self.headings = []
        self.legacy = {}                       # old anchor -> heading id on this page
        self.search_extra = []                 # extra search entries
        self.mentions = set()                  # mod entries this page documents (shortcodes and covers)
        self.commands_mentioned = set()
        self.screenshots = []
        self.hidden = meta.get("hidden") == "true"   # not listed in the sidebar (item and command pages)
        self.parent = meta.get("parent")       # breadcrumb parent page id

    @property
    def url(self):
        return self.id + ".html"


class Site:
    def __init__(self):
        self.data = Data()
        with open(os.path.join(paths.SRC, "sections.json"), encoding="utf-8") as f:
            self.sections = json.load(f)
        self.section_by_id = {s["id"]: s for s in self.sections}
        self.pages = {}
        self.problems = []      # build failures
        self.warnings = []      # reported, not fatal
        self.icon_fallbacks = []
        self.icon_missing = []
        self.snippets = []      # ELCL programs and DDS from the pages
        self.copy_images = []
        self.version = self.data.mod.get("version", "?")

    # ---- URLs ----
    @staticmethod
    def rel(from_url, to_url):
        if to_url.startswith(("http://", "https://", "mailto:", "#")):
            return to_url
        target, _, anchor = to_url.partition("#")
        base = posixpath.dirname(from_url)
        path = posixpath.relpath(target, base or ".") if target else posixpath.basename(from_url)
        return path + ("#" + anchor if anchor else "")

    def item_url(self, ident):
        ident = full_id(ident)
        if ident in self.data.entries:
            return "reference/items/%s.html" % short_id(ident).replace(":", "/")
        return None

    @staticmethod
    def command_url(name):
        return "elcl/commands/%s.html" % name.upper()

    @staticmethod
    def message_url(ident):
        return "elcl/messages.html#%s" % ident.upper()

    @staticmethod
    def config_anchor(path):
        return "cfg-" + path.replace(".", "-")

    def config_url(self, path):
        return "reference/config.html#" + self.config_anchor(path)

    def icon_url(self, ident):
        """The icon for an item id (rendered, or the texture as a fallback), or None."""
        ident = full_id(ident)
        ns, _, path = ident.partition(":")
        rendered = os.path.join(paths.ICONS, ns, path + ".png")
        if os.path.exists(rendered):
            return "assets/icons/%s/%s.png" % (ns, path)
        fallback = self.texture_fallback(ns, path)
        if fallback:
            return fallback
        if ident not in self.icon_missing:
            self.icon_missing.append(ident)
        return None

    def texture_fallback(self, ns, path):
        out = os.path.join(paths.SITE, "assets", "textures", ns, path + ".png")
        url = "assets/textures/%s/%s.png" % (ns, path)
        if os.path.exists(out):
            return url
        for folder in ("item", "block"):
            source = os.path.join(paths.MOD_ASSETS, ns, "textures", folder, path + ".png")
            if os.path.exists(source):
                os.makedirs(os.path.dirname(out), exist_ok=True)
                shutil.copyfile(source, out)
                self.icon_fallbacks.append("%s:%s (texture %s/%s.png)" % (ns, path, folder, path))
                return url
        return None

    # ---- pages ----
    def add(self, page):
        if page.id in self.pages:
            self.problems.append("Two pages with id %s" % page.id)
        self.pages[page.id] = page
        return page

    def load_hand_pages(self):
        for root, _, files in os.walk(paths.PAGES):
            for name in sorted(files):
                if not name.endswith(".md"):
                    continue
                full = os.path.join(root, name)
                rel = os.path.relpath(full, paths.PAGES).replace(os.sep, "/")[:-3]
                with open(full, encoding="utf-8") as f:
                    meta, body = md.parse_front_matter(f.read())
                if "title" not in meta:
                    self.problems.append("%s.md has no title" % rel)
                page = self.add(Page(rel, meta, source=body))
                page.file = os.path.relpath(full, paths.ROOT).replace(os.sep, "/")
                covers = meta.get("covers") or []
                for pattern in covers if isinstance(covers, list) else [covers]:
                    matched = [e for e in self.data.entries if fnmatch.fnmatch(e, full_id(pattern))]
                    if not matched:
                        self.problems.append("%s: covers '%s' matches no registered block or item" % (page.file, pattern))
                    page.mentions.update(matched)

    def section_pages(self, section):
        pages = [p for p in self.pages.values() if p.section == section and not p.hidden]
        return sorted(pages, key=lambda p: (p.order, p.title))

    def page_link(self, from_page, page_id, anchor=None, label=None):
        page = self.pages.get(page_id)
        if page is None:
            self.problems.append("%s links to page '%s', which doesn't exist" % (getattr(from_page, "file", from_page.id), page_id))
            return "#"
        return self.rel(from_page.url, page.url + ("#" + anchor if anchor else ""))

    # ---- layout ----
    def render(self, page, template):
        section = self.section_by_id.get(page.section, {"id": page.section, "title": page.section.title()})
        siblings = self.section_pages(page.section)
        rel = lambda url: self.rel(page.url, url)
        top_links = []
        for s in self.sections:
            if s.get("nav") is False:
                continue
            index = s.get("index", s["id"] + "/index")
            current = ' aria-current="true" class="current"' if s["id"] == page.section else ""
            top_links.append('<li><a href="%s"%s>%s</a></li>' % (rel(index + ".html"), current, html.escape(s.get("short", s["title"]))))
        side = []
        for p in siblings:
            current = ' aria-current="page" class="current"' if p.id == page.id else ""
            side.append('<li><a href="%s"%s>%s</a></li>' % (rel(p.url), current, html.escape(p.meta.get("nav", p.title))))
        sidebar = '<p class="toc-title">%s</p><nav aria-label="%s pages"><ol>%s</ol></nav>' % (
            html.escape(section["title"]), html.escape(section["title"]), "".join(side)) if side else ""
        # Breadcrumbs: Home > Section > (parent) > Page
        crumbs = ['<a href="%s">Home</a>' % rel("index.html")]
        index_id = section.get("index", section["id"] + "/index")
        if page.id != "index" and index_id in self.pages and index_id != page.id:
            crumbs.append('<a href="%s">%s</a>' % (rel(index_id + ".html"), html.escape(section["title"])))
        if page.parent and page.parent in self.pages and page.parent != index_id:
            parent = self.pages[page.parent]
            crumbs.append('<a href="%s">%s</a>' % (rel(parent.url), html.escape(parent.title)))
        if page.id != "index":
            crumbs.append('<span aria-current="page">%s</span>' % html.escape(page.title))
        breadcrumbs = '<nav class="breadcrumbs" aria-label="Breadcrumbs">%s</nav>' % ' <span class="sep" aria-hidden="true">/</span> '.join(crumbs)
        # On-page contents for long pages.
        toc = ""
        listed = [h for h in page.headings if h[0] in (2, 3)]
        if len(listed) >= 4 and page.meta.get("toc") != "false":
            items = []
            for level, ident, inner, _ in listed:
                label = re.sub(r'<a class="anchor".*?</a>', "", inner)
                items.append('<li class="toc-l%d"><a href="#%s">%s</a></li>' % (level, ident, md.strip_tags(label).strip()))
            toc = '<details class="page-toc" open><summary>On this page</summary><nav aria-label="On this page"><ol>%s</ol></nav></details>' % "".join(items)
        # Previous / next within the section.
        prevnext = ""
        if page in siblings:
            k = siblings.index(page)
            parts = []
            if k > 0:
                parts.append('<a class="prev" href="%s" rel="prev"><span>Previous</span>%s</a>' % (rel(siblings[k - 1].url), html.escape(siblings[k - 1].title)))
            if k + 1 < len(siblings):
                parts.append('<a class="next" href="%s" rel="next"><span>Next</span>%s</a>' % (rel(siblings[k + 1].url), html.escape(siblings[k + 1].title)))
            if parts:
                prevnext = '<nav class="prevnext" aria-label="Previous and next">%s</nav>' % "".join(parts)
        summary = '<p class="lead">%s</p>' % md.Converter(lambda *a: "", lambda *a: "", lambda h: h).inline(page.summary) if page.summary and page.kind == "page" else ""
        title = page.title if page.id == "index" else "%s - Encoded Logistics" % page.title
        values = {
            "title": html.escape(title),
            "description": html.escape(md.strip_tags(page.summary or page.title)),
            "root": rel("index.html")[: -len("index.html")] or "./",
            "home": rel("index.html"),
            "search": rel("search.html"),
            "css": rel("assets/site.css"),
            "js": rel("assets/site.js"),
            "index_js": rel("assets/search-index.js"),
            "top_links": "".join(top_links),
            "sidebar": sidebar,
            "breadcrumbs": breadcrumbs,
            "h1": html.escape(page.meta.get("h1", page.title)),
            "summary": summary,
            "toc": toc,
            "body": page.body,
            "prevnext": prevnext,
            "version": html.escape(self.version),
            "minecraft": html.escape(self.data.mod.get("minecraft", "")),
            "neoforge": html.escape(self.data.mod.get("neoforge", "")),
            "repo": paths.REPO_URL,
            "section_title": html.escape(section["title"]),
        }
        return re.sub(r"\{\{(\w+)\}\}", lambda m: values.get(m.group(1), m.group(0)), template)
