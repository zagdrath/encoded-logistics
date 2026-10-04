# Exporter for rack device batch 2. Run from the project root: python tools/export2.py
import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import devices2 as D, rack_models as RM
A='src/main/resources/assets/encodedlogistics/'; T=A+'textures/'; M=A+'models/'; RL=lambda p:'encodedlogistics:'+p
def save(im,p): os.makedirs(os.path.dirname(p),exist_ok=True); im.save(p)
def jd(o,p): os.makedirs(os.path.dirname(p),exist_ok=True); json.dump(o,open(p,'w'),indent=1)
def mc(ft): return '{\n  "animation": {\n    "frametime": %d\n  }\n}\n'%ft
for n,(u,base,glow) in D.DEVICES.items():
    P=T+'block/rack_device/'
    save(base(),P+f'{n}.png')
    on=Image.new('RGBA',(128,512),(0,0,0,0))                               # activity LEDs: 4 frames
    for f in range(4): on.alpha_composite(glow(f,'on'),(0,128*f))
    save(on,P+f'{n}_on.png'); open(P+f'{n}_on.png.mcmeta','w').write(mc(3))
    fa=Image.new('RGBA',(128,256),(0,0,0,0)); fa.alpha_composite(glow(0,'fault'),(0,0))    # fault blink: 2 frames
    save(fa,P+f'{n}_fault.png'); open(P+f'{n}_fault.png.mcmeta','w').write(mc(10))
    jd(RM.device_model(n,u),M+f'block/rack_device/{n}.json'); jd(RM.device_model(n,u,'on'),M+f'block/rack_device/{n}_on.json')
    jd(RM.device_model(n,u,'fault'),M+f'block/rack_device/{n}_fault.json')
    save(D.icon(n),T+f'item/{n}.png')
    jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{n}')}},M+f'item/{n}.json')
    jd({'model':{'type':'minecraft:model','model':RL(f'item/{n}')}},A+f'items/{n}.json')
print('files',sum(len(f) for _,_,f in os.walk('src')))
