# Phase 4 items - Arcforge bases where one exists (tools/arcforge_ref/), vanilla-style shading.
import os, random
from PIL import Image
from el_style import H, put, img, g
import arc_based
from items_v3 import load, remap, grain, ramp, L
from items_p2 import module
LINK=ramp('#5A0E36','#A82868','#FF5FA2','#FFC2DC')               # Link Card accent (signal pink)
SFP_BAIL=ramp('#14306A','#2C5CB8','#4A8FE0','#A8CCF6')            # transceiver pull-bail (10G blue)
LENS=ramp('#0E3A48','#2A7A90','#7FD8EC','#E6FBFF')
def transceiver():
    """Arcforge rubber bar reshaped as an SFP module: steel cage body with a seam, blue pull-bail at the back,
       a two-port optical face with a small glass lens at the front."""
    src=load('rubber'); ST=ramp('#2A3038','#4E565F','#737C87','#98A1AB','#BCC4CC','#DDE3E8')
    cols=sorted({src.getpixel((x,y))[:3] for x in range(16) for y in range(16) if src.getpixel((x,y))[3]},key=L)
    n=len(cols); out=remap(src,{c:ST[min(5,int(i*6/n))] for i,c in enumerate(cols)})
    xs=[x for x in range(16) for y in range(16) if out.getpixel((x,y))[3]]; ys=[y for y in range(16) for x in range(16) if out.getpixel((x,y))[3]]
    x0,x1,y0,y1=min(xs),max(xs),min(ys),max(ys)
    for x in range(x0+2,x1-1): 
        y=y0+(y1-y0)//2
        if out.getpixel((x,y))[3]: out.putpixel((x,y),ST[1]+(255,))                    # cage seam
    for y in range(y0+2,y1-1):                                                        # optical face (right end)
        for x in (x1-2,x1-1):
            if out.getpixel((x,y))[3]: out.putpixel((x,y),ST[0]+(255,))
    my=(y0+y1)//2
    for (x,y,c) in ((x1-2,my-1,LENS[3]),(x1-1,my-1,LENS[2]),(x1-2,my+1,LENS[2]),(x1-1,my+1,LENS[1])): out.putpixel((x,y),c+(255,))
    for y in range(y0+1,y1):                                                          # pull-bail (left end)
        if out.getpixel((x0+1,y))[3]: out.putpixel((x0+1,y),SFP_BAIL[2 if y<my else 1]+(255,))
    out.putpixel((x0+1,y0+1),SFP_BAIL[3]+(255,))
    return grain(out,101,0.06,keep=set(LENS)|set(SFP_BAIL))
def link_card(written):
    """Card family (Arcforge settings card, flush contacts); pink accent. Written: lit data strip + two paired dots."""
    src=load('settings_card'); out=src.copy(); band=LINK if written else ramp('#3A4048','#5A616A','#8C949D','#B4BAC0')
    for y in range(16):
        for x in range(16):
            p=src.getpixel((x,y))
            if not p[3]: continue
            if p[:3]==H('#A47CE8'): out.putpixel((x,y),band[2]+(255,))
            if p[:3]==H('#5E3E96'): out.putpixel((x,y),band[0]+(255,))
    for y in range(5,11):
        for x in range(4,11): out.putpixel((x,y),(H('#14181C') if (x+y)%7 else H('#1A1F24'))+(255,))
    if written:
        for (x,y) in ((5,6),(9,6)): out.putpixel((x,y),LINK[3]+(255,))
        for x in range(6,9): out.putpixel((x,6),LINK[1]+(255,))
        r=random.Random(3)
        for x in range(4,11): out.putpixel((x,9),(LINK[3] if r.random()<0.5 else LINK[2])+(255,))
    else:
        for (x,y) in ((5,6),(9,6)): out.putpixel((x,y),H('#2C333B')+(255,))
        for x in range(4,11,2): out.putpixel((x,9),H('#2C333B')+(255,))
    for y in (14,15):
        for x in range(16): out.putpixel((x,y),(0,0,0,0))
    for x in range(2,14): out.putpixel((x,14),H('#0C0D0F')+(255,))
    return grain(out,102,0.05,keep=set(band)|{H('#14181C')})
def handheld(state):
    """Handheld Terminal: slate body lit top-left, bevelled screen, keypad, antenna nub top-right with a status light.
       unlinked: dark screen, grey light; linked: mint item-grid screen, green light; out of range: dimmed screen with
       an amber no-signal mark, amber light."""
    im=img(); B=[g(0),g(2),g(3),g(4),g(5),g(6),g(7)]
    for y in range(3,15):
        for x in range(3,13):
            t=5 if (x==3 or y==3) else 2 if (x==12 or y==14) else 4
            put(im,x,y,B[t])
    for y in range(1,3): put(im,10,y,g(6)); put(im,11,y,g(4))                       # antenna nub
    put(im,10,0,g(8))
    MINT=[H('#0B3A2C'),H('#127A57'),H('#1FB582'),H('#5CF0B8')]; AMB=[H('#4E340C'),H('#9A6410'),H('#F5B23A')]
    for y in range(5,10):
        for x in range(5,11): put(im,x,y,H('#0E1A17'))
    for x in range(5,11): put(im,x,4,g(2)); put(im,x,10,g(6))
    for y in range(5,10): put(im,4,y,g(2)); put(im,11,y,g(6))
    if state=='linked':
        for x in range(5,11): put(im,x,5,MINT[1])
        for y in (7,9):
            for x in range(5,11,2): put(im,x,y,MINT[3] if (x+y)%3 else MINT[2])
        led=H('#3CE05A')
    elif state=='out_of_range':
        for x in range(5,11): put(im,x,5,H('#20302A'))
        for (x,y) in ((6,6),(7,7),(8,8),(9,9),(9,6),(8,7),(7,8),(6,9)): put(im,x,y,AMB[1] if (x+y)%2 else AMB[2])
        led=AMB[2]
    else:
        led=g(7)
    put(im,12,1,led)                                                                  # status light on the nub
    for y in (12,):                                                                   # keypad
        for x in range(5,11,2): put(im,x,y,g(7)); put(im,x,y+1,g(3))
    for y in range(2,16):
        for x in range(2,14):
            if im.getpixel((x,y))[3]: continue
            if any(0<=x+dx<16 and 0<=y+dy<16 and im.getpixel((x+dx,y+dy))[3] for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))): put(im,x,y,H('#0C0D0F'))
    for (x,y) in ((9,1),(9,2),(12,2),(11,0),(10,-1)): 
        if 0<=y and not im.getpixel((x,y))[3]: put(im,x,y,H('#0C0D0F'))
    return grain(im,103,0.08,keep={H('#0E1A17'),led})
ITEMS={'optical_transceiver':transceiver,'link_card':lambda: link_card(False),'link_card_written':lambda: link_card(True),
       'handheld_terminal':lambda: handheld('unlinked'),'handheld_terminal_linked':lambda: handheld('linked'),
       'handheld_terminal_out_of_range':lambda: handheld('out_of_range'),
       'fuzzy_match_module':lambda: module('fuzzy_match'),'redstone_control_module':lambda: module('redstone_control')}
