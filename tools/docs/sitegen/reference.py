"""The generated reference: an entry page for every block and item, the recipe, tag, config, device type, sound and
screen references, and the changelog."""

import fnmatch
import html
import os
import re

from . import md, paths, tables
from .data import MOD, full_id, short_id
from .site import Page

ITEM_NOTES = os.path.join(paths.NOTES, "items.txt")


def load_item_notes(site):
    """notes/items.txt: [id-or-glob] sections, each a description paragraph plus optional 'works:' and 'page:' lines.
    {name} in a glob's text is the entry's name."""
    notes = []
    current = None
    with open(ITEM_NOTES, encoding="utf-8") as f:
        for number, line in enumerate(f, 1):
            line = line.rstrip("\n")
            if line.startswith("#") and not line.startswith("#["):
                continue
            m = re.match(r"^\[(.+)\]\s*$", line)
            if m:
                current = {"pattern": full_id(m.group(1).strip()), "text": [], "works": [], "page": None, "line": number}
                notes.append(current)
                continue
            if current is None or not line.strip():
                continue
            if line.startswith("works:"):
                current["works"] += [w.strip() for w in line[6:].split(",") if w.strip()]
            elif line.startswith("page:"):
                current["page"] = line[5:].strip()
            else:
                current["text"].append(line.strip())
    by_entry = {}
    for note in notes:
        matched = [e for e in site.data.entries if fnmatch.fnmatch(e, note["pattern"])]
        if not matched:
            site.problems.append("notes/items.txt line %d: [%s] matches no registered block or item" % (note["line"], note["pattern"]))
        for e in matched:
            by_entry.setdefault(e, note)  # the first (most specific, listed first) wins
    return by_entry


def generic_page(site, page_id, title, section, body, summary="", order=100, parent=None, hidden=False):
    meta = {"title": title, "section": section, "order": order, "summary": summary}
    if hidden:
        meta["hidden"] = "true"
    if parent:
        meta["parent"] = parent
    page = Page(page_id, meta, body=body, kind="generated")
    return site.add(page)


class Reference:
    def __init__(self, site, recipes, shortcodes_for):
        self.site = site
        self.data = site.data
        self.recipes = recipes
        self.sc_for = shortcodes_for
        self.notes = load_item_notes(site)

    # ---------- item entries ----------
    def item_pages(self):
        for ident in sorted(self.data.entries, key=lambda e: self.data.entries[e]["name"].lower()):
            entry = self.data.entries[ident]
            page = generic_page(self.site, "reference/items/" + short_id(ident), entry["name"], "reference", "", parent="reference/items/index",
                                hidden=True)
            page.kind = "item"
            page.entry = ident
        index = generic_page(self.site, "reference/items/index", "Blocks and items", "reference", "",
                             "Every block and item in the mod, with its recipe, what it's used in and where it's documented.", order=1)
        index.kind = "items-index"

    def fill_item_pages(self):
        for page in list(self.site.pages.values()):
            if page.kind == "item":
                page.body = self.item_body(page)
            elif page.kind == "items-index":
                page.body = self.items_index(page)

    def related_pages(self, ident):
        out = []
        for p in self.site.pages.values():
            if p.kind == "page" and ident in p.mentions:
                out.append(p)
        return sorted(out, key=lambda p: (p.section != "reference", p.order, p.title))

    def item_body(self, page):
        site, data = self.site, self.data
        ident = page.entry
        sc = self.sc_for(page)
        entry = data.entries[ident]
        item, block = entry["item"], entry["block"]
        note = self.notes.get(ident)
        if note is None:
            site.problems.append("%s has no description in notes/items.txt" % ident)
        out = []
        icon = site.icon_url(ident)
        icon_html = '<img class="entry-icon pixel" src="%s" alt="%s" width="64" height="64">' % (
            html.escape(site.rel(page.url, icon)), html.escape(entry["name"])) if icon else '<span class="entry-icon noicon" aria-hidden="true"></span>'
        facts = ['<code>%s</code>' % html.escape(ident)]
        if item and block:
            facts.append("Block")
        elif block:
            facts.append("Block (no item)")
        else:
            facts.append("Item")
        if item:
            facts.append("Stacks to %d" % item["maxStackSize"])
        tabs = [t["title"] for t in data.registry["creativeTabs"] if ident in t["items"]]
        if tabs:
            facts.append("Creative tab: " + ", ".join(html.escape(t) for t in tabs))
        out.append('<div class="entry-head">%s<div><p class="entry-facts">%s</p>' % (icon_html, " &middot; ".join(facts)))
        if note:
            text = " ".join(note["text"]).replace("{name}", entry["name"])
            out.append('<p class="entry-desc">%s</p>' % sc.inline(text))
        out.append("</div></div>")
        tooltip = data.tooltip(ident) if item else []
        shift = data.shift_info(ident) if item else None
        if tooltip or shift:
            out.append('<div class="tooltip-box" role="group" aria-label="In-game tooltip"><p class="tt-name">%s</p>%s%s</div>' % (
                html.escape(entry["name"]), "".join('<p>%s</p>' % html.escape(l) for l in tooltip if l.strip()),
                '<p class="tt-shift"><span class="tt-key">Shift:</span> %s</p>' % html.escape(shift) if shift else ""))
        if note and note["works"]:
            links = []
            for pattern in note["works"]:
                matched = sorted(e for e in data.entries if fnmatch.fnmatch(e, full_id(pattern)))
                if not matched:
                    site.problems.append("notes/items.txt [%s]: works with '%s' matches nothing" % (note["pattern"], pattern))
                links += [sc.entry_link(e) for e in matched]
            out.append('<h2 id="works-with">Works with</h2><ul class="item-list">%s</ul>' % "".join("<li>%s</li>" % l for l in links))
        # Device type on a network.
        types = [t for t in data.device_types if t["item"] == ident]
        if types:
            t = types[0]
            out.append('<h2 id="device">On a network</h2><p>It counts as a device of type <code>%s</code> in Work with Devices and in ELCL; '
                       'the first one on a network is named <code>%s</code>. See %s.</p>' % (
                           html.escape(t["type"]), html.escape(t["example"]), sc.sc_page(["reference/device-types"], {}, False)))
        # Recipes.
        made = data.made_by.get(ident, [])
        out.append('<h2 id="recipe">How to get it</h2>')
        if made:
            out.append('<div class="recipes">%s</div>' % "".join(self.recipes.render(page, r) for r in sorted(made, key=lambda r: r.id)))
        obtained = self.obtained(ident, sc)
        if obtained:
            out.append(obtained)
        if not made and not obtained:
            out.append("<p>No recipe makes it.</p>")
        used = sorted({o for r in data.used_in.get(ident, []) for o in r.outputs() if o != ident}, key=lambda o: data.name(o).lower())
        out.append('<h2 id="used-in">Used to make</h2>')
        out.append('<ul class="item-list">%s</ul>' % "".join("<li>%s</li>" % sc.entry_link(o) for o in used) if used else "<p>Nothing.</p>")
        # Block facts.
        if block:
            tags = block["tags"]
            tool = "pickaxe" if "minecraft:mineable/pickaxe" in tags else "axe" if "minecraft:mineable/axe" in tags else "hand"
            level = "iron" if "minecraft:needs_iron_tool" in tags else "stone" if "minecraft:needs_stone_tool" in tags else "diamond" if "minecraft:needs_diamond_tool" in tags else ""
            hardness = "unbreakable" if block["hardness"] < 0 else "%g" % block["hardness"]
            rows = [("Hardness", hardness), ("Blast resistance", "{:,.4g}".format(block["blastResistance"])),
                    ("Tool", ((level + " " if level else "any ") + tool if tool != "hand" else "any") + (" (needed for it to drop)" if block["requiresTool"] else "")),
                    ("Light level", "%d in its default state" % block["lightEmission"])]
            out.append('<h2 id="block">As a block</h2><div class="table-wrap"><table class="facts">%s</table></div>' % "".join(
                "<tr><th scope=\"row\">%s</th><td>%s</td></tr>" % (k, html.escape(v)) for k, v in rows))
        tags = sorted(set((item or {}).get("tags", [])))
        if tags:
            out.append('<h2 id="tags">Tags</h2><p>%s</p>' % ", ".join(
                '<a href="%s"><code>#%s</code></a>' % (html.escape(site.rel(page.url, "reference/tags.html#" + tag_anchor(t))), html.escape(t)) for t in tags))
        related = self.related_pages(ident)
        if note and note.get("page"):
            main = site.pages.get(note["page"])
            if main is None:
                site.problems.append("notes/items.txt [%s]: page: %s doesn't exist" % (note["pattern"], note["page"]))
            elif main in related:
                related.remove(main)
                related.insert(0, main)
        if related:
            out.append('<h2 id="related">Where it\'s documented</h2><ul>%s</ul>' % "".join(
                '<li><a href="%s">%s</a> <span class="muted">(%s)</span></li>' % (
                    html.escape(site.rel(page.url, p.url)), html.escape(p.title),
                    html.escape(site.section_by_id.get(p.section, {}).get("title", p.section))) for p in related))
        else:
            site.problems.append("%s isn't documented on any guide page (mention it with {{item}} or list it in a page's covers)" % ident)
        page.search_text = md.strip_tags(" ".join(note["text"])) if note else ""
        return "\n".join(out)

    def obtained(self, ident, sc):
        """How a block with a different drop or worldgen is found: ores (mined for raw metal) and what loot gives."""
        out = []
        loot = self.data.worldgen["blockLoot"].get(ident)
        if loot:
            drops = set()
            _walk_items(loot, drops)
            drops.discard(ident)
            if drops:
                out.append("<p>Mining it drops %s (Silk Touch drops the block itself).</p>" % ", ".join(sc.entry_link(d) for d in sorted(drops)))
        for key, feature in self.data.worldgen["configuredFeatures"].items():
            if ident in str(feature):
                placed = self.data.worldgen["placedFeatures"].get(key, {})
                out.append("<p>Generates in the Overworld (%s).</p>" % html.escape(placement_text(feature, placed)))
                break
        return "".join(out)

    def items_index(self, page):
        sc = self.sc_for(page)
        data = self.data
        out = []
        placed = set()
        for tab in data.registry["creativeTabs"]:
            entries = [i for i in tab["items"] if i in data.entries]
            placed.update(entries)
            out.append('<h2 id="%s">%s</h2>' % (md.slugify(tab["title"]), html.escape(tab["title"])))
            out.append(self.icon_grid(page, entries))
        rest = sorted(e for e in data.entries if e not in placed)
        if rest:
            out.append('<h2 id="not-in-a-tab">Not in a creative tab</h2><p>Internal blocks and items: you never hold them, but you\'ll see their names.</p>')
            out.append(self.icon_grid(page, rest))
        return "\n".join(out)

    def icon_grid(self, page, entries):
        cells = []
        for e in entries:
            icon = self.site.icon_url(e)
            img = '<img src="%s" alt="" width="32" height="32" loading="lazy">' % html.escape(self.site.rel(page.url, icon)) if icon else '<span class="noicon"></span>'
            cells.append('<li><a href="%s">%s<span>%s</span></a></li>' % (
                html.escape(self.site.rel(page.url, self.site.item_url(e))), img, html.escape(self.data.entries[e]["name"])))
        return '<ul class="icon-grid">%s</ul>' % "".join(cells)

    # ---------- other references ----------
    def recipes_page(self):
        page = generic_page(self.site, "reference/recipes", "Recipes", "reference", "", "Every recipe in the mod, by machine.", order=2)
        page.kind = "recipes"

    def fill_recipes_page(self):
        page = self.site.pages["reference/recipes"]
        groups = {}
        for r in self.data.recipes:
            groups.setdefault(r.type, []).append(r)
        out = []
        from .recipes import TYPE_TITLES
        order = ["minecraft:crafting_shaped", "minecraft:crafting_shapeless", "encodedlogistics:tape_upgrade", "minecraft:crafting_dye",
                 "encodedlogistics:rack_elevation_copy", "encodedlogistics:rack_elevation_clear", "encodedlogistics:lithography",
                 "minecraft:smelting", "minecraft:blasting", "minecraft:stonecutting", "arcforge:crushing"]
        for t in order + sorted(set(groups) - set(order)):
            if t not in groups:
                continue
            title = (self.recipes.notes.get(t) or {}).get("title") or TYPE_TITLES.get(t, t)
            out.append('<h2 id="%s">%s <span class="muted">(%d)</span></h2>' % (md.slugify(t), html.escape(title), len(groups[t])))
            rs = sorted(groups[t], key=lambda r: self.data.name(r.outputs()[0] if r.outputs() else r.id).lower())
            out.append('<div class="recipes">%s</div>' % "".join(self.recipes.render(page, r) for r in rs))
        page.body = "\n".join(out)

    def tags_page(self):
        sc = None
        page = generic_page(self.site, "reference/tags", "Tags", "reference", "",
                            "The item and block tags the mod's blocks and items are in, and the ones its recipes use.", order=4)
        sc = self.sc_for(page)
        out = []
        for kind, tags in (("Item tags", self.data.item_tags), ("Block tags", self.data.block_tags)):
            out.append('<h2 id="%s">%s</h2>' % (md.slugify(kind), kind))
            rows = []
            for tag, members in sorted(tags.items()):
                ours = [m for m in members if m in self.data.entries]
                shown = ours if len(members) > 12 and ours else members
                text = ", ".join(sc.entry_link(m) for m in shown)
                if len(shown) < len(members):
                    text += ' <span class="muted">and %d others</span>' % (len(members) - len(shown))
                rows.append('<td id="%s"><code>#%s</code></td><td>%s</td>' % (
                    tag_anchor(tag) if kind == "Item tags" else "block-" + tag_anchor(tag), html.escape(tag), text or '<span class="muted">empty</span>'))
            out.append(tables._wrap(["Tag", "Members"], rows))
        page.body = "\n".join(out)

    def config_page(self):
        page = generic_page(self.site, "reference/config", "Config options", "reference", "",
                            "Every option in the mod's two config files, with its default, range and what it does.", order=5)
        sc = self.sc_for(page)
        out = []
        for f in self.data.config["files"]:
            kind = "client" if f["type"] == "CLIENT" else "main"
            out.append('<h2 id="%s"><code>%s</code></h2>' % (kind, html.escape(f["fileName"])))
            out.append("<p>%s</p>" % ("Each player's own settings, in the game folder's <code>config/</code> folder." if kind == "client" else
                                      "The mod's main options, one file per game or server installation in its <code>config/</code> folder (not per world)."))
            for s in f["sections"]:
                out.append('<h3 id="cfg-%s-%s">%s <code>[%s]</code></h3>' % (kind, s["path"], html.escape(s.get("label") or s["path"].title()), html.escape(s["path"])))
                if s.get("comment"):
                    out.append("<p>%s</p>" % html.escape(s["comment"]))
                options = [dict(o, file=f["fileName"]) for o in s["options"]]
                out.append(tables._wrap(["Option", "Type", "Default", "Allowed", "Config screen", "What it does"],
                                        tables.option_rows(sc, options, True), "cfg-table"))
        page.body = "\n".join(out)

    def simple_page(self, page_id, title, summary, order, table, intro=""):
        page = generic_page(self.site, page_id, title, "reference", "", summary, order=order)
        sc = self.sc_for(page)
        page.body = (("<p>%s</p>" % sc.inline(intro)) if intro else "") + getattr(tables, "table_" + table)(sc, [], {})
        return page

    def data_components_page(self):
        page = generic_page(self.site, "reference/data-components", "Data components", "reference", "",
                            "The item data components the mod registers, for pack makers and add-on authors.", order=9)
        notes = tables.load_note("data-components.json")
        rows = []
        for c in self.data.registry["dataComponents"]:
            note = notes.get(c["id"])
            if note is None:
                self.site.problems.append("data component %s has no description in notes/data-components.json" % c["id"])
            rows.append("<td><code>%s</code></td><td>%s</td><td>%s</td>" % (
                html.escape(c["id"]), "saved" if c["persistent"] else "not saved", self.sc_for(page).inline(note or "")))
        page.body = tables._wrap(["Component", "Saved with the item", "What it holds"], rows)

    def screens_page(self):
        page = generic_page(self.site, "reference/screens", "Screens and menus", "reference", "",
                            "The mod's menu types and every Terminal OS screen a command opens.", order=8)
        sc = self.sc_for(page)
        out = ["<h2 id=\"terminal-os\">Terminal OS screens</h2>", tables.table_screens(sc, [], {}),
               "<h2 id=\"menu-types\">Menu types</h2><p>Every menu (container screen) the mod registers.</p>",
               '<ul class="cols">%s</ul>' % "".join("<li><code>%s</code></li>" % html.escape(m) for m in self.data.registry["menuTypes"])]
        page.body = "\n".join(out)

    def changelog_page(self):
        with open(paths.CHANGELOG, encoding="utf-8") as f:
            text = f.read()
        text = re.sub(r"^# Changelog\s*\n", "", text)
        page = generic_page(self.site, "changelog", "Changelog", "changelog", "", "What changed in each version of Encoded Logistics.", order=1)
        page.kind = "changelog"
        page.source = text
        return page


def tag_anchor(tag):
    return "tag-" + re.sub(r"[^a-z0-9]+", "-", tag.lower()).strip("-")


def _walk_items(json_value, out):
    if isinstance(json_value, dict):
        if json_value.get("type") == "minecraft:item" and "name" in json_value:
            out.add(json_value["name"])
        for v in json_value.values():
            _walk_items(v, out)
    elif isinstance(json_value, list):
        for v in json_value:
            _walk_items(v, out)


def placement_text(feature, placed):
    size = feature.get("config", {}).get("size")
    parts = []
    if size:
        parts.append("veins of up to %d" % size)
    for modifier in placed.get("placement", []):
        t = modifier.get("type", "")
        if t == "minecraft:count":
            parts.append("%s veins per chunk" % modifier.get("count"))
        elif t == "minecraft:height_range":
            h = modifier.get("height", {})
            parts.append("%s from %s to %s" % ("most common in the middle" if h.get("type") == "minecraft:trapezoid" else "evenly",
                                                _anchor(h.get("min_inclusive")), _anchor(h.get("max_inclusive"))))
    return ", ".join(parts)


def _anchor(a):
    if not isinstance(a, dict):
        return str(a)
    if "absolute" in a:
        return "Y %d" % a["absolute"]
    if "above_bottom" in a:
        return "the bottom" if a["above_bottom"] == 0 else "%d above the bottom" % a["above_bottom"]
    if "below_top" in a:
        return "%d below the top" % a["below_top"]
    return str(a)
