# Phase 2 + 3 block/part textures, v2 - redrawn to the controller / cable / Drive Bay standard (docs/TEXTURE_STYLE.md)
# using tools/style_kit.py. Every texture keeps its size and UV layout, so they replace the repo files one-for-one.
import math, random
from PIL import Image
from el_style import H, put, img
from style_kit import g, accent, dark_field, rail, raised, recess, inlay, brushed, octagon, oct_ring, slats, bolts
INGRESS=accent('#4A8FE0'); EGRESS=accent('#E8913A'); TAP=accent('#A77BF0'); RED=accent('#E5483C'); GOLD=accent('#E8C24A')
INDIGO=accent('#5C6CF0'); MINT=accent('#1FB582'); AMB=accent('#F5B23A'); HOT=accent('#F0702A'); PCB=accent('#2C7C4A'); WARM=accent('#F2B848')
def glow_from(pts,ramp,seed,size=(16,16)):
    im=Image.new('RGBA',size,(0,0,0,0)); P=set(pts)                  # clean: no random dim pixels
    for (x,y) in P:
        k=5 if ((x,y-1) not in P or (x-1,y) not in P) else 4
        im.putpixel((x,y),ramp[k]+(255,))
    return im
def mini_rail(im,x0,y0,w,h,lip=True):
    """Guide rail on a sub-plate (parts smaller than a block face)."""
    for a in range(w):
        put(im,x0+a,y0,g(9)); put(im,x0+a,y0+h-1,g(6))
    for a in range(h):
        put(im,x0,y0+a,g(9)); put(im,x0+w-1,y0+a,g(6))
    put(im,x0,y0,g(10)); put(im,x0+w-1,y0,g(7)); put(im,x0,y0+h-1,g(7)); put(im,x0+w-1,y0+h-1,g(5))
    if lip:
        for a in range(1,w-1): put(im,x0+a,y0+1,g(2))
        for a in range(1,h-1): put(im,x0+1,y0+a,g(2))
# ================= PHASE 2 =================
def port_mouth(acc):
    """12x12 intake plate (2..13): steel rail + lip, dark field, recessed slot grille with steel bars, small accent
       inlays at the four corners (the accent no longer forms the frame)."""
    im=img(); dark_field(im,2,2,12,12,501); mini_rail(im,2,2,12,12)
    for i,y in enumerate((5,8,11)):
        recess(im,5,y,6,1,502+i)
    raised(im,[(x,y) for y in (7,10) for x in range(5,11)],505,bounds=(3,3,13,13))
    for (x,y) in ((4,4),(11,4),(4,11),(11,11)): inlay(im,[(x,y)],acc,506+x+y)
    inlay(im,[(x,3) for x in range(6,10)],acc,507)
    return im
# Port body sheet. The port is a solid stepped housing (tools/parts_p2.py port_models): a 12x12x1 plate at the
# inventory, a 10x10x2 body, an 8x8x1 back plate, a 4x4 stub to the cable. Every side face maps 1:1 onto its own strip
# (no stretched texels): strips are flat along the depth and symmetric across, so any face orientation reads the same.
#   across x depth (up/down faces):  plate (0,0) 12x1, body (0,2) 10x2, back (0,5) 8x1
#   depth x across (east/west faces): plate (15,0) 1x12, body (13,0) 2x10, back (12,0) 1x8
#   back face (south, toward the cable): (0,8) 8x8
PORT_STRIPS={'plate':(12,1,(0,0),(15,0)),'body':(10,2,(0,2),(13,0)),'back':(8,1,(0,5),(12,0))}
def port_side(acc,inward,glow=False):
    """Clean steel strips: plate edge bright (it catches the light), body mid steel with a 2 px status light in the
       middle of every side (the accent; also the glow), back plate darker; ends of each strip one step darker."""
    im=img()
    tone={'plate':(8,6),'body':(6,4),'back':(5,4)}
    for k,(n,d,(hx,hy),(vx,vy)) in PORT_STRIPS.items():
        for a in range(n):
            for b in range(d):
                light=k=='body' and a in (n//2-1,n//2)
                if glow and not light: continue
                if light: c=acc[5 if glow else 4] if a==n//2-1 else acc[4 if glow else 3]
                else: c=g(tone[k][1] if a in (0,n-1) else tone[k][0])
                put(im,hx+a,hy+b,c); put(im,vx+b,vy+a,c)
    if not glow:
        for y in range(8):
            for x in range(8):
                r=min(x,y,7-x,7-y)
                c=g(8 if (x==0 or y==0) else 4) if r==0 else g(6) if r==1 else g(4)
                put(im,x,8+y,c)
        put(im,7,8,g(6)); put(im,0,15,g(6))
    return im
def tap_plate():
    """14x14 probe plate (1..14): rail + lip, dark field, raised clamp bars top and bottom, two recessed rows of gold
       contact pads, small violet inlays on the clamp ends."""
    im=img(); dark_field(im,1,1,14,14,520); mini_rail(im,1,1,14,14)
    raised(im,[(x,y) for y in (3,12) for x in range(3,13)],521,bounds=(2,2,14,14))
    for i,y in enumerate((6,9)):
        recess(im,3,y,10,1,522+i)
        inlay(im,[(x,y) for x in range(4,12,2)],GOLD,524+i)
    inlay(im,[(2,3),(2,12),(13,3),(13,12)],TAP,526)
    return im
def tap_body(glow=False):
    light=[(0,3),(1,3),(0,4),(1,4)]; light2=[(7,0),(8,0),(7,1),(8,1)]
    if glow: return glow_from(light+light2,TAP,530)
    im=img(); brushed(im,0,0,2,8,[7,5],531,'v'); brushed(im,4,0,8,2,[7,5],532,'h')
    for a in range(8): put(im,0,a,g(8)); put(im,4+a,0,g(8))
    for (x,y) in light: put(im,x,y,TAP[1])
    for (x,y) in light2: put(im,x,y,TAP[1])
    return im
def sensor_panel():
    """10x10 panel (3..12): rail + lip, dark field, raised octagonal lamp bezel round the lamp socket, tick bolts."""
    im=img(); dark_field(im,3,3,10,10,540); mini_rail(im,3,3,10,10)
    for (x,y) in octagon(7.5,7.5,1.5,0): put(im,x,y,g(1))
    raised(im,oct_ring(7.5,7.5,2.5,1,1),541,bounds=(4,4,12,12))
    bolts(im,((5,5),(10,5),(5,10),(10,10)) if False else ((4,8),(11,8)),542)
    return im
def sensor_lamp(on):
    """4x4 lens: off = red ramp dark steps with a dull glint; on = bright steps with a hot glint (also its glow)."""
    im=img()
    for y in range(4):
        for x in range(4):
            if on: k=6 if (x,y)==(1,1) else 5 if x+y<3 else 4 if x+y<5 else 3
            else: k=2 if (x,y)==(1,1) else 1 if x+y<4 else 0
            put(im,x,y,RED[k])
    return im
def terminal_front(stripe=None):
    """Terminal bezel (1..14): steel rail + lip, dark field bezel, screen glass (3..12, 3..11) recessed with a stepped
       diagonal reflection (guide: never flat), button row; optional accent stripe as an inlay under the screen."""
    im=img(); dark_field(im,1,1,14,14,550,base=3); mini_rail(im,1,1,14,14)
    glass=[H('#0B1614'),H('#0E1A17'),H('#132420'),H('#1A2E29')]
    for y in range(3,12):
        for x in range(3,13):
            k=1
            if 4<=x-y+3<=5: k=3
            elif x-y+3 in (3,6): k=2
            if y==3 or x==3: k=0
            put(im,x,y,glass[k])
    for x in range(3,13): put(im,x,12,g(6))
    for y in range(3,13): put(im,13,y,g(6))
    if stripe: inlay(im,[(x,13) for x in range(4,12)],stripe,551)
    else:
        for x in range(4,12,3): put(im,x,13,g(8)); put(im,x+1,13,g(3))
    return im
# ================= PHASE 3 =================
WIN=(3,3,10,8)
def fab_front():
    """Fabricator: dark field + rail, the window pocket's back wall is a recessed panel with two raised guide rails and
       a raised work plate (so it reads through the glass), status strip as small inlays."""
    im=img(); dark_field(im,0,0,16,16,560); rail(im,0,561)
    x0,y0,w,h=WIN
    recess(im,x0,y0,w,h-1,562)
    raised(im,[(x,y0+2) for x in range(x0+1,x0+w-1)],563,bounds=(x0,y0,x0+w,y0+h))
    raised(im,[(x,y0+h-2) for x in range(x0+1,x0+w-1)],564,bounds=(x0,y0,x0+w,y0+h))
    for y in range(y0+3,y0+h-2): put(im,x0+1,y,g(3)); put(im,x0+w-2,y,g(3))
    for x in range(x0-1,x0+w+1): put(im,x,y0-1,g(2)); put(im,x,y0+h,g(7))
    for y in range(y0-1,y0+h+1): put(im,x0-1,y,g(2)); put(im,x0+w,y,g(7))
    recess(im,3,12,10,1,565)
    inlay(im,[(4,13)],GOLD,566); inlay(im,[(6,13)],g_ramp(),567) if False else None
    return im
def g_ramp(): return [g(i) for i in (3,4,5,6,7,8,9)]
def ribbed(seed,mark=None,mark_ramp=None):
    im=img(); dark_field(im,0,0,16,16,seed); rail(im,0,seed+1)
    slats(im,3,13,(3,12),seed+2)
    pts=[(x,y) for y in range(5,11) for x in range(5,11) if x in (5,10) or y in (5,10)]
    recess(im,6,6,4,3,seed+3); raised(im,pts,seed+4)
    for x0 in (3,12): raised(im,[(x0,y) for y in range(5,11)],seed+5+x0)
    bolts(im,((2,2),(12,2),(2,12),(12,12)),seed+6)
    if mark: inlay(im,mark,mark_ramp,seed+7)
    return im
def fab_side(seed): return ribbed(seed)
def fab_lights(on):
    im=img()
    for x in range(8):
        put(im,x,0,(WARM[5] if x%3 else WARM[6]) if on else g(5+(x%3==0)))
        put(im,x,1,WARM[3] if on else g(3))
    return im
def fab_wash():
    im=img()
    for y in range(8):
        for x in range(10):
            k=5 if y<2 else 4 if y<5 else 3
            put(im,x,y,WARM[k],int(60+150*(1-y/8)) if y<7 else 40)
    return im
def gateway_face(glow=False,active=False):
    """Every face: dark field + rail, recessed port with a raised octagonal collar, four corner status lights as
       stepped inlays (mint / amber)."""
    lights=[((2,2),MINT),((13,13),MINT),((13,2),AMB),((2,13),AMB)]
    if glow:
        im=Image.new('RGBA',(16,16),(0,0,0,0))
        if active:
            for (p,ramp) in lights: im.putpixel(p,ramp[5]+(255,))
        return im
    im=img(); dark_field(im,0,0,16,16,570); rail(im,0,571)
    for (x,y) in octagon(7.5,7.5,2.5,1): put(im,x,y,g(1))
    for (x,y) in octagon(7.5,7.5,2.5,1):
        if (x-1,y) not in octagon(7.5,7.5,2.5,1) or (x,y-1) not in octagon(7.5,7.5,2.5,1): put(im,x,y,g(0))
    raised(im,oct_ring(7.5,7.5,4.5,2,2),572,bounds=(1,1,15,15))
    for (p,ramp) in lights: put(im,*p,ramp[1])
    return im
# ---- Scheduler (connected textures; mask bits 1 up, 2 right, 4 down, 8 left) ----
def core_art(im,seed,mask=0):
    """Console: dark field, raised bezel round a recessed display glass with a stepped reflection, slats below."""
    dark_field(im,0,0,16,16,seed)
    glass=[H('#0B1614'),H('#0E1A17'),H('#132420')]
    for y in range(4,12):
        for x in range(3,13): put(im,x,y,glass[2] if x-y in (3,4) else glass[1] if y>4 else glass[0])
    raised(im,[(x,3) for x in range(3,13)]+[(2,y) for y in range(3,13)],seed+1,bounds=(1,1,15,15))
    for x in range(3,13): put(im,x,12,g(6))
    for y in range(4,13): put(im,13,y,g(6))
    raised(im,[(x,14) for x in range(4,12)],seed+2,shadow=False)
def buffer_art(im,seed,mask=0):
    """Memory racks: raised steel slot guides top and bottom, four DIMMs - PCB on the green ramp (lit left edge,
       shaded right), raised dark chips with shadow, gold contacts as stepped inlays in the slot."""
    dark_field(im,0,0,16,16,seed)
    x0,x1=(0 if mask&8 else 2),(16 if mask&2 else 14)
    raised(im,[(x,2) for x in range(x0,x1)],seed+1,shadow=False)
    raised(im,[(x,13) for x in range(x0,x1)],seed+2,shadow=False)
    for i,x0 in enumerate((3,6,9,12)):
        for y in range(3,12):
            put(im,x0,y,PCB[4] if y>3 else PCB[5]); put(im,x0+1,y,PCB[2])
        for y in (5,8):
            put(im,x0,y,g(2)); put(im,x0+1,y,g(1)); put(im,x0+1,y+1,g(0))
        inlay(im,[(x0,12),(x0+1,12)],GOLD,seed+3+i)
def thread_art(im,seed,mask=0):
    """Heatsink: raised fins (lit top edge, step-0 shadow under each, brushed along the fin), raised heat spreader."""
    dark_field(im,0,0,16,16,seed)
    x0,x1=(0 if mask&8 else 2),(16 if mask&2 else 14)
    ys=range(1 if mask&1 else 3,16 if mask&4 else 14,2)
    for y in ys:
        brushed(im,x0,y,x1-x0,1,[7],seed+y,'h')
        for x in (x0,x1-1):
            if (x==x0 and not mask&8) or (x==x1-1 and not mask&2): put(im,x,y,g(8))
        if y+1<16:
            for x in range(x0,x1): put(im,x,y+1,g(0) if x<x0+3 else g(1))   # gap: shadow under the fin, dark steel beyond
    for y in range(5,11):
        for x in range(5,11): put(im,x,y,g(6))
    for x in range(5,11): put(im,x,5,g(8)); put(im,x,10,g(4))
    for y in range(5,11): put(im,5,y,g(8)); put(im,10,y,g(4))
    for x in range(6,12): put(im,x,11,g(0))
    put(im,7,7,g(9)); put(im,8,8,g(5))
ART={'scheduler_core':core_art,'job_buffer':buffer_art,'thread_unit':thread_art}
def sched_tex(kind,mask):
    im=img(); ART[kind](im,600+mask*7,mask); rail(im,mask,650+mask); return im
def core_display(frames=8,formed=True):
    C=MINT if formed else RED
    strip=Image.new('RGBA',(16,16*frames),(0,0,0,0))
    for f in range(frames):
        P=lambda x,y,k: strip.putpixel((x,16*f+y),C[k]+(255,))
        if formed:
            for i,(y,ln) in enumerate(((5,8),(7,5),(9,7))):
                L_=min(9,(ln+f+i*3)%10)
                for x in range(4,4+L_): P(x,y,5 if x==3+L_ else 4 if x%3 else 3)
            for x in range(4,12): P(x,10,2)
            for x in range(4,4+2+f%6): P(x,10,5)
            if f%4<2: P(11,5,6)
        else:
            for x in range(4,12): P(x,7,4 if (x+f)%2 else 3)
            if f%4<2: P(7,9,5); P(8,9,5)
    return strip
def thread_glow():
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(4,14,2):
        for x in range(3,13):
            d=abs(x-7.5)+abs(y-7.5)
            if d<6 and not (5<=x<=10 and 5<=y<=10):
                im.putpixel((x,y),HOT[6 if d<3.5 else 5 if d<4.5 else 4]+(255,))
    return im
# ---- Drive Bay (was shipped pre-shaded with heavy grain; now painted clean, same layout and UVs) ----
def _bay_frame(im):
    """Outer 1px rail: top/left lit (9), bottom/right shaded (8/7), corners 7."""
    for a in range(16): put(im,a,0,g(9)); put(im,0,a,g(9)); put(im,15,a,g(8)); put(im,a,15,g(8))
    for (x,y) in ((0,0),(15,0),(0,15),(15,15)): put(im,x,y,g(7))
def drive_bay_front():
    """Front (the modelled ribs use it): steel frame, shelves at rows 3/6/9/12, a spine at cols 7-8, and ten 6x2
       pockets - each a step-0 shadow row under the shelf above and a step-1 floor."""
    im=img(); _bay_frame(im)
    for y in range(1,15):
        for x in range(1,15):
            if x in (7,8): c=g(9 if x==7 else 8)
            elif y in (3,6,9,12): c=g(8)
            else: c=g(0 if y in (1,4,7,10,13) else 1)
            put(im,x,y,c)
    return im
def drive_bay_casing(vertical):
    """Side / top: frame, dark recessed panel, three raised vent slats (horizontal on the sides, vertical on top) with
       a step-0 shadow down-right, four raised bolts."""
    im=img(); _bay_frame(im)
    for y in range(1,15):
        for x in range(1,15): put(im,x,y,g(2 if (2<=x<=13 and 2<=y<=13) else 1))
    for i in (4,7,10):
        pts=[(i,y) for y in range(4,12)] if vertical else [(x,i) for x in range(4,12)]
        raised(im,pts,0,bounds=(2,2,14,14))
    bolts(im,((3,3),(12,3),(3,12),(12,12)),0)
    return im
