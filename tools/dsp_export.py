import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import dsp_tex as T
from mr2_models import el, F, GLOW, shapes, RL
A_='src/main/resources/assets/encodedlogistics/'
def save(im,p): os.makedirs(os.path.dirname(A_+p),exist_ok=True); im.save(A_+p)
def jd(o,p): os.makedirs(os.path.dirname(A_+p),exist_ok=True); json.dump(o,open(A_+p,'w'),indent=1)
def mc(p,ft): open(A_+p+'.mcmeta','w').write(json.dumps({'animation':{'frametime':ft}},indent=2)+'\n')
def model(tx,E,rt='minecraft:cutout'):
    return {'parent':'minecraft:block/block','render_type':rt,'textures':{**{k:RL(v) for k,v in tx.items()},'particle':RL(list(tx.values())[0])},
            'elements':[{k:v for k,v in e.items() if k!='_solid'} for e in E]}
def box(n,a,b,m,solid=True,**k):
    fs={d:F(*m.get(d,m.get('*'))) for d in ('north','south','east','west','up','down') if m.get(d,m.get('*'))}
    return el(n,a,b,fs,solid,**k)
P='block/display_panel/'; FULL=[0,0,16,16]
for n,fn in (('bezel',T.bezel),('glass_off',T.glass_off),('side',T.side),('back',T.back),('bottom',T.bottom)): save(fn(),f'textures/{P}{n}.png')
for st in ('boot','online','nosignal'):
    g,b=T.led(st); save(g,f'textures/{P}led_{st}.png'); (mc(f'textures/{P}led_{st}.png',10 if st=='nosignal' else 4) if b else None)
# Display Panel (facing north: screen toward -z; 8 thick, flush to the wall at z 16). Pieces, so merged panels drop the
# bezel on joined edges: base (glass + body + back), bezel_top/bottom/left/right (1 px strips, 0.5 proud of the glass).
G=('#glass',FULL)
base=[box('glass',(0,0,8),(16,16,8.5),{'north':G}),
      box('body',(0,0,8.5),(16,16,15),{'east':('#side',[0,0,6.5,16]),'west':('#side',[0,0,6.5,16]),'up':('#side',[0,0,16,6.5]),'down':('#bottom',[0,0,16,6.5])}),
      box('plate',(1,1,15),(15,15,16),{'south':('#back',FULL),'*':('#side',[0,0,16,1])})]
edges={'top':((0,15,7.5),(16,16,8.5)),'bottom':((0,0,7.5),(16,1,8.5)),'left':((15,0,7.5),(16,16,8.5)),'right':((0,0,7.5),(1,16,8.5))}   # left/right as seen from the front
tx={'glass':P+'glass_off','side':P+'side','back':P+'back','bottom':P+'bottom','bezel':P+'bezel'}
jd(model(tx,base),'models/block/display_panel/base.json')
for n,(a,b) in edges.items():
    jd(model({'bezel':P+'bezel'},[box('bezel_'+n,a,b,{'*':('#bezel',[1,6,15,9])})]),f'models/block/display_panel/bezel_{n}.json')
jd(model({'led':P+'led_online'},[box('led',(0,0,7.49),(16,16,7.49),{'north':('#led',FULL)},False,**GLOW)]),'models/block/display_panel/led_online.json')
for st in ('boot','nosignal'): jd(model({'led':P+f'led_{st}'},[box('led',(0,0,7.49),(16,16,7.49),{'north':('#led',FULL)},False,**GLOW)]),f'models/block/display_panel/led_{st}.json')
ROT={'north':{},'east':{'y':90},'south':{'y':180},'west':{'y':270}}
mp=[]
for fc,r in ROT.items():
    mp.append({'when':{'facing':fc},'apply':{'model':RL('block/display_panel/base'),**r}})
    for n in edges: mp.append({'when':{'facing':fc,n:'false'},'apply':{'model':RL(f'block/display_panel/bezel_{n}'),**r}})   # true = joined to a neighbour on that edge
    for st in ('boot','online','nosignal'): mp.append({'when':{'facing':fc,'led':'true','state':st},'apply':{'model':RL(f'block/display_panel/led_{st}'),**r}})
jd({'multipart':mp},'blockstates/display_panel.json')
SH={'display_panel':shapes(base,[(0,0,0)])}
# Small Wireless Bridge (facing = toward the machine face it sits on, -z): 6 x 6 x 3 box + stubby antenna + LED
Q='block/small_wireless_bridge/'
save(T.swb_face(),f'textures/{Q}face.png'); save(T.swb_side(),f'textures/{Q}side.png')
for st in ('unlinked','linked','fault'):
    g,b=T.swb_led(st); save(g,f'textures/{Q}led_{st}.png'); (mc(f'textures/{Q}led_{st}.png',10) if b else None)
swb=[box('body',(5,5,0),(11,11,3),{'south':('#face',FULL),'north':('#side',FULL),'*':('#side',[0,0,16,8])}),
     box('antenna',(9.5,11,1),(10.5,13.5,2),{'*':('#side',[0,0,2,6])},False)]
jd(model({'face':Q+'face','side':Q+'side'},swb),'models/block/small_wireless_bridge/small_wireless_bridge.json')
for st in ('unlinked','linked','fault'):
    jd(model({'led':Q+f'led_{st}'},[box('led',(5,5,3.01),(11,11,3.01),{'south':('#led',FULL)},False,**GLOW)]),f'models/block/small_wireless_bridge/led_{st}.json')
F6={'north':{},'south':{'y':180},'west':{'y':270},'east':{'y':90},'up':{'x':270},'down':{'x':90}}
mp=[]
for fc,r in F6.items():
    mp.append({'when':{'facing':fc},'apply':{'model':RL('block/small_wireless_bridge/small_wireless_bridge'),**r}})
    for st in ('unlinked','linked','fault'): mp.append({'when':{'facing':fc,'state':st},'apply':{'model':RL(f'block/small_wireless_bridge/led_{st}'),**r}})
jd({'multipart':mp},'blockstates/small_wireless_bridge.json'); SH['small_wireless_bridge']=shapes(swb,[(0,0,0)])
for n,im in (('display_panel',T.icon_panel()),('small_wireless_bridge',T.icon_swb())):
    save(im,f'textures/item/{n}.png'); jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{n}')}},f'models/item/{n}.json')
    jd({'model':{'type':'minecraft:model','model':RL(f'item/{n}')}},f'items/{n}.json')
os.makedirs('shapes',exist_ok=True); json.dump(SH,open('shapes/collision_shapes.json','w'),indent=1)
print('files',sum(len(f) for _,_,f in os.walk('src')))
