# Fluid / Pressurized / Energy Storage Drives - built FROM the shipped tier art (shape + tier colours kept), adding a type
# marking. TEXTURE_STYLE: Minecraft-size pixels, ramps, structural shading, no specks, no high-res.
import os
from PIL import Image
from w2_tex import S, H
R='/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/'
TIERS=('8k','32k','128k','512k','2m')
WATER=[H(x) for x in ('#16306E','#1E4FA8','#2D6FD2','#4F95EE','#8CC4FF')]           # sight glass (deeper than the 512K tier blue)
BRASS=[H(x) for x in ('#5A3410','#8A5420','#C07A30','#E8A04A','#FFC878')]           # valve, warm accent
VOLT=[H(x) for x in ('#5A4A08','#8F7410','#D8B020','#FFD84A','#FFF0A0')]            # charge (yellow)
def px(im,x,y,c):
    if 0<=x<im.width and 0<=y<im.height: im.putpixel((x,y),tuple(c)+(255,))
def body_box(im):
    """The housing's light top face: bbox of the light pixels (luminance > 200) that are not the tier stripe."""
    pts=[(x,y) for y in range(16) for x in range(16) if im.getpixel((x,y))[3] and sum(im.getpixel((x,y))[:3])/3>200 and max(im.getpixel((x,y))[:3])-min(im.getpixel((x,y))[:3])<30]
    xs=[p[0] for p in pts]; ys=[p[1] for p in pts]; return min(xs),min(ys),max(xs),max(ys),set(pts)
def icon(tier,kind):
    im=Image.open(R+f'item/storage_drive_{tier}.png').convert('RGBA').copy(); x0,y0,x1,y1,face=body_box(im)
    cx,cy=(x0+x1)//2,(y0+y1)//2+1
    if kind=='fluid':                                    # sight-glass window (4 x 3): dark frame, water ramp, surface line, glint
        for y in range(cy-2,cy+3):
            for x in range(cx-3,cx+3):
                if (x,y) not in face: continue
                edge=y in (cy-2,cy+2) or x in (cx-3,cx+2)
                px(im,x,y,S[3] if edge else (WATER[4] if y==cy-1 else WATER[2] if y==cy else WATER[1]))
        px(im,cx-2,cy-1,(255,255,255))
    elif kind=='pressurized':                            # two dark reinforcing straps + a brass valve wheel with stem
        for x in range(x0,x1+1):
            for y in (cy-3,cy+2):
                if (x,y) in face: px(im,x,y,S[2])
                if (x,y+1) in face and y==cy-3: px(im,x,y+1,S[7])
        for (dx,dy,k) in ((-1,0,4),(0,0,3),(1,0,2),(0,-1,3),(0,1,1)): px(im,cx+dx,cy+dy-1,BRASS[k])
        px(im,cx,cy+1,S[3])
    else:                                                # energy: 4-segment charge bar, yellow (lit, base, shade)
        for i in range(4):
            for y in (cy-1,cy):
                x=cx-4+i*2
                if (x,y) in face: px(im,x,y,VOLT[3] if y==cy-1 else VOLT[2])
                if (x+1,y) in face: px(im,x+1,y,S[4])
        px(im,cx-4,cy-1,VOLT[4])
    return im
# ---------- holders ----------
def bay_sheet(kind):
    """Same layout as drive_bay/drives.png (16 x 16, a 6 x 2 sled per tier at row 2*tier, tier colour in column 5): adds the
       type marking in columns 1-3 of each sled."""
    src=Image.open(R+'block/drive_bay/drives.png').convert('RGBA').copy()
    for t in range(5):
        y=2*t
        if kind=='fluid': px(src,2,y,WATER[3]); px(src,3,y,WATER[2]); px(src,2,y+1,WATER[2]); px(src,3,y+1,WATER[1])
        elif kind=='pressurized': px(src,1,y,S[3]); px(src,1,y+1,S[3]); px(src,3,y,BRASS[3]); px(src,3,y+1,BRASS[1])
        else: px(src,1,y,VOLT[3]); px(src,2,y,VOLT[3]); px(src,3,y,S[4]); px(src,1,y+1,VOLT[2]); px(src,2,y+1,VOLT[2])
    return src
def charge_leds():
    """Energy charge lights, same 2x2-cell layout as drive_bay/leds.png: k 0 full (bright yellow), 1 = 75%, 2 = 50%,
       3 = 25% (dim amber), 4 = empty (off)."""
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    cols=[(VOLT[4],VOLT[3]),(VOLT[3],VOLT[2]),(VOLT[2],VOLT[1]),(BRASS[2],BRASS[1]),(S[3],S[2])]
    for k,(a,b) in enumerate(cols):
        x=k*3
        px(im,x,0,a); px(im,x+1,0,a); px(im,x,1,b); px(im,x+1,1,b)
    return im
def charging_strip():
    """Charging pulse (animated, 4 frames x 4 ticks): a 2x2 light stepping up the yellow ramp - drawn instead of the charge
       light while the drive is being charged."""
    st=Image.new('RGBA',(16,64),(0,0,0,0))
    for f in range(4):
        c=VOLT[1+f]; 
        for (x,y) in ((0,0),(1,0),(0,1),(1,1)): px(st,x,16*f+y,c)
    return st
def rack_sleds(kind,san):
    """NAS (12 x 14 sleds at 13t, row 0) / SAN (6 x 14 at 7t, row 0) sheets for a type: the shipped sleds (copied from nas.png
       / san.png rows 64 / 80) + the type marking; the type's lights at (70 + 3k, 0) (energy: charge cells; others: copies of
       the shipped fill lights)."""
    sheet=Image.open(R+('block/rack_device/san.png' if san else 'block/rack_device/nas.png')).convert('RGBA')
    row=80 if san else 64; w=6 if san else 12
    out=Image.new('RGBA',(128,16),(0,0,0,0)); out.alpha_composite(sheet.crop((0,row,128,row+14)),(0,0))
    for t in range(5):
        x0=t*(w+1); mx=x0+(3 if san else 5)
        if kind=='fluid':
            for y in range(4,10): px(out,mx,y,WATER[3] if y<6 else WATER[2]); px(out,mx+1,y,WATER[2] if y<6 else WATER[1])
            px(out,mx,5,WATER[4])
        elif kind=='pressurized':
            for x in range(x0+1,x0+w-1): px(out,x,3,S[2]); px(out,x,10,S[2])
            px(out,mx,6,BRASS[3]); px(out,mx+1,6,BRASS[2]); px(out,mx,7,BRASS[2]); px(out,mx+1,7,BRASS[1])
        else:
            for i in range(4): px(out,mx,4+i*2,VOLT[3]); px(out,mx+1,4+i*2,VOLT[2])
    if kind=='energy':
        cl=charge_leds()
        for k in range(5): out.alpha_composite(cl.crop((k*3,0,k*3+2,2)),(70+3*k,0))
    return out
def pack(kind):
    """Midrange Disk Drive pack for a typed drive (16 x 16 sheet): platter top 6 x 6 at (0,0), platter edge 6 x 1 at (0,7),
       hub 3 x 3 at (8,0), hub edge 3 x 1 at (8,4). Fluid: blue glass discs; pressurized: dark steel discs, brass hub
       (valve); energy: discs with a yellow charge ring."""
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    if kind=='fluid': top,edge,hub,hube=WATER,WATER,S,S
    elif kind=='pressurized': top,edge,hub,hube=S,S,BRASS,BRASS
    else: top,edge,hub,hube=S,S,VOLT,VOLT
    for y in range(6):
        for x in range(6):
            k=(3 if (x+y)<5 else 2) if kind!='pressurized' else (5 if (x+y)<5 else 4)
            if kind=='energy' and (x in (0,5) or y in (0,5)): px(im,x,y,VOLT[3] if (x+y)<5 else VOLT[2])
            else: px(im,x,y,top[k] if kind!='energy' else S[k+4])
    for x in range(6): px(im,x,7,edge[1] if kind!='energy' else VOLT[1])
    for y in range(3):
        for x in range(3): px(im,8+x,y,hub[4] if (x+y)<2 else hub[3])
    for x in range(3): px(im,8+x,4,hube[2])
    return im
