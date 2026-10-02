# The texture standard as reusable primitives (docs/TEXTURE_STYLE.md, as used by the controller, cables, Drive Bay).
import random
from el_style import G, H, put, img, dye_ramp
def g(i): return G[max(0,min(10,i))]
def accent(base_hex):
    """Guide 2.2: 7-step dye ramp (-3..+3) around a base colour; index 3 = base."""
    return dye_ramp(base_hex)
def dark_field(im,x0,y0,w,h,seed,base=2):
    """Guide 3: a flat field at one ramp step (default 2) - no random speckle; seed kept for call compatibility."""
    for y in range(y0,y0+h):
        for x in range(x0,x0+w): put(im,x,y,g(base))
def rail(im,mask=0,seed=1,lip=True):
    """Guide 4 bevel on every side whose mask bit is NOT set (bits 1 up, 2 right, 4 down, 8 left):
       rail top/left 9, bottom/right 6, corners 10/7/7/5; inner lip 2 (lit side) / 5. Clean: no wear specks or glints."""
    U,R,D,Lf=not mask&1,not mask&2,not mask&4,not mask&8
    def wear(base,a): return base
    for a in range(16):
        if U: put(im,a,0,g(wear(9,a)))
        if D: put(im,a,15,g(wear(6,a)))
        if Lf: put(im,0,a,g(wear(9,a)))
        if R: put(im,15,a,g(wear(6,a)))
    if lip:
        for a in range(1,15):
            if U: put(im,a,1,g(2))
            if D: put(im,a,14,g(5))
            if Lf: put(im,1,a,g(2))
            if R: put(im,14,a,g(5))
    if U and Lf: put(im,0,0,g(10))
    if U and R: put(im,15,0,g(7))
    if D and Lf: put(im,0,15,g(7))
    if D and R: put(im,15,15,g(5))
def raised(im,pts,seed,base=7,shadow=True,bounds=(0,0,16,16)):
    """Guide 5 raised steel: runs step 7, ends and bends step 8, step-0 shadow one pixel down-right (no random wear)."""
    P=set(pts); x0,y0,x1,y1=bounds
    if shadow:
        for (x,y) in P:
            s=(x+1,y+1)
            if s not in P and x0<=s[0]<x1 and y0<=s[1]<y1: put(im,*s,g(0))
    for (x,y) in P:
        n=sum((x+dx,y+dy) in P for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)))
        bend=((x-1,y) in P or (x+1,y) in P) and ((x,y-1) in P or (x,y+1) in P)
        t=base+1 if (n<=1 or bend) else base
        put(im,x,y,g(t))
def recess(im,x0,y0,w,h,seed,back=(1,2)):
    """Guide 5 recess: back wall steps 1-2 (darkest part of the face), step-0 shadow line under the overhang (top),
       lit lower lip (step 6) where the floor catches light."""
    for y in range(y0,y0+h):
        for x in range(x0,x0+w): put(im,x,y,g(back[1]))                     # flat back wall, no speckle
    for x in range(x0,x0+w): put(im,x,y0,g(0)); put(im,x,y0+h,g(6))
    for y in range(y0,y0+h+1): put(im,x0-1,y,g(0)) if x0>0 else None
def inlay(im,pts,ramp,seed):
    """Accent inlay (small, guide 2.2): lit top-left pixels use +1/+2, body base, bottom-right -1, never flat."""
    P=set(pts)
    for (x,y) in P:
        up=(x,y-1) in P; left=(x-1,y) in P; dn=(x,y+1) in P; rt=(x+1,y) in P
        k=3
        if not up or not left: k=4
        if not dn and not rt: k=2
        if (not up and not left): k=5
        put(im,x,y,ramp[k])
def brushed(im,x0,y0,w,h,base,seed,axis='v'):
    """Long metal parts: clean stepped bands across the part (base = one step, or one step per row/column across it),
       constant along its length - no random brushed runs (guide 3). seed kept for call compatibility."""
    A,B=(w,h) if axis=='v' else (h,w)
    for a in range(A):
        t=base[a] if isinstance(base,(list,tuple)) else base
        for k in range(B):
            x,y=(x0+a,y0+k) if axis=='v' else (x0+k,y0+a)
            put(im,x,y,g(t))

# ---- structure primitives (the references cover ~half of every face with raised steel) ----
def octagon(cx,cy,r,ch):
    """Filled octagon: |dx|,|dy| <= r and |dx|+|dy| <= 2r-ch  (straight sides, clean 45-degree chamfers)."""
    out=set()
    for y in range(16):
        for x in range(16):
            dx,dy=abs(x-cx),abs(y-cy)
            if dx<=r and dy<=r and dx+dy<=2*r-ch: out.add((x,y))
    return out
def oct_ring(cx,cy,r,ch,w=1):
    return octagon(cx,cy,r,ch)-octagon(cx,cy,r-w,max(0,ch-1))
def slats(im,x0,x1,ys,seed):
    """Raised steel slats (drive-bay-side style): 1 px raised run per row with a recess gap under it."""
    for i,y in enumerate(ys):
        raised(im,[(x,y) for x in range(x0,x1)],seed+i)
def bolts(im,pts,seed):
    """Raised bolt heads: 1 px step 8 with a step-0 shadow down-right."""
    for (x,y) in pts:
        put(im,x+1,y+1,g(0)); put(im,x,y,g(8))
def mid_lum(im):
    def L(c): return 0.299*c[0]+0.587*c[1]+0.114*c[2]
    v=sorted(L(im.getpixel((x,y))) for x in range(16) for y in range(16) if im.getpixel((x,y))[3]); return round(v[len(v)//2])
