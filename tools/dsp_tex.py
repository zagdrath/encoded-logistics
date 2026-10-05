# Display Panel + Small Wireless Bridge textures (docs/TEXTURE_STYLE.md: steel ramp, structural + stepped shading, no specks).
from PIL import Image
from w2_tex import S, LB, OR, YE, RD, new, px, fill, frame, recess, raised, field, glow_px, outline, H
def bezel():
    """Dark bezel strip material (steps 3-6): lit top/left lip, shaded bottom/right, stepped field."""
    im=new(); field(im,0,0,16,16,4)
    for x in range(16): px(im,x,0,S[6]); px(im,x,15,S[2])
    for y in range(16): px(im,0,y,S[6]); px(im,15,y,S[2])
    for k in (6,7): px(im,k,0,S[7]); px(im,0,k,S[7])
    return im
def glass_off():
    """Dark glass (steps 1-2 - the only near-black field, it is a screen): stepped diagonal reflection band and a
       top-left glint; deterministic, no specks."""
    im=new()
    for y in range(16):
        for x in range(16):
            t=x+y; px(im,x,y,S[2] if 4<=t<=7 else S[1] if t<22 else S[0])
    px(im,1,1,S[3]); px(im,2,1,S[3]); px(im,1,2,S[3])
    return im
def side():
    """Panel body edge (8 px deep): stepped steel with a seam where the plate meets the body."""
    im=new(); field(im,0,0,16,16,5)
    for x in range(16): px(im,x,0,S[8]); px(im,x,15,S[3])
    for y in range(16): px(im,11,y,S[3]); px(im,12,y,S[7])
    return im
def back():
    """Mounting plate: framed, keyhole slots (recessed) for wall mounting, the cable socket in the middle, bolts."""
    im=new(); field(im,0,0,16,16,5); frame(im,0,0,16,16)
    for (x,y) in ((3,3),(11,3)): recess(im,x,y,2,3,back=1)
    recess(im,6,6,4,4,back=1)
    for (x,y) in ((7,7),(8,7),(7,8),(8,8)): px(im,x,y,S[0] if (x,y)==(7,7) else S[2])
    raised(im,[(3,12),(4,12)]); raised(im,[(11,12),(12,12)])
    return im
def bottom():
    im=side(); recess(im,6,4,4,4,back=1); return im                        # bottom cable socket
LED=[(12,14),(13,14)]
def led(state):
    ramp={'boot':LB,'online':LB,'nosignal':YE}[state]; blink=state in ('boot','nosignal')
    im=new(16,32 if blink else 16)
    for i,(x,y) in enumerate(LED): px(im,x,y,glow_px(ramp,i==0))
    return im,blink
# ---- Small Wireless Bridge (6 x 6 x 3 module) ----
def swb_face():
    im=new(); field(im,0,0,16,16,6); frame(im,0,0,16,16); recess(im,4,4,8,6,back=2)
    for x in range(5,11): px(im,x,6,S[7]); px(im,x+1,7,S[0]) if x+1<12 else None
    fill(im,10,12,3,2,S[1]); return im
def swb_side():
    im=new(); field(im,0,0,16,16,5)
    for x in range(16): px(im,x,0,S[9]); px(im,x,15,S[3])
    return im
def swb_led(state):
    ramp={'unlinked':YE,'linked':LB,'fault':OR}[state]; blink=state=='unlinked'
    im=new(16,32 if blink else 16)
    for x in range(10,13): px(im,x,12,glow_px(ramp,x==10)); px(im,x,13,glow_px(ramp))
    return im,blink
def icon_panel():
    im=new()
    for y in range(2,14):
        for x in range(1,15): px(im,x,y,S[4] if (x in (1,14) or y in (2,13)) else (S[2] if (x+y)<12 else S[1]))
    for x in range(1,15): px(im,x,2,S[6])
    for x in range(3,9): px(im,x,5,(0x3C,0xE0,0x5A))
    for x in range(3,11): px(im,x,8,(0x3C,0xE0,0x5A))
    for x in range(3,7): px(im,x,11,LB[3])
    px(im,12,12,LB[6])
    return outline(im,S[0])
def icon_swb():
    im=new()
    for y in range(6,13):
        for x in range(4,12): px(im,x,y,S[7] if (x+y)<14 else S[6] if (x+y)<19 else S[5])
    for x in range(4,12): px(im,x,6,S[9])
    for y in range(6,13): px(im,4,y,S[9])
    for y in range(2,6): px(im,9,y,S[8]); px(im,10,y,S[6])
    px(im,9,1,LB[6]); px(im,9,11,LB[6]); px(im,10,11,LB[3])
    return outline(im,S[1])
