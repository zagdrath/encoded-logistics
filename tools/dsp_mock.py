import sys,os,math,random; sys.path.insert(0,'/home/claude/dsp/tools')
from dsp_canvas import *
from PIL import Image
OUT='/home/claude/dsp/previews/'; os.makedirs(OUT,exist_ok=True)
ITEM='/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/item/'
def wave(n,seed,base=50,amp=30):
    r=random.Random(seed); v=base; out=[]
    for i in range(n): v+=r.uniform(-amp/4,amp/4)+math.sin(i/6)*amp/10; v=max(5,min(100,v)); out.append(v)
    return out
def framed(c,bw,bh,scale=3):
    """The merged screen as seen: canvas inside the bezel (1 model px = 2 canvas px), status LED bottom-right."""
    b=2; W,Hh=c.size; f=Image.new('RGB',(W+2*b,Hh+2*b),S[4])
    for x in range(f.width): f.putpixel((x,0),S[6]); f.putpixel((x,f.height-1),S[2])
    for y in range(f.height): f.putpixel((0,y),S[6]); f.putpixel((f.width-1,y),S[2])
    f.paste(c,(b,b)); f.putpixel((f.width-5,f.height-1),LB[6]); f.putpixel((f.width-4,f.height-1),LB[3])
    return f.resize((f.width*scale,f.height*scale),Image.NEAREST)
shots={}
# 1x1 - text and a gauge
c=canvas(1,1); text(c,1,1,'LANES',MUTED); text(c,1,11,'12/32',TEXT); gauge(c,1,24,30,5,12/32); shots['1x1_text_gauge']=framed(c,1,1,6)
c=canvas(1,1); text(c,1,1,'14:32',ACCENT); item_icon(c,8,12,ITEM+'logic_die.png'); text(c,1,22,'1.2K',TEXT) if False else None; shots['1x1_clock_item']=framed(c,1,1,6)
# 3x2 - dashboard
c=canvas(3,2); text(c,2,1,'ELNET01',ACCENT); text(c,94-text_w('14:32'),1,'14:32',MUTED)
gauge(c,2,12,92,5,0.62,'HOT','62%'); gauge(c,2,29,92,5,0.31,'COLD','31%',ramp=(LB[5],LB[3],LB[1])); gauge(c,2,46,42,5,0.84,'FE','84',ramp=(YE[5],YE[3],YE[1])); gauge(c,52,46,42,5,12/32,'LN','12')
shots['3x2_dashboard']=framed(c,3,2); c.save('/home/claude/dsp/previews/raw_3x2.png')
c=canvas(3,2); graph(c,1,1,94,62,wave(60,3),label='ITEMS/MIN'); text(c,94-text_w('846'),2,'846',TEXT); shots['3x2_graph']=framed(c,3,2)
c=canvas(3,2); graph(c,1,1,94,62,wave(24,5),kind='bar',col=[H('#1C6E07'),H('#398F11'),H('#5DB11E'),H('#87D32E'),H('#ADF54E'),H('#C3FF6B'),H('#D1FF84')],label='JOBS/MIN'); shots['3x2_bars']=framed(c,3,2)
c=canvas(3,2); test_pattern(c); shots['3x2_booting']=framed(c,3,2)
c=canvas(3,2); no_signal(c); shots['3x2_no_signal']=framed(c,3,2)
c=canvas(3,2); text(c,2,2,'STORAGE 91%',H('#E8C24A')); text(c,2,14,'LANES  12/32',TEXT); text(c,2,26,'POWER  UPS',TEXT); text(c,2,38,'JOBS   3',TEXT); text(c,2,50,'NOCWALL 14:32',MUTED); shots['3x2_text']=framed(c,3,2)
# 8x6 - split regions: graph (left 5x4), text + image (right), status list + counter + clock (bottom)
c=canvas(8,6)
graph(c,1,1,158,126,wave(120,9,60,40),label='ENERGY DRAW  FE/T'); text(c,158-text_w('1,840'),3,'1,840',TEXT)
text(c,164,2,'ELNET01',ACCENT,2); text(c,164,24,'STORAGE',MUTED); gauge(c,164,34,90,5,0.91); text(c,164,44,'LANES',MUTED); gauge(c,164,54,90,5,12/32)
image(c,162,64,94,62,'/home/claude/brand/out/banner_1920x640.png',True)
list_rows(c,1,130,158,[('on','AP01','6/8'),('on','WBRIDGE01','32'),('warn','UPS01','BATT'),('fault','WEGRESS01','LINK'),('off','DSP02','')],11)
item_icon(c,164,132,ITEM+'logic_die.png',2); text(c,200,136,'1.2K',TEXT,2); text(c,200,160,'LOGIC DIE',MUTED)
text(c,164,174,'14:32:07',ACCENT); text(c,256-text_w('DAY 2')-2,174,'DAY 2',MUTED)
shots['8x6_split']=framed(c,8,6,2)
for k,im in shots.items(): im.save(OUT+f'canvas_{k}.png')
print('ok', list(shots))
