# Placeholder art for typed (fluid / pressurized) storage features that the typed-drives handoff didn't cover, until final
# art: the Point-to-Point Link's Fluids and Pressurized faces and models (recoloured from the Items link: teal for fluids,
# brass for pressurized; docs/TEXTURE_STYLE.md ramps kept by shifting hue only), its two screen icons (a droplet, a
# cylinder) and the Resource Entry item's droplet. Run from the repository root: python tools/typed_placeholders.py
import colorsys, json, os
from PIL import Image

A = 'src/main/resources/assets/encodedlogistics/'
HUES = {'fluids': 185 / 360.0, 'pressurized': 32 / 360.0}


def rehue(src, dst, hue):
    im = Image.open(src).convert('RGBA')
    out = im.copy()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a == 0 or max(r, g, b) - min(r, g, b) < 40:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            r2, g2, b2 = colorsys.hsv_to_rgb(hue, s, v)
            out.putpixel((x, y), (round(r2 * 255), round(g2 * 255), round(b2 * 255), a))
    out.save(dst)


def p2p():
    for kind, hue in HUES.items():
        for end in ('in', 'out'):
            for glow in ('', '_glow'):
                rehue(A + 'textures/block/part/p2p/items_%s%s.png' % (end, glow), A + 'textures/block/part/p2p/%s_%s%s.png' % (kind, end, glow), hue)
            for linked in ('', '_linked'):
                text = open(A + 'models/part/p2p_items_%s%s.json' % (end, linked), encoding='utf-8').read()
                open(A + 'models/part/p2p_%s_%s%s.json' % (kind, end, linked), 'w', encoding='utf-8', newline='\n').write(
                    text.replace('p2p/items_', 'p2p/%s_' % kind))


def ramp(*hexes):
    return [tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) for h in hexes]


WATER = ramp('#0E4A52', '#16707A', '#2296A0', '#46BCC4', '#8AE0E4')
BRASS = ramp('#5A3410', '#8A5420', '#C07A30', '#E8A04A', '#FFC878')
STEEL = ramp('#373C44', '#555B65', '#79808A', '#A3A9B1', '#D3D7DB')


def droplet(colours):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    rows = [(2, 7, 8), (3, 7, 8), (4, 6, 9), (5, 6, 9), (6, 5, 10), (7, 5, 10), (8, 4, 11), (9, 4, 11), (10, 4, 11), (11, 4, 11), (12, 5, 10), (13, 6, 9)]
    for y, x0, x1 in rows:
        for x in range(x0, x1 + 1):
            c = colours[2]
            if x in (x0, x1) or y == 13:
                c = colours[1]
            if y == 13 or (x == x1 and y > 9):
                c = colours[0]
            im.putpixel((x, y), c + (255,))
    for x, y in ((6, 8), (6, 9), (7, 7)):
        im.putpixel((x, y), colours[4] + (255,))
    im.putpixel((6, 10), colours[3] + (255,))
    return im


def cylinder():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(4, 15):
        for x in range(5, 11):
            c = BRASS[2] if x in (6, 7) else BRASS[1] if x in (5, 8, 9) else BRASS[0]
            if y == 14:
                c = BRASS[0]
            im.putpixel((x, y), c + (255,))
        im.putpixel((6, y), BRASS[3] + (255,))
    # Valve on top: a steel neck and a wheel.
    for x in range(7, 9):
        im.putpixel((x, 3), STEEL[2] + (255,))
    for x in range(5, 11):
        im.putpixel((x, 1), STEEL[3] + (255,))
        im.putpixel((x, 2), STEEL[1] + (255,))
    im.putpixel((5, 1), STEEL[4] + (255,))
    return im


if __name__ == '__main__':
    p2p()
    droplet(WATER).save(A + 'textures/gui/sprites/p2p/type_fluids.png')
    cylinder().save(A + 'textures/gui/sprites/p2p/type_pressurized.png')
    droplet(ramp('#16306E', '#1E4FA8', '#2D6FD2', '#4F95EE', '#8CC4FF')).save(A + 'textures/item/resource_entry.png')
    print('ok')
