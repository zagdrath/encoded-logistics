# Server Rack textures - docs/TEXTURE_STYLE.md via style_kit, black powder-coat variant of the steel ramp.
# Densities: frame 1 texel/px, door mesh 2 texels/px, rails 4 texels/px, devices 8 texels/px.
import random, math
from PIL import Image
from el_style import H, put
from style_kit import g, accent
BLK=[H(c) for c in ['#0B0C0E','#121417','#181B1F','#1F2328','#272C32','#323840','#414851','#58616C']]   # powder coat, 0..7
def b(i): return BLK[max(0,min(7,i))]
def new(w,h): return Image.new('RGBA',(w,h),(0,0,0,0))
def coat(im,x0,y0,w,h,seed,base=3):
    """Powder coat: fine one-step speckle, mostly darker (guide 3 wear), no lerps."""
    r=random.Random(seed)
    for y in range(y0,y0+h):
        for x in range(x0,x0+w):
            v=r.random(); put(im,x,y,b(base-1 if v<0.14 else base+1 if v<0.19 else base))
def bevel(im,x0,y0,w,h,lit=5,dark=1):
    for a in range(w): put(im,x0+a,y0,b(lit)); put(im,x0+a,y0+h-1,b(dark))
    for a in range(h): put(im,x0,y0+a,b(lit)); put(im,x0+w-1,y0+a,b(dark))
    put(im,x0+w-1,y0,b(lit-1)); put(im,x0,y0+h-1,b(dark+1))
# ---------------- frame ----------------
def frame_tex():
    """16x16 general frame / post texture. Columns read as a rounded vertical edge: stepped highlight across x."""
    im=new(16,16); coat(im,0,0,16,16,701)
    prof=[2,3,4,5,6,5,4,3,3,3,3,3,3,3,2,1]                      # rounded edge: lit crest at x=4, falls off both ways
    r=random.Random(702)
    for y in range(16):
        for x in range(16): put(im,x,y,b(prof[x]-(1 if r.random()<0.1 else 0)))
    return im
def side_panel(seed,upper):
    """32x32 (1 texel/px; uv = px/2). Side panel 30 deep x ~21.75 tall: coat, a pressed recess border, two lock points,
       the seam edge (top of the lower panel / bottom of the upper) darker."""
    im=new(32,32); coat(im,0,0,32,32,seed)
    bevel(im,0,0,30,22,4,1)
    for x in range(2,28): put(im,x,2,b(1)); put(im,x,19,b(4))        # pressed panel recess
    for y in range(2,20): put(im,2,y,b(1)); put(im,27,y,b(4))
    for (x,y) in ((5,11),(24,11)):                                   # lock points: raised round with key slot
        for (dx,dy,k) in ((0,-1,5),(-1,0,5),(0,0,4),(1,0,3),(0,1,2),(1,1,1)): put(im,x+dx,y+dy,b(k))
        put(im,x,y,b(0))
    seam_y=21 if not upper else 0
    for x in range(30): put(im,x,seam_y,b(0))
    return im
def roof_tex():
    """32x32 top (16 wide x 32 deep, 1 texel/px; uv = px/2): coat, vent perforation field, two cable-entry slots
       with brush seals, raised lip all round."""
    im=new(32,32); coat(im,0,0,16,32,711)
    bevel(im,0,0,16,32,5,1)
    for y in range(4,14):                                             # vent perforation (front half)
        for x in range(3,13):
            if (x+y)%2==0: put(im,x,y,b(0))
    for y0 in (17,24):                                                # cable-entry slots (rear half)
        for y in range(y0,y0+4):
            for x in range(3,13): put(im,x,y,b(0) if y==y0 else b(1))
        for x in range(3,13,2): put(im,x,y0+2,b(2))                  # brush seal fibres
        for x in range(3,13): put(im,x,y0+4,b(5))                    # lit lip below
    return im
def plinth_tex():
    """16x16: plinth band (2 px tall in world) + caster wheel and levelling-foot texels."""
    im=new(16,16); coat(im,0,0,16,16,721,2)
    for x in range(16): put(im,x,0,b(5)); put(im,x,1,b(2))
    for (x,y,c) in ((0,8,g(6)),(1,8,g(4)),(0,9,g(3)),(1,9,g(2))): put(im,x,y,c)           # caster wheel (grey rubber/steel)
    for (x,y,c) in ((4,8,g(8)),(5,8,g(6)),(4,9,g(5)),(5,9,g(3))): put(im,x,y,c)           # levelling foot (zinc)
    return im
def interior_tex():
    im=new(16,16); coat(im,0,0,16,16,731,1); return im
# ---------------- rails (4 texels/px) ----------------
DIG={'0':['###','#.#','###'],'1':['.#.','##.','.#.'] if False else ['##.','.#.','###'],'2':['##.','.#.','.##'],'3':['###','.##','###'],
     '4':['#.#','###','..#'],'5':['.##','.#.','##.'],'6':['#..','###','###'],'7':['###','..#','..#'],'8':['.##','###','##.'],'9':['###','###','..#']}
def rail_tex():
    """8 x 192 texels = 2 px x 48 px of rail at 4 texels/px. Per U (4 texel rows): a square mounting hole at x 0-1 and the
       U number (3x3 micro digits, zinc-on-black print) right-aligned; 1 at the bottom (y = -13 in model space)."""
    im=new(8,192)
    for y in range(192):
        for x in range(8): put(im,x,y,g(6) if x==0 else g(5) if x<7 else g(3))   # zinc-plated rail flange
    for u in range(1,43):
        y0=192-12-4*u                                                  # U1 starts 3 px (12 texels) above the bottom
        for (dx,dy) in ((0,1),(1,1),(0,2),(1,2)): put(im,dx,y0+dy,g(0))
        put(im,0,y0+3,g(8)); put(im,1,y0+3,g(8))                       # lit lower lip of the hole
        s=str(u); x=7-(4*len(s)-1)+1
        for ch in s:
            for ry,row in enumerate(DIG[ch]):
                for rx,v in enumerate(row):
                    if v=='#' and 0<=x+rx<8: put(im,x+rx,y0+ry,g(3))     # subtle print (zinc 5-6 -> 3), holes stay darkest
            x+=4
        put(im,7,y0+3,g(4))                                           # U boundary tick
    return im
# ---------------- doors (2 texels/px) ----------------
def mesh_door(w_px,h_px,seed,header=True,handle_side=None,badge=False):
    """Door face at 2 texels/px. Solid powder-coat frame (3 texels sides, 8 header with the blank badge plate, 3 bottom),
       hex-perforated mesh (staggered 1-texel holes = alpha 0, so equipment shows through), stepped frame bevel."""
    W,Hh=w_px*2,h_px*2; im=new(W,Hh); coat(im,0,0,W,Hh,seed,3)
    top=8 if header else 3
    for y in range(top,Hh-3):
        for x in range(3,W-3):
            if (x+y)%2==0: im.putpixel((x,y),(0,0,0,0))                # staggered holes: closest hex packing at 2 texels/px
    for x in range(3,W-3): put(im,x,top-1,b(1)); put(im,x,Hh-3,b(5))
    for y in range(top,Hh-3): put(im,2,y,b(1)); put(im,W-3,y,b(5))
    bevel(im,0,0,W,Hh,6,1)
    if badge:                                                         # blank badge plate (no logo)
        cx=W//2
        for y in range(2,6):
            for x in range(cx-5,cx+5): put(im,x,y,b(5) if y==2 or x==cx-5 else b(2) if y==5 or x==cx+4 else b(4))
    return im
def door_parts():
    """16x16 small parts: handle (recessed pocket + lever), lock barrel, hinge knuckle."""
    im=new(16,16); coat(im,0,0,16,16,741,2)
    for y in range(0,8):
        put(im,0,y,b(0)); put(im,1,y,b(1)); put(im,2,y,b(1)); put(im,3,y,b(5))   # handle pocket (recess)
    for y in range(1,7): put(im,1,y,g(6) if y<3 else g(4))                          # lever
    for (x,y,c) in ((6,1,g(8)),(7,1,g(6)),(6,2,g(5)),(7,2,g(3))): put(im,x,y,c)    # lock barrel
    put(im,6,2,g(0))
    for y in range(0,4): put(im,10,y,b(6)); put(im,11,y,b(3))                       # hinge knuckle
    return im
