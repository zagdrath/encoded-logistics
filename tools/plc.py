# PLC + sensor modules + EEPROM cartridge. TEXTURE_STYLE: steel ramp, stepped falloff, structural detail, 1 texel / px;
# the only high-res texture is the LCD insert (a display).
import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from mr3 import *
from w2_tex import LB, OR, YE, RD, outline
import mid_tex as M
H2=H
MAG=[H2(x) for x in ('#6B1F69','#8D2E88','#B04AAA','#D464C8','#EA8ADB','#F5ABE6','#FFC8F0')]
LIME=[H2(x) for x in ('#1C6E07','#398F11','#5DB11E','#87D32E','#ADF54E','#C3FF6B','#D1FF84')]
MODULES={'presence_sensor':(MAG,'Presence Sensor'),'inventory_sensor':(OR,'Inventory Sensor'),'fluid_sensor':(LB,'Fluid Sensor'),
         'light_sensor':(YE,'Light Sensor'),'timer_module':(LIME,'Timer Module')}
P='block/plc/'
def build():
    A=Atlas(64,64)
    bp=face(14,12,S,4)
    for (x,y) in ((1,1),(12,1),(1,10),(12,10)): recess(bp,S,x,y,1,1,0)          # mounting holes
    A.add('back',bp); A.add('back_e',face(14,1,S,3)); A.add('back_s',face(1,12,S,3))
    rail=face(14,2,S,9)
    for x in range(0,14,3): px(rail,x,1,S[6])                                  # DIN rail slots
    A.add('rail',rail); A.add('rail_e',face(14,1,S,8))
    cpu=face(5,10,S,8)                                                          # CPU front: LCD socket, LEDs, keyswitch, terminals
    for x in range(1,4): px(cpu,x,1,S[1]); px(cpu,x,2,S[1])                     # LCD window (hi-res insert drawn over it)
    px(cpu,1,4,S[2]); px(cpu,3,4,S[2])                                          # RUN / ERROR LED sockets
    for x in range(1,4): px(cpu,x,5,S[2])                                       # I/O LEDs row 1 (3)
    for x in range(1,4): px(cpu,x,6,S[2])                                       # I/O LEDs row 2 (3)
    px(cpu,2,7,S[10]); px(cpu,2,8,S[5])                                         # keyswitch
    A.add('cpu',cpu); A.add('cpu_s',face(4,10,S,7)); A.add('cpu_t',face(5,4,S,9))
    term=face(5,2,S,2)
    for x in range(0,5,2): px(term,x,0,S[9]); px(term,x,1,S[6])               # screw heads
    A.add('term',term)
    E=[box('backplate',(1,2,15),(15,14,16),A,{'north':'back','up':'back_e','down':'back_e','east':'back_s','west':'back_s'}),
       box('din_rail',(1,7,14.2),(15,9,15),A,{'north':'rail','up':'rail_e','down':'rail_e','east':'rail_e','west':'rail_e'}),
       box('cpu',(9.5,3,10),(14.5,13,14.2),A,{'north':'cpu','up':'cpu_t','down':'cpu_t','east':'cpu_s','west':'cpu_s'}),
       box('cpu_term_top',(9.5,13,11),(14.5,14,14.2),A,{'north':'term','up':'term','east':'term','west':'term'}),
       box('cpu_term_bot',(9.5,2,11),(14.5,3,14.2),A,{'north':'term','down':'term','east':'term','west':'term'})]
    return A,E
def module_tex(R):
    """Module front (2 x 10): steel 8 field, an accent stripe (dye ramp, two tones) and a status LED socket."""
    im=face(2,10,S,8)
    for y in range(1,9): px(im,0,y,R[3] if y>1 else R[5])
    px(im,1,2,S[2]); return im
def module_el(slot,R_name,A):
    x0=1.5+slot*2                                                                # slots 1-4 left of the CPU (x 1.5..9.5)
    return [box(f'module{slot}',(x0,3.5,10.5),(x0+1.9,12.5,14.2),A,{'north':R_name,'up':'mod_t','down':'mod_t','east':'mod_s','west':'mod_s'}),
            box(f'module{slot}_term',(x0,12.5,11.5),(x0+1.9,13.5,14.2),A,{'north':'term','up':'term','east':'term','west':'term'})]
def lcd(state,frames):
    """LCD insert (display: 12 x 8 texels over 3 x 2 px): backlit green; text RUN / STOP / FLT + code."""
    st=Image.new('RGBA',(12,8*frames),(0,0,0,0)); BG=(0x7C,0xA8,0x5A); FG=(0x1E,0x2E,0x16)
    for f in range(frames):
        for y in range(8):
            for x in range(12): st.putpixel((x,8*f+y),BG+(255,))
        txt={'stop':'STP','run':'RUN','fault':'F15' if f%2==0 else '   '}[state]
        G={'R':['##.','#.#','##.','#.#','#.#'],'U':['#.#','#.#','#.#','#.#','###'],'N':['##.','#.#','#.#','#.#','#.#'],
           'S':['###','#..','###','..#','###'],'T':['###','.#.','.#.','.#.','.#.'],'P':['###','#.#','###','#..','#..'],
           'F':['###','#..','##.','#..','#..'],'1':['.#.','##.','.#.','.#.','###'],'5':['###','#..','###','..#','###'],' ':['...']*5}
        for i,ch in enumerate(txt):
            for ry,row in enumerate(G[ch]):
                for rx,v in enumerate(row):
                    if v=='#': st.putpixel((0+i*4+rx,8*f+1+ry),FG+(255,))
    return st
def leds(state,frames):
    """1:1 LED overlay on the CPU face (5 x 10): RUN (1,4) green, ERROR (3,4) red. The six I/O LEDs (1..3, 5..6) are
    drawn by PlcRenderer, each only while its face's level changes (amber, flickering 4 frames x 3 ticks): io_led.png."""
    st=Image.new('RGBA',(5,10*frames),(0,0,0,0))
    for f in range(frames):
        Y=10*f
        if state in ('run','stop'): st.putpixel((1,Y+4),(M.GRN[3] if state=='run' else M.AMB[2])+(255,))
        if state=='fault' and f%2==0: st.putpixel((3,Y+4),M.RED[4]+(255,))
    return st
def export(root):
    A,E=build()
    for n,(R,_) in MODULES.items(): A.add(n,module_tex(R))
    A.add('mod_t',face(2,4,S,9)); A.add('mod_s',face(4,9,S,7)); A.add('empty_slot',face(2,9,S,2))
    save(A.im,'textures/'+P+'plc.png')
    jd(model({'t':P+'plc'},E),'models/'+P+'plc.json')
    for slot in range(4):
        x0=1.5+slot*2
        jd(model({'t':P+'plc'},[box('empty',(x0+0.2,3.5,14.19),(x0+1.7,12.5,14.2),A,{'north':'empty_slot'},False)]),f'models/{P}slot{slot+1}_empty.json')
        for n in MODULES: jd(model({'t':P+'plc'},module_el(slot,n,A)),f'models/{P}slot{slot+1}_{n}.json')
    for st,(n,ft) in (('stop',(1,20)),('run',(4,3)),('fault',(2,10))):
        nl=1 if st=='run' else n                                                    # the run overlay: the RUN LED only
        save(leds(st,nl),f'textures/{P}leds_{st}.png'); (mc(f'textures/{P}leds_{st}.png',ft,5,10) if nl>1 else None)
        save(lcd(st,n),f'textures/{P}lcd_{st}.png'); (mc(f'textures/{P}lcd_{st}.png',ft,12,8) if n>1 else None)
        jd(model({'leds':P+f'leds_{st}','lcd':P+f'lcd_{st}'},[el('leds',(9.5,3,9.99),(14.5,13,9.99),{'north':F('#leds',[0,0,16,16])},False,**GLOW),
                                                             el('lcd',(10.5,10.5,9.98),(13.5,12.5,9.98),{'north':F('#lcd',[0,0,16,16])},False,**GLOW)]),f'models/{P}state_{st}.json')
    io=Image.new('RGBA',(16,16),M.AMB[3]+(255,)); save(io,f'textures/{P}io_led.png')  # PlcRenderer's I/O LED (16 x 16: whole atlas mips)
    ROT={'north':{},'east':{'y':90},'south':{'y':180},'west':{'y':270}}
    mp=[]
    for fc,r in ROT.items():
        mp.append({'when':{'facing':fc},'apply':{'model':RL(P+'plc'),**r}})
        for st in ('stop','run','fault'): mp.append({'when':{'facing':fc,'state':st},'apply':{'model':RL(f'{P}state_{st}'),**r}})
        for slot in range(1,5):
            mp.append({'when':{'facing':fc,f'slot{slot}':'empty'},'apply':{'model':RL(f'{P}slot{slot}_empty'),**r}})
            for n in MODULES: mp.append({'when':{'facing':fc,f'slot{slot}':n},'apply':{'model':RL(f'{P}slot{slot}_{n}'),**r}})
    jd({'multipart':mp},'blockstates/plc.json')
    full=E+[e for s in range(4) for e in module_el(s,'presence_sensor',A)]
    os.makedirs('shapes',exist_ok=True); json.dump({'plc':shapes(full,[(0,0,0)])},open('shapes/collision_shapes.json','w'),indent=1)
    # item icons (outlined in the material's darkest step)
    def icon_plc():
        im=Image.new('RGBA',(16,16),(0,0,0,0))
        for y in range(3,14):
            for x in range(1,15): px(im,x,y,S[4] if (x+y)<14 else S[3])
        for x in range(1,15): px(im,x,7,S[9]); px(im,x,8,S[7])
        for y in range(4,13):
            for x in range(9,14): px(im,x,y,S[8] if x<12 else S[7])
            for k,(R,_) in enumerate(list(MODULES.values())[:4]): px(im,2+k*2,y,S[8]); px(im,3+k*2,y,R[3] if y>4 else R[5])
        for x in range(10,13): px(im,x,5,(0x7C,0xA8,0x5A))
        px(im,10,8,M.GRN[3]); px(im,12,8,S[2])
        return outline(im,S[1])
    def icon_mod(R):
        im=Image.new('RGBA',(16,16),(0,0,0,0))
        for y in range(2,14):
            for x in range(5,11): px(im,x,y,S[9] if (x+y)<12 else S[8] if (x+y)<19 else S[7])
        for y in range(3,13): px(im,6,y,R[3] if y>3 else R[5]); px(im,7,y,R[2])
        for x in range(5,11,2): px(im,x,14,S[9]); px(im,x,15,S[6])
        px(im,9,4,R[5]); return outline(im,S[1])
    def icon_eeprom(written):
        im=Image.new('RGBA',(16,16),(0,0,0,0))
        for y in range(4,12):
            for x in range(2,14): px(im,x,y,S[3] if (x+y)<14 else S[2])
        for x in range(4,12): px(im,x,12,H2('#C8A040')); px(im,x,13,H2('#A07A28')) if x%2==0 else None
        for y in range(5,8):
            for x in range(5,11): px(im,x,y,(H2('#F2F0E8') if written else S[6]))
        if written:
            for x in range(6,10): px(im,x,6,S[2])
        px(im,12,5,M.GRN[3] if written else S[5]); return outline(im,S[0])
    icons={'plc':icon_plc(),'eeprom_cartridge':icon_eeprom(False),'eeprom_cartridge_written':icon_eeprom(True),**{n:icon_mod(R) for n,(R,_) in MODULES.items()}}
    for n,im in icons.items():
        save(im,f'textures/item/{n}.png'); jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{n}')}},f'models/item/{n}.json')
        if n!='eeprom_cartridge_written': jd({'model':{'type':'minecraft:model','model':RL(f'item/{n}')}},f'items/{n}.json')
    jd({'model':{'type':'minecraft:condition','property':'minecraft:has_component','component':'encodedlogistics:plc_program',
         'on_true':{'type':'minecraft:model','model':RL('item/eeprom_cartridge_written')},'on_false':{'type':'minecraft:model','model':RL('item/eeprom_cartridge')}}},'items/eeprom_cartridge.json')
    return icons
if __name__=='__main__':
    ic=export('.'); print('files',sum(len(f) for _,_,f in os.walk('src')))
