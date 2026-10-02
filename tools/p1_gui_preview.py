# Renders the Lithography Press and Access Terminal GUIs with real items into previews/.
# Run from the project root: python tools/p1_gui_preview.py <Minecraft's font/ascii.png>
import os, sys
from PIL import Image
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
R='src/main/resources/assets/encodedlogistics/textures/'; P='previews/'; os.makedirs(P,exist_ok=True)
it=lambda n: Image.open(R+f'item/{n}.png').convert('RGBA')
g=Image.open(R+'gui/lithography_press.png').convert('RGBA').crop((0,0,176,166))
bar=Image.open(R+'gui/sprites/controller/energy_bar.png').convert('RGBA')
g.alpha_composite(bar.crop((0,50-36,10,50)),(9,19+50-36))
g.alpha_composite(it('logic_photomask'),(80,17)); g.alpha_composite(it('silicon_wafer'),(44,36))
g.alpha_composite(Image.open(R+'gui/sprites/lithography_press/ghost_additive.png'),(80,59))
g.alpha_composite(Image.open(R+'gui/sprites/lithography_press/progress.png').convert('RGBA').crop((0,0,15,17)),(70,36))
g.alpha_composite(it('logic_die'),(113,36)); T(g,8,5,'Lithography Press','#F0F0F0'); T(g,8,72,'Inventory','#B4B4B4')
for i,n in enumerate(['silicon_wafer','ferrite','storage_photomask','silica']): g.alpha_composite(it(n),(8+18*i,142))
g.resize((176*3,166*3),Image.NEAREST).save(P+'lithography_press_gui.png')
t=Image.open(R+'gui/terminal/reference_6_rows.png').convert('RGBA')
c=Image.new('RGBA',(t.width+24,t.height),(0,0,0,0)); c.alpha_composite(t,(24,0))
names=['silicon_wafer','silica','silica_blend','ferrite','copper_foil','fiberglass','solder_paste','circuit_substrate','logic_die','storage_die_8k',
       'storage_die_32k','silicon_boule','storage_drive_8k','storage_drive_32k','logic_photomask','storage_photomask']
counts=['1.2K','64','24','312','48','16','9','24','128','12','3','40','2','1','1','1']
for i,n in enumerate(names):
    x=24+9+(i%9)*18; y=19+1+(i//9)*18; c.alpha_composite(it(n),(x,y))
c.alpha_composite(Image.open(R+'gui/sprites/terminal/slot_highlight.png'),(24+9+3*18,19+1))
c.alpha_composite(Image.open(R+'gui/sprites/terminal/search_field_focused.png'),(24+104,4)); T(c,24+108,6,'si','#F0F0F0')
T(c,24+8,6,'Access Terminal','#F0F0F0'); c.alpha_composite(Image.open(R+'gui/sprites/terminal/scroll_thumb.png'),(24+176,19+2))
for i,(b,ic) in enumerate((('button','icon_sort_count'),('button_hover','icon_dir_desc'))):
    c.alpha_composite(Image.open(R+f'gui/sprites/terminal/{b}.png'),(2,6+20*i)); c.alpha_composite(Image.open(R+f'gui/sprites/terminal/{ic}.png'),(3,7+20*i))
bt=19+6*18; T(c,24+9,bt+6,'Inventory','#B4B4B4')
for i,n in enumerate(['storage_drive_8k','silicon_wafer','logic_die']): c.alpha_composite(it(n),(24+9+18*i,bt+17+58+1))
bg=Image.new('RGBA',c.size,(36,37,40,255)); bg.alpha_composite(c); big=bg.resize((bg.width*2,bg.height*2),Image.NEAREST)
for i,s in enumerate(counts):
    x=24+9+(i%9)*18; y=19+1+(i//9)*18; T(big,2*(x+17)-tw(s)-1,2*(y+16)-9,s,'#F0F0F0',True)
big.resize((big.width*3//2,big.height*3//2),Image.NEAREST).save(P+'access_terminal_gui.png'); print('ok')
