# Rack device batch 2: L2 switches (24/48), L3 switch, Compute / Memory / Fabrication / Monitoring servers, NAS, SAN.
# Same conventions as the Firewall/Router/UPS (device_tex.py): 128x128 sheets at 8 texels/px, front (0,0)-(104,8n),
# rear (0,32)-(104,32+8n), chassis texels (112,0)-(128,16), extras from (0,64) (SAN: rear (0,32)-(104,64), extras (0,64)).
# Smooth shading everywhere (speck = smooth sheen), no flat fills, no speckle.
import random, math
from PIL import Image
from el_style import H, put
from style_kit import g, accent
from device_tex import (new, fill, speck, bevel, ears, bezel, port_rj45, sfp_cage, fan, iec_inlet, chassis, seg_digit, _lerp,
                        GRN, AMB, RED, BLU, LCD)
MINT=accent('#00D992'); INDIGO=accent('#5C6CF0'); ORANGE=accent('#F0702A'); MAGENTA=accent('#E05ACF'); GOLD=accent('#E8C24A'); TEAL=accent('#27C4B4')
TIERS=[accent(c) for c in ('#F07A3C','#F0C030','#00D992','#3FA3F5','#A66BF5')]      # 8K 32K 128K 512K 2M (Drive Bay scheme)
FILL=[GRN,accent('#F0D030'),accent('#F08A2A'),RED]                                     # <50 / 50-75 / 75-<100 / full
def accent_stripe(im,y0,h,acc,x=11,w=3):
    for yy in range(h):
        for xx in range(w): put(im,x+xx,y0+yy,_lerp(acc[4],acc[1],0.75*yy/max(1,h-1)+0.25*xx/max(1,w-1)))
def mini_port(im,x,y,w,h):
    """Dense switch port (w x h): shaded recess, a copper pin row, a lit lower lip."""
    for yy in range(h):
        for xx in range(w): put(im,x+xx,y+yy,_lerp(g(0),g(2),yy/max(1,h-1)))
    for xx in range(1,w-1 if w>2 else w): put(im,x+xx,y+1,accent('#D07A40')[3] if xx%2 else accent('#D07A40')[2])
    for xx in range(w): put(im,x+xx,y+h,g(6))
def psu(im,x,y,w,h):
    """Hot-swap PSU module (rear): bevelled slot, fan grille, handle, IEC inlet."""
    speck(im,x,y,w,h,4,0); bevel(im,x,y,w,h,7,2); fan(im,x+5,y+h//2,min(4,h//2-1))
    for yy in range(y+2,y+h-2): put(im,x+w-3,yy,g(8))
    iec_inlet(im,x+11,y+(h-6)//2)
def rear(im,n,psus=1,fans=2,net=0,sfp=0):
    y0=32; speck(im,0,y0,104,8*n,3,0); bevel(im,0,y0,104,8*n,6,1)
    if psus==2: psu(im,2,y0+1,24,8*n-2); psu(im,27,y0+1,24,8*n-2)
    else: iec_inlet(im,6,y0+1)
    for i in range(fans): fan(im,70+i*14 if n<4 else 62+i*14,y0+4*n,min(3+(n-1)*3,6))
    for i in range(net): port_rj45(im,(56 if psus==2 else 20)+i*8,y0+1)
    for i in range(sfp): sfp_cage(im,56+i*10 if n>=4 else 52+i*9,y0+2)
    return im
def ears_bezel(im,n,seed):
    ears(im,0,8*n); bezel(im,0,8*n,seed)
# ---------------- switches ----------------
def switch(kind):
    im=new(); ears_bezel(im,1,2010)
    acc=INDIGO if kind=='l3' else MINT; accent_stripe(im,1,6,acc)
    if kind=='24':
        for r in range(2):
            for c in range(12): mini_port(im,17+c*5,1+r*3,4,2)
        cages=[(78,2),(83,2),(88,2)] if False else None
        for i in range(2): sfp_cage(im,78+i*0,2) if False else None
        for i in range(2): sfp_cage_small(im,79+i*6,1)
    else:
        for r in range(2):
            for c in range(24): mini_port(im,16+c*3,1+r*3,2,2)
        for i in range(2): sfp_cage_small(im,79+i*6,1)
    if kind=='l3':
        for j in range(4): fill(im,91,1+j*2 if j<3 else 7,1,1,g(1))                    # extra status LED column
    fill(im,92,3,1,2,g(1))
    rear(im,1,1,2); chassis(im)
    return im
def sfp_cage_small(im,x,y):
    fill(im,x,y,5,6,g(1)); bevel(im,x,y,5,6,8,5); put(im,x+2,y+2,g(0))
def switch_glow(kind,frame,state='on'):
    im=new()
    if state=='fault':
        if frame==0: put(im,92,3,RED[6]); put(im,92,4,RED[5])
        return im
    r=random.Random(77+frame); cols=12 if kind=='24' else 24; pw=5 if kind=='24' else 3; x0=17 if kind=='24' else 16
    for rr in range(2):
        for c in range(cols):
            if r.random()<0.65: put(im,x0+c*pw,1+rr*3,(GRN if r.random()<0.75 else AMB)[5])
    for i in range(2): put(im,81+i*6,6,GRN[5])                                          # uplink links
    put(im,92,3,GRN[6]); put(im,92,4,GRN[5])
    if kind=='l3':
        for j,c in enumerate((INDIGO,INDIGO,AMB,GRN)): put(im,91,1+j*2 if j<3 else 7,c[5] if (frame+j)%3 else c[4])
    return im
# ---------------- servers ----------------
def drive_blank(im,x,y,w,h):
    speck(im,x,y,w,h,4,0); bevel(im,x,y,w,h,7,2)
    for yy in range(y+2,y+h-2,2): put(im,x+w//2,yy,g(2))
def compute():
    im=new(); ears_bezel(im,2,2020); accent_stripe(im,1,14,ORANGE)
    for i in range(8): drive_blank(im,16+i*7,1,6,14)
    for y in range(2,14):                                                                 # vent grille
        for x in range(74,86):
            if (x+y)%2==0: put(im,x,y,g(1))
    fill(im,88,3,4,4,g(2)); bevel(im,88,3,4,4,7,3)                                          # power button
    fill(im,88,9,2,1,g(1)); fill(im,88,11,2,1,g(1)); fill(im,88,13,2,1,g(1))
    rear(im,2,psus=2,fans=2,net=2); chassis(im); return im
def compute_glow(frame,state='on'):
    im=new()
    if state=='fault': (put(im,88,9,RED[6]),put(im,89,9,RED[5])) if frame==0 else None; return im
    for (x,y) in ((89,4),(90,4),(89,5)): put(im,x,y,GRN[5])
    put(im,88,9,GRN[5]); put(im,89,9,GRN[4]); put(im,88,11,ORANGE[5] if frame%2 else ORANGE[3]); put(im,88,13,GRN[5] if frame!=2 else GRN[3])
    for i in range(8): put(im,18+i*7,13,(GRN[5] if (i+frame)%3 else GRN[3]))            # drive activity
    return im
def memory():
    im=new(); ears_bezel(im,1,2030); accent_stripe(im,1,6,MAGENTA)
    for i in range(16): fill(im,18+i*4,3,2,2,g(1))
    for y in range(2,7):
        for x in range(84,90):
            if (x+y)%2==0: put(im,x,y,g(1))
    fill(im,92,3,1,2,g(1))
    rear(im,1,1,2); chassis(im); return im
def memory_glow(frame,state='on'):
    im=new()
    if state=='fault': (put(im,92,3,RED[6]),put(im,92,4,RED[5])) if frame==0 else None; return im
    r=random.Random(31+frame)
    for i in range(16):
        if r.random()<0.6: fill(im,18+i*4,3,2,2,MAGENTA[5] if r.random()<0.5 else MAGENTA[4])
    put(im,92,3,GRN[6]); put(im,92,4,GRN[5]); return im
def fabrication():
    im=new(); ears_bezel(im,2,2040); accent_stripe(im,1,14,GOLD)
    fill(im,18,2,40,12,g(1)); bevel(im,18,2,40,12,1,6)                                     # schematic window
    for c in range(3):
        for rr in range(2):
            x=20+c*12; y=4+rr*5
            fill(im,x,y,10,4,g(2)); bevel(im,x,y,10,4,5,1)                                 # card slots
    put(im,22,5,GOLD[4]); put(im,23,5,GOLD[3]); put(im,34,5,GOLD[4]); put(im,22,10,GOLD[4])   # cards present
    for i in range(6): fill(im,64+i*4,7,2,2,g(1))
    fill(im,88,3,4,4,g(2)); bevel(im,88,3,4,4,7,3)
    rear(im,2,psus=1,fans=2); chassis(im); return im
def fabrication_glow(frame,state='on'):
    im=new()
    if state=='fault': (put(im,89,4,RED[6]),put(im,90,4,RED[5])) if frame==0 else None; return im
    for (x,y) in ((89,4),(90,4),(89,5)): put(im,x,y,GRN[5])
    for i in range(6): fill(im,64+i*4,7,2,2,GOLD[5] if i<=frame+2 else GOLD[3]) if i<=frame+2 else None
    return im
def monitoring():
    im=new(); ears_bezel(im,1,2050); accent_stripe(im,1,6,TEAL)
    fill(im,18,1,30,6,g(1)); bevel(im,18,1,30,6,1,6)
    for yy in range(4):
        for xx in range(28): put(im,19+xx,2+yy,_lerp(H('#20302C'),H('#0F1A17'),yy/3))
    for i in range(4): fill(im,54+i*4,3,2,2,g(1))
    fill(im,92,3,1,2,g(1)); rear(im,1,1,2); chassis(im); return im
def monitoring_glow(frame,state='on'):
    im=new()
    if state=='fault': (put(im,92,3,RED[6]),put(im,92,4,RED[5])) if frame==0 else None; return im
    for yy in range(4):
        for xx in range(28): put(im,19+xx,2+yy,_lerp(TEAL[2],TEAL[0],yy/3))
    pts=[3,2,2,3,1,2,1,0,1,1,2,1,0,0,1,2,2,1,1,0,1,2,3,2,1,1,0,1]
    sh=frame*2
    for xx in range(28): put(im,19+xx,2+pts[(xx+sh)%28],TEAL[6])
    for i in range(4): fill(im,54+i*4,3,2,2,(GRN,GRN,AMB,GRN)[i][5])
    put(im,92,3,GRN[6]); put(im,92,4,GRN[5]); return im
# ---------------- storage ----------------
NAS_BAY=(13,14); SAN_BAY=(6,14)
def nas_bays(): return [(15+i*12,1) for i in range(6)]
def san_bays(): return [(14+c*6,1+r*15) for r in range(2) for c in range(12)]
def bay(im,x,y,w,h):
    for yy in range(h):
        for xx in range(w): put(im,x+xx,y+yy,_lerp(g(0),g(1),yy/max(1,h-1)))
    for xx in range(w): put(im,x+xx,y+h-1,g(5))
    for yy in range(h): put(im,x,yy+y,g(2)); put(im,x+w-1,yy+y,g(3))
def nas():
    im=new(); ears_bezel(im,2,2060)
    for (x,y) in nas_bays(): bay(im,x,y,12,14)
    fill(im,88,3,2,2,g(1)); fill(im,88,7,2,2,g(1))
    rear(im,2,psus=1,fans=1,net=4); chassis(im)
    sleds(im,12,14,64)                                                                    # extras: NAS sleds per tier
    return im
def san():
    im=new(); ears(im,0,32); bezel(im,0,32,2070)
    for (x,y) in san_bays(): bay(im,x,y,6,14)
    fill(im,86,3,6,10,g(1)); bevel(im,86,3,6,10,1,6)                                    # status panel
    for i in range(4): fill(im,87,16+i*3,2,2,g(1))
    rear(im,4,psus=2,fans=2,sfp=4); chassis(im)
    sleds(im,6,14,80)                                                                     # extras: SAN sleds per tier
    # transceiver-in-cage sprite (same as the Router's) at (0,112)
    for x in range(6): put(im,x,112,g(7)); put(im,x,113,g(5))
    put(im,0,112,BLU[4]); put(im,0,113,BLU[2]); put(im,5,112,accent('#7FD8EC')[4])
    return im
def sleds(im,w,h,y0):
    """Drive sled sprites (one per tier, drawn by the renderer into an occupied bay): carrier with a smooth sheen, a
       tier-colour label stripe, latch; the fullness LED is a separate 2x2 sprite (row y0+h+1: 5 states)."""
    for t in range(5):
        x0=t*(w+1)
        for yy in range(h):
            for xx in range(w): put(im,x0+xx,y0+yy,_lerp(g(6),g(3),0.8*yy/(h-1)+0.2*xx/(w-1)))
        for xx in range(w): put(im,x0+xx,y0,g(8)); put(im,x0+xx,y0+h-1,g(2))
        for yy in range(2,h-4):
            put(im,x0+1,y0+yy,TIERS[t][4] if yy<h//2 else TIERS[t][2])                  # tier stripe
        for yy in range(3,h-5,2): put(im,x0+w//2,y0+yy,g(2))                               # vents
        put(im,x0+w-2,y0+h-3,g(8))
    for k,c in enumerate(FILL+[None]):                                                     # fullness LEDs: green yellow orange red off
        x=70+k*3
        cc=[c[6],c[5],c[5],c[4]] if c else [H('#2B3A33'),H('#24302A'),H('#24302A'),H('#1C2622')]
        for (dx,dy),col in zip(((0,0),(1,0),(0,1),(1,1)),cc): put(im,x+dx,y0+dy,col)
def storage_glow(kind,frame,state='on'):
    im=new()
    sx,sy=((88,3) if kind=='nas' else (87,4))
    if state=='fault':
        if frame==0: put(im,sx,sy,RED[6]); put(im,sx+1,sy,RED[5])
        return im
    put(im,sx,sy,GRN[6]); put(im,sx+1,sy,GRN[5])
    if kind=='san':
        for yy in range(8):
            for xx in range(4): put(im,87+xx,4+yy,_lerp(LCD[3],LCD[1],yy/7)) if yy>0 else None
        for i in range(4): fill(im,87,16+i*3,2,2,(GRN if i==0 else BLU)[5] if (frame+i)%4 else GRN[3])
    else:
        put(im,88,7,AMB[5] if frame%2 else AMB[3])
    return im
DEVICES={'l2_switch_24':(1,lambda: switch('24'),lambda f,s='on': switch_glow('24',f,s)),
         'l2_switch_48':(1,lambda: switch('48'),lambda f,s='on': switch_glow('48',f,s)),
         'l3_switch':(1,lambda: switch('l3'),lambda f,s='on': switch_glow('l3',f,s)),
         'compute_server':(2,compute,compute_glow),'memory_server':(1,memory,memory_glow),
         'fabrication_server':(2,fabrication,fabrication_glow),'monitoring_server':(1,monitoring,monitoring_glow),
         'nas':(2,nas,lambda f,s='on': storage_glow('nas',f,s)),'san':(4,san,lambda f,s='on': storage_glow('san',f,s))}

# ---------------- item icons (16x16, 3/4 slab, outline in the darkest step) ----------------
def _out(im):
    src=im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x,y))[3]: continue
            if any(0<=x+dx<16 and 0<=y+dy<16 and src.getpixel((x+dx,y+dy))[3] for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))): put(im,x,y,g(0))
def icon(kind):
    im=Image.new('RGBA',(16,16),(0,0,0,0)); u={'l2_switch_24':1,'l2_switch_48':1,'l3_switch':1,'compute_server':2,'memory_server':1,
       'fabrication_server':2,'monitoring_server':1,'nas':2,'san':4}[kind]
    fh={1:3,2:5,4:8}[u]; top0=max(1,9-fh-1) if u<4 else 1; front0=top0+(4 if u<4 else 3)
    for y in range(top0,front0):
        for x in range(2,14): put(im,x,y,_lerp(g(7),g(4),0.6*(y-top0)/max(1,front0-top0-1)+0.4*(x-2)/11))
    for y in range(front0,front0+fh):
        for x in range(2,14): put(im,x,y,_lerp(g(4),g(2),(y-front0)/max(1,fh-1)))
        put(im,1,y,g(6)); put(im,14,y,g(5))
    acc={'l2_switch_24':MINT,'l2_switch_48':MINT,'l3_switch':INDIGO,'compute_server':ORANGE,'memory_server':MAGENTA,
         'fabrication_server':GOLD,'monitoring_server':TEAL,'nas':None,'san':None}[kind]
    if acc:
        for y in range(front0,front0+fh): put(im,3,y,acc[4] if y==front0 else acc[2])
    f=front0
    if kind.startswith('l2') or kind=='l3_switch':
        step=2 if kind=='l2_switch_24' else 1
        for x in range(5,12,step): put(im,x,f+1,g(0)); put(im,x,f,GRN[4] if x%3 else g(1))
        put(im,12,f+1,g(7))
    elif kind=='compute_server':
        for x in range(5,10): put(im,x,f+1,g(6)); put(im,x,f+3,g(5))
        put(im,12,f+1,GRN[4])
    elif kind=='memory_server':
        for x in range(5,12,2): put(im,x,f+1,MAGENTA[4])
    elif kind=='fabrication_server':
        for x in range(5,10): put(im,x,f+1,g(1)); put(im,x,f+2,g(1))
        put(im,6,f+1,GOLD[4]); put(im,12,f+2,GRN[4])
    elif kind=='monitoring_server':
        for x in range(5,10): put(im,x,f+1,TEAL[3] if x%2 else TEAL[4])
        put(im,12,f+1,GRN[4])
    elif kind=='nas':
        for x in range(4,13,3):
            for y in range(f+1,f+4): put(im,x,y,g(6)); put(im,x+1,y,g(1))
        put(im,13,f+1,GRN[4])
    else:
        for x in range(4,12,2):
            for y in range(f+1,f+7): put(im,x,y,g(6) if y!=f+4 else g(1))
        put(im,12,f+1,LCD[4]); put(im,12,f+3,GRN[4])
    _out(im); return im
