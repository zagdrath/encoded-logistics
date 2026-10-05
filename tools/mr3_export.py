import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from mr3 import *
from PIL import Image
import mid_tex as M
NF={'ipl':(8,5),'run':(1,20),'busy':(10,4),'attn':(2,8)}
FOOT={}
def footprint(name,blocks): FOOT[name]=blocks
# Midrange System (single block, centred)
B='midrange_system'
for st in NF:
    n,ft=NF[st]; save(ms_glow(st,n),f'textures/block/{B}/panel_{st}.png'); (mc(f'textures/block/{B}/panel_{st}.png',ft,28,12) if n>1 else None)
mp=[]
for exp in (False,True):
    A,P,E,glow=midrange(exp); suf='_expansion' if exp else ''
    save(A.im,f'textures/block/{B}/cabinet.png'); save(P.im,f'textures/block/{B}/panels{suf}.png')
    jd(model({'t':f'block/{B}/cabinet','p':f'block/{B}/panels{suf}'},E),f'models/block/{B}/{B}{suf}.json')
    for st in NF: jd(model({'glow':f'block/{B}/panel_{st}'},glow),f'models/block/{B}/{B}_{st}.json')
    for fc,r in ROT.items():
        mp.append({'when':{'facing':fc,'expansion':str(exp).lower()},'apply':{'model':RL(f'block/{B}/{B}{suf}'),**r}})
SH[B]=shapes(midrange(False)[2],[(0,0,0)]); footprint(B,[(0,0,0)])
for fc,r in ROT.items():
    for st in NF: mp.append({'when':{'facing':fc,'state':st},'apply':{'model':RL(f'block/{B}/{B}_{st}'),**r}})
jd({'multipart':mp},f'blockstates/{B}.json')
# Expansion Cabinet
B='expansion_cabinet'; li=Image.new('RGBA',(16,16),(0,0,0,0))
for y in range(16):
    for x in range(16): li.putpixel((x,y),(M.GRN[4] if (x,y)==(0,0) else M.GRN[3])+(255,))
save(li,f'textures/block/{B}/lamp_on.png'); mp=[]
for att in ('none','neg','pos'):
    A,E,lamp=expansion(att); save(A.im,f'textures/block/{B}/cabinet.png')
    jd(model({'t':f'block/{B}/cabinet'},E),f'models/block/{B}/{B}_{att}.json'); jd(model({'lamp':f'block/{B}/lamp_on'},[lamp]),f'models/block/{B}/{B}_{att}_lamp.json')
    SH[f'{B}[attached={att}]']=shapes(E,[(0,0,0)])
    for fc,r in ROT.items():
        mp.append({'when':{'facing':fc,'attached':att},'apply':{'model':RL(f'block/{B}/{B}_{att}'),**r}})
        mp.append({'when':{'facing':fc,'attached':att,'lit':'true'},'apply':{'model':RL(f'block/{B}/{B}_{att}_lamp'),**r}})
jd({'multipart':mp},f'blockstates/{B}.json'); footprint(B,[(0,0,0)])
# Integrated (3 x 2, master = bottom centre)
B='integrated_midrange'; A,P,E,glow,screen=integrated(); save(A.im,f'textures/block/{B}/cabinet.png'); save(P.im,f'textures/block/{B}/panels.png')
for st in NF:
    n,ft=NF[st]; save(ims_glow(st,n),f'textures/block/{B}/fascia_{st}.png'); (mc(f'textures/block/{B}/fascia_{st}.png',ft,52,28) if n>1 else None)
jd(model({'t':f'block/{B}/cabinet','p':f'block/{B}/panels','kb':'block/terminal_desk/keyboard'},E),f'models/block/{B}/{B}.json')
for st in NF: jd(model({'glow':f'block/{B}/fascia_{st}'},glow),f'models/block/{B}/{B}_{st}.json')
for sc in ('boot','on'): jd(model({'screen':f'block/terminal_desk/crt_screen_{sc}'},screen),f'models/block/{B}/{B}_screen_{sc}.json')
blocks=[(-1,0,0),(0,0,0),(1,0,0),(-1,1,0),(0,1,0)]; SH[B]=shapes(E,blocks); footprint(B,blocks)
mp=[]
for fc,r in ROT.items():
    w={'part':'master','facing':fc}; mp.append({'when':w,'apply':{'model':RL(f'block/{B}/{B}'),**r}})
    for st in NF: mp.append({'when':{**w,'state':st},'apply':{'model':RL(f'block/{B}/{B}_{st}'),**r}})
    for sc in ('boot','on'): mp.append({'when':{**w,'console':sc},'apply':{'model':RL(f'block/{B}/{B}_screen_{sc}'),**r}})
jd({'parent':'minecraft:block/block','textures':{'particle':RL(f'block/{B}/cabinet')},'elements':[]},f'models/block/{B}/empty.json')
mp.append({'when':{'part':'dummy'},'apply':{'model':RL(f'block/{B}/empty')}}); jd({'multipart':mp},f'blockstates/{B}.json')
ZONES={'console':[e['name'] for e in E if e['name'].startswith('console')],'control_panel':['body_left','magazine_unit'],'none':['body_right','plinth']}
# simple 2-part / single blocks
def simple(B,E,A,tx,extra,blocks,active_parts):
    save(A.im,f'textures/block/{B}/cabinet.png')
    jd(model({**{'t':f'block/{B}/cabinet'},**tx},E),f'models/block/{B}/{B}.json')
    jd(model({**{'t':f'block/{B}/cabinet'},**tx,**active_parts[0]},E+active_parts[1]),f'models/block/{B}/{B}_active.json')
    SH[B]=shapes(E,blocks); footprint(B,blocks)
    v={}
    for fc,r in ROT.items():
        for a in ('false','true'):
            if len(blocks)>1:
                v[f'facing={fc},part=master,active={a}']={'model':RL(f'block/{B}/{B}'+('_active' if a=='true' else '')),**r}
                v[f'facing={fc},part=dummy,active={a}']={'model':RL(f'block/{B}/empty')}
            else: v[f'facing={fc},active={a}']={'model':RL(f'block/{B}/{B}'+('_active' if a=='true' else '')),**r}
    if len(blocks)>1: jd({'parent':'minecraft:block/block','textures':{'particle':RL(f'block/{B}/cabinet')},'elements':[]},f'models/block/{B}/empty.json')
    jd({'variants':v},f'blockstates/{B}.json')
A,E,feed=keypunch(); save(M.card_feed(),'textures/block/keypunch/card_feed.png'); mc('textures/block/keypunch/card_feed.png',2)
simple('keypunch',E,A,{'kb':'block/terminal_desk/keyboard'},None,[(0,0,0),(0,1,0)],({'feed':'block/keypunch/card_feed'},[feed]))
A,E,feed,lamps=card_reader(); save(M.cr_track(),'textures/block/card_reader/track.png'); mc('textures/block/card_reader/track.png',1)
lg=Image.new('RGBA',(16,16),(0,0,0,0))
for (x,y,c) in ((2,6,M.GRN[3]),(5,6,M.AMB[2]),(8,6,M.GRN[3])):
    for dx in range(2):
        for dy in range(3): lg.putpixel((x+dx,y+dy),c+(255,))
save(lg,'textures/block/card_reader/lamps_on.png')
simple('card_reader',E,A,{},None,[(0,0,0)],({'feed':'block/card_reader/track','lamps':'block/card_reader/lamps_on'},[feed,lamps]))
A,P,E,glow=line_printer(); save(P.im,'textures/block/line_printer/panels.png'); save(lp_glow(),'textures/block/line_printer/panel_glow.png'); mc('textures/block/line_printer/panel_glow.png',6,64,12)
save(M.paper(1,False),'textures/block/line_printer/paper.png'); save(M.paper(8,True),'textures/block/line_printer/paper_printing.png'); mc('textures/block/line_printer/paper_printing.png',2)
simple('line_printer',E,A,{'p':'block/line_printer/panels','paper':'block/line_printer/paper'},None,[(0,0,0),(0,1,0)],({'paper':'block/line_printer/paper_printing','glow':'block/line_printer/panel_glow'},[glow]))
A,P,E,win,lamp=disk_drive(); save(P.im,'textures/block/disk_drive/panels.png')
save(disk_window(4,False),'textures/block/disk_drive/window.png'); mc('textures/block/disk_drive/window.png',100,40,16)
save(disk_window(4,True),'textures/block/disk_drive/window_spin.png'); mc('textures/block/disk_drive/window_spin.png',2,40,16)
dl=Image.new('RGBA',(16,16),M.GRN[3]+(255,)); save(dl,'textures/block/disk_drive/lamp_on.png')
simple('disk_drive',E+[win],A,{'p':'block/disk_drive/panels','win':'block/disk_drive/window'},None,[(0,0,0)],({'win':'block/disk_drive/window_spin','lamp':'block/disk_drive/lamp_on'},[lamp]))
A,P,E,win,ctl,glow=tape_drive(); save(P.im,'textures/block/tape_drive/panels.png')
save(window_reels(4,False),'textures/block/tape_drive/reels.png'); mc('textures/block/tape_drive/reels.png',100,48,40)
save(window_reels(4,True),'textures/block/tape_drive/reels_spin.png'); mc('textures/block/tape_drive/reels_spin.png',3,48,40)
tg=Image.new('RGBA',(48,10),(0,0,0,0))
for i in (0,1): 
    for x in range(14+i*5,15+i*5): tg.putpixel((x,3),(M.AMB[3] if i==0 else M.GRN[3])+(255,))
save(tg,'textures/block/tape_drive/lamps_on.png')
simple('tape_drive',E+[win,ctl],A,{'p':'block/tape_drive/panels','reels':'block/tape_drive/reels'},None,[(0,0,0),(0,1,0)],({'reels':'block/tape_drive/reels_spin','glow':'block/tape_drive/lamps_on'},[glow]))
# items: new icons (disk drive, tape drive, tape reel) - others unchanged from the shipped textures
from w2_tex import outline, px as wpx
def icon(kind):
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    if kind=='tape_reel':
        import math
        for y in range(16):
            for x in range(16):
                d=math.hypot(x-7.5,y-7.5)
                if d<=6.8: wpx(im,x,y,S[9] if d>5.6 else S[3] if d>2 else S[8])
        for (x,y) in ((7,3),(8,3),(3,8),(12,8)): wpx(im,x,y,S[1])
        return outline(im,S[0])
    body=BLUE if kind=='disk_drive' else CREAM
    top,bot=(3,13) if kind=='disk_drive' else (1,15)
    for y in range(top,bot):
        for x in range(3,13): wpx(im,x,y,(CREAM[9] if y<top+3 else body[6] if x<8 else body[5]) if kind=='disk_drive' else (CREAM[9] if x<8 else CREAM[8]))
    if kind=='disk_drive':
        for x in range(8,12): wpx(im,x,top-1,BLUE[7]); wpx(im,x,top-2,BLUE[8])
        for x in range(4,7): wpx(im,x,top+1,S[1])
    else:
        for y in range(top+5,top+9):
            for x in range(4,12): wpx(im,x,y,S[1])
        wpx(im,6,top+7,S[9]); wpx(im,9,top+7,S[7])
        for y in range(top+10,bot-1): wpx(im,7,y,BLUE[6]); wpx(im,8,y,BLUE[5])
    return outline(im,CREAM[1])
for n in ('disk_drive','tape_drive','tape_reel'):
    save(icon(n),f'textures/item/{n}.png'); jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{n}')}},f'models/item/{n}.json')
    jd({'model':{'type':'minecraft:model','model':RL(f'item/{n}')}},f'items/{n}.json')
os.makedirs('shapes',exist_ok=True); json.dump({'shapes':SH,'footprints':FOOT,'integrated_click_zones':ZONES},open('shapes/collision_shapes.json','w'),indent=1)
print('files',sum(len(f) for _,_,f in os.walk('src')))
