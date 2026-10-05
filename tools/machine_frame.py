# Machine screen frame: the green-screen text grid inside a standard GUI-kit panel (no CRT bezel). Same 80 x 24 grid, same
# font and phosphor colours, square GUI pixels (1 screen px = 1 GUI px), a light scanline pass, no vignette / curvature.
import sys; sys.path.insert(0,'/home/claude/msf/tools')
from PIL import Image, ImageDraw, ImageFilter
import numpy as np
import render_layouts as R
from p1_gui import C, OUT
CW,CH=6,10; GW,GH=80*CW,24*CH                         # 480 x 240
TITLE_H=16; PAD=8; WELL=3                             # title band, panel padding, recessed well border
PW,PH=GW+2*PAD+2*WELL, TITLE_H+GH+PAD+2*WELL          # 502 x 270 GUI px
KIT={'out':OUT,'hi':C('#C6C6C6'),'face':C('#4B4B4B'),'lo':C('#2E2E2E'),'well':C('#1A1A1A'),'well_hi':C('#6E6E6E')}
def panel_texture():
    """9-slice source (32 x 32, 4 px borders): the kit's outline, lit top-left bevel, shaded bottom-right, flat face."""
    im=Image.new('RGBA',(32,32),(0,0,0,0)); p=im.load()
    for y in range(32):
        for x in range(32):
            if x in (0,31) or y in (0,31): c=KIT['out']
            elif x==1 or y==1: c=KIT['hi']
            elif x==30 or y==30: c=KIT['lo']
            else: c=KIT['face']
            p[x,y]=c
    for (x,y) in ((0,0),(31,0),(0,31),(31,31)): p[x,y]=(0,0,0,0)
    return im
def well_texture():
    """9-slice (16 x 16, 3 px borders): recessed screen well - dark lip top-left, light lip bottom-right, black centre."""
    im=Image.new('RGBA',(16,16),(0,0,0,0)); p=im.load()
    for y in range(16):
        for x in range(16):
            if x==0 or y==0: c=KIT['lo']
            elif x==15 or y==15: c=KIT['well_hi']
            elif x in (1,2) or y in (1,2): c=KIT['out']
            else: c=(0,0,0)
            p[x,y]=c+(255,) if len(c)==3 else c
    return im
def nine(src,w,h,b):
    out=Image.new('RGBA',(w,h),(0,0,0,0)); sw,sh=src.size
    def piece(sx0,sy0,sx1,sy1,dx0,dy0,dx1,dy1):
        if dx1>dx0 and dy1>dy0: out.alpha_composite(src.crop((sx0,sy0,sx1,sy1)).resize((dx1-dx0,dy1-dy0),Image.NEAREST),(dx0,dy0))
    xs=[(0,b,0,b),(b,sw-b,b,w-b),(sw-b,sw,w-b,w)]; ys=[(0,b,0,b),(b,sh-b,b,h-b),(sh-b,sh,h-b,h)]
    for (sx0,sx1,dx0,dx1) in xs:
        for (sy0,sy1,dy0,dy1) in ys: piece(sx0,sy0,sx1,sy1,dx0,dy0,dx1,dy1)
    return out
def grid_image(screen,ph='green'):
    """The text grid at 1:1 (480 x 240), same phosphor colours and attributes as the CRT renderer, light scanlines."""
    N,B,D,BG=R.PH[ph]; im=Image.new('RGBA',(GW,GH),BG+(255,)); px=im.load(); sheet=R.sheet
    for r in range(24):
        for c in range(80):
            col={'n':N,'b':B,'d':D}[screen.attr[r][c]]; x0,y0=c*CW,r*CH; fg=col
            if screen.rv[r][c]:
                for y in range(CH):
                    for x in range(CW): px[x0+x,y0+y]=col+(255,)
                fg=BG
            ch=screen.rows[r][c]
            if ch!=' ':
                i=R.idx(ch); g=sheet.crop(((i%16)*CW,(i//16)*CH,(i%16)*CW+CW,(i//16)*CH+CH)).load()
                for y in range(CH):
                    for x in range(CW):
                        if g[x,y][3]: px[x0+x,y0+y]=fg+(255,)
            if screen.ul[r][c]:
                for x in range(CW): px[x0+x,y0+CH-1]=col+(255,)
    gl=im.filter(ImageFilter.GaussianBlur(1.2)); a=np.asarray(gl).astype(np.float32); a[...,3]*=0.35
    base=Image.new('RGBA',(GW,GH),BG+(255,)); base.alpha_composite(Image.fromarray(a.astype(np.uint8))); base.alpha_composite(im)
    sl=Image.new('RGBA',(GW,GH),(0,0,0,0)); d=ImageDraw.Draw(sl)
    for y in range(1,GH,2): d.line((0,y,GW,y),fill=(0,0,0,26))
    base.alpha_composite(sl); return base
def frame(screen,title,ph='green'):
    """Panel (9-slice), title in the kit's TEXT colour at (8,5) - drawn with the GUI font in game -, the well, the grid."""
    out=nine(panel_texture(),PW,PH,4)
    out.alpha_composite(nine(well_texture(),GW+2*WELL,GH+2*WELL,3),(PAD,TITLE_H))
    out.alpha_composite(grid_image(screen,ph),(PAD+WELL,TITLE_H+WELL))
    sys.path.insert(0,'/home/claude/rack2/tools')
    src=open('/home/claude/rack2/tools/gui2_preview.py').read(); g={}
    exec(src.split("P='/home/claude/rack2/previews/'")[0].replace("ROOTS=['/home/claude/rack2/","ROOTS=['/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/','/home/claude/rack2/"),g)
    g['T'](out,8,5,title,'#F0F0F0'); return out
