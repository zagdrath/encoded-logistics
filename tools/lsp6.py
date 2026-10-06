# Caged light (the author's model, slimmed) + 2-block speaker. Mod-consistent: the author's sheet greys remapped onto the mod's
# steel ramp; silver steel cabinet; every face 1 texel per px.
import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
from lsp import *
def to_steel(im):
    """Remap the author's sheet onto the mod's steel ramp by luminance (greys and browns -> steel steps 0-8); keep alpha."""
    out=im.copy(); p=out.load()
    for y in range(im.height):
        for x in range(im.width):
            r,g,b,a=p[x,y]
            if a==0: continue
            lum=0.299*r+0.587*g+0.114*b
            if lum>200: continue                                                 # leave the white window (it is tinted / recoloured)
            p[x,y]=tuple(S[max(0,min(8,int(lum/22)))])+(a,)
    return out
def bulb_tex(lit):
    """Greyscale bulb for tintindex 0 (the block colour handler supplies the dye colour): steel-ramp whites, a lighter centre,
       a highlight; lit one step brighter. 16 x 16, 1 texel per px."""
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            c=S[10] if (lit and x%3==1) else S[9] if lit else (S[8] if x%3==1 else S[7])
            px(im,x,y,c)
    px(im,1,1,(255,255,255)); px(im,1,2,(255,255,255))
    return im
def caged_light(lit):
    """The author's caged light (caged_light_off/on.json), slimmed: cage 4 x 7 x 4 (was 6 x 6 x 6), double-sided like the
       original (an inside-out copy shows the bars' inner faces through the gaps), bulb 3 x 6 x 3 (was 4.5 x 5.5 x 4.5), base
       6 x 1 x 6 (was 8 x 1 x 8). uv crops of the author's 32 x 32 sheet at 1 texel per px."""
    side=[4,0,6,3.5]; top=[7,3,9,5]
    def cage(a,b,n):
        return {'name':n,'from':a,'to':b,'faces':{'north':F('#3',side),'east':F('#3',side),'south':F('#3',side),'west':F('#3',side),'up':F('#3',top),'down':F('#3',top)}}
    bulb={'name':'bulb','from':[6.5,1,6.5],'to':[9.5,7,9.5],'faces':{d:{'uv':[0,0,3,6] if d not in ('up','down') else [0,0,3,3],'texture':'#4','tintindex':0} for d in ('north','east','south','west','up','down')}}
    if lit: bulb.update(GLOW)
    base={'name':'base','from':[5,0,5],'to':[11,1,11],'faces':{'north':F('#3',[0,8,3,8.5]),'east':F('#3',[0,8.5,3,9]),'south':F('#3',[0,9,3,9.5]),'west':F('#3',[4,9,7,9.5]),'up':F('#3',[3.5,3.5,0.5,0.5]),'down':F('#3',[3.5,3.5,0.5,0.5])}}
    return [cage([10,1,6],[6,8,10],'cage_inner'),cage([6,1,6],[10,8,10],'cage'),bulb,base]
# ---------------- speaker: 2 blocks tall ----------------
USER_SPK='/mnt/user-data/uploads/smart_speaker_front.png'
def grille():
    """The author's grille: the 10 x 10 circle region (texels 3-12) of smart_speaker_front.png, with its alpha mask."""
    im=Image.open(USER_SPK).convert('RGBA'); g=Image.new('RGBA',(10,10),(0,0,0,0))
    rows=[4,8,10,10,10,10,10,10,8,4]
    for r,w in enumerate(rows):
        s_=(10-w)//2
        for x in range(s_,s_+w):
            p=im.getpixel((3+x,3+r))
            if p[0]-p[2]>18:                                                    # a brown frame pixel on the rim -> the mod's steel
                lum=0.299*p[0]+0.587*p[1]+0.114*p[2]; p=tuple(S[max(0,min(4,int(lum/26)))])+(255,)
            g.putpixel((x,r),p)
    return g
def speaker_front():
    """16 x 32 sheet (1 texel per px): the cabinet front 14 x 30 at (1, 1): silver steel (steps 6-8 with stepped falloff from the
       top-left, the mod's bevel: lit top / left rail 9, shaded bottom / right 5, glint 10 at 6-7), two of the author's grilles
       (top at rows 5-14, bottom at rows 17-26) each sunk in a recessed ring (step 3 shadow line top-left, step 9 lip
       bottom-right), the LED socket bottom-right."""
    im=Image.new('RGBA',(16,32),(0,0,0,0)); W,Hh=14,30
    for y in range(Hh):
        for x in range(W):
            t=(x+y)/(W+Hh); px(im,1+x,1+y,S[8] if t<0.3 else S[7] if t<0.6 else S[6])
    for x in range(W): px(im,1+x,1,S[9]); px(im,1+x,Hh,S[5])
    for y in range(Hh): px(im,1,1+y,S[9]); px(im,W,1+y,S[5])
    for k in (6,7): px(im,1+k,1,S[10]); px(im,1,1+k,S[10])
    g=grille()
    for gy in (5,17):
        gx=3
        for y in range(10):
            for x in range(10):
                if g.getpixel((x,y))[3]:
                    # ring: shadow line on the top / left neighbours, lit lip on the bottom / right
                    for dx,dy,c in ((-1,0,S[3]),(0,-1,S[3]),(1,0,S[9]),(0,1,S[9])):
                        if not g.getpixel((min(9,max(0,x+dx)),min(9,max(0,y+dy))))[3] or not (0<=x+dx<10 and 0<=y+dy<10):
                            px(im,gx+x+dx,gy+y+dy,c)
        im.alpha_composite(g,(gx,gy))
    px(im,12,28,S[1])                                                           # LED socket
    return im
def speaker_side():
    im=Image.new('RGBA',(16,32),(0,0,0,0))
    for y in range(32):
        for x in range(16): px(im,x,y,S[7] if y<10 else S[6] if y<22 else S[5])
    for y in range(32): px(im,0,y,S[8])
    return im
def speaker_models():
    """Wall: back flush to the wall (z 12-16); floor: standing (z 6-10, a plinth); ceiling: hanging (y -14..16 from the top block,
       a bracket). Front faces north before rotation; 14 x 30 x 4 cabinet."""
    fr=F('#front',[1,0.5,15,15.5]); sd=F('#side',[0,0,4,15]); tp=F('#side',[0,0,14,2])   # 16 x 32 sheets: v 0-16 spans 32 texels
    def cab(y0,z0): return {'name':'cabinet','from':[1,y0,z0],'to':[15,y0+30,z0+4],'faces':{'north':fr,'south':F('#side',[0,0,14,15]),'east':sd,'west':sd,'up':tp,'down':tp}}
    wall=[cab(1,12)]
    floor=[cab(1,6),{'name':'plinth','from':[3,0,5],'to':[13,1,11],'faces':{d:F('#side',[0,0,10,0.5] if d not in ('up','down') else [0,0,10,3]) for d in ('north','south','east','west','up','down')}}]
    ceil=[cab(-15,6),{'name':'bracket','from':[6,15,7],'to':[10,16,9],'faces':{d:F('#side',[0,0,4,0.5] if d not in ('up','down') else [0,0,4,1]) for d in ('north','south','east','west','up','down')}}]
    return wall,floor,ceil
def led_el(y0,z0,st): return [{'name':'led','from':[3,y0+2,z0-0.01],'to':[4,y0+3,z0-0.01],'faces':{'north':F('#led',[0,0,1,1])},**GLOW}]
# ---------------- speaker v2: one block, a 14 x 14 x 2 panel ----------------
def speaker_panel_front():
    """16 x 16: a 14 x 14 silver steel panel (steps 6-8, stepped falloff from the top-left, the mod's bevel - lit top / left
       rail 9, shaded bottom / right 5, glint 10) with the author's grille centred (texels 3-12) in a recessed ring, LED socket
       in the bottom-right corner."""
    im=Image.new('RGBA',(16,16),(0,0,0,0)); W=14
    for y in range(W):
        for x in range(W):
            t=(x+y)/(2*W); px(im,1+x,1+y,S[8] if t<0.3 else S[7] if t<0.62 else S[6])
    for x in range(W): px(im,1+x,1,S[9]); px(im,1+x,W,S[5])
    for y in range(W): px(im,1,1+y,S[9]); px(im,W,1+y,S[5])
    for k in (6,7): px(im,1+k,1,S[10]); px(im,1,1+k,S[10])
    g=grille(); gx=gy=3
    for y in range(10):
        for x in range(10):
            if not g.getpixel((x,y))[3]: continue
            for dx,dy,c in ((-1,0,S[3]),(0,-1,S[3]),(1,0,S[9]),(0,1,S[9])):
                inside=0<=x+dx<10 and 0<=y+dy<10 and g.getpixel((x+dx,y+dy))[3]
                if not inside: px(im,gx+x+dx,gy+y+dy,c)
    im.alpha_composite(g,(gx,gy))
    px(im,13,13,S[1])
    return im
def speaker_panel_side():
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16): px(im,x,y,S[8] if y%2==0 else S[6])
    return im
def speaker_panel():
    """14 x 14 x 2, built floor-mounted (back on the block below), rotated to any face; the front face rotated 180 so the
       texture is upright on walls."""
    fr=dict(F('#front',[1,1,15,15]),rotation=180); sd=F('#side',[0,0,14,2])
    return [{'name':'panel','from':[1,0,1],'to':[15,2,15],'faces':{'up':fr,'down':F('#side',[1,1,15,15]),'north':sd,'south':sd,'east':sd,'west':sd}}]
def speaker_led():
    # texel (13, 13) with the face rotated 180 -> model x 2..3, z 2..3 on the top face (floor orientation)
    return [{'name':'led','from':[2,2.01,2],'to':[3,2.01,3],'faces':{'up':F('#led',[0,0,1,1])},**GLOW}]
