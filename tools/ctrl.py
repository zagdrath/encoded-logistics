# Encoded Logistics controller textures: procedural maze traces, AE2-style soft shading + glow.
# Run from the project root: python tools/ctrl.py (seeded, so it regenerates identical art).
import random, colorsys
from PIL import Image
B='src/main/resources/assets/encodedlogistics/textures/block/'
def H(s): return tuple(int(s[i:i+2],16) for i in (1,3,5))+(255,)
def lerp(a,b,t): return tuple(round(a[i]+(b[i]-a[i])*t) for i in range(3))+(255,)
def q(t,n): return min(n-1,max(0,int(t*n)))/(n-1) if n>1 else 0

def maze(nx,ny,wx,wy,seed,prune=0.0):
    """Perfect maze (DFS) on nx*ny nodes; wx/wy = wrap (torus) on that axis. Returns edge set."""
    rnd=random.Random(seed); seen={(0,0)}; st=[(0,0)]; E=set()
    while st:
        x,y=st[-1]; nb=[]
        for dx,dy in((1,0),(-1,0),(0,1),(0,-1)):
            X,Y=x+dx,y+dy
            if wx: X%=nx
            if wy: Y%=ny
            if 0<=X<nx and 0<=Y<ny and (X,Y) not in seen: nb.append((X,Y,dx,dy))
        if not nb: st.pop(); continue
        X,Y,dx,dy=rnd.choice(nb); seen.add((X,Y)); E.add((x,y,dx,dy)); st.append((X,Y))
    # prune some dead-end stubs so it reads as circuitry with breathing room
    for _ in range(2):
        deg={}
        for x,y,dx,dy in E:
            X,Y=x+dx,y+dy
            if wx: X%=nx
            if wy: Y%=ny
            deg[(x,y)]=deg.get((x,y),0)+1; deg[(X,Y)]=deg.get((X,Y),0)+1
        for e in list(E):
            x,y,dx,dy=e; X,Y=x+dx,y+dy
            if wx: X%=nx
            if wy: Y%=ny
            if (deg.get((x,y))==1 or deg.get((X,Y))==1) and rnd.random()<prune: E.discard(e)
    return E

def raster(E,ox,oy,W=16,Hh=None):
    Hh=Hh or W
    px=set()
    for x,y,dx,dy in E:
        a=(ox+2*x,oy+2*y); px.add((a[0]%W,a[1]%Hh))
        px.add(((a[0]+dx)%W,(a[1]+dy)%Hh)); px.add(((a[0]+2*dx)%W,(a[1]+2*dy)%Hh))
    return px

TRIM=[H('#E2E7EC'),H('#C3CAD3'),H('#A2ABB6'),H('#838D99'),H('#66707C')]  # light -> dark
BG=[H('#2D323A'),H('#262B32'),H('#20242A'),H('#1A1D22')]
TR=[H('#7C8592'),H('#68717D'),H('#565E69')]
SH=H('#121418')

def tri(v): return abs(v-7.5)/7.5          # 0 centre -> 1 edge, symmetric so tiles join
def tri32(v): return abs((v%32)-15.5)/15.5

def grown_maze(seed,xlo=3,xhi=12,ylo=3,yhi=12,H=16,wrap_y=False,start=None):
    """Labyrinth grown outward from the centre. Every trace is 1px with a 1px gap to every other trace.
    wrap_y makes it seamless top-to-bottom on a strip H tall (for column tiles)."""
    rnd=random.Random(seed)
    cy=H//2-1 if not wrap_y else H//2
    start=start or ((7,cy),(8,cy))
    lit=set(start); tips=[(start[0],(-1,0)),(start[1],(1,0))]
    D=((1,0),(-1,0),(0,1),(0,-1))
    norm=lambda x,y:(x,y%H) if wrap_y else (x,y)
    def inb(x,y): return xlo<=x<=xhi and (wrap_y or ylo<=y<=yhi)
    def ok(n,e):
        x,y=n
        if not inb(x,y) or norm(x,y) in lit: return False
        px,py=e[1],e[0]
        for mx,my in ((x+e[0],y+e[1]),(x+px,y+py),(x-px,y-py),(x+e[0]+px,y+e[1]+py),(x+e[0]-px,y+e[1]-py)):
            if norm(mx,my) in lit: return False
        return True
    while tips:
        i=rnd.randrange(len(tips)) if rnd.random()<0.3 else len(tips)-1
        p,d=tips[i]
        dirs=[d]*3+[e for e in D if e!=(-d[0],-d[1])]
        rnd.shuffle(dirs); grown=False
        for e in dirs:
            a1=norm(p[0]+e[0],p[1]+e[1]); a2=norm(a1[0]+e[0],a1[1]+e[1])
            if ok(a1,e):
                lit.add(a1)
                if ok(a2,e): lit.add(a2); tips.append((a2,e))
                else: tips.append((a1,e))
                grown=True; break
        if not grown: tips.pop(i)
    return lit

EXITS=(5,10)   # mirrored pair (5 <-> 15-5): matches on every edge under any rotation or flip
def exit_stubs(edges='TBLR',depth=3):
    """Trace pixels from the block edge inward, at EXITS on each listed edge, plus inward-facing tips."""
    lit=set(); tips=[]
    for e in edges:
        for p in EXITS:
            if e=='T': pts=[(p,d) for d in range(depth)]; d=(0,1)
            if e=='B': pts=[(p,15-d) for d in range(depth)]; d=(0,-1)
            if e=='L': pts=[(d,p) for d in range(depth)]; d=(1,0)
            if e=='R': pts=[(15-d,p) for d in range(depth)]; d=(-1,0)
            lit|=set(pts); tips.append((pts[-1],d))
    return lit,tips

def grow(seed,lit,tips,xlo,xhi,ylo,yhi,H=16,wrap_y=False,forbid=frozenset()):
    rnd=random.Random(seed); lit=set(lit); tips=list(tips)
    D=((1,0),(-1,0),(0,1),(0,-1))
    norm=lambda x,y:(x,y%H) if wrap_y else (x,y)
    def ok(n,e):
        x,y=n
        if not(xlo<=x<=xhi and (wrap_y or ylo<=y<=yhi)) or norm(x,y) in lit or (x,y) in forbid: return False
        px,py=e[1],e[0]
        for mx,my in ((x+e[0],y+e[1]),(x+px,y+py),(x-px,y-py),(x+e[0]+px,y+e[1]+py),(x+e[0]-px,y+e[1]-py)):
            if norm(mx,my) in lit: return False
        return True
    while tips:
        i=rnd.randrange(len(tips)) if rnd.random()<0.3 else len(tips)-1
        p,d=tips[i]; dirs=[d]*3+[e for e in D if e!=(-d[0],-d[1])]; rnd.shuffle(dirs); g=False
        for e in dirs:
            a1=norm(p[0]+e[0],p[1]+e[1]); a2=norm(a1[0]+e[0],a1[1]+e[1])
            if ok(a1,e):
                lit.add(a1)
                if ok(a2,e): lit.add(a2); tips.append((a2,e))
                else: tips.append((a1,e))
                g=True; break
        if not g: tips.pop(i)
    return lit

def single_pattern(seed):
    """Maze grown from the centre and from eight edge exits; the exits cross the silver frame so the
    traces continue onto the neighbouring faces, AE2-style."""
    lit,tips=exit_stubs('TBLR')
    lit|={(7,7),(8,7)}; tips+=[((7,7),(-1,0)),((8,7),(1,0))]
    return grow(seed,lit,tips,2,13,2,13)

def inside_sheet(seed,gates=(8,)):   # one gate per tile edge, 16px apart
    rnd_lit={(7,7),(8,7),(23,7),(24,7),(7,23),(8,23),(23,23),(24,23)}
    tips=[((x,y),(-1,0) if x%16==7 else (1,0)) for x,y in rnd_lit]
    rnd=random.Random(seed); lit=set(rnd_lit)
    # a trace across every tile edge at each gate, so crossings are evenly spaced (every 16px)
    for g in gates:
        for b in (15,31):
            for k in (0,16):
                h=[((b+d)%32,k+g) for d in (-1,0,1,2)]; lit|=set(h); tips+=[(h[0],(-1,0)),(h[-1],(1,0))]
                v=[(k+g,(b+d)%32) for d in (-1,0,1,2)]; lit|=set(v); tips+=[(v[0],(0,-1)),(v[-1],(0,1))]
    D=((1,0),(-1,0),(0,1),(0,-1)); N=lambda x,y:(x%32,y%32)
    def crosses_ok(p,n):
        if p[0]//16!=n[0]//16 or (p[0]==31 and n[0]==0) or (p[0]==0 and n[0]==31): return p[1]%16 in gates
        if p[1]//16!=n[1]//16 or (p[1]==31 and n[1]==0) or (p[1]==0 and n[1]==31): return p[0]%16 in gates
        return True
    def ok(p,n,e):
        x,y=n
        if n in lit or not crosses_ok(p,n): return False
        px,py=e[1],e[0]
        for mx,my in ((x+e[0],y+e[1]),(x+px,y+py),(x-px,y-py),(x+e[0]+px,y+e[1]+py),(x+e[0]-px,y+e[1]-py)):
            if N(mx,my) in lit: return False
        return True
    while tips:
        i=rnd.randrange(len(tips)) if rnd.random()<0.3 else len(tips)-1
        p,d=tips[i]; dirs=[d]*3+[e for e in D if e!=(-d[0],-d[1])]; rnd.shuffle(dirs); g=False
        for e in dirs:
            a1=N(p[0]+e[0],p[1]+e[1]); a2=N(a1[0]+e[0],a1[1]+e[1])
            if ok(p,a1,e):
                lit.add(a1)
                if ok(a1,a2,e): lit.add(a2); tips.append((a2,e))
                else: tips.append((a1,e))
                g=True; break
        if not g: tips.pop(i)
    return lit

def column_strip(seed):
    """16x32 seamless strip; exits on the left and right edges of each 16px tile."""
    lit=set(); tips=[]
    for off,p in ((0,8),(16,8)):   # same local y on every tile: exits exactly 16px apart
        if True:
            for side,x0,d in (('L',0,(1,0)),('R',15,(-1,0))):
                pts=[(x0+d[0]*k,off+p) for k in range(3)]; lit|=set(pts); tips.append((pts[-1],d))
    lit|={(7,8),(8,8),(7,24),(8,24)}; tips+=[((7,8),(-1,0)),((8,8),(1,0)),((7,24),(-1,0)),((8,24),(1,0))]
    return grow(seed,lit,tips,2,13,0,31,H=32,wrap_y=True)
HUB=set()   # no hub: traces meet at the centre junction

def column_traces(big,off):
    """Left half of the column maze mirrored to the right, so the column is symmetric about its centre."""
    L={(x,y-off[1]) for x,y in big if off[1]<=y<off[1]+16 and x<=7}
    return L|{(15-x,y) for x,y in L}

def build(kind,seed,big=None,off=(0,0)):
    """kind: single | column | inside. Silver edge is 1px; a 1px dark lip sits inside it."""
    im=Image.new('RGBA',(16,16)); p=im.load()
    if kind=='single': framed=lambda x,y: min(x,y)<=1 or max(x,y)>=14
    elif kind=='column': framed=lambda x,y: x<=1 or x>=14
    else: framed=lambda x,y: False
    if kind=='single': E=single_pattern(seed)
    elif kind=='column': E={(x,y-off[1]) for x,y in big if off[1]<=y<off[1]+16}
    else: E={(x-off[0],y-off[1]) for x,y in big if off[0]<=x<off[0]+16 and off[1]<=y<off[1]+16}
    for y in range(16):
        for x in range(16):
            if framed(x,y):
                r=min(x,y,15-x,15-y) if kind=='single' else min(x,15-x)
                lit=(x<=y and x+y<15) or (y<x and x+y<15) if kind=='single' else x<8
                t=(x+y)/30
                if r==0: c=lerp(TRIM[0],TRIM[2],q(t,3)) if lit else lerp(TRIM[2],TRIM[4],q(t,3))
                else: c=H('#15181C') if lit else H('#3A414A')
                p[x,y]=c
            else:
                if kind=='inside': d=0.15+0.8*(tri32(x+off[0])+tri32(y+off[1]))/2
                elif kind=='column': d=tri(x)
                else: d=(tri(x)+tri(y))/2
                p[x,y]=BG[min(3,int(d*4))]
    if kind=='single':
        p[14,1]=p[1,14]=H('#2E343C')
        E=E-HUB
    if kind=='inside': E={e for e in E if not framed(*e)}
    for (x,y) in E|(HUB if kind=='single' else set()):
        sx,sy=(x+1)%16,(y+1)%16
        if (sx,sy) not in E and not framed(sx,sy) and not (kind=='single' and (sx,sy) in HUB): p[sx,sy]=SH
    for (x,y) in E:
        t=(tri(x)+tri(y))/2 if kind!='inside' else (tri32(x+off[0])+tri32(y+off[1]))/2
        p[x,y]=TR[0] if t<0.45 else TR[1] if t<0.75 else TR[2]
    return im,E,framed
def emissive(E,framed,hue_fn,frames,sat=0.72,hub=False):
    out=Image.new('RGBA',(16,16*frames),(0,0,0,0)); o=out.load()
    for f in range(frames):
        oy=16*f
        halo=set()
        for x,y in E:
            for dx,dy in((1,0),(-1,0),(0,1),(0,-1)):
                X,Y=(x+dx)%16,(y+dy)%16
                if (X,Y) not in E and not framed(X,Y): halo.add((X,Y))
        if hub:
            for x,y in HUB:
                h=hue_fn(f,x,y); inner=(x,y) in{(7,7),(8,7),(7,8),(8,8)}
                r,g,b=colorsys.hsv_to_rgb(h,sat*(0.25 if inner else 0.6),1.0 if inner else 0.95); o[x,oy+y]=(int(r*255),int(g*255),int(b*255),255)
        for x,y in E:
            h=hue_fn(f,x,y); t=(tri(x)+tri(y))/2
            v=1.0 if t<0.35 else 0.88 if t<0.7 else 0.76          # brighter toward the core
            s=sat
            r,g,b=colorsys.hsv_to_rgb(h,s,v); o[x,oy+y]=(int(r*255),int(g*255),int(b*255),255)
    return out


# ---------------- multiblock pieces (AE2-style) ----------------
# A formed controller is either a column piece (formed neighbours on both ends of one axis and nowhere else) or a
# block (corners, line ends, cubes, singles). Blocks are framed on every side: each reads as its own section.
# Column pieces have rails (frame) along their long sides only; their ends either
# join the next column piece (one trace across the seam at pixel 8) or meet a block, where there's no cap: the rails
# run straight into the block's frame and the maze stops short of it. Columns come vertical (v) and horizontal (h) in
# texture space; their masks use Arcforge's bits (1 = up, 2 = right, 4 = down, 8 = left: a set bit joins the next
# column piece), only along the column: v 0, 1, 4, 5 and h 0, 2, 8, 10.
#
# The maze grows outward from the centre of each piece, 1px traces with a 1px gap between them: on blocks right up to
# the groove (1px of black round it), on columns with 2px of black round it. Every piece comes in VARIANTS seeded differently, all with the same seam crossings, and the model picks one
# per block face from its position, so a structure's maze never repeats.
#
# The frame is a 1px steel edge, shaded smoothly along its length (a sheen at the middle of each block's edge, darker
# toward its ends, the same rule on every side so edges, corners and seams match), and a 1px dark groove. The maze
# never crosses a framed side, so nothing wraps round the cube edges.

# ======================= Minecraft-style rendering (redraw) =======================
# Rules (shared with the cable / drive array texture pass): one stepped 11-tone steel ramp (cool shadows, warm-neutral
# highlights), no lerped gradients, no near-black except true holes, one-step grain inside materials only, raised
# traces with a top-left highlight and a down-right cast shadow, emissive pixels with 3 tones.
G=[H(c) for c in ['#1F2228','#2B2F36','#373C44','#454B54','#555B65','#666D77','#79808A','#8D949D','#A3A9B1','#BBC0C6','#D3D7DB']]
FRAME=2
VARIANTS=8
GATE=8
COLUMN_MASKS={'v':(0,1,4,5),'h':(0,2,8,10)}
def edge_grain(along,side_seed):
    """Stepped wear on a frame edge: mostly 0, some -1 specks, one 2px glint per edge (same for every block)."""
    r=random.Random(side_seed*31+along)
    if along in (6,7): return 1                       # the glint: the same place on every edge, so seams match
    return -1 if r.random()<0.18 else 0
def frame_px(depth,along,lit_side,corner=None):
    if corner=='tl': return G[10]
    if corner in ('tr','bl'): return G[7]
    if corner=='br': return G[5]
    if depth==0:
        base=9 if lit_side else 6
        return G[max(0,min(10,base+edge_grain(along,1 if lit_side else 2)))]
    # inner lip: shadow under the lit (top/left) rail, lit edge on the shaded (bottom/right) rail
    return G[2] if lit_side else G[5]
# Divider: laid over a block's frame where it meets a column of the same structure, so the join reads as a seam
# (darker steel), stepped and grained like the frame.
def divider(side):
    im=Image.new('RGBA',(16,16),(0,0,0,0)); p=im.load()
    r=random.Random({'u':1,'d':2,'l':3,'r':4}[side])
    for a in range(1,15):
        t=4 if 2<=a<=13 else 3
        if r.random()<0.2: t-=1
        x,y={'u':(a,0),'d':(a,15),'l':(0,a),'r':(15,a)}[side]
        p[x,y]=G[t]
    return im
def piece_sides(kind,mask):
    """Each side of a piece (U, R, D, L): 'frame', 'join' (to the next column piece) or 'open' (a column end that
    meets a block)."""
    U,R,Dn,L=bool(mask&1),bool(mask&2),bool(mask&4),bool(mask&8)
    if kind=='block': return {'U':'frame','R':'frame','D':'frame','L':'frame'}
    if kind=='v': return {'U':'join' if U else 'open','D':'join' if Dn else 'open','L':'frame','R':'frame'}
    return {'L':'join' if L else 'open','R':'join' if R else 'open','U':'frame','D':'frame'}

def piece_pattern(kind,mask,seed):
    """The maze, grown outward from the centre (every trace 1px with a 1px gap to every other). Blocks run it right
    up to the groove (1px of black round the maze); columns keep 2px of black round it: a pixel in from the groove
    along their rails, and two pixels short of an open end. Across a joined seam one trace crosses at pixel 8."""
    sides=piece_sides(kind,mask)
    rail=3 if kind!='block' else 2
    lo=lambda side: 1 if sides[side]=='join' else rail if sides[side]=='frame' else 2
    hi=lambda side: 15 if sides[side]=='join' else 15-rail if sides[side]=='frame' else 13
    lit=set(); tips=[]
    for side in 'URDL':
        if sides[side]!='join': continue
        if side=='U': pts=[(GATE,d) for d in range(3)]; d=(0,1)
        if side=='D': pts=[(GATE,15-d) for d in range(2)]; d=(0,-1)
        if side=='L': pts=[(d,GATE) for d in range(3)]; d=(1,0)
        if side=='R': pts=[(15-d,GATE) for d in range(2)]; d=(-1,0)
        lit|=set(pts); tips.append((pts[-1],d))
    lit|={(7,7),(8,7)}
    tips+=[((7,7),(-1,0)),((8,7),(1,0))]
    return grow(seed,lit,tips,lo('L'),hi('R'),lo('U'),hi('D')),sides


def piece_build(kind,mask,seed):
    E,sides=piece_pattern(kind,mask,seed)
    def edge_info(x,y):
        """(depth, along, lit_side, corner) for framed pixels, else None."""
        hits=[]
        for v,a,side in ((y,x,'U'),(15-y,x,'D'),(x,y,'L'),(15-x,y,'R')):
            if sides[side]=='frame' and v<FRAME: hits.append((v,a,side))
        if not hits: return None
        hits.sort(key=lambda t:t[0]); v,a,side=hits[0]
        corner=None
        if len(hits)>1 and hits[0][0]==hits[1][0]==0:
            s2={hits[0][2],hits[1][2]}
            corner={frozenset('UL'):'tl',frozenset('UR'):'tr',frozenset('DL'):'bl',frozenset('DR'):'br'}.get(frozenset(s2))
        if len(hits)>1 and hits[0][0]==hits[1][0]==1:      # inner lip corner: take the shadow if either side casts it
            lit=any(h[2] in 'UL' for h in hits[:2]); return (1,a,lit,None)
        return (v,a,side in 'UL',corner)
    framed=lambda x,y: edge_info(x,y) is not None
    rnd=random.Random(seed*7+3)
    im=Image.new('RGBA',(16,16)); p=im.load()
    for y in range(16):
        for x in range(16):
            e=edge_info(x,y)
            if e is not None: p[x,y]=frame_px(*e); continue
            t=2                                          # field: dark-mid slate with sparse grain
            r=rnd.random()
            if r<0.10: t=1
            elif r<0.17: t=3
            p[x,y]=G[t]
    for (x,y) in E:                                      # cast shadow down-right of raised traces
        sx,sy=x+1,y+1
        if sx<16 and sy<16 and (sx,sy) not in E and not framed(sx,sy): p[sx,sy]=G[0]
    for (x,y) in E:
        n=sum(((x+dx,y+dy) in E) for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)))
        bend=((x-1,y) in E or (x+1,y) in E) and ((x,y-1) in E or (x,y+1) in E)
        t=8 if (n<=1 or bend) else 7                     # ends and corners catch the light, runs are steel
        if rnd.random()<0.10: t-=1                       # wear
        p[x,y]=G[t]
    return im,E,framed

def emissive(E,framed,hue_fn,frames,sat=0.72,hub=False):
    """Glow overlay with 3 tones per frame: exposed (top/left) edge pixels brighter and whiter, interior base,
    a few dimmer wear pixels - so the glow has the same texture as the metal under it."""
    out=Image.new('RGBA',(16,16*frames),(0,0,0,0)); o=out.load()
    rnd=random.Random(len(E)*13+7); wear={e for e in E if rnd.random()<0.12}
    for f in range(frames):
        oy=16*f
        for x,y in E:
            h=hue_fn(f,x,y); t=(tri(x)+tri(y))/2
            v=0.98 if t<0.4 else 0.90 if t<0.75 else 0.82
            s=sat
            n=sum(((x+dx,y+dy) in E) for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)))
            bend=((x-1,y) in E or (x+1,y) in E) and ((x,y-1) in E or (x,y+1) in E)
            if n<=1 or bend: s=sat*0.6; v=min(1.0,v+0.06)   # glints at ends and corners only
            if (x,y) in wear: v*=0.86
            r,g,b=colorsys.hsv_to_rgb(h,s,v); o[x,oy+y]=(int(r*255),int(g*255),int(b*255),255)
    return out
def clumps16(E): return sum(1 for x in range(15) for y in range(15) if {(x,y),(x+1,y),(x,y+1),(x+1,y+1)}<=E)
N=16
cycle=lambda f,x,y: ((0.62+f/N) + 0.14*((tri(x)+tri(y))/2))%1.0   # two-hue gradient rotating round the wheel
err  =lambda f,x,y: (0.99+0.03*((tri(x)+tri(y))/2))%1.0
import glob, os, shutil
for f in glob.glob(B+'controller*'): os.remove(f)
# 16 hue frames, 6 ticks each, blended: one turn of the colour wheel every 4.8 s.
mc='{\n  "animation": {\n    "frametime": 6,\n    "interpolate": true\n  }\n}\n'
def save(name,kind,mask,seed):
    while True:
        im,E,fr=piece_build(kind,mask,seed)
        if clumps16(E)==0: break
        seed+=1000
    im.save(B+name+'.png'); emissive(E,fr,cycle,N).save(B+name+'_emissive.png'); open(B+name+'_emissive.png.mcmeta','w').write(mc)
    emissive(E,fr,err,1,sat=0.8).save(B+name+'_error_emissive.png')
for v in range(VARIANTS):
    save(f'controller_block_{v}','block',0,11+v)
    for kind,masks in COLUMN_MASKS.items():
        for m in masks:
            save(f'controller_column_{kind}_{m:02d}_{v}',kind,m,101+17*v+m+(0 if kind=='v' else 500))
for side in 'urdl':
    divider(side).save(B+'controller_divider_'+side+'.png')
# The item and particle texture: the first block variant.
for suffix in ('.png','_emissive.png','_emissive.png.mcmeta','_error_emissive.png'):
    shutil.copy(B+'controller_block_0'+suffix,B+'controller'+suffix)
print(len(glob.glob(B+'controller*')),'files')
