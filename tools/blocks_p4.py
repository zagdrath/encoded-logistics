# Phase 4 blocks and parts, v2 - drawn to the controller / cable / Drive Bay standard (docs/TEXTURE_STYLE.md):
# dark recessed field inside the steel bevel, raised steel detail with lit ends and step-0 cast shadows, recesses with
# shadow line + lit lip, accents only as small inlays from the guide's 7-step hue-shifted dye ramps, brushed grain on
# long parts, glow with base / glint / wear tones.
import math, random
from PIL import Image
from el_style import H, put, img
from style_kit import g, accent, dark_field, rail, raised, recess, inlay, brushed, octagon, oct_ring, slats, bolts
CYAN=accent('#3FB4D8'); REDR=accent('#E5483C')
TYPES={'items':accent('#4A8FE0'),'energy':accent('#F0C030'),'redstone':accent('#E5483C'),'lanes':accent('#00D992')}
COLLECT=accent('#4A8FE0'); DEPLOY=accent('#E8913A')
def ring_pts(cx,cy,r0,r1,x0=0,y0=0,x1=16,y1=16):
    return [(x,y) for y in range(y0,y1) for x in range(x0,x1) if r0<math.hypot(x-cx,y-cy)<=r1]
def disc_pts(cx,cy,r,x0=0,y0=0,x1=16,y1=16):
    return [(x,y) for y in range(y0,y1) for x in range(x0,x1) if math.hypot(x-cx,y-cy)<=r]
def glow_from(pts,ramp,seed,size=(16,16)):
    """Guide 6 textured glow: saturated base, whiter glints at exposed top-left/ends, ~12% dimmer wear pixels."""
    im=Image.new('RGBA',size,(0,0,0,0)); P=set(pts); r=random.Random(seed)
    for (x,y) in P:
        exposed=(x,y-1) not in P or (x-1,y) not in P
        k=5 if exposed else 4
        if r.random()<0.12: k=3
        im.putpixel((x,y),ramp[k]+(255,))
    return im
# ---------------- Relay Antenna ----------------
def cable_port(im,cx,cy,seed):
    """Raised steel port collar (6x6) round a 4x4 recess: where cables attach (same reading as the casing port)."""
    pts=[(x,y) for y in range(cy-3,cy+3) for x in range(cx-3,cx+3) if x in (cx-3,cx+2) or y in (cy-3,cy+2)]
    recess(im,cx-2,cy-2,4,3,seed)
    raised(im,pts,seed)
def ribbed_side(seed,mark=None):
    """Casing side: raised slats above and below a central cable port, bolts in the corners."""
    im=img(); dark_field(im,0,0,16,16,seed); rail(im,0,seed+1)
    slats(im,3,13,(3,12),seed+2); cable_port(im,8,8,seed+3)
    for x0 in (3,12):
        raised(im,[(x0,y) for y in range(5,11)],seed+4+x0)
    bolts(im,((2,2),(12,2),(2,12),(12,12)),seed+5)
    if mark: inlay(im,mark,CYAN,seed+6)
    return im
def relay_side(): return ribbed_side(401,[(7,4),(8,4)])
def relay_top():
    """Mast socket: raised octagonal flange round a recess, four bolts."""
    im=img(); dark_field(im,0,0,16,16,405); rail(im,0,406)
    for (x,y) in octagon(7.5,7.5,2.5,1): put(im,x,y,g(1))
    for (x,y) in octagon(7.5,7.5,2.5,1):
        if (x-1,y) not in octagon(7.5,7.5,2.5,1) or (x,y-1) not in octagon(7.5,7.5,2.5,1): put(im,x,y,g(0))
    raised(im,oct_ring(7.5,7.5,4.5,2,2),407)
    bolts(im,((3,3),(11,3),(3,11),(11,11)),408); return im
def relay_front():
    """Drive-bay-style shelving: raised shelf ribs and a centre spine, four recessed SFP cages with cyan port inlays."""
    im=img(); dark_field(im,0,0,16,16,408); rail(im,0,409)
    for i,(x0,y0) in enumerate(((3,4),(9,4),(3,10),(9,10))):
        recess(im,x0,y0,4,3,410+i)
        inlay(im,[(x0+3,y0+1),(x0+3,y0+2)],CYAN,420+i)
    ribs=[(x,y) for y in (2,8,14) for x in range(2,14)]+[(x,y) for x in (7,8) for y in range(2,15)]
    for (x,y) in ribs: put(im,x,y,g(7))
    raised(im,ribs,430,bounds=(1,1,15,15))
    return im
def mast_tex():
    """Mast texels: pole (x0..1) brushed along its length, lit/shade sides; cross-arm strip (rows 4-5) brushed across;
       tip (12..13,0..1) dark red lens."""
    im=img()
    brushed(im,0,0,2,16,[8,5],441,'v'); brushed(im,2,4,10,2,[7,4],442,'h')
    for (x,y,k) in ((12,0,1),(13,0,0),(12,1,0),(13,1,0)): put(im,x,y,REDR[k])
    return im
def tip_blink(frames=16):
    strip=Image.new('RGBA',(16,16*frames),(0,0,0,0))
    for f in (0,1):
        for (x,y,k) in ((12,0,6),(13,0,5),(12,1,5),(13,1,4)): strip.putpixel((x,16*f+y),REDR[k]+(255,))
    return strip
# ---------------- Network Bridge ----------------
CX,CY=7.5,7.5
def bridge_front():
    """Large optical port: raised octagonal flange (2 px), bolts at the chamfers, recessed bore with a step-0 shadow
       under the flange, unlit octagonal lens (cyan ramp, dark steps)."""
    im=img(); dark_field(im,0,0,16,16,450); rail(im,0,451)
    bore=octagon(CX,CY,3.5,2)
    for (x,y) in bore: put(im,x,y,g(1) if (x+y)%5 else g(2))
    for (x,y) in bore:
        if (x-1,y) not in bore or (x,y-1) not in bore: put(im,x,y,g(0))
    for (x,y) in LENS: put(im,x,y,CYAN[1] if (x,y) in octagon(CX,CY,1.5,1) else CYAN[0])
    put(im,6,6,CYAN[2])
    raised(im,oct_ring(CX,CY,5.5,3,2),452,bounds=(1,1,15,15))
    bolts(im,((2,2),(12,2),(2,12),(12,12)),453)
    return im
LENS=octagon(CX,CY,2.5,1)
def bridge_glow(level):
    frames=8 if level=='active' else 1
    strip=Image.new('RGBA',(16,16*frames),(0,0,0,0)); lens=LENS; r=random.Random(455)
    wear={p for p in lens if r.random()<0.12}
    for f in range(frames):
        k=0.5+0.5*math.sin(2*math.pi*f/frames) if level=='active' else 0
        for (x,y) in lens:
            d=math.hypot(x-CX,y-CY)
            if level=='idle': c=CYAN[3] if d<1.2 else CYAN[2]
            else: c=CYAN[6] if d<0.8+k else CYAN[5] if d<1.8+k else CYAN[4]
            if (x,y) in wear: c=CYAN[3]
            strip.putpixel((x,16*f+y),c+(255,))
    return strip
def bridge_side(): return ribbed_side(460,[(2,y) for y in (7,8)]+[(13,y) for y in (7,8)])
# ---------------- Point-to-Point Link ----------------
PC=7.5
def p2p_face(kind,direction,glow=False):
    """10x10 endpoint (3..12): steel bezel, dark field, raised octagonal collar with cast shadow, an accent inlay
       octagon inside it; IN = accent dot in the centre, OUT = accent ticks at the four chamfer-free sides."""
    acc=TYPES[kind]; ring=sorted(oct_ring(PC,PC,2.5,1,1))
    marks=[(7,7),(8,7),(7,8),(8,8)] if direction=='in' else [(7,4),(8,4),(4,7),(4,8),(11,7),(11,8),(7,11),(8,11)]
    if glow: return glow_from(ring+marks,acc,470)
    im=img(); dark_field(im,3,3,10,10,471)
    for a in range(3,13):
        put(im,a,3,g(9 if a not in (6,7) else 10)); put(im,3,a,g(9)); put(im,a,12,g(6)); put(im,12,a,g(6))
    put(im,3,3,g(10)); put(im,12,3,g(7)); put(im,3,12,g(7)); put(im,12,12,g(5))
    for a in range(4,12): put(im,a,4,g(2)); put(im,4,a,g(2))
    collar=oct_ring(PC,PC,3.5,2,1)
    if direction=='out': collar-=set(marks)
    raised(im,collar,472,bounds=(4,4,12,12))
    for (x,y) in octagon(PC,PC,1.5,1): put(im,x,y,g(1))
    inlay(im,ring,acc,473); inlay(im,marks,acc,474)
    return im
# ---------------- Planes (connected textures) ----------------
def plane_tex(acc,mask,seed):
    """Plate face: dark field, guide rail on open sides, three recessed grille slots (shadow line + lit lip),
       small accent inlays at the slot ends - the accent stays small, the steel carries the read."""
    im=img(); dark_field(im,0,0,16,16,seed,base=3); rail(im,mask,seed+1)
    for i,y in enumerate((3,7,11)):
        recess(im,3,y,10,2,seed+2+i)
        inlay(im,[(2,y),(2,y+1)],acc,seed+10+i); inlay(im,[(13,y),(13,y+1)],acc,seed+20+i)
    for y in (5,9,13): raised(im,[(x,y) for x in range(4,12)],seed+30+y,bounds=(0,0,16,16))   # steel bars between slots
    return im
def plane_flash(acc,frames=6):
    strip=Image.new('RGBA',(16,16*frames),(0,0,0,0)); slots=[(x,y) for y0 in (3,7,11) for y in (y0,y0+1) for x in range(3,13)]
    marks=[(x,y) for x in (2,13) for y0 in (3,7,11) for y in (y0,y0+1)]
    base=glow_from(slots+marks,acc,480)
    for f in range(frames):
        k=max(0,1-f/3)
        if k<=0: continue
        fr=base.copy(); fr.putalpha(fr.split()[3].point(lambda a,k=k:int(a*k)))
        strip.alpha_composite(fr,(0,16*f))
    return strip
def plane_edge(acc):
    """Plate edge strip (16x2): steel step 8 over step 5, an accent pip every 4 px."""
    im=img()
    for x in range(16): put(im,x,0,g(8)); put(im,x,1,g(5))
    for x in (2,6,10,14): put(im,x,1,acc[2])
    return im
