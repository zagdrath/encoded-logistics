# Terminal Desk + Swivel Chair textures. World-scale parts follow the vanilla method used for the rack exterior:
# 1 texel per model px, a small palette per material, brushed runs of 3-7 px one tone up/down, light stepping in bands,
# crisp bevels - no speckle, no blended gradients. Only the CRT screen and the keyboard use a finer density.
import random
from PIL import Image
from el_style import H, put
def pal(*h): return [H(c) for c in h]
STEEL=pal('#2C3036','#3A3F46','#4A5058','#5C636C','#717881','#878E97','#9EA5AD','#B6BCC3')      # desk frame, grey enamel
LAM=pal('#6E5536','#80653F','#94784C','#A88B5A','#B99C68','#C9AD7A','#D8BE8E')               # laminate (light woodgrain)
EDGE=pal('#3A2E22','#4A3B2B','#5A4934','#6A573E')                                            # laminate edge band
CASE=pal('#8E8A7C','#A29E8F','#B5B1A2','#C6C2B3','#D4D1C3','#E1DED2','#ECEADF')              # CRT off-white plastic
DARK=pal('#15171A','#1C1F23','#24282D','#2D3238','#373D44')                                  # bezel, screen surround
KEY=pal('#5E5B54','#77736A','#8F8B81','#A6A297','#BDB9AE','#D2CFC4')                          # keycaps (warm grey)
TWEED=pal('#3A2A1E','#4A3626','#5A4330','#6B513A','#7C6046','#8E7054')                        # chair fabric (brown tweed)
GLASS=pal('#0B100D','#101712','#152019','#1C2A21')                                           # CRT glass (off)
def new(w,h): return Image.new('RGBA',(w,h),(0,0,0,0))
def c(p,i): return p[max(0,min(len(p)-1,i))]
def vfill(im,x0,y0,w,h,p,base,seed,axis='h',bands=True,runs=(3,7),w_=(0.2,0.62,0.18)):
    r=random.Random(seed); A,B=(h,w) if axis=='h' else (w,h)
    for a in range(A):
        band=0
        if bands:
            pos=a/max(1,A-1); band=1 if pos<0.2 else (-1 if pos>0.8 else 0)
        b=0
        while b<B:
            n=r.randint(*runs); o=r.choices((-1,0,1),w_)[0]
            for k in range(b,min(B,b+n)):
                x,y=(x0+k,y0+a) if axis=='h' else (x0+a,y0+k); put(im,x,y,c(p,base+band+o))
            b+=n
def rim(im,x0,y0,w,h,p,lit,dark):
    for a in range(w): put(im,x0+a,y0,c(p,lit)); put(im,x0+a,y0+h-1,c(p,dark))
    for a in range(h): put(im,x0,y0+a,c(p,lit)); put(im,x0+w-1,y0+a,c(p,dark))
# ---------------- desk ----------------
def laminate():
    """32x32: top surface (32 x 16, uv rows 0-15) with a long horizontal grain, then the edge band (rows 16-17),
       then the laminate's underside (rows 18-31)."""
    im=new(32,32); vfill(im,0,0,32,16,LAM,3,801,runs=(4,9),w_=(0.22,0.58,0.20))
    for y in (3,9,13):                                                   # a few longer grain lines
        r=random.Random(y)
        for x in range(r.randint(0,4),32,1):
            if r.random()<0.8: put(im,x,y,c(LAM,2))
    for x in range(32): put(im,x,0,c(LAM,5))
    vfill(im,0,16,32,2,EDGE,2,802,bands=False)
    for x in range(32): put(im,x,16,c(EDGE,3))
    vfill(im,0,18,32,14,LAM,1,803,bands=False)
    return im
def steel(seed=811):
    im=new(16,16); vfill(im,0,0,16,16,STEEL,4,seed,axis='v'); rim(im,0,0,16,16,STEEL,6,2); return im
def pedestal():
    """16x16 drawer pedestal front (11 x 12 px used): three drawers with recessed pulls; the top drawer is the 9-slot
       output drawer (a small label card)."""
    im=new(16,16); vfill(im,0,0,16,16,STEEL,4,821); rim(im,0,0,11,12,STEEL,6,2)
    for i,y0 in enumerate((1,5,9)):
        h=3 if i<2 else 2
        rim(im,1,y0,9,h,STEEL,5,1)
        for x in range(3,8): put(im,x,y0+1,c(STEEL,0)); put(im,x,y0+2 if h>2 else y0+1,c(STEEL,6)) if h>2 else None
    for x in range(7,10): put(im,x,2,H('#E8E4D4'))                       # label card on the output drawer
    return im
def modesty():
    im=new(16,16); vfill(im,0,0,16,16,STEEL,3,831); rim(im,0,0,16,16,STEEL,5,1)
    for y in (4,8,12):
        for x in range(2,14): put(im,x,y,c(STEEL,2))
    return im
# ---------------- CRT terminal ----------------
def crt_case():
    """32x32 at 1 texel/px: front bezel (0,0)-(12,10), side (12,0)-(18,10) [6 deep], top (0,10)-(12,16),
       rear taper side (18,0)-(23,8), rear taper back (0,16)-(9,24), rear taper top (12,10)-(21,15), base trim (24,0)-(32,2)."""
    im=new(32,32)
    vfill(im,0,0,12,10,CASE,3,841)                                       # front: chunky moulded bezel
    rim(im,0,0,12,10,CASE,5,1)
    for y in range(1,8):                                                # screen recess (dark surround, inset)
        for x in range(1,11): put(im,x,y,c(DARK,2))
    for x in range(1,11): put(im,x,1,c(DARK,0)); put(im,x,8,c(CASE,5))
    for y in range(1,8): put(im,1,y,c(DARK,0)); put(im,10,y,c(CASE,4))
    for x in range(2,10): put(im,x,9,c(CASE,2))                          # chin
    put(im,9,9,c(CASE,0)); put(im,10,9,c(CASE,1))                        # power switch rocker
    vfill(im,12,0,6,10,CASE,2,842); rim(im,12,0,6,10,CASE,4,0)
    for y in (2,4,6): 
        for x in range(13,17): put(im,x,y,c(CASE,0))                     # side vents
    vfill(im,0,10,12,6,CASE,4,843,bands=False)
    for x in range(2,10,2): put(im,x,14,c(CASE,1))                       # top vents
    vfill(im,18,0,5,8,CASE,2,844); rim(im,18,0,5,8,CASE,3,0)
    vfill(im,0,16,9,8,CASE,1,845); rim(im,0,16,9,8,CASE,3,0)
    for y in range(18,23,2):
        for x in range(2,7): put(im,x,y,c(CASE,0))
    vfill(im,12,10,9,5,CASE,3,846,bands=False)
    vfill(im,24,0,8,2,DARK,3,847,bands=False)
    return im
def crt_screen(state,frames=1):
    """CRT glass + content at 4 texels/px: 36 x 28 frames (the glass is 9 x 7 px). Curved-glass shading in the corners.
       off: dark glass, a faint reflection; boot: 12 frames of text scrolling up; on: menu text + blinking cursor (2)."""
    W,Hh=36,28; st=Image.new('RGBA',(W,Hh*frames),(0,0,0,0)); G=pal('#0B3A1C','#127A3A','#2EC060','#7CFFA0','#C8FFD8')
    def base(f):
        for y in range(Hh):
            for x in range(W):
                cx=abs(x-17.5)/18; cy=abs(y-13.5)/14; corner=max(0,cx*cx+cy*cy-0.55)
                st.putpixel((x,f*Hh+y),c(GLASS,1 if corner<0.25 else 0)+(255,))
        for (x,y) in ((4,3),(5,3),(6,3),(4,4)): st.putpixel((x,f*Hh+y),c(GLASS,3)+(255,))   # reflection glint
    lines=['ab cd efghi','jkl mnop q','rstu vw xy','zz abcd','ef ghijklm','nopq rs','tu vwxy z','abc de','fghi jkl','mn opqrs','tuv wx','yz ab']
    import random as R
    rr=R.Random(4)
    widths=[rr.randint(10,30) for _ in range(40)]
    for f in range(frames):
        if state=='off': base(f); continue
        for y in range(Hh):
            for x in range(W): st.putpixel((x,f*Hh+y),(5,14,8,255))
        def text_row(y,w,col,ox=3):
            for x in range(ox,ox+w):
                if (x*7+y*3)%5!=0 and x<W-3: st.putpixel((x,f*Hh+y),col+(255,))
        if state=='boot':
            off=f*2
            for i in range(10):
                y=Hh-4-i*2-(off%2)
                k=(i+f)%len(widths)
                if 2<=y<Hh-2: text_row(y,widths[k],G[2] if i>0 else G[3])
            if f<2:                                                     # degauss bloom at power-on
                for y in range(Hh):
                    for x in range(W):
                        cx=abs(x-17.5)/18; cy=abs(y-13.5)/14
                        if cx*cx+cy*cy<0.35-0.15*f: st.putpixel((x,f*Hh+y),G[1 if f else 2]+(255,))
        else:
            text_row(2,14,G[3],11)                                     # title
            for i,w in enumerate((18,16,20,14,12)): text_row(6+i*3,w,G[2],5)
            text_row(Hh-6,8,G[2],3)
            if f==0:
                for x in range(12,14): st.putpixel((x,f*Hh+Hh-6),G[4]+(255,))   # blinking cursor
    return st
def keyboard():
    """32 x 12 at 3 texels/px (keyboard top 10 x 4 px): warm-grey case, function-key row, 4 key rows, space bar."""
    im=new(32,12); vfill(im,0,0,32,12,KEY,1,851,bands=False)
    rim(im,0,0,32,12,KEY,3,0)
    for x in range(2,30,3):                                             # function-key row (darker caps)
        put(im,x,1,c(KEY,3)); put(im,x+1,1,c(KEY,2))
    for r in range(4):
        for x in range(2+(r%2),27,2):
            put(im,x,3+r*2,c(KEY,5) if r<3 else c(KEY,4)); put(im,x,4+r*2,c(KEY,2))
    for x in range(8,22): put(im,x,10,c(KEY,4))                          # space bar
    for x in range(27,30):                                              # numeric block
        for y in (3,5,7): put(im,x,y,c(KEY,4))
    return im
def lamp(on):
    im=new(4,4)
    for y in range(4):
        for x in range(4): put(im,x,y,(H('#7CFFA0') if (x,y)==(1,1) else H('#2EC060')) if on else H('#1E3A26'))
    return im
# ---------------- clutter ----------------
def clutter():
    """32x32: mug (0,0)-(8,10) side, (8,0)-(14,6) top with coffee; green-bar printout stack (0,12)-(16,20) top,
       (16,12)-(32,14) edge; clipboard (0,22)-(14,32) top with paper and clip."""
    im=new(32,32); MUG=pal('#8C2E28','#A8392F','#C44A3A','#D86A56')
    for y in range(10):
        for x in range(8): put(im,x,y,c(MUG,3 if x<2 else 2 if x<5 else 1 if x<7 else 0))
    for y in range(6):
        for x in range(8,14): put(im,x,y,H('#3A2416') if 1<=x-8<=4 and 1<=y<=4 else c(MUG,3))
    for y in range(12,20):                                              # green-bar paper top: alternating bands
        for x in range(16): put(im,x,y,H('#E8EEDF') if ((y-12)//2)%2==0 else H('#C8DDB8'))
        put(im,0,y,H('#BFC8B6')); put(im,15,y,H('#BFC8B6'))
    for x in range(1,15,3): put(im,x,13,H('#9AA894'))                    # tractor holes
    for y in range(12,14):
        for x in range(16,32): put(im,x,y,H('#E8EEDF') if (x+y)%2 else H('#D4DACB'))
    for y in range(22,32):                                              # clipboard
        for x in range(14): put(im,x,y,H('#8A6A42') if x in (0,13) or y in (22,31) else H('#9E7C4E'))
    for y in range(24,31):
        for x in range(2,12): put(im,x,y,H('#F2F2EA') if (y-24)%2==0 else H('#E2E2D8'))
    for x in range(4,10): put(im,x,22,STEEL[6]); put(im,x,23,STEEL[4])
    return im
# ---------------- chair ----------------
def chair_fabric():
    im=new(16,16); vfill(im,0,0,16,16,TWEED,3,861,runs=(2,3),w_=(0.3,0.4,0.3))      # tweed: short runs = woven read
    for y in range(0,16,2):
        for x in range((y//2)%2,16,2): put(im,x,y,c(TWEED,4))
    rim(im,0,0,16,16,TWEED,5,1); return im
def chair_metal():
    im=new(16,16); vfill(im,0,0,16,16,DARK,2,871,axis='v'); rim(im,0,0,16,16,DARK,4,0); return im
def chair_caster():
    im=new(16,16); vfill(im,0,0,16,16,DARK,1,881,bands=False)
    for x in range(16): put(im,x,0,c(DARK,3))
    return im
# ---------------- icons ----------------
def _out(im,col):
    src=im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x,y))[3]: continue
            if any(0<=x+dx<16 and 0<=y+dy<16 and src.getpixel((x+dx,y+dy))[3] for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))): put(im,x,y,col)
def desk_icon():
    im=new(16,16)
    for x in range(1,15): put(im,x,9,c(LAM,5)); put(im,x,10,c(EDGE,3))   # desk top
    for y in range(11,15): put(im,2,y,c(STEEL,5)); put(im,13,y,c(STEEL,4)); put(im,12,y,c(STEEL,3)); put(im,11,y,c(STEEL,4))
    for y in range(2,9):                                                # CRT
        for x in range(3,10): put(im,x,y,c(CASE,4 if y<4 else 3))
    for y in range(3,7):
        for x in range(4,8): put(im,x,y,H('#2EC060') if (y==4 and x<7) else H('#0B3A1C'))
    for y in range(3,8): put(im,10,y,c(CASE,2))
    for x in range(5,10): put(im,x,8,c(KEY,4))
    _out(im,H('#1A1A18')); return im
def chair_icon():
    im=new(16,16)
    for y in range(2,8):
        for x in range(5,11): put(im,x,y,c(TWEED,4 if y<4 else 3))
    for x in range(4,12): put(im,x,8,c(TWEED,4)); put(im,x,9,c(TWEED,2))
    for y in range(10,13): put(im,7,y,STEEL[5]); put(im,8,y,STEEL[3])
    for (x,y) in ((3,13),(5,14),(8,14),(10,14),(12,13)): put(im,x,y,c(DARK,3))
    for x in range(4,12): put(im,x,13,c(DARK,2))
    _out(im,H('#1A1410')); return im
