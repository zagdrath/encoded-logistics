import sys
sys.path.insert(0,'/home/claude/rack/tools')
from PIL import Image, ImageDraw
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
R='/home/claude/rack/src/main/resources/assets/encodedlogistics/textures/'; P='/home/claude/rack/previews/'
REPO='/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/'
import os
def tex(p):
    for root in (R,REPO,'/home/claude/p4/src/main/resources/assets/encodedlogistics/textures/'):
        if os.path.exists(root+p): return Image.open(root+p).convert('RGBA')
    raise FileNotFoundError(p)
def device_front(n,u,state):
    im=tex(f'block/rack_device/{n}.png').crop((0,0,104,8*u))
    if state!='off': im.alpha_composite(tex(f'block/rack_device/{n}_{state}.png').crop((0,0,104,8*u)))
    return im
def button(img,x,y,w,h,label,col='#F0F0F0'):
    b=tex('gui/sprites/common/button_wide.png')
    img.alpha_composite(b.crop((0,0,w-3,18)).resize((w-3,h),Image.NEAREST),(x,y)); img.alpha_composite(b.crop((197,0,200,18)).resize((3,h),Image.NEAREST),(x+w-3,y))
    T(img,x+(w-tw(label))//2,y+(h-8)//2,label,col)
def screen(top_name):
    c=Image.new('RGBA',(176,268),(0,0,0,0)); c.alpha_composite(tex(f'gui/rack/{top_name}.png').crop((0,0,176,168)),(0,0))
    c.alpha_composite(tex('gui/rack/inventory.png').crop((0,0,176,100)),(0,168)); T(c,8,174,'Inventory','#B4B4B4'); return c
def finish(c,name,scale=3):
    bg=Image.new('RGBA',c.size,(36,37,40,255)); bg.alpha_composite(c); bg.resize((c.width*scale,c.height*scale),Image.NEAREST).save(P+name)
# --- elevation (scrolled to U16..U1)
c=screen('elevation'); T(c,8,5,'Server Rack','#F0F0F0'); T(c,168-tw('36U free'),5,'36U free','#B4B4B4')
ROWY=lambda u: 19+(16-u)*9
devices={3:('ups',2,'on'),12:('router',1,'on'),14:('firewall',1,'on')}
occupied={3,4,12,14}
for u in range(1,17):
    y=ROWY(u); s=str(u); T(c,23-tw(s),y,s,'#7A7A7A' if u not in occupied else '#B4B4B4')
    if u not in occupied: c.alpha_composite(tex('gui/sprites/rack/slot_empty.png'),(26,y))
for u,(n,size,st) in devices.items():
    y=ROWY(u+size-1); c.alpha_composite(device_front(n,size,st),(26,y))
c.alpha_composite(tex('gui/sprites/rack/select_1u.png'),(25,ROWY(14)-1))
c.alpha_composite(tex('gui/sprites/rack/drop_target_2u.png'),(25,ROWY(8)-1))
c.alpha_composite(tex('gui/sprites/rack/scroll_thumb.png'),(151,147))
items=['firewall','router','ups']
for i,n in enumerate(items): c.alpha_composite(tex(f'item/{n}.png'),(8+18*i,168+17+58+1))
finish(c,'gui_elevation.png')
# --- firewall
c=screen('firewall'); T(c,8,5,'Firewall','#F0F0F0'); c.alpha_composite(tex('gui/sprites/terminal/button.png').crop((0,0,18,14)),(150,2)) if False else None
c.alpha_composite(tex('gui/sprites/rack/back.png'),(151,2))
button(c,8,18,160,14,'Default: View only')
for i,k in enumerate(('view','insert','extract','craft','build')): c.alpha_composite(tex(f'gui/sprites/rack/firewall/perm_{k}.png'),(118+i*11,40))
T(c,12,40,'Player','#B4B4B4')
rows=[('Cody','11111'),('Alex','11100'),('Sam','1i1i0'),('Guest','10000')]
for r,(name,perm) in enumerate(rows):
    y=52+r*13; c.alpha_composite(tex('gui/sprites/rack/firewall/head_placeholder.png'),(12,y+2)); T(c,22,y+3,name,'#F0F0F0')
    for i,v in enumerate(perm):
        spr={'1':'toggle_on','0':'toggle_off','i':'toggle_inherit'}[v]; c.alpha_composite(tex(f'gui/sprites/rack/firewall/{spr}.png'),(117+i*11,y+2))
T(c,12,151,'Player name...','#7A7A7A'); button(c,122,148,46,14,'Add')
finish(c,'gui_firewall.png')
# --- router
c=screen('router'); T(c,8,5,'Router','#F0F0F0'); c.alpha_composite(tex('gui/sprites/rack/back.png'),(151,2))
T(c,8,20,'Segments','#B4B4B4'); T(c,68,20,'Routes','#B4B4B4')
for r,s in enumerate(('Core','Smelt','Farm','Vault')):
    y=32+r*14; c.alpha_composite(tex('gui/sprites/rack/router/segment_dot.png'),(12,y+4)); T(c,20,y+3,s,'#F0F0F0')
for r,(a,b2,it) in enumerate((('Farm','Core','wheat'),('Core','Smelt','raw_iron'),('Smelt','Vault','iron_ingot'))):
    y=32+r*14; T(c,72,y+3,a[:5],'#F0F0F0'); c.alpha_composite(tex('gui/sprites/rack/router/arrow.png').crop((0,0,16,9)),(100,y+2)); T(c,116,y+3,b2[:5],'#F0F0F0')
button(c,68,116,100,12,'+ Add route')
c.alpha_composite(tex('item/optical_transceiver.png'),(9,129)); c.alpha_composite(tex('item/optical_transceiver.png'),(27,129))
c.alpha_composite(tex('gui/sprites/relay/ghost_transceiver.png'),(45,129))
T(c,68,132,'64 items/s','#B4B4B4'); c.alpha_composite(tex('gui/sprites/common/bar_fill_mint.png').crop((0,0,66,6)),(69,144))
finish(c,'gui_router.png')
# --- ups
c=screen('ups'); T(c,8,5,'UPS','#F0F0F0'); c.alpha_composite(tex('gui/sprites/rack/back.png'),(151,2))
bar=tex('gui/sprites/controller/energy_bar.png'); c.alpha_composite(bar.crop((0,50-43,10,50)),(9,23+50-43))
for i,(k,v,col) in enumerate((('Charge','87%','#00D992'),('Load','1,240 FE/t','#F0F0F0'),('Runtime','6m 12s','#F0F0F0'),('Source','Mains','#B4B4B4'))):
    T(c,30,25+i*12,k,'#B4B4B4'); T(c,164-tw(v),25+i*12,v,col)
c.alpha_composite(tex('gui/sprites/terminal/button.png').resize((14,14)),(8,80)); c.alpha_composite(tex('gui/sprites/rack/ups/mode_online.png').crop((0,0,16,9)).resize((12,7)),(9,83))
c.alpha_composite(tex('gui/sprites/common/bar_fill_gold.png').crop((0,0,62,6)),(27,85))
T(c,8,95,'Events','#B4B4B4')
for r,s in enumerate(('12:04 Mains lost - on battery','12:06 Mains back - charging')): T(c,10,106+r*12,s,'#F0F0F0' if r==0 else '#B4B4B4')
finish(c,'gui_ups.png')
print('ok')
