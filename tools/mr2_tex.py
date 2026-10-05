# Midrange line at true scale (1 px = 6.25 cm). Cabinets: vanilla method, 1 texel per model px, each face drawn at its
# exact size and packed into a per-block atlas (Atlas) so model UVs are pixel-exact. Panels / fascias: 4 texels per px.
from PIL import Image
from el_style import H, put
from desk_tex import vfill, rim, c, pal, DARK, STEEL, KEY
import mid_tex as M
CREAM=pal('#8E887A','#A39D8E','#B6B0A0','#C7C1B1','#D5D0C1','#E1DCCF','#EBE7DC')     # period off-white (photos)
SEG=M.SEG; seg=M.seg; GRN=M.GRN; RED=M.RED; AMB=M.AMB
class Atlas:
    """Shelf packer: add(name, image) -> (x, y, w, h); uv(name) -> [u0, v0, u1, v1] in 0..16 for the sheet size."""
    def __init__(s,W,Hh): s.W,s.H=W,Hh; s.im=Image.new('RGBA',(W,Hh),(0,0,0,0)); s.r={}; s.x=s.y=s.row=0
    def add(s,n,img):
        w,h=img.size
        if s.x+w>s.W: s.x=0; s.y+=s.row+1; s.row=0
        assert s.y+h<=s.H, f'atlas full at {n}'
        s.im.alpha_composite(img,(s.x,s.y)); s.r[n]=(s.x,s.y,w,h); s.x+=w+1; s.row=max(s.row,h); return s.r[n]
    def uv(s,n,flip=False):
        x,y,w,h=s.r[n]; u=[x*16/s.W,y*16/s.H,(x+w)*16/s.W,(y+h)*16/s.H]
        return [round(v,4) for v in ([u[2],u[1],u[0],u[3]] if flip else u)]
def face(w,h,p=CREAM,base=4,seed=1,axis='h',bands=True):
    im=Image.new('RGBA',(w,h),(0,0,0,0)); vfill(im,0,0,w,h,p,base,seed,axis=axis,bands=bands); return im
def edge(im,p=CREAM,lit=6,dark=2):
    w,h=im.size
    for x in range(w): put(im,x,0,c(p,lit)); put(im,x,h-1,c(p,dark))
    for y in range(h): put(im,0,y,c(p,lit-1)); put(im,w-1,y,c(p,dark))
    return im
def vents(im,x0,y0,w,n,p=CREAM,pitch=2,vertical=False):
    """Louvre slots: dark slot row with a lit lip below (light from above)."""
    for k in range(n):
        if vertical:
            x=x0+k*pitch
            for y in range(y0,y0+w): put(im,x,y,c(p,0)); put(im,x+1,y,c(p,5))
        else:
            y=y0+k*pitch
            for x in range(x0,x0+w): put(im,x,y,c(p,0)); put(im,x,y+1,c(p,5))
def seam(im,x=None,y=None,p=CREAM):
    w,h=im.size
    if x is not None:
        for yy in range(h): put(im,x,yy,c(p,1)); put(im,x+1,yy,c(p,5)) if x+1<w else None
    if y is not None:
        for xx in range(w): put(im,xx,y,c(p,1)); put(im,xx,y+1,c(p,5)) if y+1<h else None
    return im
# ---------------- Midrange System (18 x 16 x 12) ----------------
def ms_atlas(two_slots=False):
    A=Atlas(64,64)
    f=edge(face(18,12,seed=11)); seam(f,y=1); seam(f,x=8); vents(f,2,8,14,2); A.add('front',f)               # front: top seam, cover seam, bottom louvres
    s=edge(face(12,12,seed=12,axis='h')); vents(s,1,2,5,4,vertical=False); seam(s,x=7); A.add('side',s)    # side: front-top vents (photo), service cover seam
    r=edge(face(18,12,base=3,seed=13))
    for (x,y) in ((2,2),(5,2),(8,2),(11,2),(14,2)):                                                       # rear I/O: terminal connectors
        for dy in range(2):
            for dx in range(2): put(r,x+dx,y+dy,c(DARK,1))
    vents(r,2,7,14,2); A.add('rear',r)
    A.add('top',edge(face(18,12,base=5,seed=14,axis='v',bands=False),lit=6,dark=3))
    A.add('house_l_top',edge(face(9,11,base=5,seed=15,bands=False))); A.add('house_r_top',edge(face(7,10,base=5,seed=16,bands=False)))
    A.add('house_side',edge(face(11,3,base=3,seed=17,bands=False))); A.add('house_back',edge(face(9,3,base=3,seed=18,bands=False)))
    A.add('deck',face(2,11,base=4,seed=19,bands=False))
    pl=Image.new('RGBA',(18,1),c(DARK,1)+(255,)); A.add('plinth',pl); A.add('plinth_side',Image.new('RGBA',(12,1),c(DARK,1)+(255,)))
    epo=Image.new('RGBA',(4,4),(0,0,0,0))
    for y in range(4):
        for x in range(4): put(epo,x,y,RED[3] if (x,y) not in ((3,3),(3,2),(2,3)) else RED[1])
    put(epo,0,0,RED[4]); A.add('epo',epo)
    return A
def ms_panels():
    """128 x 64, 4 texels/px: diskette plate (36 x 13: smoked window + 1 or 2 slots with latches), operator plate
       (28 x 13: cream left, dark strip right with lamps, 4-digit display, buttons, keyswitch)."""
    P=Atlas(128,64)
    for n,slots in (('disk1',1),('disk2',2)):
        im=edge(face(36,13,base=5,seed=21,bands=False),lit=6,dark=2)
        for y in range(2,7):
            for x in range(4,32): put(im,x,y,c(DARK,1) if y in (2,6) else c(DARK,2))      # smoked window
        for k in range(slots):
            sx=4+k*15
            for x in range(sx,sx+12): put(im,x,9,c(DARK,0)); put(im,x,10,c(CREAM,6))       # 8" slot
            for dy in range(3): put(im,sx+13,8+dy,c(STEEL,6) if dy==0 else c(STEEL,3))     # latch
        P.add(n,im)
    op=edge(face(28,13,base=5,seed=22,bands=False),lit=6,dark=2)
    for y in range(1,12):
        for x in range(15,27): put(op,x,y,c(DARK,1) if x in (15,26) or y in (1,11) else c(DARK,2))
    for i in range(4): put(op,17+i*2,3,c(DARK,0))                      # lamps: power, attention, activity, I/O
    for y in range(5,10):
        for x in range(17,25): put(op,x,y,c(DARK,0))                    # 4-digit display (2 wide digits at 4tex/px)
    for i in range(3): put(op,17+i*3,10,c(CREAM,5))                      # function buttons
    put(op,25,9,c(STEEL,6)); put(op,25,10,c(STEEL,3))                    # keyswitch
    P.add('oper',op)
    return P
SMALL={'A':['##','##'],'0':['##','##']}
def ms_glow(state,frames):
    """28 x 13 per frame, same layout as 'oper': lamps at (17+2i, 3), display (17-24, 5-9): 4 digits 2 px wide."""
    st=Image.new('RGBA',(28,13*frames),(0,0,0,0)); ipl=['C1','C3','C6','C9','CA','CC','--','A6']
    for f in range(frames):
        Y=13*f; L=lambda i,col: st.putpixel((17+i*2,Y+3),col+(255,))
        if state=='ipl': (L(0,GRN[3]) if f%3!=2 else None); (L(1,AMB[2]) if f%2==0 else None); code=ipl[f%len(ipl)]; col=AMB[2]
        elif state=='run': L(0,GRN[3]); code='A600'; col=GRN[3]
        elif state=='busy': L(0,GRN[3]); (L(2,AMB[3]) if f%2==0 else None); code='A6'+'b'+str(f%10); col=GRN[3]
        else: L(0,GRN[3]); L(1,RED[4]); code='E2C4' if f%2==0 else '    '; col=RED[3]
        # the display shows the first two characters of the code (3x5 digits, 1 gap): A6, C3, E2 ...
        for i,ch in enumerate((code+'  ')[:2]): M.seg(st,17+i*4,Y+5,ch,col)
    return st
# ---------------- Expansion Cabinet (10 x 16 x 12) ----------------
def ec_atlas():
    A=Atlas(64,32)
    f=edge(face(10,12,seed=31)); seam(f,y=1); vents(f,2,8,6,2)
    for (x,y) in ((7,3),(8,3)): put(f,x,y,c(DARK,1))                                 # connected lamp socket
    A.add('front',f); A.add('side',edge(face(12,12,seed=32))); A.add('rear',edge(face(10,12,base=3,seed=33)))
    A.add('top',edge(face(10,12,base=5,seed=34,axis='v',bands=False))); A.add('cap_front',edge(face(10,3,base=5,seed=35,bands=False)))
    A.add('cap_side',edge(face(12,3,base=4,seed=36,bands=False))); A.add('plinth',Image.new('RGBA',(10,1),c(DARK,1)+(255,)))
    A.add('plinth_side',Image.new('RGBA',(12,1),c(DARK,1)+(255,)))
    return A
def ec_lamp():
    im=Image.new('RGBA',(64,32),(0,0,0,0)); return im
# ---------------- Integrated Midrange System (28 x 20 x 12) ----------------
def ims_atlas():
    A=Atlas(128,64)
    for n,seed in (('front_l',41),('front_r',42)):
        f=edge(face(14,12,seed=seed)); seam(f,y=1)
        vents(f,1,3,12,3)                                                               # vent band near the top (photo)
        A.add(n,f)
    A.add('side',edge(face(12,12,seed=43))); r=edge(face(28,12,base=3,seed=44))
    for x in range(3,25,3):
        for dy in range(2):
            for dx in range(2): put(r,x+dx,2+dy,c(DARK,1))
    vents(r,3,7,22,2); A.add('rear',r)
    A.add('deck',edge(face(28,12,base=5,seed=45,axis='v',bands=False),lit=6,dark=3))
    A.add('hood_side',edge(face(7,7,base=4,seed=46,bands=False))); A.add('hood_top',edge(face(14,7,base=5,seed=47,bands=False)))
    A.add('hood_back',edge(face(14,7,base=3,seed=48,bands=False)))
    A.add('mag_top',edge(face(10,8,base=5,seed=49,bands=False))); A.add('mag_side',edge(face(8,3,base=4,seed=50,bands=False)))
    A.add('mag_back',edge(face(10,3,base=3,seed=51,bands=False)))
    tr=Image.new('RGBA',(11,4),(0,0,0,0)); vfill(tr,0,0,11,4,CREAM,3,52,bands=False); A.add('tray',edge(tr,lit=2,dark=5))   # recessed tray
    A.add('plinth',Image.new('RGBA',(28,1),c(DARK,1)+(255,))); A.add('plinth_side',Image.new('RGBA',(12,1),c(DARK,1)+(255,)))
    return A
def ims_panels():
    """128 x 64, 4 texels/px: fascia (56 x 24: dark; recessed screen at left, lamp matrix, two rotary selectors,
       buttons, 4-digit display) and the magazine plate (40 x 10: smoked slot window, magazine visible, latch)."""
    P=Atlas(128,64)
    fa=Image.new('RGBA',(56,24),(0,0,0,0))
    for y in range(24):
        for x in range(56): put(fa,x,y,c(CREAM,5) if (x<2 or x>53 or y<2 or y>21) else c(DARK,2))
    for x in range(56): put(fa,x,0,c(CREAM,6)); put(fa,x,23,c(CREAM,2))
    for y in range(4,20):                                                                   # recessed screen (glass, off)
        for x in range(4,24): put(fa,x,y,c(DARK,0) if x in (4,23) or y in (4,19) else H('#0A100C'))
    for r_ in range(2):
        for k in range(8): put(fa,27+k*3,4+r_*3,c(DARK,0))                                   # lamp matrix
    for cx in (29,36):                                                                       # rotary selectors
        for (dx,dy) in ((0,0),(1,0),(0,1),(1,1),(-1,0),(2,1),(0,-1),(1,2)): put(fa,cx+dx,12+dy,c(STEEL,5) if dy<1 else c(STEEL,3))
    for k in range(4): put(fa,27+k*5,18,c(CREAM,5)); put(fa,28+k*5,18,c(CREAM,4))           # buttons
    for y in range(10,17):
        for x in range(40,53): put(fa,x,y,c(DARK,0))                                         # 3-digit display
    P.add('fascia',fa)
    mg=edge(face(40,10,base=5,seed=53,bands=False),lit=6,dark=2)
    for y in range(2,7):
        for x in range(4,30): put(mg,x,y,c(DARK,0) if y in (2,6) else H('#2A2016'))
    for k in range(4):
        for y in range(3,6): put(mg,6+k*6,y,H('#E8E4D4'))                                  # magazine's diskettes seen edge-on
    for dy in range(4): put(mg,34,3+dy,c(STEEL,6) if dy==0 else c(STEEL,3))
    P.add('magazine',mg)
    return P
def ims_glow(state,frames):
    st=Image.new('RGBA',(56,24*frames),(0,0,0,0)); ipl=['C1','C3','C6','C9','CA','CC','--','A6']
    for f in range(frames):
        Y=24*f; P=lambda x,y,col: st.putpixel((x,Y+y),col+(255,))
        lamps=[False]*16
        if state=='ipl': lamps=[(k+f)%3==0 for k in range(16)]; code=ipl[f%8]; col=AMB[2]
        elif state=='run': lamps[0]=lamps[8]=True; code='A600'; col=RED[4]
        elif state=='busy': lamps[0]=lamps[8]=True; lamps[3]=f%2==0; code='A6b'+str(f%10); col=RED[4]
        else: lamps[0]=True; lamps[1]=True; code='E2C4' if f%2==0 else '    '; col=RED[4]
        for k,on in enumerate(lamps):
            if on: P(27+(k%8)*3,4+(k//8)*3,(RED[4] if k in (1,) else GRN[3]))
        for i,ch in enumerate((code+'   ')[:3]): M.seg(st,41+i*4,Y+11,ch,col)
    return st
