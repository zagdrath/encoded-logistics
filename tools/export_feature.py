# Encoded Logistics - Power Inlet, Capacitor Bank, Fiber Cable, Cable Anchor, Cable Facade, Segment Isolator.
# Run from the project root: python tools/export_feature.py   (seeded: identical output every run)
import os, json, colorsys, random, sys, zlib
sys.path.insert(0,os.path.dirname(__file__))
from el_style import *
from PIL import Image
from item_display import centred
A='src/main/resources/assets/encodedlogistics'
T=A+'/textures'; M=A+'/models'
def save(im,path):
    os.makedirs(os.path.dirname(path),exist_ok=True); im.save(path)
def jdump(obj,path):
    os.makedirs(os.path.dirname(path),exist_ok=True); json.dump(obj,open(path,'w',newline='\n'),indent=1)
MCMETA_CYCLE='{\n  "animation": {\n    "frametime": 6,\n    "interpolate": true\n  }\n}\n'   # same timing as the controller
RL=lambda p:'encodedlogistics:'+p
def face(tex,uv,**kw): d={'texture':tex,'uv':uv}; d.update(kw); return d
def el(frm,to,faces,**kw): d={'from':list(frm),'to':list(to),'faces':faces}; d.update(kw); return d
GLOW={'neoforge_data':{'block_light':15,'sky_light':15},'shade':False}
def tex_rect(x0,y0,w,h):
    """Front-texture rect -> world box x/y on a NORTH face (north u runs toward -x)."""
    return (16-x0-w,16-y0-h,16-x0,16-y0)

# ---------- shared ----------
def parts_tex():
    """Solid steel texels for faces you only see edge-on: 2x2 each at x = 0 lit, 2 mid, 4 dark, 6 shadow, 8 brass."""
    im=img()
    for i,c in enumerate([g(8),g(6),g(3),g(1),BRASS[2]]):
        for y in range(2):
            for x in range(2): put(im,2*i+x,y,c)
    return im
PU={'lit':[0,0,2,2],'mid':[2,0,4,2],'dark':[4,0,6,2],'shadow':[6,0,8,2],'brass':[8,0,10,2]}
def leds_tex(glow=False):
    """2x2 lights: x0 green off, x2 green on, x4 amber off, x6 amber on."""
    im=img()
    def dot(x0,cols,lit):
        if glow and not lit: return
        put(im,x0,0,cols[2] if lit else cols[0]); put(im,x0+1,0,cols[1]); put(im,x0,1,cols[1]); put(im,x0+1,1,cols[0] if lit else cols[0])
    dot(0,[H('#1C2622'),H('#24302A'),H('#2B3A33')],False); dot(2,[LED_GREEN[0],LED_GREEN[1],LED_GREEN[2]],True)
    dot(4,[AMBER[0],H('#4E340C'),H('#5E3F10')],False); dot(6,[AMBER[2],AMBER[3],AMBER[4]],True)
    return im
LED={'green_off':[0,0,2,2],'green_on':[2,0,4,2],'amber_off':[4,0,6,2],'amber_on':[6,0,8,2]}

# ================= 1. POWER INLET =================
def inlet_front():
    im=img(); field(im,1,1,14,14,2,11)
    for a in range(1,15): put(im,a,1,g(1)); put(im,1,a,g(1))          # recessed panel: shadow under the rim
    bevel_frame(im,0,0,16,16,12,lip=False)
    # housing (texture rect 3,5,10,8): steel rail, FE band, socket recess, three brass pins (IEC C20-style)
    bevel_frame(im,3,5,10,8,13,lip=False)
    for x in range(4,12): put(im,x,6,FE[3] if x==4 else FE[1] if x==11 else FE[2])
    for y in range(7,12):
        for x in range(4,12): put(im,x,y,g(0 if y==7 else 1))
    for (x,y) in ((7,8),(8,8)): put(im,x,y,BRASS[3])                  # earth pin
    for (x,y) in ((5,10),(6,10)): put(im,x,y,BRASS[3] if x==5 else BRASS[2])
    for (x,y) in ((9,10),(10,10)): put(im,x,y,BRASS[3] if x==9 else BRASS[2])
    for x in (5,6,9,10): put(im,x,11,g(0))
    put(im,7,9,g(0)); put(im,8,9,g(0))
    for (x,y) in ((11,1),(14,1),(11,4),(14,4)): pass
    for x in range(11,15): put(im,x,1,g(5)); put(im,x,4,g(7))           # LED bezel (texture 11..14, 1..4)
    for y in range(1,5): put(im,11,y,g(5)); put(im,14,y,g(7))
    return im
def inlet_models():
    P=RL('block/power_inlet/'); faces_side=lambda uv: face('#casing',uv)
    els=[el((0,0,1),(16,16,16),{'north':face('#front',[0,0,16,16]),'south':face('#casing',[0,0,16,16]),
            'east':face('#casing',[0,0,15,16]),'west':face('#casing',[1,0,16,16]),'up':face('#casing',[0,1,16,16]),'down':face('#casing',[0,0,16,15])})]
    for (x0,y0,w,h) in ((0,0,16,1),(0,15,16,1),(0,1,1,14),(15,1,1,14)):   # 1 px rim
        X0,Y0,X1,Y1=tex_rect(x0,y0,w,h)
        f={'north':face('#front',[x0,y0,x0+w,y0+h]),'south':face('#parts',PU['shadow'])}
        f['up']=face('#casing',[X0,0,X1,1]) if y0==0 else face('#parts',PU['lit'])
        f['down']=face('#casing',[X0,15,X1,16]) if y0==15 else face('#parts',PU['shadow'])
        f['east']=face('#casing',[15,16-Y1,16,16-Y0]) if X1==16 else face('#parts',PU['dark'])
        f['west']=face('#casing',[0,16-Y1,1,16-Y0]) if X0==0 else face('#parts',PU['dark'])
        els.append(el((X0,Y0,0),(X1,Y1,1),f))
    X0,Y0,X1,Y1=tex_rect(3,5,10,8)                                       # socket housing, flush with the rim
    els.append(el((X0,Y0,0),(X1,Y1,1),{'north':face('#front',[3,5,13,13]),'up':face('#parts',PU['lit']),'down':face('#parts',PU['shadow']),
                                        'east':face('#parts',PU['mid']),'west':face('#parts',PU['dark'])}))
    X0,Y0,X1,Y1=tex_rect(12,2,2,2)                                       # status light, half a pixel proud of the panel
    def led(state,glow=False):
        e=el((X0,Y0,0.5),(X1,Y1,1),{'north':face('#leds_glow' if glow else '#leds',LED[state]),'up':face('#parts',PU['mid']),
                                     'down':face('#parts',PU['dark']),'east':face('#parts',PU['dark']),'west':face('#parts',PU['mid'])})
        if glow: e['faces']={'north':e['faces']['north']}; e.update(GLOW)
        return e
    tx={'front':P+'front','casing':RL('block/network_casing_port'),'parts':RL('block/parts_steel'),'leds':RL('block/leds'),
        'leds_glow':RL('block/leds_glow'),'particle':RL('block/network_casing')}
    jdump({'parent':'minecraft:block/block','textures':tx,'elements':els+[led('green_off')]},f'{M}/block/power_inlet.json')
    jdump({'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':els+[led('green_on'),led('green_on',True)]},f'{M}/block/power_inlet_powered.json')
    rot={'north':{},'east':{'y':90},'south':{'y':180},'west':{'y':270},'up':{'x':270},'down':{'x':90}}
    jdump({'variants':{f'facing={f},powered={p}':{'model':RL('block/power_inlet'+('_powered' if p=='true' else '')),**r}
           for f,r in rot.items() for p in ('false','true')}},f'{A}/blockstates/power_inlet.json')
    jdump({'model':{'type':'minecraft:model','model':RL('block/power_inlet')}},f'{A}/items/power_inlet.json')

# ================= 2. CAPACITOR BANK =================
SEG_ROWS=[(12,13),(9,10),(6,7),(3,4)]            # bottom -> top; stage k lights the first k
def cap_side(stage,glow=False):
    im=img()
    if not glow:
        field(im,0,0,16,16,4,21); bevel_frame(im,0,0,16,16,22)
        for cx in (2,11):                                                  # two capacitor cans
            for y in range(2,14):
                for i,t in enumerate((8,7,5)): put(im,cx+i,y,g(t))
            for i in range(3): put(im,cx+i,2,g(9)); put(im,cx+i,13,g(3)); put(im,cx+i,4,g(6)); put(im,cx+i,11,g(6))
            put(im,cx+1,14,g(1))
        for y in range(2,14): put(im,5,y,g(1)); put(im,10,y,g(6))          # gauge bezel
        for y in range(2,14):
            for x in range(6,10): put(im,x,y,g(0))                          # window back / separators
    for k,(r0,r1) in enumerate(SEG_ROWS):
        lit=k<stage
        if glow and not lit: continue
        for y in (r0,r1):
            for x in range(6,10):
                if lit:
                    c=FE[3] if (x==6 and y==r0) else FE[2] if (y==r0 or x in (7,8)) else FE[1]
                else:
                    c=g(2) if (x==6 and y==r0) else g(1)
                put(im,x,y,c)
    return im
def cap_models():
    P=RL('block/capacitor_bank/')
    for s in range(5):
        tx={'side':P+f'side_{s}','end':RL('block/network_casing_port'),'particle':P+'side_0'}
        sides={f:face('#side',[0,0,16,16]) for f in ('north','south','east','west')}
        els=[el((0,0,0),(16,16,16),{**sides,'up':face('#end',[0,0,16,16]),'down':face('#end',[0,0,16,16])})]
        if s>0:
            tx['glow']=P+f'side_{s}_glow'
            g_el=el((0,0,0),(16,16,16),{f:face('#glow',[0,0,16,16]) for f in ('north','south','east','west')}); g_el.update(GLOW); els.append(g_el)
        jdump({'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':els},f'{M}/block/capacitor_bank_{s}.json')
    jdump({'variants':{f'fill={s}':{'model':RL(f'block/capacitor_bank_{s}')} for s in range(5)}},f'{A}/blockstates/capacitor_bank.json')
    jdump({'model':{'type':'minecraft:model','model':RL('block/capacitor_bank_3')}},f'{A}/items/capacitor_bank.json')

# ================= 3. FIBER CABLE =================
# Geometry follows the cable spec: 6x6x6 core cube, 4x4x1 neck, 6x6x4 sleeve (sleeves of neighbours meet as one 8 px run).
# Glass sheath (translucent, dye-tinted) over a 2x2 full-bright fiber core; steel necks and flange.
def glass_tint(colour):
    """Sheath glass: the dye's base tone (darker than the core), so the lit fibre reads through it."""
    if colour=='neutral': return (150,170,186)
    if colour=='white': return (196,204,212)
    return dye_ramp(DYE_BASE[colour])[3]
def core_cols(colour,frame=0):
    if colour=='neutral':
        h=(0.62+frame/16)%1; return [tuple(int(v*255) for v in colorsys.hsv_to_rgb(h,s,v)) for s,v in ((0.75,0.80),(0.62,1.0),(0.22,1.0))]
    if colour=='white': return [g(8),g(10),(250,252,255)]
    r=dye_ramp(DYE_BASE[colour]); return [r[3],r[5],r[6]]
def sheath_tex(colour):
    """rows 0..5: sheath side, sheen along u; x0..5/y6..15: same with sheen along v; (8,8) 6x6: cube face."""
    im=img(); tint=glass_tint(colour); rnd=random.Random(zlib.crc32(colour.encode()))
    def glass(along,across,w):
        # alpha + value per row across the tube: bright rim, soft sheen, clear body, darker lower rim
        prof={0:(200,1.35),1:(120,1.25),w-1:(185,0.62)}                       # bright rim, sheen, dark lower rim
        a,k=prof.get(across,(62,0.85))                                            # clear body: the core shows through
        if 0<across<w-1 and rnd.random()<0.12: a+=20; k-=0.06                     # faint streaks (glass grain)
        return tuple(min(255,int(c*k)) for c in tint),a
    for u in range(16):
        for v in range(6):
            c,a=glass(u,v,6); put(im,u,v,c,a)
    for v in range(10):
        for u in range(6):
            c,a=glass(v,u,6); put(im,u,6+v,c,a)
    for y in range(6):
        for x in range(6):
            edge=x in (0,5) or y in (0,5)
            c,a=glass(x,1 if (x==0 or y==0) else (5 if (x==5 or y==5) else 3),6)
            put(im,8+x,8+y,c,a if edge else 58)
    return im
def core_tex(colour,frame=0):
    """Fiber core: rows 0..1 run along u, cols 0..1 run along v (centre bright, edge saturated)."""
    im=img(); lo,mid,hi=core_cols(colour,frame)
    for u in range(16):
        put(im,u,0,hi if u%5 else mid); put(im,u,1,mid)
    for v in range(2,16):
        put(im,0,v,hi if v%5 else mid); put(im,1,v,mid)
    return im
def fittings_tex():
    """Neck collar 4x4 at (0,0); flange face 8x8 at (4,0); flange rim strip at (0,8) 8x2."""
    im=img()
    for y in range(4):
        for x in range(4): put(im,x,y,g(8 if (x==0 or y==0) else 4 if (x==3 or y==3) else 6))
    bevel_frame(im,4,0,8,8,31,lip=False)
    for y in range(1,7):
        for x in range(5,11): put(im,x,y,g(5 if (x+y)%5 else 4))
    port_ring(im,8,4)
    for x in range(8): put(im,x,8,g(8)); put(im,x,9,g(5))
    return im
def fiber_models():
    B='block/cable/fiber/'
    def sleeve(z0,z1):
        L=z1-z0
        return el((5,5,z0),(11,11,z1),{'east':face('#sheath',[16-z1,0,16-z0,6]),'west':face('#sheath',[z0,0,z1,6]),
                   'up':face('#sheath',[0,6,6,6+L]),'down':face('#sheath',[0,6,6,6+L]),
                   'south':face('#sheath',[8,8,14,14])})
    def neck(z0,z1): return el((6,6,z0),(10,10,z1),{f:face('#fittings',[0,0,4,4]) for f in ('north','south','east','west','up','down')})
    def core(z0,z1):
        e=el((7,7,z0),(9,9,z1),{'east':face('#core',[16-z1,0,16-z0,2]),'west':face('#core',[z0,0,z1,2]),
                                'up':face('#core',[0,z0,2,z1]),'down':face('#core',[0,z0,2,z1])}); e.update(GLOW); return e
    cube=el((5,5,5),(11,11,11),{f:face('#sheath',[8,8,14,14]) for f in ('north','south','east','west','up','down')})
    core_c=el((7,7,7),(9,9,9),{f:face('#core',[0,0,2,2]) for f in ('north','south','east','west','up','down')}); core_c.update(GLOW)
    flange=el((4,4,0),(12,12,1),{'north':face('#fittings',[4,0,12,8]),'south':face('#fittings',[4,0,12,8]),
              'east':face('#fittings',[0,8,1,9]),'west':face('#fittings',[0,8,1,9]),'up':face('#fittings',[0,8,8,9]),'down':face('#fittings',[0,9,8,10])})
    def capped(e,*faces): e['faces'].update({f:face('#sheath',[8,8,14,14]) for f in faces}); return e
    # item: a straight run along Z like the normal / dense cable items (sleeve | neck | cube | neck | sleeve), both ends closed
    item=[core(0,16),capped(sleeve(0,4),'north'),neck(4,5),cube,neck(11,12),capped(sleeve(12,16),'north')]
    # core first, sheath after it: the translucent glass draws over the lit fibre
    parts={'cube':[core_c,cube],'arm_cable':[core(0,5),neck(4,5),sleeve(0,4)],'arm_block':[core(1,5),flange,neck(4,5),sleeve(1,4)],'item':item}
    for name,els in parts.items():
        jdump({'parent':'minecraft:block/block','render_type':'minecraft:translucent','textures':{'particle':'#fittings'},'elements':els},f'{M}/{B}template_{name}.json')
    DIRS=['north','south','east','west','up','down']; ROT={'north':{},'south':{'y':180},'east':{'y':90},'west':{'y':270},'up':{'x':270},'down':{'x':90}}
    for c in COLOURS:
        tx={'sheath':RL(f'{B}{c}_sheath'),'core':RL(f'{B}{c}_core'),'fittings':RL(f'{B}fittings'),'particle':RL(f'{B}{c}_sheath')}
        for name in parts: jdump({'parent':RL(f'{B}template_{name}'),'textures':tx},f'{M}/{B}{c}/{name}.json')
        mp=[{'apply':{'model':RL(f'{B}{c}/cube')}}]
        for d in DIRS:
            mp.append({'when':{d:'cable'},'apply':{'model':RL(f'{B}{c}/arm_cable'),**ROT[d]}})
            mp.append({'when':{d:'block'},'apply':{'model':RL(f'{B}{c}/arm_block'),**ROT[d]}})
        bid='fiber_cable' if c=='neutral' else f'{c}_fiber_cable'
        jdump({'multipart':mp},f'{A}/blockstates/{bid}.json')
        jdump({'model':{'type':'minecraft:model','model':RL(f'{B}{c}/item')}},f'{A}/items/{bid}.json')
def anchor_tex():
    """Plate 8x8 at (0,0), dense plate 10x10 at (0,8)... kept separate: (0,0) 8x8 normal, (8,0) 8x8 used as bracket sides."""
    im=img()
    def plate(x0,y0,n,seed):
        field(im,x0,y0,n,n,6,seed); bevel_frame(im,x0,y0,n,n,seed,lip=False)
        for (x,y) in ((x0+1,y0+1),(x0+n-2,y0+1),(x0+1,y0+n-2),(x0+n-2,y0+n-2)): put(im,x,y,BRASS[3])
        for (x,y) in ((x0+2,y0+2),(x0+n-1-1,y0+2),(x0+2,y0+n-1),(x0+n-1,y0+n-1)): pass
    plate(0,0,8,41)
    field(im,0,8,16,8,6,42); bevel_frame(im,3,8,10,8,43,lip=False)      # dense plate 10x10 not needed: dense uses (3,6)? see models
    for x in range(8,16):
        for y in range(0,8): put(im,x,y,g(7 if y<2 else 5 if y<6 else 4))  # bracket sides / stub steel
    return im
def anchor_dense_tex():
    im=img(); field(im,3,3,10,10,6,44); bevel_frame(im,3,3,10,10,45,lip=False)
    for (x,y) in ((4,4),(11,4),(4,11),(11,11)): put(im,x,y,BRASS[3])
    for y in range(16):
        for x in range(16):
            if im.getpixel((x,y))[3]==0: put(im,x,y,g(5))
    return im
def anchor_models():
    tx={'anchor':RL('block/cable_anchor'),'dense':RL('block/cable_anchor_dense'),'parts':RL('block/parts_steel'),'particle':RL('block/cable_anchor')}
    side=lambda: face('#anchor',[8,0,10,2])
    bolt=lambda x,y,z0,z1: el((x,y,z0),(x+1,y+1,z1),{f:face('#parts',PU['brass']) for f in ('north','east','west','up','down')})
    normal=[el((6,6,3),(10,10,5),{f:side() for f in ('east','west','up','down')}),                    # stub off the 6x6 core
            el((4,4,2),(12,12,3),{'north':face('#anchor',[0,0,8,8]),'south':face('#anchor',[0,0,8,8]),'east':side(),'west':side(),'up':side(),'down':side()})]
    normal+=[bolt(x,y,1.5,2) for x,y in ((5,5),(10,5),(5,10),(10,10))]
    dense=[el((3,3,2),(13,13,3),{'north':face('#dense',[3,3,13,13]),'south':face('#dense',[3,3,13,13]),'east':side(),'west':side(),'up':side(),'down':side()})]
    dense+=[bolt(x,y,1.5,2) for x,y in ((4,4),(11,4),(4,11),(11,11))]
    jdump({'parent':'minecraft:block/block','textures':tx,'elements':normal},f'{M}/block/cable_anchor.json')
    jdump({'parent':'minecraft:block/block','textures':tx,'elements':dense},f'{M}/block/cable_anchor_dense.json')
    jdump({'parent':'minecraft:item/generated','textures':{'layer0':RL('item/cable_anchor')}},f'{M}/item/cable_anchor.json')
    jdump({'model':{'type':'minecraft:model','model':RL('item/cable_anchor')}},f'{A}/items/cable_anchor.json')
def anchor_icon():
    im=img(); O=g(0)
    for y in range(3,13):
        for x in range(3,13):
            edge=x in (3,12) or y in (3,12)
            put(im,x,y,O if edge else g(9 if (x==4 or y==4) else 5 if (x==11 or y==11) else 7))
    for (x,y) in ((5,5),(10,5),(5,10),(10,10)): put(im,x,y,BRASS[3]); put(im,x+1,y+1,BRASS[1])
    for y in range(6,10): put(im,7,y,g(3)); put(im,8,y,g(2))                         # cable bore
    for x in range(6,10): put(im,x,7,g(3)); put(im,x,8,g(2))
    for y in range(13,15): put(im,7,y,g(6)); put(im,8,y,g(4))                         # stub
    return im

# ================= 5. CABLE FACADE =================
def facade_blank():
    im=img(); field(im,0,0,16,16,5,51); bevel_frame(im,0,0,16,16,52)
    for i in range(3,13):                                                          # faint hatch: "no block chosen"
        if i%3==0: put(im,i,i,g(6)); put(im,15-i,i,g(6))
    for (x,y) in ((2,2),(13,2),(2,13),(13,13)): put(im,x,y,g(8)); put(im,x+1,y+1,g(2))
    return im
def facade_models():
    def panel(hole):
        f=lambda X0,Y0,X1,Y1: {'north':face('#facade',[16-X1,16-Y1,16-X0,16-Y0]),'south':face('#facade',[X0,16-Y1,X1,16-Y0]),
                               'east':face('#facade',[15,16-Y1,16,16-Y0]),'west':face('#facade',[0,16-Y1,1,16-Y0]),
                               'up':face('#facade',[X0,0,X1,1]),'down':face('#facade',[X0,15,X1,16])}
        if hole is None: return [el((0,0,0),(16,16,1),f(0,0,16,16))]
        a,b=8-hole//2,8+hole//2
        return [el((0,b,0),(16,16,1),f(0,b,16,16)),el((0,0,0),(16,a,1),f(0,0,16,a)),
                el((0,a,0),(a,b,1),f(0,a,a,b)),el((b,a,0),(16,b,1),f(b,a,16,b))]
    for name,hole in (('facade_solid',None),('facade_cutout_6',6),('facade_cutout_8',8)):
        jdump({'parent':'minecraft:block/block','textures':{'facade':RL('block/facade_blank'),'particle':'#facade'},'elements':panel(hole)},f'{M}/block/{name}.json')
    jdump({'parent':RL('block/facade_solid'),'display':centred({'gui':{'rotation':[30,225,0],'scale':[0.625,0.625,0.625]},
           'ground':{'scale':[0.5,0.5,0.5]},'fixed':{'scale':[0.5,0.5,0.5]},'firstperson_righthand':{'rotation':[0,45,0],'scale':[0.4,0.4,0.4]},
           'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[0.375,0.375,0.375]}},f'{M}/block/facade_solid.json')},
           f'{M}/item/cable_facade.json')
    jdump({'model':{'type':'minecraft:model','model':RL('item/cable_facade')}},f'{A}/items/cable_facade.json')
def facade_icon():
    im=img(); O=g(0)
    for y in range(1,15):
        for x in range(1,15):
            edge=x in (1,14) or y in (1,14); hole=6<=x<=9 and 6<=y<=9
            if hole: continue
            put(im,x,y,O if edge else g(8 if (x==2 or y==2) else 4 if (x==13 or y==13) else 6))
    for (x,y) in ((3,3),(12,3),(3,12),(12,12)): put(im,x,y,g(9))
    for i in range(6,10): put(im,i,5,g(2)); put(im,5,i,g(2)); put(im,i,10,g(8)); put(im,10,i,g(8))
    return im

# ================= 6. SEGMENT ISOLATOR =================
def iso_half(base,seed):
    """The body's sides show only the top-left 10x7 (up/down) or 7x10 (sides) of this, so it's drawn for that corner
    and is the same both ways round: a lit rail along row/column 0, a shaded one along 9, and between them a recessed
    pocket in the Drive Bay's manner (step-0 cast shadow under the lit rail, a deeper far edge, a lit centre). base: the
    pocket's step, which tells the two halves apart (A dark, B light)."""
    im=img(); field(im,0,0,16,16,base,seed)
    for y in range(16):
        for x in range(16):
            a,b=min(x,y),max(x,y)
            if a==0: c=base+6 if b==0 else base+5 if b<9 else base+3
            elif b==9 or a==9: c=base+2 if a<9 else base+1
            elif a==1: c=max(0,base-2)
            elif b==8: c=base-1
            elif 3<=a and b<=6: c=base+1
            else: c=base
            put(im,x,y,g(c))
    return im
def iso_collar(glow=False,active=True):
    """12x2 collar strip twice: x0..1 / y0..11 (along v) and x4..15 / y0..1 (along u); amber light in the middle."""
    im=img()
    def px(a,along,c):
        if not glow: return c
        return None
    for a in range(12):
        for b in range(2):
            led=a in (5,6)
            if glow and not (led and active): continue
            if led: c=AMBER[4] if (b==0 and a==5) else AMBER[3] if active else (AMBER[0] if b else AMBER[1])
            else: c=g(9 if b==0 else 6) if a not in (0,11) else g(5)
            put(im,b,a,c); put(im,4+a,b,c)
    if not glow:
        for x in range(16): put(im,x,15,g(1))                                         # seam texel at (0..,15)
    return im
def iso_end():
    im=img(); field(im,0,0,10,10,4,61); bevel_frame(im,0,0,10,10,62,lip=False); port_ring(im,5,5)
    return im
def iso_models():
    B=RL('block/segment_isolator/')
    def half(z0,z1,tex):
        L=z1-z0
        return el((3,3,z0),(13,13,z1),{'east':face(tex,[0,0,L,10]),'west':face(tex,[0,0,L,10]),'up':face(tex,[0,0,10,L]),'down':face(tex,[0,0,10,L])})
    endA=el((3,3,0),(13,13,0.001),{'north':face('#end',[0,0,10,10])})
    def body(active,glow=False):
        cf={'east':face('#collar_glow' if glow else '#collar',[0,0,2,12]),'west':face('#collar_glow' if glow else '#collar',[0,0,2,12]),
            'up':face('#collar_glow' if glow else '#collar',[4,0,16,2]),'down':face('#collar_glow' if glow else '#collar',[4,0,16,2])}
        if not glow: cf.update({'north':face('#collar',[0,15,12,16]),'south':face('#collar',[0,15,12,16])})
        return el((2,2,7),(14,14,9),cf)
    for active in (False,True):
        els=[half(0,7,'#a'),half(9,16,'#b'),body(active),
             el((3,3,0),(13,13,7),{'north':face('#end',[0,0,10,10])}), el((3,3,9),(13,13,16),{'south':face('#end',[0,0,10,10])})]
        tx={'a':B+'half_a','b':B+'half_b','end':B+'end','collar':B+('collar_active' if active else 'collar_idle'),'particle':B+'half_a'}
        if active:
            tx['collar_glow']=B+'collar_glow'; gl=body(True,True); gl.update(GLOW); els.append(gl)
        jdump({'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':els},f'{M}/block/segment_isolator'+('_active' if active else '')+'.json')
    rot={'z':{},'x':{'y':90},'y':{'x':90}}
    jdump({'variants':{f'axis={a},active={s}':{'model':RL('block/segment_isolator'+('_active' if s=='true' else '')),**r}
           for a,r in rot.items() for s in ('false','true')}},f'{A}/blockstates/segment_isolator.json')
    jdump({'model':{'type':'minecraft:model','model':RL('block/segment_isolator_active')}},f'{A}/items/segment_isolator.json')

# ================= GUI: Capacitor Bank =================
def cap_gui():
    W,HH=176,80; im=Image.new('RGBA',(256,256),(0,0,0,0)); p=im.load()
    def C(s): return H(s)+(255,)
    FILL,OUT=C('#4B4B4B'),C('#141414'); L1,L2,D1,D2=C('#7E7E7E'),C('#626262'),C('#262626'),C('#363636')
    for y in range(HH):
        for x in range(W):
            r=min(x,y,W-1-x,HH-1-y)
            if r==0: c=OUT
            elif r in (1,2):
                lit=(x==r or y==r) and not (x==W-1-r or y==HH-1-r); c=(L1 if r==1 else L2) if lit else (D1 if r==1 else D2)
            else: c=FILL
            p[x,y]=c
    for x,y in ((0,0),(W-1,0),(0,HH-1),(W-1,HH-1)): p[x,y]=(0,0,0,0)
    for y in range(3,15):
        for x in range(3,W-3): p[x,y]=C('#525252')
    for x in range(3,W-3): p[x,15]=C('#444444')
    for y in range(18,70):                                                 # Arcforge energy track, exact
        for x in range(8,20):
            p[x,y]=C('#0E0E0E') if (x==8 or y==18) else C('#5C5C5C') if (x==19 or y==69) else C('#1F1F1F')
    p[19,18]=C('#1F1F1F'); p[8,69]=C('#1F1F1F')
    for y in range(18,70):                                                 # info inset
        for x in range(24,168):
            p[x,y]=C('#161616') if (x==24 or y==18) else C('#707070') if (x==167 or y==69) else C('#1F1F1F') if (x==25 or y==19) else C('#2A2A2A')
    for x in range(26,166): p[x,31]=C('#383838')
    save(im,f'{T}/gui/capacitor_bank.png')
    jdump({'includes':['common/palette.json'],'background':{'texture':'gui/capacitor_bank.png','width':176,'height':80},
           'sprites':{'energy_bar':{'sprite':'controller/energy_bar','width':10,'height':50,'fill':'bottom_up','note':'reuses the controller/Arcforge bar'}},
           'widgets':{'energy_bar':{'left':9,'top':19,'sprite':'energy_bar','tooltip':'energy'}},
           'text':{'title':{'key':'block.encodedlogistics.capacitor_bank','left':8,'top':5,'color':'TEXT'},
                   'fill':{'right':168,'top':5,'color':'ACCENT'},
                   'stored':{'left':28,'top':22,'valueLeft':84,'color':'TEXT','labelColor':'TEXT_MUTED'},
                   'input':{'left':28,'top':35,'valueLeft':84,'color':'TEXT','labelColor':'TEXT_MUTED'},
                   'output':{'left':28,'top':46,'valueLeft':84,'color':'TEXT','labelColor':'TEXT_MUTED'},
                   'capacity':{'left':28,'top':57,'valueLeft':84,'color':'TEXT','labelColor':'TEXT_MUTED'}}},
          f'{A}/screens/capacitor_bank.json')

def write_all():
    save(network_casing(1,True),f'{T}/block/network_casing_port.png'); save(network_casing(2,False),f'{T}/block/network_casing.png')
    save(parts_tex(),f'{T}/block/parts_steel.png'); save(leds_tex(),f'{T}/block/leds.png'); save(leds_tex(True),f'{T}/block/leds_glow.png')
    save(inlet_front(),f'{T}/block/power_inlet/front.png'); inlet_models()
    for s in range(5):
        save(cap_side(s),f'{T}/block/capacitor_bank/side_{s}.png')
        if s: save(cap_side(s,True),f'{T}/block/capacitor_bank/side_{s}_glow.png')
    cap_models()
    save(fittings_tex(),f'{T}/block/cable/fiber/fittings.png')
    for c in COLOURS:
        save(sheath_tex(c),f'{T}/block/cable/fiber/{c}_sheath.png')
        if c=='neutral':
            strip=Image.new('RGBA',(16,256),(0,0,0,0))
            for fr in range(16): strip.paste(core_tex(c,fr),(0,16*fr))
            save(strip,f'{T}/block/cable/fiber/{c}_core.png'); open(f'{T}/block/cable/fiber/{c}_core.png.mcmeta','w',newline='\n').write(MCMETA_CYCLE)
        else: save(core_tex(c),f'{T}/block/cable/fiber/{c}_core.png')
    fiber_models()
    save(anchor_tex(),f'{T}/block/cable_anchor.png'); save(anchor_dense_tex(),f'{T}/block/cable_anchor_dense.png'); save(anchor_icon(),f'{T}/item/cable_anchor.png'); anchor_models()
    save(facade_blank(),f'{T}/block/facade_blank.png'); save(facade_icon(),f'{T}/item/cable_facade.png'); facade_models()
    save(iso_half(2,71),f'{T}/block/segment_isolator/half_a.png'); save(iso_half(4,72),f'{T}/block/segment_isolator/half_b.png')
    save(iso_end(),f'{T}/block/segment_isolator/end.png'); save(iso_collar(active=False),f'{T}/block/segment_isolator/collar_idle.png')
    save(iso_collar(active=True),f'{T}/block/segment_isolator/collar_active.png'); save(iso_collar(glow=True),f'{T}/block/segment_isolator/collar_glow.png'); iso_models()
    cap_gui()
if __name__=='__main__':
    write_all(); n=sum(len(f) for _,_,f in os.walk(A)); print('files:',n)
