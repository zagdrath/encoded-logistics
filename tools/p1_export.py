# Encoded Logistics - Phase 1 asset exporter. Run from the project root: python tools/p1_export.py
import os, json, sys, shutil
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from el_style import *
from p1_items import TIERS, TIER_RGB
from items_v3 import ITEMS
from p1_blocks import *
from PIL import Image
A='src/main/resources/assets/encodedlogistics'; T=A+'/textures'; M=A+'/models'
RL=lambda p:'encodedlogistics:'+p
GLOW={'neoforge_data':{'block_light':15,'sky_light':15},'shade':False}
MC8='{\n  "animation": {\n    "frametime": 3,\n    "interpolate": true\n  }\n}\n'
def save(im,p): os.makedirs(os.path.dirname(p),exist_ok=True); im.save(p)
def jd(o,p): os.makedirs(os.path.dirname(p),exist_ok=True); json.dump(o,open(p,'w',newline='\n'),indent=1)
def face(t,uv): return {'texture':t,'uv':uv}
def el(a,b,f,**k): d={'from':list(a),'to':list(b),'faces':f}; d.update(k); return d
def tr(x0,y0,w,h): return (16-x0-w,16-y0-h,16-x0,16-y0)       # front-texture rect -> world x/y on a NORTH face

def items():
    for name,fn in ITEMS.items():
        save(fn(),f'{T}/item/{name}.png')
        jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{name}')}},f'{M}/item/{name}.json')
        jd({'model':{'type':'minecraft:model','model':RL(f'item/{name}')}},f'{A}/items/{name}.json')

def press():
    P=f'{T}/block/lithography_press/'
    save(press_front(),P+'front.png'); save(press_side(91),P+'side.png'); save(press_top(),P+'top.png'); save(press_back(),P+'back.png')
    save(press_glass(),P+'glass.png'); save(press_lamp(False),P+'lamp.png'); save(press_lamp(True),P+'lamp_on.png')
    save(press_uv_glow(),P+'uv_glow.png'); open(P+'uv_glow.png.mcmeta','w',newline='\n').write(MC8)
    tx={k:RL(f'block/lithography_press/{k}') for k in ('front','side','top','back','glass','lamp','lamp_on','uv_glow')}
    tx.update({'parts':RL('block/parts_steel'),'leds':RL('block/leds'),'leds_glow':RL('block/leds_glow'),'particle':RL('block/lithography_press/side')})
    els=[el((0,0,2),(16,16,16),{'north':face('#front',[0,0,16,16]),'south':face('#back',[0,0,16,16]),'east':face('#side',[0,0,14,16]),
            'west':face('#side',[2,0,16,16]),'up':face('#top',[0,2,16,16]),'down':face('#side',[0,0,16,14])})]
    for (x0,y0,w,h) in ((0,0,16,3),(0,10,16,6),(0,3,3,7),(13,3,3,7)):        # front shell round the window pocket
        X0,Y0,X1,Y1=tr(x0,y0,w,h); f={'north':face('#front',[x0,y0,x0+w,y0+h])}
        f['up']=face('#top',[X0,0,X1,2]) if Y1==16 else face('#parts',PU['lit'])
        f['down']=face('#side',[X0,14,X1,16]) if Y0==0 else face('#parts',PU['shadow'])
        f['east']=face('#side',[14,16-Y1,16,16-Y0]) if X1==16 else face('#parts',PU['dark'])
        f['west']=face('#side',[0,16-Y1,2,16-Y0]) if X0==0 else face('#parts',PU['mid'])
        els.append(el((X0,Y0,0),(X1,Y1,2),f))
    lamp=lambda t: el((4,12,0.6),(12,13,1.6),{'north':face(t,[0,0,8,1]),'down':face(t,[0,1,8,2]),'east':face('#parts',PU['dark']),'west':face('#parts',PU['dark'])})
    glass=el((3,6,0.4),(13,13,0.4),{'north':face('#glass',[0,0,10,7]),'south':face('#glass',[0,0,10,7])})
    led=lambda t,uv: el((3,3,-0.02),(5,4,0),{'north':face(t,uv)})
    idle=els+[lamp('#lamp'),led('#leds',[0,0,2,1]),glass]
    lit_lamp=lamp('#lamp_on'); g1=el((4,12,0.6),(12,13,1.6),{'north':face('#lamp_on',[0,0,8,1]),'down':face('#lamp_on',[0,1,8,2])},**GLOW)
    uvg=el((3,6,1.95),(13,13,1.95),{'north':face('#uv_glow',[0,0,10,7])},**GLOW)
    active=els+[lit_lamp,g1,uvg,led('#leds',[2,0,4,1]),el((3,3,-0.03),(5,4,-0.01),{'north':face('#leds_glow',[2,0,4,1])},**GLOW),glass]
    for name,e in (('lithography_press',idle),('lithography_press_active',active)):
        jd({'parent':'minecraft:block/block','render_type':'minecraft:translucent','textures':tx,'elements':e},f'{M}/block/{name}.json')
    rot={'north':{},'east':{'y':90},'south':{'y':180},'west':{'y':270}}
    jd({'variants':{f'facing={f},active={a}':{'model':RL('block/lithography_press'+('_active' if a=='true' else '')),**r} for f,r in rot.items() for a in ('false','true')}},
       f'{A}/blockstates/lithography_press.json')
    jd({'model':{'type':'minecraft:model','model':RL('block/lithography_press')}},f'{A}/items/lithography_press.json')

def terminal():
    P=f'{T}/block/access_terminal/'
    save(term_front(False),P+'front.png'); save(term_side(),P+'side.png'); save(term_back(),P+'back.png')
    save(term_screen(),P+'screen.png'); open(P+'screen.png.mcmeta','w',newline='\n').write(MC8)
    tx={'front':RL('block/access_terminal/front'),'side':RL('block/access_terminal/side'),'back':RL('block/access_terminal/back'),
        'screen':RL('block/access_terminal/screen'),'parts':RL('block/parts_steel'),'particle':RL('block/access_terminal/front')}
    # modelled for the NORTH side of its host block: the screen faces outward (-Z), the stub reaches the cable core at z=5
    housing=el((1,1,0),(15,15,2.5),{'north':face('#front',[1,1,15,15]),'south':face('#back',[1,1,15,15]),
               'east':face('#side',[0,0,2.5,14]),'west':face('#side',[0,0,2.5,14]),'up':face('#side',[0,0,14,2.5]),'down':face('#side',[0,0,14,2.5])})
    stub=el((6,6,2.5),(10,10,5),{f:face('#parts',PU['mid']) for f in ('east','west','up','down','south')})
    screen=el((3,4,-0.02),(13,13,-0.02),{'north':face('#screen',[3,3,13,12])},**GLOW)
    jd({'parent':'minecraft:block/block','textures':tx,'elements':[housing,stub]},f'{M}/part/access_terminal.json')
    jd({'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':[housing,stub,screen]},f'{M}/part/access_terminal_online.json')
    jd({'parent':RL('part/access_terminal_online'),'display':{'gui':{'rotation':[30,200,0],'translation':[0,0,0],'scale':[0.8,0.8,0.8]},
        'ground':{'scale':[0.4,0.4,0.4]},'fixed':{'rotation':[0,180,0],'scale':[0.6,0.6,0.6]},
        'firstperson_righthand':{'rotation':[0,200,0],'scale':[0.45,0.45,0.45]},'thirdperson_righthand':{'rotation':[75,200,0],'translation':[0,2.5,0],'scale':[0.4,0.4,0.4]}}},
       f'{M}/item/access_terminal.json')
    jd({'model':{'type':'minecraft:model','model':RL('item/access_terminal')}},f'{A}/items/access_terminal.json')

def shared():
    save(parts_tex(),f'{T}/block/parts_steel.png'); save(leds_tex(),f'{T}/block/leds.png'); save(leds_tex(True),f'{T}/block/leds_glow.png')

def drive_bay(src_tex,src_assets):
    """Drive Bay (the Drive Array v2 handoff, renamed): repainted block textures + in-bay sled sprites for 8K..2M."""
    B=f'{T}/block/drive_bay/'; os.makedirs(B,exist_ok=True)
    for n in ('front','side','top','parts','leds','leds_glow'): shutil.copy(f'{src_tex}/{n}.png',B+n+'.png')
    im=img()                                                     # in-bay sled faces: row 2*t, 6x2 (x0..1 = status light quad)
    for t in range(5):
        dp,sh,base,lt=TIER_RGB[t]
        row0=[g(1),g(1),H('#D3D7DB'),H('#A3A9B1'),g(5),lt]       # latch handle (silver), sled face, label edge in tier colour
        row1=[g(1),g(1),H('#8D949D'),H('#79808A'),g(4),base]
        for x in range(6): put(im,x,2*t,row0[x]); put(im,x,2*t+1,row1[x])
    im.save(B+'drives.png')
    m=json.load(open(f'{src_assets}/models/block/drive_array.json'))
    m['textures']={k:(v.replace('drive_array','drive_bay')) for k,v in m['textures'].items()}
    jd(m,f'{M}/block/drive_bay.json')
    bs=json.load(open(f'{src_assets}/blockstates/drive_array.json'))
    for v in bs['variants'].values(): v['model']=v['model'].replace('drive_array','drive_bay')
    jd(bs,f'{A}/blockstates/drive_bay.json')
    jd({'model':{'type':'minecraft:model','model':RL('block/drive_bay')}},f'{A}/items/drive_bay.json')
    shutil.copy(f'{src_assets}/textures/gui/drive_array.png',f'{T}/gui/drive_bay.png') if os.path.isdir(f'{T}/gui') else None
    os.makedirs(f'{T}/gui/sprites/drive_bay',exist_ok=True)
    for f in os.listdir(f'{src_assets}/textures/gui/sprites/drive_array'): shutil.copy(f'{src_assets}/textures/gui/sprites/drive_array/{f}',f'{T}/gui/sprites/drive_bay/{f}')
    s=json.load(open(f'{src_assets}/screens/drive_array.json'))
    js=json.dumps(s).replace('drive_array','drive_bay'); jd(json.loads(js),f'{A}/screens/drive_bay.json')

if __name__=='__main__':
    os.makedirs(f'{T}/gui',exist_ok=True)
    shared(); items(); press(); terminal()
    if len(sys.argv)>2: drive_bay(sys.argv[1],sys.argv[2])
    print('files',sum(len(f) for _,_,f in os.walk(A)))
