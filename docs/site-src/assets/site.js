/* Encoded Logistics documentation: the theme toggle, live search (the index loads on first use, so it works from disk
   too), the full results page, and recipe slots that cycle through a tag's members. No dependencies. */
(function () {
  "use strict";
  var body = document.body;
  var root = body.getAttribute("data-root") || "./";

  /* ---- Theme: automatic (the system's), light or dark; remembered per browser. ---- */
  var toggle = document.querySelector(".theme-toggle");
  var themes = ["auto", "light", "dark"];
  function currentTheme() {
    var t = document.documentElement.getAttribute("data-theme");
    return t === "light" || t === "dark" ? t : "auto";
  }
  function setTheme(t) {
    if (t === "auto") { document.documentElement.removeAttribute("data-theme"); } else { document.documentElement.setAttribute("data-theme", t); }
    try { if (t === "auto") { localStorage.removeItem("el-theme"); } else { localStorage.setItem("el-theme", t); } } catch (e) { /* storage off */ }
    if (toggle) {
      var label = { auto: "automatic", light: "light", dark: "dark" }[t];
      toggle.setAttribute("aria-label", "Colour theme: " + label);
      toggle.title = "Colour theme: " + label + " (click to change)";
    }
  }
  if (toggle) {
    setTheme(currentTheme());
    toggle.addEventListener("click", function () { setTheme(themes[(themes.indexOf(currentTheme()) + 1) % themes.length]); });
  }

  /* ---- Close the mobile menus when a link in them is followed. ---- */
  document.querySelectorAll(".mobile-toc a, .sections-menu a").forEach(function (a) {
    a.addEventListener("click", function () {
      var d = a.closest("details");
      if (d) { d.removeAttribute("open"); }
    });
  });

  /* ---- Recipe slots holding a tag show each of its members in turn. ---- */
  var cycling = document.querySelectorAll(".slot[data-cycle]");
  if (cycling.length && !(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches)) {
    var tick = 0;
    setInterval(function () {
      tick++;
      cycling.forEach(function (slot) {
        var list;
        try { list = JSON.parse(slot.getAttribute("data-cycle")); } catch (e) { return; }
        var entry = list[tick % list.length];
        var img = slot.querySelector("img");
        if (img && entry.src) { img.src = entry.src; img.alt = entry.name; }
        slot.title = entry.name;
        if (slot.tagName === "A" && entry.href) { slot.href = entry.href; }
      });
    }, 1300);
  }

  /* ---- Search ---- */
  var input = document.getElementById("q");
  var box = document.getElementById("search-results");
  var index = null, loading = null;
  var KIND_ORDER = ["page", "heading", "item", "command", "config", "message"];
  var KIND_TITLE = { page: "Pages", heading: "Sections", item: "Blocks and items", command: "ELCL commands", config: "Config options", message: "Messages" };

  function loadIndex(done) {
    if (index) { done(); return; }
    if (window.EL_SEARCH) { prepare(); done(); return; }
    if (!loading) {
      loading = [];
      var s = document.createElement("script");
      s.src = body.getAttribute("data-index");
      s.onload = function () { prepare(); loading.forEach(function (f) { f(); }); };
      document.head.appendChild(s);
    }
    loading.push(done);
  }
  function prepare() {
    index = (window.EL_SEARCH || []).map(function (e) {
      return { kind: e[0], title: e[1], url: e[2], summary: e[3], text: e[4], extra: e[5],
               lt: e[1].toLowerCase(), ls: (e[3] || "").toLowerCase(), lx: (e[4] || "").toLowerCase(), le: (e[5] || "").toLowerCase() };
    });
  }
  function terms(q) { return q.toLowerCase().split(/\s+/).filter(function (t) { return t.length > 0; }); }

  /* Exact name first, then the name starting with or holding the query, then headings, then summaries, then body text. */
  function score(e, q, words) {
    var s = 0;
    if (e.lt === q || e.le === q) { s = 1000; }
    else if (e.lt.indexOf(q) === 0) { s = 600; }
    else if (e.lt.indexOf(q) >= 0) { s = 450; }
    var allTitle = words.every(function (w) { return e.lt.indexOf(w) >= 0 || e.le.indexOf(w) >= 0; });
    if (!s && allTitle) { s = 350; }
    if (!s && words.every(function (w) { return e.lt.indexOf(w) >= 0 || e.ls.indexOf(w) >= 0 || e.le.indexOf(w) >= 0; })) { s = 200; }
    if (!s && words.every(function (w) { return e.lt.indexOf(w) >= 0 || e.ls.indexOf(w) >= 0 || e.lx.indexOf(w) >= 0; })) { s = 80; }
    if (!s) { return 0; }
    if (e.kind === "heading") { s -= 30; }
    if (e.kind === "page") { s += 20; }
    return s - Math.min(e.title.length, 60) / 10;
  }
  function search(q) {
    q = q.trim().toLowerCase();
    if (!q) { return []; }
    var words = terms(q);
    var hits = [];
    for (var i = 0; i < index.length; i++) {
      var s = score(index[i], q, words);
      if (s > 0) { hits.push({ e: index[i], s: s }); }
    }
    hits.sort(function (a, b) { return b.s - a.s; });
    return hits;
  }
  function esc(t) { return String(t).replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; }); }
  function highlight(text, words) {
    var out = esc(text);
    words.forEach(function (w) {
      if (w.length < 2) { return; }
      out = out.replace(new RegExp("(" + esc(w).replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + ")", "ig"), "<mark>$1</mark>");
    });
    return out;
  }
  function snippet(e, words) {
    var text = e.summary || "";
    var hay = e.text || "";
    var low = hay.toLowerCase();
    for (var k = 0; k < words.length && !text; k++) {
      var at = low.indexOf(words[k]);
      if (at >= 0) { text = (at > 40 ? "…" : "") + hay.substr(Math.max(0, at - 40), 140) + "…"; }
    }
    return text || e.extra || "";
  }

  var selected = -1;
  function links() { return box ? box.querySelectorAll("a[data-hit]") : []; }
  function select(k) {
    var all = links();
    all.forEach(function (a) { a.removeAttribute("aria-selected"); });
    selected = k;
    if (k >= 0 && k < all.length) { all[k].setAttribute("aria-selected", "true"); all[k].scrollIntoView({ block: "nearest" }); }
  }
  function show(q) {
    if (!box) { return; }
    var hits = search(q);
    var words = terms(q);
    var groups = {};
    hits.slice(0, 60).forEach(function (h) { (groups[h.e.kind] = groups[h.e.kind] || []).push(h); });
    var html = "";
    var count = 0;
    KIND_ORDER.forEach(function (kind) {
      var list = groups[kind];
      if (!list) { return; }
      html += '<div class="group">' + KIND_TITLE[kind] + "</div>";
      list.slice(0, kind === "page" || kind === "heading" ? 5 : 4).forEach(function (h) {
        var sub = h.e.kind === "heading" ? h.e.extra : snippet(h.e, words);
        html += '<a data-hit role="option" href="' + esc(root + h.e.url) + '">' + highlight(h.e.title, words) +
                (sub ? '<span class="sub">' + highlight(sub, words) + "</span>" : "") + "</a>";
        count++;
      });
    });
    if (!count) { html = '<p class="none">Nothing found for “' + esc(q) + '”.</p>'; }
    html += '<a data-hit class="all" href="' + esc(root + "search.html?q=" + encodeURIComponent(q)) + '">All ' + hits.length + " results</a>";
    box.innerHTML = html;
    box.hidden = false;
    input.setAttribute("aria-expanded", "true");
    selected = -1;
  }
  function hide() { if (box) { box.hidden = true; input.setAttribute("aria-expanded", "false"); } }

  if (input) {
    input.addEventListener("focus", function () { loadIndex(function () {}); });
    input.addEventListener("input", function () {
      var q = input.value;
      if (!q.trim()) { hide(); return; }
      loadIndex(function () { if (input.value === q) { show(q); } });
    });
    input.addEventListener("keydown", function (ev) {
      var all = links();
      if (ev.key === "ArrowDown") { ev.preventDefault(); if (box.hidden && input.value) { show(input.value); } select(Math.min(selected + 1, all.length - 1)); }
      else if (ev.key === "ArrowUp") { ev.preventDefault(); select(Math.max(selected - 1, -1)); }
      else if (ev.key === "Enter") {
        if (selected >= 0 && all[selected]) { ev.preventDefault(); location.href = all[selected].href; }
      } else if (ev.key === "Escape") { hide(); input.blur(); }
    });
    document.addEventListener("click", function (ev) { if (!ev.target.closest || !ev.target.closest(".search")) { hide(); } });
  }
  document.addEventListener("keydown", function (ev) {
    var tag = (ev.target.tagName || "").toLowerCase();
    if (ev.key === "/" && input && tag !== "input" && tag !== "textarea" && !ev.ctrlKey && !ev.metaKey && !ev.altKey) {
      ev.preventDefault();
      input.focus();
      input.select();
    }
  });

  /* ---- The full results page (search.html?q=...) ---- */
  var page = document.getElementById("search-page");
  if (page) {
    var match = /[?&]q=([^&]*)/.exec(location.search);
    var q = match ? decodeURIComponent(match[1].replace(/\+/g, " ")) : "";
    if (input) { input.value = q; }
    if (!q) { page.innerHTML = "<p>Type in the search box above.</p>"; return; }
    loadIndex(function () {
      var hits = search(q);
      var words = terms(q);
      var html = "<p>" + hits.length + " results for “" + esc(q) + "”.</p>";
      hits.slice(0, 300).forEach(function (h) {
        html += '<div class="hit"><a href="' + esc(root + h.e.url) + '">' + highlight(h.e.title, words) + '</a><span class="badge b-pgm kind">' +
                KIND_TITLE[h.e.kind] + "</span>" + "<p>" + highlight(h.e.kind === "heading" ? h.e.extra + ": " + snippet(h.e, words) : snippet(h.e, words), words) + "</p></div>";
      });
      page.innerHTML = html;
      document.title = "Search: " + q + " - Encoded Logistics";
    });
  }
})();
