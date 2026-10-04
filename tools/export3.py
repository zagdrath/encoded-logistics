# Exporter for rack device batch 3. Run from the project root: python tools/export3.py
import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import devices3 as D, rack_models as RM
A='src/main/resources/assets/encodedlogistics/'; T=A+'textures/'; M=A+'models/'; DD='src/main/resources/data/'; RL=lambda p:'encodedlogistics:'+p
GLOW={'neoforge_data':{'block_light':15,'sky_light':15},'shade':False}
def save(im,p): os.makedirs(os.path.dirname(p),exist_ok=True); im.save(p)
def jd(o,p): os.makedirs(os.path.dirname(p),exist_ok=True); json.dump(o,open(p,'w'),indent=1)
def mc(ft,w=None,h=None):
    a={'frametime':ft}
    if w: a['width']=w; a['height']=h
    return json.dumps({'animation':a},indent=2)+'\n'
uvf=RM.uvf; face=RM.face; el=RM.el
def strips(name,base,glow,H_=128):
    P=T+'block/rack_device/'
    save(base,P+f'{name}.png')
    on=Image.new('RGBA',(128,H_*4),(0,0,0,0))
    for f in range(4): on.alpha_composite(glow(f,'on'),(0,H_*f))
    save(on,P+f'{name}_on.png'); open(P+f'{name}_on.png.mcmeta','w').write(mc(3,128 if H_!=128 else None,H_ if H_!=128 else None))
    fa=Image.new('RGBA',(128,H_*2),(0,0,0,0)); fa.alpha_composite(glow(0,'fault'),(0,0))
    save(fa,P+f'{name}_fault.png'); open(P+f'{name}_fault.png.mcmeta','w').write(mc(10,128 if H_!=128 else None,H_ if H_!=128 else None))
def device6_model(name,glow=None):
    """6U: 128x256 sheet - front (0,0)-(104,48), back (0,64)-(104,112), chassis (112,0)-(128,16)."""
    fr=uvf((0,0,104,48),128,256); rr=uvf((104,64,0,112),128,256); ch=uvf((112,0,128,16),128,256)
    E=[el('chassis',(1.55,0,1.75),(14.45,6,29.25),{'north':face('#tex',fr),'south':face('#tex',rr),'east':face('#tex',ch),'west':face('#tex',ch),'up':face('#tex',ch),'down':face('#tex',ch)})]
    tx={'tex':RL(f'block/rack_device/{name}'),'particle':RL(f'block/rack_device/{name}')}
    if glow:
        tx['glow']=RL(f'block/rack_device/{name}_{glow}')
        E.append(el('leds',(1.55,0,1.74),(14.45,6,29.26),{'north':face('#glow',fr),'south':face('#glow',rr)},**GLOW))
    return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':E}
def console_models():
    """Rack Console split for the renderer (device space, bottom at y = 0):
       body  - the fixed chassis; its front face is the dark drawer opening (texture (0,48)-(104,56))
       drawer - front plate (z 1.25-1.75) + tray (z 1.75-13.75); slides along -z
       keyboard - on the tray; moves with the drawer
       lid - the fold-up screen, hinged at (8, 0.625, 13.5) on the drawer; rotates about +X"""
    tex=RL('block/rack_device/rack_console'); parts=RL('block/rack_device/rack_console_parts'); scr=RL('block/rack_device/rack_console_screen')
    T128=lambda r: uvf(r,128,128)
    body=[el('chassis',(1.55,0,1.75),(14.45,1,29.25),{'north':face('#tex',T128((0,48,104,56))),'south':face('#tex',T128((104,32,0,40))),
          'east':face('#tex',T128((112,0,128,16))),'west':face('#tex',T128((112,0,128,16))),'up':face('#tex',T128((112,0,128,16))),'down':face('#tex',T128((112,0,128,16)))})]
    drawer=[el('front',(1.55,0,1.25),(14.45,1,1.75),{'north':face('#tex',T128((0,0,104,8))),'south':face('#tex',T128((112,0,128,16))),
                'east':face('#tex',T128((112,0,128,2))),'west':face('#tex',T128((112,0,128,2))),'up':face('#tex',T128((112,0,128,2))),'down':face('#tex',T128((112,0,128,2)))}),
            el('tray',(2,0,1.75),(14,0.25,13.75),{'up':face('#parts',T128((56,52,108,100))),'down':face('#parts',T128((112,0,128,16))),
                'east':face('#parts',T128((112,0,128,2))),'west':face('#parts',T128((112,0,128,2))),'south':face('#parts',T128((112,0,128,2)))})]
    kb=[el('keyboard',(2.5,0.25,2),(13.5,0.5,8),{'up':face('#parts',T128((0,0,52,48))),'north':face('#parts',T128((0,46,52,48))),
            'east':face('#parts',T128((0,0,2,48))),'west':face('#parts',T128((0,0,2,48)))})]
    lid=[el('lid',(2.25,0.5,2),(13.75,0.75,13.5),{'up':face('#parts',T128((56,0,108,48))),'down':face('#parts',T128((0,52,52,100))),
             'north':face('#parts',T128((112,0,128,1))),'east':face('#parts',T128((112,0,128,1))),'west':face('#parts',T128((112,0,128,1))),'south':face('#parts',T128((112,0,128,1)))})]
    lid_on=lid+[el('screen',(3.25,0.49,3),(12.75,0.49,12.5),{'down':face('#screen',[1,1,13,13])},**GLOW)]
    base={'parent':'minecraft:block/block','render_type':'minecraft:cutout'}
    tx={'tex':tex,'parts':parts,'screen':scr,'particle':tex}
    out={'rack_console':{**base,'textures':tx,'elements':body},
         'rack_console_drawer':{**base,'textures':tx,'elements':drawer},
         'rack_console_drawer_on':{**base,'textures':{**tx,'glow':RL('block/rack_device/rack_console_on')},'elements':drawer+[el('led',(1.55,0,1.24),(14.45,1,1.24),{'north':face('#glow',T128((0,0,104,8)))},**GLOW)]},
         'rack_console_drawer_fault':{**base,'textures':{**tx,'glow':RL('block/rack_device/rack_console_fault')},'elements':drawer+[el('led',(1.55,0,1.24),(14.45,1,1.24),{'north':face('#glow',T128((0,0,104,8)))},**GLOW)]},
         'rack_console_keyboard':{**base,'textures':tx,'elements':kb},
         'rack_console_lid':{**base,'textures':tx,'elements':lid},
         'rack_console_lid_on':{**base,'textures':tx,'elements':lid_on}}
    # the type's state models (<id>_on / _fault) are the static body; the drawer carries the LED glow
    out['rack_console_on']=out['rack_console']; out['rack_console_fault']=out['rack_console']
    return out
def item_3d(name,tex_icon,w,h,d,side_uv):
    """Small 3D in-hand / dropped model (gui keeps the flat icon via items/<id>.json display_context select)."""
    x0,y0,z0=8-w/2,8-h/2,8-d/2
    return {'parent':'minecraft:item/handheld','textures':{'icon':RL(f'item/{tex_icon}'),'particle':RL(f'item/{tex_icon}')},
            'elements':[el(name,(x0,y0,z0),(x0+w,y0+h,z0+d),{'north':face('#icon',[2,2,14,14]),'south':face('#icon',[2,2,14,14]),
                'east':face('#icon',side_uv),'west':face('#icon',side_uv),'up':face('#icon',side_uv),'down':face('#icon',side_uv)})],
            'display':{'thirdperson_righthand':{'rotation':[0,90,0],'translation':[0,1,0],'scale':[0.55,0.55,0.55]},
                       'firstperson_righthand':{'rotation':[0,-30,0],'translation':[1,2,0],'scale':[0.6,0.6,0.6]},
                       'ground':{'scale':[0.5,0.5,0.5]},'fixed':{'scale':[0.8,0.8,0.8]}}}
def items():
    icons={**{f'lto_tape_{g}':(lambda g=g: D.tape_icon(g)) for g in (6,7,8,9,10)},'lto_tape_drive':D.drive_icon,
           **{k:(lambda k=k: D.device_icon(k)) for k in ('rack_console','wireless_controller','tape_library_4u','tape_library_6u')}}
    for n,f in icons.items():
        save(f(),T+f'item/{n}.png'); jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{n}')}},M+f'item/{n}.json')
        if n.startswith('lto_'):
            m3=item_3d(n,n,12,12,3,[3,3,4,4]) if 'tape_' in n and n!='lto_tape_drive' else item_3d(n,n,12,5,10,[3,11,4,12])
            jd(m3,M+f'item/{n}_3d.json')
            jd({'model':{'type':'minecraft:select','property':'minecraft:display_context',
                         'cases':[{'when':['gui','fixed'],'model':{'type':'minecraft:model','model':RL(f'item/{n}')}}],
                         'fallback':{'type':'minecraft:model','model':RL(f'item/{n}_3d')}}},A+f'items/{n}.json')
        else:
            jd({'model':{'type':'minecraft:model','model':RL(f'item/{n}')}},A+f'items/{n}.json')
    return list(icons)
if __name__=='__main__':
    strips('rack_console',D.console(),D.console_glow); strips('wireless_controller',D.wireless(),D.wireless_glow)
    strips('tape_library_4u',D.library(4),lambda f,s='on': D.library_glow(4,f,s))
    strips('tape_library_6u',D.library(6),lambda f,s='on': D.library_glow(6,f,s),256)
    save(D.console_parts(),T+'block/rack_device/rack_console_parts.png')
    save(D.console_screen(),T+'block/rack_device/rack_console_screen.png'); open(T+'block/rack_device/rack_console_screen.png.mcmeta','w').write(mc(4))
    for n in ('wireless_controller','tape_library_4u'):
        u=1 if n=='wireless_controller' else 4
        jd(RM.device_model(n,u),M+f'block/rack_device/{n}.json'); jd(RM.device_model(n,u,'on'),M+f'block/rack_device/{n}_on.json'); jd(RM.device_model(n,u,'fault'),M+f'block/rack_device/{n}_fault.json')
    jd(device6_model('tape_library_6u'),M+'block/rack_device/tape_library_6u.json'); jd(device6_model('tape_library_6u','on'),M+'block/rack_device/tape_library_6u_on.json')
    jd(device6_model('tape_library_6u','fault'),M+'block/rack_device/tape_library_6u_fault.json')
    for n,m in console_models().items(): jd(m,M+f'block/rack_device/{n}.json')
    it=items(); print('items',len(it),'files',sum(len(f) for _,_,f in os.walk('src')))
