# Phase 1 item art - follows docs/TEXTURE_STYLE.md (outline = darkest step of the item's own material,
# 4-7 tones per material, light from the top-left, one-step grain on faces bigger than ~4x4).
import random, colorsys
from el_style import *
from PIL import Image
import arc_based
# tier scheme (TEXTURE_STYLE 2.3 storage tiers, now named by capacity)
TIERS=[('8k','8K','Ember',['#7A3212','#B8521F','#F07A3C','#FFB48A']),
       ('32k','32K','Amber',['#7A5C08','#B88E14','#F0C030','#FFE08A']),
       ('128k','128K','Verdant',['#00663F','#00A06B','#00D992','#B5FFE3']),
       ('512k','512K','Azure',['#164A80','#2474C2','#3FA3F5','#A8D8FF']),
       ('2m','2M','Violet',['#4C2685','#7A44C8','#A66BF5','#DDC4FF'])]
CYAN=[H(c) for c in ['#0E5560','#168A96','#2FD0D8','#A8F4F7']]          # Logic Die accent
TIER_RGB=[[H(c) for c in t[3]] for t in TIERS]                             # deep, shade, base, light
# 3x5 pixel font (proportional: '1' is 1 px wide, 'M' 4 px) for printed capacities
FONT={'0':['###','#.#','#.#','#.#','###'],'1':['#','#','#','#','#'],'2':['###','..#','###','#..','###'],
      '3':['###','..#','.##','..#','###'],'4':['#.#','#.#','###','..#','..#'],'5':['###','#..','###','..#','###'],
      '6':['###','#..','###','#.#','###'],'7':['###','..#','..#','..#','..#'],'8':['###','#.#','###','#.#','###'],
      '9':['###','#.#','###','..#','###'],'K':['#.#','#.#','##.','#.#','#.#'],'M':['#...#','##.##','#.#.#','#...#','#...#']}
def text_w(s): return sum(len(FONT[c][0]) for c in s)+len(s)-1
def draw_text(im,x,y,s,col):
    for ch in s:
        g=FONT[ch]
        for r,row in enumerate(g):
            for c,v in enumerate(row):
                if v=='#': put(im,x+c,y+r,col)
        x+=len(g[0])+1
def outline(im,col):
    """1 px outline round every opaque pixel (4-neighbour), in the given (dark) colour."""
    src=im.copy(); w,h=im.size
    for y in range(h):
        for x in range(w):
            if src.getpixel((x,y))[3]: continue
            if any(0<=x+dx<w and 0<=y+dy<h and src.getpixel((x+dx,y+dy))[3] for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))):
                put(im,x,y,col)
def mul(c,k): return tuple(max(0,min(255,int(v*k))) for v in c)

def mc_grain(im,seed,p=0.12,keep=()):
    """Vanilla-style one-step grain: interior pixels (all four neighbours the same colour) nudge ~8% darker
       (mostly) or lighter. Edges, outlines, 1 px details and any colour in `keep` stay crisp."""
    r=random.Random(seed); src=im.copy(); w,h=im.size
    for y in range(1,h-1):
        for x in range(1,w-1):
            c=src.getpixel((x,y))
            if not c[3] or c[:3] in keep: continue
            if all(src.getpixel((x+dx,y+dy))==c for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))) and r.random()<p:
                k=0.9 if r.random()<0.7 else 1.1
                put(im,x,y,mul(c[:3],k),c[3])
    return im
# ---------------- materials ----------------
def silica():
    """Fine white/grey powder heap: clustered grain, lit top-left, darker base."""
    im=img(); r=random.Random(3)
    W=[H('#5E6168'),H('#8E9299'),H('#B9BCC2'),H('#DADCE0'),H('#F2F3F5')]
    for y in range(5,14):
        half=min(6,int((y-4)*0.8)+2)
        for x in range(8-half,8+half):
            t=3 if y<8 else 2 if y<11 else 1
            if x<8-half+2: t=min(4,t+1)
            if x>8+half-3: t=max(0,t-1)
            t+=r.choice((-1,0,0,0,1)); put(im,x,y,W[max(0,min(4,t))])
    for (x,y) in ((3,13),(12,13),(2,12),(13,12)): put(im,x,y,W[1])
    outline(im,W[0]); return im
def boule():
    return mc_grain(_boule(),31,0.12)
def _boule():
    """Grown silicon ingot: horizontal-ish cylinder with a seed cone, dark blue-grey metal with a bright sheen line."""
    im=img(); S=[H('#1C2129'),H('#2C333E'),H('#3E4755'),H('#56606F'),H('#7C8796'),H('#BCC6D2')]
    for x in range(3,15):
        for y in range(4,12):
            if x==14 and y in (4,11): continue
            band=y-4
            t=[3,5,4,3,2,2,1,1][band]
            if (x+y)%9==0: t=max(1,t-1)
            put(im,x,y,S[t])
    for y in range(5,11): put(im,2,y,S[2] if y<8 else S[1])          # tapered seed end
    for y in range(6,10): put(im,1,y,S[2] if y<8 else S[1])
    for y in range(4,12): put(im,14,y,S[3] if y<8 else S[2])         # flat cut end face
    for y in range(5,11): put(im,15,y,S[1])
    outline(im,S[0]); return im
def wafer():
    """Thin round wafer, full circle: stepped polished silver lit from the top-left, darker rim, a soft diagonal
       rainbow sheen (thin-film interference), a small glint, light grain."""
    im=img(); cx,cy,R=7.5,7.5,6.6
    SIL=[H('#34393F'),H('#5E6670'),H('#848D98'),H('#A7AFB9'),H('#C6CDD5'),H('#E2E7EC')]
    rain=[H('#E9A3C9'),H('#EFD099'),H('#AEE2A4'),H('#9ECFEA'),H('#BCA9EC')]
    SPAN={1:(5,10),2:(3,12),3:(2,13),12:(2,13),13:(3,12),14:(5,10)}     # hand-tuned 14 px circle (vanilla-style)
    for y in range(1,15):
        x0,x1=SPAN.get(y,(1,14))
        for x in range(x0,x1+1):
            d=((x-cx)**2+(y-cy)**2)**0.5
            edge=(x in (x0,x1)) or y in (1,14) or not (SPAN.get(y-1,(1,14))[0]<=x<=SPAN.get(y-1,(1,14))[1] if y>1 else False) \
                 or not (SPAN.get(y+1,(1,14))[0]<=x<=SPAN.get(y+1,(1,14))[1] if y<14 else False)
            k=(x+y)/2-7.5                                   # diagonal light: negative = toward the top-left
            t=4 if k<-3 else 3 if k<0 else 2 if k<3 else 1
            if edge: t=max(1,t-1)                           # rim falls off
            c=SIL[t]
            band=x-y                                        # rainbow sheen band across the face
            if -1<=band<=3 and not edge and d<R-1.0:
                rc=rain[band+1]; c=tuple(round(c[i]*0.45+rc[i]*0.55) for i in range(3))
            put(im,x,y,c)
    for (x,y) in ((4,4),(5,4),(4,5)): put(im,x,y,SIL[5])
    put(im,6,3,SIL[4])
    mc_grain(im,21,0.10,keep={SIL[5]})
    outline(im,SIL[0]); return im
def ferrite():
    """Dark grey speckled ceramic-metal ingot (own silhouette: bevelled bar in 3/4 view)."""
    im=img(); F=[H('#141619'),H('#24272C'),H('#33373E'),H('#454A52'),H('#5D636C'),H('#7D848E')]; r=random.Random(5)
    for y in range(4,13):
        for x in range(1+(12-y)//3,15-(y-4)//3):
            top=y<=5
            t=4 if top else 3 if y<9 else 2
            if top and x<6: t=5
            if r.random()<0.18: t+= -1 if r.random()<0.7 else 1          # ceramic speckle
            put(im,x,y,F[max(1,min(5,t))])
    outline(im,F[0]); return im
def copper_foil():
    return mc_grain(_copper_foil(),32,0.12)
def _copper_foil():
    """Thin rolled copper sheet: flat sheet with its right end rolled into a tube."""
    im=img(); C=[H('#4A2210'),H('#7A3A1C'),H('#A8582C'),H('#D47C45'),H('#EFA36B'),H('#FFD2A8')]
    for y in range(4,12):
        for x in range(1,11):
            t=4 if y==4 else 3 if y<8 else 2
            if (x*3+y)%7==0: t-=1
            put(im,x,y,C[t])
    for y in range(3,13):                                              # roll
        for x in range(11,15):
            t=[4,5,3,2][x-11] if 3<y<12 else 2
            put(im,x,y,C[t])
    put(im,12,7,C[1]); put(im,12,8,C[1]); put(im,13,7,C[0]); put(im,13,8,C[1])
    outline(im,C[0]); return im
def fiberglass():
    """Woven pale green/white mat: square cloth with an over-under weave, curled corner."""
    im=img(); Gn=[H('#3F5A49'),H('#7FA08A'),H('#A9C8B2'),H('#D4E8DA'),H('#F2FAF4')]
    for y in range(2,14):
        for x in range(2,14):
            over=((x//2)+(y//2))%2==0
            t=3 if over else 2
            if (x%2==0 and over) or (y%2==0 and not over): t+=1
            if x>=12 or y>=12: t-=1
            put(im,x,y,Gn[max(0,min(4,t))])
    for (x,y) in ((13,2),(12,2),(13,3)): im.putpixel((x,y),(0,0,0,0))
    put(im,12,3,Gn[4]); put(im,11,2,Gn[3])
    outline(im,Gn[0]); return im
def solder_paste():
    """Syringe of grey paste, diagonal: clear barrel (rim highlight) with grey fill, steel needle, plunger and flange."""
    im=img(); BAR=[H('#2C3138'),H('#9AA3AE'),H('#C9D0D8'),H('#EEF2F6')]; P=[H('#33373D'),H('#5A5F66'),H('#7A8088'),H('#A0A6AD')]
    for i in range(4,12):                       # barrel, 4 px across the diagonal
        x,y=i,15-i
        for d,c in ((-2,BAR[3]),(-1,P[3] if i>5 else BAR[2]),(0,P[2] if i>5 else BAR[1]),(1,P[1] if i>5 else BAR[1])):
            put(im,x+d,y,c)
    for (x,y) in ((12,4),(13,3),(14,2),(12,3)): put(im,x,y,P[3] if (x,y)!=(12,3) else P[2])     # tip + needle
    for (x,y) in ((2,13),(3,13),(1,13),(2,12)): put(im,x,y,BAR[2])                        # flange
    for (x,y) in ((1,14),(0,15)): put(im,x,y,BAR[1])                                      # plunger
    outline(im,BAR[0]); return im

# ---------------- components ----------------
def substrate():
    return mc_grain(_substrate(),33,0.12)
def _substrate():
    """Blank green PCB: copper traces and pads, mounting holes, no chips."""
    im=img(); PCB=[H('#0C2A16'),H('#14402A'),H('#1D5A39'),H('#2A7A4C'),H('#3C9862')]; CU=[H('#8C5A1E'),H('#C98A3A'),H('#F0C070')]
    for y in range(2,14):
        for x in range(1,15):
            t=3 if (x+y)%5 else 2
            if y==2 or x==1: t=4
            if y==13 or x==14: t=1
            put(im,x,y,PCB[t])
    for x in range(3,13): put(im,x,5,CU[1])
    for y in range(5,11): put(im,8,y,CU[1])
    for x in range(8,12): put(im,x,10,CU[1])
    for (x,y) in ((3,5),(12,5),(11,10),(5,9),(5,8)): put(im,x,y,CU[2])
    for y in range(8,11): put(im,4,y,CU[1])
    for (x,y) in ((2,3),(13,3),(2,12),(13,12)): put(im,x,y,PCB[0])      # mounting holes
    outline(im,PCB[0]); return im
def chip(accent,label=None,small=False,pattern='storage'):
    """Etched die in a package: dark body, gold pins, accent rim, etched surface, optional printed capacity."""
    im=img(); B=[H('#121418'),H('#1C2026'),H('#262B33'),H('#323843'),H('#434A56')]; GOLD=[H('#7A5A16'),H('#C99A35'),H('#F2D27A')]
    lo,hi=(3,12) if small else (2,13)
    for y in range(lo,hi+1):
        for x in range(lo,hi+1):
            t=3 if (x==lo or y==lo) else 1 if (x==hi or y==hi) else 2
            put(im,x,y,B[t])
    for a in range(lo+1,hi,2):                                          # pins on all four sides
        put(im,a,lo-1,GOLD[2]); put(im,a,hi+1,GOLD[1]); put(im,lo-1,a,GOLD[2]); put(im,hi+1,a,GOLD[1])
    for a in range(lo+1,hi):                                            # accent rim (bond ring)
        put(im,a,lo+1,accent[3] if a<8 else accent[2]); put(im,lo+1,a,accent[3] if a<8 else accent[2])
        put(im,a,hi-1,accent[1]); put(im,hi-1,a,accent[1])
    if pattern=='logic':                                                # etched logic: small maze of traces
        for (x,y) in ((5,5),(6,5),(7,5),(7,6),(7,7),(8,7),(9,7),(9,8),(9,9),(10,9),(5,7),(5,8),(5,9),(6,9),(7,9),(7,10)):
            if lo+2<=x<=hi-2 and lo+2<=y<=hi-2: put(im,x,y,accent[2] if (x+y)%3 else accent[3])
    else:                                                               # etched memory array: regular cell grid
        for y in range(lo+2,lo+5):
            for x in range(lo+2,hi-1):
                if (x+y)%2==0: put(im,x,y,accent[1])
    if label:
        w=text_w(label); x0=8-w//2; draw_text(im,x0,hi-7,label,(232,236,240))
    outline(im,B[0]); return mc_grain(im,52,0.14)
def storage_die(accent,label):
    """Storage die: wide SOIC-style package (pins top and bottom), accent bond ring, memory-array etch,
       capacity printed full width so every tier label (8K .. 512K, 2M) fits."""
    im=img(); B=[H('#121418'),H('#1C2026'),H('#262B33'),H('#323843'),H('#434A56')]; GOLD=[H('#7A5A16'),H('#C99A35'),H('#F2D27A')]
    for y in range(3,13):
        for x in range(1,15):
            t=3 if (x==1 or y==3) else 1 if (x==14 or y==12) else 2
            put(im,x,y,B[t])
    for x in range(2,14,2): put(im,x,2,GOLD[2]); put(im,x,13,GOLD[1]); put(im,x,1,GOLD[1]); put(im,x,14,GOLD[0])
    for x in range(2,14): put(im,x,4,accent[3] if x<8 else accent[2]); put(im,x,11,accent[1])   # accent rails
    for x in range(2,14):
        if x%2==0: put(im,x,5,accent[0])                                       # memory-array etch
    w=text_w(label); draw_text(im,8-w//2,6,label,(232,236,240))
    put(im,2,10,accent[2])                                                      # pin-1 dot
    outline(im,B[0]); return mc_grain(im,51,0.14,keep={(232,236,240)})
def photomask(kind):
    """Reusable glass plate in a chrome frame with a visible pattern: logic = traced maze, storage = cell grid."""
    im=img(); GL=[H('#3E6F7A'),H('#6FA6B2'),H('#9FD0D8'),H('#CCEEF2')]; CR=[H('#3A3F46'),H('#8C949E'),H('#C7CDD4'),H('#EEF1F4')]
    PAT=H('#2A3F66') if kind=='logic' else H('#5A2A5E')
    for y in range(1,15):
        for x in range(1,15):
            edge=x in (1,14) or y in (1,14)
            if edge: put(im,x,y,CR[3] if (x==1 or y==1) else CR[1]); continue
            t=2 if x+y<14 else 1
            if x-y in (3,4) : t=3                                           # glass reflection streak
            put(im,x,y,GL[t])
    if kind=='logic':
        pts=[(3,3),(4,3),(5,3),(5,4),(5,5),(6,5),(7,5),(7,6),(7,7),(8,7),(9,7),(9,8),(9,9),(10,9),(11,9),(11,10),(11,11),(12,11),
             (3,6),(3,7),(3,8),(4,8),(5,8),(5,9),(5,10),(6,10),(7,10),(7,11),(7,12),(9,3),(10,3),(11,3),(11,4),(11,5),(12,5)]
        for p in pts: put(im,*p,PAT)
    else:
        for y in range(3,13,2):
            for x in range(3,13,2): put(im,x,y,PAT); put(im,x+1,y,PAT)
    outline(im,CR[0]); return mc_grain(im,41 if kind=='logic' else 42,0.10,keep={PAT,CR[3]})
PLATE=[H('#1F2228'),H('#2B2F36'),H('#454B54'),H('#555B65'),H('#666D77'),H('#79808A')]   # Arcforge plate ramp -> slate
def drive(tier):
    """Storage drive on the Arcforge plate construction, laid flat as an upright rectangle (so the capacity can be
       printed): dark outline all round (plate idx 0/1), stepped face lit top-left with plate grain (idx 3-5),
       thickness band (idx 2) carrying the latch and status light, printed label with tier-colour bands."""
    code,label,name,cols=TIERS[tier]; T=[H(c) for c in cols]
    im=img(); r=random.Random(60+tier)
    for y in range(3,11):                                               # top face
        for x in range(1,15):
            k=(x+2*y)/2
            t=5 if k<8 else 4 if k<12 else 3
            if r.random()<0.16: t=max(3,t-1)
            put(im,x,y,PLATE[t])
    for y in range(11,13):                                              # thickness band
        for x in range(1,15): put(im,x,y,PLATE[2] if y==11 else PLATE[1])
    for y in range(4,11):                                               # printed label, full face width
        for x in range(1,15): put(im,x,y,(226,230,234) if y<10 else (206,210,216))
    for x in range(1,15): put(im,x,4,T[2] if x<8 else T[1])             # tier band above the print
    w=text_w(label); draw_text(im,8-w//2,5,label,(36,40,46))
    for x in range(3,9): put(im,x,11,H('#A3A9B1') if x<8 else H('#79808A'))   # latch
    put(im,12,11,H('#3CE05A')); put(im,13,11,T[2])                      # status light + tier pip
    for x in range(1,15): put(im,x,2,PLATE[1]); put(im,x,13,PLATE[0])   # plate outline (dark all round)
    for y in range(3,13): put(im,0,y,PLATE[1]); put(im,15,y,PLATE[0])
    return im

ITEMS={'silica':arc_based.silica,'silica_blend':arc_based.silica_blend,'silicon_boule':boule,'silicon_wafer':wafer,'ferrite':arc_based.ferrite,'copper_foil':copper_foil,
       'fiberglass':fiberglass,'solder_paste':solder_paste,'circuit_substrate':substrate,
       'logic_die':lambda: chip(CYAN,None,small=True,pattern='logic'),
       'logic_photomask':lambda: photomask('logic'),'storage_photomask':lambda: photomask('storage')}
for i,(code,label,name,cols) in enumerate(TIERS):
    ITEMS[f'storage_die_{code}']=(lambda i=i,label=label: storage_die(TIER_RGB[i],label))
    ITEMS[f'storage_drive_{code}']=(lambda i=i: drive(i))
