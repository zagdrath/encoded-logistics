"""Shortcodes ({{name args}}) and fenced code in the hand-written pages.

Every shortcode that names a block, item, command, message or config option checks it against the exported data, so
a page can't mention one that isn't registered (the build fails instead).
"""

import fnmatch
import html
import os
import re
import shlex

from . import md, paths, tables
from .data import full_id, short_id

SCREENSHOT_KEYS = ("file", "alt", "show", "angle", "size")


def parse(text):
    try:
        parts = shlex.split(text, posix=True)
    except ValueError:
        parts = text.split()
    name = parts[0] if parts else ""
    args, kwargs = [], {}
    for part in parts[1:]:
        if "=" in part and re.match(r"^[\w-]+=", part):
            key, _, value = part.partition("=")
            kwargs[key] = value
        else:
            args.append(part)
    return name, args, kwargs


class Shortcodes:
    def __init__(self, site, page, recipes):
        self.site = site
        self.data = site.data
        self.page = page
        self.recipes = recipes
        self.snippet_count = 0

    def problem(self, message):
        self.site.problems.append("%s: %s" % (getattr(self.page, "file", self.page.id), message))

    def inline(self, text):
        """Markdown inline text (with shortcodes) from a notes file."""
        return md.Converter(self, self.fence, self.link).inline(text)

    def link(self, href):
        """Rewrites page:ID#anchor links to the page's relative URL."""
        if href.startswith("page:"):
            page_id, _, anchor = href[5:].partition("#")
            return self.site.page_link(self.page, page_id, anchor or None)
        if href.startswith("item:"):
            url = self.site.item_url(href[5:])
            if url is None:
                self.problem("links to item %s, which has no page" % href[5:])
                return "#"
            self.page.mentions.add(full_id(href[5:]))
            return self.rel(url)
        if href.startswith("cmd:"):
            name = href[4:].upper()
            if name not in self.data.commands:
                self.problem("links to command %s, which isn't registered" % name)
            return self.rel(self.site.command_url(name))
        if href.startswith("site:"):
            return self.rel(href[5:])
        if not href.startswith(("http://", "https://", "mailto:", "#")):
            # A path in the repository (the changelog links docs/RELEASING.md): link to it on GitHub.
            return "%s/blob/main/%s" % (paths.REPO_URL, href.lstrip("./"))
        return href

    def rel(self, url):
        return self.site.rel(self.page.url, url)

    # ---- the hook for {{...}} ----
    def __call__(self, text, block):
        name, args, kw = parse(text)
        handler = getattr(self, "sc_" + name.replace("-", "_"), None)
        if handler is None:
            self.problem("unknown shortcode {{%s}}" % text)
            return ""
        return handler(args, kw, block)

    # ---- links to things ----
    def entry_link(self, ident, label=None, icon=True):
        ident = full_id(ident)
        data = self.data
        if ident.startswith("encodedlogistics:"):
            if ident not in data.entries:
                self.problem("mentions %s, which isn't a registered block or item" % ident)
                return html.escape(label or ident)
            self.page.mentions.add(ident)
        elif ident not in data.names:
            self.problem("mentions %s, which isn't in the game's registries" % ident)
        name = label or data.name(ident)
        src = self.site.icon_url(ident) if icon else None
        img = '<img class="icon" src="%s" alt="" width="20" height="20" loading="lazy">' % html.escape(self.rel(src)) if src else ""
        url = self.site.item_url(ident)
        if url:
            return '<a class="item-link" href="%s">%s%s</a>' % (html.escape(self.rel(url)), img, html.escape(name))
        return '<span class="item-link">%s%s</span>' % (img, html.escape(name))

    def sc_item(self, args, kw, block):
        return self.entry_link(args[0], kw.get("label"), kw.get("icon", "yes") != "no")

    def sc_items(self, args, kw, block):
        pattern = full_id(args[0])
        matched = sorted(e for e in self.data.entries if fnmatch.fnmatch(e, pattern))
        if not matched:
            self.problem("{{items %s}} matches nothing" % args[0])
        if kw.get("style") == "inline":
            return ", ".join(self.entry_link(e) for e in matched)
        return '<ul class="item-list">%s</ul>' % "".join("<li>%s</li>" % self.entry_link(e) for e in matched)

    def sc_icon(self, args, kw, block):
        ident = full_id(args[0])
        size = int(kw.get("size", 64))
        src = self.site.icon_url(ident)
        if ident.startswith("encodedlogistics:"):
            self.page.mentions.add(ident)
        if not src:
            return ""
        return '<img class="icon pixel" src="%s" alt="%s" width="%d" height="%d">' % (
            html.escape(self.rel(src)), html.escape(self.data.name(ident)), size, size)

    def sc_recipe(self, args, kw, block):
        ident = full_id(args[0])
        if ident not in self.data.entries and ident not in self.data.names:
            self.problem("{{recipe %s}}: no such item" % args[0])
            return ""
        out = self.recipes.all_for(self.page, ident)
        if not out:
            self.problem("{{recipe %s}}: nothing makes it" % args[0])
        self.page.mentions.add(ident)
        return '<div class="recipes">%s</div>' % out

    def sc_recipe_id(self, args, kw, block):
        for recipe in self.data.recipes:
            if recipe.id == full_id(args[0]):
                return '<div class="recipes">%s</div>' % self.recipes.render(self.page, recipe)
        self.problem("{{recipe-id %s}}: no such recipe" % args[0])
        return ""

    def sc_cmd(self, args, kw, block):
        name = args[0].upper()
        if name not in self.data.commands:
            self.problem("mentions command %s, which isn't registered" % name)
            return "<code>%s</code>" % html.escape(" ".join(args))
        self.page.commands_mentioned.add(name)
        text = " ".join(args) if len(args) > 1 else name
        return '<a class="cmd" href="%s"><code>%s</code></a>' % (html.escape(self.rel(self.site.command_url(name))), html.escape(text))

    def sc_msg(self, args, kw, block):
        ident = args[0].upper()
        if ident not in self.data.messages:
            self.problem("mentions message %s, which isn't defined" % ident)
            return "<code>%s</code>" % html.escape(ident)
        return '<a class="msg" href="%s"><code>%s</code></a>' % (html.escape(self.rel(self.site.message_url(ident))), ident)

    def sc_config(self, args, kw, block):
        path = args[0]
        option = self.data.config_options.get(path)
        if option is None:
            self.problem("mentions config option %s, which doesn't exist" % path)
            return "<code>%s</code>" % html.escape(path)
        text = '<a class="cfg" href="%s"><code>%s</code></a>' % (html.escape(self.rel(self.site.config_url(path))), html.escape(path))
        if "default" in args[1:] or kw.get("default") == "yes":
            text += " (default %s)" % html.escape(tables.config_value(option["default"]))
        return text

    def sc_default(self, args, kw, block):
        option = self.data.config_options.get(args[0])
        if option is None:
            self.problem("{{default %s}}: no such config option" % args[0])
            return "?"
        return html.escape(tables.config_value(option["default"]))

    def sc_page(self, args, kw, block):
        page_id, _, anchor = args[0].partition("#")
        target = self.site.pages.get(page_id)
        label = kw.get("label") or (" ".join(args[1:]) if len(args) > 1 else (target.title if target else page_id))
        return '<a href="%s">%s</a>' % (html.escape(self.site.page_link(self.page, page_id, anchor or None)), html.escape(label))

    def sc_version(self, args, kw, block):
        return html.escape(self.site.version)

    # ---- green screens ----
    def sc_screen(self, args, kw, block):
        ident = args[0].upper()
        for screen in self.data.screens["screens"]:
            if screen["id"] == ident and screen.get("grid"):
                caption = kw.get("caption", "%s (%s) as it opens, before any data arrives" % (screen["title"], screen["typed"]))
                return term_block(screen["grid"], caption)
        self.problem("{{screen %s}}: no rendered screen with that id in screens.json" % ident)
        return ""

    def sc_layout(self, args, kw, block):
        layout = self.data.screens["layouts"].get(args[0])
        if layout is None:
            self.problem("{{layout %s}}: no such layout" % args[0])
            return ""
        return term_block(layout, kw.get("caption", ""))

    # ---- screenshots ----
    def sc_screenshot(self, args, kw, block):
        missing = [k for k in ("file", "alt", "show") if not kw.get(k)]
        if missing:
            self.problem("{{screenshot}} needs %s" % ", ".join(missing))
            return ""
        file = kw["file"]
        source = os.path.join(paths.IMAGES, file)
        entry = dict(kw, page=self.page.id, title=self.page.title, exists=os.path.exists(source))
        self.page.screenshots.append(entry)
        caption = kw.get("caption", "")
        if entry["exists"]:
            self.site.copy_images.append(file)
            return '<figure class="shot"><img src="%s" alt="%s" loading="lazy">%s</figure>' % (
                html.escape(self.rel("assets/images/" + file)), html.escape(kw["alt"]),
                "<figcaption>%s</figcaption>" % html.escape(caption) if caption else "")
        return ('<figure class="shot placeholder" role="img" aria-label="%s"><div class="ph-box"><p class="ph-title">Screenshot needed</p>'
                '<p class="ph-show">%s</p><p class="ph-file"><code>docs/site-src/images/%s</code>%s</p></div>%s</figure>') % (
            html.escape("Screenshot needed: " + kw["alt"]), html.escape(kw["show"]), html.escape(file),
            " &middot; %s" % html.escape(kw["size"]) if kw.get("size") else "",
            "<figcaption>%s</figcaption>" % html.escape(caption) if caption else "")

    # ---- ELCL examples ----
    def sc_example(self, args, kw, block):
        name = args[0]
        path = os.path.join(paths.EXAMPLES, name)
        if not os.path.exists(path):
            self.problem("{{example %s}}: no file docs/site-src/examples/%s" % (name, name))
            return ""
        with open(path, encoding="utf-8") as f:
            code = f.read().rstrip("\n")
        info = " ".join("%s=%s" % (k, v) for k, v in kw.items() if k != "caption")
        if name.lower().endswith(".pf"):
            return self.fence("dds", "file=%s %s" % (kw.get("file", "ELGPL/" + os.path.splitext(name)[0].upper()), info), code, title=name)
        return self.fence("elcl", info, code, title=kw.get("caption", name))

    # ---- generated tables ----
    def sc_table(self, args, kw, block):
        name = args[0]
        fn = getattr(tables, "table_" + name.replace("-", "_"), None)
        if fn is None:
            self.problem("{{table %s}}: no such table" % name)
            return ""
        return fn(self, args[1:], kw)

    # ---- fenced code ----
    def fence(self, lang, info, code, title=None):
        attrs = md.parse_attrs(info)
        if lang == "term":
            return term_block(code.split("\n"), attrs.get("caption", ""))
        if lang in ("elcl", "dds"):
            self.snippet_count += 1
            name = attrs.get("name") or "%s-%d" % (self.page.id.replace("/", "-"), self.snippet_count)
            if lang == "dds":
                if "file" not in attrs:
                    self.problem("a ```dds block needs file=LIB/NAME")
                self.site.snippets.append({"kind": "file", "page": getattr(self.page, "file", self.page.id), "name": attrs.get("file", "X/X"),
                                           "code": code, "id": name})
            elif "nocompile" in attrs:
                if not attrs["nocompile"] or attrs["nocompile"] in ("true", "yes"):
                    self.problem("```elcl nocompile needs a reason: nocompile=\"why\"")
            else:
                self.site.snippets.append({"kind": "program", "page": getattr(self.page, "file", self.page.id), "name": name, "code": code,
                                           "wrap": "wrap" in attrs or not re.match(r"^\s*(/\*.*?\*/\s*)*PGM\b", code, re.S | re.I),
                                           "target": attrs.get("target", "JOB").upper(), "warnings": "warnings" in attrs})
            for match in re.finditer(r"^\s*(?:\w+:\s*)?([A-Z][A-Z0-9]{1,9})\b", code, re.M):
                if match.group(1) in self.data.commands:
                    self.page.commands_mentioned.add(match.group(1))
            body = highlight_elcl(code) if lang == "elcl" else html.escape(code)
            head = '<div class="code-title">%s</div>' % html.escape(title) if title else ""
            return '<div class="code-block">%s<pre class="code elcl"><code>%s</code></pre></div>' % (head, body)
        return '<pre class="code"><code>%s</code></pre>' % html.escape(code)


def term_block(rows, caption):
    text = "\n".join(r.rstrip() for r in rows)
    pre = '<pre class="term" aria-label="Terminal screen">%s</pre>' % html.escape(text)
    if caption:
        return '<figure class="term-figure">%s<figcaption>%s</figcaption></figure>' % (pre, html.escape(caption))
    return pre


def highlight_elcl(code):
    out = []
    pos = 0
    for match in re.finditer(r"/\*.*?\*/|'(?:[^']|'')*'", code, re.S):
        out.append(html.escape(code[pos:match.start()]))
        token = match.group(0)
        if token.startswith("/*"):
            out.append('<span class="c">%s</span>' % html.escape(token))
        else:
            out.append('<span class="s">%s</span>' % html.escape(token))
        pos = match.end()
    out.append(html.escape(code[pos:]))
    return "".join(out)
