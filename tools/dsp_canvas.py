# Display Panel canvas renderer (what the block entity renderer draws): 32 x 32 canvas px per panel block, text from the
# terminal font sheet (6 x 10 cells; 1x and 2x), widget visuals in the mod's style (steel ramp chrome, status-light fills).
import math, random
from PIL import Image
from w2_tex import S, LB, OR, YE, RD, H
FONT=Image.open('/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/font/terminal.png').convert('RGBA')
EXTRA='\u00ac\u2500\u2502\u250c\u2510\u2514\u2518\u251c\u2524\u252c\u2534\u253c'
BG=S[1]; TEXT=H('#F0F0F0'); MUTED=H('#B4B4B4'); ACCENT=H('#00D992'); GREEN=(H('#B5FFB0'),H('#3CE05A'),H('#22A03C'))
STATUS=[(H('#B5FFB0'),H('#3CE05A'),H('#22A03C')),(H('#FFF3A0'),H('#F0D030'),H('#B89A14')),(H('#FFC890'),H('#F08A2A'),H('#B05E14')),(H('#FF9C90'),H('#E5483C'),H('#8E231C'))]
def canvas(bw,bh): return Image.new('RGB',(32*bw,32*bh),BG)
def P(c,x,y,col):
    if 0<=x<c.width and 0<=y<c.height: c.putpixel((x,y),tuple(col))
def rect(c,x0,y0,w,h,col):
    for y in range(y0,y0+h):
        for x in range(x0,x0+w): P(c,x,y,col)
def glyph_index(ch): return ord(ch)-32 if 32<=ord(ch)<128 else (96+EXTRA.index(ch) if ch in EXTRA else 31)
def text(c,x,y,s,col=TEXT,scale=1):
    for i,ch in enumerate(s):
        k=glyph_index(ch); gx,gy=(k%16)*6,(k//16)*10
        for yy in range(10):
            for xx in range(6):
                if FONT.getpixel((gx+xx,gy+yy))[3]:
                    for sy in range(scale):
                        for sx in range(scale): P(c,x+(i*6+xx)*scale+sx,y+yy*scale+sy,col)
def text_w(s,scale=1): return len(s)*6*scale
def chrome(c,x0,y0,w,h):
    """Widget frame: 1-px steel border (lit top/left step 5, shaded bottom/right step 3), field step 2 inside."""
    rect(c,x0,y0,w,h,S[2])
    for x in range(x0,x0+w): P(c,x,y0,S[5]); P(c,x,y0+h-1,S[3])
    for y in range(y0,y0+h): P(c,x0,y,S[5]); P(c,x0+w-1,y,S[3])
def status_ramp(frac): return STATUS[0 if frac<0.5 else 1 if frac<0.75 else 2 if frac<0.99 else 3]
def gauge(c,x0,y0,w,h,frac,label=None,value=None,ramp=None):
    """Bar gauge: track (step 1 with a step-0 shadow line on top and a step-3 lip below), fill in status colours by level,
       two tones (lit top row, base body, shade bottom row); label above, value right."""
    ty=y0
    if label: text(c,x0,y0,label,MUTED); ty=y0+10
    if value: text(c,x0+w-text_w(value),y0,value,TEXT)
    rect(c,x0,ty,w,h,S[1])
    for x in range(x0,x0+w): P(c,x,ty,S[0]); P(c,x,ty+h-1,S[3])
    fw=int((w-2)*max(0,min(1,frac))); r=ramp or status_ramp(frac)
    for yy in range(1,h-1):
        for xx in range(1,1+fw): P(c,x0+xx,ty+yy,r[0] if yy==1 else r[2] if yy==h-2 else r[1])
def graph(c,x0,y0,w,h,series,col=LB,kind='line',label=None,unit=None):
    """Line / bar graph: chrome, dotted grid every 8 px (step 3), axes (step 6) bottom and left, the series scaled to the
       area; line in the light-blue dye ramp (base, lit sample points), bars two-tone."""
    chrome(c,x0,y0,w,h)
    for gy in range(y0+h-2,y0,-8):
        for x in range(x0+2,x0+w-1,2): P(c,x,gy,S[3])
    for x in range(x0+1,x0+w-1): P(c,x,y0+h-2,S[6])
    for y in range(y0+1,y0+h-1): P(c,x0+1,y,S[6])
    n=len(series); lo,hi=min(series),max(series); span=(hi-lo) or 1
    top=12 if label else 3
    pts=[(x0+2+int(i*(w-4)/(n-1)),y0+h-3-int((v-lo)/span*(h-3-top))) for i,v in enumerate(series)]
    if kind=='bar':
        bw=max(1,(w-4)//n-1)
        for (x,y) in pts:
            for yy in range(y,y0+h-2):
                for xx in range(bw): P(c,x+xx,yy,col[4] if yy==y else col[3] if xx<bw-1 else col[2])
    else:
        for (a,b) in zip(pts,pts[1:]):
            steps=max(abs(b[0]-a[0]),abs(b[1]-a[1]),1)
            for k in range(steps+1): P(c,round(a[0]+(b[0]-a[0])*k/steps),round(a[1]+(b[1]-a[1])*k/steps),col[3])
        for (x,y) in pts[::max(1,n//8)]: P(c,x,y,col[6])
    if label: text(c,x0+3,y0+2,label,MUTED)
def list_rows(c,x0,y0,w,rows,row_h=11):
    """Rows: alternating step 1 / 2 background, a 3x3 status dot (online mint / offline step 5 / fault red), text."""
    for i,(dot,s,v) in enumerate(rows):
        y=y0+i*row_h; rect(c,x0,y,w,row_h,S[2] if i%2 else S[1])
        col={'on':ACCENT,'off':S[5],'warn':H('#E8C24A'),'fault':H('#E5483C')}[dot]
        rect(c,x0+2,y+4,3,3,col); P(c,x0+2,y+4,tuple(min(255,v_+50) for v_ in col))
        text(c,x0+8,y+1,s,TEXT if dot!='off' else MUTED)
        if v: text(c,x0+w-text_w(v)-2,y+1,v,MUTED)
def item_icon(c,x,y,path,scale=1):
    im=Image.open(path).convert('RGBA')
    for yy in range(16):
        for xx in range(16):
            p=im.getpixel((xx,yy))
            if p[3]>128:
                for sy in range(scale):
                    for sx in range(scale): P(c,x+xx*scale+sx,y+yy*scale+sy,p[:3])
PAL16=[S[0],S[2],S[4],S[6],S[8],S[10],LB[1],LB[3],LB[5],OR[2],OR[4],YE[3],H('#3CE05A'),H('#22A03C'),RD[3],H('#6A4A2A')]
def image(c,x0,y0,w,h,src,dither=True,colors=256):
    """Scale an image into a region. colors: 16 = the fixed panel palette (PAL16); 64 / 256 = an adaptive palette picked
       for this image (median cut); 'full' = 24-bit colour. Dithered modes use an ordered 4x4 Bayer dither (stable from frame
       to frame - no flicker); full colour and dither=False use nearest-neighbour."""
    im=Image.open(src).convert('RGB')
    if colors=='full' or not dither:
        im=im.resize((w,h),Image.BILINEAR if colors=='full' else Image.NEAREST)
        for y in range(h):
            for x in range(w): P(c,x0+x,y0+y,im.getpixel((x,y)))
        return
    im=im.resize((w,h),Image.BILINEAR)
    pal=PAL16 if colors==16 else [tuple(im.quantize(colors=colors,method=Image.Quantize.MEDIANCUT).getpalette()[i*3:i*3+3]) for i in range(colors)]
    B=[[0,8,2,10],[12,4,14,6],[3,11,1,9],[15,7,13,5]]; amp=48 if colors==16 else 24 if colors==64 else 12
    cache={}
    for y in range(h):
        for x in range(w):
            r,g,b=im.getpixel((x,y)); t=(B[y%4][x%4]/16-0.5)*amp
            key=(int(r+t)>>2,int(g+t)>>2,int(b+t)>>2)
            if key not in cache: rr,gg,bb=key[0]<<2,key[1]<<2,key[2]<<2; cache[key]=min(pal,key=lambda q:(q[0]-rr)**2+(q[1]-gg)**2+(q[2]-bb)**2)
            P(c,x0+x,y0+y,cache[key])
def test_pattern(c):
    """Booting: colour bars (status/dye ramps) over a steel step ramp, then the system name."""
    W,Hh=c.size; bars=[S[10],YE[3],LB[3],H('#3CE05A'),OR[3],RD[3],H('#494EB6'),S[1]]
    for i,col in enumerate(bars): rect(c,i*W//8,0,W//8+1,Hh*2//3,col)
    for i in range(11): rect(c,i*W//11,Hh*2//3,W//11+1,Hh//3,S[i])
def no_signal(c):
    W,Hh=c.size; rect(c,0,0,W,Hh,S[0]); s='NO SIGNAL'; sc=2 if W>=text_w(s,2)+8 else 1
    text(c,(W-text_w(s,sc))//2,(Hh-10*sc)//2,s,H('#E8C24A'),sc)
    rect(c,(W-text_w(s,sc))//2,(Hh+10*sc)//2+2,text_w(s,sc),1,S[4])
