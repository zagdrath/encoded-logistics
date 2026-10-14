"""Tables generated from the exported data, for {{table name}} in the pages and for the reference pages."""

import html
import json
import os
import re

from . import paths
from .data import full_id, short_id


def config_value(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, float):
        return ("%.10g" % value)
    if isinstance(value, int):
        return "{:,}".format(value) if abs(value) >= 10000 else str(value)
    if isinstance(value, list):
        return "[" + ", ".join(config_value(v) for v in value) + "]" if value else "[] (empty)"
    if value is None:
        return ""
    return str(value)


def config_range(option):
    if option.get("allowed"):
        return ", ".join(option["allowed"])
    if "min" in option:
        lo, hi = option["min"], option["max"]
        big = {2147483647: "no limit", 9223372036854775807: "no limit", 1.7976931348623157e308: "no limit"}
        hi_text = big.get(hi, config_value(hi))
        return "%s to %s" % (config_value(lo), hi_text) if hi_text != "no limit" else "%s or more" % config_value(lo)
    return ""


def config_comment(text):
    """NeoForge appends 'Default: ...' / 'Range: ...' / 'Allowed Values: ...' lines to comments; the table shows those
    in their own columns."""
    if not text:
        return ""
    lines = [l.strip() for l in text.split("\n")]
    lines = [l for l in lines if l and not re.match(r"^(Default|Range|Allowed Values)\s*:", l)]
    return " ".join(lines)


def _wrap(head, rows, css=""):
    out = ['<div class="table-wrap"><table%s><thead><tr>' % (' class="%s"' % css if css else "")]
    out += ["<th>%s</th>" % h for h in head]
    out.append("</tr></thead><tbody>")
    for row in rows:
        out.append("<tr>%s</tr>" % row)
    out.append("</tbody></table></div>")
    return "".join(out)


def option_rows(sc, options, anchors):
    rows = []
    for o in options:
        key = '<code>%s</code>' % html.escape(o["path"])
        if anchors:
            key = '<span id="%s"></span>%s' % (sc.site.config_anchor(o["path"]), key)
        else:
            key = '<a href="%s">%s</a>' % (html.escape(sc.rel(sc.site.config_url(o["path"]))), key)
        label = html.escape(o.get("label") or "")
        restart = ' <span class="badge b-no">restart: %s</span>' % html.escape(o["restart"].lower().replace("_", " ")) if o.get("restart", "NONE") != "NONE" else ""
        rows.append("<td>%s%s</td><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td>" % (
            key, restart, html.escape(o["type"]), html.escape(config_value(o["default"])), html.escape(config_range(o)), label,
            html.escape(config_comment(o.get("comment")))))
    return rows


def table_config(sc, args, kw):
    data = sc.data
    keys = kw.get("keys")
    section = kw.get("section")
    options = []
    if keys:
        for path in keys.split(","):
            option = data.config_options.get(path.strip())
            if option is None:
                sc.problem("{{table config}}: no option %s" % path)
            else:
                options.append(option)
    elif section:
        options = [o for o in data.config_options.values() if o["section"] == section and (not kw.get("file") or kw["file"] in o["file"])]
        if not options:
            sc.problem("{{table config}}: no section %s" % section)
    return _wrap(["Option", "Type", "Default", "Allowed", "Config screen", "What it does"], option_rows(sc, options, False), "cfg-table")


def table_rack_devices(sc, args, kw):
    rows = []
    for t in sc.data.device_types:
        if t["kind"] != "rack":
            continue
        rows.append("<td>%s</td><td class=\"num\">%dU</td><td><code>%s</code></td><td><code>%s</code></td>" % (
            sc.entry_link(t["item"]), t["units"], html.escape(t["type"]), html.escape(t["example"])))
    return _wrap(["Device", "Height", "ELCL type", "First name"], rows)


def table_device_types(sc, args, kw):
    kinds = {"rack": "Rack device", "part": "Cable part", "wirelessPort": "Wireless Port's part", "block": "Block"}
    rows = []
    seen = set()
    for t in sc.data.device_types:
        key = (t["item"], t["type"])
        if key in seen:
            continue
        seen.add(key)
        item = t["item"]
        what = sc.entry_link(item) if full_id(item) in sc.data.entries else html.escape(item)
        rows.append("<td>%s</td><td>%s</td><td><code>%s</code></td><td><code>%s</code></td>" % (
            what, kinds.get(t["kind"], t["kind"]), html.escape(t["type"]), html.escape(t["example"])))
    return _wrap(["Device", "Kind", "Type code", "First name"], rows)


def table_sysvals(sc, args, kw):
    rows = []
    for v in sc.data.elcl["systemValues"]:
        default = "ELNET<i>nn</i> (the network's number)" if v["name"] == "SYSNAME" else "<code>%s</code>" % html.escape(v["default"])
        rows.append("<td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td>" % (
            html.escape(v["name"]), html.escape(v["description"]), default, html.escape(", ".join(v["allowed"]))))
    return _wrap(["System value", "What it is", "Default", "Allowed"], rows)


def trigger_events(data):
    command = data.commands.get("ADDTRGEVT")
    for param in command["params"] if command else []:
        if param["keyword"] == "EVENT":
            return param["specials"]
    return []


def table_triggers(sc, args, kw):
    notes = load_note("trigger-events.json")
    rows = []
    for event in trigger_events(sc.data):
        note = notes.get(event)
        if note is None:
            sc.problem("trigger event %s has no description in notes/trigger-events.json" % event)
            note = {}
        rows.append("<td><code>%s</code></td><td>%s</td><td>%s</td>" % (
            html.escape(event), sc.inline(note.get("when", "")), sc.inline(note.get("data", ""))))
    for event in notes:
        if event not in trigger_events(sc.data):
            sc.problem("notes/trigger-events.json describes %s, which ADDTRGEVT doesn't take" % event)
    return _wrap(["Event", "When it fires", "&amp;DATA"], rows)


def table_builtins(sc, args, kw):
    notes = load_note("builtins.json")
    rows = []
    for b in sc.data.elcl["builtins"]:
        note = notes.get(b["name"])
        if note is None:
            sc.problem("built-in %s has no description in notes/builtins.json" % b["name"])
            note = {}
        args_text = str(b["minArgs"]) if b["minArgs"] == b["maxArgs"] else "%d to %d" % (b["minArgs"], b["maxArgs"])
        rows.append("<td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td>" % (
            html.escape(b["name"]), args_text, html.escape(b["result"] or "as its arguments"), sc.inline(note.get("text", ""))))
    return _wrap(["Function", "Arguments", "Gives", "What it does"], rows)


def table_chassis_parts(sc, args, kw):
    kinds = {"SUPERVISOR": "Chassis Switch: supervisor bay", "LINE": "Chassis Switch: line card bay", "CHASSIS_PSU": "Chassis Switch: power bay",
             "SHELF_CONTROLLER": "Transport Shelf: controller bay", "SHELF_LINE": "Transport Shelf: line card bay",
             "BLADE": "Blade Enclosure: front blade bay", "INTERCONNECT": "Blade Enclosure: interconnect bay",
             "BLADE_PSU": "Blade Enclosure: power bay", "MANAGEMENT": "Blade Enclosure: management bay"}
    rows = []
    for p in sc.data.devices["chassisParts"]:
        rows.append("<td>%s</td><td>%s</td>" % (sc.entry_link(p["item"]), html.escape(kinds.get(p["kind"], p["kind"]))))
    return _wrap(["Part", "Goes in"], rows)


def table_sounds(sc, args, kw):
    rows = []
    for s in sc.data.registry["soundEvents"]:
        rows.append("<td><code>%s</code></td><td>%s</td><td>%d</td>" % (
            html.escape(s["id"]), html.escape(s.get("subtitle", "")), len(s.get("sounds", []))))
    return _wrap(["Sound event", "Subtitle", "Variants"], rows)


def table_main_menu(sc, args, kw):
    rows = []
    for o in sc.data.screens["mainMenu"]:
        label = sc.data.lang.get("crt.encodedlogistics.menu.%d" % o["number"], "")
        cmd = o["command"].split()[0] if o["command"] else ""
        cmd_html = sc.sc_cmd([o["command"]], {}, False) if cmd in sc.data.commands else "<code>%s</code>" % html.escape(o["command"])
        rows.append("<td class=\"num\">%d</td><td>%s</td><td>%s</td>" % (o["number"], html.escape(label), cmd_html))
    return _wrap(["Option", "Menu text", "Runs"], rows)


def table_screens(sc, args, kw):
    rows = []
    for s in sc.data.screens["screens"]:
        rows.append("<td><code>%s</code></td><td>%s</td><td>%s</td>" % (html.escape(s["id"]), html.escape(s["title"]), sc.sc_cmd([s["command"]], {}, False)))
    return _wrap(["Screen", "Title", "Opened by"], rows)


def table_creative_tabs(sc, args, kw):
    rows = []
    for t in sc.data.registry["creativeTabs"]:
        rows.append("<td>%s</td><td>%s</td><td class=\"num\">%d</td>" % (sc.entry_link(t["icon"], icon=True, label=t["title"]), html.escape(t["id"]), len(t["items"])))
    return _wrap(["Tab", "Id", "Entries"], rows)


def table_commands(sc, args, kw):
    cats = load_note("command-categories.json")["categories"]
    wanted = args[0] if args else None
    rows = []
    for cat in cats:
        if wanted and cat["id"] != wanted:
            continue
        for name in cat["commands"]:
            c = sc.data.commands.get(name)
            if c is None:
                continue
            rows.append("<td>%s</td><td>%s</td><td>%s</td><td>%s</td>" % (
                sc.sc_cmd([name], {}, False), html.escape(c["description"]), context_badge(c["context"]), auth_text(c["auth"])))
    return _wrap(["Command", "Description", "Where", "Firewall permission"], rows, "cmd-table")


CONTEXTS = {"BOTH": ('b-ok', "Command line and programs"), "PROGRAM": ('b-pgm', "Programs only"),
            "INTERACTIVE": ('b-scr', "Command line only"), "BATCH": ('b-no', "Batch only")}


def context_badge(context):
    css, text = CONTEXTS.get(context, ("", context))
    return '<span class="badge %s">%s</span>' % (css, text)


def auth_text(auth):
    return {"NONE": "None", "CONFIGURE": "BUILD"}.get(auth, auth)


def load_note(name):
    path = os.path.join(paths.NOTES, name)
    if not os.path.exists(path):
        return {}
    with open(path, encoding="utf-8") as f:
        return json.load(f)
