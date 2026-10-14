"""Recipes drawn from the exported recipe JSON: crafting grids, furnaces, the stonecutter, the Lithography Press, the
Arc Crusher and the mod's special crafting recipes, built from the rendered icons."""

import html
import json
import os

from . import paths
from .data import full_id, ingredient_options

TYPE_TITLES = {
    "minecraft:crafting_shaped": "Crafting Table",
    "minecraft:crafting_shapeless": "Crafting Table (shapeless)",
    "minecraft:smelting": "Furnace",
    "minecraft:blasting": "Blast Furnace",
    "minecraft:smoking": "Smoker",
    "minecraft:campfire_cooking": "Campfire",
    "minecraft:stonecutting": "Stonecutter",
    "minecraft:crafting_dye": "Crafting Table (dyeing)",
    "encodedlogistics:lithography": "Lithography Press",
    "encodedlogistics:tape_upgrade": "Crafting Table (tape upgrade, shapeless)",
    "arcforge:crushing": "Arc Crusher (Arcforge)",
}


def load_notes():
    path = os.path.join(paths.NOTES, "recipe-types.json")
    with open(path, encoding="utf-8") as f:
        return json.load(f)


class RecipeRenderer:
    def __init__(self, site):
        self.site = site
        self.notes = load_notes()

    # One slot: an item or tag option list (a tag cycles through its members), with a count.
    def slot(self, page, options, count=1, big=False, label=None, note=None):
        site = self.site
        entries = []
        for kind, value in options:
            if kind == "item":
                entries.append((full_id(value), None))
            else:
                members = site.data.tag_members(value)
                if not members:
                    site.problems.append("%s: a recipe uses tag #%s, which the export has no members for" % (page.id, value))
                for member in members:
                    entries.append((member, value))
        classes = "slot big" if big else "slot"
        if not entries:
            return '<span class="%s empty" aria-hidden="true"></span>' % classes
        cycle = []
        for ident, tag in entries:
            name = site.data.name(ident)
            icon = site.icon_url(ident)
            href = site.item_url(ident)
            title = name if not tag else "%s (any #%s)" % (name, tag)
            cycle.append({"src": site.rel(page.url, icon) if icon else "", "name": title,
                          "href": site.rel(page.url, href) if href else ""})
            if href:
                page.mentions.add(ident)
        first = cycle[0]
        inner = ('<img src="%s" alt="%s" width="32" height="32" loading="lazy">' % (html.escape(first["src"]), html.escape(first["name"]))
                 if first["src"] else '<span class="noicon">%s</span>' % html.escape(first["name"]))
        if count and count > 1:
            inner += '<span class="count">%d</span>' % count
        attrs = ' title="%s"' % html.escape(first["name"])
        if len(cycle) > 1:
            attrs += " data-cycle='%s'" % html.escape(json.dumps(cycle), quote=True)
        if label:
            inner += '<span class="slot-label">%s</span>' % html.escape(label)
        tag = "a" if first["href"] else "span"
        href = ' href="%s"' % html.escape(first["href"]) if first["href"] else ""
        note_html = '<span class="slot-note">%s</span>' % html.escape(note) if note else ""
        return '<%s class="%s"%s%s>%s</%s>%s' % (tag, classes, href, attrs, inner, tag, note_html)

    def grid(self, page, cells, width=3):
        out = ['<span class="grid g%d" role="group" aria-label="Ingredients">' % width]
        for options in cells:
            out.append(self.slot(page, options) if options else '<span class="slot empty" aria-hidden="true"></span>')
        out.append("</span>")
        return "".join(out)

    def result(self, page, recipe):
        ident, count = recipe.result()
        return self.slot(page, [("item", ident)], count, big=True) if ident else ""

    def render(self, page, recipe):
        d = recipe.data
        t = recipe.type
        note = self.notes.get(t)
        machine = ""
        middle = ""
        if t == "minecraft:crafting_shaped":
            pattern = d.get("pattern", [])
            width = max(len(row) for row in pattern) if pattern else 3
            size = 3 if width > 2 or len(pattern) > 2 else 2
            cells = []
            for r in range(size):
                row = pattern[r] if r < len(pattern) else ""
                for c in range(size):
                    ch = row[c] if c < len(row) else " "
                    cells.append(ingredient_options(d["key"].get(ch)) if ch != " " else None)
            inputs = self.grid(page, cells, size)
        elif t in ("minecraft:crafting_shapeless", "encodedlogistics:tape_upgrade"):
            ings = [ingredient_options(i) for i in d.get("ingredients", [])]
            size = 2 if len(ings) <= 4 else 3
            ings += [None] * (size * size - len(ings))
            inputs = self.grid(page, ings, size)
        elif t in ("minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking"):
            inputs = self.slot(page, ingredient_options(d.get("ingredient")))
            seconds = d.get("cookingtime", 200) / 20
            middle = "%s s" % _num(seconds) + (", %s XP" % _num(d["experience"]) if d.get("experience") else "")
        elif t == "minecraft:stonecutting":
            inputs = self.slot(page, ingredient_options(d.get("ingredient")))
        elif t == "encodedlogistics:lithography":
            inputs = '<span class="litho">%s%s%s</span>' % (
                self.slot(page, ingredient_options(d.get("photomask")), label="Photomask", note="kept"),
                self.slot(page, ingredient_options(d.get("wafer")), label="Wafer"),
                self.slot(page, ingredient_options(d.get("additive")), label="Additive"))
            middle = "%s FE, %s s" % ("{:,}".format(d.get("energy", 0)), _num(d.get("time", 0) / 20))
            machine = self.slot(page, [("item", "encodedlogistics:lithography_press")])
        elif t == "arcforge:crushing":
            inputs = self.slot(page, ingredient_options(d.get("ingredient")))
            middle = "%s s" % _num(d.get("time", 200) / 20)
        elif t == "minecraft:crafting_dye":
            inputs = self.grid(page, [ingredient_options(d.get("target")), ingredient_options(d.get("dye"))] + [None] * 2, 2)
        elif note and note.get("slots"):
            inputs = self.grid(page, [ingredient_options(s) if s else None for s in note["slots"]], note.get("size", 3))
        else:
            self.site.problems.append("%s: no way to draw recipe type %s (%s): add it to recipes.py or notes/recipe-types.json"
                                      % (page.id, t, recipe.id))
            inputs = ""
        if note and note.get("result"):
            result = self.slot(page, [("item", note["result"]["id"])], note["result"].get("count", 1), big=True)
        else:
            result = self.result(page, recipe)
        extra = ""
        if d.get("bonus"):
            b = d["bonus"]
            extra = '<span class="bonus">+%s<span class="chance">%d%%</span></span>' % (
                self.slot(page, [("item", b.get("id"))], b.get("count", 1)), round(d.get("bonus_chance", 0) * 100))
        title = (note or {}).get("title") or TYPE_TITLES.get(t, t)
        badges = ""
        mod = recipe.required_mod()
        if mod:
            badges += ' <span class="badge b-no">Needs %s</span>' % html.escape(mod.title())
        elif not recipe.loaded:
            badges += ' <span class="badge b-no">Not loaded</span>'
        text = '<p class="recipe-note">%s</p>' % html.escape(note["text"]) if note and note.get("text") else ""
        arrow = '<span class="arrow" aria-hidden="true">%s<span class="arrow-glyph">&#10140;</span></span>' % (
            '<span class="arrow-text">%s</span>' % html.escape(middle) if middle else "")
        if middle:
            arrow += '<span class="visually-hidden">(%s)</span>' % html.escape(middle)
        return ('<figure class="recipe" id="recipe-%s"><figcaption>%s%s</figcaption><div class="recipe-body">%s%s%s%s%s</div>%s'
                '<p class="recipe-id">%s</p></figure>') % (
            html.escape(recipe.id.replace(":", "-").replace("/", "-")), html.escape(title), badges,
            inputs, machine and '<span class="machine">%s</span>' % machine, arrow, result, extra, text, html.escape(recipe.id))

    def all_for(self, page, ident):
        recipes = self.site.data.made_by.get(full_id(ident), [])
        return "".join(self.render(page, r) for r in sorted(recipes, key=lambda r: r.id))


def _num(value):
    return ("%g" % value) if isinstance(value, float) else str(value)
