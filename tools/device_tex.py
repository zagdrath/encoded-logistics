# Rack device textures at 8 texels/px on 128x128 sheets:
#   front (0,0)-(104,8n) | rear (0,32)-(104,32+8n) | chassis texels (112,0)-(128,16) | extra sprites from (0,64)
import random, math
from PIL import Image
from el_style import H, put
from style_kit import g, accent
GRN=accent('#3CE05A'); AMB=accent('#F5B23A'); RED=accent('#E5483C'); BLU=accent('#4A8FE0'); CU=accent('#D07A40'); LCD=accent('#5CD08A')
def new(): return Image.new('RGBA',(128,128),(0,0,0,0))
def fill(im,x0,y0,w,h,c):
    for y in range(y0,y0+h):
        for x in range(x0,x0+w): put(im,x,y,c)
def _lerp(a,c,t): return tuple(round(a[i]+(c[i]-a[i])*t) for i in range(3))
def speck(im,x0,y0,w,h,base,seed,p=0.0,span=1.8):
    """Smooth shaded fill (kept the old name for callers): light falls off from the top edge down and slightly to the
       right - the sheen on a rack faceplate. No noise."""
    for y in range(y0,y0+h):
        for x in range(x0,x0+w):
            t=0.8*((y-y0)/max(1,h-1))+0.2*((x-x0)/max(1,w-1)); v=base+span/2-span*t
            v=max(0,min(9.999,v)); i=int(v); put(im,x,y,_lerp(g(i),g(i+1),v-i))
def bevel(im,x0,y0,w,h,lit,dark):
    for a in range(w): put(im,x0+a,y0,g(lit)); put(im,x0+a,y0+h-1,g(dark))
    for a in range(h): put(im,x0,y0+a,g(lit)); put(im,x0+w-1,y0+a,g(dark))
def ears(im,y0,h):
    """Rack ears (x 0..9 and 94..103): zinc steel with a mounting screw per U."""
    for x0 in (0,94):
        speck(im,x0,y0,10,h,6,900+x0); bevel(im,x0,y0,10,h,8,4)
        for u in range(h//8):
            cy=y0+u*8+3
            for (dx,dy,k) in ((4,0,9),(5,0,7),(4,1,6),(5,1,3)): put(im,x0+dx,cy+dy,g(k))
def bezel(im,y0,h,seed,base=3):
    speck(im,10,y0,84,h,base,seed); bevel(im,10,y0,84,h,base+3,base-2)
def port_rj45(im,x,y,led_l=None,led_r=None):
    """6x5 copper port: shaded recess (darker at the top under the lip), gold pins, latch notch, link LEDs."""
    for yy in range(5):
        for xx in range(6): put(im,x+xx,y+yy,_lerp(g(0),g(2),yy/4))
    for dx in range(1,5): put(im,x+dx,y+1,CU[4] if dx%2 else CU[3])
    put(im,x+2,y+4,g(1)); put(im,x+3,y+4,g(1))
    for dx in range(6): put(im,x+dx,y+5,g(6))
    put(im,x,y-1,led_l or g(1)); put(im,x+5,y-1,led_r or g(1))
def sfp_cage(im,x,y):
    """8x4 SFP cage: bright steel lip, dark opening, spring tabs."""
    fill(im,x,y,8,4,g(1)); bevel(im,x,y,8,4,8,5); put(im,x+2,y+1,g(0)); put(im,x+5,y+1,g(0))
def fan(im,cx,cy,r):
    for y in range(cy-r,cy+r+1):
        for x in range(cx-r,cx+r+1):
            d=math.hypot(x-cx,y-cy)
            if d<=r:
                a=(math.degrees(math.atan2(y-cy,x-cx))+360-d*20)%120
                put(im,x,y,g(7) if d>r-0.8 else g(6) if d<1.2 else g(5) if a<45 else g(1))
def iec_inlet(im,x,y):
    fill(im,x,y,8,6,g(1)); bevel(im,x,y,8,6,6,2)
    for (dx,dy) in ((2,2),(5,2),(3,4)): put(im,x+dx,y+dy,g(8))
def rear_panel(im,n,seed,fans=2,inlet=True):
    y0=32; speck(im,0,y0,104,8*n,3,seed); bevel(im,0,y0,104,8*n,6,1)
    if inlet: iec_inlet(im,6,y0+1)
    for i in range(fans): fan(im,70+i*14,y0+4*n,min(3+(n-1)*3,6))
    for x in range(20,60,3): put(im,x,y0+2,g(1)); put(im,x,y0+5,g(1))    # vent slots
def chassis(im):
    speck(im,112,0,16,16,4,999); bevel(im,112,0,16,16,6,2)
# ---------------- Firewall (1U) ----------------
def firewall(state='off'):
    im=new(); ears(im,0,8); bezel(im,0,8,1010)
    for yy in range(6):
        for xx in range(3): put(im,11+xx,1+yy,_lerp(RED[4],RED[1],0.75*yy/5+0.25*xx/2))   # red accent stripe, shaded
    for i in range(8): port_rj45(im,26+i*8,2)
    fill(im,90,3,2,2,g(1))                                                              # status LED socket
    rear_panel(im,1,1011); chassis(im)
    return im
def firewall_glow(state):
    im=new()
    if state=='off': return im
    led=GRN if state=='on' else RED
    for i in range(8):
        if state=='on' and i in (0,1,2,4,6):
            put(im,26+i*8,1,GRN[5]); put(im,31+i*8,1,AMB[4] if i%2 else GRN[4])
    put(im,90,3,led[6]); put(im,91,3,led[5]); put(im,90,4,led[5]); put(im,91,4,led[4])
    put(im,98 if False else 12,4,RED[5]) if state=='fault' else None
    return im
# ---------------- Router (1U) ----------------
def router(state='off'):
    im=new(); ears(im,0,8); bezel(im,0,8,1020)
    for yy in range(6):
        for xx in range(3): put(im,11+xx,1+yy,_lerp(BLU[4],BLU[1],0.75*yy/5+0.25*xx/2))   # blue accent, shaded
    for i in range(6): port_rj45(im,18+i*8,2)
    for i in range(4): sfp_cage(im,68+i*5 if False else 66+i*6,2) if False else None
    for i in range(2):
        for j in range(2): sfp_cage(im,68+i*9,1+j*0 if False else 2) if False else None
    for i in range(4): sfp_cage(im,67+i*6,2) if False else None
    for i in range(3): sfp_cage(im,67+i*8,2)                                            # 3 SFP cages
    fill(im,92,3,2,2,g(1))
    rear_panel(im,1,1021); chassis(im)
    # sprite: transceiver as seen in a cage (6x2: blue bail + steel body), drawn by the renderer per installed transceiver
    for x in range(6): put(im,x,64,g(7)); put(im,x,65,g(5))
    put(im,0,64,BLU[4]); put(im,0,65,BLU[2]); put(im,5,64,accent('#7FD8EC')[4])
    return im
def router_glow(state):
    im=new()
    if state=='off': return im
    led=GRN if state=='on' else RED
    if state=='on':
        for i in (0,1,3,4): put(im,18+i*8,1,GRN[5]); put(im,23+i*8,1,AMB[4])
    put(im,92,3,led[6]); put(im,93,3,led[5]); put(im,92,4,led[5]); put(im,93,4,led[4])
    return im
# ---------------- UPS (2U) ----------------
SEG={'0':'abcdef','1':'bc','2':'abged','3':'abgcd','4':'fgbc','5':'afgcd','6':'afgedc','7':'abc','8':'abcdefg','9':'abcdfg','%':''}
def seg_digit(im,x,y,ch,col):
    on=SEG.get(ch,'')
    pts={'a':[(1,0),(2,0)],'b':[(3,1),(3,2)],'c':[(3,4),(3,5)],'d':[(1,6),(2,6)],'e':[(0,4),(0,5)],'f':[(0,1),(0,2)],'g':[(1,3),(2,3)]}
    for sname in on:
        for (dx,dy) in pts[sname]: put(im,x+dx,y+dy,col)
def ups(state='off'):
    im=new(); ears(im,0,16); bezel(im,0,16,1030)
    fill(im,14,3,26,10,g(1)); bevel(im,14,3,26,10,1,6)                                 # LCD window (dark when off)
    for yy in range(8):
        for xx in range(24): put(im,15+xx,4+yy,_lerp(H('#22382C'),H('#101A15'),0.7*yy/7+0.3*xx/23))   # LCD glass, shaded
    for i in range(10): fill(im,44+i*3,6,2,3,g(1))                                     # load bar LEDs (off)
    fill(im,76,5,5,5,g(2)); bevel(im,76,5,5,5,7,3)                                      # power button
    for y in range(2,14):                                                               # vented battery bezel
        for x in range(84,93):
            if (x+y)%2==0: put(im,x,y,g(0))
    rear_panel(im,2,1031,fans=1,inlet=True)
    for i in range(4): iec_inlet(im,24+i*10,32+8)                                       # output sockets
    chassis(im)
    # 7-segment digit sprites 0-9 (4x7 each) at (0,64) for the renderer's live battery %, plus a '%' glyph
    for i,ch in enumerate('0123456789'): seg_digit(im,i*5,64,ch,LCD[5])
    for (dx,dy) in ((0,0),(3,0),(2,2),(1,4),(0,6),(3,6)): put(im,50+dx,64+dy,LCD[5])
    return im
def ups_glow(state,load=6):
    im=new()
    if state=='off': return im
    if state=='on':
        for yy in range(8):
            for xx in range(24): put(im,15+xx,4+yy,_lerp(LCD[3],LCD[1],0.7*yy/7+0.3*xx/23))   # LCD backlight, smooth
        for i in range(10):
            if i<load: fill(im,44+i*3,6,2,3,(GRN if i<6 else AMB if i<8 else RED)[5])
        for (dx,dy) in ((1,1),(2,1),(1,2)): put(im,76+dx,5+dy,GRN[5])
        put(im,78,7,GRN[4])
    else:
        fill(im,15,4,24,8,RED[1]); put(im,77,6,RED[6]); put(im,78,6,RED[5])
    return im
DEVICES={'firewall':(1,firewall,firewall_glow),'router':(1,router,router_glow),'ups':(2,ups,ups_glow)}
