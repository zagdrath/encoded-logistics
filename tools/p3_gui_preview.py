import os
from PIL import Image
A=Image.open('/home/claude/ascii.png').convert('RGBA')
def glyph(ch):
    o=ord(ch); gx,gy=(o%16)*8,(o//16)*8; g=A.crop((gx,gy,gx+8,gy+8))
    return g,(3 if ch==' ' else max([x for x in range(8) for y in range(8) if g.getpixel((x,y))[3]],default=-1)+1)
def tw(s): return sum(glyph(c)[1]+1 for c in s)-1
def T(img,x,y,s,col,shadow=False):
    r,g_,b=(int(col[i:i+2],16) for i in (1,3,5))
    for ch in s:
        g,w=glyph(ch)
        for dx,dy,cc in ([(1,1,(r//4,g_//4,b//4))] if shadow else [])+[(0,0,(r,g_,b))]:
            for yy in range(8):
                for xx in range(8):
                    if g.getpixel((xx,yy))[3]: img.putpixel((x+xx+dx,y+yy+dy),cc+(255,))
        x+=w+1
ROOTS=['/home/claude/p3/src/main/resources/assets/encodedlogistics/textures/','/home/claude/p2/src/main/resources/assets/encodedlogistics/textures/',
       '/home/claude/p1/src/main/resources/assets/encodedlogistics/textures/','/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/']
def tex(p):
    for R in ROOTS:
        if os.path.exists(R+p): return Image.open(R+p).convert('RGBA')
    raise FileNotFoundError(p)
it=lambda n: tex(f'item/{n}.png')
def ghost(img,n,x,y):
    i=it(n).copy(); i.putalpha(i.split()[3].point(lambda v:v*0.55)); img.alpha_composite(i,(x,y))
def button(img,x,y,w,h,label,state='',col='#F0F0F0'):
    b=tex(f'gui/sprites/common/button_wide{state}.png')
    seg=b.crop((0,0,w-3,h)) if h==18 else b.crop((0,0,w-3,h-1)).resize((w-3,h),Image.NEAREST)
    img.alpha_composite(b.crop((0,0,w-3,18)).resize((w-3,h),Image.NEAREST),(x,y)); img.alpha_composite(b.crop((197,0,200,18)).resize((3,h),Image.NEAREST),(x+w-3,y))
    T(img,x+(w-tw(label))//2,y+(h-8)//2,label,col)
def finish(im,name,scale=3):
    bg=Image.new('RGBA',im.size,(36,37,40,255)); bg.alpha_composite(im); bg.resize((im.width*scale,im.height*scale),Image.NEAREST).save('/home/claude/p3/previews/'+name)
P='/home/claude/p3/previews/'
# --- Schematic Encoder, both modes
for mode in ('crafting','processing'):
    rows=3; sec=tex(f'gui/terminal/encoder_{mode}.png').crop((0,0,195,76)); top=tex('gui/terminal/top.png').crop((0,0,195,19)); row=tex('gui/terminal/row.png').crop((0,0,195,18))
    bot=tex('gui/terminal/bottom_plain.png').crop((0,0,195,99)); H_=19+18*rows+76+99
    c=Image.new('RGBA',(219,H_),(0,0,0,0)); c.alpha_composite(top,(24,0))
    for r in range(rows): c.alpha_composite(row,(24,19+18*r))
    cy=19+18*rows; c.alpha_composite(sec,(24,cy)); c.alpha_composite(bot,(24,cy+76))
    for i,n in enumerate(['silicon_wafer','doped_silicon','gallium_ingot','processor_die','heatsink','memory_die','ferrite','copper_foil','tantalum_capacitor','circuit_substrate','logic_die']):
        c.alpha_composite(it(n),(24+9+(i%9)*18,20+(i//9)*18))
    T(c,32,6,'Schematic Encoder','#F0F0F0')
    c.alpha_composite(tex('gui/sprites/terminal/button.png'),(24+8,cy+8)); c.alpha_composite(tex(f'gui/sprites/encoder/mode_{mode}.png'),(24+9,cy+9))
    c.alpha_composite(tex('gui/sprites/terminal/clear_grid.png'),(24+84,cy+8))
    if mode=='crafting':
        for i,n in enumerate(['ferrite','processor_die','ferrite','circuit_substrate','heatsink','circuit_substrate','ferrite','processor_die','ferrite']): ghost(c,n,24+31+(i%3)*18,cy+9+(i//3)*18)
        ghost(c,'thread_unit' if False else 'heatsink',24+119,cy+26)
    else:
        for i,n in enumerate(['raw_gallium','raw_gallium']): ghost(c,n,24+31+i*18,cy+9)
        ghost(c,'gallium_ingot',24+117,cy+9)
    c.alpha_composite(it('schematic_card'),(24+161,cy+9)); button(c,24+150,cy+29,38,14,'Encode')
    c.alpha_composite(it(f'encoded_schematic_{mode}'),(24+161,cy+47))
    T(c,33,cy+76+6,'Inventory','#B4B4B4')
    big=Image.new('RGBA',c.size,(36,37,40,255)); big.alpha_composite(c); big=big.resize((c.width*2,c.height*2),Image.NEAREST)
    if mode=='processing':
        for (x,y,s) in ((24+31,cy+9,'4'),(24+49,cy+9,'4'),(24+117,cy+9,'2')): T(big,2*(x+17)-tw(s)-1,2*(y+16)-9,s,'#F0F0F0',True)
    big.resize((big.width*3//2,big.height*3//2),Image.NEAREST).save(P+f'encoder_{mode}_gui.png')
# --- Fabricator
g=tex('gui/fabricator.png').crop((0,0,176,166)); T(g,8,5,'Fabricator','#F0F0F0'); T(g,8,72,'Inventory','#B4B4B4')
for i in range(5): g.alpha_composite(it('encoded_schematic_crafting'),(45+(i%3)*18,18+(i//3)*18))
g.alpha_composite(tex('gui/sprites/lithography_press/progress.png').crop((0,0,14,17)),(108,35)); g.alpha_composite(it('throughput_module'),(153,27))
g.alpha_composite(tex('gui/sprites/port/ghost_module.png'),(153,45)); finish(g,'fabricator_gui.png')
# --- Gateway
g=tex('gui/gateway.png').crop((0,0,176,206)); T(g,8,5,'Gateway','#F0F0F0'); T(g,8,39,'Keep stocked','#B4B4B4'); T(g,8,72,'Buffer','#B4B4B4'); T(g,8,112,'Inventory','#B4B4B4')
for i in range(3): g.alpha_composite(it('encoded_schematic_processing'),(8+18*i,18))
for i,n in enumerate(['silicon_wafer','gallium_ingot','heatsink']): ghost(g,n,8+18*i,50)
for i,n in enumerate(['raw_gallium','silicon_wafer']): g.alpha_composite(it(n),(9+18*i,84))
big=Image.new('RGBA',g.size,(36,37,40,255)); big.alpha_composite(g); big=big.resize((g.width*2,g.height*2),Image.NEAREST)
for i,s in enumerate(['64','16','8']): T(big,2*(8+18*i+17)-tw(s)-1,2*(50+16)-9,s,'#F0F0F0',True)
for i,s in enumerate(['4','12']): T(big,2*(9+18*i+17)-tw(s)-1,2*(84+16)-9,s,'#F0F0F0',True)
big.resize((big.width*3//2,big.height*3//2),Image.NEAREST).save(P+'gateway_gui.png')
# --- Scheduler Core
g=tex('gui/scheduler_core.png').crop((0,0,208,186)); T(g,8,5,'Scheduler Core','#F0F0F0'); T(g,8,18,'Active jobs','#B4B4B4'); T(g,8,98,'Queue','#B4B4B4')
fill=tex('gui/sprites/common/bar_fill_mint.png'); gold=tex('gui/sprites/common/bar_fill_gold.png')
for r,(n,label,pct) in enumerate((('storage_drive_2m','2M Storage Drive x2',0.62),('processor_die','Processor Die x16',0.3),('heatsink','Heatsink x8',0.85))):
    y=30+r*20; g.alpha_composite(it(n),(12,y+2)); T(g,32,y+2,label,'#F0F0F0'); g.alpha_composite(fill.crop((0,0,int(120*pct),6)),(32,y+12))
    g.alpha_composite(tex('gui/sprites/common/cancel_small.png'),(188,y+5))
for r,(n,label) in enumerate((('fabricator','Fabricator x1'),('gateway','Gateway x4'))):
    y=110+r*20; T(g,32,y+6,label,'#B4B4B4')
g.alpha_composite(gold.crop((0,0,68,6)),(9,163)); g.alpha_composite(fill.crop((0,0,40,6)),(109,163))
T(g,8,172,'Threads 6 / 8','#B4B4B4'); T(g,108,172,'Buffer 18K / 40K','#B4B4B4'); finish(g,'scheduler_core_gui.png')
# --- Craft amount
g=tex('gui/craft_amount.png').crop((0,0,176,92)); T(g,8,5,'Craft amount','#F0F0F0'); g.alpha_composite(it('storage_drive_2m'),(13,31)); T(g,44,39,'4','#F0F0F0')
for lab,x,y in (('+1',40,18),('+10',66,18),('+64',92,18),('-1',40,54),('-10',66,54),('-64',92,54)): button(g,x,y,24,14,lab)
button(g,124,34,44,18,'Next','_hover'); finish(g,'craft_amount_gui.png')
# --- Craft plan
g=tex('gui/craft_plan.png').crop((0,0,220,196)); T(g,8,5,'Craft plan','#F0F0F0'); T(g,212-tw('1 missing'),18,'1 missing','#FF6B6B')
for lab,x in (('Have',122),('Make',152),('Miss',182)): T(g,x+10-tw(lab),31,lab,'#B4B4B4')
rows=[(0,'storage_drive_2m','2M Drive','0','4','0'),(1,'storage_die_2m','2M Die','0','4','0'),(2,'storage_die_512k','512K Die','2','14','0'),
      (2,'processor_die','Processor','6','0','0'),(1,'circuit_substrate','Substrate','9','0','0'),(2,'gallium_ingot','Gallium','3','0','5')]
ex=tex('gui/sprites/common/tree_collapse.png')
for r,(lvl,n,label,st,cr,ms) in enumerate(rows):
    y=44+r*18; x=10+lvl*8
    if lvl<2 and r<5: g.alpha_composite(ex,(x,y+5))
    g.alpha_composite(it(n),(x+8,y+1)); T(g,x+26,y+5,label,'#F0F0F0')
    T(g,122+10-tw(st),y+5,st,'#F0F0F0'); T(g,152+10-tw(cr),y+5,cr,'#00D992' if cr!='0' else '#7A7A7A'); T(g,182+10-tw(ms),y+5,ms,'#FF6B6B' if ms!='0' else '#7A7A7A')
button(g,8,168,104,18,'Scheduler: Auto'); button(g,118,168,46,18,'Start','_disabled','#7A7A7A'); button(g,166,168,46,18,'Cancel')
finish(g,'craft_plan_gui.png')
# --- Job status
g=tex('gui/job_status.png').crop((0,0,220,150)); T(g,8,5,'Job status','#F0F0F0'); g.alpha_composite(it('storage_drive_2m'),(9,21)); T(g,32,20,'2M Storage Drive x4','#F0F0F0')
g.alpha_composite(fill.crop((0,0,int(178*0.45),6)),(33,32))
for i,(n,lab,pct) in enumerate((('storage_die_2m','2M Die 2/4',0.5),('storage_die_512k','512K Die 9/16',0.56),('processor_die','Processor 4/4',1.0),('heatsink','Heatsink 1/2',0.5))):
    x=10+(i%2)*100; y=50+(i//2)*22; g.alpha_composite(it(n),(x,y)); T(g,x+18,y+1,lab,'#F0F0F0'); g.alpha_composite(gold.crop((0,0,int(76*pct),6)),(x+18,y+11))
button(g,166,124,46,18,'Cancel'); finish(g,'job_status_gui.png')
print('ok')
