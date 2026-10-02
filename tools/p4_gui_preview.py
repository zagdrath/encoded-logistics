import os,sys
sys.path.insert(0,'/home/claude/p3/tools')
exec(open('/home/claude/p3/tools/p3_gui_preview.py').read().split('# --- Schematic Encoder')[0].replace("ROOTS=[","ROOTS=['/home/claude/p4/src/main/resources/assets/encodedlogistics/textures/',"))
P='/home/claude/p4/previews/'
_fin=finish
def finish(im,name,scale=3):
    bg=Image.new('RGBA',im.size,(36,37,40,255)); bg.alpha_composite(im); bg.resize((im.width*scale,im.height*scale),Image.NEAREST).save(P+name)
# handheld: terminal (5 rows) + side panel
top=tex('gui/terminal/top.png').crop((0,0,195,19)); row=tex('gui/terminal/row.png').crop((0,0,195,18)); bot=tex('gui/terminal/bottom.png').crop((0,0,195,99))
rows=5; H_=19+18*rows+99; c=Image.new('RGBA',(24+195+26,H_),(0,0,0,0)); c.alpha_composite(top,(24,0))
for r in range(rows): c.alpha_composite(row,(24,19+18*r))
c.alpha_composite(bot,(24,19+18*rows))
sp=tex('gui/terminal/handheld_panel.png').crop((0,0,26,96)); c.alpha_composite(sp,(24+195-2,4))
bar=tex('gui/sprites/controller/energy_bar.png'); c.alpha_composite(bar.crop((0,50-30,10,50)),(24+193+8,4+9+50-30))
c.alpha_composite(tex('gui/sprites/handheld/link_linked.png'),(24+193+10,4+70)); c.alpha_composite(tex('gui/sprites/handheld/signal_3.png'),(24+193+7,4+78))
for i,n in enumerate(['optical_transceiver','link_card','gallium_ingot','processor_die','silicon_wafer','heatsink','memory_die','schematic_card']): c.alpha_composite(it(n),(24+9+(i%9)*18,20+(i//9)*18))
T(c,32,6,'Handheld Terminal','#F0F0F0'); c.alpha_composite(tex('gui/sprites/controller/scroll_thumb.png'),(24+176,19)); T(c,33,19+18*rows+6,'Inventory','#B4B4B4')
for i,(b,ic) in enumerate((('button','icon_sort_name'),('button','icon_dir_asc'))):
    c.alpha_composite(tex(f'gui/sprites/terminal/{b}.png'),(2,6+20*i)); c.alpha_composite(tex(f'gui/sprites/terminal/{ic}.png'),(3,7+20*i))
finish(c,'handheld_terminal_gui.png',3)
# relay
g=tex('gui/relay_antenna.png').crop((0,0,176,176)); T(g,8,5,'Relay Antenna','#F0F0F0'); T(g,11,21,'Range: 96 blocks','#00D992'); T(g,8,35,'Linked terminals','#B4B4B4')
for i in range(2): g.alpha_composite(it('optical_transceiver'),(107+17*i,17))
for i in (2,3): g.alpha_composite(tex('gui/sprites/relay/ghost_transceiver.png'),(107+17*i,17))
for r,s in enumerate(('Cody - 12 m','Alex - 54 m')): T(g,12,46+r*11+2,s,'#F0F0F0')
T(g,8,82,'Inventory','#B4B4B4'); finish(g,'relay_antenna_gui.png')
# bridge
g=tex('gui/network_bridge.png').crop((0,0,176,96)); T(g,8,5,'Network Bridge','#F0F0F0'); g.alpha_composite(tex('gui/sprites/bridge/status_linked.png'),(12,23))
T(g,22,22,'Linked','#F0F0F0'); T(g,12,34,'Partner: 1204, 64, -388','#B4B4B4'); T(g,12,44,'minecraft:overworld','#7A7A7A')
on=tex('gui/sprites/bridge/lane_on.png')
for i in range(19): g.alpha_composite(on.resize((2,8)),(9+i*5,63))
T(g,8,76,'Lanes 19 / 32','#B4B4B4'); finish(g,'network_bridge_gui.png')
# p2p
g=tex('gui/point_to_point_link.png').crop((0,0,176,96)); T(g,8,5,'Point-to-Point Link','#F0F0F0')
for i,k in enumerate(('items','energy','redstone','lanes')):
    g.alpha_composite(tex('gui/sprites/terminal/'+('button_hover' if k=='energy' else 'button')+'.png'),(8+20*i,20)); g.alpha_composite(tex(f'gui/sprites/p2p/type_{k}.png'),(9+20*i,21))
g.alpha_composite(tex('gui/sprites/terminal/button.png'),(150,20)); g.alpha_composite(tex('gui/sprites/p2p/dir_out.png'),(151,21))
T(g,12,50,'Linked - Energy output','#F0F0F0'); T(g,12,63,'Partner: 18, 70, -42','#B4B4B4'); finish(g,'point_to_point_link_gui.png')
# deployer
g=tex('gui/deployer_plane.png').crop((0,0,176,166)); T(g,8,5,'Deployer Plane','#F0F0F0'); T(g,8,72,'Inventory','#B4B4B4')
g.alpha_composite(tex('gui/sprites/terminal/button.png'),(8,18)); g.alpha_composite(tex('gui/sprites/deployer/mode_place.png'),(9,19))
for i,n in enumerate(['ferrite','silica']): ghost(g,n,62+18*i,18)
finish(g,'deployer_plane_gui.png')
print('ok')
