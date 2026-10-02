# Phase 1 blocks: Lithography Press (machine) and Access Terminal (cable part). Follows docs/TEXTURE_STYLE.md.
import random, colorsys, math
from el_style import *
from PIL import Image
UV=[H(c) for c in ['#2A1350','#4B2290','#7A3FE0','#A877FF','#D9C2FF']]         # UV / exposure violet ramp
MINT=[H(c) for c in ['#0B3A2C','#127A57','#1FB582','#5CF0B8','#C8FFE9']]       # terminal screen glow
def parts_tex():
    im=img()
    for i,c in enumerate([g(8),g(6),g(3),g(1),BRASS[2]]):
        for y in range(2):
            for x in range(2): put(im,2*i+x,y,c)
    return im
PU={'lit':[0,0,2,2],'mid':[2,0,4,2],'dark':[4,0,6,2],'shadow':[6,0,8,2],'brass':[8,0,10,2]}
def leds_tex(glow=False):
    im=img()
    def dot(x0,cols,lit):
        if glow and not lit: return
        put(im,x0,0,cols[2] if lit else cols[0]); put(im,x0+1,0,cols[1]); put(im,x0,1,cols[1]); put(im,x0+1,1,cols[0])
    dot(0,[H('#1C2622'),H('#24302A'),H('#2B3A33')],False); dot(2,[LED_GREEN[0],LED_GREEN[1],LED_GREEN[2]],True)
    dot(4,[AMBER[0],H('#4E340C'),H('#5E3F10')],False); dot(6,[AMBER[2],AMBER[3],AMBER[4]],True)
    return im

# ================= LITHOGRAPHY PRESS =================
# Front texture regions (texture px): window (3,3)-(12,9) [10x7], lamp bar row 3 inside it, control strip rows 11..13.
WIN=(3,3,10,7)
def press_front():
    im=img(); field(im,0,0,16,16,4,101); bevel_frame(im,0,0,16,16,102)
    # window pocket back wall (chamber interior): dark steel, photomask holder, wafer on its chuck
    x0,y0,w,h=WIN
    for y in range(y0,y0+h):
        for x in range(x0,x0+w): put(im,x,y,g(1 if y==y0+1 else 2))
    for x in range(x0+2,x0+w-2): put(im,x,y0+3,H('#6FA6B2'))                         # photomask plate (edge-on)
    for x in range(x0+3,x0+w-3): put(im,x,y0+5,g(8)); put(im,x,y0+6,g(5))           # wafer + chuck
    put(im,x0+2,y0+5,g(6)); put(im,x0+w-3,y0+5,g(6))
    # window surround: thin inner lip so the pocket reads deep
    for x in range(x0-1,x0+w+1): put(im,x,y0-1,g(2)); put(im,x,y0+h,g(7))
    for y in range(y0-1,y0+h+1): put(im,x0-1,y,g(2)); put(im,x0+w,y,g(7))
    # control strip: inset with three keys and a status light socket
    for x in range(3,13): put(im,x,11,g(2)); put(im,x,13,g(6))
    for x in range(3,13): put(im,x,12,g(3))
    for i,c in enumerate((H('#3CE05A'),AMBER[3],H('#E5483C'))): put(im,4+2*i,12,c)
    put(im,11,12,g(1)); put(im,12,12,g(1))                                           # status light socket (lit by a quad)
    # small UV warning plate top-left
    for x in range(2,5): put(im,x,1,UV[2]); put(im,x,2,UV[1])
    return im
def press_side(seed):
    im=img(); field(im,0,0,16,16,4,seed); bevel_frame(im,0,0,16,16,seed+1)
    for y in range(2,14): put(im,8,y,g(2)); put(im,9,y,g(6))                          # panel seam
    for vy in (4,6,8,10):                                                             # vent louvres, right panel
        for x in range(10,14): put(im,x,vy,g(1)); put(im,x,vy+1,g(6))
    for (x,y) in ((3,3),(6,3),(3,12),(6,12)): put(im,x,y,g(8)); put(im,x+1,y+1,g(1))
    for y in range(6,10):                                                             # UV hazard label, left panel
        for x in range(3,7): put(im,x,y,UV[2] if (x+y)%2 else g(1))
    return im
def press_top():
    """Exhaust fan: guard ring, four bold curved blades, raised hub."""
    im=img(); field(im,0,0,16,16,4,111); bevel_frame(im,0,0,16,16,112)
    cx,cy=7.5,7.5
    for y in range(2,14):
        for x in range(2,14):
            d=math.hypot(x-cx,y-cy)
            if d>6.2: continue
            if d>5.3: put(im,x,y,g(8) if x+y<15 else g(4)); continue          # guard ring, lit top-left
            a=(math.degrees(math.atan2(y-cy,x-cx))+360-d*14)%90                 # 4 blades, swept
            if d<1.6: put(im,x,y,g(9) if x+y<15 else g(6))                      # hub
            elif a<38: put(im,x,y,g(7) if a<14 else g(6))                       # blade (leading edge lit)
            else: put(im,x,y,g(1))                                              # dark duct between blades
    return im
def press_back():
    im=press_side(121)
    for y in range(5,11):                                                             # FE inlet plate
        for x in range(4,12): put(im,x,y,g(3))
    for x in range(5,11): put(im,x,6,FE[2]); put(im,x,7,FE[1])
    port_ring(im,8,9) if False else None
    for (x,y) in ((6,9),(9,9)): put(im,x,y,BRASS[3])
    return im
def press_glass():
    """Viewing window glass (translucent): dark cool tint at low alpha so the chamber and the UV glow read through,
       one reflection streak, slightly stronger edge."""
    im=img(); w,h=10,7
    for y in range(h):
        for x in range(w):
            a=34; c=(96,116,138)
            if x-y in (1,2): a=78; c=(200,214,230)
            if x in (0,w-1) or y in (0,h-1): a=64
            put(im,x,y,c,a)
    return im
def press_lamp(on=False):
    im=img()
    for x in range(8):
        put(im,x,0,(UV[4] if x%3 else UV[3]) if on else g(6)); put(im,x,1,UV[2] if on else g(3))
    return im
def press_uv_glow(frames=8):
    """Animated exposure light inside the window (10x7): violet haze from the lamp down onto the wafer, pulsing."""
    strip=Image.new('RGBA',(16,16*frames),(0,0,0,0))
    for f in range(frames):
        k=0.5+0.5*math.sin(2*math.pi*f/frames)
        for y in range(7):
            for x in range(10):
                fall=1.0-y/7.0
                v=fall*(0.55+0.45*k)
                if v<0.18: continue
                idx=min(4,int(v*5))
                c=UV[idx]
                if y in (5,) and 3<=x<=6: c=UV[4] if k>0.5 else UV[3]          # lit wafer
                strip.putpixel((x,16*f+y),c+(int(120+120*v),))
    return strip

# ================= ACCESS TERMINAL =================
def term_front(online=False):
    """Bezel (14x14 at 1..14) with a 10x9 screen (3,3)-(12,11) and a button row; screen dark when offline."""
    im=img()
    for y in range(1,15):
        for x in range(1,15): put(im,x,y,g(5))
    bevel_frame(im,1,1,14,14,131,lip=False)
    for y in range(2,14):
        for x in range(2,14):
            if not (x in (2,13) or y in (2,13)): put(im,x,y,g(4))
    for y in range(3,12):                                                             # screen glass (off)
        for x in range(3,13): put(im,x,y,H('#0E1A17') if not online else MINT[0])
    for x in range(3,13): put(im,x,3,H('#16261F'))                                    # glass edge sheen
    for x in range(4,12,3): put(im,x,12,g(7)); put(im,x+1,12,g(2))                   # button row
    return im
def term_screen(frames=8):
    """Animated screen (10x9 at 3,3): header bar with a search caret, item-grid rows, a slow scan line."""
    strip=Image.new('RGBA',(16,16*frames),(0,0,0,0))
    rnd=random.Random(7); cells={(x,y):rnd.random() for x in range(10) for y in range(9)}
    for f in range(frames):
        def P(x,y,c): strip.putpixel((3+x,16*f+3+y),c+(255,))
        for y in range(9):
            for x in range(10): P(x,y,MINT[0])
        for x in range(10): P(x,0,MINT[1])
        P(1,0,MINT[4] if f%4<2 else MINT[2])                                          # search caret blinks
        for gy in range(2,9,2):                                                       # item grid: 4 rows x 4 tiles
            for gx in range(0,10,3):
                v=cells[(gx,gy)]
                c=MINT[3] if v>0.6 else MINT[2] if v>0.25 else MINT[1]
                P(gx,gy,c); P(min(9,gx+1),gy,MINT[2] if v>0.4 else MINT[1])
        sl=1+f%8                                                                       # scan line
        if sl<9:
            for x in range(10):
                if strip.getpixel((3+x,16*f+3+sl))[:3]==MINT[0]: P(x,sl,MINT[1])
    return strip
def term_side():
    im=img()
    for y in range(16):
        for x in range(16): put(im,x,y,g(6) if y<2 else g(5) if y<14 else g(3))
    for x in range(16): put(im,x,0,g(8))
    return im
def term_back():
    im=img(); field(im,0,0,16,16,4,141); bevel_frame(im,1,1,14,14,142,lip=False); port_ring(im,8,8)
    return im
