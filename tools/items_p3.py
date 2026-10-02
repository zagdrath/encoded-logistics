# Phase 3 items - from Cody's Arcforge sprites (tools/arcforge_ref/), recoloured/reshaped, vanilla-style shading.
import os, random
from PIL import Image
from el_style import H, put, img
import arc_based
from items_v3 import load, remap, grain, ramp, module_die, storage_die, drive, L
from items_p2 import ore_overlay, raw, ingot, dust, GLYPHS
PROCESSOR=ramp('#1E2470','#3A44B8','#5C6CF0','#B8C0FF')          # Processor Die / Processor Photomask
CRAFTING=ramp('#7A5C08','#B88E14','#E8C24A','#F5DE8A')           # Crafting Schematic (autocrafting gold)
PROCESSING=ramp('#0B4A44','#138A7E','#27C4B4','#A8F0E6')         # Processing Schematic (teal)
GA_RAMP=['#3A4450','#5E6C7C','#8C9CAE','#B6C6D6','#DCE8F2','#F6FBFF']   # pale silver, bluish shimmer
GA_INGOT=['#262C34','#343C46','#424C58','#56626F','#74828F','#98A8B6','#C4D2DE','#EEF6FC']
def card(acc,state):
    """Schematic card on the shared module card (Arcforge settings card, flush contacts, no pins).
       blank: neutral steel band, empty dark window with a dim dotted strip;
       crafting / processing: accent band, lit data strip across the window + a small mark (3x3 grid / in-out arrow)."""
    src=load('settings_card'); out=src.copy()
    band=acc if acc else ramp('#3A4048','#5A616A','#8C949D','#B4BAC0')
    for y in range(16):
        for x in range(16):
            p=src.getpixel((x,y))
            if not p[3]: continue
            if p[:3]==H('#A47CE8'): out.putpixel((x,y),band[2]+(255,))
            if p[:3]==H('#5E3E96'): out.putpixel((x,y),band[0]+(255,))
    for y in range(5,11):
        for x in range(4,11): out.putpixel((x,y),(H('#14181C') if (x+y)%7 else H('#1A1F24'))+(255,))
    if state=='blank':
        for x in range(4,11,2): out.putpixel((x,9),H('#2C333B')+(255,))
    else:
        r=random.Random(7 if state=='crafting' else 9)
        for x in range(4,11): out.putpixel((x,9),(acc[3] if r.random()<0.5 else acc[2])+(255,))    # lit data strip
        for x in range(4,11): out.putpixel((x,10),acc[1]+(255,))
        mark=['###','#.#','###'] if state=='crafting' else ['#..','###','#..']
        for ry,row in enumerate(mark):
            for rx,v in enumerate(row):
                if v=='#': out.putpixel((6+rx,5+ry),acc[3]+(255,))
    for y in (14,15):
        for x in range(16): out.putpixel((x,y),(0,0,0,0))
    for x in range(2,14): out.putpixel((x,14),H('#0C0D0F')+(255,))
    return grain(out,91,0.05,keep=set(band)|{H('#14181C')})
def processor_die():
    """Logic/Memory Die family (Arcforge area module), indigo accent, larger dense etch filling the face."""
    im=module_die('area_module',PROCESSOR)
    for y in range(4,12):
        for x in range(3,11):
            if (x+y)%2==0 or y in (6,9): im.putpixel((x,y),(PROCESSOR[2] if (x*3+y)%5 else PROCESSOR[3])+(255,))
            else: im.putpixel((x,y),PROCESSOR[0]+(255,))
    return im
def photomask_processor():
    src=load('plate_die')
    CR=ramp('#0C0D0F','#4A525C','#7C8590','#A9B1BA','#D7DDE3'); GL=ramp('#3E6F7A','#6AA0AC','#96CAD4','#C4EAF0')
    m={H('#0C0D0F'):CR[0],H('#747880'):CR[4],H('#2E3237'):CR[1],H('#484C54'):CR[2],H('#5C646C'):CR[3],H('#4A8FE0'):PROCESSOR[2]}
    out=remap(src,m)
    for y in range(3,13):
        for x in range(2,14):
            t=3 if x+y<11 else 2 if x+y<19 else 1
            if x-y in (2,3): t=3
            out.putpixel((x,y),GL[t]+(255,))
    PAT=H('#1E2470')
    for y in range(4,12):                                                # dense mesh with a centre core block
        for x in range(3,13):
            if (x%2==1 and y%2==0) or (6<=x<=9 and 6<=y<=9 and (x+y)%2==0): out.putpixel((x,y),PAT+(255,))
    out.putpixel((12,12),PROCESSOR[3]+(255,))
    return grain(out,93,0.08,keep={PAT,GL[3]})
def heatsink():
    """Arcforge rubber bar reshaped as a finned aluminium heatsink: copper base plate, deep fin slots on top."""
    src=load('rubber'); AL=ramp('#2E343C','#56606C','#7E8A96','#A8B4C0','#D2DCE6'); CU=ramp('#5A2A12','#9A4E24','#D07A40')
    cols=sorted({src.getpixel((x,y))[:3] for x in range(16) for y in range(16) if src.getpixel((x,y))[3]},key=L)
    n=len(cols); m={c:AL[min(4,int(i*5/n))] for i,c in enumerate(cols)}
    out=remap(src,m)
    ys=[y for y in range(16) for x in range(16) if out.getpixel((x,y))[3]]; top,bot=min(ys),max(ys)
    for x in range(16):                                                  # fin slots across the top half
        for y in range(top+1,top+(bot-top)//2+1):
            p=out.getpixel((x,y))
            if p[3] and x%2==0 and p[:3]!=AL[0]: out.putpixel((x,y),AL[1]+(255,))
    for x in range(16):                                                  # copper base plate along the bottom edge
        for y in (bot-2,bot-1):
            p=out.getpixel((x,y))
            if p[3] and p[:3]!=AL[0]: out.putpixel((x,y),(CU[2] if y==bot-2 else CU[1])+(255,))
    return out
ITEMS={'schematic_card':lambda: card(None,'blank'),'encoded_schematic_crafting':lambda: card(CRAFTING,'crafting'),
       'encoded_schematic_processing':lambda: card(PROCESSING,'processing'),
       'raw_gallium':lambda: raw(GA_RAMP,95),'gallium_ingot':lambda: ingot(GA_INGOT),'gallium_dust':lambda: dust(GA_RAMP),
       'processor_die':processor_die,'processor_photomask':photomask_processor,'heatsink':heatsink,
       'storage_die_2m':lambda: storage_die(4),'storage_drive_2m':lambda: drive(4)}
BLOCK_TEX={'ore/deepslate_gallium_ore':lambda: ore_overlay('ore_deepslate_nickel_ore.png',GA_RAMP)}
