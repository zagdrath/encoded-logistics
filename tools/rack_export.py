# Encoded Logistics - Server Rack + devices asset exporter. Run from the project root: python tools/rack_export.py
import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import rack_tex as RT, rack_models as RM, device_tex as DT, rack_items as RI
A='src/main/resources/assets/encodedlogistics/'; T=A+'textures/'; M=A+'models/'; RL=lambda p:'encodedlogistics:'+p
def save(im,p): os.makedirs(os.path.dirname(p),exist_ok=True); im.save(p)
# Block textures must be multiples of 16 on both sides: anything else lowers the whole block atlas's mip levels.
def save16(im,p): save(RM.pad16(im),p)
def jd(o,p): os.makedirs(os.path.dirname(p),exist_ok=True); json.dump(o,open(p,'w'),indent=1)
def textures():
    R=T+'block/rack/'
    save(RT.frame_tex(),R+'frame.png'); save(RT.side_panel(751,False),R+'side_lower.png'); save(RT.side_panel(752,True),R+'side_upper.png')
    save(RT.roof_tex(),R+'roof.png'); save(RT.plinth_tex(),R+'plinth.png'); save(RT.interior_tex(),R+'interior.png'); save16(RT.rail_tex(),R+'rail.png')
    save16(RT.mesh_door(15,45,761,badge=True),R+'door_front.png'); save16(RT.mesh_door(8,45,762,header=False),R+'door_rear.png'); save(RT.door_parts(),R+'door_parts.png')
    D=T+'block/rack_device/'
    for n,(u,base,glow) in DT.DEVICES.items():
        save(base(),D+f'{n}.png'); save(glow('on'),D+f'{n}_on.png')
        strip=Image.new('RGBA',(128,256),(0,0,0,0)); strip.alpha_composite(glow('fault'),(0,0)); save(strip,D+f'{n}_fault.png')   # frame 2 empty = blink
        open(D+f'{n}_fault.png.mcmeta','w').write('{\n  "animation": {\n    "frametime": 10\n  }\n}\n')
    for n,f in RI.ICONS.items(): save(f(),T+f'item/{n}.png')
def models():
    jd(RM.frame_model(),M+'block/rack/rack_frame.json')
    for n,m in RM.door_models().items(): jd(m,M+f'block/rack/{n}.json')
    for n,(u,_,_) in DT.DEVICES.items():
        jd(RM.device_model(n,u),M+f'block/rack_device/{n}.json'); jd(RM.device_model(n,u,'on'),M+f'block/rack_device/{n}_on.json'); jd(RM.device_model(n,u,'fault'),M+f'block/rack_device/{n}_fault.json')
    # blockstates: master renders the frame (static); dummies have no model (RenderShape.INVISIBLE)
    rot={'north':{},'east':{'y':90},'south':{'y':180},'west':{'y':270}}
    jd({'variants':{**{f'facing={f},part=master':{'model':RL('block/rack/rack_frame'),**r} for f,r in rot.items()},
                    **{f'facing={f},part=dummy':{'model':RL('block/rack/rack_empty')} for f in rot}}},A+'blockstates/server_rack.json')
    jd({'parent':'minecraft:block/block','textures':{'particle':RL('block/rack/frame')},'elements':[]},M+'block/rack/rack_empty.json')
    for n in RI.ICONS:
        jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{n}')}},M+f'item/{n}.json')
        jd({'model':{'type':'minecraft:model','model':RL(f'item/{n}')}},A+f'items/{n}.json')
if __name__=='__main__':
    textures(); models(); print('files',sum(len(f) for _,_,f in os.walk('src')))
