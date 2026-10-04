# Server Rack GUIs + HUD sprites (lighter grey style; same kit as the terminals/controller).
import os, json
from PIL import Image
from p1_gui import C, FILL, OUT, SLOT, panel, slot, inventory, header, corners, energy_track, scroll_track
from gui_p3 import inset, bar_track, sprite, icon
W_=C('#E6E6E6'); M_=C('#9A9A9A'); MINT=[C('#127A57'),C('#1FB582'),C('#5CF0B8')]; RED=C('#E5483C'); GRY=C('#6A6A6A'); AMB=C('#F5B23A')
ROW=9; ROWS=16                     # elevation: 9 px per U, 16 U visible (scroll for 42)
EL={'inset':(8,18,140,146),'u_col':(10,24),'slot_x':26,'slot_w':104,'scroll':(150,18,8,146)}
def top_panel(p,W=176,H=168):
    panel(p,0,0,W,H,'tlr'); header(p,W); p[0,0]=(0,0,0,0); p[W-1,0]=(0,0,0,0)
def elevation(T):
    im=Image.new('RGBA',(256,256),(0,0,0,0)); p=im.load(); top_panel(p)
    inset(p,*EL['inset'])
    for r in range(ROWS):                                         # U rows: number column + empty slot strip
        y=19+r*ROW
        for x in range(26,130): p[x,y+ROW-1]=C('#262626')
        for x in range(10,24): p[x,y+ROW-1]=C('#262626')
    for y in range(19,163): p[25,y]=C('#3A3A3A')
    scroll_track(p,150,18,146)                                    # the controller's track; thumb (controller/scroll_thumb) at x 151
    im.save(T+'gui/rack/elevation.png')
    b=Image.new('RGBA',(256,128),(0,0,0,0)); q=b.load(); panel(q,0,0,176,100,'blr'); inventory(q,7,16)
    q[0,99]=(0,0,0,0); q[175,99]=(0,0,0,0); b.save(T+'gui/rack/inventory.png')
    S=T+'gui/sprites/rack/'
    sprite(S+'slot_empty.png',104,8,lambda x,y: C('#1C1C1C') if 0<y<7 and 0<x<103 else C('#333333') if (x%8==0) else C('#262626'))
    for n,h in (('select_1u',10),('select_2u',19)):
        sprite(S+n+'.png',106,h,lambda x,y,h=h: MINT[2] if (x in (0,105) or y in (0,h-1)) else None)
    sprite(S+'drop_target_1u.png',106,10,lambda x,y: AMB if (x in (0,105) or y in (0,9)) and (x+y)%2==0 else None)
    sprite(S+'drop_target_2u.png',106,19,lambda x,y: AMB if (x in (0,105) or y in (0,18)) and (x+y)%2==0 else None)
    sprite(S+'drop_blocked.png',106,10,lambda x,y: RED if (x in (0,105) or y in (0,9)) else None)
    icon(S+'back.png',['................','.....#..........','....##..........','...##########...','....##.......#..','.....#.......#..','.............#..','.....#########..'],W_)
def firewall_panel(T):
    im=Image.new('RGBA',(256,256),(0,0,0,0)); p=im.load(); top_panel(p)
    inset(p,8,36,160,108)                                         # player list: 8 rows of 13
    for r in range(1,8):
        for x in range(10,166): p[x,36+r*13+1]=C('#232323')
    for x in range(10,166): p[x,50]=C('#3A3A3A')                 # header separator
    inset(p,8,148,110,14)                                         # add-player name field
    im.save(T+'gui/rack/firewall.png')
    S=T+'gui/sprites/rack/firewall/'
    P={'view':['........','.######.','##.##.##','#..##..#','##.##.##','.######.','........'],
       'insert':['...##...','...##...','.######.','..####..','...##...','........','.######.'],
       'extract':['.######.','........','...##...','..####..','.######.','...##...','...##...'],
       'craft':['##.##.##','##.##.##','........','##.##.##','##.##.##','........','##.##.##'],
       'build':['..####..','.##..##.','......##','.....##.','....##..','...##...','..##....']}
    for k,rows in P.items(): sprite(S+f'perm_{k}.png',8,7,lambda x,y,rows=rows: W_ if rows[y][x]=='#' else None)
    sprite(S+'toggle_on.png',9,9,lambda x,y: OUT if (x in (0,8) or y in (0,8)) else MINT[2] if (x,y) in ((2,4),(3,5),(4,6),(5,5),(6,4),(6,3),(7,2)) else MINT[0])
    sprite(S+'toggle_off.png',9,9,lambda x,y: OUT if (x in (0,8) or y in (0,8)) else C('#3A3A3A'))
    sprite(S+'toggle_inherit.png',9,9,lambda x,y: OUT if (x in (0,8) or y in (0,8)) else C('#555555') if (x+y)%2 else C('#3A3A3A'))
    sprite(S+'head_placeholder.png',8,8,lambda x,y: C('#C69C7A') if 1<y<7 else C('#5A3A22'))
def router_panel(T):
    im=Image.new('RGBA',(256,256),(0,0,0,0)); p=im.load(); top_panel(p)
    inset(p,8,30,56,84)                                          # segments
    inset(p,68,30,100,84)                                        # rules
    for r in range(1,6):
        for x in range(10,62): p[x,30+r*14]=C('#232323')
        for x in range(70,166): p[x,30+r*14]=C('#232323')
    for i in range(3): slot(p,8+i*18,128)                        # transceiver slots
    bar_track(p,68,143,100)
    im.save(T+'gui/rack/router.png')
    S=T+'gui/sprites/rack/router/'
    icon(S+'arrow.png',['................','................','................','.........#......','.........##.....','..##########....','.........##.....','.........#......'],M_)
    sprite(S+'segment_dot.png',5,5,lambda x,y: None if (x in (0,4) and y in (0,4)) else MINT[1])
def ups_panel(T):
    im=Image.new('RGBA',(256,256),(0,0,0,0)); p=im.load(); top_panel(p)
    energy_track(p,8,22)                                         # Arcforge 12x52 track, bar sprite controller/energy_bar
    inset(p,26,22,142,52)                                        # readouts
    bar_track(p,26,84,142,8)                                     # load bar
    inset(p,8,104,160,40)                                        # event log (switchovers)
    im.save(T+'gui/rack/ups.png')
# ---------------- HUD ----------------
def hud(T):
    S=T+'gui/sprites/hud/'
    PAN=C('#4B4B4B')[:3]+(236,)
    def pan(x,y,w=16,h=16):
        if x in (0,w-1) or y in (0,h-1): return (20,20,20,240)
        if x==1 or y==1: return (126,126,126,236)
        if x==w-2 or y==h-2: return (38,38,38,236)
        return PAN
    sprite(S+'panel.png',16,16,pan)                                # 9-slice: 3 px borders
    sprite(S+'divider.png',16,1,lambda x,y: C('#3A3A3A'))
    for n,c in (('dot_online',MINT[2]),('dot_offline',GRY),('dot_fault',RED)):
        sprite(S+n+'.png',5,5,lambda x,y,c=c: None if (x in (0,4) and y in (0,4)) else (c if (x,y)!=(1,1) else tuple(min(255,v+60) for v in c[:3])+(255,)))
    sprite(S+'bar_track.png',64,5,lambda x,y: C('#0E0E0E') if (x==0 or y==0) else C('#5C5C5C') if (x==63 or y==4) else C('#1F1F1F'))
    sprite(S+'bar_fill.png',62,3,lambda x,y: MINT[2] if y==0 else MINT[1])
    sprite(S+'bar_fill_warn.png',62,3,lambda x,y: C('#FFE08A') if y==0 else AMB)
    sprite(S+'bar_fill_low.png',62,3,lambda x,y: C('#FF9C90') if y==0 else RED)
def screens(A):
    S=A+'screens/rack/'; os.makedirs(S,exist_ok=True); pal={'includes':['common/palette.json']}
    json.dump({**pal,'top':{'texture':'gui/rack/elevation.png','width':176,'height':168},'bottom':{'texture':'gui/rack/inventory.png','width':176,'height':100},
      'elevation':{'inset':EL['inset'],'row_height':ROW,'rows_visible':ROWS,'u_number':{'right':23,'color':'TEXT_MUTED'},'slot':{'left':26,'width':104,'height':8,
        'empty_sprite':'rack/slot_empty','device_front':'drawn from the device texture (0,0)-(104,8n) at 1:1'},
        'select':{'1u':'rack/select_1u','2u':'rack/select_2u','offset':[-1,-1]},'drop_target':{'1u':'rack/drop_target_1u','2u':'rack/drop_target_2u','blocked':'rack/drop_blocked'},
        'scrollbar':{'track':EL['scroll'],'thumb':'controller/scroll_thumb','thumb_hover':'controller/scroll_thumb_hover','thumb_x':151},'order':'U42 at the top, U1 at the bottom (rack elevation convention); opens scrolled to the top'},
      'player_inventory':{'left':8,'top_in_bottom':17},
      'text':{'title':{'key':'block.encodedlogistics.server_rack','left':8,'top':5,'color':'TEXT'},'free':{'right':168,'top':5,'color':'TEXT_MUTED'},
              'inventory':{'key':'container.inventory','left':8,'top_in_bottom':6,'color':'TEXT_MUTED'}}},open(S+'elevation.json','w'),indent=1)
    back={'left':150,'top':2,'sprite':'rack/back','button':'terminal/button','tooltip':'gui.encodedlogistics.rack.back'}
    json.dump({**pal,'top':{'texture':'gui/rack/firewall.png','width':176,'height':168},'back':back,
      'widgets':{'default_policy':{'left':8,'top':18,'width':160,'height':14,'button':'common/button_wide','text':'gui.encodedlogistics.firewall.default'},
                 'list':{'left':10,'top':52,'rows':7,'row_height':13,'head':[2,2],'name':[12,3],
                         'perms':{'order':['view','insert','extract','craft','build'],'left':108,'spacing':11,'header_top':40,'header_icons':'rack/firewall/perm_<id>',
                                  'toggle':{'on':'rack/firewall/toggle_on','off':'rack/firewall/toggle_off','inherit':'rack/firewall/toggle_inherit'}}},
                 'add_player':{'field':[10,151,106,10],'button':{'left':122,'top':148,'width':46,'height':14,'button':'common/button_wide','text':'gui.encodedlogistics.firewall.add'}}},
      'text':{'title':{'key':'item.encodedlogistics.firewall','left':8,'top':5,'color':'TEXT'}}},open(S+'firewall.json','w'),indent=1)
    json.dump({**pal,'top':{'texture':'gui/rack/router.png','width':176,'height':168},'back':back,
      'widgets':{'segments':{'left':10,'top':32,'rows':6,'row_height':14,'dot':'rack/router/segment_dot'},
                 'rules':{'left':70,'top':32,'rows':6,'row_height':14,'columns':{'source':2,'arrow':30,'dest':46,'filter':80},'arrow':'rack/router/arrow','filter_ghost':True},
                 'add_rule':{'left':68,'top':116,'width':100,'height':12,'button':'common/button_wide','text':'gui.encodedlogistics.router.add_rule'},
                 'transceivers':{'left':[9,27,45],'top':129,'accepts':'encodedlogistics:optical_transceiver','ghost':'relay/ghost_transceiver'},
                 'throughput':{'left':69,'top':144,'width':98,'sprite':'common/bar_fill_mint'}},
      'text':{'title':{'key':'item.encodedlogistics.router','left':8,'top':5,'color':'TEXT'},'segments':{'key':'gui.encodedlogistics.router.segments','left':8,'top':20,'color':'TEXT_MUTED'},
              'rules':{'key':'gui.encodedlogistics.router.rules','left':68,'top':20,'color':'TEXT_MUTED'},'throughput':{'left':68,'top':132,'color':'TEXT_MUTED'}}},open(S+'router.json','w'),indent=1)
    json.dump({**pal,'top':{'texture':'gui/rack/ups.png','width':176,'height':168},'back':back,
      'widgets':{'energy_bar':{'left':9,'top':23,'sprite':'controller/energy_bar','fill':'bottom_up'},
                 'readouts':{'left':30,'top':25,'line_height':12,'lines':['charge','load','runtime','source']},
                 'load_bar':{'left':27,'top':85,'width':140,'height':6,'sprite':'common/bar_fill_gold'},
                 'mode':{'left':8,'top':80,'width':14,'height':14,'button':'terminal/button','icons':['rack/ups/mode_online','rack/ups/mode_standby']},
                 'log':{'left':10,'top':106,'rows':3,'row_height':12}},
      'text':{'title':{'key':'item.encodedlogistics.ups','left':8,'top':5,'color':'TEXT'},'log':{'key':'gui.encodedlogistics.ups.log','left':8,'top':95,'color':'TEXT_MUTED'}}},open(S+'ups.json','w'),indent=1)
def ups_icons(T):
    S=T+'gui/sprites/rack/ups/'
    icon(S+'mode_online.png',['................','......####......','....##....##....','...#...##...#...','...#...##...#...','...#........#...','....##....##....','......####......'],MINT[2])
    icon(S+'mode_standby.png',['................','......####......','....##....##....','...#...##...#...','...#...##...#...','...#........#...','....##....##....','......####......'],AMB)
def hud_layout(A):
    os.makedirs(A+'screens/rack',exist_ok=True)
    json.dump({'$comment':'Rack unit HUD popup. Drawn at crosshair + offset, clamped to the screen; 9-slice panel.',
      'panel':{'sprite':'hud/panel','border':3,'padding':[5,4],'offset_from_crosshair':[12,-8],'min_width':96,'max_width':180,'alpha':0.92},
      'header':{'icon':[0,0],'name':[20,0],'u':[20,9],'name_color':'TEXT','u_color':'TEXT_MUTED','height':18},
      'divider':{'sprite':'hud/divider','margin_top':2,'margin_bottom':3},
      'status':{'dot':{'online':'hud/dot_online','offline':'hud/dot_offline','fault':'hud/dot_fault'},'text':[8,0],'height':10,
                'colors':{'online':'ACCENT','offline':'TEXT_MUTED','fault':'ERROR'}},
      'line':{'height':10,'label_color':'TEXT_MUTED','value_color':'TEXT','value_align':'right','gap':8},
      'bar':{'track':'hud/bar_track','fill':'hud/bar_fill','warn':'hud/bar_fill_warn','low':'hud/bar_fill_low','height':6,'width':'line'},
      'empty':{'text':'gui.encodedlogistics.rack.hud.empty','single_line':True},
      'outline':{'color':'#5CF0B8','alpha':0.85,'line_width':2.0,'inflate_px':0.15,'note':'world-space box around the device: x 1.5..14.5, z 1.75..29.25, y = its U span'}},
      open(A+'screens/rack/hud.json','w'),indent=1)
if __name__=='__main__':
    import sys
    A=sys.argv[1]; T=A+'textures/'; os.makedirs(T+'gui/rack',exist_ok=True)
    elevation(T); firewall_panel(T); router_panel(T); ups_panel(T); ups_icons(T); hud(T); screens(A); hud_layout(A); print('gui ok')
