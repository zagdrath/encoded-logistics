import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import mr4 as V
from mr3 import save, jd, mc, model, ROT, RL, shapes, el, F, GLOW
from PIL import Image
import mid_tex as M
SH=json.load(open('shapes/collision_shapes.json'))
NF={'ipl':(8,5),'run':(1,20),'busy':(10,4),'attn':(2,8)}
def anim(im,p,ft,n,w,h): save(im,p); (mc(p,ft,w,h) if n>1 else None)
import shutil
for b in ('midrange_system','integrated_midrange','line_printer','disk_drive','tape_drive'):
    shutil.rmtree(f'src/main/resources/assets/encodedlogistics/textures/block/{b}',ignore_errors=True)
    shutil.rmtree(f'src/main/resources/assets/encodedlogistics/models/block/{b}',ignore_errors=True)
# Midrange System
B='midrange_system'; LAMPS=[(1,1,'power'),(2,1,'attn'),(3,1,'activity')]          # 1:1 lamps on the operator strip (texture coords)
for st in NF:
    n,ft=NF[st]
    anim(V.lamps_glow(st,n,7,3,[(6-x,y,r) for x,y,r in LAMPS]),f'textures/block/{B}/lamps_{st}.png',ft,n,7,3)
    anim(V.disp_glow(st,n,2,12,5),f'textures/block/{B}/display_{st}.png',ft,n,12,5)
mp=[]
for exp in (False,True):
    A,_,E,glow=V.midrange(exp); suf='_expansion' if exp else ''
    save(A.im,f'textures/block/{B}/cabinet{suf}.png'); jd(model({'t':f'block/{B}/cabinet{suf}'},E),f'models/block/{B}/{B}{suf}.json')
    for fc,r in ROT.items(): mp.append({'when':{'facing':fc,'expansion':str(exp).lower()},'apply':{'model':RL(f'block/{B}/{B}{suf}'),**r}})
for st in NF:
    jd(model({'lamps':f'block/{B}/lamps_{st}','disp':f'block/{B}/display_{st}'},V.midrange(False)[3]),f'models/block/{B}/{B}_{st}.json')
    for fc,r in ROT.items(): mp.append({'when':{'facing':fc,'state':st},'apply':{'model':RL(f'block/{B}/{B}_{st}'),**r}})
jd({'multipart':mp},f'blockstates/{B}.json'); SH['shapes'][B]=shapes(V.midrange(False)[2],[(0,0,0)])
# Integrated
B='integrated_midrange'; A,_,E,glow,screen=V.integrated(); save(A.im,f'textures/block/{B}/cabinet.png')
IL=[(12-x,y,r) for x,y,r in ((1,2,'power'),(3,2,'attn'),(5,2,'activity'))]
for st in NF:
    n,ft=NF[st]; anim(V.lamps_glow(st,n,13,7,IL),f'textures/block/{B}/lamps_{st}.png',ft,n,13,7); anim(V.disp_glow(st,n,3,12,5),f'textures/block/{B}/display_{st}.png',ft,n,12,5)
jd(model({'t':f'block/{B}/cabinet','kb':'block/terminal_desk/keyboard'},E),f'models/block/{B}/{B}.json')
for st in NF: jd(model({'lamps':f'block/{B}/lamps_{st}','disp':f'block/{B}/display_{st}'},glow),f'models/block/{B}/{B}_{st}.json')
for sc in ('boot','on'): jd(model({'screen':f'block/terminal_desk/crt_screen_{sc}'},screen),f'models/block/{B}/{B}_screen_{sc}.json')
jd({'parent':'minecraft:block/block','textures':{'particle':RL(f'block/{B}/cabinet')},'elements':[]},f'models/block/{B}/empty.json')
SH['shapes'][B]=shapes(E,SH['footprints'][B])
# Line Printer
B='line_printer'; A,_,E,glow=V.line_printer(); save(A.im,f'textures/block/{B}/cabinet.png'); anim(V.lp_glow1(),f'textures/block/{B}/panel_glow.png',6,2,20,5)
save(M.paper(1,False),f'textures/block/{B}/paper.png'); save(M.paper(8,True),f'textures/block/{B}/paper_printing.png'); mc(f'textures/block/{B}/paper_printing.png',2)
jd(model({'t':f'block/{B}/cabinet','paper':f'block/{B}/paper'},E),f'models/block/{B}/{B}.json')
jd(model({'t':f'block/{B}/cabinet','paper':f'block/{B}/paper_printing','glow':f'block/{B}/panel_glow'},E+[glow]),f'models/block/{B}/{B}_active.json')
jd({'parent':'minecraft:block/block','textures':{'particle':RL(f'block/{B}/cabinet')},'elements':[]},f'models/block/{B}/empty.json')
SH['shapes'][B]=shapes(E,SH['footprints'][B])
# 1311 Disk Drive + 729 Tape Drive: solid model + translucent glass model (multipart)
save(V.glass_tex(),'textures/block/disk_drive/glass.png'); save(V.glass_tex(),'textures/block/tape_drive/glass.png')
lamp=Image.new('RGBA',(16,16),M.GRN[3]+(255,)); save(lamp,'textures/block/disk_drive/lamp_on.png')
A,E,glass,lamp_el=V.disk_drive(); save(A.im,'textures/block/disk_drive/cabinet.png')
jd(model({'t':'block/disk_drive/cabinet'},E),'models/block/disk_drive/disk_drive.json')
jd(model({'glass':'block/disk_drive/glass'},glass,'minecraft:translucent'),'models/block/disk_drive/disk_drive_glass.json')
jd(model({'lamp':'block/disk_drive/lamp_on'},[lamp_el]),'models/block/disk_drive/disk_drive_lamp.json')
mp=[]
for fc,r in ROT.items():
    mp+=[{'when':{'facing':fc},'apply':{'model':RL('block/disk_drive/disk_drive'),**r}},{'when':{'facing':fc},'apply':{'model':RL('block/disk_drive/disk_drive_glass'),**r}},
         {'when':{'facing':fc,'active':'true'},'apply':{'model':RL('block/disk_drive/disk_drive_lamp'),**r}}]
jd({'multipart':mp},'blockstates/disk_drive.json'); SH['shapes']['disk_drive']=shapes(E,[(0,0,0)])
A,E,glass,glow,top=V.tape_drive(); save(A.im,'textures/block/tape_drive/cabinet.png')
tg=Image.new('RGBA',(18,8),(0,0,0,0)); tg.putpixel((14,4),(0xFF,0xD7,0x56,255)); tg.putpixel((10,4),M.GRN[3]+(255,)); save(tg,'textures/block/tape_drive/lamps_on.png')
jd(model({'t':'block/tape_drive/cabinet'},E),'models/block/tape_drive/tape_drive.json')
jd(model({'t':'block/tape_drive/cabinet'},top),'models/block/tape_drive/tape_drive_top.json')
jd(model({'glass':'block/tape_drive/glass'},glass,'minecraft:translucent'),'models/block/tape_drive/tape_drive_glass.json')
jd(model({'glow':'block/tape_drive/lamps_on'},[glow]),'models/block/tape_drive/tape_drive_lamps.json')
jd({'parent':'minecraft:block/block','textures':{'particle':RL('block/tape_drive/cabinet')},'elements':[]},'models/block/tape_drive/empty.json')
mp=[]
for fc,r in ROT.items():
    w={'facing':fc,'part':'master'}; wt={'facing':fc,'part':'top'}
    mp+=[{'when':w,'apply':{'model':RL('block/tape_drive/tape_drive'),**r}},{'when':w,'apply':{'model':RL('block/tape_drive/tape_drive_glass'),**r}},
         {'when':wt,'apply':{'model':RL('block/tape_drive/tape_drive_top'),**r}},{'when':{**wt,'active':'true'},'apply':{'model':RL('block/tape_drive/tape_drive_lamps'),**r}}]
mp.append({'when':{'part':'middle'},'apply':{'model':RL('block/tape_drive/empty')}})
jd({'multipart':mp},'blockstates/tape_drive.json')
SH['shapes']['tape_drive']={**shapes(E,[(0,0,0),(0,1,0)]),'0,2,0':shapes(top,[(0,0,0)])['0,0,0']}; SH['footprints']['tape_drive']=[(0,0,0),(0,1,0),(0,2,0)]
json.dump(SH,open('shapes/collision_shapes.json','w'),indent=1)
print('ok')
