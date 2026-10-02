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


# ---------------- connected (multiblock) pieces ----------------
# mask bits match Arcforge: 1 = up, 2 = right, 4 = down, 8 = left (a set bit = that side joins another controller).
# Mask 0 is the single block (controller.png).
# Open sides get a flat 1px silver edge and a 1px dark lip; the maze stays a further pixel in from the lip (a clean
# dark channel, AE2-style) and never crosses an open side, so nothing wraps round the cube edges. Joined sides carry
# one trace across the seam at pixel 8. The 3x3 corner squares stay clear of traces so the inner-corner overlays
# (controller_corner_*) can close the border round a frame's holes.
SILVER=H('#C3CAD3'); LIP=H('#15181C')
CORNER=frozenset((x,y) for x in range(16) for y in range(16) if (x<=2 or x>=13) and (y<=2 or y>=13))
def ctm_pattern(mask,seed,gate=8):
    U,R,Dn,L=bool(mask&1),bool(mask&2),bool(mask&4),bool(mask&8)
    xlo,xhi=(1 if L else 3),(15 if R else 12); ylo,yhi=(1 if U else 3),(15 if Dn else 12)
    lit=set(); tips=[]
    for side,conn in (('U',U),('R',R),('D',Dn),('L',L)):
        if not conn: continue
        if side=='U': pts=[(gate,d) for d in range(3)]; d=(0,1)
        if side=='D': pts=[(gate,15-d) for d in range(2)]; d=(0,-1)
        if side=='L': pts=[(d,gate) for d in range(3)]; d=(1,0)
        if side=='R': pts=[(15-d,gate) for d in range(2)]; d=(-1,0)
        lit|=set(pts); tips.append((pts[-1],d))
    lit|={(7,7),(8,7)}
    tips+=[((7,7),(-1,0)),((8,7),(1,0))]
    return grow(seed,lit,tips,xlo,xhi,ylo,yhi,forbid=CORNER)

def ctm_build(mask,seed):
    U,R,Dn,L=bool(mask&1),bool(mask&2),bool(mask&4),bool(mask&8)
    def framed(x,y):
        return (not U and y<=1) or (not Dn and y>=14) or (not L and x<=1) or (not R and x>=14)
    E=ctm_pattern(mask,seed)
    im=Image.new('RGBA',(16,16)); p=im.load()
    for y in range(16):
        for x in range(16):
            if framed(x,y):
                outer=(not U and y==0) or (not Dn and y==15) or (not L and x==0) or (not R and x==15)
                p[x,y]=SILVER if outer else LIP
            else:
                p[x,y]=BG[min(3,int(((tri(x)+tri(y))/2)*4))]
    for (x,y) in E:
        sx,sy=x+1,y+1
        if sx<16 and sy<16 and (sx,sy) not in E and not framed(sx,sy): p[sx,sy]=SH
    for (x,y) in E:
        t=(tri(x)+tri(y))/2
        p[x,y]=TR[0] if t<0.45 else TR[1] if t<0.75 else TR[2]
    return im,E,framed

def corner(name):
    """The 2x2 that closes the border where two joined sides meet an open diagonal: silver in the very corner, lip
    on the other three pixels (they continue the neighbours' edges)."""
    im=Image.new('RGBA',(16,16),(0,0,0,0)); p=im.load()
    cx,cy=(0 if 'l' in name[1] else 15),(0 if name[0]=='t' else 15)
    ix,iy=(1 if cx==0 else 14),(1 if cy==0 else 14)
    p[cx,cy]=SILVER; p[ix,cy]=LIP; p[cx,iy]=LIP; p[ix,iy]=LIP
    return im

def clumps16(E): return sum(1 for x in range(15) for y in range(15) if {(x,y),(x+1,y),(x,y+1),(x+1,y+1)}<=E)
N=16
cycle=lambda f,x,y: ((0.62+f/N) + 0.14*((tri(x)+tri(y))/2))%1.0   # two-hue gradient rotating round the wheel
err  =lambda f,x,y: (0.99+0.03*((tri(x)+tri(y))/2))%1.0
import glob, os
for f in glob.glob(B+'controller_column*')+glob.glob(B+'controller_inside*')+glob.glob(B+'controller_ctm_*'): os.remove(f)
# 16 hue frames, 8 ticks each, blended: one turn of the colour wheel every 6.4 s.
mc='{\n  "animation": {\n    "frametime": 8,\n    "interpolate": true\n  }\n}\n'
CTM_SEED=5
for m in range(0,16):
    sd=CTM_SEED
    while True:
        im,E,fr=ctm_build(m,sd)
        if clumps16(E)==0: break
        sd+=100
    n='controller' if m==0 else f'controller_ctm_{m:02d}'
    im.save(B+n+'.png'); emissive(E,fr,cycle,N).save(B+n+'_emissive.png'); open(B+n+'_emissive.png.mcmeta','w').write(mc)
    emissive(E,fr,err,1,sat=0.8).save(B+n+'_error_emissive.png')
    print(n,'seed',sd,'colours',len(set(im.getdata())))
for c in ('tl','tr','bl','br'):
    corner(c).save(B+'controller_corner_'+c+'.png')
