import os,sys,random
from PIL import Image
A_=Image.open('/home/claude/ascii.png').convert('RGBA')
def glyph(ch):
    o=ord(ch); gx,gy=(o%16)*8,(o//16)*8; g=A_.crop((gx,gy,gx+8,gy+8))
    return g,(3 if ch==' ' else max([x for x in range(8) for y in range(8) if g.getpixel((x,y))[3]],default=-1)+1)
def tw(s): return sum(glyph(c)[1]+1 for c in s)-1
def T(img,x,y,s,col,shadow=False):
    r,g_,b=(int(col[i:i+2],16) for i in (1,3,5))
    for ch in s:
        g,w=glyph(ch)
        for dx,dy,cc in ([(1,1,(r//4,g_//4,b//4))] if shadow else [])+[(0,0,(r,g_,b))]:
            for yy in range(8):
                for xx in range(8):
                    if g.getpixel((xx,yy))[3] and 0<=x+xx+dx<img.width and 0<=y+yy+dy<img.height: img.putpixel((x+xx+dx,y+yy+dy),cc+(255,))
        x+=w+1
ROOTS=['/home/claude/rack2/src/main/resources/assets/encodedlogistics/textures/','/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/',
       '/home/claude/rack/src/main/resources/assets/encodedlogistics/textures/','/home/claude/p4/src/main/resources/assets/encodedlogistics/textures/',
       '/home/claude/p3/src/main/resources/assets/encodedlogistics/textures/','/home/claude/p2/src/main/resources/assets/encodedlogistics/textures/',
       '/home/claude/p1/src/main/resources/assets/encodedlogistics/textures/']
def tex(p):
    for R in ROOTS:
        if os.path.exists(R+p): return Image.open(R+p).convert('RGBA')
    raise FileNotFoundError(p)
it=lambda n: tex(f'item/{n}.png').crop((0,0,16,16))
def button(img,x,y,w,h,label,col='#F0F0F0',state=''):
    b=tex(f'gui/sprites/common/button_wide{state}.png')
    img.alpha_composite(b.crop((0,0,w-3,18)).resize((w-3,h),Image.NEAREST),(x,y)); img.alpha_composite(b.crop((197,0,200,18)).resize((3,h),Image.NEAREST),(x+w-3,y))
    T(img,x+(w-tw(label))//2,y+(h-8)//2,label,col)
def panel(name,title):
    c=Image.new('RGBA',(176,168),(0,0,0,0)); c.alpha_composite(tex(f'gui/rack/{name}.png').crop((0,0,176,168)),(0,0)); T(c,8,5,title,'#F0F0F0')
    c.alpha_composite(tex('gui/sprites/rack/back.png'),(151,2)); return c
P='/home/claude/rack2/previews/'; out=[]
# L2 switch
c=panel('switch','48-Port L2 Switch'); T(c,168-tw('9 / 32 lanes'),19,'9 / 32 lanes','#B4B4B4')
c.alpha_composite(tex('gui/sprites/common/bar_fill_mint.png').crop((0,0,30,6)),(9,21))
rows=[('compute_server','Compute Srv',2,'Core'),('memory_server','Memory Srv',1,'Core'),('nas','NAS',1,'Vault'),('san','SAN',1,'Vault'),('fabrication_server','Fab Server',2,'Core'),('monitoring_server','Monitor',1,'-'),('firewall','Firewall',1,'-')]
for r,(n,name,lanes,seg) in enumerate(rows):
    y=34+r*13; c.alpha_composite(it(n).resize((11,11)),(11,y)); T(c,24,y+2,name,'#F0F0F0')
    c.alpha_composite(tex('gui/sprites/rack/switch/lane_bar_track.png'),(86,y+5)); c.alpha_composite(tex('gui/sprites/rack/switch/lane_bar_fill.png').crop((0,0,lanes*7,2)),(87,y+6))
    button(c,122,y,44,12,seg)
c.alpha_composite(tex('gui/sprites/hud/dot_online.png'),(12,147)); T(c,20,145,'Uplink: 1 lane','#F0F0F0'); out.append(('gui_l2_switch.png',c))
# L3 switch (routes tab)
c=panel('l3_switch','L3 Switch')
for i,(lab,act) in enumerate((('Devices',0),('Routes',1),('QoS',0))):
    c.alpha_composite(tex('gui/sprites/rack/switch/tab_active.png' if act else 'gui/sprites/rack/switch/tab_inactive.png'),(8+i*53,17)); T(c,8+i*53+(52-tw(lab))//2,19,lab,'#F0F0F0' if act else '#9A9A9A')
for r,(a,b2) in enumerate((('Core','Vault'),('Farm','Core'),('Core','Smelt'))):
    y=32+r*14; T(c,12,y+3,a,'#F0F0F0'); c.alpha_composite(tex('gui/sprites/rack/router/arrow.png').crop((0,0,16,9)),(52,y+2)); T(c,72,y+3,b2,'#F0F0F0')
button(c,8,146,160,14,'+ Add route'); out.append(('gui_l3_switch.png',c))
# compute
c=panel('server','Compute Server')
for i,(k,v) in enumerate((('Threads provided','4'),('Threads in use','3'),('Rack scheduler','Active'))): T(c,12,26+i*12,k,'#B4B4B4'); T(c,164-tw(v),26+i*12,v,'#00D992' if i==2 else '#F0F0F0')
c.alpha_composite(tex('gui/sprites/common/bar_fill_gold.png').crop((0,0,118,6)),(9,101)); T(c,12,122,'2M Drive x4 (2 threads)','#F0F0F0'); T(c,12,134,'Processor x16 (1 thread)','#B4B4B4')
out.append(('gui_compute_server.png',c))
# fabrication
c=panel('fabrication_server','Fabrication Server')
for i in range(5): c.alpha_composite(it('encoded_schematic_crafting'),(31+(i%3)*18,23+(i//3)*18))
c.alpha_composite(tex('gui/sprites/lithography_press/progress.png').crop((0,0,14,17)),(92,40)); c.alpha_composite(it('throughput_module'),(141,33))
c.alpha_composite(tex('gui/sprites/port/ghost_module.png'),(141,51)); T(c,12,88,'Job: Heatsink x8','#F0F0F0'); T(c,12,100,'Progress 3 / 8','#B4B4B4')
out.append(('gui_fabrication_server.png',c))
# monitoring
c=panel('monitoring_server','Monitoring Server')
for i,s in enumerate(('items','energy','lanes','jobs')):
    c.alpha_composite(tex('gui/sprites/terminal/button'+('_hover' if s=='items' else '')+'.png'),(8+i*20,18)); c.alpha_composite(tex(f'gui/sprites/rack/monitor/stat_{s}.png'),(9+i*20,19))
for i,l in enumerate(('1m','10m','1h','1d')): button(c,92+i*19,21,18,12,l,'#F0F0F0' if l=='1h' else '#B4B4B4')
dot=tex('gui/sprites/rack/monitor/line_items.png'); r=random.Random(2); v=60
pts=[]
for x in range(10,166,2): v=max(10,min(95,v+r.randint(-8,8))); pts.append((x,146-v))
for (x0,y0),(x1,y1) in zip(pts,pts[1:]):
    for k in range(5):
        t=k/4; c.alpha_composite(dot,(int(x0+(x1-x0)*t),int(y0+(y1-y0)*t)))
T(c,12,48,'1.2K/min','#7A7A7A'); T(c,8,152,'Now: 846 items/min','#F0F0F0'); out.append(('gui_monitoring_server.png',c))
# nas
c=panel('nas','NAS')
for i,n in enumerate(['storage_drive_8k','storage_drive_32k','storage_drive_128k','storage_drive_512k',None,'storage_drive_2m']):
    if n: c.alpha_composite(it(n),(35+i*18,23))
    else: c.alpha_composite(tex('gui/sprites/rack/storage/ghost_drive.png'),(35+i*18,23))
T(c,12,55,'Priority','#B4B4B4'); c.alpha_composite(tex('gui/sprites/inventory_tap/number_field.png'),(12,64)); T(c,15,66,'0','#F0F0F0')
c.alpha_composite(tex('gui/sprites/terminal/button.png'),(60,58)); c.alpha_composite(tex('gui/sprites/inventory_tap/access_read_write.png'),(61,59)); T(c,82,63,'Read/write','#B4B4B4')
c.alpha_composite(tex('gui/sprites/common/bar_fill_mint.png').crop((0,0,90,6)),(12,80)); out.append(('gui_nas.png',c))
# san
c=panel('san','SAN'); rr=random.Random(4)
for i in range(24):
    x=9+(i%6)*18; y=23+(i//6)*18
    if rr.random()<0.75: c.alpha_composite(it(['storage_drive_8k','storage_drive_32k','storage_drive_128k','storage_drive_512k','storage_drive_2m'][rr.randrange(5)]),(x,y))
    else: c.alpha_composite(tex('gui/sprites/rack/storage/ghost_drive.png'),(x,y))
for i in range(4):
    x=125+(i%2)*18; y=23+(i//2)*18
    c.alpha_composite(it('optical_transceiver') if i<2 else tex('gui/sprites/relay/ghost_transceiver.png'),(x,y))
T(c,12,104,'Priority','#B4B4B4'); c.alpha_composite(tex('gui/sprites/inventory_tap/number_field.png'),(12,113)); T(c,15,115,'5','#F0F0F0')
c.alpha_composite(tex('gui/sprites/terminal/button.png'),(60,108)); c.alpha_composite(tex('gui/sprites/inventory_tap/access_read_write.png'),(61,109))
c.alpha_composite(tex('gui/sprites/common/bar_fill_mint.png').crop((0,0,120,6)),(12,132)); T(c,12,144,'Uplink: 2 transceivers','#F0F0F0'); out.append(('gui_san.png',c))
for n,im in out:
    bg=Image.new('RGBA',im.size,(36,37,40,255)); bg.alpha_composite(im); bg.resize((im.width*3,im.height*3),Image.NEAREST).save(P+n)
# HUD popup mockups (panels only) - same 9-slice construction as the rack popup
def popup(header,status,lines,bar=None,badge=False,S=3):
    pad=5; w=max(118,max([tw(header[1])+20,tw(header[2])+20+(tw('Scheduler')+18 if badge else 0),tw(status[1])+8]+[tw(a)+8+tw(b) for a,b in lines])+2*pad)
    h=4+18+6+10+len(lines)*10+(8 if bar else 0)+4; im=Image.new('RGBA',(w,h),(0,0,0,0)); pan=tex('gui/sprites/hud/panel.png')
    for (sx,sy,dx,dy) in ((0,0,0,0),(13,0,w-3,0),(0,13,0,h-3),(13,13,w-3,h-3)): im.alpha_composite(pan.crop((sx,sy,sx+3,sy+3)),(dx,dy))
    im.alpha_composite(pan.crop((3,0,13,3)).resize((w-6,3),Image.NEAREST),(3,0)); im.alpha_composite(pan.crop((3,13,13,16)).resize((w-6,3),Image.NEAREST),(3,h-3))
    im.alpha_composite(pan.crop((0,3,3,13)).resize((3,h-6),Image.NEAREST),(0,3)); im.alpha_composite(pan.crop((13,3,16,13)).resize((3,h-6),Image.NEAREST),(w-3,3))
    im.alpha_composite(pan.crop((3,3,13,13)).resize((w-6,h-6),Image.NEAREST),(3,3))
    y=4; im.alpha_composite(it(header[0]),(pad,y)); T(im,pad+20,y,header[1],'#F0F0F0',True); T(im,pad+20,y+9,header[2],'#B4B4B4')
    if badge:
        bx=w-pad-tw('Scheduler')-11; im.alpha_composite(tex('gui/sprites/hud/badge_scheduler.png'),(bx,y+9)); T(im,bx+11,y+9,'Scheduler','#E8C24A',True)
    y+=19; im.alpha_composite(tex('gui/sprites/hud/divider.png').resize((w-2*pad,1)),(pad,y)); y+=4
    dot={'Online':'dot_online','Offline':'dot_offline','Fault':'dot_fault'}[status[0]]; col={'Online':'#00D992','Offline':'#7A7A7A','Fault':'#FF6B6B'}[status[0]]
    im.alpha_composite(tex(f'gui/sprites/hud/{dot}.png'),(pad,y+1)); T(im,pad+8,y,status[1],col,True); y+=11
    for i,(a,b2) in enumerate(lines):
        T(im,pad,y,a,'#9A9A9A'); T(im,w-pad-tw(b2),y,b2,'#F0F0F0',True); y+=10
        if bar and i==bar[0]:
            im.alpha_composite(tex('gui/sprites/hud/bar_track.png').resize((w-2*pad,5)),(pad,y)); im.alpha_composite(tex('gui/sprites/hud/bar_fill.png').resize((max(1,int((w-2*pad-2)*bar[1])),3)),(pad+1,y+1)); y+=8
    return im.resize((w*S,h*S),Image.NEAREST)
POP=[popup(('l2_switch_24','24-Port L2 Switch','U17'),('Online','Online'),[('Lanes','11 / 16'),('Devices','6'),('Uplink','Linked (1 lane)')]),
     popup(('l2_switch_48','48-Port L2 Switch','U16'),('Online','Online'),[('Lanes','9 / 32'),('Devices','7'),('Uplink','Linked (1 lane)')]),
     popup(('l3_switch','L3 Switch','U15'),('Online','Online'),[('Lanes','14 / 32'),('Active routes','3'),('QoS rules','2'),('Uplink','Linked (1 lane)')]),
     popup(('compute_server','Compute Server','U9-U10'),('Online','Online'),[('Threads provided','4'),('Threads in use','3')],badge=True),
     popup(('memory_server','Memory Server','U11'),('Online','Online'),[('Buffer','64K'),('In use','18K')],badge=True),
     popup(('fabrication_server','Fabrication Server','U12-U13'),('Online','Online'),[('Schematics','5'),('Job','Heatsink x8'),('Progress','3 / 8')]),
     popup(('monitoring_server','Monitoring Server','U14'),('Online','Online'),[('Items','846/min'),('Energy','2,410 FE/t'),('Lanes','23 used'),('Active jobs','2')]),
     popup(('nas','NAS','U7-U8'),('Online','Online'),[('Capacity','1.1M / 2.7M'),('Drives','5 / 6')],bar=(0,0.41)),
     popup(('san','SAN','U3-U6'),('Online','Online'),[('Capacity','18.4M / 31.0M'),('Drives','18 / 24'),('Uplink','2 transceivers')],bar=(0,0.59)),
     popup(('san','SAN','U3-U6'),('Fault','Offline - no uplink'),[('Capacity','0 / 31.0M'),('Drives','18 / 24'),('Uplink','No transceiver')],bar=(0,0.0))]
W=3*max(p.width for p in POP)+80; Hrow=max(p.height for p in POP)+30
sheet=Image.new('RGB',(W,4*Hrow+20),(30,31,34))
for i,p in enumerate(POP): sheet.paste(p,(20+(i%3)*(max(q.width for q in POP)+20),20+(i//3)*Hrow),p)
sheet.save(P+'hud_mockups.png'); POP[3].save(P+'hud_header_scheduler.png'); print('ok')
