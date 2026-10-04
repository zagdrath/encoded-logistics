# Rack device batch 3: Rack Console (animated drawer), Wireless Controller, 4U / 6U Tape Libraries, LTO tapes + drive.
# Conventions as batches 1-2: 8 texels/px device sheets, smooth shading, no flat fills, no speckle, unbranded.
#   1U-4U sheets 128x128: front (0,0)-(104,8n), back (0,32)-(104,32+8n), chassis (112,0)-(128,16), extras from (0,64).
#   6U sheet 128x256:     front (0,0)-(104,48),  back (0,64)-(104,112),    chassis (112,0)-(128,16), extras from (0,128).
import random, math
from PIL import Image
from el_style import H, put
from style_kit import g, accent
from device_tex import fill, speck, bevel, ears, bezel, port_rj45, sfp_cage, fan, iec_inlet, chassis, _lerp, GRN, AMB, RED, BLU, LCD
from devices2 import accent_stripe, psu
CYAN=accent('#3FB4D8'); GOLD=accent('#E8C24A')
# LTO generations: shell colour (roughly as real cartridges), capacity label
LTO={6:('LTO-6','8M','#26282C'),7:('LTO-7','32M','#7A3FB0'),8:('LTO-8','128M','#2F5A8C'),9:('LTO-9','512M','#2E8A4C'),10:('LTO-10','2G','#8C2A34')}
LTO_R={k:accent(v[2]) for k,v in LTO.items()}
FILL=[GRN,accent('#F0D030'),accent('#F08A2A'),RED]
def sheet(h=128): return Image.new('RGBA',(128,h),(0,0,0,0))
def rear_panel(im,y0,n,seed,psus=1,net=2,drive_bays=0):
    speck(im,0,y0,104,8*n,3,seed); bevel(im,0,y0,104,8*n,6,1)
    bays=[]
    for i in range(drive_bays):
        x=4+(i%2)*26; y=y0+2+(i//2)*12
        for yy in range(10):
            for xx in range(24): put(im,x+xx,y+yy,_lerp(g(0),g(1),yy/9))
        for xx in range(24): put(im,x+xx,y+10,g(5))
        bays.append((x,y-y0))
    px=58
    if psus==2: psu(im,px,y0+1,22,8*n//2-2); psu(im,px,y0+8*n//2+1,22,8*n//2-2)
    else: iec_inlet(im,px,y0+2)
    for i in range(net): port_rj45(im,84+i*8 if n>1 else 30+i*8,y0+(8*n-6 if n>1 else 1))
    if n>1: fan(im,92,y0+4*n-4,4)
    return bays
# ---------------- Rack Console ----------------
def console():
    im=sheet(); ears(im,0,8); speck(im,10,0,84,8,3,0); bevel(im,10,0,84,8,6,1)
    for x in range(36,68):                                                    # recessed pull handle
        put(im,x,3,g(0)); put(im,x,4,_lerp(g(1),g(2),(x-36)/31)); put(im,x,5,g(7))
    fill(im,88,3,2,2,g(1))                                                    # status LED socket
    # back: video + USB + power
    speck(im,0,32,104,8,3,0); bevel(im,0,32,104,8,6,1); iec_inlet(im,6,33)
    fill(im,24,34,8,4,g(1)); bevel(im,24,34,8,4,6,2)                          # video port
    for i in range(3): fill(im,36+i*6,35,4,2,g(0)); put(im,36+i*6,34,g(6))   # USB
    fan(im,80,36,3); chassis(im)
    # chassis opening seen when the drawer is out (dark recess, front face of the fixed body): (0,48)-(104,56)
    for y in range(48,56):
        for x in range(104): put(im,x,y,_lerp(g(0),g(1),(y-48)/7))
    return im
def console_glow(frame,state='on'):
    im=sheet()
    if state=='fault':
        if frame==0: put(im,88,3,RED[6]); put(im,89,3,RED[5])
        return im
    put(im,88,3,GRN[6]); put(im,89,3,GRN[5]); put(im,88,4,GRN[5]); return im
def console_parts():
    """128x128 at 4 texels/px: keyboard (0,0)-(52,48) [13 x 12 px], lid back (56,0)-(108,48), screen + bezel
       (0,52)-(52,100), tray floor (56,52)-(108,100), steel texels (112,0)-(128,16)."""
    im=sheet()
    for y in range(48):                                                       # keyboard: deck + keys
        for x in range(52): put(im,x,y,_lerp(g(4),g(2),0.7*y/47+0.3*x/51))
    for r in range(5):
        for c in range(12):
            x=3+c*4; y=4+r*5
            for yy in range(3):
                for xx in range(3): put(im,x+xx,y+yy,_lerp(g(7),g(5),yy/2))
            put(im,x,y+3,g(1)); put(im,x+1,y+3,g(1)); put(im,x+2,y+3,g(1))
    for x in range(14,38): put(im,x,29,g(7)); put(im,x,30,g(5)); put(im,x,31,g(1))     # space bar
    for y in range(34,46):                                                   # touchpad
        for x in range(18,34): put(im,x,y,_lerp(g(3),g(2),(y-34)/11))
    for x in range(18,34): put(im,x,46,g(6))
    for y in range(48):                                                       # lid back
        for x in range(56,108): put(im,x,y,_lerp(g(5),g(2),0.7*y/47+0.3*(x-56)/51))
    bevel(im,56,0,52,48,7,1)
    for y in range(52,100):                                                   # screen bezel + glass
        for x in range(52): put(im,x,y,_lerp(g(3),g(1),(y-52)/47))
    for y in range(56,96):
        for x in range(4,48): put(im,x,y,_lerp(H('#14201C'),H('#0A1210'),0.6*(y-56)/39+0.4*(x-4)/43))
    for y in range(52,100):                                                   # tray floor
        for x in range(56,108): put(im,x,y,_lerp(g(3),g(1),(y-52)/47))
    speck(im,112,0,16,16,4,0); bevel(im,112,0,16,16,6,2)
    return im
def console_screen(frames=4):
    """Emissive screen (52x48 in a 64x64 frame, 4 frames): an Access-Terminal style grid of item cells + search bar."""
    st=Image.new('RGBA',(64,64*frames),(0,0,0,0)); M=accent('#1FB582'); r=random.Random(9)
    cells=[(c,rr,r.random()) for rr in range(5) for c in range(8)]
    for f in range(frames):
        Y=64*f
        for y in range(4,44):
            for x in range(4,48): st.putpixel((x,Y+y),_lerp(M[1],M[0],(y-4)/39)+(255,))
        for x in range(6,46): st.putpixel((x,Y+6),M[3]+(255,))                # search bar
        for (c,rr,v) in cells:
            x=6+c*5; y=10+rr*6
            if v<0.75:
                col=(M[5] if (c+rr+f)%7 else M[6]) if v<0.5 else M[4]
                for yy in range(4):
                    for xx in range(4): st.putpixel((x+xx,Y+y+yy),col+(255,))
        if f%2==0: st.putpixel((44,Y+6),M[6]+(255,))                          # caret blink
    return st
# ---------------- Wireless Controller ----------------
def wireless():
    im=sheet(); ears(im,0,8); bezel(im,0,8,3010); accent_stripe(im,1,6,CYAN)
    for i in range(6): port_rj45(im,18+i*8,2)
    for i in range(4):                                                        # gold antenna connectors (SMA)
        cx=70+i*5
        for (dx,dy,k) in ((0,2,4),(1,2,5),(2,2,4),(0,3,3),(1,3,2),(2,3,2),(0,4,1),(1,4,1),(2,4,1)): put(im,cx+dx,dy,GOLD[k])
        put(im,cx+1,3,g(0))
    for j in range(3): fill(im,91,1+j*2,1,1,g(1))
    speck(im,0,32,104,8,3,0); bevel(im,0,32,104,8,6,1); iec_inlet(im,6,33)
    for i in range(2):
        cx=30+i*6
        for (dx,dy,k) in ((0,2,4),(1,2,5),(2,2,4),(0,3,3),(1,3,2),(2,3,2),(0,4,1),(1,4,1),(2,4,1)): put(im,cx+dx,32+dy,GOLD[k])
    fan(im,70,36,3); fan(im,84,36,3); chassis(im); return im
def wireless_glow(frame,state='on'):
    im=sheet()
    if state=='fault':
        if frame==0: put(im,91,1,RED[6])
        return im
    r=random.Random(40+frame)
    for i in range(6):
        if r.random()<0.6: put(im,18+i*8,1,GRN[5])
    for j,c in enumerate((GRN,CYAN,CYAN)): put(im,91,1+j*2,c[6] if (frame+j)%2==0 or j==0 else c[4])
    return im
# ---------------- Tape Library ----------------
def lib_layout(n):
    """Slots (window space, texels) and drive bays. 4U: 2 rows x 12, 6U: 4 rows x 12; slots 4 wide x 9 tall."""
    rows=2 if n==4 else 4
    slots=[(17+c*4,4+r*10) for r in range(rows) for c in range(12)]
    return slots
def library(n):
    H_=128 if n==4 else 256; im=sheet(H_); ears(im,0,8*n); speck(im,10,0,84,8*n,3,0); bevel(im,10,0,84,8*n,6,1)
    wy1=8*n-3
    for y in range(2,wy1):                                                    # window: dark interior
        for x in range(14,70): put(im,x,y,_lerp(g(1),g(0),(y-2)/(wy1-2)))
    bevel(im,13,1,58,wy1-0,1,6)
    for (x,y) in lib_layout(n):                                               # magazine slots (empty)
        for yy in range(9):
            for xx in range(4): put(im,x+xx,y+yy,_lerp(g(2),g(1),yy/8) if xx<3 else g(0))
    for x in range(15,69): put(im,x,3,g(5))                                   # picker rail (gantry)
    for y in range(4,wy1-1,3): put(im,66,y,g(4))                              # vertical guide
    for y in range(2,wy1):                                                    # glass sheen: one clean diagonal band
        for x in range(14,70):
            d=(x-14)-(y-2)*1.2-18
            if 0<=d<3: put(im,x,y,_lerp(im.getpixel((x,y))[:3],g(7),0.22 if d<2 else 0.12))
    fill(im,74,3,18,9,g(1)); bevel(im,74,3,18,9,1,6)                          # operator LCD
    for y in range(4,11):
        for x in range(75,91): put(im,x,y,_lerp(H('#22382C'),H('#101A15'),(y-4)/6))
    for i in range(4):                                                        # buttons
        x=75+i*4; fill(im,x,14,3,3,g(4)); put(im,x,14,g(7)); put(im,x+2,16,g(2))
    my=20 if n==4 else 30
    fill(im,74,my,18,4,g(0)); bevel(im,74,my-1,18,6,6,2)                      # mail slot
    for x in range(75,91): put(im,x,my+1,g(1))
    fill(im,88,my+7,2,2,g(1)); fill(im,84,my+7,2,2,g(1))                      # status LEDs
    by=32 if n==4 else 64
    bays=rear_panel(im,by,n,0,psus=2 if n==6 else 1,net=2,drive_bays=2 if n==4 else 4)
    chassis(im)
    ex=64 if n==4 else 128
    # extras: tape edge sprites per generation (4x9) at (k*5, ex), fill bar states (4x1) at (30+k*5, ex), picker (8x6) at
    # (60, ex), drive sled (24x10) at (0, ex+12) with its 3 status LEDs at (26, ex+12)+(3*s)
    for k,gen in enumerate((6,7,8,9,10)):
        R=LTO_R[gen]
        for yy in range(9):
            for xx in range(3): put(im,k*5+xx,ex+yy,_lerp(R[4],R[1],0.7*yy/8+0.3*xx/2))
        for yy in range(2,6): put(im,k*5+1,ex+yy,H('#E8E8E2'))               # label strip
        put(im,k*5+3,ex,g(0))
    for k,c in enumerate(FILL+[None]):
        for xx in range(4): put(im,30+k*5+xx,ex,(c[5] if xx<3 else c[3]) if c else H('#24302A'))
    for yy in range(6):                                                       # picker head
        for xx in range(8): put(im,60+xx,ex+yy,_lerp(g(8),g(4),yy/5))
    for xx in range(8): put(im,60+xx,ex+5,g(2))
    put(im,62,ex+2,AMB[4]); put(im,63,ex+2,g(0)); put(im,64,ex+2,g(0))
    for yy in range(10):                                                      # drive sled (rear)
        for xx in range(24): put(im,xx,ex+12+yy,_lerp(g(6),g(3),0.8*yy/9+0.2*xx/23))
    for xx in range(24): put(im,xx,ex+12,g(8)); put(im,xx,ex+21,g(2))
    for yy in range(2,8): put(im,2,ex+12+yy,g(8)); put(im,3,ex+12+yy,g(4))     # handle
    for xx in range(8,20): put(im,xx,ex+16,g(1))                              # cartridge slot
    for s,c in enumerate((GRN,BLU,AMB)):                                      # idle / reading / writing LEDs
        put(im,26+s*3,ex+12,c[6]); put(im,27+s*3,ex+12,c[5])
    return im
def library_glow(n,frame,state='on'):
    H_=128 if n==4 else 256; im=sheet(H_); my=20 if n==4 else 30
    if state=='fault':
        if frame==0: put(im,88,my+7,RED[6]); put(im,89,my+7,RED[5])
        return im
    for y in range(4,11):
        for x in range(75,91): put(im,x,y,_lerp(LCD[3],LCD[1],(y-4)/6))
    msg=[1,1,0,1,1,1,0,1,0,1,1,0,1,1,1,0]
    for x in range(16):
        if msg[(x+frame*2)%16]: put(im,75+x,6,LCD[6])
    put(im,88,my+7,GRN[6]); put(im,89,my+7,GRN[5]); put(im,84,my+7,AMB[5] if frame%2 else AMB[3])
    return im
# ---------------- items ----------------
DIG={'0':['###','#.#','#.#','#.#','###'],'1':['.#','##','.#','.#','.#'],'6':['###','#..','###','#.#','###'],'7':['###','..#','..#','.#.','.#.'],
     '8':['###','#.#','###','#.#','###'],'9':['###','#.#','###','..#','###']}
def _out(im,col=None):
    src=im.copy(); col=col or g(0)
    for y in range(16):
        for x in range(16):
            if src.getpixel((x,y))[3]: continue
            if any(0<=x+dx<16 and 0<=y+dy<16 and src.getpixel((x+dx,y+dy))[3] for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))): put(im,x,y,col)
def tape_icon(gen):
    """LTO cartridge, front 3/4: generation-coloured shell (lit top-left), white label with the generation number,
       write-protect tab, leader door on the right."""
    R=LTO_R[gen]; im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(2,14):
        for x in range(2,14):
            put(im,x,y,_lerp(R[5],R[1],0.65*(y-2)/11+0.35*(x-2)/11))
    for x in range(2,14): put(im,x,2,R[6])
    for y in range(2,14): put(im,2,y,R[5])
    for y in range(5,11):                                                     # label
        for x in range(4,11): put(im,x,y,_lerp(H('#F4F4EE'),H('#C8CCC4'),(y-5)/5))
    s=str(gen); x=5 if len(s)==1 else 4
    for ch in s:
        for ry,row in enumerate(DIG[ch]):
            for rx,v in enumerate(row):
                if v=='#': put(im,x+rx,5+ry,R[1] if gen!=6 else g(1))
        x+=len(DIG[ch][0])+1
    put(im,12,4,AMB[4]); put(im,12,5,AMB[3])                                  # write-protect tab
    for y in range(9,13): put(im,13,y,R[0])                                   # leader door
    _out(im,R[0] if gen!=6 else H('#0C0D0F')); return im
def drive_icon():
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(5,10):
        for x in range(2,14): put(im,x,y,_lerp(g(7),g(4),0.6*(y-5)/4+0.4*(x-2)/11))
    for y in range(10,13):
        for x in range(1,15): put(im,x,y,_lerp(g(4),g(2),(y-10)/2))
    for x in range(5,11): put(im,x,11,g(0))
    put(im,2,11,g(8)); put(im,13,11,GRN[4]); put(im,12,11,BLU[4])
    _out(im); return im
def device_icon(kind):
    im=Image.new('RGBA',(16,16),(0,0,0,0)); u={'rack_console':1,'wireless_controller':1,'tape_library_4u':4,'tape_library_6u':6}[kind]
    fh={1:3,4:8,6:11}[u]; top0={1:5,4:2,6:1}[u]; front0=top0+(4 if u==1 else 2)
    for y in range(top0,front0):
        for x in range(2,14): put(im,x,y,_lerp(g(7),g(4),0.6*(y-top0)/max(1,front0-top0-1)+0.4*(x-2)/11))
    for y in range(front0,min(16,front0+fh)):
        for x in range(2,14): put(im,x,y,_lerp(g(4),g(2),(y-front0)/max(1,fh-1)))
        put(im,1,y,g(6)); put(im,14,y,g(5))
    f=front0
    if kind=='rack_console':
        for x in range(6,11): put(im,x,f+1,g(0))
        put(im,12,f+1,GRN[4])
    elif kind=='wireless_controller':
        for y in range(f,f+fh): put(im,3,y,CYAN[3])
        for x in (9,11): put(im,x,f+1,GOLD[4])
        put(im,12,f-1,g(6)); put(im,12,f-2,g(5))                             # antenna stub
        put(im,13,f+1,GRN[4])
    else:
        for y in range(f+1,min(15,f+fh-1)):
            for x in range(3,10): put(im,x,y,g(1) if (x+y)%2 else g(0))
        for x in range(3,10,2):
            for y in range(f+2,min(14,f+fh-2),3): put(im,x,y,LTO_R[[7,8,9,10][(x+y)%4]][3])
        put(im,11,f+1,LCD[4]); put(im,12,f+1,LCD[3]); put(im,11,f+4,g(0)); put(im,12,f+4,g(0))
    _out(im); return im
