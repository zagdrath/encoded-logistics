# Phase 4 GUIs - same kit as earlier screens.
import os, json, sys
from PIL import Image
from p1_gui import C, FILL, OUT, SLOT, panel, slot, inventory, header, corners, TW, energy_track
from gui_p3 import new, inset, bar_track, sprite, icon
from p1_gui import sep, ghost
W_=C('#E6E6E6'); CY=[C('#1E6E86'),C('#3FB4D8'),C('#9CE6F6')]; MINT=[C('#127A57'),C('#1FB582'),C('#5CF0B8')]
AMB=C('#F5B23A'); RED=C('#E5483C'); GRY=C('#5A5A5A')
TYPE_COL={'items':C('#4A8FE0'),'energy':C('#F0C030'),'redstone':C('#E5483C'),'lanes':C('#00D992')}
def handheld(T):
    """Side panel attached to the terminal's right edge (26 x 96): Arcforge energy track, link light, signal bars."""
    im=Image.new('RGBA',(64,128),(0,0,0,0)); p=im.load(); panel(p,0,0,26,96,'tbr'); p[25,0]=p[25,95]=(0,0,0,0)
    energy_track(p,7,8)
    for y in range(66,90):
        for x in range(5,21): p[x,y]=SLOT[0] if (x==5 or y==66) else SLOT[3] if (x==20 or y==89) else C('#1C1C1C')
    os.makedirs(T+'gui/terminal',exist_ok=True); im.save(T+'gui/terminal/handheld_panel.png')
    S=T+'gui/sprites/handheld/'
    for name,col in (('link_linked',MINT[2]),('link_out_of_range',AMB),('link_unlinked',GRY)):
        sprite(S+name+'.png',6,6,lambda x,y,c=col: None if (x in (0,5) and y in (0,5)) else c)
    for n in range(5):                                        # signal bars 0..4
        sprite(S+f'signal_{n}.png',12,10,lambda x,y,n=n: (MINT[2] if (x//3)<n else C('#3A3A3A')) if (x%3<2 and y>=8-2*(x//3)) else None)
RELAY={'range':(8,17,84,18),'slots_x':[96,114,132,150],'slots_y':17,'list':(8,46,160,34),'row':10,'inv':(7,93)}
def relay_gui(T):
    """Range readout and the four transceiver slots on one row inside the frame, then the linked-terminal list."""
    im,p=new(176,176)
    inset(p,*RELAY['range'])
    for x in RELAY['slots_x']: slot(p,x,RELAY['slots_y'])
    lx,ly,lw,lh=RELAY['list']; inset(p,lx,ly,lw,lh)
    for r in (1,2): sep(p,lx+2,lx+lw-2,ly+2+r*RELAY['row']-1)
    inventory(p,*RELAY['inv']); im.save(T+'gui/relay_antenna.png')
    ghost(T+'gui/sprites/relay/ghost_transceiver.png','transceiver')
def bridge_gui(T):
    im,p=new(176,96)
    inset(p,8,18,160,38)                                       # status, partner, dimension lines
    for i in range(32):                                        # lane usage: 32 segments
        x0=8+i*5
        for y in range(62,72):
            for x in range(x0,x0+4): p[x,y]=C('#0E0E0E') if (y==62 or x==x0) else C('#5C5C5C') if (y==71 or x==x0+3) else C('#1F1F1F')
    im.save(T+'gui/network_bridge.png')
    sprite(T+'gui/sprites/bridge/lane_on.png',2,8,lambda x,y: MINT[2] if y==0 else MINT[1])
    for n,col in (('status_linked',MINT[2]),('status_unlinked',AMB),('status_offline',RED)):
        sprite(T+f'gui/sprites/bridge/{n}.png',6,6,lambda x,y,c=col: None if (x in (0,5) and y in (0,5)) else c)
def p2p_gui(T):
    im,p=new(176,96)
    inset(p,8,46,160,32)
    im.save(T+'gui/point_to_point_link.png')
    S=T+'gui/sprites/p2p/'
    rows={'items':['................','..##########....','..#........#....','..#.######.#....','..#........#....','..##########....'],
          'energy':['................','.......####.....','......####......','.....#######....','........###.....','.......###......','......##........','.....#..........'],
          'redstone':['................','.......##.......','......####......','.......##.......','.......##.......','.......##.......','.......##.......','.....######.....'],
          'lanes':['................','..#.#.#.#.#.....','..#.#.#.#.#.....','..#.#.#.#.#.....','..#.#.#.#.#.....','..##########....']}
    for k,r in rows.items(): icon(S+f'type_{k}.png',r,TYPE_COL[k])
    icon(S+'dir_in.png',['................','.......##.......','.......##.......','.....######.....','......####......','.......##.......','................','....########....'],W_)
    icon(S+'dir_out.png',['................','....########....','................','.......##.......','......####......','.....######.....','.......##.......','.......##.......'],W_)
def collector_gui(T):
    """The Collector Plane: its module slot where the Deployer has its mode button, the same 3x3 filter."""
    im,p=new(176,166)
    slot(p,7,17)
    for r in range(3):
        for c in range(3): slot(p,61+c*18,17+r*18)
    inventory(p,7,83); im.save(T+'gui/collector_plane.png')
def deployer_gui(T):
    im,p=new(176,166)
    for r in range(3):
        for c in range(3): slot(p,61+c*18,17+r*18)
    inventory(p,7,83); im.save(T+'gui/deployer_plane.png')
    S=T+'gui/sprites/deployer/'
    icon(S+'mode_place.png',['................','....########....','....#......#....','....#......#....','....#......#....','....########....','.......##.......','.......##.......','.....######.....'],W_)
    icon(S+'mode_drop.png',['................','.......##.......','.......##.......','.....######.....','......####......','.......##.......','................','.....#....#.....','...#....#....#..'],W_)
def screens(A):
    S=A+'screens/'; os.makedirs(S,exist_ok=True); pal={'includes':['common/palette.json']}
    btn={'button':'terminal/button','button_hover':'terminal/button_hover'}
    json.dump({'includes':['terminal/base_terminal.json'],'text':{'title':{'key':'gui.encodedlogistics.handheld_terminal'}},
      'side_panel':{'texture':'gui/terminal/handheld_panel.png','width':26,'height':96,'attach':'right','top':4,
        'energy_bar':{'left':8,'top':9,'sprite':'controller/energy_bar','fill':'bottom_up','tooltip':'tooltip.encodedlogistics.handheld.energy'},
        'link_light':{'left':10,'top':70,'sprites':{'linked':'handheld/link_linked','out_of_range':'handheld/link_out_of_range','unlinked':'handheld/link_unlinked'}},
        'signal':{'left':7,'top':78,'sprites':['handheld/signal_0','handheld/signal_1','handheld/signal_2','handheld/signal_3','handheld/signal_4']}},
      'offline_overlay':{'when':['unlinked','out_of_range'],'text':'gui.encodedlogistics.handheld.out_of_range'}},open(S+'handheld_terminal.json','w',newline='\n'),indent=1)
    json.dump({**pal,'background':{'texture':'gui/relay_antenna.png','width':176,'height':176},
      'slots':{'transceivers':{'left':[97,115,133,151],'top':18,'accepts':'encodedlogistics:optical_transceiver','ghost':'relay/ghost_transceiver'}},
      'text':{'title':{'key':'block.encodedlogistics.relay_antenna','left':8,'top':5,'color':'TEXT'},'range':{'key':'gui.encodedlogistics.relay.range','left':13,'top':22,'color':'ACCENT'},
              'linked':{'key':'gui.encodedlogistics.relay.linked','left':8,'top':37,'color':'TEXT_MUTED'},'inventory':{'key':'container.inventory','left':8,'top':83,'color':'TEXT_MUTED'}},
      'lists':{'linked':{'left':12,'top':48,'rows':3,'row_height':10,'entry':'gui.encodedlogistics.relay.entry','scroll':True}},
      'player_inventory':{'left':8,'top':94}},open(S+'relay_antenna.json','w',newline='\n'),indent=1)
    json.dump({**pal,'background':{'texture':'gui/network_bridge.png','width':176,'height':96},
      'widgets':{'status_light':{'left':12,'top':23,'sprites':{'linked':'bridge/status_linked','unlinked':'bridge/status_unlinked','offline':'bridge/status_offline'}},
                 'lanes':{'left':9,'top':63,'segments':32,'pitch':5,'sprite':'bridge/lane_on'}},
      'text':{'title':{'key':'block.encodedlogistics.network_bridge','left':8,'top':5,'color':'TEXT'},'status':{'left':22,'top':22,'color':'TEXT'},
              'partner':{'key':'gui.encodedlogistics.bridge.partner','left':12,'top':34,'color':'TEXT_MUTED','note':'x, y, z on this line'},
              'dimension':{'left':12,'top':44,'color':'TEXT_MUTED'},
              'lanes':{'key':'gui.encodedlogistics.bridge.lanes','left':8,'top':76,'color':'TEXT_MUTED'}}},open(S+'network_bridge.json','w',newline='\n'),indent=1)
    json.dump({**pal,'background':{'texture':'gui/point_to_point_link.png','width':176,'height':96},
      'widgets':{'type':{'left':8,'top':20,'spacing':20,**btn,'options':['items','energy','redstone','lanes'],'icons':['p2p/type_items','p2p/type_energy','p2p/type_redstone','p2p/type_lanes'],
                         'note':'locked once paired; re-pick after unpairing'},
                 'direction':{'left':150,'top':20,**btn,'icons':['p2p/dir_in','p2p/dir_out']}},
      'text':{'title':{'key':'item.encodedlogistics.point_to_point_link','left':8,'top':5,'color':'TEXT'},'status':{'left':12,'top':50,'color':'TEXT'},
              'partner':{'key':'gui.encodedlogistics.p2p.partner','left':12,'top':63,'color':'TEXT_MUTED'}}},open(S+'point_to_point_link.json','w',newline='\n'),indent=1)
    json.dump({**pal,'background':{'texture':'gui/deployer_plane.png','width':176,'height':166},
      'slots':{'filter':{'left':62,'top':18,'columns':3,'rows':3,'ghost_items':True}},
      'widgets':{'mode':{'left':8,'top':18,**btn,'icons':['deployer/mode_place','deployer/mode_drop']}},
      'player_inventory':{'left':8,'top':84},
      'text':{'title':{'key':'item.encodedlogistics.deployer_plane','left':8,'top':5,'color':'TEXT'},'inventory':{'key':'container.inventory','left':8,'top':72,'color':'TEXT_MUTED'}}},
      open(S+'deployer_plane.json','w',newline='\n'),indent=1)
    json.dump({**pal,'background':{'texture':'gui/collector_plane.png','width':176,'height':166},
      'slots':{'module':{'left':8,'top':18,'accepts':'#encodedlogistics:plane_modules','ghost':'port/ghost_module'},
               'filter':{'left':62,'top':18,'columns':3,'rows':3,'ghost_items':True}},
      'widgets':{'filter_options':{'left':134,'top':18,'spacing':20,**btn,'when':'filter_module','options':['deny','tags','components']}},
      'player_inventory':{'left':8,'top':84},
      'text':{'title':{'key':'item.encodedlogistics.collector_plane','left':8,'top':5,'color':'TEXT'},'inventory':{'key':'container.inventory','left':8,'top':72,'color':'TEXT_MUTED'}}},
      open(S+'collector_plane.json','w',newline='\n'),indent=1)
if __name__=='__main__':
    A=sys.argv[1]; T=A+'textures/'; os.makedirs(T+'gui',exist_ok=True)
    handheld(T); relay_gui(T); bridge_gui(T); p2p_gui(T); collector_gui(T); deployer_gui(T); screens(A); print('gui ok')
