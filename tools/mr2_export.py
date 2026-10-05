import os, json, sys, shutil
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import mr2_tex as T, mr2_models as MM, mid_tex as M
from mr2_models import save, jd, mc, model, RL, el, F, GLOW
from el_style import put
ROT={'north':{},'east':{'y':90},'south':{'y':180},'west':{'y':270}}
SHAPES={}
STATES=('ipl','run','busy','attn'); NF={'ipl':(8,5),'run':(1,20),'busy':(10,4),'attn':(2,8)}
def mp_empty(block): jd({'parent':'minecraft:block/block','textures':{'particle':RL(f'block/{block}/cabinet')},'elements':[]},f'models/block/{block}/empty.json')
# ---- Midrange System ----
B='midrange_system'; A,P,_=MM.midrange('none'); save(A.im,f'textures/block/{B}/cabinet.png'); save(P.im,f'textures/block/{B}/panels.png')
for st in STATES:
    n,ft=NF[st]; save(T.ms_glow(st,n),f'textures/block/{B}/panel_{st}.png')
    if n>1: mc(f'textures/block/{B}/panel_{st}.png',ft,28,13)
mp=[]
for exp in ('none','pos','neg'):
    A,P,E=MM.midrange(exp); jd(model({'cab':f'block/{B}/cabinet','pan':f'block/{B}/panels'},E,'minecraft:cutout'),f'models/block/{B}/{B}_{exp}.json')
    SHAPES[f'{B}[expansion={exp}]']=MM.shapes(E,[(0,0,0),(1,0,0)])
    for st in STATES: jd(model({'glow':f'block/{B}/panel_{st}'},MM.midrange_glow(exp,st),'minecraft:cutout'),f'models/block/{B}/{B}_{exp}_{st}.json')
    for fc,r in ROT.items():
        mp.append({'when':{'part':'master','facing':fc,'expansion':exp},'apply':{'model':RL(f'block/{B}/{B}_{exp}'),**r}})
        for st in STATES: mp.append({'when':{'part':'master','facing':fc,'expansion':exp,'state':st},'apply':{'model':RL(f'block/{B}/{B}_{exp}_{st}'),**r}})
mp_empty(B); mp.append({'when':{'part':'dummy'},'apply':{'model':RL(f'block/{B}/empty')}})
jd({'multipart':mp},f'blockstates/{B}.json')
# ---- Expansion Cabinet ----
B='expansion_cabinet'; A,E,lamp=MM.expansion('none'); save(A.im,f'textures/block/{B}/cabinet.png')
li=Image.new('RGBA',(4,4),(0,0,0,0))
for y in range(4):
    for x in range(4): li.putpixel((x,y),M.GRN[3]+(255,) if (x,y)!=(0,0) else M.GRN[4]+(255,))
save(li,f'textures/block/{B}/lamp_on.png'); mp=[]
for att in ('none','pos','neg'):
    A,E,lamp=MM.expansion(att); jd(model({'cab':f'block/{B}/cabinet'},E),f'models/block/{B}/{B}_{att}.json')
    jd(model({'lamp':f'block/{B}/lamp_on'},[lamp],'minecraft:cutout'),f'models/block/{B}/{B}_{att}_lamp.json')
    SHAPES[f'{B}[attached={att}]']=MM.shapes(E,[(0,0,0)])
    for fc,r in ROT.items():
        mp.append({'when':{'facing':fc,'attached':att},'apply':{'model':RL(f'block/{B}/{B}_{att}'),**r}})
        mp.append({'when':{'facing':fc,'attached':att,'lit':'true'},'apply':{'model':RL(f'block/{B}/{B}_{att}_lamp'),**r}})
jd({'multipart':mp},f'blockstates/{B}.json')
# ---- Integrated Midrange System ----
B='integrated_midrange'; A,P,E=MM.integrated(); save(A.im,f'textures/block/{B}/cabinet.png'); save(P.im,f'textures/block/{B}/panels.png')
for st in STATES:
    n,ft=NF[st]; save(T.ims_glow(st,n),f'textures/block/{B}/fascia_{st}.png')
    if n>1: mc(f'textures/block/{B}/fascia_{st}.png',ft,56,24)
jd(model({'cab':f'block/{B}/cabinet','pan':f'block/{B}/panels','kb':'block/terminal_desk/keyboard'},E,'minecraft:cutout'),f'models/block/{B}/{B}.json')
for st in STATES: jd(model({'glow':f'block/{B}/fascia_{st}'},MM.integrated_glow(st),'minecraft:cutout'),f'models/block/{B}/{B}_{st}.json')
for sc in ('boot','on'): jd(model({'screen':f'block/terminal_desk/crt_screen_{sc}'},MM.integrated_screen(sc),'minecraft:cutout'),f'models/block/{B}/{B}_screen_{sc}.json')
SHAPES[B]=MM.shapes(E,[(0,0,0),(1,0,0),(0,1,0),(1,1,0)])
mp=[]
for fc,r in ROT.items():
    w={'part':'master','facing':fc}; mp.append({'when':w,'apply':{'model':RL(f'block/{B}/{B}'),**r}})
    for st in STATES: mp.append({'when':{**w,'state':st},'apply':{'model':RL(f'block/{B}/{B}_{st}'),**r}})
    for sc in ('boot','on'): mp.append({'when':{**w,'console':sc},'apply':{'model':RL(f'block/{B}/{B}_screen_{sc}'),**r}})
mp_empty(B); mp.append({'when':{'part':'dummy'},'apply':{'model':RL(f'block/{B}/empty')}})
jd({'multipart':mp},f'blockstates/{B}.json')
# ---- Keypunch ----
B='keypunch'; A,E,feed=MM.keypunch(); save(A.im,f'textures/block/{B}/cabinet.png'); save(M.card_feed(),f'textures/block/{B}/card_feed.png'); mc(f'textures/block/{B}/card_feed.png',2)
tx={'kp':f'block/{B}/cabinet','kb':'block/terminal_desk/keyboard'}
jd(model(tx,E,'minecraft:cutout'),f'models/block/{B}/{B}.json'); jd(model({**tx,'feed':f'block/{B}/card_feed'},E+[feed],'minecraft:cutout'),f'models/block/{B}/{B}_active.json')
SHAPES[B]=MM.shapes(E,[(0,0,0),(0,1,0)]); mp_empty(B)
jd({'variants':{**{f'facing={fc},part=master,active={a}':{'model':RL(f'block/{B}/{B}'+('_active' if a=='true' else '')),**r} for fc,r in ROT.items() for a in ('false','true')},
                **{f'facing={fc},part=dummy,active={a}':{'model':RL(f'block/{B}/empty')} for fc in ROT for a in ('false','true')}}},f'blockstates/{B}.json')
# ---- Card Reader ----
B='card_reader'; A,E,feed,lamps=MM.card_reader(); save(A.im,f'textures/block/{B}/cabinet.png'); save(M.cr_track(),f'textures/block/{B}/track.png'); mc(f'textures/block/{B}/track.png',1)
lg=Image.new('RGBA',(16,16),(0,0,0,0)); put(lg,3+2,4+4,M.GRN[3]); put(lg,3+4,4+4,M.AMB[2]); save(lg,f'textures/block/{B}/lamps_on.png')
tx={'cr':f'block/{B}/cabinet'}
jd(model(tx,E,'minecraft:cutout'),f'models/block/{B}/{B}.json'); jd(model({**tx,'feed':f'block/{B}/track','lamps':f'block/{B}/lamps_on'},E+[feed,lamps],'minecraft:cutout'),f'models/block/{B}/{B}_active.json')
SHAPES[B]=MM.shapes(E,[(0,0,0)])
jd({'variants':{f'facing={fc},active={a}':{'model':RL(f'block/{B}/{B}'+('_active' if a=='true' else '')),**r} for fc,r in ROT.items() for a in ('false','true')}},f'blockstates/{B}.json')
# ---- Line Printer ----
B='line_printer'; A,E,glow=MM.line_printer(); save(A.im,f'textures/block/{B}/cabinet.png')
save(M.lp_panel(),f'textures/block/{B}/panel.png'); save(M.lp_panel_glow(),f'textures/block/{B}/panel_glow.png'); mc(f'textures/block/{B}/panel_glow.png',6,28,8)
save(M.lid(),f'textures/block/{B}/lid.png'); save(M.paper(1,False),f'textures/block/{B}/paper.png'); save(M.paper(8,True),f'textures/block/{B}/paper_printing.png'); mc(f'textures/block/{B}/paper_printing.png',2)
tx={'lp':f'block/{B}/cabinet','panel':f'block/{B}/panel','lid':f'block/{B}/lid','paper':f'block/{B}/paper'}
jd(model(tx,E,'minecraft:cutout'),f'models/block/{B}/{B}.json')
jd(model({**tx,'paper':f'block/{B}/paper_printing','glow':f'block/{B}/panel_glow'},E+[glow],'minecraft:cutout'),f'models/block/{B}/{B}_active.json')
SHAPES[B]=MM.shapes(E,[(0,0,0),(1,0,0),(0,1,0),(1,1,0)]); mp_empty(B)
jd({'variants':{**{f'facing={fc},part=master,active={a}':{'model':RL(f'block/{B}/{B}'+('_active' if a=='true' else '')),**r} for fc,r in ROT.items() for a in ('false','true')},
                **{f'facing={fc},part=dummy,active={a}':{'model':RL(f'block/{B}/empty')} for fc in ROT for a in ('false','true')}}},f'blockstates/{B}.json')
# ---- items ----
ic=M.icons()
def ib(body,top,fn): return M.icon_box(body,top,fn)
def ec_fn(im):
    for x in range(4,12,2): put(im,x,12,T.c(T.CREAM,1))
    put(im,10,9,M.GRN[3])
def ims_fn(im):
    for y in range(4,8):
        for x in range(8,14): put(im,x,y,T.c(T.DARK,1))
    for x in range(9,12): put(im,x,5,M.GRN[3])
    for x in range(3,7): put(im,x,6,T.c(T.CREAM,6))
    for x in range(3,13): put(im,x,9,T.c(T.CREAM,1))
icons={'midrange_system':ib(T.CREAM,T.CREAM,lambda im:(ic['midrange_system'],None)) if False else ic['midrange_system'],
       'expansion_cabinet':ib(T.CREAM,T.CREAM,ec_fn),'integrated_midrange':ib(T.CREAM,T.CREAM,ims_fn),
       'keypunch':ic['keypunch'],'card_reader':ic['card_reader'],'line_printer':ic['line_printer']}
mag=Image.new('RGBA',(16,16),(0,0,0,0))
for y in range(4,14):
    for x in range(2,14): put(mag,x,y,T.c(T.DARK,2) if x in (2,13) or y==13 else T.c(T.DARK,1))
for k in range(4):
    for y in range(3,12): put(mag,4+k*2,y,(0xEE,0xED,0xE6) if y>3 else (0xDA,0xD8,0xCE))
for x in range(2,14): put(mag,x,4,T.c(STEEL:=T.STEEL,5))
M._out(mag,(0x1A,0x1A,0x18)); icons['diskette_magazine']=mag
for n,im in icons.items():
    save(im,f'textures/item/{n}.png'); jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{n}')}},f'models/item/{n}.json')
    jd({'model':{'type':'minecraft:model','model':RL(f'item/{n}')}},f'items/{n}.json')
for n,im in (('punch_card',M.punch_card(False)),('punch_card_punched',M.punch_card(True)),('diskette_8in',M.diskette(False)),('diskette_8in_written',M.diskette(True))):
    save(im,f'textures/item/{n}.png'); jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{n}')}},f'models/item/{n}.json')
jd({'model':{'type':'minecraft:condition','property':'minecraft:has_component','component':'encodedlogistics:punched_recipe',
     'on_true':{'type':'minecraft:model','model':RL('item/punch_card_punched')},'on_false':{'type':'minecraft:model','model':RL('item/punch_card')}}},'items/punch_card.json')
jd({'model':{'type':'minecraft:condition','property':'minecraft:has_component','component':'encodedlogistics:diskette_recipes',
     'on_true':{'type':'minecraft:model','model':RL('item/diskette_8in_written')},'on_false':{'type':'minecraft:model','model':RL('item/diskette_8in')}}},'items/diskette_8in.json')
os.makedirs('shapes',exist_ok=True); json.dump(SHAPES,open('shapes/collision_shapes.json','w'),indent=1)
# sounds (synthesised earlier for the Midrange line)
src='/home/claude/mid/src/main/resources/assets/encodedlogistics/'
shutil.copytree(src+'sounds/block/midrange','src/main/resources/assets/encodedlogistics/sounds/block/midrange',dirs_exist_ok=True)
shutil.copy(src+'sounds.midrange.json','src/main/resources/assets/encodedlogistics/sounds.midrange.json')
print('files',sum(len(f) for _,_,f in os.walk('src')))
