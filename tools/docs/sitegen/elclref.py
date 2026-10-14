"""The ELCL command and message references, generated from the command registry and the message definitions."""

import html
import os
import re

from . import md, paths, tables
from .site import Page

SEVERITY = {0: "Information", 10: "Warning", 20: "Error", 30: "Severe error", 40: "Ends the job"}
KIND_TEXT = {
    "CHAR": "Text", "INT": "Whole number", "DEC": "Decimal number", "LGL": "Logical expression", "NAME": "Name (up to 10 characters)",
    "QUALIFIED": "Library/name", "ITEM": "Item ID", "DEVICE": "Device name", "SPECIAL": "Special value", "MSGID": "Message ID",
    "LABEL": "Label in the program", "VARIABLE": "Variable", "COMMAND": "Command", "VALUE": "Any value", "TIME": "Time of day (HHMM or HHMMSS)",
}
SAMPLE_VARS = {"*CHAR": "TYPE(*CHAR) LEN(64)", "*INT": "TYPE(*INT)", "*DEC": "TYPE(*DEC)", "*LGL": "TYPE(*LGL)", "*LIST": "TYPE(*LIST)"}


def categories():
    return tables.load_note("command-categories.json")["categories"]


class ElclReference:
    def __init__(self, site, shortcodes_for):
        self.site = site
        self.data = site.data
        self.sc_for = shortcodes_for
        self.cats = categories()
        self.category_of = {}
        listed = []
        for cat in self.cats:
            for name in cat["commands"]:
                self.category_of[name] = cat
                listed.append(name)
                if name not in self.data.commands:
                    site.problems.append("notes/command-categories.json lists %s, which isn't registered" % name)
        for name in self.data.commands:
            if name not in self.category_of:
                site.problems.append("command %s isn't in any category in notes/command-categories.json" % name)
        if len(listed) != len(set(listed)):
            site.problems.append("notes/command-categories.json lists a command twice")

    def pages(self):
        site = self.site
        index = Page("elcl/commands/index", {"title": "Command reference", "section": "elcl", "order": 90,
                                             "summary": "All %d ELCL commands, by what they work on." % len(self.data.commands)},
                     body="", kind="commands-index")
        site.add(index)
        for name, command in self.data.commands.items():
            page = Page("elcl/commands/" + name, {"title": name, "section": "elcl", "hidden": "true", "parent": "elcl/commands/index",
                                                  "summary": command["description"]}, body="", kind="command")
            page.command = command
            notes = os.path.join(paths.NOTES, "commands", name + ".md")
            if os.path.exists(notes):
                with open(notes, encoding="utf-8") as f:
                    page.notes_source = md.parse_front_matter(f.read())[1]
                page.file = os.path.relpath(notes, paths.ROOT).replace(os.sep, "/")
            else:
                page.notes_source = None
                page.file = "elcl/commands/" + name
            site.add(page)
        site.add(Page("elcl/messages", {"title": "Message reference", "section": "elcl", "order": 91,
                                        "summary": "All %d ELCL messages: their IDs, severities and text." % len(self.data.messages)},
                      body="", kind="messages"))

    # ---- one command ----
    def command_body(self, page, convert):
        c = page.command
        sc = self.sc_for(page)
        out = []
        badges = [tables.context_badge(c["context"]), '<span class="badge b-pgm">Firewall: %s</span>' % tables.auth_text(c["auth"])]
        if c["plcLocal"]:
            badges.append('<span class="badge b-ok">Runs on a PLC with no network</span>')
        out.append('<p class="badges">%s</p>' % " ".join(badges))
        out.append('<p class="lead">%s</p>' % html.escape(c["description"]))
        out.append('<h2 id="syntax">Syntax</h2>')
        out.append('<pre class="code elcl syntax"><code>%s</code></pre>' % html.escape(self.synopsis(c)))
        if c["positional"]:
            first = [p["keyword"] for p in c["params"][: c["positional"]]]
            out.append("<p>The first %s can be given without %s keyword, in that order: %s.</p>" % (
                "parameter" if len(first) == 1 else "%d parameters" % len(first), "its" if len(first) == 1 else "their",
                ", ".join("<code>%s</code>" % k for k in first)))
        if c["params"]:
            out.append('<h2 id="parameters">Parameters</h2>')
            out.append(self.param_table(c))
        else:
            out.append("<p>It takes no parameters.</p>")
        if page.notes_source:
            out.append(convert(page, page.notes_source))
        has_example = page.notes_source and re.search(r"^```elcl", page.notes_source, re.M)
        if not has_example:
            example = self.example(c)
            if example:
                out.append('<h2 id="example">Example</h2>')
                info = "" if c["context"] != "INTERACTIVE" else 'nocompile="a command-line command: type it at the ===> prompt"'
                out.append(sc.fence("elcl", info + " name=cmd-" + c["name"], example))
        cat = self.category_of.get(c["name"])
        if cat:
            others = [n for n in cat["commands"] if n != c["name"]]
            out.append('<h2 id="see-also">%s</h2><p>%s</p>' % (html.escape(cat["title"]), " ".join(sc.sc_cmd([n], {}, False) for n in others)))
            out.append('<p><a href="%s">All commands</a></p>' % html.escape(sc.rel("elcl/commands/index.html#" + cat["id"])))
        return "\n".join(out)

    @staticmethod
    def synopsis(c):
        parts = [c["name"]]
        for p in c["params"]:
            value = p["returns"] + " variable" if p["returns"] else (" | ".join(p["specials"]) if p["kind"] == "SPECIAL" else KIND_TEXT.get(p["kind"], p["kind"]).lower())
            if p["kind"] != "SPECIAL" and p["specials"]:
                value = " | ".join(p["specials"]) + " | " + value
            text = "%s(%s)" % (p["keyword"], value)
            parts.append(text if p["required"] else "[" + text + "]")
        return " ".join(parts)

    def param_table(self, c):
        rows = []
        for p in c["params"]:
            takes = KIND_TEXT.get(p["kind"], p["kind"])
            if p["returns"]:
                takes = "A %s variable the command sets" % p["returns"]
            notes = []
            if "min" in p:
                notes.append("%s to %s" % (tables.config_value(p["min"]), tables.config_value(p["max"])))
            if p["maxValues"] > 1:
                notes.append("a list of up to %d values" % p["maxValues"])
            if p["length"] and p["kind"] in ("CHAR", "NAME", "QUALIFIED", "ITEM", "DEVICE", "VALUE"):
                notes.append("prompter field %d long" % p["length"])
            if p["valueList"] not in ("NONE", "SPECIALS"):
                notes.append("F4 lists %s" % p["valueList"].lower())
            specials = ", ".join("<code>%s</code>" % html.escape(s) for s in p["specials"])
            rows.append("<td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td>" % (
                html.escape(p["keyword"]), html.escape(p["label"]), html.escape(takes) + ("<br><span class=\"muted\">%s</span>" % html.escape("; ".join(notes)) if notes else ""),
                "Yes" if p["required"] else "No", "<code>%s</code>" % html.escape(p["default"]) if p.get("default") else "", specials))
        return tables._wrap(["Keyword", "Prompt", "Takes", "Required", "Default", "Special values"], rows, "param-table")

    def example(self, c):
        """A program line using the command with its required parameters, declaring any variable it sets. None when
        there's no sensible stand-alone example (the language's own statements)."""
        if c["context"] == "PROGRAM" and c["name"] in ("PGM", "ENDPGM", "IF", "ELSE", "DO", "ENDDO", "DOWHILE", "DOUNTIL", "DOFOR", "FOREACH",
                                                       "ENDFOR", "LEAVE", "ITERATE", "SELECT", "WHEN", "OTHERWISE", "ENDSELECT", "GOTO", "SUBR",
                                                       "ENDSUBR", "CALLSUBR", "DCL", "DCLF", "RCVF", "POSDBF", "CLOF", "CHNRCD", "WRTRCD", "UPDRCD",
                                                       "DLTRCD", "MONMSG", "CHGVAR", "RETURN", "RCVMSG"):
            return None
        decls = []
        args = []
        for p in c["params"]:
            if not p["required"] and not p["returns"]:
                continue
            if p["returns"]:
                var = "&" + p["keyword"][:9]
                decls.append("DCL VAR(%s) %s" % (var, SAMPLE_VARS.get(p["returns"], "TYPE(*CHAR) LEN(64)")))
                args.append("%s(%s)" % (p["keyword"], var))
                continue
            value = self.sample(p)
            if value is None:
                return None
            args.append("%s(%s)" % (p["keyword"], value))
        line = " ".join([c["name"]] + args)
        if c["context"] == "INTERACTIVE":
            return line
        return "\n".join(decls + [line])

    @staticmethod
    def sample(p):
        kind = p["kind"]
        if p["specials"] and kind == "SPECIAL":
            return p["specials"][0]
        lo = p.get("min", 1)
        return {"CHAR": "'Hello'", "INT": str(max(lo, 1)) if p.get("max", 2) >= 1 else str(lo), "DEC": str(max(lo, 1)), "NAME": "MYNAME",
                "QUALIFIED": "MYLIB/MYNAME", "ITEM": "minecraft:iron_ingot", "DEVICE": "UPS01", "MSGID": "USR0001", "VALUE": "'1'",
                "TIME": "0800"}.get(kind)

    # ---- index and messages ----
    def index_body(self, page):
        sc = self.sc_for(page)
        for old in ("commands", "cmd-overview", "cmd-desk", "cmd-extra"):
            page.legacy[old] = ""
        out = ["<p>Each command has its own page with every parameter. Badges show where a command can run: on the command line, in programs, or both.</p>"]
        for cat in self.cats:
            legacy = ' data-legacy="%s"' % cat["legacy"] if cat.get("legacy") else ""
            out.append('<h2 id="%s"%s>%s</h2>' % (cat["id"], legacy, html.escape(cat["title"])))
            if cat.get("legacy"):
                page.legacy[cat["legacy"]] = cat["id"]
            page.headings.append((2, cat["id"], html.escape(cat["title"]), {}))
            out.append(tables.table_commands(sc, [cat["id"]], {}))
        return "\n".join(out)

    def messages_body(self, page):
        ranges = tables.load_note("message-ranges.json")
        page.legacy["messages"] = ""
        groups = {}
        for m in self.data.messages.values():
            groups.setdefault(m["id"][:5], []).append(m)
        out = ["<p>Every message an ELCL command, program or screen sends. <code>&amp;1</code>, <code>&amp;2</code> and so on stand for the "
               "details filled in when it's sent. Severity 20 and above is an error: in a program, a <code>MONMSG</code> can catch it, "
               "otherwise it ends the program. <code>USR</code> messages are your own, sent with "
               "<code>SNDPGMMSG</code>.</p>"]
        for prefix in sorted(groups):
            info = ranges.get(prefix)
            if info is None:
                self.site.problems.append("message range %sxx has no title in notes/message-ranges.json" % prefix)
                info = {"title": prefix + "xx"}
            anchor = "msg-" + prefix[3:5]  # ELC12 -> msg-12, as the old single page had them
            title = "%sxx: %s" % (prefix, info["title"])
            out.append('<h2 id="%s">%s</h2>' % (anchor, html.escape(title)))
            page.headings.append((2, anchor, html.escape(title), {}))
            page.legacy[anchor] = anchor
            if info.get("text"):
                out.append("<p>%s</p>" % self.sc_for(page).inline(info["text"]))
            rows = []
            for m in sorted(groups[prefix], key=lambda m: m["id"]):
                text = html.escape(m["text"])
                text = re.sub(r"&amp;(\d)", r'<var>&amp;\1</var>', text)
                rows.append('<td id="%s"><code>%s</code></td><td>%d <span class="muted">%s</span></td><td>%s</td>' % (
                    m["id"], m["id"], m["severity"], SEVERITY.get(m["severity"], ""), text))
            out.append(tables._wrap(["ID", "Severity", "Text"], rows, "msg-table"))
        for prefix in ranges:
            if prefix not in groups and prefix != "USR":
                self.site.problems.append("notes/message-ranges.json has %s, which no message uses" % prefix)
        if "USR" in ranges:
            out.append('<h2 id="msg-usr">USRxxxx: your own messages</h2><p>%s</p>' % self.sc_for(page).inline(ranges["USR"]["text"]))
            page.headings.append((2, "msg-usr", "USRxxxx: your own messages", {}))
            page.legacy["msg-usr"] = "msg-usr"
        return "\n".join(out)
