# Items derived from Arcforge's own textures (Cody's art): dust heap, ingot and plate, recoloured by brightness so
# their shading and grain carry over exactly. Source PNGs live in tools/arcforge_ref/.
import os, random, colorsys
from PIL import Image
REF=os.path.join(os.path.dirname(os.path.abspath(__file__)),'arcforge_ref')
def H(s): return tuple(int(s[i:i+2],16) for i in (1,3,5))
def L(c): return 0.299*c[0]+0.587*c[1]+0.114*c[2]
def recolour(src,stops):
    """Map each pixel's brightness (normalised over the sprite) onto a dark->light list of target stops."""
    im=Image.open(os.path.join(REF,src)).convert('RGBA'); px=[im.getpixel((x,y)) for y in range(16) for x in range(16)]
    ls=[L(p) for p in px if p[3]]; lo,hi=min(ls),max(ls); out=Image.new('RGBA',(16,16),(0,0,0,0))
    stops=[H(s) if isinstance(s,str) else s for s in stops]
    for y in range(16):
        for x in range(16):
            p=im.getpixel((x,y))
            if not p[3]: continue
            t=(L(p)-lo)/max(1,hi-lo)*(len(stops)-1); i=min(len(stops)-2,int(t)); f=t-i
            c=tuple(round(stops[i][k]+(stops[i+1][k]-stops[i][k])*f) for k in range(3))
            out.putpixel((x,y),c+(p[3],))
    return out
def recolour_indexed(src,ramp):
    """Few-colour sprites (ingot 8, plate 6): map the k-th darkest source colour to ramp[k] exactly (stays stepped)."""
    im=Image.open(os.path.join(REF,src)).convert('RGBA')
    cols=sorted({im.getpixel((x,y))[:3] for x in range(16) for y in range(16) if im.getpixel((x,y))[3]},key=L)
    m={c:H(ramp[i]) if isinstance(ramp[i],str) else ramp[i] for i,c in enumerate(cols)}
    out=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            p=im.getpixel((x,y))
            if p[3]: out.putpixel((x,y),m[p[:3]]+(p[3],))
    return out,cols
def silica():
    return recolour('nether_quartz_dust.png',['#3E4148','#7C8089','#AEB2B9','#D3D6DB','#EEF0F2','#FBFCFD'])
def silica_blend():
    return recolour('nether_quartz_dust.png',['#121316','#2C2E33','#4A4D54','#6A6E76','#888C94','#A4A8AF'])
def ferrite():
    """Arcforge ingot recoloured to ferrite: near-black warm-grey ceramic-metal, plus sparse one-step ceramic specks."""
    R=['#0E0E0F','#161618','#1D1E20','#25262A','#33353A','#43464C','#5D6168','#7D828A']
    im,cols=recolour_indexed('steel_ingot.png',R); r=random.Random(9); ramp=[H(c) for c in R]
    for y in range(16):
        for x in range(16):
            p=im.getpixel((x,y))
            if not p[3]: continue
            i=ramp.index(p[:3])
            nb=all(0<=x+dx<16 and 0<=y+dy<16 and im.getpixel((x+dx,y+dy))[3] for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)))
            if nb and 3<=i<=6 and r.random()<0.22: im.putpixel((x,y),ramp[i-1 if r.random()<0.7 else min(7,i+1)]+(255,))
    return im
def long_plate(stretch=3):
    """Arcforge plate made longer: stretch the middle band along its long (2:1) axis, keeping both ends intact,
       then re-centre. Returns the 6-index map (0 = darkest) so callers can colour it."""
    im=Image.open(os.path.join(REF,'steel_plate.png')).convert('RGBA')
    cols=sorted({im.getpixel((x,y))[:3] for x in range(16) for y in range(16) if im.getpixel((x,y))[3]},key=L)
    idx={c:i for i,c in enumerate(cols)}
    d=(2/5**0.5,1/5**0.5)                                      # long axis: 2 across, 1 down
    c0=(7.5,7.5); t=lambda x,y:(x-c0[0])*d[0]+(y-c0[1])*d[1]
    vx,vy=d[0]*stretch,d[1]*stretch; band=(-1.0,1.0)
    out={}
    for y in range(16):
        for x in range(16):
            # inverse map: output pixel -> source pixel (shift back by the stretch, centred)
            ox,oy=x+vx/2,y+vy/2
            tt=t(ox,oy)
            if tt<band[0]: sx,sy=ox,oy
            elif tt>band[1]+stretch: sx,sy=ox-vx,oy-vy
            else:
                f=(tt-band[0])/(band[1]-band[0]+stretch)*(band[1]-band[0])+band[0]
                sx,sy=ox-(tt-f)*d[0],oy-(tt-f)*d[1]
            sx,sy=int(round(sx)),int(round(sy))
            if 0<=sx<16 and 0<=sy<16:
                p=im.getpixel((sx,sy))
                if p[3]: out[(x,y)]=idx[p[:3]]
    return out

def rect_plate(A=(4,1),long=(10,5),short=(-4,6),thick=2,seed=17):
    """Arcforge-plate construction at rectangular proportions: top face parallelogram (long edge 2:1, like the
       plate), thickness band below, 6-index tones: 0 outline/underside, 1 lit rim, 2 side, 3-5 face (5 = lit)."""
    import random
    r=random.Random(seed); ax,ay=A
    def inside(x,y):
        px,py=x+0.5-ax,y+0.5-ay; det=long[0]*short[1]-long[1]*short[0]
        u=(px*short[1]-py*short[0])/det; v=(long[0]*py-long[1]*px)/det
        return 0<=u<=1 and 0<=v<=1,u,v
    top={}
    for y in range(16):
        for x in range(16):
            ok,u,v=inside(x,y)
            if ok: top[(x,y)]=(u,v)
    out={}
    for (x,y),(u,v) in top.items():
        edge=any((x+dx,y+dy) not in top for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)))
        if edge and (u<0.12 or v<0.12): t=1                              # lit rim on the top-left edges
        else:
            t=5 if u+v<0.75 else 4 if u+v<1.35 else 3                    # light falls off toward the far corner
            if r.random()<0.16: t=max(3,t-1)                              # one-step grain
        out[(x,y)]=t
    for k in range(1,thick+1):                                           # thickness band (visible below/right)
        for (x,y) in list(top):
            p=(x,y+k)
            if p not in top and p not in out and 0<=p[1]<16: out[p]=2 if k<thick else 0
    for (x,y),t in list(out.items()):                                    # dark outline on the outer lower edges
        if t in (3,4,5) and ((x+1,y) not in out): out[(x,y)]=2
    return out
