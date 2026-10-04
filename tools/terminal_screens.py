"""Screens drawn like the Access / Fabrication Terminal blocks' (textures/block/access_terminal/screen.png): one texel
per pixel, a header bar with its cursor, rows of item cells over dark separator rows, the same five greens (and the
fabrication terminal's gold output). 16 x 16 animated frames (8, interpolated), the picture in the top-left corner:
   rack_console_screen.png      9 x 6  - the Rack Console's lid (Access Terminal)
   rack_console_screen_fab.png  9 x 6  - the same with a Memory Die (Fabrication Terminal)
   terminal_desk/crt_screen_on.png 10 x 8 - the Terminal Desk's CRT (a command screen: text lines and a prompt)
Run from the repo root."""
import random
from PIL import Image

HEAD, CURSOR, BRIGHT, MID, DARK, GOLD = '#127a57', '#c8ffe9', '#5cf0b8', '#1fb582', '#0b3a2c', '#f5de8a'
FRAMES = 8
TEX = 'src/main/resources/assets/encodedlogistics/textures/block/'


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def save(frames, w, h, path, frametime=3):
    im = Image.new('RGBA', (16, 16 * FRAMES), (0, 0, 0, 0))
    for f, grid in enumerate(frames):
        for y in range(h):
            for x in range(w):
                im.putpixel((x, f * 16 + y), rgb(grid[y][x]))
    im.save(TEX + path)
    open(TEX + path + '.mcmeta', 'w').write('{\n  "animation": {\n    "frametime": %d,\n    "interpolate": true\n  }\n}\n' % frametime)


def access(w, h, seed):
    """Header (cursor in it), then item rows over separators; a scrollbar down the right; cells twinkle."""
    rnd = random.Random(seed)
    cells = [[rnd.choice([HEAD, MID, MID, BRIGHT, DARK]) for _ in range(w - 1)] for _ in range(h)]
    frames = []
    for f in range(FRAMES):
        g = [[DARK] * w for _ in range(h)]
        g[0] = [HEAD] * w
        g[0][1] = CURSOR
        for y in range(1, h):
            if y % 2 == 1:
                for x in range(w - 1):
                    c = cells[y][x]
                    if rnd.random() < 0.18:
                        c = rnd.choice([MID, BRIGHT, HEAD])
                    g[y][x] = c
            g[y][w - 1] = HEAD
        thumb = 1 + (f // 2) % (h - 2)
        g[thumb][w - 1] = BRIGHT
        frames.append(g)
    return frames


def fabrication(w, h, seed):
    """Header, a row of stored items as dots, then the crafting grid, its arrow and the gold output."""
    rnd = random.Random(seed)
    frames = []
    for f in range(FRAMES):
        g = [[DARK] * w for _ in range(h)]
        g[0] = [HEAD] * w
        for x in range(0, w, 2):
            g[1][x] = rnd.choice([BRIGHT, MID, BRIGHT])
        for y in range(3, 6):
            for x in range(3):
                g[y][x] = MID
        g[3 + f % 3][f // 3 % 3] = BRIGHT
        g[4][4] = MID
        g[4][5] = BRIGHT if f % 2 else MID
        for y in range(3, 6):
            g[y][7] = GOLD
        g[4][8] = '#e8c24a'
        frames.append(g)
    return frames


def command(w, h, seed):
    """A command screen: the header, three lines of text (runs of cells), the prompt with a blinking cursor."""
    rnd = random.Random(seed)
    lines = []
    for _ in range(3):
        row, x = [DARK] * w, rnd.randint(0, 1)
        while x < w:
            run = rnd.randint(1, 3)
            for i in range(x, min(w, x + run)):
                row[i] = rnd.choice([MID, MID, BRIGHT])
            x += run + 1
        lines.append(row)
    frames = []
    for f in range(FRAMES):
        g = [[DARK] * w for _ in range(h)]
        g[0] = [HEAD] * w
        g[0][1] = CURSOR
        g[2], g[3], g[5] = list(lines[0]), list(lines[1]), list(lines[2])
        g[7][0] = MID
        if f % 4 < 2:
            g[7][2] = CURSOR
        frames.append(g)
    return frames


save(access(9, 6, 7), 9, 6, 'rack_device/rack_console_screen.png')
save(fabrication(9, 6, 11), 9, 6, 'rack_device/rack_console_screen_fab.png')
save(command(10, 8, 5), 10, 8, 'terminal_desk/crt_screen_on.png', 5)
print('ok')
