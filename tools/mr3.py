# Midrange line v3: guide-method textures (material ramps, stepped falloff, structural seams / louvres, no grain, no specks),
# cabinets 1 texel per model px, panels / displays 4 texels per px; models centred on their block (overhang <= 2 px, not
# collided); no rotated overlay plates (no clipping): every housing face carries its own panel.
import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
from mr2_models import el, F, GLOW, shapes, RL
from w2_tex import S, LB, OR, YE, RD, H
import mid_tex as M
A_='src/main/resources/assets/encodedlogistics/'
def ramp(*h): return [H(x) for x in h]
CREAM=ramp('#4A4234','#5E5544','#736856','#887C68','#9C907A','#AFA38C','#C1B69E','#D1C7B0','#DED6C1','#E9E3D2','#F3EFE3')
BLUE=ramp('#16263E','#1E3352','#284366','#33537C','#3F6492','#4B75A6','#5A86B6','#6C98C4','#80AAD0','#96BCDB','#AECDE5')
GREY=S
def px(im,x,y,c):
    if 0<=x<im.width and 0<=y<im.height: im.putpixel((x,y),tuple(c)+(255,))
def face(w,h,R,base=7):
    """Flat material field with stepped falloff from the top-left (4 bands, whole ramp steps) + a 1-px lit top/left edge and
       shaded bottom/right edge (the panel's own bevel)."""
    im=Image.new('RGBA',(w,h),(0,0,0,0)); span=max(1,w+h-2)
    for y in range(h):
        for x in range(w):
            t=(x+y)/span; k=1 if t<0.22 else 0 if t<0.48 else -1 if t<0.76 else -2
            px(im,x,y,R[max(0,min(10,base+k))])
    for x in range(w): px(im,x,0,R[min(10,base+2)]); px(im,x,h-1,R[max(0,base-3)])
    for y in range(h): px(im,0,y,R[min(10,base+2)]); px(im,w-1,y,R[max(0,base-3)])
    return im
def seam(im,R,x=None,y=None,base=7):
    if x is not None:
        for yy in range(1,im.height-1): px(im,x,yy,R[max(0,base-4)]); px(im,x+1,yy,R[min(10,base+1)])
    if y is not None:
        for xx in range(1,im.width-1): px(im,xx,y,R[max(0,base-4)]); px(im,xx,y+1,R[min(10,base+1)])
    return im
def louvres(im,R,x0,y0,w,n,base=7,pitch=2):
    """Louvre slots: slot step base-5 under a step-0-like shadow, lit lip base+1 below - structural, like a recess."""
    for k in range(n):
        y=y0+k*pitch
        for x in range(x0,x0+w): px(im,x,y,R[max(0,base-5)]); px(im,x,y+1,R[min(10,base+1)])
    return im
def recess(im,R,x0,y0,w,h,back=1):
    for y in range(y0,y0+h):
        for x in range(x0,x0+w): px(im,x,y,R[back] if (y-y0)<(h+1)//2 else R[min(10,back+1)])
    for x in range(x0,x0+w): px(im,x,y0,R[0]); px(im,x,y0+h-1,R[min(10,back+4)])
    for y in range(y0,y0+h-1): px(im,x0,y,R[0])
    return im
class Atlas:
    def __init__(s,W,Hh): s.W,s.H=W,Hh; s.im=Image.new('RGBA',(W,Hh),(0,0,0,0)); s.r={}; s.x=s.y=s.row=0
    def add(s,n,img):
        w,h=img.size
        if s.x+w>s.W: s.x=0; s.y+=s.row+1; s.row=0
        assert s.y+h<=s.H, n
        s.im.alpha_composite(img,(s.x,s.y)); s.r[n]=(s.x,s.y,w,h); s.x+=w+1; s.row=max(s.row,h)
    def uv(s,n,flip=False):
        x,y,w,h=s.r[n]; u=[x*16/s.W,y*16/s.H,(x+w)*16/s.W,(y+h)*16/s.H]
        return [round(v,4) for v in ([u[2],u[1],u[0],u[3]] if flip else u)]
def box(n,a,b,A,m,solid=True,tex='#t',**k):
    fs={}
    for d in ('north','south','east','west','up','down'):
        v=m.get(d,m.get('*'))
        if v is None: continue
        if isinstance(v,tuple): fs[d]=F(v[0],v[1])
        else: fs[d]=F(tex,A.uv(v,flip=d in ('south','west')))
    return el(n,a,b,fs,solid,**k)
def model(tx,E,rt='minecraft:cutout'):
    return {'parent':'minecraft:block/block','render_type':rt,'textures':{**{k:RL(v) for k,v in tx.items()},'particle':RL(list(tx.values())[0])},
            'elements':[{k:v for k,v in e.items() if k!='_solid'} for e in E]}
def save(im,p): os.makedirs(os.path.dirname(A_+p),exist_ok=True); im.save(A_+p)
def jd(o,p,root=A_): os.makedirs(os.path.dirname(root+p),exist_ok=True); json.dump(o,open(root+p,'w'),indent=1)
def mc(p,ft,w=None,h=None):
    a={'frametime':ft}
    if w: a['width']=w; a['height']=h
    open(A_+p+'.mcmeta','w').write(json.dumps({'animation':a},indent=2)+'\n')
ROT={'north':{},'east':{'y':90},'south':{'y':180},'west':{'y':270}}
SH={}
# ================= panels (4 texels / px) =================
def dark(w,h,base=1):
    im=Image.new('RGBA',(w,h),(0,0,0,0))
    for y in range(h):
        for x in range(w): px(im,x,y,S[base+1] if (x+y)<(w+h)//3 else S[base])
    for x in range(w): px(im,x,0,S[base+3]); px(im,x,h-1,S[0])
    for y in range(h): px(im,0,y,S[base+3]); px(im,w-1,y,S[0])
    return im
def ms_disk(slots):
    im=face(36,12,CREAM,8)
    recess(im,S,3,2,30,4,1)                                   # smoked window
    for k in range(slots):
        sx=4+k*15
        for x in range(sx,sx+12): px(im,x,8,S[0]); px(im,x,9,CREAM[9])
        px(im,sx+13,7,S[8]); px(im,sx+13,8,S[6]); px(im,sx+13,9,S[5])   # latch
    return im
def ms_oper():
    im=face(28,12,CREAM,8); d=dark(13,10,1); im.alpha_composite(d,(14,1))
    for i in range(4): px(im,16+i*2,3,S[0])
    for y in range(5,10):
        for x in range(16,24): px(im,x,y,S[0])
    for i in range(3): px(im,16+i*3,10,CREAM[8])
    px(im,25,9,S[8]); px(im,25,10,S[5])
    return im
def ms_glow(state,frames):
    st=Image.new('RGBA',(28,12*frames),(0,0,0,0))
    ipl=['C1','C3','C6','C9','CA','CC','--','A6']
    for f in range(frames):
        Y=12*f; L=lambda i,c: st.putpixel((16+i*2,Y+3),tuple(c)+(255,))
        if state=='ipl': (L(0,M.GRN[3]) if f%3!=2 else None); (L(1,M.AMB[2]) if f%2==0 else None); code=ipl[f%8]; col=M.AMB[2]
        elif state=='run': L(0,M.GRN[3]); code='A6'; col=M.GRN[3]
        elif state=='busy': L(0,M.GRN[3]); (L(2,M.AMB[3]) if f%2==0 else None); code='A6' if f%2 else 'b'+str(f%10); col=M.GRN[3]
        else: L(0,M.GRN[3]); L(1,M.RED[4]); code='E2' if f%2==0 else '  '; col=M.RED[3]
        for i,ch in enumerate(code[:2]): M.seg(st,16+i*4,Y+5,ch,col)
    return st
def ims_fascia():
    im=dark(52,28,1)
    for x in range(52): px(im,x,0,CREAM[9]); px(im,x,27,CREAM[5])
    recess(im,S,3,3,22,20,0)                                  # screen recess
    for r in range(2):
        for k in range(7): px(im,28+k*3,4+r*3,S[0])
    for cx in (29,36):
        for (dx,dy) in ((0,0),(1,0),(0,1),(1,1)): px(im,cx+dx,12+dy,S[7] if dy==0 else S[5])
    for y in range(10,17):
        for x in range(40,51): px(im,x,y,S[0])
    for k in range(4): px(im,28+k*5,20,CREAM[8]); px(im,29+k*5,20,CREAM[7])
    return im
def ims_glow(state,frames):
    st=Image.new('RGBA',(52,28*frames),(0,0,0,0)); ipl=['C1','C3','C6','C9','CA','CC','--','A6']
    for f in range(frames):
        Y=28*f; P=lambda x,y,c: st.putpixel((x,Y+y),tuple(c)+(255,))
        lamps=[False]*14
        if state=='ipl': lamps=[(k+f)%3==0 for k in range(14)]; code=ipl[f%8]; col=M.AMB[2]
        elif state=='run': lamps[0]=lamps[7]=True; code='A60'; col=M.RED[4]
        elif state=='busy': lamps[0]=lamps[7]=True; lamps[3]=f%2==0; code='A6'+str(f%10); col=M.RED[4]
        else: lamps[0]=lamps[1]=True; code='E2C' if f%2==0 else '   '; col=M.RED[4]
        for k,on in enumerate(lamps):
            if on: P(28+(k%7)*3,4+(k//7)*3,M.RED[4] if k==1 else M.GRN[3])
        for i,ch in enumerate((code+'   ')[:3]): M.seg(st,41+i*3+ (0 if i==0 else i-1) ,Y+11,ch,col) if False else M.seg(st,41+i*3,Y+11,ch,col)
    return st
def ims_mag():
    im=face(40,12,CREAM,8); recess(im,S,3,2,28,6,1)
    for k in range(4):
        for y in range(3,7): px(im,6+k*6,y,CREAM[10])
    px(im,34,4,S[8]); px(im,34,5,S[6]); px(im,34,6,S[5])
    return im
def lp_panel():
    im=dark(64,12,1)
    for (x,y) in ((4,3),(8,3)): px(im,x,y,M.RED[1])
    for x in range(12,22): px(im,x,6,S[0])
    for r in range(2):
        for i in range(7): px(im,26+i*3,4+r*3,BLUE[6]); px(im,27+i*3,4+r*3,BLUE[4])
    for i in range(2): px(im,52+i*4,5,BLUE[6])
    return im
def lp_glow(frames=2):
    st=Image.new('RGBA',(64,12*frames),(0,0,0,0))
    for f in range(frames):
        st.putpixel((4,12*f+3),M.RED[4]+(255,))
        if f==0: st.putpixel((8,12*f+3),M.RED[3]+(255,))
        for x in range(12,22): st.putpixel((x,12*f+6),(M.AMB[2] if (x+f)%2 else M.AMB[3])+(255,))
    return st
def window_reels(frames=4,active=True):
    """729 reel window (48 x 40, 4 texels / px): dark glass, two reels (white supply, grey take-up) with hub spokes that turn
       (4 frames), head block and vacuum columns below."""
    import math
    st=Image.new('RGBA',(48,40*frames),(0,0,0,0))
    for f in range(frames):
        im=dark(48,40,0)
        for (cx,cy,col,rr) in ((13,13,S[9],10),(35,13,S[7],10)):
            for y in range(40):
                for x in range(48):
                    d=math.hypot(x-cx,y-cy)
                    if d<=rr: px(im,x,y,col if d>rr-2 else S[max(0,S.index(col)-2)] if d>4 else S[5])
            a0=(f*math.pi/6) if active else 0
            for k in range(3):
                a=a0+k*2*math.pi/3
                for r in range(2,9): px(im,int(round(cx+r*math.cos(a))),int(round(cy+r*math.sin(a))),S[1])
            px(im,cx,cy,S[10])
        for y in range(26,34):
            for x in range(20,28): px(im,x,y,S[6] if y==26 else S[4])
        for x in (6,40):
            for y in range(25,38): px(im,x,y,S[3]); px(im,x+1,y,S[2])
        st.alpha_composite(im,(0,40*f))
    return st
def disk_window(frames=4,active=True):
    """1311 window (40 x 16): the disk pack platters stacked, a highlight that turns (4 frames)."""
    st=Image.new('RGBA',(40,16*frames),(0,0,0,0))
    for f in range(frames):
        im=dark(40,16,0)
        for k in range(5):
            y=4+k*2
            for x in range(6,34): px(im,x,y,(H('#B07A44') if (x+f*3)%11 else H('#E0B07C')) if active else H('#9C6C3C'))
            px(im,6,y,S[3]); px(im,33,y,S[3])
        for y in range(3,14): px(im,20,y,S[8])
        st.alpha_composite(im,(0,16*f))
    return st
# ================= Midrange System (18 x 16 x 12, centred: x -1..17) =================
def midrange(expansion):
    A=Atlas(64,64)
    f=face(18,12,CREAM,8); seam(f,CREAM,y=1,base=8); seam(f,CREAM,x=8,base=8); louvres(f,CREAM,2,7,14,2,8); A.add('front',f)
    s=face(12,12,CREAM,7); louvres(s,CREAM,1,2,5,4,7); seam(s,CREAM,x=7,base=7); A.add('side',s)
    r=face(18,12,CREAM,6)
    for x in (2,5,8,11,14):
        recess(r,S,x,2,2,2,1)
    louvres(r,CREAM,2,7,14,2,6); A.add('rear',r)
    A.add('top',face(18,12,CREAM,9)); A.add('h_top',face(9,11,CREAM,9)); A.add('o_top',face(7,10,CREAM,9)); A.add('h_side',face(11,3,CREAM,7))
    A.add('h_back',face(9,3,CREAM,6)); A.add('o_back',face(7,3,CREAM,6)); A.add('deck',face(2,11,CREAM,8)); A.add('dk',face(18,1,S,1)); A.add('dk_s',face(12,1,S,1))
    epo=face(4,4,RD,3); A.add('epo',epo)
    P=Atlas(128,32); P.add('disk',ms_disk(2 if expansion else 1)); P.add('oper',ms_oper())
    E=[box('body',(-1,1,4),(17,13,16),A,{'north':'front','south':'rear','east':'side','west':'side','up':'top'}),
       box('plinth',(-0.5,0,4.5),(16.5,1,15.5),A,{'north':'dk','south':'dk','east':'dk_s','west':'dk_s'}),
       box('disk_unit',(8,13,5),(17,16,16),A,{'north':('#p',P.uv('disk')),'up':'h_top','east':'h_side','west':'h_side','south':'h_back'}),
       box('deck',(6,13,6),(8,13.5,16),A,{'up':'deck','north':'dk'}),
       box('oper_unit',(-1,13,6),(6,16,16),A,{'north':('#p',P.uv('oper')),'up':'o_top','east':'h_side','west':'h_side','south':'o_back'}),
       box('epo',(17,9.5,5.5),(17.4,10.5,6.5),A,{'*':'epo'},False)]
    glow=[el('oper_glow',(-1,13,5.99),(6,16,5.99),{'north':F('#glow',[0,0,16,16])},False,**GLOW)]
    return A,P,E,glow
# ================= Expansion Cabinet (10 x 16 x 12) =================
def expansion(att):
    """att: side its Midrange is on - 'neg' (Midrange at -x: flush left, x 1..11), 'pos' (x 5..15), 'none' (x 3..13)."""
    A=Atlas(64,32); x0={'none':3,'neg':1,'pos':5}[att]; X=lambda v:x0+v
    f=face(10,12,CREAM,8); seam(f,CREAM,y=1,base=8); louvres(f,CREAM,2,7,6,2,8); recess(f,S,7,3,2,1,1); A.add('front',f)
    A.add('side',face(12,12,CREAM,7)); A.add('rear',face(10,12,CREAM,6)); A.add('top',face(10,12,CREAM,9)); A.add('cap',face(10,2,CREAM,8)); A.add('cap_s',face(11,2,CREAM,7))
    A.add('dk',face(10,1,S,1)); A.add('dk_s',face(12,1,S,1))
    E=[box('body',(X(0),1,4),(X(10),13,16),A,{'north':'front','south':'rear','east':'side','west':'side','up':'top'}),
       box('plinth',(X(0.5),0,4.5),(X(9.5),1,15.5),A,{'north':'dk','south':'dk','east':'dk_s','west':'dk_s'}),
       box('cap',(X(0),13,5),(X(10),15,16),A,{'north':'cap','up':'top','east':'cap_s','west':'cap_s','south':'cap'})]
    lamp=el('lamp',(X(7),9,3.99),(X(9),10,3.99),{'north':F('#lamp',[0,0,16,16])},False,**GLOW)
    return A,E,lamp
# ================= Integrated Midrange System (28 x 20 x 12, centred: x -6..22; footprint 3 x 2) =================
def integrated():
    A=Atlas(128,64)
    for n,b in (('front_l',8),('front_r',8)):
        f=face(14,12,CREAM,b); seam(f,CREAM,y=1,base=b); louvres(f,CREAM,1,3,12,3,b); A.add(n,f)
    A.add('side',face(12,12,CREAM,7)); r=face(28,12,CREAM,6); louvres(r,CREAM,3,7,22,2,6); A.add('rear',r)
    A.add('deck',face(28,12,CREAM,9)); A.add('hood_s',face(7,7,CREAM,7)); A.add('hood_t',face(13,7,CREAM,9)); A.add('hood_b',face(13,7,CREAM,6))
    A.add('mag_t',face(10,8,CREAM,9)); A.add('mag_s',face(8,3,CREAM,7)); A.add('mag_b',face(10,3,CREAM,6))
    A.add('kb_s',face(11,1,CREAM,7)); A.add('dk',face(28,1,S,1)); A.add('dk_s',face(12,1,S,1))
    P=Atlas(128,64); P.add('fascia',ims_fascia()); P.add('mag',ims_mag())
    E=[box('body_left',(8,1,4),(22,13,16),A,{'north':'front_l','east':'side','south':'rear','up':'deck'}),
       box('body_right',(-6,1,4),(8,13,16),A,{'north':'front_r','west':'side','south':'rear','up':'deck'}),
       box('plinth',(-5.5,0,4.5),(21.5,1,15.5),A,{'north':'dk','south':'dk','east':'dk_s','west':'dk_s'}),
       box('magazine_unit',(10,13,6),(20,16,14),A,{'north':('#p',P.uv('mag')),'up':'mag_t','east':'mag_s','west':'mag_s','south':'mag_b'}),
       box('console_hood',(-5,13,9),(8,20,16),A,{'north':('#p',P.uv('fascia')),'up':'hood_t','east':'hood_s','west':'hood_s','south':'hood_b'}),
       box('console_keyboard',(-4,13,4.5),(7,14,8.5),A,{'up':('#kb',[0,0,16,16]),'north':'kb_s','east':'kb_s','west':'kb_s','south':'kb_s'})]
    glow=[el('fascia_glow',(-5,13,8.99),(8,20,8.99),{'north':F('#glow',[0,0,16,16])},False,**GLOW)]
    # screen inside the fascia: fascia texels (3..25, 3..23) of 52 x 28 -> from the face's texture-left (x = 8) 0.75..6.25 px
    screen=[el('screen',(8-6.25,13+(28-23)/4,8.98),(8-0.75,13+(28-3)/4,8.98),{'north':F('#screen',[0,0,16,16])},False,**GLOW)]
    return A,P,E,glow,screen
# ================= Keypunch (029 style, 16 x 18 x 12) =================
def keypunch():
    A=Atlas(64,64)
    A.add('top',face(16,12,S,9)); A.add('edge',face(16,1,S,8)); A.add('edge_s',face(12,1,S,8))
    p=face(7,10,S,4); seam(p,S,y=3,base=4); A.add('ped',p); A.add('ped_s',face(9,10,S,4))
    A.add('leg',face(1,11,S,8))
    u=face(14,4,S,5); recess(u,S,1,1,12,2,1); A.add('unit',u); A.add('unit_t',face(14,7,S,6)); A.add('unit_s',face(7,4,S,5))
    h=face(4,2,S,7); A.add('hop',h); A.add('hop_t',face(4,4,CREAM,9)); A.add('kb_s',face(7,1,S,3))
    E=[box('desk',(0,11,4),(16,12,16),A,{'up':'top','north':'edge','south':'edge','east':'edge_s','west':'edge_s','down':'top'}),
       box('pedestal',(8,1,6),(15,11,15),A,{'north':'ped','south':'ped','east':'ped_s','west':'ped_s'}),
       box('leg_a',(0.5,0,4.5),(1.5,11,5.5),A,{'*':'leg'}),box('leg_b',(0.5,0,14.5),(1.5,11,15.5),A,{'*':'leg'}),
       box('leg_c',(14.5,0,4.5),(15.5,1,5.5),A,{'*':'leg'}),
       box('card_unit',(1,12,10),(15,16,16),A,{'north':'unit','up':'unit_t','east':'unit_s','west':'unit_s','south':'unit'}),
       box('hopper',(1.5,16,11),(5.5,18,15),A,{'up':'hop_t','*':'hop'}),
       box('stacker',(10.5,16,11),(14.5,17,15),A,{'up':'hop_t','*':'hop'}),
       box('keyboard',(1,12,4.5),(8,13,8.5),A,{'up':('#kb',[0,0,16,16]),'*':'kb_s'})]
    feed=el('card',(1,12,9.99),(15,16,9.99),{'north':F('#feed',[1,0,15,4])},False)
    return A,E,feed
# ================= Card Reader (2501 style, 12 x 15 x 11) =================
def card_reader():
    A=Atlas(64,64)
    d=face(12,9,BLUE,6); seam(d,BLUE,y=1,base=6); A.add('door',d); A.add('side',face(11,9,S,4))
    A.add('deck',face(14,2.5 if False else 2,S,6)); A.add('deck_s',face(12,2,S,6)); A.add('deck_t',face(14,12,S,8))
    w=face(7,9,CREAM,9); A.add('work',w)
    c=face(5,4,S,7); A.add('chute',c); A.add('chute_t',face(5,7,CREAM,10)); A.add('lamps',face(6,2,S,1))
    E=[box('cabinet',(2,0,5),(14,9,16),A,{'north':'door','east':'side','west':'side','south':'door'}),
       box('deck',(1,9,4),(15,11,16),A,{'north':'deck','south':'deck','east':'deck_s','west':'deck_s','up':'deck_t'}),
       box('chute',(2,11,7),(7,15,14),A,{'up':'chute_t','*':'chute'}),
       box('stacker',(9,11,7),(14,12,14),A,{'up':'chute_t','*':'chute'})]
    feed=el('feed',(7,11.01,9),(9,11.01,12),{'up':F('#feed',[7,6,9,10])},False)
    lamps=el('lamps',(9,9,3.99),(14,11,3.99),{'north':F('#lamps',[0,0,16,16])},False,**GLOW)
    return A,E,feed,lamps
# ================= Line Printer (20 x 17 (+paper 22) x 12, centred: x -2..18) =================
def line_printer():
    A=Atlas(64,64)
    st=face(20,9,CREAM,7); seam(st,CREAM,y=1,base=7); louvres(st,CREAM,3,5,14,2,7); A.add('stand',st); A.add('stand_s',face(12,9,CREAM,6))
    A.add('body',face(20,5,CREAM,8)); A.add('body_s',face(12,5,CREAM,7)); A.add('body_t',face(20,12,CREAM,9))
    A.add('trac',face(3,2,CREAM,10)); A.add('trac_s',face(5,2,CREAM,9)); A.add('knob',face(2,2,S,2)); A.add('rod',face(1,1,S,8)); A.add('slot',face(14,1,S,0))
    P=Atlas(64,16); P.add('panel',lp_panel())
    E=[box('stand',(-2,0,4),(18,9,16),A,{'north':'stand','south':'stand','east':'stand_s','west':'stand_s'}),
       box('body',(-2,9,4),(18,14,16),A,{'north':('#p',P.uv('panel')) ,'south':'body','east':'body_s','west':'body_s','up':'body_t'}),
       box('paper_slot',(1,14,8),(15,14.05,9),A,{'up':'slot'},False),
       box('tractor_l',(13,14,6),(16,16,11),A,{'*':'trac','east':'trac_s','west':'trac_s'}),
       box('tractor_r',(0,14,6),(3,16,11),A,{'*':'trac','east':'trac_s','west':'trac_s'}),
       box('knob',(18,10.5,9),(19,12.5,11),A,{'*':'knob'},False),
       el('paper',(3,14,8.5),(13,22,8.5),{'north':F('#paper',[0,0,16,16]),'south':F('#paper',[0,0,16,16])},False,rotation={'angle':22.5,'axis':'x','origin':[8,14,8.5]})]
    for i,x in enumerate((3,6,9.5,13)):
        E.append(box(f'basket_{i}',(x,14,13),(x+0.3,20,13.3),A,{'*':'rod'},False,rotation={'angle':22.5,'axis':'x','origin':[x,14,13]}))
    E.append(box('basket_bar',(3,19.5,15),(13.3,19.8,15.3),A,{'*':'rod'},False))
    glow=el('panel_glow',(-2,9,3.99),(18,14,3.99),{'north':F('#glow',[0,0,16,16])},False,**GLOW)
    return A,P,E,glow
# ================= Disk Drive (1311 style, 14 x 15 x 12) =================
def disk_drive():
    A=Atlas(64,64)
    d=face(12,9,BLUE,6); seam(d,BLUE,y=1,base=6); A.add('door',d); A.add('frame_s',face(12,10,S,4)); A.add('frame',face(14,1,S,3))
    u=face(14,4,CREAM,8); A.add('upper',u); A.add('upper_s',face(12,4,CREAM,7)); A.add('upper_t',face(14,12,CREAM,9))
    can=face(5,5,BLUE,7); A.add('can',can); A.add('can_s',face(5,1,BLUE,6))
    P=Atlas(64,16); p=face(28,8,CREAM,8); recess(p,S,2,2,18,4,1); px(p,4,3,M.GRN[1]); P.add('ctl',p)
    E=[box('cabinet',(1,0.5,4),(15,10,16),A,{'north':'door','east':'frame_s','west':'frame_s','south':'door'}),
       box('frame',(1,10,4),(15,11,16),A,{'*':'frame','up':'upper_t'}),
       box('upper',(1,11,4),(15,14,16),A,{'north':('#p',P.uv('ctl')),'east':'upper_s','west':'upper_s','south':'upper','up':'upper_t'}),
       box('pack_cover',(9,14,6),(14,15.5,11),A,{'up':'can','*':'can_s'}),box('pack_cover2',(9.5,15.5,6.5),(13.5,16,10.5),A,{'up':'can','*':'can_s'})]
    win=el('window',(1,11,3.99),(8,14,3.99),{'north':F('#win',[0,0,16,16])},False)
    lamp=el('lamp',(13,13,3.98),(14,13.5,3.98),{'north':F('#lamp',[0,0,16,16])},False,**GLOW)
    return A,P,E,win,lamp
# ================= Tape Drive (729 style, 12 x 28 x 12, footprint 1 x 2) =================
def tape_drive():
    A=Atlas(64,64)
    f=face(12,12,CREAM,8)
    for y in range(1,11):
        for x in range(4,8): px(f,x,y,BLUE[6] if x<6 else BLUE[5])
    seam(f,CREAM,x=3,base=8); seam(f,CREAM,x=8,base=8); A.add('lower',f)
    A.add('side',face(12,27,CREAM,7)); A.add('rear',face(12,27,CREAM,6)); A.add('top',face(12,12,CREAM,9))
    A.add('mid',face(12,1,CREAM,9)); A.add('ctl_b',face(12,3,CREAM,9))
    P=Atlas(64,16); c=dark(48,10,1)
    for i in range(6): px(c,14+i*5,3,H('#E8C24A') if i==0 else M.RED[1] if i in (2,3) else S[6]); px(c,14+i*5,6,S[6])
    for y in range(2,8):
        for x in range(3,9): px(c,x,y,S[9])
    P.add('ctl',c)
    E=[box('cabinet',(2,0.5,4),(14,28,16),A,{'north':'lower','east':'side','west':'side','south':'rear','up':'top'})]
    win=el('reels',(2,14,3.98),(14,24,3.98),{'north':F('#reels',[0,0,16,16])},False)
    ctl=el('control',(2,25,3.98),(14,27.5,3.98),{'north':F('#p',P.uv('ctl'))},False)
    glow=el('lamps',(2,25,3.97),(14,27.5,3.97),{'north':F('#glow',[0,0,16,16])},False,**GLOW)
    return A,P,E,win,ctl,glow
