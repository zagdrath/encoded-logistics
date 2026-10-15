# Item art for the modular chassis parts (rack/chassis/ChassisPart), in three families a player can tell apart at a glance:
#   Blade Enclosure        - tall silver blade sleds, an accent label tab at the top, an ejector lever across the bottom;
#                            each type has its own face detail (heatsink, DIMMs, drive carriers, crafting grid, ports, fan, LCD).
#   Chassis Switch         - wide line cards: a PCB edge behind a navy faceplate with silver ejector tabs at both ends;
#                            ports per type (RJ45 rows, SFP cages, VLAN-tagged ports, an ASIC, console + LEDs, a PSU fan).
#   Optical Transport Shelf - narrow dark teal cards with yellow LC connectors and fibre jumpers looping out to the side,
#                            wavelength (rainbow) LEDs.
# docs/TEXTURE_STYLE.md: steel ramp + dye ramps, flat fields, top-left light, outline in the material's darkest step.
# Writes textures/item/<id>.png (the item models already point there). Run from the repository root:
#   python tools/chassis_part_items.py [preview.png]
import os, sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import el_kit as K

A = 'src/main/resources/assets/encodedlogistics/textures/item/'
H = lambda h: tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))
S = K.S
# dye ramps (docs/TEXTURE_STYLE.md 2.2), -3 .. +3
def ramp(*hx): return [H(h) for h in hx]
ORANGE = ramp('#7B0F02', '#A02C0A', '#C65217', '#EC7F27', '#FF9B44', '#FFAB5D', '#FFBB77')
MAGENTA = ramp('#6C1E70', '#922F93', '#B543B0', '#D85BCC', '#FB83E6', '#FF9EE7', '#FFB8E7')
LBLUE = ramp('#17577B', '#2678A0', '#399CC6', '#50C2EC', '#70E8FF', '#89F9FF', '#A3FFFA')
YELLOW = ramp('#7B360B', '#A05D16', '#C68B25', '#ECC138', '#FFD756', '#FFDE70', '#FFE489')
LIME = ramp('#1C6E07', '#398F11', '#5DB11E', '#87D32E', '#ADF54E', '#C3FF6B', '#D1FF84')
CYAN = ramp('#074A58', '#0F6574', '#1A838F', '#27A2AA', '#41C5C0', '#60E0CF', '#85FCDF')
PURPLE = ramp('#3E1167', '#571D87', '#732DA6', '#913FC6', '#BF60E6', '#E784FF', '#F69EFF')
RED = ramp('#610E23', '#7E1929', '#9C262E', '#BA3B37', '#D85E55', '#F6877A', '#FFA698')
GREEN = ramp('#1F450C', '#355A14', '#4E701F', '#6A852B', '#819A41', '#9AB05C', '#B3C57B')
NAVY = ramp('#0E1430', '#162042', '#1F2D5A', '#2B3D78', '#3B5296', '#5872B8', '#7A92D2')
# status lights (2.4) and the 8 channel colours the shelf / multiplexer screens use
LED_G, LED_GL = H('#3CE05A'), H('#B5FFB0')
LED_A, LED_AL = H('#F08A2A'), H('#FFC890')
LED_R = H('#E5483C')
LED_OFF = H('#1C2622')
WAVE = [H(x) for x in ('#B02E26', '#EC7F27', '#FED83D', '#87D32E', '#50C2EC', '#3C44AA', '#8932B8', '#D85BCC')]


def px(im, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        im.putpixel((x, y), (S[c] if isinstance(c, int) else tuple(c)) + (255,))


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, c)


def box(im, x0, y0, x1, y1, field, lit, shade, corner=None):
    """A bevelled box (inclusive): flat field, lit top / left, shaded bottom / right."""
    rect(im, x0, y0, x1, y1, field)
    for x in range(x0, x1 + 1): px(im, x, y0, lit); px(im, x, y1, shade)
    for y in range(y0, y1 + 1): px(im, x0, y, lit); px(im, x1, y, shade)
    px(im, x1, y0, field); px(im, x0, y1, field)
    if corner: px(im, x0, y0, corner)


def outline(im, col, mask=None):
    """Outline every transparent pixel next to an opaque one (of `mask`, default the image) in `col`."""
    m = mask or im
    out = im.copy()
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and m.getpixel((x + dx, y + dy))[3]
                                                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), tuple(col) + (255,))
    return out


def new(): return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


# ---------------------------------------------------------------- Blade Enclosure: silver sleds
def blade(accent, detail, lever=True):
    im = new()
    box(im, 4, 0, 11, 12, 8, 9, 6, corner=10)          # the sled face, x 4-11, y 0-12
    px(im, 7, 0, 10); px(im, 8, 0, 10)                   # glint on the top rail
    rect(im, 5, 1, 10, 1, accent[3]); px(im, 5, 1, accent[4]); px(im, 10, 1, accent[2])   # coloured pull tab / label
    px(im, 5, 2, 7); rect(im, 6, 2, 10, 2, 7)            # shadow line under the tab
    detail(im)
    if lever:                                            # ejector lever: a dark bar across the foot with a grip on the right
        rect(im, 4, 13, 11, 13, 4); rect(im, 4, 14, 12, 14, 3); px(im, 4, 13, 5)
        rect(im, 12, 12, 13, 13, 5); px(im, 12, 12, 7); px(im, 13, 14, 2); px(im, 5, 13, 6)
    im = outline(im, S[1])
    return im


def d_compute(im):                                       # CPU heatsink: fin stack, two blue LEDs
    box(im, 5, 3, 10, 9, 6, 8, 4)
    for y in (4, 6, 8): rect(im, 6, y, 9, y, 3)
    for y in (5, 7): rect(im, 6, y, 9, y, 9)
    px(im, 5, 11, LBLUE[4]); px(im, 6, 11, LBLUE[3]); px(im, 10, 11, LED_G)


def d_memory(im):                                        # four DIMM heat spreaders
    for x in (5, 7, 9):
        rect(im, x, 3, x + 1, 10, MAGENTA[3]); rect(im, x, 3, x, 10, MAGENTA[4]); px(im, x, 3, MAGENTA[5])
        px(im, x + 1, 10, MAGENTA[1])
        for y in (5, 8): px(im, x + 1, y, MAGENTA[2])
    px(im, 10, 11, LED_G); px(im, 5, 11, MAGENTA[4])


def d_storage(im):                                       # three drive carriers, each with a handle slot and activity LED
    for y in (3, 6, 9):
        box(im, 5, y, 10, y + 1, 7, 9, 5)
        rect(im, 6, y + 1, 8, y + 1, 2); px(im, 10, y, LED_G if y != 6 else LED_A)
        px(im, 6, y, LIME[3])
    px(im, 10, 11, LED_G)


def d_fabrication(im):                                   # 3x3 crafting grid, cells lit orange
    rect(im, 5, 3, 10, 9, 7)
    for x in (5, 7, 9): rect(im, x, 3, x, 9, 3)
    for y in (3, 5, 7, 9): rect(im, 5, y, 10, y, 3)
    rect(im, 5, 9, 10, 9, 3); px(im, 10, 3, 3)
    for cx in (6, 8, 10):
        for cy in (4, 6, 8): px(im, cx, cy, 8)
    px(im, 8, 6, ORANGE[4]); px(im, 6, 8, ORANGE[3]); px(im, 10, 4, ORANGE[3])
    px(im, 5, 11, ORANGE[4]); px(im, 6, 11, ORANGE[3]); px(im, 10, 11, LED_G)


def d_interconnect(im):                                  # two columns of ports with purple link LEDs
    for y in (3, 6, 9):
        for x in (5, 8):
            rect(im, x, y, x + 2, y + 1, 1); rect(im, x, y + 1, x + 2, y + 1, 2); px(im, x + 1, y, 0)
            px(im, x + 2, y + 2 if y < 9 else y + 2, 7)
        px(im, 7, y, PURPLE[4])
    px(im, 10, 11, LED_G); px(im, 5, 11, PURPLE[4]); px(im, 6, 11, PURPLE[3])


def d_psu(im):                                           # round fan grille over a hub, power inlet, yellow lightning label
    for y in range(3, 9):
        for x in range(5, 11):
            if (x in (5, 10)) and (y in (3, 8)): continue
            px(im, x, y, 2 if y % 2 else 5)
    for (x, y) in ((5, 3), (10, 3), (5, 8), (10, 8)): px(im, x, y, 7)
    px(im, 6, 3, 4); px(im, 5, 4, 4)
    px(im, 7, 5, 9); px(im, 8, 5, 7); px(im, 7, 6, 7); px(im, 8, 6, 6)   # hub
    box(im, 6, 9, 9, 11, 1, 0, 6); px(im, 7, 10, 7); px(im, 8, 10, 7)    # power inlet
    px(im, 5, 10, YELLOW[4]); px(im, 5, 11, YELLOW[3]); px(im, 10, 11, LED_G)


def d_mgmt(im):                                          # small status LCD, mgmt port, red/green LEDs
    box(im, 5, 3, 10, 6, CYAN[0], 2, 7)
    rect(im, 6, 4, 9, 4, LED_G); rect(im, 6, 5, 7, 5, (34, 160, 60))
    rect(im, 5, 8, 7, 9, 1); px(im, 6, 8, 0); rect(im, 5, 9, 7, 9, 2)
    px(im, 9, 8, LED_G); px(im, 10, 8, LED_R); px(im, 9, 9, 6); px(im, 10, 9, 6)
    px(im, 5, 11, RED[4]); px(im, 10, 11, LED_G)


# ---------------------------------------------------------------- Chassis Switch: navy line cards
def line_card(ports, board=None):
    im = new()
    rect(im, 2, 3, 13, 6, GREEN[1])                      # the card's PCB behind the faceplate
    rect(im, 2, 3, 13, 3, GREEN[2]); px(im, 2, 4, GREEN[2])
    for x in range(4, 12, 2): px(im, x, 3, YELLOW[3])    # edge-connector fingers along the back
    if board: board(im)
    box(im, 1, 7, 14, 12, NAVY[3], NAVY[5], NAVY[1], corner=NAVY[6])   # navy faceplate
    for x in (0, 15):                                    # silver ejector tabs at both ends
        rect(im, x, 7, x, 12, 7); px(im, x, 7, 9); px(im, x, 12, 5)
    px(im, 1, 8, 8); px(im, 14, 11, 6)                   # captive screws
    ports(im)
    return outline(im, NAVY[0])


def rj45(im, x, y):                                      # 2 x 2 RJ45 socket
    px(im, x, y, 0); px(im, x + 1, y, 1); px(im, x, y + 1, 1); px(im, x + 1, y + 1, 2)


def chip(im, x0, y0, x1, y1):
    box(im, x0, y0, x1, y1, 2, 4, 1)


def p_access(im):                                        # a row of RJ45s with link LEDs above
    for i, x in enumerate((3, 5, 7, 9, 11)):
        px(im, x, 8, LED_G if i != 3 else LED_A); rect(im, x, 9, x, 11, 0); px(im, x, 11, 1)
        px(im, x + 1, 9, NAVY[2])
    for x in (4, 6, 8, 10, 12): px(im, x, 9, NAVY[2])


def b_access(im):
    for x in (4, 7, 10): rect(im, x, 4, x + 1, 5, 2); px(im, x, 4, 4)


def p_uplink(im):                                        # SFP cages (silver frames) with blue link LEDs
    for x in (3, 8):
        box(im, x, 8, x + 3, 11, 0, 9, 6); px(im, x + 1, 9, 1); px(im, x + 2, 9, 1); px(im, x + 1, 10, 1); px(im, x + 2, 10, 1)
    px(im, 13, 9, LBLUE[4]); px(im, 13, 10, LBLUE[3])


def b_uplink(im):
    chip(im, 5, 4, 7, 5); rect(im, 9, 4, 11, 4, YELLOW[2])


def p_routing(im):                                       # two ports and a purple routing LED
    rj45(im, 3, 9); rj45(im, 6, 9)
    px(im, 10, 9, PURPLE[4]); px(im, 11, 9, PURPLE[3]); px(im, 10, 10, LED_G)


def b_routing(im):                                       # a big ASIC under a heatsink filling the board
    box(im, 4, 2, 11, 6, 7, 9, 5)
    for x in (5, 7, 9): rect(im, x, 3, x, 5, 4)
    px(im, 6, 4, PURPLE[3]); px(im, 10, 4, PURPLE[3])


def p_trunk(im):                                         # tagged ports: each socket under a VLAN colour tag
    for x, c in zip((3, 6, 9, 12), (RED, YELLOW, LIME, LBLUE)):
        px(im, x, 8, c[4]); px(im, x - 1 if x == 12 else x + 1, 8, c[2])
        rj45(im, x, 9) if x != 12 else rj45(im, 11, 9)
    px(im, 11, 8, LBLUE[4]); px(im, 12, 8, LBLUE[2]); px(im, 12, 9, 1)


def b_trunk(im):
    for x in (4, 9): chip(im, x, 4, x + 2, 5)


def p_supervisor(im):                                    # LCD, console port, three status LEDs in a column
    box(im, 2, 8, 6, 10, CYAN[0], NAVY[1], NAVY[4]); rect(im, 3, 9, 5, 9, LED_G); px(im, 5, 9, (34, 160, 60))
    rect(im, 8, 9, 9, 10, LBLUE[1]); px(im, 8, 9, LBLUE[0])           # console (serial) port
    px(im, 12, 8, LED_G); px(im, 12, 9, LED_G); px(im, 12, 10, LED_A)
    px(im, 3, 11, NAVY[5]); px(im, 4, 11, NAVY[5])


def b_supervisor(im):
    box(im, 6, 3, 9, 6, 2, 4, 1); px(im, 7, 4, 6); rect(im, 11, 4, 12, 5, 2)


def chassis_psu():                                       # a squat navy power module: fan grille, inlet, handle
    im = new()
    rect(im, 4, 1, 11, 2, 6); rect(im, 4, 1, 11, 1, 8); px(im, 4, 2, 4); px(im, 11, 2, 4)   # carry handle
    box(im, 1, 3, 14, 13, NAVY[3], NAVY[5], NAVY[1], corner=NAVY[6])
    for x in (5, 6, 7, 8, 9, 10): px(im, x, 2, 0)
    im2 = im.copy()
    for y in range(3):
        for x in range(5, 11):
            im2.putpixel((x, 2), (0, 0, 0, 0))
    im = im2
    # fan grille: a round grille on the left
    for (x, y) in [(3, 5), (4, 5), (5, 5), (6, 5), (2, 6), (7, 6), (2, 7), (7, 7), (2, 8), (7, 8), (2, 9), (7, 9),
                   (3, 10), (4, 10), (5, 10), (6, 10)]: px(im, x, y, 6)
    rect(im, 3, 6, 6, 9, 1)
    for y in (6, 8): rect(im, 3, y, 6, y, 4)
    px(im, 4, 7, 7); px(im, 5, 8, 7); px(im, 4, 8, 5); px(im, 5, 7, 5)
    px(im, 3, 5, 8); px(im, 2, 6, 8)
    # power inlet (C14) on the right, a yellow lightning label and a green power LED
    box(im, 9, 7, 12, 10, 1, 0, 6); px(im, 10, 8, 7); px(im, 11, 8, 7)
    px(im, 9, 7, 0); px(im, 12, 7, 0)
    px(im, 10, 5, YELLOW[4]); px(im, 11, 5, YELLOW[3]); px(im, 12, 5, LED_G)
    px(im, 9, 12, NAVY[5]); px(im, 10, 12, NAVY[5])
    return outline(im, NAVY[0])


# ---------------------------------------------------------------- Optical Transport Shelf: teal cards with fibre
def optical(face, fibres=()):
    im = new()
    box(im, 3, 1, 9, 14, CYAN[1], CYAN[3], CYAN[0], corner=CYAN[4])   # teal faceplate, x 3-9
    for y in (0, 15):                                    # ejector latches top and bottom
        rect(im, 4, y, 8, y, 7); px(im, 4, y, 9); px(im, 8, y, 5)
    px(im, 4, 2, 8); px(im, 8, 13, 6)                    # captive screws
    face(im)
    im = outline(im, (4, 50, 60))
    # yellow fibre jumpers: out of the connector to the right, a rounded bend, down off the bottom edge; each casts a
    # one-pixel shadow down / right (light from the top-left)
    for (y, x) in fibres:
        run = [(xx, y, YELLOW[4]) for xx in range(10, x)] + [(x, yy, YELLOW[3]) for yy in range(y + 1, 16)]
        for (xx, yy, c) in run:
            sx, sy = (xx, yy + 1) if yy == y else (xx + 1, yy)
            if im.getpixel((min(sx, 15), min(sy, 15)))[3] == 0: px(im, sx, sy, YELLOW[0])
        for (xx, yy, c) in run: px(im, xx, yy, c)
    return im


def lc(im, x, y):                                        # LC duplex connector seated in the faceplate: two yellow boots
    px(im, x, y, YELLOW[4]); px(im, x, y + 1, YELLOW[2]); px(im, x + 1, y, YELLOW[3]); px(im, x + 1, y + 1, YELLOW[1])


def o_controller(im):                                    # LCD, alarm LEDs (critical / major / minor), mgmt port
    box(im, 4, 3, 8, 5, (10, 40, 30), CYAN[0], CYAN[3]); rect(im, 5, 4, 7, 4, LED_G)
    px(im, 5, 7, LED_R); px(im, 6, 7, LED_A); px(im, 7, 7, LED_G)
    rect(im, 5, 9, 7, 10, 1); px(im, 6, 9, 0); rect(im, 5, 10, 7, 10, 2)
    px(im, 7, 12, YELLOW[3]); px(im, 7, 13, YELLOW[1])   # one OSC fibre port


def o_transponder(im):                                   # client SFP on top, one wavelength LED, line LC pair at the foot
    box(im, 4, 3, 7, 5, 1, 9, 6); px(im, 5, 4, 0); px(im, 6, 4, 0)
    px(im, 8, 4, LED_G)
    px(im, 5, 7, WAVE[4]); px(im, 6, 7, WAVE[4]); px(im, 7, 7, LBLUE[1])
    rect(im, 5, 8, 7, 8, CYAN[0])
    lc(im, 6, 10)
    px(im, 8, 10, YELLOW[4]); px(im, 8, 11, YELLOW[2])


def o_mux(im):                                           # 8 channel LEDs in a rainbow column, one common fibre out
    for i, c in enumerate(WAVE):
        px(im, 5, 2 + i, c); px(im, 6, 2 + i, c); px(im, 7, 2 + i, CYAN[0])
    lc(im, 6, 11); px(im, 8, 11, YELLOW[4]); px(im, 8, 12, YELLOW[2])


def o_amplifier(im):                                     # a gain triangle, a green gain bar, in / out connectors
    for i, (y, x0, x1) in enumerate(((3, 4, 4), (4, 4, 5), (5, 4, 6), (6, 4, 7), (7, 4, 6), (8, 4, 5), (9, 4, 4))):
        rect(im, x0, y, x1, y, YELLOW[3] if i < 3 else YELLOW[2])
    px(im, 4, 3, YELLOW[5]); px(im, 4, 4, YELLOW[4])
    for y in (5, 6, 7, 8): px(im, 8, y, LED_G)
    px(im, 8, 4, LED_OFF); px(im, 8, 3, LED_OFF); px(im, 8, 8, LED_GL)
    px(im, 8, 11, YELLOW[4]); px(im, 8, 12, YELLOW[2])   # in
    px(im, 5, 11, YELLOW[3]); px(im, 5, 12, YELLOW[1])   # out (fibre runs under the card)


def o_protection(im):                                    # working + protect fibre pairs, a 1+1 switch between them
    px(im, 8, 3, YELLOW[4]); px(im, 8, 4, YELLOW[2])     # working pair (top)
    px(im, 8, 11, YELLOW[4]); px(im, 8, 12, YELLOW[2])   # protect pair (bottom)
    px(im, 4, 3, LED_G); px(im, 4, 11, LED_A)            # working active, protect standby
    # the switch: a common pole on the left, its blade thrown up to the working contact, the protect contact open
    px(im, 4, 7, 10); px(im, 4, 8, 8)
    px(im, 5, 6, 10); px(im, 6, 5, 10); px(im, 7, 4, 9)
    px(im, 7, 9, 6); px(im, 7, 10, 7)
    px(im, 6, 8, CYAN[0]); px(im, 5, 8, 6)


ITEMS = {
    'compute_blade': lambda: blade(LBLUE, d_compute),
    'memory_blade': lambda: blade(MAGENTA, d_memory),
    'storage_blade': lambda: blade(LIME, d_storage),
    'fabrication_blade': lambda: blade(ORANGE, d_fabrication),
    'interconnect_module': lambda: blade(PURPLE, d_interconnect),
    'blade_psu': lambda: blade(YELLOW, d_psu),
    'management_module': lambda: blade(RED, d_mgmt),
    'supervisor_card': lambda: line_card(p_supervisor, b_supervisor),
    'access_card': lambda: line_card(p_access, b_access),
    'uplink_card': lambda: line_card(p_uplink, b_uplink),
    'routing_card': lambda: line_card(p_routing, b_routing),
    'trunk_card': lambda: line_card(p_trunk, b_trunk),
    'chassis_psu': chassis_psu,
    'controller_card': lambda: optical(o_controller, ((12, 11),)),
    'transponder_card': lambda: optical(o_transponder, ((10, 11), (11, 13))),
    'mux_card': lambda: optical(o_mux, ((11, 12),)),
    'amplifier_card': lambda: optical(o_amplifier, ((11, 11), (12, 13))),
    'protection_card': lambda: optical(o_protection, ((3, 13), (11, 11))),
}


def preview(path):
    sc, cols = 8, 7
    names = list(ITEMS)
    out = Image.new('RGBA', (cols * (16 * sc + 8), 3 * (16 * sc + 20)), (139, 139, 139, 255))
    d = ImageDraw.Draw(out)
    for i, n in enumerate(names):
        im = Image.open(A + n + '.png').convert('RGBA').resize((16 * sc, 16 * sc), Image.NEAREST)
        x, y = (i % cols) * (16 * sc + 8), (i // cols) * (16 * sc + 20)
        if i >= 7: x, y = ((i - 7) % 6) * (16 * sc + 8), (1 if i < 13 else 2) * (16 * sc + 20)
        if i >= 13: x = (i - 13) * (16 * sc + 8)
        out.alpha_composite(im, (x, y)); d.text((x, y + 16 * sc + 4), n, fill=(0, 0, 0, 255))
    out.save(path)


if __name__ == '__main__':
    for name, make in ITEMS.items():
        make().save(A + name + '.png')
    if len(sys.argv) > 1: preview(sys.argv[1])
