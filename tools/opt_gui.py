# Slot-view GUIs (the chassis front at 1:1 with its cards), the mux channel panel, HUD popups, WRKHW / DSPOPT screens.
# Text-fit checker: overflow / overlap / closer than 3 px -> exit 1.
import sys,os,json; sys.path.insert(0,'/home/claude/rack2/tools'); sys.path.insert(0,'/home/claude/msf/tools'); os.chdir('/home/claude/opt')
src=open('/home/claude/rack2/tools/gui2_preview.py').read()
exec(src.split("P='/home/claude/rack2/previews/'")[0].replace("ROOTS=['/home/claude/rack2/","ROOTS=['/home/claude/opt/src/main/resources/assets/encodedlogistics/textures/','/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/','/home/claude/rack2/"))
psrc='def popup'+src.split('def popup')[1].split('POP=[')[0]
psrc=psrc.replace("dot={'Online':'dot_online','Offline':'dot_offline','Fault':'dot_fault'}[status[0]]; col={'Online':'#00D992','Offline':'#7A7A7A','Fault':'#FF6B6B'}[status[0]]",
                  "dot={'Online':'dot_online','Offline':'dot_offline','Fault':'dot_fault','Warn':'dot_warning'}[status[0]]; col={'Online':'#00D992','Offline':'#7A7A7A','Fault':'#FF6B6B','Warn':'#F5B23A'}[status[0]]")
exec(psrc)
from PIL import Image
A='/home/claude/opt/src/main/resources/assets/encodedlogistics/textures/'
SP=json.load(open('/home/claude/opt/src/main/resources/assets/encodedlogistics/models/block/rack_card/slot_positions.json'))
TEXT,MUTED,ACC,WARN,RED='#F0F0F0','#B4B4B4','#00D992','#E8C24A','#FF6B6B'
issues=[]; AR=(12,34,153,139)
class C:
    def __init__(s,im,n): s.im=im; s.b=[]; s.n=n
    def t(s,x,y,txt,col,area=AR,right=None):
        w=tw(txt); x=right-w if right is not None else x; box=(x,y,x+w,y+8)
        if box[0]<area[0] or box[2]>area[2] or box[1]<area[1] or box[3]>area[3]: issues.append(f'{s.n}: OVERFLOW {txt!r}')
        for b,o in s.b:
            if box[0]<b[2]+3 and b[0]<box[2]+3 and box[1]<b[3] and b[1]<box[3]: issues.append(f'{s.n}: TOO CLOSE {txt!r}/{o!r}')
        s.b.append((box,txt)); T(s.im,x,y,txt,col)
def tabimg(w,active):
    sp=tex('gui/sprites/rack/switch/'+('tab_active' if active else 'tab_inactive')+'.png'); W,Hh=sp.size; out=Image.new('RGBA',(w,Hh),(0,0,0,0))
    out.alpha_composite(sp.crop((0,0,min(W-3,w-3),Hh)),(0,0))
    if w-3>W-3:
        mid=sp.crop((W//2,0,W//2+1,Hh))
        for x in range(W-3,w-3): out.alpha_composite(mid,(x,0))
    out.alpha_composite(sp.crop((W-3,0,W,Hh)),(w-3,0)); return out
def panel(title,tabs,active,n):
    im=Image.new('RGBA',(176,168),(0,0,0,0)); im.alpha_composite(tex('gui/rack/l3_switch.png').crop((0,0,176,168))); c=C(im,n)
    c.t(8,5,title,TEXT,(8,0,168,16)); x=8
    for i,l in enumerate(tabs):
        w=max(36,tw(l)+10); im.alpha_composite(tabimg(w,i==active),(x,17)); c.t(x+w//2-tw(l)//2,20,l,TEXT if i==active else MUTED,(x+1,17,x+w-1,30)); x+=w+1
        if x>169: issues.append(f'{n}: tabs too wide')
    return im,c
def chassis_view(im,dev,U,cards,x0,y0):
    front=Image.open(A+f'block/rack_device/{dev}.png').convert('RGBA').crop((10,0,94,8*U)); im.alpha_composite(front,(x0,y0))
    for (slot,name) in cards:
        tx,ty,w,h=slot
        for suf in ('','_on'): im.alpha_composite(Image.open(A+f'block/rack_card/{name}{suf}.png').convert('RGBA').crop((0,0,w,h)),(x0+tx-10,y0+ty))
out={}
im,c=panel('Chassis Switch  CSW01',['Slots','Lanes','Power'],0,'chassis_slots')
chassis_view(im,'chassis_switch',10,[(SP['switch']['sup'][1],'supervisor_card')]+list(zip(SP['switch']['line'],['uplink_card','access_card','access_card','routing_card','trunk_card','access_card']))+[(s,'chassis_psu') for s in SP['switch']['psu'][:3]],14,36)
for i,(a,b,col) in enumerate((('Sup A','failed',RED),('Sup B','active',ACC),('Lanes','144',TEXT),('Uplinks','4/4',TEXT),('PSU','3/4',WARN))): c.t(102,36+i*20,a,MUTED); c.t(102,45+i*20,b,col)
out['gui_chassis_switch']=im
im,c=panel('Blade Enclosure  BLD01',['Front','Rear','Totals'],0,'blade_slots')
chassis_view(im,'blade_enclosure',10,[(s,['compute_blade','compute_blade','memory_blade','fabrication_blade','storage_blade'][i%5]) for i,s in enumerate(SP['blade_front'][:13])],14,36)
for i,(a,b) in enumerate((('Threads','104'),('Buffer','160K'),('Storage','6 bays'),('Lanes','64'),('Blades','13/16'))): c.t(102,36+i*20,a,MUTED); c.t(102,45+i*20,b,TEXT)
out['gui_blade_enclosure']=im
im,c=panel('Transport Shelf  OTS01',['Slots','Channels','Spans'],0,'shelf_slots')
chassis_view(im,'optical_transport_shelf',6,list(zip(SP['shelf'],['controller_card','controller_card','transponder_card','transponder_card','mux_card','amplifier_card','protection_card','transponder_card','transponder_card','transponder_card'])),14,36)
for i,(a,b,col) in enumerate((('Path','protect',WARN),('Channels','6/40',TEXT),('Quality','92%',TEXT),('Ctrl','1 + 1',TEXT))): c.t(102,36+i*20,a,MUTED); c.t(102,45+i*20,b,col)
c.t(14,88,'Switched 14:31',WARN); c.t(14,100,'Span 2 cut',MUTED)
out['gui_transport_shelf']=im
im,c=panel('Optical Mux  MUX01',['Channels','Line'],0,'mux_channels')
c.t(12,36,'Ch',MUTED); c.t(30,36,'Network',MUTED); c.t(0,36,'Lanes',MUTED,right=128); c.t(0,36,'Up',MUTED,right=152)
for i,(n,l,u) in enumerate((('ELNET02','32','yes'),('FARMNET','18','yes'),('MINES','9','yes'),('-','-','-'),('ELNET07','32','yes'),('ELNET19','4','no'),('-','-','-'),('BACKUP','12','yes'))):
    y=49+i*11; c.t(12,y,str(i+1),TEXT); c.t(30,y,n,TEXT); c.t(0,y,l,TEXT,right=128); c.t(0,y,u,RED if u=='no' else TEXT,right=152)
out['gui_optical_mux']=im
for n,im in out.items():
    bg=Image.new('RGBA',im.size,(36,37,40,255)); bg.alpha_composite(im); bg.resize((528,504),Image.NEAREST).save(f'previews/{n}.png')
o=Image.new('RGB',(2*528+20,2*504+20),(30,31,34))
for i,n in enumerate(out): o.paste(Image.open(f'previews/{n}.png').convert('RGB'),((i%2)*548,(i//2)*524))
o.resize((o.width*40//100,o.height*40//100)).save('/home/claude/opt_gui.png')
POP=[popup(('chassis_switch','Chassis Switch','CSW01 U31'),('Warn','Degraded'),[('Supervisor','B active, A failed'),('Lanes','144'),('PSU','3 / 4')]),
     popup(('supervisor_card','Supervisor','CSW01 slot A'),('Fault','Failed'),[('Since','14:28')]),
     popup(('blade_enclosure','Blade Enclosure','BLD01 U19'),('Online','Online'),[('Blades','13 / 16'),('Threads','104')]),
     popup(('compute_blade','Compute Blade','BLD01 slot 3'),('Online','Online'),[('Threads','8')]),
     popup(('optical_transport_shelf','Transport Shelf','OTS01 U11'),('Warn','On protect'),[('Channels','6 / 40'),('Quality','92%')]),
     popup(('optical_multiplexer_rack','Optical Mux','MUX01'),('Online','Online'),[('Channels','6 / 8 up'),('Line','2.1 km')])]
cw=max(p.width for p in POP); hh=max(p.height for p in POP); sh=Image.new('RGB',(3*cw+80,2*hh+60),(30,31,34))
for i,p in enumerate(POP): sh.paste(p,(20+(i%3)*(cw+20),20+(i//3)*(hh+20)),p)
sh.save('previews/hud_popups.png')
import machine_frame as MF
from layouts import Screen, pad
L={}
s=Screen('WRKHW','Work with Hardware',prompt='Parameters or command',keys='F1=Help F3=Exit F5=Refresh F11=Cards F12=Cancel')
s.put(2,1,'Network . :  ELNET01   Chassis :  3   Cards :  31   Faults :  2')
s.put(3,1,'Type options, press Enter.'); s.put(4,3,'5=Display  8=Locate')
s.put(6,0,'Opt  Device    Slot   Part              Status      Detail','b')
for i,(d,sl,pt,st,dt) in enumerate((('CSW01','A','Supervisor','*FAILED','failover to B 14:28'),('CSW01','B','Supervisor','*ACTIVE','standby lost'),('CSW01','L1','Uplink Card','*ONLINE','4/4 cages'),
        ('CSW01','P4','Power Supply','*EMPTY','3/4 PSUs'),('BLD01','F3','Compute Blade','*ONLINE','8 threads'),('BLD01','R1','Interconnect','*ONLINE','64 lanes'),('OTS01','C1','Controller','*ACTIVE',''),
        ('OTS01','L5','Protection','*PROTECT','working path down'))):
    s.field(7+i,0,3,''); s.put(7+i,5,pad(d,10)+pad(sl,7)+pad(pt,18)+pad(st,12)+dt[:22],'b' if st in ('*FAILED','*PROTECT') else 'n')
L['wrkhw']=s
s=Screen('DSPOPT','Display Optical Status',prompt=None,keys='F1=Help F3=Exit F5=Refresh F10=Channels F12=Cancel')
s.put(2,1,'Network . :  ELNET01   Shelves :  1   Muxes :  2   Amplifiers :  3')
s.put(4,1,'Span          Path      Length   Lanes  Loss  Quality  Protection','b')
for i,(sp,pa,ln,la,lo,q,pr) in enumerate((('OTS01-OTS02','working','612','-','-','0%','*FAILED'),('OTS01-OTS02','protect','488','32','2','92%','*ACTIVE'),('MUX01-MUX02','line','301','32','0','100%','-'),('MUX02-AMP01','line','290','31','1','96%','-'))):
    s.put(5+i,1,pad(sp,14)+pad(pa,10)+ln.rjust(6)+la.rjust(8)+lo.rjust(6)+q.rjust(9)+'  '+pr,'b' if pr=='*FAILED' else 'n')
s.put(10,1,'Protection switch at 14:31:05 (working fibre cut), revertive: yes','d'); L['dspopt']=s
os.makedirs('layouts',exist_ok=True)
for k,sc in L.items(): open(f'layouts/{k}.txt','w').write(sc.text()+'\n'); MF.frame(sc,'Terminal Desk').save(f'previews/crt_{k}.png')
print('ISSUES',len(issues)); print('\n'.join(issues)); sys.exit(1 if issues else 0)
