import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import w2_tex as T
from mr2_models import el, F, GLOW, shapes, RL
A_='src/main/resources/assets/encodedlogistics/'; D_='src/main/resources/data/encodedlogistics/'
def save(im,p): os.makedirs(os.path.dirname(A_+p),exist_ok=True); im.save(A_+p)
def jd(o,p,root=A_): os.makedirs(os.path.dirname(root+p),exist_ok=True); json.dump(o,open(root+p,'w'),indent=1)
def mc(p,ft): open(A_+p+'.mcmeta','w').write(json.dumps({'animation':{'frametime':ft}},indent=2)+'\n')
def model(tx,E,rt='minecraft:cutout'):
    return {'parent':'minecraft:block/block','render_type':rt,'textures':{**{k:RL(v) for k,v in tx.items()},'particle':RL(list(tx.values())[0])},
            'elements':[{k:v for k,v in e.items() if k!='_solid'} for e in E]}
def box(n,a,b,m,solid=True,**k):
    fs={}
    for d in ('north','south','east','west','up','down'):
        v=m.get(d,m.get('*'))
        if v: fs[d]=F(v[0],v[1])
    return el(n,a,b,fs,solid,**k)
P='block/wireless/'; SH={}
FACE6={'up':{},'down':{'x':180},'north':{'x':90},'south':{'x':90,'y':180},'west':{'x':90,'y':270},'east':{'x':90,'y':90}}   # for 'puck on top' models
FACING={'north':{},'south':{'y':180},'west':{'y':270},'east':{'y':90},'up':{'x':270},'down':{'x':90}}                     # for 'faces -z' models
# textures
for n,fn in (('casing',T.casing),('ap_top',T.ap_top),('puck_top',T.puck_top),('puck_side',T.puck_side),('bridge_side',T.bridge_side),('bridge_top',T.bridge_top),
             ('mast',T.mast),('port_front',T.port_front)): save(fn(),f'textures/{P}{n}.png')
for k in ('ingress','egress'): save(T.port_back(k),f'textures/{P}port_{k}_back.png'); save(T.port_side(k),f'textures/{P}port_{k}_side.png')
for st in ('linking','online','fault'):
    g,b=T.ring_glow(st); save(g,f'textures/{P}ap_ring_{st}.png'); (mc(f'textures/{P}ap_ring_{st}.png',10) if b else None)
    g,b=T.led_glow(st); save(g,f'textures/{P}port_led_{st}.png'); (mc(f'textures/{P}port_led_{st}.png',10) if b else None)
for st in ('linking','online','active','fault'):
    g,b=T.window_glow(st); save(g,f'textures/{P}bridge_window_{st}.png'); (mc(f'textures/{P}bridge_window_{st}.png',4 if st=='active' else 10) if b else None)
    save(T.tip_glow(st),f'textures/{P}bridge_tip_{st}.png')
FULL=[0,0,16,16]
# ---- Access Point: casing body 16 x 13 x 16 (cables connect to its full sides and bottom) + puck (stepped disc, 10
#      across, 2.5 tall) on its top face; facing = the face the puck is on (any of 6) ----
C=('#casing',FULL)
ap=[box('body',(0,0,0),(16,13,16),{'up':('#top',FULL),'*':('#casing',[0,3,16,16]),'down':C}),
    box('puck_a',(3,13,5),(13,15.5,11),{'up':('#puck',[3,5,13,11]),'*':('#puckside',[0,0,16,2.5])}),
    box('puck_b',(5,13,3),(11,15.5,13),{'up':('#puck',[5,3,11,13]),'*':('#puckside',[0,0,16,2.5])}),
    box('puck_c',(4,13,4),(12,15.5,12),{'up':('#puck',[4,4,12,12]),'*':('#puckside',[0,0,16,2.5])})]
ring=[box('ring',(3,15.51,3),(13,15.51,13),{'up':('#ring',[3,3,13,13])},False,**GLOW)]
tx={'casing':P+'casing','top':P+'ap_top','puck':P+'puck_top','puckside':P+'puck_side'}
jd(model(tx,ap),'models/block/wireless/access_point.json'); SH['access_point']=shapes(ap,[(0,0,0)])
for st in ('linking','online','fault'): jd(model({'ring':P+f'ap_ring_{st}'},ring),f'models/block/wireless/access_point_{st}.json')
mp=[]
for fc,r in FACE6.items():
    mp.append({'when':{'facing':fc},'apply':{'model':RL('block/wireless/access_point'),**r}})
    for st in ('linking','online','fault'): mp.append({'when':{'facing':fc,'state':st},'apply':{'model':RL(f'block/wireless/access_point_{st}'),**r}})
jd({'multipart':mp},'blockstates/access_point.json')
# ---- Wireless Bridge: casing body 16 x 10 x 16 + mast (2 x 6) on top; window + tip glow ----
br=[box('body',(0,0,0),(16,10,16),{'up':('#top',FULL),'down':C,'*':('#side',[0,6,16,16])}),
    box('mast',(7,10,7),(9,15,9),{'*':('#mast',[0,0,4,10]),'up':('#mast',[0,0,4,4])}),
    box('tip',(6.5,15,6.5),(9.5,16,9.5),{'*':('#mast',[0,0,6,2])})]
glow=[box('window',(-0.01,0,-0.01),(16.01,10,16.01),{d:('#win',[0,6,16,16]) for d in ('north','south','east','west')},False,**GLOW),
      box('tip_glow',(6.49,15,6.49),(9.51,16.01,9.51),{'*':('#tip',[0,0,6,2]),'up':('#tip',[0,0,6,6])},False,**GLOW)]
jd(model({'side':P+'bridge_side','top':P+'bridge_top','casing':P+'casing','mast':P+'mast'},br),'models/block/wireless/wireless_bridge.json'); SH['wireless_bridge']=shapes(br,[(0,0,0)])
for st in ('linking','online','active','fault'): jd(model({'win':P+f'bridge_window_{st}','tip':P+f'bridge_tip_{st}'},glow),f'models/block/wireless/wireless_bridge_{st}.json')
jd({'multipart':[{'apply':{'model':RL('block/wireless/wireless_bridge')}}]+[{'when':{'state':st},'apply':{'model':RL(f'block/wireless/wireless_bridge_{st}')}} for st in ('linking','online','active','fault')]},'blockstates/wireless_bridge.json')
# ---- Wireless Ingress / Egress: QIO-style panel against the inventory it faces (facing = toward the inventory, -z) ----
pm=lambda k:[box('panel',(2,2,0),(14,14,4),{'north':('#front',[2,2,14,14]),'south':('#back',FULL),'*':('#side',[0,0,16,4])}),
             box('nub',(11.5,14,2),(12.5,16,3),{'*':('#mast',[0,0,2,4])},False)]
led=[box('led',(2,2,4.01),(14,14,4.01),{'south':('#led',FULL)},False,**GLOW)]
for k in ('ingress','egress'):
    b=f'wireless_{k}_port'; E=pm(k)
    jd(model({'front':P+'port_front','back':P+f'port_{k}_back','side':P+f'port_{k}_side','mast':P+'mast'},E),f'models/block/wireless/{b}.json')
    for st in ('linking','online','fault'): jd(model({'led':P+f'port_led_{st}'},led),f'models/block/wireless/{b}_{st}.json')
    mp=[]
    for fc,r in FACING.items():
        mp.append({'when':{'facing':fc},'apply':{'model':RL(f'block/wireless/{b}'),**r}})
        for st in ('linking','online','fault'): mp.append({'when':{'facing':fc,'state':st},'apply':{'model':RL(f'block/wireless/{b}_{st}'),**r}})
    jd({'multipart':mp},f'blockstates/{b}.json')
SH['wireless_port']=shapes(pm('ingress'),[(0,0,0)])
# ---- items ----
icons={'access_point':T.icon_ap(),'wireless_bridge':T.icon_bridge(),'wireless_ingress_port':T.icon_port('ingress'),'wireless_egress_port':T.icon_port('egress'),'radio_module':T.icon_radio()}
for n,im in icons.items():
    save(im,f'textures/item/{n}.png'); jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{n}')}},f'models/item/{n}.json')
    jd({'model':{'type':'minecraft:model','model':RL(f'item/{n}')}},f'items/{n}.json')
os.makedirs('shapes',exist_ok=True); json.dump(SH,open('shapes/collision_shapes.json','w'),indent=1)
print('files',sum(len(f) for _,_,f in os.walk('src')))
