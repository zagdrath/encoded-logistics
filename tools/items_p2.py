# Phase 2 items - all from Cody's Arcforge sprites (tools/arcforge_ref/), recoloured/reshaped, vanilla-style shading.
import os, random, colorsys
from PIL import Image
from el_style import H, put, img
import arc_based
from items_v3 import load, palette, remap, grain, ramp, TIERS, CYAN, wafer, module_die, drive, storage_die, L
MEMORY=ramp('#5A1450','#9A2A8A','#E05ACF','#F7B8EE')            # Memory Die / Memory Photomask accent
# module accents (Phase 2 + Phase 4): one shared card, accent + face symbol per module
MODULES={'filter':(ramp('#145A24','#22A03C','#3CE05A','#B5FFB0'),'funnel'),
         'throughput':(ramp('#6B3A08','#B05E14','#F08A2A','#FFC890'),'chevrons'),
         'fuzzy_match':(ramp('#3C5208','#7A9A14','#C8E040','#EEFAA8'),'wave'),          # Phase 4
         'redstone_control':(ramp('#5A0E0A','#8E231C','#E5483C','#FF9C90'),'torch')}    # Phase 4
NEO_RAMP=['#3A3644','#5E5970','#8A849C','#B4AEC6','#D8D2E6','#F2EEFA']   # silvery grey, faint purple
TAN_RAMP=['#121822','#1E2836','#2E3C50','#44566E','#647A94','#93A8C0']   # dark blue-grey
NEO_INGOT=['#29252F','#3A3644','#47425A','#5E5970','#7A748E','#9C96B0','#C8C2DA','#F0ECFA']
TAN_INGOT=['#0C1018','#151C26','#1C2532','#24303F','#344458','#4A5E78','#7690AE','#AFC4DA']
def sat(p): return colorsys.rgb_to_hsv(*[v/255 for v in p[:3]])[1]
def ore_overlay(src,ramp6):
    """Arcforge ore overlay: saturated ore pixels recoloured by brightness; neutral stone-shading pixels kept."""
    im=Image.open(os.path.join(arc_based.REF,src)).convert('RGBA'); out=im.copy()
    ore=[im.getpixel((x,y)) for y in range(16) for x in range(16) if im.getpixel((x,y))[3] and sat(im.getpixel((x,y)))>0.06]
    lo,hi=min(L(p) for p in ore),max(L(p) for p in ore); R=[H(c) for c in ramp6]
    for y in range(16):
        for x in range(16):
            p=im.getpixel((x,y))
            if not p[3] or sat(p)<=0.06: continue
            t=(L(p)-lo)/max(1,hi-lo)*(len(R)-1); i=min(len(R)-2,int(t)); f=t-i
            out.putpixel((x,y),tuple(round(R[i][k]+(R[i+1][k]-R[i][k])*f) for k in range(3))+(p[3],))
    return out
def raw(ramp6,seed): return grain(arc_based.recolour('raw_nickel.png',ramp6),seed,0.06)
def dust(ramp6): return arc_based.recolour('nether_quartz_dust.png',ramp6)   # Arcforge's dust heap in the raw ore's ramp
def ingot(ramp8):
    im,_=arc_based.recolour_indexed('nickel_ingot.png',ramp8); return im
def doped_silicon():
    """Silicon wafer with a redstone-doped red tint (glint and outline kept)."""
    w=wafer(); out=w.copy(); RED=(200,40,32)
    for y in range(16):
        for x in range(16):
            p=w.getpixel((x,y))
            if not p[3] or L(p[:3])<40 or L(p[:3])>215: continue
            out.putpixel((x,y),tuple(round(p[i]*0.62+RED[i]*0.38) for i in range(3))+(p[3],))
    return out
def memory_die():
    """Logic Die family (Arcforge area module) with the memory accent; the dot grid joined into word lines."""
    im=module_die('area_module',MEMORY)
    for y in (5,7,9):
        for x in range(4,9): im.putpixel((x,y),(MEMORY[2] if x%2==0 else MEMORY[1])+(255,))
    return im
def photomask_memory():
    """Matches the Phase 1 photomasks (Arcforge plate die frame): glass with a word-line / bit-line crosshatch,
       magenta strip."""
    src=load('plate_die')
    CR=ramp('#0C0D0F','#4A525C','#7C8590','#A9B1BA','#D7DDE3'); GL=ramp('#3E6F7A','#6AA0AC','#96CAD4','#C4EAF0')
    m={H('#0C0D0F'):CR[0],H('#747880'):CR[4],H('#2E3237'):CR[1],H('#484C54'):CR[2],H('#5C646C'):CR[3],H('#4A8FE0'):MEMORY[2]}
    out=remap(src,m)
    for y in range(3,13):
        for x in range(2,14):
            t=3 if x+y<11 else 2 if x+y<19 else 1
            if x-y in (2,3): t=3
            out.putpixel((x,y),GL[t]+(255,))
    PAT=H('#5A1450')
    for y in (4,7,10):
        for x in range(3,13): out.putpixel((x,y),PAT+(255,))
    for x in (4,8,12):
        for y in range(4,12): out.putpixel((x,y),PAT+(255,))
    out.putpixel((12,12),MEMORY[3]+(255,))
    return grain(out,83,0.08,keep={PAT,GL[3]})
def tantalum_capacitor():
    """Arcforge battery as a dipped tantalum capacitor: amber body, steel end caps, copper lead, polarity bar."""
    src=load('wrought_battery'); AMB=ramp('#6B4208','#9A6410','#D8941C','#F5B23A','#FFD27A'); ST=ramp('#3A4048','#6A727C','#9AA2AC','#C8CED6')
    m={H('#535A62'):AMB[0],H('#6A727A'):AMB[1],H('#838B93'):AMB[2],H('#9CA4AC'):AMB[3],           # body walls -> amber
       H('#962820'):AMB[0],H('#A82D25'):AMB[0],H('#B8332A'):AMB[1],H('#CC3D32'):AMB[1],H('#E5483C'):AMB[2],H('#F07064'):AMB[3],   # bands -> darker amber
       H('#9C7A22'):ST[0],H('#BA9736'):ST[1],H('#D9B54A'):ST[2],H('#E5C86B'):ST[2],H('#F2DC8C'):ST[3]}
    out=remap(src,m)
    for y in range(5,13): out.putpixel((10,y),H('#3A2604')+(255,)); out.putpixel((11,y),AMB[0]+(255,))   # polarity band
    return grain(out,84,0.08,keep=set(AMB[3:]))
GLYPHS={'funnel':['#####','#####','.###.','..#..','..#..'],'chevrons':['#..#.','.#..#','..#..','.#..#','#..#.'],
        'wave':['.....','.#...','#.#.#','...#.','.....'],'torch':['..#..','.###.','..#..','..#..','..#..']}
def module(name):
    """Shared module card (Arcforge settings card): accent band + border recoloured per module, the face window a
       dark display with the module's symbol in its accent colour."""
    acc,glyph=MODULES[name]; src=load('settings_card'); out=src.copy()
    for y in range(16):
        for x in range(16):
            p=src.getpixel((x,y))
            if not p[3]: continue
            if p[:3]==H('#A47CE8'): out.putpixel((x,y),acc[2]+(255,))
            if p[:3]==H('#5E3E96'): out.putpixel((x,y),acc[0]+(255,))
    for y in range(5,11):
        for x in range(4,11): out.putpixel((x,y),(H('#14181C') if (x+y)%7 else H('#1A1F24'))+(255,))
    for r,row in enumerate(GLYPHS[glyph]):
        for c,v in enumerate(row):
            if v=='#': out.putpixel((5+c,5+r),(acc[3] if r<2 else acc[2])+(255,))
    for y in (14,15):                                                     # cards: flush edge contacts, no pins
        for x in range(16): out.putpixel((x,y),(0,0,0,0))
    for x in range(2,14): out.putpixel((x,14),H('#0C0D0F')+(255,))
    return grain(out,85,0.06,keep=set(acc)|{H('#14181C')})
ITEMS={'raw_neodymium':lambda: raw(NEO_RAMP,81),'raw_tantalum':lambda: raw(TAN_RAMP,82),
       'neodymium_ingot':lambda: ingot(NEO_INGOT),'tantalum_ingot':lambda: ingot(TAN_INGOT),
       'neodymium_dust':lambda: dust(NEO_RAMP),'tantalum_dust':lambda: dust(TAN_RAMP),
       'doped_silicon':doped_silicon,'memory_die':memory_die,'memory_photomask':photomask_memory,
       'tantalum_capacitor':tantalum_capacitor,'filter_module':lambda: module('filter'),'throughput_module':lambda: module('throughput'),
       'fuzzy_match_module':lambda: module('fuzzy_match'),'redstone_control_module':lambda: module('redstone_control'),
       'storage_die_128k':lambda: storage_die(2),'storage_die_512k':lambda: storage_die(3),
       'storage_drive_128k':lambda: drive(2),'storage_drive_512k':lambda: drive(3)}
BLOCK_TEX={'ore/neodymium_ore':lambda: ore_overlay('ore_nickel_ore.png',NEO_RAMP),'ore/deepslate_neodymium_ore':lambda: ore_overlay('ore_deepslate_nickel_ore.png',NEO_RAMP),
           'ore/tantalum_ore':lambda: ore_overlay('ore_nickel_ore.png',TAN_RAMP),'ore/deepslate_tantalum_ore':lambda: ore_overlay('ore_deepslate_nickel_ore.png',TAN_RAMP)}
