"""A small Markdown converter for the site's hand-written pages.

It covers what the pages use and nothing more: front matter, ATX headings with {#id} attributes, paragraphs, nested
lists, pipe tables, fenced code, callouts (> [!NOTE] / [!WARNING] / [!TIP]), raw HTML blocks, inline code, bold,
italics, links and a few inline HTML tags. Shortcodes ({{name args}}) and fenced code are handed to the caller's hooks,
so the builder decides what they become.
"""

import html
import re

INLINE_TAGS = r"(?:kbd|br|span|sub|sup|abbr|em|strong|code|a|small|mark|wbr|img)"
INLINE_TAG = re.compile(r"</?" + INLINE_TAGS + r"(?:\s[^<>]*)?/?>")
SHORTCODE = re.compile(r"\{\{(.+?)\}\}")
HEADING = re.compile(r"^(#{1,4})\s+(.*?)\s*(?:\{([^}]*)\})?\s*$")
LIST_ITEM = re.compile(r"^(\s*)([-*]|\d+\.)\s+(.*)$")
FENCE = re.compile(r"^(\s*)(```+)\s*([^`]*)$")
BLOCK_HTML = re.compile(r"^\s*<(?:div|table|figure|section|aside|details|pre|dl|ol|ul|p|h[1-6]|blockquote|nav|svg|!--)\b", re.I)
CALLOUT = re.compile(r"^\[!(NOTE|WARNING|TIP|IMPORTANT)\]\s*(.*)$", re.I)


def parse_front_matter(text):
    """Splits '---' front matter (simple key: value lines, [a, b] lists) from the body."""
    meta = {}
    if text.startswith("---"):
        end = text.find("\n---", 3)
        if end != -1:
            block = text[3:end].strip("\n")
            text = text[end + 4:].lstrip("\n")
            key = None
            for line in block.splitlines():
                if not line.strip() or line.lstrip().startswith("#"):
                    continue
                if line.startswith((" ", "\t")) and key and line.strip().startswith("- "):
                    meta.setdefault(key, [])
                    if not isinstance(meta[key], list):
                        meta[key] = [meta[key]] if meta[key] else []
                    meta[key].append(line.strip()[2:].strip())
                    continue
                key, _, value = line.partition(":")
                key = key.strip()
                value = value.strip()
                if value.startswith("[") and value.endswith("]"):
                    meta[key] = [v.strip().strip("\"'") for v in value[1:-1].split(",") if v.strip()]
                elif value == "":
                    meta[key] = []
                else:
                    meta[key] = value.strip("\"'") if len(value) > 1 and value[0] == value[-1] and value[0] in "\"'" else value
    return meta, text


def slugify(text):
    text = re.sub(r"<[^>]+>", "", text)
    text = html.unescape(text).lower()
    text = re.sub(r"[`*_]", "", text)
    text = re.sub(r"[^a-z0-9]+", "-", text).strip("-")
    return text or "section"


def parse_attrs(text):
    """'#id .class key="value" key2=value' -> dict."""
    attrs = {}
    if not text:
        return attrs
    for match in re.finditer(r'(#[\w-]+)|(\.[\w-]+)|([\w-]+)=("([^"]*)"|\S+)', text):
        if match.group(1):
            attrs["id"] = match.group(1)[1:]
        elif match.group(2):
            attrs.setdefault("class", []).append(match.group(2)[1:])
        else:
            attrs[match.group(3)] = match.group(5) if match.group(5) is not None else match.group(4)
    return attrs


class Converter:
    """Converts one page's Markdown. Hooks:
    shortcode(text, block) -> html          for {{...}}
    fence(lang, info, code) -> html         for ``` blocks
    link(href) -> href                      to rewrite link targets (page: links)
    """

    def __init__(self, shortcode, fence, link):
        self.shortcode = shortcode
        self.fence = fence
        self.link = link
        self.headings = []  # (level, id, text-html, attrs)
        self.ids = set()

    # ---- inline ----
    def inline(self, text):
        placeholders = []

        def hold(fragment):
            placeholders.append(fragment)
            return "\x00%d\x00" % (len(placeholders) - 1)

        text = SHORTCODE.sub(lambda m: hold(self.shortcode(m.group(1).strip(), False)), text)
        text = re.sub(r"(`+)(.+?)\1", lambda m: hold("<code>" + html.escape(m.group(2).strip() if m.group(1) == "``" else m.group(2), quote=False) + "</code>"), text)
        text = INLINE_TAG.sub(lambda m: hold(m.group(0)), text)
        text = re.sub(r"&(#?\w+);", lambda m: hold(m.group(0)), text)
        text = html.escape(text, quote=False)

        def link(m):
            label, href = m.group(1), html.unescape(m.group(2).strip())
            title = ""
            tm = re.match(r'(\S+)\s+"([^"]*)"$', href)
            if tm:
                href, title = tm.group(1), ' title="%s"' % html.escape(tm.group(2))
            return hold('<a href="%s"%s>' % (html.escape(self.link(href)), title)) + label + hold("</a>")

        text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", link, text)
        text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
        text = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"<em>\1</em>", text)
        text = re.sub(r"(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?![\w_])", r"<em>\1</em>", text)
        for _ in range(3):
            text = re.sub(r"\x00(\d+)\x00", lambda m: placeholders[int(m.group(1))], text)
        return text

    # ---- blocks ----
    def convert(self, text):
        lines = text.replace("\r\n", "\n").replace("\t", "    ").split("\n")
        return self.blocks(lines)

    def blocks(self, lines):
        out = []
        i = 0
        n = len(lines)
        while i < n:
            line = lines[i]
            stripped = line.strip()
            if not stripped:
                i += 1
                continue
            fence = FENCE.match(line)
            if fence:
                indent, ticks, info = fence.groups()
                body = []
                i += 1
                while i < n and not lines[i].strip().startswith(ticks):
                    body.append(lines[i][len(indent):] if lines[i].startswith(indent) else lines[i])
                    i += 1
                i += 1
                info = info.strip()
                lang, _, rest = info.partition(" ")
                out.append(self.fence(lang, rest.strip(), "\n".join(body)))
                continue
            heading = HEADING.match(line)
            if heading:
                out.append(self.heading(len(heading.group(1)), heading.group(2), parse_attrs(heading.group(3))))
                i += 1
                continue
            if SHORTCODE.fullmatch(stripped):
                out.append(self.shortcode(stripped[2:-2].strip(), True))
                i += 1
                continue
            if stripped.startswith("|"):
                rows = []
                while i < n and lines[i].strip().startswith("|"):
                    rows.append(lines[i].strip())
                    i += 1
                out.append(self.table(rows))
                continue
            if stripped.startswith(">"):
                quote = []
                while i < n and lines[i].strip().startswith(">"):
                    quote.append(re.sub(r"^\s*>\s?", "", lines[i]))
                    i += 1
                out.append(self.callout(quote))
                continue
            if LIST_ITEM.match(line):
                block = []
                while i < n and (lines[i].strip() == "" or LIST_ITEM.match(lines[i]) or lines[i].startswith("  ")):
                    if lines[i].strip() == "" and (i + 1 >= n or not (LIST_ITEM.match(lines[i + 1]) or lines[i + 1].startswith("  "))):
                        break
                    block.append(lines[i])
                    i += 1
                out.append(self.list(block))
                continue
            if BLOCK_HTML.match(line):
                block = []
                while i < n and lines[i].strip():
                    block.append(lines[i])
                    i += 1
                out.append("\n".join(block))
                continue
            para = []
            while i < n and lines[i].strip() and not (HEADING.match(lines[i]) or FENCE.match(lines[i]) or lines[i].strip().startswith(("|", ">"))
                                                      or LIST_ITEM.match(lines[i]) or SHORTCODE.fullmatch(lines[i].strip())):
                para.append(lines[i].strip())
                i += 1
            out.append("<p>" + self.inline(" ".join(para)) + "</p>")
        return "\n".join(out)

    def heading(self, level, text, attrs):
        ident = attrs.get("id") or slugify(text)
        base, k = ident, 2
        while ident in self.ids:
            ident = "%s-%d" % (base, k)
            k += 1
        self.ids.add(ident)
        inner = self.inline(text)
        self.headings.append((level, ident, inner, attrs))
        return '<h%d id="%s">%s<a class="anchor" href="#%s" aria-label="Link to this section">#</a></h%d>' % (level, ident, inner, ident, level)

    def table(self, rows):
        def cells(row):
            row = row.strip()
            if row.startswith("|"):
                row = row[1:]
            if row.endswith("|") and not row.endswith("\\|"):
                row = row[:-1]
            parts = re.split(r"(?<!\\)\|", row)
            return [p.strip().replace("\\|", "|") for p in parts]

        head = cells(rows[0])
        aligns = []
        body = rows[1:]
        if body and re.fullmatch(r"\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?", body[0]):
            for spec in cells(body[0]):
                aligns.append("right" if spec.endswith(":") and not spec.startswith(":") else "")
            body = body[1:]
        out = ['<div class="table-wrap"><table>', "<thead><tr>"]
        for k, cell in enumerate(head):
            out.append("<th>%s</th>" % self.inline(cell))
        out.append("</tr></thead><tbody>")
        for row in body:
            out.append("<tr>")
            for k, cell in enumerate(cells(row)):
                style = ' class="num"' if k < len(aligns) and aligns[k] == "right" else ""
                out.append("<td%s>%s</td>" % (style, self.inline(cell)))
            out.append("</tr>")
        out.append("</tbody></table></div>")
        return "".join(out)

    def callout(self, lines):
        kind = "note"
        title = ""
        if lines:
            m = CALLOUT.match(lines[0].strip())
            if m:
                kind = m.group(1).lower()
                title = m.group(2)
                lines = lines[1:]
        css = {"warning": "callout warn", "important": "callout warn", "tip": "callout tip"}.get(kind, "callout")
        label = {"note": "Note", "warning": "Warning", "tip": "Tip", "important": "Important"}[kind]
        inner = Converter(self.shortcode, self.fence, self.link)
        inner.ids = self.ids
        body = inner.blocks(lines)
        head = '<p class="callout-title">%s</p>' % (self.inline(title) if title else label)
        return '<div class="%s" role="note">%s%s</div>' % (css, head, body)

    def list(self, lines):
        # Build a tree by indentation.
        items = []  # (indent, ordered, text-lines)
        for line in lines:
            m = LIST_ITEM.match(line)
            if m:
                items.append([len(m.group(1)), m.group(2)[0].isdigit(), [m.group(3)]])
            elif items:
                items[-1][2].append(line.strip())
        return self.render_list(items, 0, len(items))[0]

    def render_list(self, items, start, end):
        if start >= end:
            return "", start
        base = items[start][0]
        ordered = items[start][1]
        out = ["<ol>" if ordered else "<ul>"]
        i = start
        while i < end and items[i][0] >= base:
            indent, _, text = items[i]
            if indent > base:
                i += 1
                continue
            j = i + 1
            while j < end and items[j][0] > base:
                j += 1
            inner = ""
            if j > i + 1:
                inner = self.render_list(items, i + 1, j)[0]
            content = " ".join(t for t in text if t)
            out.append("<li>%s%s</li>" % (self.inline(content), inner))
            i = j
        out.append("</ol>" if ordered else "</ul>")
        return "".join(out), i


def strip_tags(text):
    return html.unescape(re.sub(r"<[^>]+>", " ", text))
