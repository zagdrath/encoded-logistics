import sys; sys.path.insert(0,'/home/claude/ctlif/tools')
from PIL import Image, ImageFilter
import numpy as np, layouts as LY
sheet=Image.open('/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/font/terminal.png').convert('RGBA'); EXTRA='\u00ac\u2500\u2502\u250c\u2510\u2514\u2518\u251c\u2524\u252c\u2534\u253c'
PH={'green':((0x28,0xD2,0x5A),(0xDA,0xFF,0xE4),(0x16,0x7A,0x34),(2,9,4)),'amber':((0xE8,0x9A,0x10),(0xFF,0xE8,0xB8),(0x8E,0x5A,0x06),(10,6,2)),
    'white':((0xB8,0xBC,0xB6),(0xFF,0xFF,0xFF),(0x6E,0x72,0x6C),(5,6,10))}
CW,CH=6,10
def idx(c): return ord(c)-32 if 32<=ord(c)<128 else (96+EXTRA.index(c) if c in EXTRA else 31)
def render(s,ph='green',SX=2,SY=3):
    N,B,D,BG=PH[ph]; txt=Image.new('RGBA',(80*CW+40,24*CH+20),(0,0,0,0)); px=txt.load()
    for r in range(24):
        for c in range(80):
            col={'n':N,'b':B,'d':D}[s.attr[r][c]]; x0,y0=20+c*CW,10+r*CH; fg=col
            if s.rv[r][c]:
                for y in range(CH):
                    for x in range(CW): px[x0+x,y0+y]=col+(255,)
                fg=BG
            ch=s.rows[r][c]
            if ch!=' ':
                i=idx(ch); g=sheet.crop(((i%16)*CW,(i//16)*CH,(i%16)*CW+CW,(i//16)*CH+CH)).load()
                for y in range(CH):
                    for x in range(CW):
                        if g[x,y][3]: px[x0+x,y0+y]=fg+(255,)
            if s.ul[r][c]:
                for x in range(CW): px[x0+x,y0+CH-1]=col+(255,)
    big=txt.resize((txt.width*SX,txt.height*SY),Image.NEAREST); gl=big.filter(ImageFilter.GaussianBlur(3)); gl.putalpha(gl.split()[3].point(lambda v:int(v*0.45)))
    out=Image.new('RGBA',big.size,BG+(255,)); out.alpha_composite(gl); out.alpha_composite(big)
    return out.convert('RGB')
if __name__=='__main__':
    for k,s in LY.L.items(): render(s).save(f'/home/claude/ctlif/previews/screens/{k}.png')
    ims=[render(LY.L['05_source_editor'],p) for p in ('green','amber','white')]
    c=Image.new('RGB',(ims[0].width,3*ims[0].height+20),(30,31,34))
    for i,im in enumerate(ims): c.paste(im,(0,i*(im.height+10)))
    c.resize((c.width//2,c.height//2)).save('/home/claude/ctlif/previews/screens/phosphor_editor.png'); print('ok')
