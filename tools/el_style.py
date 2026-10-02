# Shared Encoded Logistics texture helpers - implements docs/TEXTURE_STYLE.md.
import random, colorsys
from PIL import Image
def H(s): return tuple(int(s[i:i+2],16) for i in (1,3,5))
G=[H(c) for c in ['#1F2228','#2B2F36','#373C44','#454B54','#555B65','#666D77','#79808A','#8D949D','#A3A9B1','#BBC0C6','#D3D7DB']]
def g(i): return G[max(0,min(10,i))]
DYE_BASE={'orange':'#EC7F27','magenta':'#D85BCC','light_blue':'#50C2EC','yellow':'#ECC138','lime':'#87D32E','pink':'#EC95B1',
          'cyan':'#27A2AA','purple':'#913FC6','blue':'#494EB6','green':'#6A852B','red':'#BA3B37'}
COLOURS=['neutral','white','orange','magenta','light_blue','yellow','lime','pink','cyan','purple','blue','green','red']
def dye_ramp(base):
    """7 tones (-3..+3): +/-16% value; shadows cooler + more saturated, highlights warmer + softer (guide 2.2)."""
    if isinstance(base,str): base=H(base)
    h,s,v=colorsys.rgb_to_hsv(*[x/255 for x in base]); out=[]
    for k in range(-3,4):
        vv=min(1,max(0.06,v*(1+0.16*k))); ss=min(1,max(0,s+(-0.10*k if k>0 else -0.05*k)))
        target=0.66 if k<0 else 0.14; d=((target-h+0.5)%1)-0.5; hh=(h+d*0.045*abs(k))%1
        r,gg,b=colorsys.hsv_to_rgb(hh,ss,vv); out.append((round(r*255),round(gg*255),round(b*255)))
    return out
FE=[H(c) for c in ['#8E231C','#B8342A','#E5483C','#FF8577']]           # energy red (matches the Arcforge bar)
AMBER=[H(c) for c in ['#6B4208','#9A6410','#D8941C','#F5B23A','#FFD27A']]
BRASS=[H(c) for c in ['#6E4F12','#9C7420','#C99A35','#E8C25A','#F8E39A']]
LED_GREEN=[H('#22A03C'),H('#3CE05A'),H('#B5FFB0')]
def img(w=16,h=16): return Image.new('RGBA',(w,h),(0,0,0,0))
def put(im,x,y,c,a=255):
    if 0<=x<im.width and 0<=y<im.height: im.putpixel((x,y),tuple(c)+(a,))
def speckle(seed,p_dark=0.10,p_light=0.07):
    r=random.Random(seed)
    def f(): x=r.random(); return -1 if x<p_dark else (1 if x<p_dark+p_light else 0)
    return f
def brushed(seed):
    r=random.Random(seed)
    def run():
        return r.randint(2,4), r.choices((-1,0,1),(0.20,0.68,0.12))[0]
    return run
def bevel_frame(im,x0,y0,w,h,seed=1,lip=True):
    """Guide 4: 1px outer rail (top/left 9, bottom/right 6, corners 10/7/5) + inner lip. Clean: no random wear specks
       (guide 3); seed is kept for call compatibility."""
    for a in range(w):
        put(im,x0+a,y0,g(9)); put(im,x0+a,y0+h-1,g(6))
    for a in range(h):
        put(im,x0,y0+a,g(9)); put(im,x0+w-1,y0+a,g(6))
    put(im,x0,y0,g(10)); put(im,x0+w-1,y0,g(7)); put(im,x0,y0+h-1,g(7)); put(im,x0+w-1,y0+h-1,g(5))
    if lip:
        for a in range(1,w-1): put(im,x0+a,y0+1,g(2)); put(im,x0+a,y0+h-2,g(5))
        for a in range(1,h-1): put(im,x0+1,y0+a,g(2)); put(im,x0+w-2,y0+a,g(5))
        put(im,x0+w-2,y0+1,g(3)); put(im,x0+1,y0+h-2,g(3))
def field(im,x0,y0,w,h,base,seed=None):
    """Guide 3: a flat steel field at one ramp step - no random speckle (seed kept for call compatibility)."""
    for y in range(y0,y0+h):
        for x in range(x0,x0+w): put(im,x,y,g(base))
def port_ring(im,cx,cy,seed=0):
    """Cable attach point: 6x6 raised ring (lit top-left) around a 4x4 recess with a shadow line under the lip."""
    x0,y0=cx-3,cy-3
    for a in range(6):
        put(im,x0+a,y0,g(8)); put(im,x0,y0+a,g(8)); put(im,x0+a,y0+5,g(5)); put(im,x0+5,y0+a,g(5))
    put(im,x0+5,y0,g(6)); put(im,x0,y0+5,g(6))
    for y in range(y0+1,y0+5):
        for x in range(x0+1,x0+5): put(im,x,y,g(1 if y==y0+1 else 2))
    put(im,x0+2,y0+3,g(3)); put(im,x0+3,y0+2,g(3))
def network_casing(seed,port=True):
    im=img(); field(im,0,0,16,16,4,seed); bevel_frame(im,0,0,16,16,seed)
    for (x,y) in ((3,3),(12,3),(3,12),(12,12)): put(im,x,y,g(8)); put(im,x+1,y+1,g(1))   # raised screws
    if port: port_ring(im,8,8,seed)
    return im
