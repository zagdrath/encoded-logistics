# The depth pass: shades the flat fields of a block texture the way the Drive Bay's are (docs/TEXTURE_STYLE.md 5), in
# whole steel-ramp steps and with no noise. Every run of one steel step (a "field") is shaded by what it is:
#   - recessed (darker than what surrounds it): a cast shadow under the top and left overhang, darker corners and
#     bottom/right edges, and a lit centre on a big enough field - the Drive Bay's pockets;
#   - raised (lighter than what surrounds it): it catches the light along its top and left edges and falls off along
#     its bottom and right edges - a plate standing proud.
# A face's main panel (a mostly rectangular field of MAIN_AREA pixels or more) is sunk to a dark pocket first, so the face reads as the
# Drive Bay's does: a lit frame and raised detail around a deep, shaded interior.
# Pixels that aren't steel (dye, status lights, screens) and pixels lit by a _glow overlay are left alone. The texture's
# edge counts as a field's edge, so don't run it over connected textures (the Scheduler's, the planes').
#
# It works in place: run it once over textures the exporters just wrote (python tools/depth_pass.py --all, or name the
# files), never twice over the same file. --preview <dir> writes before/after sheets there instead of changing anything.
import os, sys
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from el_style import G

BLOCK = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'encodedlogistics',
                     'textures', 'block')

# The flat textures this pass has been run over (relative to textures/block); --all runs it over them, for after the
# exporters have rewritten them. (The Segment Isolator's halves are drawn shaded by export_feature.iso_half instead:
# only a corner of them shows.)
SHADED = [
    'gateway/face', 'network_casing',
    'network_casing_port', 'power_inlet/front', 'access_terminal/back', 'fabrication_terminal/back', 'schematic_encoder/back', 'relay_antenna/top', 'relay_antenna/front',
    'relay_antenna/side', 'network_bridge/front', 'network_bridge/side', 'fabricator/front', 'fabricator/side', 'fabricator/top',
]

MIN_AREA = 12
MAIN_AREA = 40       # a field this big is the face's main panel: it sinks into a pocket, as the Drive Bay's does
POCKET = 3           # the step a sunk panel sits at (the Drive Bay's pockets are 2-3 with a step-0 shadow)


def step_of(c):
    """The steel step a colour is, or None when it isn't one (exact ramp colours only: dye and lights aren't)."""
    for i, s in enumerate(G):
        if max(abs(a - b) for a, b in zip(c[:3], s)) <= 3:
            return i
    return None


def shade(im, locked=None):
    w, h = im.size
    px = im.load()
    steps = [[None] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if c[3] == 255 and not (locked and locked[y][x]):
                steps[y][x] = step_of(c)
    seen = [[False] * w for _ in range(h)]
    out = [[steps[y][x] for x in range(w)] for y in range(h)]
    for y0 in range(h):
        for x0 in range(w):
            s = steps[y0][x0]
            if s is None or seen[y0][x0]:
                continue
            region, stack = [], [(x0, y0)]
            seen[y0][x0] = True
            while stack:
                x, y = stack.pop()
                region.append((x, y))
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= nx < w and 0 <= ny < h and not seen[ny][nx] and steps[ny][nx] == s:
                        seen[ny][nx] = True
                        stack.append((nx, ny))
            xs = [p[0] for p in region]
            ys = [p[1] for p in region]
            if len(region) < MIN_AREA or max(xs) - min(xs) < 3 or max(ys) - min(ys) < 3:
                continue
            inside = set(region)
            around = []
            for x, y in region:
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= nx < w and 0 <= ny < h and (nx, ny) not in inside and px[nx, ny][3] == 255:
                        around.append(steps[ny][nx] if steps[ny][nx] is not None else s)
            if not around:
                continue
            recessed = sum(around) / len(around) < s
            fill = len(region) / ((max(xs) - min(xs) + 1) * (max(ys) - min(ys) + 1))
            if len(region) >= MAIN_AREA and fill >= 0.6 and s > POCKET:
                s, recessed = POCKET, True

            def run(x, y, dx, dy):
                """How far the field goes on from (x, y) toward (dx, dy)."""
                n = 0
                while True:
                    x, y = x + dx, y + dy
                    if (x, y) not in inside:
                        return n
                    n += 1

            big = min(max(xs) - min(xs), max(ys) - min(ys)) >= 6
            for x, y in region:
                t, b, l, r = run(x, y, 0, -1), run(x, y, 0, 1), run(x, y, -1, 0), run(x, y, 1, 0)
                edge = lambda d, n: d <= n
                if recessed:
                    if edge(t, 0) or edge(l, 0):
                        d = -2 if s >= 3 else -1          # the cast shadow under the overhang
                    elif edge(b, 0) or edge(r, 0) or (edge(t, 1) and edge(l, 1)):
                        d = -1                            # the far edges and the corner deepen
                    elif big and min(t, b, l, r) >= 3:
                        d = 1                             # a big pocket's lit centre
                    else:
                        d = 0
                else:
                    if edge(t, 0) or edge(l, 0):
                        d = 1                             # the lit top and left edges
                    elif edge(b, 0) or edge(r, 0):
                        d = -1                            # the shaded bottom and right edges
                    else:
                        d = 0
                out[y][x] = max(0, min(10, s + d))
    for y in range(h):
        for x in range(w):
            if out[y][x] is not None and out[y][x] != steps[y][x]:
                px[x, y] = G[out[y][x]] + (255,)
    return im


def glow_mask(path):
    g = path[:-4] + '_glow.png'
    if not os.path.exists(g):
        return None
    im = Image.open(g).convert('RGBA')
    return [[im.getpixel((x, y))[3] > 0 for x in range(im.width)] for y in range(im.height)]


def apply(path):
    im = Image.open(path).convert('RGBA')
    return shade(im, glow_mask(path))


def preview(names, out_dir):
    tiles = []
    for name in names:
        path = os.path.join(BLOCK, name + '.png')
        before = Image.open(path).convert('RGBA')
        after = apply(path)
        pair = Image.new('RGBA', (2 * 128 + 8, 128), (40, 40, 40, 255))
        for i, im in enumerate((before, after)):
            frame = im.crop((0, 0, im.width, im.width)).resize((128, 128), Image.NEAREST)
            pair.paste(frame, (i * 136, 0), frame)
        tiles.append(pair)
    cols = 3
    sheet = Image.new('RGBA', (cols * 272, ((len(tiles) + cols - 1) // cols) * 136), (24, 24, 24, 255))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * 272, (i // cols) * 136))
    sheet.save(os.path.join(out_dir, 'depth_preview.png'))


if __name__ == '__main__':
    args = sys.argv[1:]
    if args[:1] == ['--preview']:
        preview(args[2:] or SHADED, args[1])
    elif args == ['--all']:
        for name in SHADED:
            path = os.path.join(BLOCK, name + '.png')
            apply(path).save(path)
        print('shaded', len(SHADED), 'textures')
    elif not args:
        sys.exit('usage: depth_pass.py --all | <texture.png>... | --preview <dir> [name...]')
    else:
        for path in args:
            apply(path).save(path)
            print('shaded', path)
