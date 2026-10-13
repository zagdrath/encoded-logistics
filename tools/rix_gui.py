# Router tabs, switch port panel, Exchange and Directory GUIs on the game's rack-panel textures, with a text-fit checker
# (overflow / overlap -> exit 1). Tabs: the rack switch tab sprite, widened to each label (edges kept).
import sys,os; sys.path.insert(0,'/home/claude/rack2/tools'); os.chdir('/home/claude/rix')
src=open('/home/claude/rack2/tools/gui2_preview.py').read()
exec(src.split("P='/home/claude/rack2/previews/'")[0].replace("ROOTS=['/home/claude/rack2/","ROOTS=['/home/claude/rix/src/main/resources/assets/encodedlogistics/textures/','/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/','/home/claude/rack2/"))
from PIL import Image
TEXT,MUTED,DIS,ACC,WARN,RED,BLUE='#F0F0F0','#B4B4B4','#7A7A7A','#00D992','#E8C24A','#FF6B6B','#50C2EC'
issues=[]
class C:
    def __init__(s,im,name): s.im=im; s.b=[]; s.n=name
    def t(s,x,y,txt,col,area,right=None):
        w=tw(txt); x=right-w if right is not None else x; box=(x,y,x+w,y+8)
        if box[0]<area[0] or box[2]>area[2] or box[1]<area[1] or box[3]>area[3]: issues.append(f'{s.n}: OVERFLOW {txt!r}')
        for b,o in s.b:                                                          # overlap, or closer than 3 px on the same line
            if box[0]<b[2]+3 and b[0]<box[2]+3 and box[1]<b[3] and b[1]<box[3]: issues.append(f'{s.n}: TOO CLOSE {txt!r}/{o!r}')
        s.b.append((box,txt)); T(s.im,x,y,txt,col)
def tabimg(w,active):
    sp=tex('gui/sprites/rack/switch/'+('tab_active' if active else 'tab_inactive')+'.png'); W,Hh=sp.size; out=Image.new('RGBA',(w,Hh),(0,0,0,0))
    out.alpha_composite(sp.crop((0,0,min(W-3,w-3),Hh)),(0,0))
    if w-3>W-3:
        mid=sp.crop((W//2,0,W//2+1,Hh))
        for x in range(W-3,w-3): out.alpha_composite(mid,(x,0))
    out.alpha_composite(sp.crop((W-3,0,W,Hh)),(w-3,0)); return out
def panel(title,tabs,active,name):
    im=Image.new('RGBA',(176,168),(0,0,0,0)); im.alpha_composite(tex('gui/rack/l3_switch.png').crop((0,0,176,168))); c=C(im,name)
    c.t(8,5,title,TEXT,(8,0,168,16)); x=8
    for i,l in enumerate(tabs):
        w=max(36,tw(l)+10); im.alpha_composite(tabimg(w,i==active),(x,17))
        c.t(x+w//2-tw(l)//2,20,l,TEXT if i==active else MUTED,(x+1,17,x+w-1,30)); x+=w+1
        if x>169: issues.append(f'{name}: tabs too wide')
    return im,c
AR=(12,34,153,139)   # the l3_switch panel's dark inset (measured), clear of its scrollbar
def rows(c,y0,cols,data,colr=None):
    for i,r in enumerate(data):
        for (x,al),v in zip(cols,r):
            col=(colr(i,v) if colr else TEXT)
            if al=='r': c.t(0,y0+i*11,v,col,AR,right=x)
            else: c.t(x,y0+i*11,v,col,AR)
TABS=['Routes','Mode','Table','ACLs']
def router_routing():
    im,c=panel('Router  RTR01',TABS,1,'router_routing')
    for i,(a,b) in enumerate((('Mode','Dynamic'),('Transit','Listed'),('Neighbours','3 up'),('Routes','14'),('Reconverge','5 s'),('Max hops','16'),('Throughput','4,096/s'))):
        c.t(12,36+i*13,a,MUTED,AR); c.t(0,36+i*13,b,TEXT,AR,right=152)
    return im
def router_table():
    im,c=panel('Router  RTR01',TABS,2,'router_table')
    c.t(12,36,'Network',MUTED,AR); c.t(62,36,'Next hop',MUTED,AR); c.t(0,36,'Cost',MUTED,AR,right=132); c.t(0,36,'Hop',MUTED,AR,right=152)
    rows(c,49,[(12,'l'),(62,'l'),(132,'r'),(152,'r')],[('ELNET02','direct','10','1'),('ELNET07','ELNET02','35','2'),('ELNET15','IX:MAIN','42','2'),('ELNET19','ELNET07','60','3'),('ELNET22','IX:MAIN','71','3'),('BASE','static','5','1')],
         lambda i,v: BLUE if v=='static' else TEXT)
    return im
def router_acls():
    im,c=panel('Router  RTR01',TABS,3,'router_acls')
    c.t(12,36,'ACL',MUTED,AR); c.t(0,36,'NOFUEL',TEXT,AR,right=152)
    c.t(12,49,'#',MUTED,AR); c.t(20,49,'Match',MUTED,AR); c.t(0,49,'Act',MUTED,AR,right=126); c.t(0,49,'Hits',MUTED,AR,right=152)
    rows(c,62,[(12,'l'),(20,'l'),(126,'r'),(152,'r')],[('1','Coal','deny','1.2k'),('2','#c:fuels','deny','88'),('3','*ENERGY','permit','12k'),('4','Diamond 64/m','permit','310'),('*','default','permit','4.1k')],
         lambda i,v: RED if v=='deny' else ACC if v=='permit' else TEXT)
    for i,l in enumerate(('Add','Edit','Del','Up','Dn')):
        x=12+i*31; im.alpha_composite(tex('gui/sprites/common/button_wide.png').resize((29,13)),(x,144)); c.t(x+15-tw(l)//2,147,l,TEXT,(x,144,x+29,157))
    return im
def switch_ports():
    im,c=panel('L3 Switch  SW01',['Ports','Rules','QoS'],0,'switch_ports')
    c.t(12,36,'Port',MUTED,AR); c.t(40,36,'Mode',MUTED,AR); c.t(80,36,'Allowed',MUTED,AR); c.t(0,36,'Native',MUTED,AR,right=152)
    rows(c,49,[(12,'l'),(40,'l'),(80,'l'),(152,'r')],[('1','Access','10','-'),('2','Access','20','-'),('3','Trunk','10,20,30','1'),('4','Trunk','all','1'),('5','Access','1','-')],
         lambda i,v: BLUE if v=='Trunk' else TEXT)
    return im
def exchange():
    im,c=panel('Exchange  MAIN',['Members','Peers','Setup'],0,'exchange_members')
    c.t(12,36,'Network',MUTED,AR); c.t(66,36,'Owner',MUTED,AR); c.t(0,36,'Port',MUTED,AR,right=128); c.t(0,36,'Up',MUTED,AR,right=152)
    rows(c,49,[(12,'l'),(66,'l'),(128,'r'),(152,'r')],[('ELNET01','Cody','1','yes'),('ELNET15','Alex','2','yes'),('ELNET22','Sam','5','yes'),('FARMNET','Rin','7','no'),('MINES','Jo','9','yes')],
         lambda i,v: RED if v=='no' else TEXT)
    c.t(12,110,'Request: QUARRY (Kim)',WARN,AR); c.t(12,122,'5/32 ports, RS on',MUTED,AR)
    return im
def directory():
    im,c=panel('Directory  DIR01',['Published','Remote','Lookups'],2,'directory_lookups')
    c.t(12,36,'Name',MUTED,AR); c.t(0,36,'Result',MUTED,AR,right=152)
    rows(c,49,[(12,'l'),(152,'r')],[('ELNET15/LGT01','ok 2 hops'),('ELNET22/SRN03','ok 3 hops'),('FARMNET/SPK01','no route'),('ELNET07/PLC02','denied')],
         lambda i,v: RED if v in ('no route','denied') else TEXT)
    return im
names=[('gui_router_routing',router_routing),('gui_router_table',router_table),('gui_router_acls',router_acls),('gui_switch_ports',switch_ports),('gui_exchange',exchange),('gui_directory',directory)]
o=Image.new('RGBA',(3*528+40,2*504+20),(30,31,34,255))
for i,(n,fn) in enumerate(names):
    m=fn(); m.save(f'previews/{n}.png'); bg=Image.new('RGBA',m.size,(36,37,40,255)); bg.alpha_composite(m); o.alpha_composite(bg.resize((528,504),Image.NEAREST),((i%3)*548,(i//3)*524))
o.convert('RGB').resize((o.width*38//100,o.height*38//100)).save('/home/claude/rix_gui.png')
print('ISSUES',len(issues)); print('\n'.join(issues)); sys.exit(1 if issues else 0)
