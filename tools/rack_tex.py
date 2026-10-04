# Server Rack textures - docs/TEXTURE_STYLE.md via style_kit, black powder-coat variant of the steel ramp.
# Densities: frame 1 texel/px, door mesh 2 texels/px, rails 4 texels/px, devices 8 texels/px.
import random, math
from PIL import Image
from el_style import H, put
from style_kit import g, accent
BLK=[H(c) for c in ['#0B0C0E','#121417','#181B1F','#1F2328','#272C32','#323840','#414851','#58616C']]   # powder coat, 0..7
def b(i): return BLK[max(0,min(7,i))]
def new(w,h): return Image.new('RGBA',(w,h),(0,0,0,0))
def lerp(a,c,t): return tuple(round(a[i]+(c[i]-a[i])*t) for i in range(3))
def shade_rgb(ramp,base,t,span=1.6):
    """Smooth position on a ramp: t 0 = lit (base+span/2), 1 = shaded (base-span/2); blended between ramp tones."""
    v=base+span/2-span*t; v=max(0,min(len(ramp)-1.001,v)); i=int(v); return lerp(ramp[i],ramp[i+1],v-i)
def coat_smooth(im,x0,y0,w,h,seed,base=3,span=1.6):
    """Powder coat: SMOOTH light falloff from the top-left (lit) to the bottom-right (shaded). No noise."""
    for y in range(y0,y0+h):
        for x in range(x0,x0+w):
            t=0.65*((y-y0)/max(1,h-1))+0.35*((x-x0)/max(1,w-1)); put(im,x,y,shade_rgb(BLK,base,t,span))
def bevel(im,x0,y0,w,h,lit=5,dark=1):
    for a in range(w): put(im,x0+a,y0,b(lit)); put(im,x0+a,y0+h-1,b(dark))
    for a in range(h): put(im,x0,y0+a,b(lit)); put(im,x0+w-1,y0+a,b(dark))
    put(im,x0+w-1,y0,b(lit-1)); put(im,x0,y0+h-1,b(dark+1))
# ---------------- frame ----------------
# ---- vanilla-style powder coat (exterior): small palette, brushed runs, stepped light bands, crisp bevels ----
PC=[H(c) for c in ['#121417','#181B1F','#1E2226','#252A2F','#2D3238','#363C43','#424950','#505860']]   # 0..7
def pc(i): return PC[max(0,min(7,i))]
def coat(im,x0,y0,w,h,seed,base=3,axis='h',bands=True):
    """Vanilla metal fill (netherite / anvil / hopper style): each row (or column) is broken into runs of 3-7 px that sit
       one tone above or below the base (mostly the base), and the light steps in bands - top rows +1, bottom rows -1.
       No single-pixel speckle, no blended gradients."""
    r=random.Random(seed)
    A,B=(h,w) if axis=='h' else (w,h)
    for a in range(A):
        band=0
        if bands:
            pos=a/(max(1,A-1))
            band=1 if pos<0.18 else (-1 if pos>0.82 else 0)
        b_=0
        while b_<B:
            n=r.randint(3,7); o=r.choices((-1,0,1),(0.2,0.62,0.18))[0]
            for k in range(b_,min(B,b_+n)):
                x,y=(x0+k,y0+a) if axis=='h' else (x0+a,y0+k)
                put(im,x,y,pc(base+band+o))
            b_+=n
def pc_bevel(im,x0,y0,w,h,lit=2,dark=2):
    """Panel rim: lit top/left (+lit), shaded bottom/right (-dark), corners resolved like vanilla iron door panels."""
    for a in range(w):
        put(im,x0+a,y0,lerp_none(im,x0+a,y0,lit)); put(im,x0+a,y0+h-1,lerp_none(im,x0+a,y0+h-1,-dark))
    for a in range(h):
        put(im,x0,y0+a,lerp_none(im,x0,y0+a,lit)); put(im,x0+w-1,y0+a,lerp_none(im,x0+w-1,y0+a,-dark))
def lerp_none(im,x,y,d):
    c=im.getpixel((x,y))[:3]; i=min(range(8),key=lambda k: sum(abs(c[j]-PC[k][j]) for j in range(3))); return pc(i+d)
def frame_tex():
    """16x16 post / frame texture: rounded vertical edge as STEPPED columns (crest at x 3-4) with vertical brushed runs."""
    im=new(16,16); r=random.Random(702)
    prof=[2,3,4,5,5,4,3,3,3,3,3,3,3,3,2,1]
    for x in range(16):
        y=0
        while y<16:
            n=r.randint(3,7); o=r.choices((-1,0,1),(0.18,0.66,0.16))[0] if 5<=x<=13 else 0
            for k in range(y,min(16,y+n)): put(im,x,k,pc(prof[x]+o))
            y+=n
    return im
def side_panel(seed,upper):
    """32x32 (1 texel/px; uv = px/2) side panel 30 x ~22: brushed powder coat, a pressed inset panel (vanilla iron-door
       style bevel: lit top/left rim, dark bottom/right, the inset field one tone darker), two lock points, seam edge."""
    im=new(32,32); coat(im,0,0,30,22,seed,3)
    pc_bevel(im,0,0,30,22,1,2)
    coat(im,3,3,24,16,seed+1,2,bands=False)                           # pressed-in field
    for x in range(3,27): put(im,x,2,pc(1)); put(im,x,19,pc(5))       # inset rim: shadow above, lit lip below
    for y in range(3,19): put(im,2,y,pc(1)); put(im,27,y,pc(5))
    for (x,y) in ((5,11),(24,11)):                                    # lock points: raised round with a key slot
        put(im,x,y,pc(6)); put(im,x+1,y,pc(5)); put(im,x,y+1,pc(4)); put(im,x+1,y+1,pc(2))      # round knob, lit top-left
        put(im,x+2,y+1,pc(0)); put(im,x+1,y+2,pc(0)); put(im,x+2,y+2,pc(1))                  # its shadow
    seam_y=21 if not upper else 0
    for x in range(30): put(im,x,seam_y,pc(0))
    return im
def roof_tex():
    """32x32 top (16 x 32 px): brushed coat (runs along the depth), raised rim, a vent field (front half) and two
       cable-entry slots with brush seals (rear half), every opening with a dark top edge and a lit lower lip."""
    im=new(32,32); coat(im,0,0,16,32,711,3,axis='v'); pc_bevel(im,0,0,16,32,2,2)
    for y in range(4,14,2):                                             # vent slots
        for x in range(3,13): put(im,x,y,pc(0)); put(im,x,y+1,pc(5))
    for y0 in (17,24):
        for y in range(y0,y0+4):
            for x in range(3,13): put(im,x,y,pc(0) if y==y0 else pc(1))
        for x in range(3,13,2): put(im,x,y0+2,pc(3))
        for x in range(3,13): put(im,x,y0+4,pc(5))
    return im
def roof_edge_tex():
    """32x16 roof band (the roof cap's four sides, 3 px tall; 1 texel/px, front/back use u 0-16, sides u 0-32):
       horizontal brushed runs, lit top edge, a shadow line under the lip - same vanilla treatment as the panels."""
    im=new(32,16); coat(im,0,0,32,3,712,3,bands=False)
    for x in range(32): put(im,x,0,pc(5)); put(im,x,2,pc(1))
    return im
def plinth_tex():
    """16x16: plinth band (lit top edge, brushed face) + caster wheel and levelling-foot texels."""
    im=new(16,16); coat(im,0,0,16,16,721,2)
    for x in range(16): put(im,x,0,pc(5)); put(im,x,1,pc(3))
    for (x,y,c) in ((0,8,g(6)),(1,8,g(4)),(0,9,g(3)),(1,9,g(2))): put(im,x,y,c)
    for (x,y,c) in ((4,8,g(8)),(5,8,g(6)),(4,9,g(5)),(5,9,g(3))): put(im,x,y,c)
    return im
def interior_tex():
    im=new(16,16); coat(im,0,0,16,16,731,1,bands=False); return im
# ---------------- rails (4 texels/px) ----------------
F35={'0':['###','#.#','#.#','#.#','###'],'1':['.#.','##.','.#.','.#.','###'],'2':['###','..#','###','#..','###'],
     '3':['###','..#','.##','..#','###'],'4':['#.#','#.#','###','..#','..#'],'5':['###','#..','###','..#','###'],
     '6':['###','#..','###','#.#','###'],'7':['###','..#','..#','..#','..#'],'8':['###','#.#','###','#.#','###'],'9':['###','#.#','###','..#','###']}
F33={'0':['###','#.#','###'],'1':['.#.','.#.','.#.'],'2':['##.','.#.','.##'],'3':['###','.##','###'],'4':['#.#','###','..#'],
     '5':['.##','.#.','##.'],'6':['#..','###','###'],'7':['###','..#','..#'],'8':['.##','###','##.'],'9':['###','###','..#']}
def rail_tex():
    """16 x 384 texels = 2 px x 48 px at 8 texels/px. Zinc flange shaded smoothly as a rounded strip (lit inner edge,
       darker outer edge, slight falloff toward the bottom); a square 3x3 hole per U (x 2-4) with a lit lower lip;
       small U numbers (3x5 = less than one U tall) every 5U at x 8-14. U1 begins 24 texels above the bottom."""
    im=new(16,384); Z=[g(i) for i in range(11)]
    for y in range(384):
        for x in range(16):
            v=7.6-3.4*(x/15)**1.3-0.6*(y/383)
            v=max(0,min(9.999,v)); i=int(v); put(im,x,y,lerp(Z[i],Z[i+1],v-i))
    for u in range(1,43):
        y0=384-24-8*u
        for dy in range(2,5):
            for dx in range(2,5): put(im,dx,y0+dy,lerp(g(0),g(2),(dy-2)/2))
        for dx in range(2,5): put(im,dx,y0+5,g(8))
        for x in range(16): put(im,x,y0+7,lerp(im.getpixel((x,y0+7))[:3],g(4),0.5))      # faint U boundary
        if u%5==0:
            s_=str(u); x=15-(4*len(s_)-1)
            for ch in s_:
                for ry,row in enumerate(F35[ch]):
                    for rx,v in enumerate(row):
                        if v=='#': put(im,x+rx,y0+1+ry,g(2))
                x+=4
    return im
# ---------------- doors (2 texels/px) ----------------
def mesh_door(w_px,h_px,seed,header=True,handle_side=None,badge=False,density=4):
    """Door face at 4 texels/px. Flat powder-coat frame (6 texels sides, 16 header with the blank badge plate, 6 bottom)
       and a hex-perforated mesh: 2x2 openings (alpha 0 = cutout, equipment shows through) on a pitch of 3, every other
       row shifted by half a pitch, so the openings sit in hex rows. Stepped frame bevel; no speckle."""
    D=density; W,Hh=round(w_px*D),round(h_px*D); im=new(W,Hh); coat_smooth(im,0,0,W,Hh,seed,3)
    side,top,bot=6,(16 if header else 6),6
    for y in range(top,Hh-bot):
        row=(y-top)//3; ry=(y-top)%3
        for x in range(side,W-side):
            rx=(x-side+(row%2)*2)%3 if False else (x-side+(1 if row%2 else 0))%3
            if ry<2 and rx<2: im.putpixel((x,y),(0,0,0,0))
    for x in range(side,W-side): put(im,x,top-1,b(1)); put(im,x,Hh-bot,b(5))
    for y in range(top,Hh-bot): put(im,side-1,y,b(1)); put(im,W-side,y,b(5))
    for a in range(W): put(im,a,0,b(6)); put(im,a,1,b(5)); put(im,a,Hh-1,b(1))
    for a in range(Hh): put(im,0,a,b(6)); put(im,1,a,b(5)); put(im,W-1,a,b(1))
    if badge:                                                         # blank badge plate (no logo)
        cx=W//2
        for y in range(4,12):
            for x in range(cx-10,cx+10): put(im,x,y,b(5) if (y==4 or x==cx-10) else b(2) if (y==11 or x==cx+9) else b(4))
    return im
def door_parts():
    """16x16 small parts: handle (recessed pocket + lever), lock barrel, hinge knuckle."""
    im=new(16,16); coat_smooth(im,0,0,16,16,741,2)
    for y in range(0,8):
        put(im,0,y,b(0)); put(im,1,y,b(1)); put(im,2,y,b(1)); put(im,3,y,b(5))   # handle pocket (recess)
    for y in range(1,7): put(im,1,y,g(6) if y<3 else g(4))                          # lever
    for (x,y,c) in ((6,1,g(8)),(7,1,g(6)),(6,2,g(5)),(7,2,g(3))): put(im,x,y,c)    # lock barrel
    put(im,6,2,g(0))
    for y in range(0,4): put(im,10,y,b(6)); put(im,11,y,b(3))                       # hinge knuckle
    return im
