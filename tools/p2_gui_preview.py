from PIL import Image
# Renders the Phase 2 GUIs into previews/. Run from the project root: python tools/p2_gui_preview.py <Minecraft's font/ascii.png>
import os, sys
A=Image.open(sys.argv[1]).convert('RGBA')
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
R2=R1='src/main/resources/assets/encodedlogistics/textures/'
P='previews/'; os.makedirs(P,exist_ok=True)
def it(n):
    import os
    for R in (R2,R1):
        if os.path.exists(R+f'item/{n}.png'): return Image.open(R+f'item/{n}.png').convert('RGBA')
    return Image.new('RGBA',(16,16))
def spr(p):
    import os
    for R in (R2,R1):
        if os.path.exists(R+p): return Image.open(R+p).convert('RGBA')
def ghost(img,item,x,y):
    i=it(item).copy(); a=i.split()[3].point(lambda v: v*0.55); i.putalpha(a); img.alpha_composite(i,(x,y))
out=[]
# port
g=Image.open(R2+'gui/port.png').convert('RGBA').crop((0,0,176,176))
T(g,8,5,'Ingress Port','#F0F0F0'); T(g,8,82,'Inventory','#B4B4B4')
g.alpha_composite(spr('gui/sprites/terminal/button.png'),(8,18)); g.alpha_composite(spr('gui/sprites/port/redstone_high.png'),(9,19))
for i,n in enumerate(['silicon_wafer','ferrite','copper_foil']): ghost(g,n,62+18*i,19)
g.alpha_composite(it('throughput_module'),(152,19)); g.alpha_composite(it('throughput_module'),(152,37)); g.alpha_composite(it('filter_module'),(152,55))
g.alpha_composite(spr('gui/sprites/port/ghost_module.png'),(152,73))
out.append(('port_gui.png',g))
# tap
g=Image.open(R2+'gui/inventory_tap.png').convert('RGBA').crop((0,0,176,176))
T(g,8,5,'Inventory Tap','#F0F0F0'); T(g,8,21,'Priority','#B4B4B4'); T(g,8,82,'Inventory','#B4B4B4')
g.alpha_composite(spr('gui/sprites/inventory_tap/number_field.png'),(8,31)); T(g,11,33,'10','#F0F0F0')
g.alpha_composite(spr('gui/sprites/inventory_tap/step_up.png'),(46,31)); g.alpha_composite(spr('gui/sprites/inventory_tap/step_down.png'),(46,37))
g.alpha_composite(spr('gui/sprites/terminal/button.png'),(8,56)); g.alpha_composite(spr('gui/sprites/inventory_tap/access_read_write.png'),(9,57)); T(g,30,61,'Read/write','#B4B4B4')
for i,n in enumerate(['raw_neodymium','raw_tantalum']): ghost(g,n,98+18*i,19)
out.append(('inventory_tap_gui.png',g))
# sensor
g=Image.open(R2+'gui/threshold_sensor.png').convert('RGBA').crop((0,0,176,166))
T(g,8,5,'Threshold Sensor','#F0F0F0'); T(g,26,22,'Item','#B4B4B4'); T(g,52,22,'Threshold','#B4B4B4'); T(g,8,72,'Inventory','#B4B4B4')
ghost(g,'storage_die_8k',26,33); g.alpha_composite(spr('gui/sprites/threshold_sensor/number_field_focused.png'),(52,35)); T(g,55,37,'64','#F0F0F0')
g.alpha_composite(spr('gui/sprites/terminal/button.png'),(130,31)); g.alpha_composite(spr('gui/sprites/threshold_sensor/compare_below.png'),(131,32))
T(g,52,52,'Emitting','#00D992')
out.append(('threshold_sensor_gui.png',g))
for n,im in out:
    bg=Image.new('RGBA',im.size,(36,37,40,255)); bg.alpha_composite(im); bg.resize((im.width*3,im.height*3),Image.NEAREST).save(P+n)
# fabrication terminal: top + 4 rows + crafting + bottom
top=spr('gui/terminal/top.png'); row=spr('gui/terminal/row.png'); bot=spr('gui/terminal/bottom.png'); cr=Image.open(R2+'gui/terminal/crafting.png').convert('RGBA')
rows=4; Hh=19+rows*18+76+99; t=Image.new('RGBA',(195,Hh),(0,0,0,0))
t.alpha_composite(top.crop((0,0,195,19)),(0,0))
for r in range(rows): t.alpha_composite(row.crop((0,0,195,18)),(0,19+18*r))
cy=19+rows*18; t.alpha_composite(cr.crop((0,0,195,76)),(0,cy)); t.alpha_composite(Image.open(R2+'gui/terminal/bottom_plain.png').convert('RGBA').crop((0,0,195,99)),(0,cy+76))
c=Image.new('RGBA',(195+24,Hh),(0,0,0,0)); c.alpha_composite(t,(24,0))
names=['silicon_wafer','doped_silicon','ferrite','neodymium_ingot','tantalum_ingot','copper_foil','solder_paste','circuit_substrate','logic_die','memory_die','tantalum_capacitor','storage_die_8k','storage_die_32k','storage_die_128k']
for i,n in enumerate(names): c.alpha_composite(it(n),(24+9+(i%9)*18,20+(i//9)*18))
T(c,24+8,6,'Fabrication Terminal','#F0F0F0'); c.alpha_composite(spr('gui/sprites/terminal/search_field.png'),(24+104,4)) if False else None
c.alpha_composite(spr('gui/sprites/controller/scroll_thumb.png'),(24+176,19))
grid=['circuit_substrate','memory_die','tantalum_capacitor',None,None,None,None,None,None]
for i,n in enumerate(grid):
    if n: c.alpha_composite(it(n),(24+31+(i%3)*18,cy+9+(i//3)*18))
pr=spr('gui/sprites/lithography_press/progress.png')
c.alpha_composite(it('throughput_module'),(24+127,cy+26)); c.alpha_composite(spr('gui/sprites/terminal/clear_grid.png'),(24+84,cy+8))
for i,(b,ic) in enumerate((('button','icon_sort_name'),('button','icon_dir_asc'))):
    c.alpha_composite(spr(f'gui/sprites/terminal/{b}.png'),(2,6+20*i)); c.alpha_composite(spr(f'gui/sprites/terminal/{ic}.png'),(3,7+20*i))
T(c,24+9,cy+76+6,'Inventory','#B4B4B4')
bg=Image.new('RGBA',c.size,(36,37,40,255)); bg.alpha_composite(c); bg.resize((bg.width*3,bg.height*3),Image.NEAREST).save(P+'fabrication_terminal_gui.png')
print('ok')
