# GUI mockups drawn ONLY from the game's own textures and sprites, at the positions the game's screens use.
import os,sys
src=open('/home/claude/rack2/tools/gui2_preview.py').read()
head=src.split("P='/home/claude/rack2/previews/'")[0].replace("ROOTS=['/home/claude/rack2/","ROOTS=['/home/claude/wls2/src/main/resources/assets/encodedlogistics/textures/','/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/','/home/claude/rack2/")
exec(head)
from PIL import Image
P='/home/claude/wls2/previews/'
TEXT,MUTED,DIS='#F0F0F0','#B4B4B4','#7A7A7A'
def port(kind,linked=True):
    """PortScreen layout: title (8,5); redstone button (8,18); options column x30 from y18; module slots; filter grid.
       Wireless: the link status on the title band's right: bridge status light, controller name, signal bars."""
    c=Image.new('RGBA',(176,176),(0,0,0,0)); c.alpha_composite(tex('gui/port.png').crop((0,0,176,176)))
    T(c,8,5,f'Wireless {kind} Port',TEXT)
    c.alpha_composite(tex('gui/sprites/terminal/button.png').resize((18,18)),(8,18)); c.alpha_composite(tex('gui/sprites/port/redstone_ignore.png'),(9,19))
    # link status in the free column under the redstone button (x 8-26, y 40+): status light + signal bars;
    # tooltip "Linked to WLC01 - signal 4/4" / "Not linked - use a Link Card from a Wireless Controller"
    light=tex('gui/sprites/bridge/status_linked.png' if linked else 'gui/sprites/bridge/status_unlinked.png')
    c.alpha_composite(light,(9,42))
    c.alpha_composite(tex('gui/sprites/handheld/signal_4.png' if linked else 'gui/sprites/handheld/signal_0.png'),(17,41))
    return c
def controller(tab):
    """L3SwitchPanel layout: tabs (8,17) step 53 width 52 (rack/switch tabs), list x10 y31 rows 14 (8 rows), summary (12,148);
       rows as WirelessControllerPanel draws them: dot, name, where (right-aligned at 130), unlink (common/cancel_small)."""
    c=Image.new('RGBA',(176,168),(0,0,0,0)); c.alpha_composite(tex('gui/rack/wireless_controller.png').crop((0,0,176,168)))
    T(c,8,5,'Wireless Controller',TEXT); c.alpha_composite(tex('gui/sprites/rack/back.png'),(160,3))
    for i,n in enumerate(('APs','Linked')):
        a=i==tab; spr=tex('gui/sprites/rack/switch/tab_active.png' if a else 'gui/sprites/rack/switch/tab_inactive.png')
        c.alpha_composite(spr,(8+i*53,17)); T(c,8+i*53+(52-tw(n))//2,19 if a else 20,n,TEXT if a else MUTED)
    rows=[('AP01','6 / 8 clients',True),('AP02','8 / 8 - full',True),('AP03','no uplink',False)] if tab==0 else \
         [('WBRIDGE01','bridge',True),('WINGRESS01','ingress',True),('WEGRESS01','egress',False),('Zagdrath','handheld',True)]
    for i,(n,where,on) in enumerate(rows):
        y=31+i*14; c.alpha_composite(tex('gui/sprites/hud/dot_online.png' if on else 'gui/sprites/hud/dot_offline.png'),(12,y+4))
        T(c,21,y+3,n,TEXT if on else DIS); T(c,130-tw(where),y+3,where,MUTED)
        if tab==1: c.alpha_composite(tex('gui/sprites/common/cancel_small.png'),(152,y+2))
    T(c,12,148,'3 APs, 2 online - 14 / 16 clients' if tab==0 else '4 linked, 3 connected',TEXT)
    return c
for n,im in (('gui_wireless_ingress_port',port('Ingress')),('gui_wireless_egress_port_unlinked',port('Egress',False)),('gui_wireless_controller_aps',controller(0)),('gui_wireless_controller_linked',controller(1))):
    bg=Image.new('RGBA',im.size,(36,37,40,255)); bg.alpha_composite(im)
    if n.startswith('gui_wireless_ingress'):                                   # show the tooltip as it appears on hover
        msg='Linked to WLC01 - signal 4/4'; tt=Image.new('RGBA',(tw(msg)+8,14),(16,0,16,240)); T(tt,4,3,msg,TEXT); bg2=Image.new('RGBA',(bg.width+80,bg.height),(36,37,40,255)); bg2.alpha_composite(bg); bg2.alpha_composite(tt,(22,54)); bg=bg2
    bg.resize((bg.width*3,bg.height*3),Image.NEAREST).save(P+n+'.png')
ims=[Image.open(P+n+'.png') for n in ('gui_wireless_ingress_port','gui_wireless_egress_port_unlinked','gui_wireless_controller_aps','gui_wireless_controller_linked')]
W=sum(i.width+20 for i in ims); H=max(i.height for i in ims); s=Image.new('RGB',(W,H),(30,31,34)); x=0
for i in ims: s.paste(i,(x,0)); x+=i.width+20
s.resize((W*3//10,H*3//10)).save('/home/claude/w2_guis.png'); print('ok')
