# Sirens as beacon lights (the reference photo) + the author's caged light in the mod's silver, 1 px taller.
import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
from lsp import *
LENS={'green':'#80C71F','amber':'#F9801D','red':'#B02E26'}   # the lamps' dye colours: lime, orange, red (DyeColor diffuse)
def lens_tex(name,lit):
    """Ribbed lens coloured like the lamps' bulbs (steel-white greys x the dye colour): ribs = alternate rows one step apart.
       The highlight STRIPE (3 texels per 6-texel facet) is shaded: brightest in its middle column, one step down at its edges,
       following the ribs; a shade step on the columns either side of it. Everything else as before."""
    c=H(LENS[name]); im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            col=x%6; rib=y%2==0
            base=(9 if rib else 8) if lit else (4 if rib else 3)
            if col==2: k=base+2                                                  # stripe centre
            elif col in (1,3): k=base+1                                          # stripe edges
            elif col in (0,4): k=base-1                                          # shade beside the stripe
            else: k=base
            k=max(0,min(10,k)); g=S[k]; px(im,x,y,tuple(int(g[i]*c[i]/255) for i in range(3)))
    return im
def sounder_tex():
    """The siren sounder (round base): dark steel with a ring of grille holes (step 0) on a 2-px grid, lit top edge (step 5)."""
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16): px(im,x,y,S[0] if (x%2==0 and y%2==1) else S[3] if (x+y)%5 else S[2])
    for x in range(16): px(im,x,0,S[5])
    return im
def sounder_top():
    """Sounder top, 16 x 16, sampled at the model's real x / z (the disc spans 3.5-12.5): stepped falloff from the top-left
       (steel 6 -> 3); the outer rim one step brighter on the lit half and one step darker on the far half; a darker ring
       (step 2) round the centre where the silver mount (x / z 6-10) sits."""
    import math
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            cx,cy=x+0.5-8,y+0.5-8; t=cx+cy; d=math.hypot(cx,cy)
            k=6 if t<-5 else 5 if t<-1 else 4 if t<3 else 3
            if d>3.6: k=k+1 if t<0 else k-1                                     # outer rim: lit half / far half
            elif d>2.2: k=2                                                     # shadow ring round the mount
            px(im,x,y,S[max(0,min(10,k))])
    return im
def black_tex():
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16): px(im,x,y,S[2] if x%6==1 else S[1] if x%6 in (0,2) else S[0])
    for x in range(16): px(im,x,0,S[3])
    return im
def silver_tex():
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16): px(im,x,y,(S[9] if y%2==0 else S[7]) if x%5!=4 else S[6])     # thread lines
    return im
def cyl(n,x0,x1,y0,y1,t,u):
    """A round-reading cylinder: two crossed boxes (a full-width and a narrower one), 1 texel / px."""
    w=x1-x0; inset=round(w*0.2*2)/2
    def b(a,bb,ww,dd): return {'name':n,'from':a,'to':bb,'faces':{'north':F(t,[0,0,ww,y1-y0]),'south':F(t,[0,0,ww,y1-y0]),'east':F(t,[0,0,dd,y1-y0]),'west':F(t,[0,0,dd,y1-y0]),'up':F(t,[0,0,ww,dd]),'down':F(t,[0,0,ww,dd])}}
    return [b([x0,y0,x0+inset],[x1,y1,x1-inset],w,w-2*inset),b([x0+inset,y0,x0],[x1-inset,y1,x1],w-2*inset,w)]
def siren():
    """Beacon light on its siren sounder (16 tall): a ROUND SOUNDER BASE 9 across (y 0-3, grille holes round its side), a silver
       threaded mount 4 across (3-4), a black hex stem 3 across (4-5.5), a black base 7 across (5.5-8.5), the ribbed lens 6 across
       (8.5-15) and a lens cap 4 across (15-16)."""
    snd=cyl('sounder',3.5,12.5,0,3,'#sounder',0)                                 # 9 across
    for e in snd: e['faces']['up']=F('#sounder_top',[e['from'][0],e['from'][2],e['to'][0],e['to'][2]])   # real x / z: continuous shading
    for e in snd:                                                               # lit outer rim on the top edge of the sides
        pass
    return (snd+cyl('mount',6,10,3,4,'#silver',0)+[{'name':'stem','from':[6.5,4,6.5],'to':[9.5,5.5,9.5],'faces':{d:F('#black',[0,0,3,1.5] if d not in ('up','down') else [0,0,3,3]) for d in ('north','south','east','west','up','down')}}]
            +cyl('base',4.5,11.5,5.5,8.5,'#black',0)+cyl('lens',5,11,8.5,15,'#lens',0)+cyl('cap',6,10,15,16,'#lens',0))
def siren_glow():
    E=cyl('lens',4.99,11.01,8.49,15.01,'#lens',0)+cyl('cap',5.99,10.01,14.99,16.0,'#lens',0)
    for e in E: e.update(GLOW)
    return E
# ---- caged light: the author's model, silver, 1 px taller ----
def to_silver(im):
    """The author's sheet onto the mod's SILVER steel (steps 5-9 by brightness); the white window kept for the tint."""
    out=im.copy(); p=out.load()
    lums=[0.299*r+0.587*g+0.114*b for (r,g,b,a) in im.getdata() if a and min(r,g,b)<=200]; lo,hi=min(lums),max(lums)
    for y in range(im.height):
        for x in range(im.width):
            r,g,b,a=p[x,y]
            if a==0 or min(r,g,b)>200: continue
            t=(0.299*r+0.587*g+0.114*b-lo)/max(1,hi-lo); p[x,y]=tuple(S[5+min(4,int(t*5))])+(a,)
    return out
def bulb_tex(lit):
    """32 x 32 greyscale bulb for tintindex 0, laid out for the author's bulb uvs (any region works): steel whites, a lighter
       vertical band, a highlight; lit one step brighter."""
    im=Image.new('RGBA',(32,32),(0,0,0,0))
    for y in range(32):
        for x in range(32):
            band=x%4 in (1,2); px(im,x,y,(S[10] if band else S[9]) if lit else (S[8] if band else S[7]))
    for (x,y) in ((5,5),(5,6),(1,5),(1,6),(9,5),(13,5)): px(im,x,y,(255,255,255))
    return im
def caged_light(lit):
    """The author's caged_light_off/on.json elements unchanged except: cage and bulb 1 px taller (cage y 1-8, bulb y 1-7.5), their
       uv crops extended by the same amount; textures in this mod's namespace; the on model's bulb emissive."""
    m=json.load(open('/mnt/user-data/uploads/caged_light_'+('on' if lit else 'off')+'.json'))
    E=m['elements']
    for e in E[:2]:                                                             # the two cage boxes: 2 px taller (y 1-9)
        e['to'][1]=9 if e['to'][1]==7 else e['to'][1]
        if e['from'][1]==7: e['from'][1]=9
        for d in ('north','east','south','west'):
            u=e['faces'][d]['uv']; e['faces'][d]['uv']=[u[0],u[1],u[2],u[3]+1.0]
    b=E[2]; b['to'][1]=8.5                                                      # bulb 2 px taller (y 1-8.5)
    for d in ('north','east','south','west'):
        u=b['faces'][d]['uv']; b['faces'][d]['uv']=[u[0],u[1],u[2],u[3]+1.0]
    if lit: b.update(GLOW)
    for e in E: e.pop('rotation',None)                                          # angle 0 rotations - no effect, dropped
    m['textures']={'3':RL('block/cage_light/caged_signal_light'),'4':RL('block/cage_light/bulb_'+('on' if lit else 'off')),'particle':RL('block/cage_light/caged_signal_light')}
    m.pop('texture_size',None); m['parent']='minecraft:block/block'
    return m
