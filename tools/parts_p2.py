# Phase 2 cable parts: Ingress/Egress Port, Inventory Tap, Threshold Sensor, Fabrication Terminal.
# All modelled on the NORTH face of their host (z = 0 is the block face they mount on / look out of); the stub reaches
# the cable core at z = 5. Rotate like cable arms: east y90, south y180, west y270, up x270, down x90.
import random, math
from el_style import *
from PIL import Image
INGRESS=[H('#22497E'),H('#3A76C0'),H('#4A8FE0'),H('#7FB3F0'),H('#C9E0FA')]
EGRESS=[H('#9E5A1C'),H('#C8782A'),H('#E8913A'),H('#F5B574'),H('#FFE2C2')]
TAP=[H('#4E3285'),H('#7D55C8'),H('#A77BF0'),H('#CDB4FA'),H('#EEE4FE')]
RED=[H('#5A0E0A'),H('#8E231C'),H('#E5483C'),H('#FF9C90'),H('#FFE0DA')]
GOLD=[H('#7A5C08'),H('#B88E14'),H('#E8C24A'),H('#F5DE8A')]
def parts_tex():
    im=img()
    for i,c in enumerate([g(8),g(6),g(3),g(1),BRASS[2],g(5)]):
        for y in range(2):
            for x in range(2): put(im,2*i+x,y,c)
    return im
PU={'lit':[0,0,2,2],'mid':[2,0,4,2],'dark':[4,0,6,2],'shadow':[6,0,8,2],'brass':[8,0,10,2],'side':[10,0,12,2]}
# ---------------- ports ----------------
def port_mouth(acc):
    """Mouth plate face (12x12 at 2,2): accent ring, bevelled steel, slotted intake grille."""
    im=img(); field(im,2,2,12,12,4,201); bevel_frame(im,2,2,12,12,202,lip=False)
    for a in range(3,13): put(im,a,3,acc[2]); put(im,3,a,acc[2]); put(im,a,12,acc[1]); put(im,12,a,acc[1])
    for y in range(5,11):
        for x in range(5,11): put(im,x,y,g(1) if y%2 else g(3))
    for x in range(5,11): put(im,x,5,g(0))
    return im
def port_side(acc,inward,glow=False):
    """Body side strip, two orientations: (0,0) 3 wide (u = depth) x 10 tall; (4,0) 10 wide x 3 tall (v = depth).
       A chevron pointing toward the cable (ingress, +depth) or out to the inventory (egress, -depth)."""
    im=img()
    if not glow:
        for y in range(10):
            for x in range(3): put(im,x,y,g(6) if y==0 else g(3) if y==9 else g(5))
        for x in range(10):
            for y in range(3): put(im,4+x,y,g(6) if x==0 else g(3) if x==9 else g(5))
    # chevron: depth d = 0..2, across a = 2..7
    pts=[]
    for a in range(2,8):
        d=abs(a-4.5)
        dd=int(2-min(2,d)) if inward else int(min(2,d))
        pts.append((dd,a))
    for (d,a) in pts:
        c=acc[3] if a<5 else acc[2]
        put(im,d,a,c); put(im,4+a,d,c)
    return im
def port_models(kind,acc,inward):
    T=f'encodedlogistics:block/part/{kind}'
    tx={'mouth':T+'_mouth','side':T+'_side','side_glow':T+'_side_glow','casing':'encodedlogistics:block/network_casing_port',
        'parts':'encodedlogistics:block/part/parts','particle':T+'_mouth'}
    def face(t,uv): return {'texture':t,'uv':uv}
    def side_faces(t,z0,z1):
        d=z1-z0
        return {'east':face(t,[0,0,d,10]),'west':face(t,[0,0,d,10]),'up':face(t,[4,0,14,d]),'down':face(t,[4,0,14,d])}
    els=[{'from':[2,2,0],'to':[14,14,1],'faces':{'north':face('#mouth',[2,2,14,14]),'south':face('#parts',PU['dark']),
          'east':face('#parts',PU['side']),'west':face('#parts',PU['side']),'up':face('#parts',PU['lit']),'down':face('#parts',PU['shadow'])}},
         {'from':[3,3,1],'to':[13,13,4],'faces':side_faces('#side',1,4)},
         {'from':[4,4,4],'to':[12,12,4.5],'faces':{'south':face('#casing',[4,4,12,12]),'east':face('#parts',PU['mid']),'west':face('#parts',PU['mid']),
          'up':face('#parts',PU['lit']),'down':face('#parts',PU['dark'])}},
         {'from':[6,6,4.5],'to':[10,10,5],'faces':{f:face('#parts',PU['mid']) for f in ('east','west','up','down')}}]
    glow={'from':[3,3,1],'to':[13,13,4],'faces':side_faces('#side_glow',1,4),'neoforge_data':{'block_light':15,'sky_light':15},'shade_direction_override':'up'}
    return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':els},\
           {'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':els+[glow]}
# ---------------- inventory tap ----------------
def tap_plate():
    """Probe plate face (14x14 at 1,1): dark board, two rows of gold contact pads, violet accent edge."""
    im=img(); field(im,1,1,14,14,3,211); bevel_frame(im,1,1,14,14,212,lip=False)
    for x in range(2,14): put(im,x,2,TAP[2]); put(im,x,13,TAP[1])
    for x in range(3,13,2):
        for y in (5,6,9,10): put(im,x,y,GOLD[2] if y in (5,9) else GOLD[1])
    return im
def tap_body(glow=False):
    """Body sides (8 wide x 2 deep) with a 2x1 status light: (0,0) u=depth 2 x v 8; (4,0) 8 x 2."""
    im=img()
    if not glow:
        for y in range(8):
            for x in range(2): put(im,x,y,g(6) if y==0 else g(4))
        for x in range(8):
            for y in range(2): put(im,4+x,y,g(6) if x==0 else g(4))
    for (x,y) in ((0,3),(1,3),(0,4),(1,4)): put(im,x,y,TAP[3] if y==3 else TAP[2])
    for (x,y) in ((7,0),(8,0),(7,1),(8,1)): put(im,x,y,TAP[3] if x==7 else TAP[2])
    return im
def tap_models():
    T='encodedlogistics:block/part/inventory_tap'
    tx={'plate':T+'_plate','body':T+'_body','body_glow':T+'_body_glow','parts':'encodedlogistics:block/part/parts','particle':T+'_plate'}
    def face(t,uv): return {'texture':t,'uv':uv}
    els=[{'from':[1,1,0],'to':[15,15,1],'faces':{'north':face('#plate',[1,1,15,15]),'south':face('#parts',PU['dark']),
          'east':face('#parts',PU['side']),'west':face('#parts',PU['side']),'up':face('#parts',PU['lit']),'down':face('#parts',PU['shadow'])}},
         {'from':[1,1,1],'to':[15,2,2.5],'faces':{f:face('#parts',PU['mid'] if f!='up' else PU['lit']) for f in ('north','south','east','west','up','down')}},   # clamp jaw
         {'from':[1,14,1],'to':[15,15,2.5],'faces':{f:face('#parts',PU['mid'] if f!='up' else PU['lit']) for f in ('north','south','east','west','up','down')}},
         {'from':[4,4,1],'to':[12,12,3],'faces':{'east':face('#body',[0,0,2,8]),'west':face('#body',[0,0,2,8]),'up':face('#body',[4,0,12,2]),'down':face('#body',[4,0,12,2]),
          'south':face('#parts',PU['dark'])}},
         {'from':[6,6,3],'to':[10,10,5],'faces':{f:face('#parts',PU['mid']) for f in ('east','west','up','down')}}]
    glow={'from':[4,4,1],'to':[12,12,3],'faces':{'east':face('#body_glow',[0,0,2,8]),'west':face('#body_glow',[0,0,2,8]),
          'up':face('#body_glow',[4,0,12,2]),'down':face('#body_glow',[4,0,12,2])},'neoforge_data':{'block_light':15,'sky_light':15},'shade_direction_override':'up'}
    return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':els},\
           {'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':els+[glow]}
# ---------------- threshold sensor ----------------
def sensor_panel():
    """Panel face (10x10 at 3,3): steel bezel, a recessed lamp socket in the middle, small tick marks."""
    im=img(); field(im,3,3,10,10,4,221); bevel_frame(im,3,3,10,10,222,lip=False)
    for x in range(5,11): put(im,x,5,g(1)); put(im,x,10,g(7))
    for y in range(5,11): put(im,5,y,g(1)); put(im,10,y,g(7))
    for x in (4,11): put(im,x,8,g(8))
    return im
def sensor_lamp(on):
    """Lamp (4x4): dark red lens off; bright red with a hot centre on (also used as its glow)."""
    im=img()
    for y in range(4):
        for x in range(4):
            if on: c=RED[4] if (x,y) in ((1,1),(2,1),(1,2)) else RED[3] if x+y<4 else RED[2]
            else: c=RED[1] if (x,y)==(1,1) else RED[0] if x+y<5 else H('#3A0806')
            put(im,x,y,c)
    return im
def sensor_models():
    T='encodedlogistics:block/part/threshold_sensor'
    def face(t,uv): return {'texture':t,'uv':uv}
    tx={'panel':T+'_panel','lamp_off':T+'_lamp_off','lamp_on':T+'_lamp_on','parts':'encodedlogistics:block/part/parts','particle':T+'_panel'}
    base=[{'from':[3,3,0.5],'to':[13,13,2],'faces':{'north':face('#panel',[3,3,13,13]),'south':face('#parts',PU['dark']),
           'east':face('#parts',PU['side']),'west':face('#parts',PU['side']),'up':face('#parts',PU['lit']),'down':face('#parts',PU['shadow'])}},
          {'from':[6,6,2],'to':[10,10,5],'faces':{f:face('#parts',PU['mid']) for f in ('east','west','up','down')}}]
    lamp=lambda t:{'from':[6,6,0],'to':[10,10,0.5],'faces':{f:face(t,[0,0,4,4] if f=='north' else [0,0,4,0.5] if f in ('up','down') else [0,0,0.5,4]) for f in ('north','east','west','up','down')}}
    on=lamp('#lamp_on'); g_=dict(lamp('#lamp_on')); g_.update({'neoforge_data':{'block_light':15,'sky_light':15},'shade_direction_override':'up'})
    return {'parent':'minecraft:block/block','textures':tx,'elements':base+[lamp('#lamp_off')]},\
           {'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':base+[on,g_]}
# ---------------- fabrication terminal ----------------
MINT=[H('#0B3A2C'),H('#127A57'),H('#1FB582'),H('#5CF0B8'),H('#C8FFE9')]
def fab_front(base_front):
    """Access Terminal front with an autocrafting-gold stripe along the bezel bottom (reads differently even offline)."""
    im=base_front.copy()
    for x in range(3,13): put(im,x,13,GOLD[2] if x<8 else GOLD[1])
    return im
def fab_screen(frames=8):
    """Screen (10x9 at 3,3): two item rows on top, a 3x3 crafting grid + arrow + output below; gold output cell."""
    strip=Image.new('RGBA',(16,16*frames),(0,0,0,0)); rnd=random.Random(5)
    vals={(x,y):rnd.random() for x in range(10) for y in range(9)}
    for f in range(frames):
        P=lambda x,y,c: strip.putpixel((3+x,16*f+3+y),c+(255,))
        for y in range(9):
            for x in range(10): P(x,y,MINT[0])
        for x in range(10): P(x,0,MINT[1])
        for gy in (1,3):
            for gx in range(0,10,2): P(gx,gy,MINT[3] if vals[(gx,gy)]>0.55 else MINT[2])
        for gy in range(5,8):
            for gx in range(0,3): P(gx,gy,MINT[2] if (gx+gy+f)%5 else MINT[3])
        P(4,6,MINT[3]); P(5,6,MINT[3]); P(5,5,MINT[2]); P(5,7,MINT[2])      # arrow
        out=GOLD[3] if f%4<2 else GOLD[2]
        for (x,y) in ((7,5),(8,5),(7,6),(8,6),(7,7),(8,7)): P(x,y,out)
    return strip
