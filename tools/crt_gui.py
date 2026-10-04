# Green-screen terminal GUI: renderer (mockups) + assets (font sheets, bezel, scanlines, vignette, phosphor palette).
import re
from PIL import Image, ImageFilter, ImageDraw
import crt_font as F
COLS,ROWS,CW,CH=80,24,F.CW,F.CH
PHOSPHOR={'green':{'normal':'#28D25A','bright':'#DAFFE4','dim':'#167A34','bg':'#020904','glow':'#33F06A'},
          'amber':{'normal':'#E89A10','bright':'#FFE8B8','dim':'#8E5A06','bg':'#0A0602','glow':'#FFA010'},
          'white':{'normal':'#B8BCB6','bright':'#FFFFFF','dim':'#6E726C','bg':'#05060A','glow':'#C8D0FF'}}
def hx(h): return tuple(int(h[i:i+2],16) for i in (1,3,5))
TOK=re.compile(r'\{(n|b|d|u|r|/u|/r)\}')
def parse(line):
    """markup -> list of (char, style, underline, reverse). {n} normal {b} bright {d} dim {u}..{/u} field {r}..{/r} reverse."""
    out=[]; st='n'; ul=False; rv=False; i=0
    for part in TOK.split(line):
        if part in ('n','b','d'): st=part; continue
        if part=='u': ul=True; continue
        if part=='/u': ul=False; continue
        if part=='r': rv=True; continue
        if part=='/r': rv=False; continue
        for ch in part: out.append((ch,st,ul,rv))
    return out[:COLS]
SX,SY=2,3              # pixel aspect: each virtual pixel is 2 wide x 3 tall -> 480x240 becomes 960x720 = 4:3 (tall CRT)
MARGIN_PX=(20,10)      # text inset inside the glass, virtual px: 20 left/right, 10 top/bottom -> glass 520 x 260 = 4:3 with tall pixels
def render(lines,ph='green',cursor=None,scale=1,frame=True):
    """scale k: output pixels per virtual pixel = (SX*k, SY*k)."""
    P=PHOSPHOR[ph]; W,H=COLS*CW,ROWS*CH; mx,my=MARGIN_PX
    txt=Image.new('RGBA',(W,H),(0,0,0,0)); sheet=F.sheet(); px=txt.load()
    for r,line in enumerate(lines[:ROWS]):
        for ci,(ch,st,ul,rv) in enumerate(parse(line)):
            col=hx(P['normal'] if st=='n' else P['bright'] if st=='b' else P['dim'])
            x0,y0=ci*CW,r*CH
            if rv:
                for y in range(CH):
                    for x in range(CW): px[x0+x,y0+y]=col+(255,)
            code=ord(ch) if 32<=ord(ch)<128 else 63; i=code-32; sx,sy=(i%16)*CW,(i//16)*CH
            for y in range(CH):
                for x in range(CW):
                    if sheet.getpixel((sx+x,sy+y))[3]: px[x0+x,y0+y]=((0,0,0,255) if rv else col+(255,))
            if ul:
                for x in range(CW): px[x0+x,y0+CH-1]=col+(255,)
    if cursor:
        cx,cy=cursor
        for y in range(1,8):
            for x in range(5): px[cx*CW+x,cy*CH+y]=hx(P['bright'])+(255,)
    VW,VH=W+2*mx,H+2*my
    bg=Image.new('RGBA',(VW,VH),hx(P['bg'])+(255,))
    layer=Image.new('RGBA',(VW,VH),(0,0,0,0)); layer.alpha_composite(txt,(mx,my))
    ox,oy=SX*scale,SY*scale
    big=bg.resize((VW*ox,VH*oy),Image.NEAREST)
    tl=layer.resize((VW*ox,VH*oy),Image.NEAREST)
    glow=tl.filter(ImageFilter.GaussianBlur(1.4*ox)); ga=glow.split()[3].point(lambda a:int(a*0.45)); glow.putalpha(ga)
    big.alpha_composite(glow); big.alpha_composite(tl)
    sl=Image.new('RGBA',big.size,(0,0,0,0)); d=ImageDraw.Draw(sl)
    for y in range(oy-1,big.height,oy): d.line((0,y,big.width,y),fill=(0,0,0,52))        # one scanline per virtual row
    big.alpha_composite(sl)
    if not frame: return big
    pad=24*scale; out=Image.new('RGBA',(big.width+2*pad,big.height+2*pad),(0,0,0,255)); out.alpha_composite(big,(pad,pad))
    out.alpha_composite(bezel(out.size,pad))
    return out
def vignette(size,radius=10):
    W,H=size; im=Image.new('L',size,0); d=ImageDraw.Draw(im); steps=min(18,W//4,H//4)
    for k in range(steps):
        a=int(70*(1-k/steps)**2); d.rounded_rectangle((k,k,W-1-k,H-1-k),radius=max(2,radius-k//2),outline=a)
    return im
def bezel(size,pad):
    """Curved CRT bezel with gently rounded corners (radius 8) and a soft edge falloff that never reaches the text."""
    W,H=size; im=Image.new('RGBA',size,(0,0,0,0)); d=ImageDraw.Draw(im)
    d.rectangle((0,0,W-1,H-1),fill=(24,26,24,255))
    d.rounded_rectangle((pad-3,pad-3,W-pad+2,H-pad+2),radius=8,fill=(0,0,0,0))
    d.rounded_rectangle((pad-3,pad-3,W-pad+2,H-pad+2),radius=8,outline=(56,60,56,255),width=2)
    d.rounded_rectangle((2,2,W-3,H-3),radius=10,outline=(44,47,44,255),width=2)
    v=vignette((W-2*pad+6,H-2*pad+6),10); vv=Image.new('RGBA',v.size,(0,0,0,0)); vv.putalpha(v)
    im.alpha_composite(vv,(pad-3,pad-3)); return im
# ---------------- assets ----------------
def glow_sheet():
    """Pre-blurred glyphs for the in-game glow pass: same 16x6 layout, cells 10 x 14 (2 px padding), alpha only."""
    s=F.sheet(); out=Image.new('RGBA',(16*10,6*14),(0,0,0,0))
    for i in range(96):
        sx,sy=(i%16)*CW,(i//16)*CH; cell=Image.new('RGBA',(10,14),(0,0,0,0)); cell.alpha_composite(s.crop((sx,sy,sx+CW,sy+CH)),(2,2))
        b=cell.filter(ImageFilter.GaussianBlur(1.2)); a=b.split()[3].point(lambda v:min(255,int(v*1.6))); b=Image.new('RGBA',(10,14),(255,255,255,0)); b.putalpha(a)
        out.alpha_composite(b,((i%16)*10,(i//16)*14))
    return out
def bezel_9slice():
    """64x64 9-slice bezel (16 px borders) drawn around the scaled screen; the centre is transparent."""
    im=Image.new('RGBA',(64,64),(0,0,0,0)); d=ImageDraw.Draw(im)
    d.rectangle((0,0,63,63),fill=(24,26,24,255))
    d.rounded_rectangle((13,13,50,50),radius=4,fill=(0,0,0,0))
    d.rounded_rectangle((13,13,50,50),radius=4,outline=(56,60,56,255),width=2)
    d.rounded_rectangle((1,1,62,62),radius=5,outline=(44,47,44,255),width=2)
    return im
def scanlines(): im=Image.new('RGBA',(1,3),(0,0,0,0)); im.putpixel((0,2),(0,0,0,52)); return im   # 1 dark row per 3 (one per virtual row)
def vignette_tex():
    v=vignette((256,192),10); im=Image.new('RGBA',(256,192),(0,0,0,0)); im.putalpha(v); return im
