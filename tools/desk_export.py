# Exporter: python tools/desk_export.py (from the project root)
import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import desk_tex as T, desk_models as M
A='src/main/resources/assets/encodedlogistics/'; RL=M.RL
def save(im,p): os.makedirs(os.path.dirname(A+p),exist_ok=True); im.save(A+p)
def jd(o,p): os.makedirs(os.path.dirname(A+p),exist_ok=True); json.dump(o,open(A+p,'w'),indent=1)
def mc(ft,w=None,h=None,interp=False,frames=None):
    a={'frametime':ft}
    if w: a['width']=w; a['height']=h
    if interp: a['interpolate']=True
    if frames: a['frames']=frames
    return json.dumps({'animation':a},indent=2)+'\n'
D='textures/block/terminal_desk/'
save(T.laminate(),D+'laminate.png'); save(T.steel(),D+'steel.png'); save(T.pedestal(),D+'pedestal.png'); save(T.modesty(),D+'modesty.png')
save(T.crt_case(),D+'crt_case.png'); save(T.keyboard(),D+'keyboard.png'); save(T.clutter(),D+'clutter.png')
save(T.lamp(False),D+'lamp_off.png'); save(T.lamp(True),D+'lamp_on.png')
save(T.crt_screen('off'),D+'crt_screen_off.png')
save(T.crt_screen('boot',12),D+'crt_screen_boot.png'); open(A+D+'crt_screen_boot.png.mcmeta','w').write(mc(5,36,28))
# crt_screen_on.png: tools/terminal_screens.py (the terminals' style, 10 x 8)
C='textures/block/swivel_chair/'
save(T.chair_fabric(),C+'fabric.png'); save(T.chair_metal(),C+'metal.png'); save(T.chair_caster(),C+'caster.png')
save(T.desk_icon(),'textures/item/terminal_desk.png'); save(T.chair_icon(),'textures/item/swivel_chair.png')
jd(M.desk(),'models/block/terminal_desk/desk.json')
for s in ('off','boot','on'): jd(M.terminal(s),f'models/block/terminal_desk/terminal_{s}.json')
jd(M.keyboard(),'models/block/terminal_desk/keyboard.json'); jd(M.clutter(),'models/block/terminal_desk/clutter.json')
jd({'parent':'minecraft:block/block','textures':{'particle':RL('block/terminal_desk/laminate')},'elements':[]},'models/block/terminal_desk/empty.json')
jd(M.chair_base(),'models/block/swivel_chair/base.json'); jd(M.chair_seat(),'models/block/swivel_chair/seat.json')
rot={'north':0,'east':90,'south':180,'west':270}
mp=[]
for fc,y in rot.items():
    w={'facing':fc,'part':'master'}; R={'y':y} if y else {}
    mp.append({'when':w,'apply':{'model':RL('block/terminal_desk/desk'),**R}})
    mp.append({'when':w,'apply':{'model':RL('block/terminal_desk/keyboard'),**R}})
    for s in ('off','boot','on'): mp.append({'when':{**w,'screen':s},'apply':{'model':RL(f'block/terminal_desk/terminal_{s}'),**R}})
    mp.append({'when':{**w,'clutter':'true'},'apply':{'model':RL('block/terminal_desk/clutter'),**R}})
jd({'multipart':mp+[{'when':{'part':'dummy'},'apply':{'model':RL('block/terminal_desk/empty')}}]},'blockstates/terminal_desk.json')
jd({'variants':{'':{'model':RL('block/swivel_chair/base')}}},'blockstates/swivel_chair.json')
for n in ('terminal_desk','swivel_chair'):
    jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{n}')}},f'models/item/{n}.json')
jd({'model':{'type':'minecraft:model','model':RL('item/terminal_desk')}},'items/terminal_desk.json')
jd({'model':{'type':'minecraft:model','model':RL('item/swivel_chair'),'tints':[{'type':'minecraft:dye','default':-1}]}},'items/swivel_chair.json')
print('files',sum(len(f) for _,_,f in os.walk('src')))
