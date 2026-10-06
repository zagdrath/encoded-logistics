# Industrial Cage Lights (16 dye colours), Sirens (green / orange / yellow / red), Wall + Ceiling Speakers.
# TEXTURE_STYLE: 1 texel per model px, colour ramps, structural shading, no specks; models built floor-mounted (attached face
# below) and rotated to any of the six faces.
import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
from w2_tex import S, outline
A_='src/main/resources/assets/encodedlogistics/'; RL=lambda p:'encodedlogistics:'+p
GLOW={'shade':False,'neoforge_data':{'block_light':15,'sky_light':15}}
H=lambda h: tuple(int(h[i:i+2],16) for i in (1,3,5))
DYES={'white':'#F9FFFE','orange':'#F9801D','magenta':'#C74EBD','light_blue':'#3AB3DA','yellow':'#FED83D','lime':'#80C71F','pink':'#F38BAA','gray':'#474F52',
      'light_gray':'#9D9D97','cyan':'#169C9C','purple':'#8932B8','blue':'#3C44AA','brown':'#835432','green':'#5E7C16','red':'#B02E26','black':'#1D1D21'}
SIREN_COLOURS=('green','orange','yellow','red')
METAL=[H(x) for x in ('#1E1612','#2C211A','#3B2C22','#4C392C','#5E4838','#715846','#856A57','#9A7E6A')]   # weathered dark metal (reference)
def ramp(hexc):
    """7 steps from dark to light around a dye colour (whole steps, no gradients): 0..2 shades, 3 the colour, 4..6 tints."""
    c=H(hexc); mix=lambda a,b,t: tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))
    return [mix(c,(0,0,0),t) for t in (0.72,0.52,0.28)]+[c]+[mix(c,(255,255,255),t) for t in (0.3,0.55,0.8)]
def px(im,x,y,c): im.putpixel((x,y),tuple(c)+(255,))
def save(im,p): os.makedirs(os.path.dirname(A_+p),exist_ok=True); im.save(A_+p)
def jd(o,p): os.makedirs(os.path.dirname(A_+p),exist_ok=True); json.dump(o,open(A_+p,'w'),indent=1)
def mc(p,ft): open(A_+p+'.mcmeta','w').write(json.dumps({'animation':{'frametime':ft}},indent=2)+'\n')
def F(t,uv): return {'texture':t,'uv':uv}
def box(n,a,b,faces,**k): d={'name':n,'from':list(a),'to':list(b),'faces':faces}; d.update(k); return d
def all6(t,uv): return {d:F(t,uv) for d in ('north','south','east','west','up','down')}
def model(tx,E,rt='minecraft:cutout'):
    return {'parent':'minecraft:block/block','render_type':rt,'textures':{**{k:RL(v) for k,v in tx.items()},'particle':RL(list(tx.values())[0])},'elements':E}
FACE6={'up':{},'down':{'x':180},'north':{'x':90},'south':{'x':90,'y':180},'west':{'x':90,'y':270},'east':{'x':90,'y':90}}
# ================= textures =================
def metal_tex():
    """Weathered dark metal: stepped falloff from the top-left, a lit edge, a few structural rivet / seam pixels."""
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            t=x+y; px(im,x,y,METAL[5] if t<8 else METAL[4] if t<18 else METAL[3] if t<26 else METAL[2])
    for x in range(16): px(im,x,0,METAL[6]); px(im,x,15,METAL[1])
    for y in range(16): px(im,0,y,METAL[6]); px(im,15,y,METAL[1])
    return im
def glass_tex(hexc,lit):
    """Solid glass (as the reference): a flat deep colour on the sides, one step lighter on the top face (texels 10-15, 0-5),
       a darker bottom row; lit: the bright colour with a small highlight."""
    R=ramp(hexc); im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            top=x>=10 and y<5
            c=(R[5] if top else R[4]) if lit else (R[3] if top else R[2])
            if not top and y in (6,14): c=R[3] if lit else R[1]
            px(im,x,y,c)
    for (x,y) in ((1,1),(1,2),(6,1),(6,2)): px(im,x,y,R[6] if lit else R[3])
    return im
def lamp_glass(hexc,lit):
    """Glass of the cube lamp, 1 texel / px: each 6 x 6 face is two 2.5-px panes either side of the cage's centre bar; deep
       shades unlit, the saturated colour lit, a small highlight top-left."""
    R=ramp(hexc); im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            edge=x%6 in (0,5) or y%6 in (0,5)
            c=(R[3] if edge else R[4]) if lit else (R[1] if edge else R[2])
            px(im,x,y,c)
    for (x,y) in ((1,1),(1,2),(2,1)): px(im,x,y,R[5] if lit else R[3])
    return im
def cage_tex():
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16): px(im,x,y,S[1] if (x+y)%7 else S[2])
    for x in range(16): px(im,x,0,S[3])
    return im
def base_tex():
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16): px(im,x,y,S[2] if (x+y)<20 else S[1])
    for x in range(16): px(im,x,0,S[4]); px(im,x,15,S[0])
    for y in range(16): px(im,0,y,S[4]); px(im,15,y,S[0])
    return im
GRILLE_ROWS=[4,8,10,10,10,10,10,10,8,4]          # the reference circle: 10 px across, row widths
SILVER=[H(x) for x in ('#3E4246','#575C61','#71767B','#8B9095','#A5AAAE','#BEC2C5','#D6D9DB','#ECEEEF')]
def grille_mask(x0=3,y0=3):
    m=set()
    for r,w in enumerate(GRILLE_ROWS):
        s_=x0+(10-w)//2
        for x in range(s_,s_+w): m.add((x,y0+r))
    return m
def speaker_face(frame=0):
    """The speaker's face (16 x 16, the disc uses texels 2-13): a shaded silver ring (lit top-left, shaded bottom-right, a lit
       outer edge, an inner shadow line against the grille) round the REFERENCE grille - a 10-px pixel circle, near-black
       outline, a 1-px checkerboard of two dark greys inside (the playing frame swaps the checker phase)."""
    import math
    im=Image.new('RGBA',(16,16),(0,0,0,0)); m=grille_mask()
    for y in range(16):
        for x in range(16):
            t=(x-7.5)*0.7+(y-7.5)*0.7; d=math.hypot(x-7.5,y-7.5)
            c=SILVER[6] if t<-4.5 else SILVER[5] if t<-1.5 else SILVER[4] if t<1.5 else SILVER[3] if t<4.5 else SILVER[2]
            if d>6.6: c=SILVER[7] if t<0 else SILVER[1]                         # the lit / shaded outer edge of the disc
            px(im,x,y,c)
    for (x,y) in m:
        edge=any((x+dx,y+dy) not in m for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)))
        px(im,x,y,H('#161616') if edge else (H('#2D2D2D') if (x+y+frame)%2 else H('#3B3B3B')))
    for (x,y) in m:                                                             # inner shadow where the ring meets the grille (top-left side)
        for dx,dy in ((0,-1),(-1,0)):
            if (x+dx,y+dy) not in m and 0<=x+dx<16 and 0<=y+dy<16 and (x+dx+y+dy)<15: px(im,x+dx,y+dy,SILVER[1])
    return im
def lens_tex(name,state):
    R=ramp({'green':'#3CC83C','orange':'#F08A2A','yellow':'#FFD83C','red':'#E5352C'}[name]); im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            t=x+y
            px(im,x,y,(R[5] if t<6 else R[4] if t<16 else R[3]) if state=='on' else (R[2] if t<10 else R[1]))
    if state=='on': px(im,2,2,R[6])
    return im
def grille_tex(kind,frame=0):
    """Speaker grilles: wall = cloth weave (2-texel rows, alternating step); ceiling = perforated (hole every 2 px)."""
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            if kind=='cloth': c=S[2] if (y+frame)%2==0 and x%2==0 else S[1]
            else: c=S[0] if (x%2==0 and y%2==0) else (S[3] if (x+y+frame)%7 else S[2])
            px(im,x,y,c)
    return im
def led_tex(state):
    im=Image.new('RGBA',(16,16),(0,0,0,0)); c={'playing':(0x3C,0xE0,0x5A),'error':(0xF0,0xB0,0x30)}[state]
    for y in range(16):
        for x in range(16): px(im,x,y,c)
    return im
# ================= models =================
def cage_light():
    """Slim caged light on the AUTHOR'S texture (textures/block/cage_light/caged_signal_light_<dye>.png, 32 x 32 - 2 texels per
       uv unit, every face sampled 1 texel per px): a 6 x 1 x 6 base (grey frame), a 4 x 7 x 4 glass column (glass_<dye>), a
       5 x 7.5 x 5 cage of the sheet's bar grid (cutout - the gaps are transparent), a 5 x 0.5 x 5 cap whose top is the sheet's
       window (the white window recoloured per dye)."""
    T=lambda u: F('#sheet',u); G=lambda u: F('#glass',u)
    side=[4.5,0.5,7,4.25]                     # texels (9,1)-(14,8.5): bars on the face's left edge and middle, rows 1, 4, 7
    E=[box('base',(5,0,5),(11,1,11),{'up':T([0,0,3,3]),'down':T([0,0,3,3]),'north':T([0,8,3,8.5]),'south':T([0,8,3,8.5]),'east':T([0,8,3,8.5]),'west':T([0,8,3,8.5])}),
       box('glass',(6,1,6),(10,8,10),{'north':G([0,0,4,7]),'south':G([4,0,8,7]),'east':G([0,8,4,15]),'west':G([4,8,8,15])}),
       box('cage',(5.5,1,5.5),(10.5,8.5,10.5),{d:T(side) for d in ('north','south','east','west')}),
       box('cap',(5.5,8.5,5.5),(10.5,9,10.5),{'up':T([0.5,0,3,2.5]),'down':T([4.5,6.5,7,9]),'north':T([0,8,2.5,8.25]),'south':T([0,8,2.5,8.25]),'east':T([0,8,2.5,8.25]),'west':T([0,8,2.5,8.25])})]
    return E
def siren():
    M=F('#metal',[0,0,16,16]); G=F('#grille',[0,0,16,16]); L=F('#lens',[0,0,16,16])
    return [box('bracket',(5,0,5),(11,1,11),{d:M for d in ('north','south','east','west','up','down')}),
            box('post',(7,1,7),(9,3,9),{d:F('#metal',[0,0,2,2]) for d in ('north','south','east','west')}),
            box('housing',(5,3,5),(11,8,11),{'north':G,'south':G,'east':G,'west':G,'up':F('#housing',[0,0,6,6]),'down':F('#housing',[0,0,6,6])}),
            box('lens_base',(5.5,8,5.5),(10.5,9,10.5),{d:L for d in ('north','south','east','west','up')}),
            box('lens_dome',(6.5,9,6.5),(9.5,11,9.5),{d:L for d in ('north','south','east','west','up')})]
def siren_glow():
    L=F('#lens',[0,0,16,16])
    return [box('lens_base',(5.49,7.99,5.49),(10.51,9.01,10.51),{d:L for d in ('north','south','east','west','up')},**GLOW),
            box('lens_dome',(6.49,8.99,6.49),(9.51,11.01,9.51),{d:L for d in ('north','south','east','west','up')},**GLOW)]
def speaker():
    """One speaker for ceilings and walls (any face): a 14 x 14 x 1 panel whose front is the AUTHOR'S smart_speaker_front.png
       (texels 1-14), sides from its frame edge."""
    Fr=dict(F('#face',[1,1,15,15]),rotation=180); Ed=F('#face',[1,0,15,1])   # rotation 180: upright when mounted on a wall
    return [box('panel',(1,0,1),(15,1,15),{'up':Fr,'down':F('#face',[1,1,15,15]),'north':Ed,'south':Ed,'east':Ed,'west':Ed})]
def led(x,z,y): return [box('led',(x,y,z),(x+1,y+0.01,z+1),{'up':F('#led',[0,0,1,1])},**GLOW)]
def shapes(E):
    return [[round(v,3) for v in e['from']+e['to']] for e in E if e['name'] in ('base','glass','cage','bracket','housing','post','lens_base','lens_dome','cabinet','disc_a','disc_b','disc_c')]
