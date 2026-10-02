# Phase 3 blocks: Fabricator, Gateway, Scheduler (Core / Job Buffer / Thread Unit) with connected textures.
import random, math
from el_style import *
from PIL import Image
WARM=[H('#7A4A10'),H('#C88420'),H('#F2B848'),H('#FFE6A8')]        # fabricator work lights
MINT=[H('#0B3A2C'),H('#127A57'),H('#1FB582'),H('#5CF0B8'),H('#C8FFE9')]
HOT=[H('#5A1A08'),H('#A8380E'),H('#F0702A'),H('#FFC07A')]          # thread unit under load
GOLD=[H('#7A5C08'),H('#B88E14'),H('#E8C24A'),H('#F5DE8A')]
# ---------------- Fabricator ----------------
WIN=(3,3,10,8)
def fab_front():
    im=img(); field(im,0,0,16,16,4,301); bevel_frame(im,0,0,16,16,302)
    x0,y0,w,h=WIN
    for y in range(y0,y0+h):
        for x in range(x0,x0+w): put(im,x,y,g(1) if y==y0 else g(2))          # cell back wall
    for x in range(x0+1,x0+w-1): put(im,x,y0+h-2,g(5)); put(im,x,y0+h-1,g(3))  # work plate
    for x in range(x0-1,x0+w+1): put(im,x,y0-1,g(2)); put(im,x,y0+h,g(7))
    for y in range(y0-1,y0+h+1): put(im,x0-1,y,g(2)); put(im,x0+w,y,g(7))
    for x in range(3,13): put(im,x,13,g(2))                                     # status strip
    for i,c in enumerate((GOLD[2],g(6),g(6))): put(im,4+2*i,13,c)
    put(im,11,13,g(1)); put(im,12,13,g(1))
    return im
def fab_side(seed):
    im=img(); field(im,0,0,16,16,4,seed); bevel_frame(im,0,0,16,16,seed+1)
    for y in range(3,13): put(im,5,y,g(2)); put(im,6,y,g(6)); put(im,10,y,g(2)); put(im,11,y,g(6))   # rail channels
    port_ring(im,8,8)
    return im
def fab_lights(on):
    """Work-light strip (8x1, row 0) and its glow."""
    im=img()
    for x in range(8): put(im,x,0,(WARM[3] if x%3 else WARM[2]) if on else g(5)); put(im,x,1,WARM[1] if on else g(3))
    return im
def fab_arm():
    """Arm parts: gantry beam (row 0-1), head (2-5), nozzle (6)."""
    im=img()
    for x in range(10): put(im,x,0,g(8)); put(im,x,1,g(5))
    for y in range(2,6):
        for x in range(4): put(im,x,y,g(7) if x==0 or y==2 else g(5))
    put(im,1,4,GOLD[2]); put(im,2,4,GOLD[1])
    for x in range(2): put(im,x,6,g(3))
    return im
# ---------------- Gateway ----------------
def gateway_face(glow=False,active=False):
    """Every face: bevel frame, recessed 8x8 port opening with a cable port inside, four corner status lights."""
    im=img()
    if not glow:
        field(im,0,0,16,16,4,311); bevel_frame(im,0,0,16,16,312)
        for y in range(4,12):
            for x in range(4,12): put(im,x,y,g(1) if (y==4 or x==4) else g(6) if (y==11 or x==11) else g(2))
        port_ring(im,8,8)
    for (x,y) in ((2,2),(13,2),(2,13),(13,13)):
        if glow and not active: continue
        c=(MINT[3] if active else MINT[1]) if (x,y) in ((2,2),(13,13)) else (AMBER[3] if active else AMBER[1])
        put(im,x,y,c)
    return im
# ---------------- Scheduler (connected textures) ----------------
# mask bits (same as the controller / Arcforge): 1 = up, 2 = right, 4 = down, 8 = left; a set bit joins another
# Scheduler block of the SAME formed structure on that side, and the frame is dropped there.
def sched_frame(im,mask,seed):
    U,R,D,Lf=bool(mask&1),bool(mask&2),bool(mask&4),bool(mask&8); r=random.Random(seed)
    for a in range(16):
        if not U: put(im,a,0,g(9 if a not in (6,7) else 10)); put(im,a,1,g(2))
        if not D: put(im,a,15,g(6)); put(im,a,14,g(5))
        if not Lf: put(im,0,a,g(9 if a not in (6,7) else 10)); put(im,1,a,g(2))
        if not R: put(im,15,a,g(6)); put(im,14,a,g(5))
    if not U and not Lf: put(im,0,0,g(10))
    if not U and not R: put(im,15,0,g(7))
    if not D and not Lf: put(im,0,15,g(7))
    if not D and not R: put(im,15,15,g(5))
def core_art(im):
    """Scheduler Core: dark console with a status display (the display itself is the emissive layer)."""
    field(im,0,0,16,16,3,321)
    for y in range(4,12):
        for x in range(3,13): put(im,x,y,H('#0E1A17') if not (y==4 or x==3) else g(1))
    for x in range(3,13): put(im,x,12,g(6))
def core_display(frames=8,formed=True):
    """Emissive status display: job bars, a thread meter and a blinking cursor (red when unformed)."""
    strip=Image.new('RGBA',(16,16*frames),(0,0,0,0)); C=MINT if formed else [H('#3A0806'),H('#8E231C'),H('#E5483C'),H('#FF9C90'),H('#FFE0DA')]
    for f in range(frames):
        P=lambda x,y,c: strip.putpixel((x,16*f+y),c+(255,))
        if formed:
            for i,(y,ln) in enumerate(((5,8),(7,5),(9,7))):
                L_=min(9,(ln+f+i*3)%10)
                for x in range(4,4+L_): P(x,y,C[3] if x==3+L_ else C[2])
            for x in range(4,12): P(x,10,C[1])
            for x in range(4,4+2+f%6): P(x,10,C[3])
            if f%4<2: P(11,5,C[4])
        else:
            for x in range(4,12): P(x,7,C[2] if (x+f)%2 else C[1])
            if f%4<2: P(7,9,C[3]); P(8,9,C[3])
    return strip
def buffer_art(im):
    """Job Buffer: memory-module racks - four vertical DIMM sticks (PCB, chips, gold contacts) in a dark bay."""
    field(im,0,0,16,16,2,331)
    PCB=[H('#0B2614'),H('#174A2C'),H('#2C7C4A')]
    for i,x0 in enumerate((3,6,9,12)):
        for y in range(3,13):
            put(im,x0,y,PCB[2] if y>3 else g(7)); put(im,x0+1,y,PCB[1])
            if y in (5,8,11): put(im,x0,y,g(1)); put(im,x0+1,y,g(2))       # chips
        put(im,x0,13,GOLD[2]); put(im,x0+1,13,GOLD[1])                   # contacts in the slot
    for x in range(2,14): put(im,x,14,g(1))
def thread_art(im):
    """Thread Unit: heatsink fins across the face with a CPU heat-spreader in the middle."""
    field(im,0,0,16,16,3,341)
    for y in range(2,14):
        for x in range(2,14):
            put(im,x,y,g(8) if y%2==0 else g(3))                          # fins
    for y in range(6,10):
        for x in range(6,10): put(im,x,y,g(7) if (x==6 or y==6) else g(5) if (x==9 or y==9) else g(6))
    put(im,7,7,g(9))
def thread_glow():
    """Active: the fin gaps glow hot near the CPU."""
    im=img()
    for y in range(3,13,2):
        for x in range(3,13):
            d=abs(x-7.5)+abs(y-7.5)
            if d<5 and not (6<=x<=9 and 6<=y<=9): put(im,x,y,HOT[3] if d<3 else HOT[2] if d<4 else HOT[1])
    return im
ART={'scheduler_core':core_art,'job_buffer':buffer_art,'thread_unit':thread_art}
def sched_tex(kind,mask):
    im=img(); ART[kind](im); sched_frame(im,mask,350+mask); return im

def fab_wash():
    """Active: warm work-light wash over the cell's back wall (10x8), strongest under the lights, translucent."""
    im=img()
    for y in range(8):
        for x in range(10):
            v=1.0-y/8.0; a=int(60+150*v)
            c=WARM[3] if y<2 else WARM[2] if y<5 else WARM[1]
            put(im,x,y,c,a if y<7 else 40)
    return im
