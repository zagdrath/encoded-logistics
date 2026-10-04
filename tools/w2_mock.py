import os,sys
src=open('/home/claude/rack2/tools/gui2_preview.py').read()
head=src.split("P='/home/claude/rack2/previews/'")[0].replace("ROOTS=['/home/claude/rack2/","ROOTS=['/home/claude/wls2/src/main/resources/assets/encodedlogistics/textures/','/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/','/home/claude/rack2/")
exec(head)
psrc='def popup'+src.split('def popup')[1].split('POP=[')[0]
psrc=psrc.replace("dot={'Online':'dot_online','Offline':'dot_offline','Fault':'dot_fault'}[status[0]]; col={'Online':'#00D992','Offline':'#7A7A7A','Fault':'#FF6B6B'}[status[0]]",
                  "dot={'Online':'dot_online','Offline':'dot_offline','Fault':'dot_fault','Warn':'dot_warning'}[status[0]]; col={'Online':'#00D992','Offline':'#7A7A7A','Fault':'#FF6B6B','Warn':'#F5B23A'}[status[0]]")
exec(psrc)
from PIL import Image
P='/home/claude/wls2/previews/'
def tabs(c,names,act):
    x=8
    for i,n in enumerate(names):
        w_=tw(n)+10; spr=tex('gui/sprites/wireless/tab_active.png' if i==act else 'gui/sprites/wireless/tab_inactive.png')
        t=Image.new('RGBA',(w_,spr.height),(0,0,0,0)); t.alpha_composite(spr.crop((0,0,2,spr.height))); t.alpha_composite(spr.crop((2,0,38,spr.height)).resize((w_-4,spr.height),Image.NEAREST),(2,0)); t.alpha_composite(spr.crop((38,0,40,spr.height)),(w_-2,0))
        c.alpha_composite(t,(x,18)); T(c,x+5,20,n,'#F0F0F0' if i==act else '#9A9A9A'); x+=w_+1
for tab in (0,1):
    c=panel('wireless_controller','Wireless Controller'); tabs(c,['Access Points','Linked'],tab)
    rows=[('access_point','AP01','6 / 8','Online','#00D992'),('access_point','AP02','8 / 8','Full','#F5B23A')] if tab==0 else \
         [('wireless_bridge','WBRIDGE01','bridge'),('wireless_ingress_port','WINGRESS01','port'),('wireless_egress_port','WEGRESS01','port'),('handheld_terminal_linked','Zagdrath','terminal')]
    for r,row in enumerate(rows):
        y=34+r*14; c.alpha_composite(it(row[0]).resize((12,12)),(10,y)); T(c,25,y+3,row[1],'#F0F0F0')
        if tab==0: T(c,100,y+3,row[2],'#F0F0F0'); T(c,166-tw(row[3]),y+3,row[3],row[4])
        else: T(c,88,y+3,row[2],'#9A9A9A'); button(c,132,y,34,12,'Unlink')
    T(c,10,147,'2 APs online - 14 / 16 clients' if tab==0 else '4 linked - infinite range','#B4B4B4')
    bg=Image.new('RGBA',c.size,(36,37,40,255)); bg.alpha_composite(c); bg.resize((c.width*3,c.height*3),Image.NEAREST).save(P+f'gui_wireless_controller_{"aps" if tab==0 else "linked"}.png')
port=tex('gui/port.png'); h=tex('gui/sprites/wireless/header.png')
c=Image.new('RGBA',(176,18+port.height),(0,0,0,0)); c.alpha_composite(h,(0,0)); c.alpha_composite(port,(0,18))
c.alpha_composite(tex('gui/sprites/handheld/link_linked.png'),(5,4)); T(c,16,4,'Linked to WLC01','#F0F0F0'); c.alpha_composite(tex('gui/sprites/handheld/signal_4.png'),(156,4))
bg=Image.new('RGBA',c.size,(36,37,40,255)); bg.alpha_composite(c); bg.resize((c.width*3,c.height*3),Image.NEAREST).save(P+'gui_wireless_port_header.png')
POP=[popup(('access_point','Access Point','AP01'),('Online','Online'),[('Clients','6 / 8'),('Controller','WLC01'),('Uplink','Dense cable')],bar=(0,0.75)),
     popup(('access_point','Access Point','AP02'),('Warn','No controller'),[('Clients','0 / 8'),('Controller','none'),('Uplink','Network cable')]),
     popup(('wireless_bridge','Wireless Bridge','WBRIDGE01'),('Online','Linked - active'),[('Controller','WLC01'),('Devices','9 behind it'),('Lanes','12 / 32')],bar=(2,0.38)),
     popup(('wireless_bridge','Wireless Bridge','WBRIDGE02'),('Warn','Not linked'),[('Controller','use a Link Card'),('Devices','0')]),
     popup(('wireless_ingress_port','Wireless Ingress Port','WINGRESS01'),('Online','Online'),[('Controller','WLC01'),('Inventory','Barrel'),('Moved','128 / min')]),
     popup(('wireless_egress_port','Wireless Egress Port','WEGRESS01'),('Fault','No access points'),[('Controller','WLC01'),('Inventory','Furnace')]),
     popup(('wireless_controller','Wireless Controller','U15'),('Online','Online'),[('APs online','2'),('Clients','14 / 16'),('Linked','4')],bar=(1,0.88)),
     popup(('wireless_controller','Wireless Controller','U15'),('Fault','No access points'),[('APs online','0'),('Clients','0 / 0'),('Linked','4 (offline)')])]
cw=max(i.width for i in POP); rows=(len(POP)+2)//3
sheet=Image.new('RGB',(3*cw+80,sum(max(POP[r*3+j].height for j in range(3) if r*3+j<len(POP))+20 for r in range(rows))+20),(30,31,34)); y=20
for r in range(rows):
    hh=0
    for j in range(3):
        if r*3+j<len(POP): im=POP[r*3+j]; sheet.paste(im,(20+j*(cw+20),y),im); hh=max(hh,im.height)
    y+=hh+20
sheet.save(P+'hud_mockups.png'); print('ok')
