# Settings panels for rack device batch 2 (176x168 top panels, same kit as the Firewall/Router/UPS panels).
import os, json, sys
from PIL import Image
from p1_gui import C, FILL, OUT, SLOT, panel, slot
from gui_p3 import inset, bar_track, sprite, icon
from rack_gui import top_panel
W_=C('#E6E6E6'); M_=C('#9A9A9A'); MINT=[C('#127A57'),C('#1FB582'),C('#5CF0B8')]
def base(): im=Image.new('RGBA',(256,256),(0,0,0,0)); p=im.load(); top_panel(p); return im,p
def switch_gui(T):
    im,p=base(); bar_track(p,8,20,100,6); inset(p,8,32,160,104)
    for r in range(1,8):
        for x in range(10,166): p[x,32+r*13]=C('#232323')
    inset(p,8,140,160,22); im.save(T+'gui/rack/switch.png')
def l3_gui(T):
    im,p=base(); inset(p,8,30,160,112)
    for r in range(1,8):
        for x in range(10,166): p[x,30+r*14]=C('#232323')
    im.save(T+'gui/rack/l3_switch.png')
    S=T+'gui/sprites/rack/switch/'
    sprite(S+'tab_active.png',52,13,lambda x,y: OUT if (x in (0,51) or y==0) else C('#7E7E7E') if (x==1 or y==1) else C('#4B4B4B'))
    sprite(S+'tab_inactive.png',52,12,lambda x,y: OUT if (x in (0,51) or y in (0,11)) else C('#626262') if (x==1 or y==1) else C('#3A3A3A'))
    sprite(S+'lane_bar_track.png',32,4,lambda x,y: C('#0E0E0E') if (x==0 or y==0) else C('#5C5C5C') if (x==31 or y==3) else C('#1F1F1F'))
    sprite(S+'lane_bar_fill.png',30,2,lambda x,y: MINT[2] if y==0 else MINT[1])
    icon(S+'qos_high.png',['................','.......##.......','......####......','.....######.....','.......##.......','.......##.......','.......##.......'],C('#F5B23A'))
def server_gui(T):
    im,p=base(); inset(p,8,22,160,64); bar_track(p,8,100,160,8); inset(p,8,118,160,44)
    im.save(T+'gui/rack/server.png')
def fab_gui(T):
    im,p=base()
    for r in range(3):
        for c in range(3): slot(p,30+c*18,22+r*18)
    ax,ay=92,40
    for y in range(17):
        for x in range(24):
            if (x<15 and 6<=y<=10) or (x>=15 and abs(y-8)<=8-(x-15)): p[ax+x,ay+y]=C('#2A2A2A')
    slot(p,140,32); slot(p,140,50); inset(p,8,84,160,40)
    im.save(T+'gui/rack/fabrication_server.png')
def monitor_gui(T):
    im,p=base(); inset(p,8,44,160,104)
    for gy in range(1,4):
        for x in range(10,166):
            if x%2==0: p[x,44+gy*26]=C('#3A3A3A')
    for gx in range(1,6):
        for y in range(46,146):
            if y%2==0: p[10+gx*26,y]=C('#3A3A3A')
    im.save(T+'gui/rack/monitoring_server.png')
    S=T+'gui/sprites/rack/monitor/'
    for n,col in (('line_items',C('#4A8FE0')),('line_energy',C('#E5483C')),('line_lanes',C('#00D992')),('line_jobs',C('#E8C24A'))):
        sprite(S+n+'.png',2,2,lambda x,y,c=col: c)
    icon(S+'stat_items.png',['................','..##########....','..#........#....','..#.######.#....','..#........#....','..##########....'],C('#4A8FE0'))
    icon(S+'stat_energy.png',['................','.......####.....','......####......','.....#######....','........###.....','.......###......','......##........','.....#..........'],C('#E5483C'))
    icon(S+'stat_lanes.png',['................','..#.#.#.#.#.....','..#.#.#.#.#.....','..#.#.#.#.#.....','..#.#.#.#.#.....','..##########....'],C('#00D992'))
    icon(S+'stat_jobs.png',['................','..###.###.###...','..#.#.#.#.#.#...','..###.###.###...','................','..###.###.###...','..#.#.#.#.#.#...','..###.###.###...'],C('#E8C24A'))
def nas_gui(T):
    im,p=base()
    for i in range(6): slot(p,34+i*18,22)
    inset(p,8,52,160,40); im.save(T+'gui/rack/nas.png')
def san_gui(T):
    im,p=base()
    for r in range(4):
        for c in range(6): slot(p,8+c*18,22+r*18)
    for r in range(2):
        for c in range(2): slot(p,124+c*18,22+r*18)
    inset(p,8,100,160,60); im.save(T+'gui/rack/san.png')
    sprite(T+'gui/sprites/rack/storage/ghost_drive.png',16,16,lambda x,y:(255,255,255,70) if ((y in (3,12) and 2<=x<=13) or (x in (2,13) and 3<=y<=12) or (y==10 and 4<=x<=11)) else None)
def hud(T):
    S=T+'gui/sprites/hud/'
    G=C('#E8C24A'); D=C('#B88E14')
    sprite(S+'badge_scheduler.png',9,9,lambda x,y: None if (x in (0,8) and y in (0,8)) else (OUT if (x in (0,8) or y in (0,8)) else (G if (x in (2,4,6) or y in (2,4,6)) and 1<x<7 and 1<y<7 else D)))
def screens(A):
    S=A+'screens/rack/'; os.makedirs(S,exist_ok=True); pal={'includes':['common/palette.json']}
    back={'left':150,'top':2,'sprite':'rack/back','button':'terminal/button','tooltip':'gui.encodedlogistics.rack.back'}
    wide='common/button_wide'
    sw={**pal,'top':{'texture':'gui/rack/switch.png','width':176,'height':168},'back':back,
        'widgets':{'pool':{'left':9,'top':21,'width':98,'sprite':'common/bar_fill_mint'},
                   'devices':{'left':10,'top':34,'rows':8,'row_height':13,'icon':[1,0],'name':[19,3],
                              'lanes':{'left':86,'top':5,'track':'rack/switch/lane_bar_track','fill':'rack/switch/lane_bar_fill'},
                              'segment':{'left':120,'top':0,'width':44,'height':12,'button':wide,'note':'cycles the known segments; "-" = default'}},
                   'uplink':{'left':12,'top':145,'dot':'hud/dot_online'}},
        'text':{'pool':{'key':'gui.encodedlogistics.switch.pool','right':168,'top':19,'color':'TEXT_MUTED'},
                'uplink':{'key':'gui.encodedlogistics.switch.uplink','left':20,'top':144,'color':'TEXT'}}}
    for n in ('l2_switch_24','l2_switch_48'):
        d=json.loads(json.dumps(sw)); d['text']['title']={'key':f'item.encodedlogistics.{n}','left':8,'top':5,'color':'TEXT'}; json.dump(d,open(S+f'{n}.json','w'),indent=1)
    json.dump({**pal,'top':{'texture':'gui/rack/l3_switch.png','width':176,'height':168},'back':back,
      'tabs':{'left':8,'top':17,'spacing':53,'active':'rack/switch/tab_active','inactive':'rack/switch/tab_inactive',
              'items':['gui.encodedlogistics.l3.tab.devices','gui.encodedlogistics.l3.tab.routes','gui.encodedlogistics.l3.tab.qos']},
      'pages':{'devices':'as l2_switch_48.json devices list, inside (10,32)',
               'routes':{'rows':8,'row_height':14,'columns':{'source':2,'arrow':44,'dest':62,'filter':112},'arrow':'rack/router/arrow','filter_ghost':True,
                         'add':{'left':8,'top':146,'width':160,'height':14,'button':wide,'text':'gui.encodedlogistics.l3.add_route'}},
               'qos':{'rows':8,'row_height':14,'columns':{'icon':2,'kind':14,'target':70,'level':130},'icon':'rack/switch/qos_high',
                      'kinds':['crafting_jobs','port','item_filter'],
                      'add':{'left':8,'top':146,'width':160,'height':14,'button':wide,'text':'gui.encodedlogistics.l3.add_qos'}}},
      'text':{'title':{'key':'item.encodedlogistics.l3_switch','left':8,'top':5,'color':'TEXT'}}},open(S+'l3_switch.json','w'),indent=1)
    for n,lines in (('compute_server',['threads_provided','threads_in_use','scheduler']),('memory_server',['buffer_capacity','buffer_in_use','scheduler'])):
        json.dump({**pal,'top':{'texture':'gui/rack/server.png','width':176,'height':168},'back':back,
          'widgets':{'readouts':{'left':12,'top':26,'line_height':12,'lines':lines},'bar':{'left':9,'top':101,'width':158,'sprite':'common/bar_fill_gold'},
                     'jobs':{'left':12,'top':122,'rows':3,'row_height':12,'note':'jobs using this rack scheduler'}},
          'text':{'title':{'key':f'item.encodedlogistics.{n}','left':8,'top':5,'color':'TEXT'}}},open(S+f'{n}.json','w'),indent=1)
    json.dump({**pal,'top':{'texture':'gui/rack/fabrication_server.png','width':176,'height':168},'back':back,
      'slots':{'schematics':{'left':31,'top':23,'columns':3,'rows':3,'accepts':'encodedlogistics:encoded_schematic_crafting'},
               'modules':{'left':141,'top':[33,51],'accepts':'encodedlogistics:throughput_module','ghost':'port/ghost_module'}},
      'widgets':{'progress':{'left':92,'top':40,'sprite':'lithography_press/progress','fill':'left_to_right'},'job':{'left':12,'top':88,'rows':3,'line_height':12}},
      'text':{'title':{'key':'item.encodedlogistics.fabrication_server','left':8,'top':5,'color':'TEXT'}}},open(S+'fabrication_server.json','w'),indent=1)
    json.dump({**pal,'top':{'texture':'gui/rack/monitoring_server.png','width':176,'height':168},'back':back,
      'widgets':{'stats':{'left':8,'top':18,'spacing':20,'button':'terminal/button','icons':['rack/monitor/stat_items','rack/monitor/stat_energy','rack/monitor/stat_lanes','rack/monitor/stat_jobs']},
                 'ranges':{'left':92,'top':21,'spacing':19,'width':18,'height':12,'button':wide,'labels':['1m','10m','1h','1d']},
                 'graph':{'inset':[8,44,160,104],'plot':[10,46,156,100],'line':{'items':'rack/monitor/line_items','energy':'rack/monitor/line_energy','lanes':'rack/monitor/line_lanes','jobs':'rack/monitor/line_jobs'},
                          'grid':'3 horizontal + 5 vertical dotted lines baked in','axis_labels':{'max':[12,48],'now':[150,150]},'hover':'tooltip with time + value'},
                 'current':{'left':8,'top':152,'color':'TEXT'}},
      'text':{'title':{'key':'item.encodedlogistics.monitoring_server','left':8,'top':5,'color':'TEXT'}}},open(S+'monitoring_server.json','w'),indent=1)
    access={'button':'terminal/button','icons':['inventory_tap/access_read_write','inventory_tap/access_read','inventory_tap/access_write']}
    prio={'field':{'sprite':'inventory_tap/number_field','sprite_focused':'inventory_tap/number_field_focused','min':-999,'max':999},
          'up':'inventory_tap/step_up','down':'inventory_tap/step_down'}
    json.dump({**pal,'top':{'texture':'gui/rack/nas.png','width':176,'height':168},'back':back,
      'slots':{'drives':{'left':[35,53,71,89,107,125],'top':23,'accepts':'#encodedlogistics:storage_drives','ghost':'rack/storage/ghost_drive','fill_led':True}},
      'widgets':{'priority':{**prio,'left':12,'top':60},'access':{**access,'left':60,'top':56},'capacity':{'left':12,'top':78,'width':152,'sprite':'common/bar_fill_mint'}},
      'text':{'title':{'key':'item.encodedlogistics.nas','left':8,'top':5,'color':'TEXT'},'priority':{'key':'gui.encodedlogistics.tap.priority','left':12,'top':55,'color':'TEXT_MUTED'}}},
      open(S+'nas.json','w'),indent=1)
    json.dump({**pal,'top':{'texture':'gui/rack/san.png','width':176,'height':168},'back':back,
      'slots':{'drives':{'left':9,'top':23,'columns':6,'rows':4,'accepts':'#encodedlogistics:storage_drives','ghost':'rack/storage/ghost_drive','fill_led':True},
               'transceivers':{'left':125,'top':23,'columns':2,'rows':2,'accepts':'encodedlogistics:optical_transceiver','ghost':'relay/ghost_transceiver'}},
      'widgets':{'priority':{**prio,'left':12,'top':110},'access':{**access,'left':60,'top':106},'capacity':{'left':12,'top':130,'width':152,'sprite':'common/bar_fill_mint'},
                 'uplink':{'left':12,'top':144}},
      'text':{'title':{'key':'item.encodedlogistics.san','left':8,'top':5,'color':'TEXT'}}},open(S+'san.json','w'),indent=1)
    # Router update: same texture (gui/rack/router.png); the left list now shows remote networks
    r=json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','src','main','resources','assets','encodedlogistics','screens','rack','router.json')))
    # (already converted when it has no 'segments' list)
    if 'segments' in r['widgets']:
        r['widgets']['networks']=r['widgets'].pop('segments'); r['text']['networks']=r['text'].pop('segments')
    r['widgets']['networks']['note']='known remote networks (linked with a Link Card); click to rename'
    r['widgets']['rules']['note']='source network -> destination network + item filter'
    r['text']['networks']['key']='gui.encodedlogistics.router.networks'
    r['text']['rules']['key']='gui.encodedlogistics.router.wan_rules'
    json.dump(r,open(S+'router.json','w'),indent=1)
if __name__=='__main__':
    A=sys.argv[1]; T=A+'textures/'; os.makedirs(T+'gui/rack',exist_ok=True)
    switch_gui(T); l3_gui(T); server_gui(T); fab_gui(T); monitor_gui(T); nas_gui(T); san_gui(T); hud(T); screens(A); print('gui ok')
