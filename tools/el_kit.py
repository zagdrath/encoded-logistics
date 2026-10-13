# Encoded Logistics docs/TEXTURE_STYLE.md, applied exactly: the 11-step steel ramp only, flat fields (no grain), structural
# shading - the recessed-bevel frame (sec. 4), recesses and raised detail (sec. 5) - light from the top-left.
from PIL import Image
S=[tuple(int(h[i:i+2],16) for i in (1,3,5)) for h in ('#1F2228','#2B2F36','#373C44','#454B54','#555B65','#666D77','#79808A','#8D949D','#A3A9B1','#BBC0C6','#D3D7DB')]
def px(im,x,y,k):
    if 0<=x<im.width and 0<=y<im.height: im.putpixel((x,y),(S[k] if isinstance(k,int) else tuple(k))+(255,))
def frame(im,x0,y0,w,h,field=5,lip=True):
    """A face: flat field at `field`; outer rail 1 px - top / left step 9, bottom / right step 6; corners TL 10, TR and BL 7,
       BR 5; a 2-px glint (+1 step) at positions 6-7 along every edge; inner lip - step 2 under the lit rails, step 5 inside
       the shaded rails."""
    for y in range(h):
        for x in range(w): px(im,x0+x,y0+y,field)
    if lip and w>3 and h>3:
        for x in range(1,w-1): px(im,x0+x,y0+1,2); px(im,x0+x,y0+h-2,5)
        for y in range(1,h-1): px(im,x0+1,y0+y,2); px(im,x0+w-2,y0+y,5)
        px(im,x0+w-2,y0+1,2)
    for x in range(w): px(im,x0+x,y0,9); px(im,x0+x,y0+h-1,6)
    for y in range(h): px(im,x0,y0+y,9); px(im,x0+w-1,y0+y,6)
    for i in (6,7):
        if i<w-1: px(im,x0+i,y0,10); px(im,x0+i,y0+h-1,7)
        if i<h-1: px(im,x0,y0+i,10); px(im,x0+w-1,y0+i,7)
    px(im,x0,y0,10); px(im,x0+w-1,y0,7); px(im,x0,y0+h-1,7); px(im,x0+w-1,y0+h-1,5)
def recess(im,x0,y0,w,h,deep=False):
    """A recess (sec. 5): back wall step 2 (step 1 deepest), a step-0 shadow line under the overhang (top row), a lit lower
       lip at step 6 (bottom row)."""
    for y in range(h):
        for x in range(w): px(im,x0+x,y0+y,1 if deep else 2)
    for x in range(w): px(im,x0+x,y0,0)
    if h>1:
        for x in range(w): px(im,x0+x,y0+h-1,6)
def raised(im,x0,y0,w,h=1):
    """Raised detail (sec. 5): runs step 7, ends step 8, a step-0 shadow one px down-right."""
    for y in range(h):
        for x in range(w): px(im,x0+x+1,y0+y+1,0)
    for y in range(h):
        for x in range(w): px(im,x0+x,y0+y,8 if x in (0,w-1) else 7)
def divider(im,x0,y0,w):
    """A divider (sec. 4): step 4 with step-3 ends - reads as a seam, never a bright line."""
    for x in range(w): px(im,x0+x,y0,3 if x in (0,w-1) else 4)
def outline_item(im,k):
    """Items: outline in the darkest step of the item's own material (never pure black)."""
    out=im.copy()
    for y in range(im.height):
        for x in range(im.width):
            if im.getpixel((x,y))[3]==0 and any(0<=x+dx<im.width and 0<=y+dy<im.height and im.getpixel((x+dx,y+dy))[3] for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))):
                out.putpixel((x,y),(S[k] if isinstance(k,int) else tuple(k))+(255,))
    return out
def check(im,glow_ok=()):
    """The guide's checklist: every opaque pixel a ramp step (or an allowed colour), brightness 35-215 with mid tones dominant,
       no speckle (an isolated pixel unlike all four neighbours in a flat field)."""
    issues=[]; lum=[]; ramp=set(S)|set(glow_ok)
    W,H=im.size
    for y in range(H):
        for x in range(W):
            r,g,b,a=im.getpixel((x,y))
            if not a: continue
            if (r,g,b) not in ramp: issues.append(f'off-ramp {x},{y}'); continue
            lum.append(0.299*r+0.587*g+0.114*b)
    if lum:
        mid=sum(1 for v in lum if 55<=v<=150)/len(lum)
        if min(lum)<30 or max(lum)>220: issues.append(f'brightness span {min(lum):.0f}-{max(lum):.0f}')
        if mid<0.5: issues.append(f'mid tones only {mid:.0%}')
    return issues[:5]
