"""Where the site's sources, data and output live (relative to the repository root)."""

import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SRC = os.path.join(ROOT, "docs", "site-src")
DATA = os.path.join(SRC, "data")
PAGES = os.path.join(SRC, "pages")
NOTES = os.path.join(SRC, "notes")
TEMPLATES = os.path.join(SRC, "templates")
ASSETS = os.path.join(SRC, "assets")
IMAGES = os.path.join(SRC, "images")
EXAMPLES = os.path.join(SRC, "examples")
SITE = os.path.join(ROOT, "docs", "site")
ICONS = os.path.join(SITE, "assets", "icons")
BUILD = os.path.join(ROOT, "build", "docs")
SNIPPETS = os.path.join(BUILD, "snippets")
MOD_ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets")
OLD_PAGE = os.path.join(ROOT, "docs", "terminal-os-elcl.html")
CHANGELOG = os.path.join(ROOT, "CHANGELOG.md")
REPO_URL = "https://github.com/zagdrath/encoded-logistics"
