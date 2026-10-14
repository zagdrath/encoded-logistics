"""The exported mod data (docs/site-src/data/*.json, written by ./gradlew exportDocsData and renderDocsIcons)."""

import json
import os

from . import paths

MOD = "encodedlogistics"


def load(name, default=None):
    path = os.path.join(paths.DATA, name)
    if not os.path.exists(path):
        if default is not None:
            return default
        raise SystemExit("Missing %s: run ./gradlew exportDocsData first" % path)
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def full_id(ident):
    return ident if ":" in ident else MOD + ":" + ident


def short_id(ident):
    return ident.split(":", 1)[1] if ident.startswith(MOD + ":") else ident


class Data:
    def __init__(self):
        self.mod = load("mod.json")
        self.registry = load("registry.json")
        self.recipes_raw = load("recipes.json")
        self.worldgen = load("worldgen.json")
        self.config = load("config.json")
        self.elcl = load("elcl.json")
        self.devices = load("devices.json")
        self.lang = load("lang.json")
        self.screens = load("screens.json", {"screens": [], "mainMenu": [], "layouts": {}})
        self.client_tooltips = load("tooltips-client.json", {})

        self.items = {i["id"]: i for i in self.registry["items"]}
        self.blocks = {b["id"]: b for b in self.registry["blocks"]}
        self.names = dict(self.registry["names"]["blocks"])
        self.names.update(self.registry["names"]["items"])
        self.item_tags = self.registry["itemTags"]
        self.block_tags = self.registry["blockTags"]
        self.commands = {c["name"]: c for c in self.elcl["commands"]}
        self.messages = {m["id"]: m for m in self.elcl["messages"]}
        self.config_options = {}
        for f in self.config["files"]:
            for section in f["sections"]:
                for option in section["options"]:
                    self.config_options[option["path"]] = dict(option, file=f["fileName"], section=section["path"])
        self.device_types = self.devices["types"]

        # Entries: every item, and every block without an item (Part Host, the Small Wireless Bridge's model block).
        self.entries = {}
        for ident, item in self.items.items():
            self.entries[ident] = {"id": ident, "name": item["name"], "item": item, "block": self.blocks.get(item.get("block"))}
        for ident, block in self.blocks.items():
            if not block.get("item") and ident not in self.entries:
                self.entries[ident] = {"id": ident, "name": block["name"], "item": None, "block": block}
            elif block.get("item") and block["item"] != ident and ident in self.entries:
                pass

        self.recipes = [Recipe(r) for r in self.recipes_raw.values()]
        self.made_by = {}
        self.used_in = {}
        for recipe in self.recipes:
            for out in recipe.outputs():
                self.made_by.setdefault(out, []).append(recipe)
            for ident in recipe.input_items(self):
                self.used_in.setdefault(ident, [])
                if recipe not in self.used_in[ident]:
                    self.used_in[ident].append(recipe)

    def name(self, ident):
        ident = full_id(ident)
        if ident in self.names:
            return self.names[ident]
        return ident

    def is_mod_entry(self, ident):
        return full_id(ident) in self.entries

    def tag_members(self, tag):
        return self.item_tags.get(tag.lstrip("#"), [])

    def tooltip(self, ident):
        """The tooltip's lines under the name (client-side ones included when the icons run wrote them), without the
        'Hold [Shift] for info' hint."""
        entry = self.client_tooltips.get(ident)
        lines = entry.get("lines") if isinstance(entry, dict) else entry
        if lines is None:
            lines = (self.items.get(ident) or {}).get("tooltip", [])
        hint = self.lang.get("tooltip.encodedlogistics.hold_shift", "").replace("%s", self.lang.get("tooltip.encodedlogistics.shift", ""))
        return [l for l in lines if l != hint]

    def shift_info(self, ident):
        """The description the item's tooltip shows while Shift is held, or None."""
        entry = self.client_tooltips.get(ident)
        return entry.get("shift") if isinstance(entry, dict) else None


def ingredient_options(value):
    """An ingredient as JSON -> a list of ('item', id) / ('tag', tag) options."""
    if value is None:
        return []
    if isinstance(value, str):
        return [("tag", value[1:])] if value.startswith("#") else [("item", value)]
    if isinstance(value, list):
        out = []
        for v in value:
            out.extend(ingredient_options(v))
        return out
    if isinstance(value, dict):
        if "item" in value:
            return [("item", value["item"])]
        if "tag" in value:
            return [("tag", value["tag"])]
        if "id" in value:
            return [("item", value["id"])]
        if "items" in value:
            return ingredient_options(value["items"])
    return []


class Recipe:
    def __init__(self, raw):
        self.id = raw["id"]
        self.loaded = raw["loaded"]
        self.data = raw["data"]
        self.type = self.data.get("type", "")
        self.conditions = self.data.get("neoforge:conditions", [])

    def result(self):
        r = self.data.get("result")
        if isinstance(r, dict):
            return r.get("id") or r.get("item"), r.get("count", 1)
        if isinstance(r, str):
            return r, 1
        return None, 0

    def outputs(self):
        ident, _ = self.result()
        return [ident] if ident else []

    def ingredient_slots(self):
        """Every ingredient position's options, for 'used in' lookups."""
        d = self.data
        slots = []
        if self.type == "minecraft:crafting_shaped":
            for row in d.get("pattern", []):
                for ch in row:
                    if ch != " ":
                        slots.append(ingredient_options(d["key"].get(ch)))
        elif "ingredients" in d:
            for ing in d["ingredients"]:
                slots.append(ingredient_options(ing))
        for key in ("ingredient", "wafer", "additive", "photomask", "dye", "target", "base", "addition", "template"):
            if key in d:
                slots.append(ingredient_options(d[key]))
        return slots

    def input_items(self, data):
        out = []
        for slot in self.ingredient_slots():
            for kind, value in slot:
                if kind == "item":
                    out.append(value)
                else:
                    out.extend(data.tag_members(value))
        return list(dict.fromkeys(out))

    def required_mod(self):
        for c in self.conditions:
            if c.get("type") == "neoforge:mod_loaded":
                return c.get("modid")
        return None
