import os,sys,random
sys.path.insert(0,'/home/claude/rack3/tools')
src=open('/home/claude/rack2/tools/gui2_preview.py').read()
head=src.split("P='/home/claude/rack2/previews/'")[0].replace("ROOTS=['/home/claude/rack2/","ROOTS=['/home/claude/rack3/src/main/resources/assets/encodedlogistics/textures/','/home/claude/rack2/")
exec(head)
exec('def popup'+src.split('def popup')[1].split('POP=[')[0])
P='/home/claude/rack3/previews/'; out=[]
# Wireless Controller
c=panel('wireless_controller','Wireless Controller')
for r,(n,where,on) in enumerate((('Cody','Overworld',1),('Alex','The Nether',1),('Sam','Overworld',0),('Guest','The End',0))):
    y=22+r*13; c.alpha_composite(tex('gui/sprites/rack/firewall/head_placeholder.png'),(11,y+2)); T(c,21,y+3,n,'#F0F0F0'); T(c,130-tw(where),y+3,where,'#9A9A9A')
    c.alpha_composite(tex('gui/sprites/hud/'+('dot_online' if on else 'dot_offline')+'.png'),(136,y+4)); c.alpha_composite(tex('gui/sprites/common/cancel_small.png'),(152,y+2))
T(c,12,148,'4 linked - 2 connected','#F0F0F0'); out.append(('gui_wireless_controller.png',c))
# Tape Library - magazine
r=random.Random(6)
def tabs(c,act):
    for i,lab in enumerate(('Magazine','Policy')):
        a=i==act; c.alpha_composite(tex('gui/sprites/rack/switch/tab_active.png' if a else 'gui/sprites/rack/switch/tab_inactive.png'),(8+i*53,17)); T(c,8+i*53+(52-tw(lab))//2,19,lab,'#F0F0F0' if a else '#9A9A9A')
c=panel('tape_library','6U Tape Library'); tabs(c,0)
fills=['slot_fill_low','slot_fill_mid','slot_fill_high','slot_fill_full']
for i in range(24):
    x=9+(i%8)*18; y=33+(i//8)*20
    if r.random()<0.85:
        c.alpha_composite(it(f'lto_tape_{r.choice((6,7,8,9,10))}'),(x,y)); c.alpha_composite(tex('gui/sprites/rack/tape/slot_fill_track.png'),(x,y+17))
        f=r.random(); c.alpha_composite(tex(f'gui/sprites/rack/tape/{fills[min(3,int(f*4))]}.png').crop((0,0,max(1,int(16*f)),1)),(x,y+17))
    else: c.alpha_composite(tex('gui/sprites/rack/tape/ghost_tape.png'),(x,y))
c.alpha_composite(tex('gui/sprites/rack/scroll_thumb.png'),(157,33))
for i,st in enumerate(('drive_read','drive_write','drive_idle',None)):
    x=9+i*40
    if st: c.alpha_composite(it('lto_tape_drive'),(x,99)); c.alpha_composite(tex(f'gui/sprites/rack/tape/{st}.png'),(x+23,104))
    else: c.alpha_composite(tex('gui/sprites/rack/tape/ghost_drive.png'),(x,99)); c.alpha_composite(tex('gui/sprites/rack/tape/drive_empty.png'),(x+23,104))
c.alpha_composite(tex('gui/sprites/common/bar_fill_mint.png').crop((0,0,64,6)),(9,125))
T(c,12,142,'Recalling: Iron Ingot x4,096','#7FB3F0'); T(c,12,152,'Archiving: 3 item types','#B4B4B4'); out.append(('gui_tape_library_magazine.png',c))
# Tape Library - policy
c=panel('tape_library_policy','6U Tape Library'); tabs(c,1)
T(c,12,36,'Archive after','#B4B4B4'); c.alpha_composite(tex('gui/sprites/inventory_tap/number_field.png'),(80,35)); T(c,83,37,'2','#F0F0F0'); button(c,120,33,40,14,'hours')
T(c,12,50,'Only above','#B4B4B4'); c.alpha_composite(tex('gui/sprites/inventory_tap/number_field.png'),(80,49)); T(c,83,51,'80%','#F0F0F0'); button(c,120,47,40,14,'On')
T(c,8,70,'Keep hot','#B4B4B4')
for i,n in enumerate(('storage_drive_8k','circuit_substrate','schematic_card')): c.alpha_composite(it(n),(9+i*18,81))
T(c,8,106,'Pinned (always hot)','#B4B4B4')
for i,n in enumerate(('processor_die','logic_die')): c.alpha_composite(it(n),(9+i*18,117))
T(c,8,140,'Hot storage 86% full','#B4B4B4'); out.append(('gui_tape_library_policy.png',c))
for n,im in out:
    bg=Image.new('RGBA',im.size,(36,37,40,255)); bg.alpha_composite(im); bg.resize((im.width*3,im.height*3),Image.NEAREST).save(P+n)
# Terminal integration mockup: a terminal row strip with items, cold badges, a running recall, and the tooltip
row=tex('gui/terminal/row.png').crop((0,0,195,18)); top=tex('gui/terminal/top.png').crop((0,0,195,19))
t=Image.new('RGBA',(195,19+18*2),(0,0,0,0)); t.alpha_composite(top,(0,0)); t.alpha_composite(row,(0,19)); t.alpha_composite(row,(0,37))
items=['iron_ingot_vanilla','silicon_wafer','ferrite','copper_foil','gallium_ingot','processor_die','memory_die','logic_die','heatsink','tantalum_capacitor','neodymium_ingot','circuit_substrate']
cold={2,5,9}; recalling=5
for i,n in enumerate(items[1:]):
    x=9+(i%9)*18; y=20+(i//9)*18
    t.alpha_composite(it(n),(x,y))
    if i in cold: t.alpha_composite(tex('gui/sprites/terminal/tape_badge.png'),(x,y+11))
    if i==recalling:
        t.alpha_composite(tex('gui/sprites/terminal/recall_track.png'),(x,y+14)); t.alpha_composite(tex('gui/sprites/terminal/recall_fill.png').crop((0,0,9,1)),(x,y+15))
        t.alpha_composite(tex('gui/sprites/terminal/recall_spinner.png').crop((0,0,8,8)),(x+8,y))
T(t,8,6,'Access Terminal','#F0F0F0')
big=Image.new('RGBA',(195+150,19+18*2+60),(36,37,40,255)); big.alpha_composite(t,(0,0))
# tooltip (vanilla-style frame)
tt=['Ferrite','On tape','Recall in ~14 s']; tw_=max(tw(s) for s in tt)+16; th=8+len(tt)*10
tip=Image.new('RGBA',(tw_,th),(16,0,16,240)); d=__import__('PIL.ImageDraw',fromlist=['ImageDraw']).Draw(tip)
d.rectangle((0,0,tw_-1,th-1),outline=(80,0,255,200)); d.rectangle((1,1,tw_-2,th-2),outline=(40,0,127,200))
T(tip,4,4,tt[0],'#FFFFFF',True); tip.alpha_composite(tex('gui/sprites/tooltip/tape.png'),(4,14)); T(tip,15,14,tt[1],'#7FB3F0',True); T(tip,4,24,tt[2],'#AAAAAA',True)
big.alpha_composite(tip,(9+2*18+10,20+36+4)); big.resize((big.width*3,big.height*3),Image.NEAREST).save(P+'terminal_cold_items.png')
# Craft plan additions
cp=tex('gui/craft_plan.png').crop((0,0,220,196)); T(cp,8,5,'Craft plan','#F0F0F0'); T(cp,212-tw('Recall +14s'),18,'Recall +14s','#7FB3F0')
for lab,x in (('Have',122),('Make',152),('Miss',182)): T(cp,x+10-tw(lab),31,lab,'#B4B4B4')
rows=[(0,'compute_server','Compute Srv','0','1','0',False),(1,'processor_die','Processor','2','0','0',False),(1,'heatsink','Heatsink','0','2','0',False),
      (2,'ferrite','Ferrite','64','0','0',True),(1,'circuit_substrate','Substrate','9','0','0',False)]
for i,(lvl,n,label,st,cr,ms,coldrow) in enumerate(rows):
    y=44+i*18; x=10+lvl*8; cp.alpha_composite(it(n),(x+8,y+1)); T(cp,x+26,y+5,label,'#F0F0F0')
    T(cp,122+10-tw(st),y+5,st,'#7FB3F0' if coldrow else '#F0F0F0'); T(cp,152+10-tw(cr),y+5,cr,'#00D992' if cr!='0' else '#7A7A7A'); T(cp,182+10-tw(ms),y+5,ms,'#7A7A7A')
    if coldrow: cp.alpha_composite(tex('gui/sprites/rack/craft_plan_recall.png'),(103,y+4))
T(cp,12,152,'Includes tape recall: +14 s','#7FB3F0')
button(cp,8,168,104,18,'Scheduler: Auto'); button(cp,118,168,46,18,'Start'); button(cp,166,168,46,18,'Cancel')
bg=Image.new('RGBA',cp.size,(36,37,40,255)); bg.alpha_composite(cp); bg.resize((cp.width*3,cp.height*3),Image.NEAREST).save(P+'craft_plan_recall.png')
# HUD popups
POP=[popup(('rack_console','Rack Console','U17'),('Online','Online'),[('Network','Online'),('Items stored','1.24M'),('Drawer','Open')]),
     popup(('wireless_controller','Wireless Controller','U13'),('Online','Online'),[('Linked terminals','4'),('Connected','2')]),
     popup(('tape_library_4u','4U Tape Library','U9-U12'),('Online','Recalling'),[('Tapes','21 / 24'),('Cold','610M / 1.2G'),('Drives busy','1 / 2'),('Activity','Recalling')],bar=(1,0.5)),
     popup(('tape_library_6u','6U Tape Library','U3-U8'),('Online','Archiving'),[('Tapes','44 / 48'),('Cold','14.8G / 22.0G'),('Drives busy','2 / 3'),('Activity','Archiving')],bar=(1,0.67))]
W=2*max(p.width for p in POP)+60; Hrow=max(p.height for p in POP)+30
sheet=Image.new('RGB',(W,2*Hrow+20),(30,31,34))
for i,p in enumerate(POP): sheet.paste(p,(20+(i%2)*(max(q.width for q in POP)+20),20+(i//2)*Hrow),p)
sheet.save(P+'hud_mockups.png'); print('ok')
