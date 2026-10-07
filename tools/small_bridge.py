# The Small Wireless Bridge at Minecraft-size pixels (docs/TEXTURE_STYLE.md: steel ramp, light from the top-left, structural
# shading, one texel per model pixel): a 10 x 10 x 4 module on a machine's face with a stubby antenna and a 2 x 2 LED
# window. Writes its textures, its block models (body and the three LEDs) and its item model (the body, centred, shown
# the size of a block in GUIs). Run from the repository root: python tools/small_bridge.py
import json, os
from PIL import Image

A = 'src/main/resources/assets/encodedlogistics/'
T = A + 'textures/block/small_wireless_bridge/'
STEEL = ['#1F2228', '#2B2F36', '#373C44', '#454B54', '#555B65', '#666D77', '#79808A', '#8D949D', '#A3A9B1', '#BBC0C6', '#D3D7DB']


def c(step):
    h = STEEL[step]
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def front():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(3, 13):
        for x in range(3, 13):
            im.putpixel((x, y), c(7))
    for i in range(3, 13):
        im.putpixel((i, 3), c(9))   # top rail, lit
        im.putpixel((3, i), c(9))   # left rail, lit
        im.putpixel((i, 12), c(5))  # bottom rail, shaded
        im.putpixel((12, i), c(5))  # right rail, shaded
    im.putpixel((3, 3), c(10))
    im.putpixel((12, 12), c(4))
    im.putpixel((12, 3), c(7))
    im.putpixel((3, 12), c(7))
    # The LED window (9-10, 5-6): recessed, its shadow cast down-right from the lit rim.
    for x, y in ((9, 5), (10, 5), (9, 6), (10, 6)):
        im.putpixel((x, y), c(1))
    for x in (8, 9, 10, 11):
        im.putpixel((x, 4), c(5))
    for y in (5, 6):
        im.putpixel((8, y), c(5))
        im.putpixel((11, y), c(8))
    for x in (9, 10, 11):
        im.putpixel((x, 7), c(8))
    # A raised badge (5-6, 5-6).
    for x, y in ((5, 5), (6, 5), (5, 6)):
        im.putpixel((x, y), c(9))
    im.putpixel((6, 6), c(6))
    # Two vent slots, each with its lit lower lip.
    for y in (8, 10):
        for x in range(5, 11):
            im.putpixel((x, y), c(2))
            im.putpixel((x, y + 1), c(8))
    im.save(T + 'face.png')


def side():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), c(6))
    # Bevels: the 4 px deep sides (rows 0-3 / columns 0-3 as the model maps them) lit on top, shaded below.
    for x in range(16):
        im.putpixel((x, 0), c(8))
        im.putpixel((x, 3), c(4))
    for y in range(16):
        im.putpixel((0, y), c(8))
        im.putpixel((3, y), c(4))
    # The antenna (14, 0-3): dark steel with a bright tip.
    for y in range(4):
        im.putpixel((14, y), c(3))
    im.putpixel((14, 0), c(9))
    im.save(T + 'side.png')


def leds():
    colours = {'linked': ((80, 194, 236), (163, 255, 250)), 'fault': ((236, 127, 39), (255, 187, 119)),
               'unlinked': ((236, 193, 56), (255, 228, 137))}
    for name, (base, glint) in colours.items():
        frames = 2 if name == 'unlinked' else 1
        im = Image.new('RGBA', (16, 16 * frames), (0, 0, 0, 0))
        for x, y in ((9, 5), (10, 5), (9, 6), (10, 6)):
            im.putpixel((x, y), base + (255,))
        im.putpixel((9, 5), glint + (255,))
        im.save(T + 'led_%s.png' % name)


def models():
    body = {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
            'textures': {'face': 'encodedlogistics:block/small_wireless_bridge/face', 'side': 'encodedlogistics:block/small_wireless_bridge/side',
                         'particle': 'encodedlogistics:block/small_wireless_bridge/side'},
            'elements': [
                {'name': 'body', 'from': [3, 3, 0], 'to': [13, 13, 4], 'faces': {
                    'south': {'texture': '#face', 'uv': [3, 3, 13, 13]},
                    'north': {'texture': '#side', 'uv': [3, 3, 13, 13]},
                    'east': {'texture': '#side', 'uv': [0, 3, 4, 13]},
                    'west': {'texture': '#side', 'uv': [0, 3, 4, 13]},
                    'up': {'texture': '#side', 'uv': [3, 0, 13, 4]},
                    'down': {'texture': '#side', 'uv': [3, 0, 13, 4]}}},
                {'name': 'antenna', 'from': [10, 13, 1], 'to': [11, 16, 2], 'faces': {
                    f: {'texture': '#side', 'uv': [14, 0, 15, 3] if f not in ('up', 'down') else [14, 0, 15, 1]}
                    for f in ('north', 'south', 'east', 'west', 'up')}}]}
    mdir = A + 'models/block/small_wireless_bridge/'
    json.dump(body, open(mdir + 'small_wireless_bridge.json', 'w', newline='\n'), indent=1)
    for name in ('linked', 'fault', 'unlinked'):
        led = {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
               'textures': {'led': 'encodedlogistics:block/small_wireless_bridge/led_' + name,
                            'particle': 'encodedlogistics:block/small_wireless_bridge/led_' + name},
               'elements': [{'name': 'led', 'from': [3, 3, 4.01], 'to': [13, 13, 4.01],
                             'faces': {'south': {'texture': '#led', 'uv': [3, 3, 13, 13]}},
                             'neoforge_data': {'block_light': 15, 'sky_light': 15}, 'shade_direction_override': 'up'}]}
        json.dump(led, open(mdir + 'led_%s.json' % name, 'w', newline='\n'), indent=1)


if __name__ == '__main__':
    front()
    side()
    leds()
    models()
    print('ok')
