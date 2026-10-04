# Terminal Desk (master + dummy, 2 x 1) and Swivel Chair models. Desk model space: x 0..32 (master 0..16, dummy 16..32),
# y 0..16, z 0..16, FRONT (the side you sit at) facing NORTH (z = 0). Multipart on the master: desk, terminal_<screen>,
# keyboard, clutter (when clutter=true). The dummy renders nothing.
import json, math
RL=lambda p:'encodedlogistics:'+p
GLOW={'neoforge_data':{'block_light':15,'sky_light':15},'shade':False}
def uvf(r,tw,th): x0,y0,x1,y1=r; return [round(x0*16/tw,4),round(y0*16/th,4),round(x1*16/tw,4),round(y1*16/th,4)]
def f(t,uv,**k): d={'texture':t,'uv':uv}; d.update(k); return d
def el(n,a,b,faces,**k): d={'name':n,'from':list(a),'to':list(b),'faces':faces}; d.update(k); return d
def box(n,a,b,t,uv_all,**k): return el(n,a,b,{s:f(t,uv_all) for s in ('north','south','east','west','up','down')},**k)
L32=lambda r: uvf(r,32,32)
def desk():
    E=[]
    E.append(el('top',(0,12,0),(32,13,16),{'up':f('#lam',L32((0,0,32,16))),'down':f('#lam',L32((0,18,32,32))),
        'north':f('#lam',L32((0,16,32,17))),'south':f('#lam',L32((0,16,32,17))),'east':f('#lam',L32((0,16,16,17))),'west':f('#lam',L32((0,16,16,17)))}))
    E.append(el('leg_panel',(0.5,0,1),(2,12,15),{'west':f('#steel',[1,4,15,16]),'east':f('#steel',[1,4,15,16]),'north':f('#steel',[0,4,1.5,16]),
        'south':f('#steel',[0,4,1.5,16]),'down':f('#steel',[0,0,1.5,14])}))
    E.append(el('foot',(0,0,0.5),(2.5,1,15.5),{s:f('#steel',[0,15,15,16]) for s in ('north','south','east','west','up')}))
    E.append(el('pedestal',(20,0,1),(31.5,12,15),{'north':f('#ped',[0,0,11,12]),'south':f('#steel',[0,4,11.5,16]),
        'east':f('#steel',[1,4,15,16]),'west':f('#steel',[1,4,15,16]),'down':f('#steel',[0,0,11.5,14])}))
    E.append(el('modesty',(2,2,13.5),(20,11.5,14.5),{'north':f('#modesty',[0,0,16,9.5]),'south':f('#modesty',[0,0,16,9.5]),'up':f('#modesty',[0,0,16,1]),'down':f('#modesty',[0,0,16,1])}))
    E.append(el('apron',(2,11,1),(20,12,2),{'north':f('#steel',[0,0,16,1]),'south':f('#steel',[0,0,16,1]),'down':f('#steel',[0,0,16,1])}))
    tx={'lam':RL('block/terminal_desk/laminate'),'steel':RL('block/terminal_desk/steel'),'ped':RL('block/terminal_desk/pedestal'),
        'modesty':RL('block/terminal_desk/modesty'),'particle':RL('block/terminal_desk/laminate')}
    return {'parent':'minecraft:block/block','textures':tx,'elements':E}
def terminal(screen):
    """CRT on the master half: front case 12 x 10 x 6 (z 4.5-10.5), rear taper 9 x 7.5 x 5, a two-step curved glass,
       power switch, status lamp. screen: off | boot | on."""
    C='#case'; E=[]
    E.append(el('case_front',(2.5,13,4.5),(14.5,23,10.5),{'north':f(C,L32((0,0,12,10))),'south':f(C,L32((0,16,9,24))),
        'east':f(C,L32((12,0,18,10))),'west':f(C,L32((18,0,12,10))),'up':f(C,L32((0,10,12,16))),'down':f(C,L32((24,0,32,2)))}))
    E.append(el('case_rear',(4,14,10.5),(13,21.5,15.5),{'south':f(C,L32((0,16,9,24))),'east':f(C,L32((18,0,23,8))),'west':f(C,L32((23,0,18,8))),
        'up':f(C,L32((12,10,21,15))),'down':f(C,L32((24,0,32,2)))}))
    E.append(el('base',(4,12.99,6),(13,13.5,12),{s:f(C,L32((24,0,32,2))) for s in ('north','south','east','west')}))
    glass='#'+('screen_'+screen)
    # curved glass: the full pane, then a smaller "bulge" pane 0.25 px proud of it
    # (the 'on' screen is tools/terminal_screens.py's 10 x 8 picture in a 16 x 16 frame: one texel a pixel)
    full,bulge=([0,0,10,8],[1,1,9,7]) if screen=='on' else ([0,0,16,16],[1.6,2,14.4,14])
    gl=lambda n,a,b,em: el(n,a,b,{'north':f(glass,full)},**(GLOW if em else {}))
    em=screen!='off'
    E.append(gl('glass',(3.5,14,4.4),(13.5,22,4.4),em))
    E.append(el('glass_bulge',(4.5,15,4.15),(12.5,21,4.15),{'north':f(glass,bulge)},**(GLOW if em else {})))
    E.append(el('switch',(12.5,13.4,4.2),(13.5,14.2,4.5),{s:f(C,L32((9,9,11,10))) for s in ('north','east','west','up','down')}))
    E.append(el('lamp',(11.2,13.5,4.3),(12,14.1,4.5),{'north':f('#lamp',[0,0,16,16])},**(GLOW if em else {})))
    tx={'case':RL('block/terminal_desk/crt_case'),f'screen_{screen}':RL(f'block/terminal_desk/crt_screen_{screen}'),
        'lamp':RL('block/terminal_desk/lamp_'+('on' if em else 'off')),'particle':RL('block/terminal_desk/crt_case')}
    return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':E}
def keyboard():
    """Separate keyboard on the desk, in front of the CRT: 11 x 0.75 x 3.5, the rear edge raised (two steps)."""
    K='#kb'; E=[]
    E.append(el('kb_front',(3,13,0.75),(14,13.5,2.5),{'up':f(K,[0,8,16,16]),'north':f(K,[0,15,16,16]),'east':f(K,[0,8,2,16]),'west':f(K,[0,8,2,16])}))
    E.append(el('kb_rear',(3,13,2.5),(14,13.85,4.25),{'up':f(K,[0,0,16,8]),'north':f(K,[0,0,16,1]),'south':f(K,[0,0,16,2]),'east':f(K,[0,0,2,8]),'west':f(K,[0,0,2,8])}))
    return {'parent':'minecraft:block/block','textures':{'kb':RL('block/terminal_desk/keyboard'),'particle':RL('block/terminal_desk/keyboard')},'elements':E}
def clutter():
    """Variant clutter on the dummy half: coffee mug, a stack of green-bar printouts, a clipboard."""
    T='#clutter'; E=[]
    E.append(el('mug',(17,13,2.5),(19,15.5,4.5),{'north':f(T,L32((0,0,8,10))),'south':f(T,L32((0,0,8,10))),'east':f(T,L32((0,0,8,10))),
        'west':f(T,L32((0,0,8,10))),'up':f(T,L32((8,0,14,6)))}))
    E.append(el('mug_handle',(19,13.6,3.2),(19.75,14.8,3.8),{s:f(T,L32((6,2,8,6))) for s in ('north','south','east','west','up','down')}))
    E.append(el('printouts',(21,13,6),(29,14.25,13.5),{'up':f(T,L32((0,12,16,20))),'north':f(T,L32((16,12,32,14))),'south':f(T,L32((16,12,32,14))),
        'east':f(T,L32((16,12,32,14))),'west':f(T,L32((16,12,32,14)))}))
    E.append(el('clipboard',(23,13,1),(30,13.3,5.5),{'up':f(T,L32((0,22,14,32))),'north':f(T,L32((0,31,14,32))),'east':f(T,L32((0,31,9,32))),'west':f(T,L32((0,31,9,32)))},
        rotation={'angle':22.5,'axis':'y','origin':[26.5,13,3.25]}))
    return {'parent':'minecraft:block/block','textures':{'clutter':RL('block/terminal_desk/clutter'),'particle':RL('block/terminal_desk/clutter')},'elements':E}
# ---------------- chair ----------------
LEG_DIRS=[0,67.5,135,225,292.5]          # five-star: every leg on the 22.5-degree grid
def chair_base():
    """Static part: five-star base (legs at 0 / 67.5 / 135 / 225 / 292.5 deg - all on Minecraft's 22.5 grid), casters,
       gas-lift column. Each leg is an element along +x/+z/-x/-z rotated by -45..45 about the centre."""
    E=[]
    for i,deg in enumerate(LEG_DIRS):
        base=round(deg/90)*90; rot=deg-base
        if rot>45: base+=90; rot-=90
        if rot<-45: base-=90; rot+=90
        rot=-rot                                                     # model y-rotation is clockwise from above
        ax={0:'+x',90:'+z',180:'-x',270:'-z',360:'+x'}[base%360 if base%360 else 0]
        if ax=='+x': a,b=(8,0.6,7.25),(14,1.6,8.75); tip=(13.25,0,7.25),(14.75,0.6,8.75)
        elif ax=='-x': a,b=(2,0.6,7.25),(8,1.6,8.75); tip=(1.25,0,7.25),(2.75,0.6,8.75)
        elif ax=='+z': a,b=(7.25,0.6,8),(8.75,1.6,14); tip=(7.25,0,13.25),(8.75,0.6,14.75)
        else: a,b=(7.25,0.6,2),(8.75,1.6,8); tip=(7.25,0,1.25),(8.75,0.6,2.75)
        r={'angle':rot,'axis':'y','origin':[8,1,8]} if rot else None
        k={'rotation':r} if r else {}
        E.append(box(f'leg{i}',a,b,'#metal',[0,0,16,4],**k))
        E.append(box(f'caster{i}',tip[0],tip[1],'#caster',[0,0,16,16],**k))
    E.append(box('hub',(6.5,0.6,6.5),(9.5,2,9.5),'#metal',[0,0,16,16]))
    E.append(box('gas_lift',(7.25,2,7.25),(8.75,6,8.75),'#metal',[4,0,8,16]))
    E.append(box('gas_lift_45',(7.25,2.6,7.25),(8.75,5.4,8.75),'#metal',[4,0,8,16],rotation={'angle':45,'axis':'y','origin':[8,4,8]}))
    return {'parent':'minecraft:block/block','textures':{'metal':RL('block/swivel_chair/metal'),'caster':RL('block/swivel_chair/caster'),
            'particle':RL('block/swivel_chair/fabric')},'elements':E}
def chair_seat():
    """Swivel part (drawn by the block entity renderer, rotated about (8, y, 8) to the chair's yaw): seat pan, padded seat,
       back post, padded back. Faces 'north' = the way the sitter faces."""
    E=[box('mechanism',(6,6,6),(10,7,10),'#metal',[0,0,16,16]),
       el('seat',(2.5,7,2.5),(13.5,9,13.5),{'up':f('#fabric',[0,0,16,16]),'down':f('#metal',[0,0,16,16]),**{s:f('#fabric',[0,12,16,15]) for s in ('north','south','east','west')}}),
       el('seat_roll',(2.5,8.5,2),(13.5,9.25,2.75),{s:f('#fabric',[0,0,16,2]) for s in ('north','up','east','west')}),
       box('back_post',(7,7,13.5),(9,12,14.75),'#metal',[0,0,8,16]),
       el('back',(3,11,13.25),(13,21,15.25),{'north':f('#fabric',[0,0,16,16]),'south':f('#fabric',[0,0,16,16]),'up':f('#fabric',[0,0,16,3]),
           'down':f('#fabric',[0,0,16,3]),'east':f('#fabric',[0,0,3,16]),'west':f('#fabric',[0,0,3,16])},
          rotation={'angle':-22.5 if False else 0,'axis':'x','origin':[8,11,14]})]
    for e in E:
        for fd in e['faces'].values():
            if fd['texture']=='#fabric': fd['tintindex']=0                # dye tint (OPEN 5); untinted = brown tweed
    return {'parent':'minecraft:block/block','textures':{'metal':RL('block/swivel_chair/metal'),'fabric':RL('block/swivel_chair/fabric'),
            'particle':RL('block/swivel_chair/fabric')},'elements':E}
