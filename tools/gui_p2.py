# Phase 2 GUIs (same kit as the controller/Phase 1 screens): ports, inventory tap, threshold sensor, terminal crafting.
import os, json, sys
from PIL import Image
from p1_gui import C, FILL, OUT, SLOT, panel, slot, inventory, header, corners, TW
def sprite(path,w,h,fn):
    im=Image.new('RGBA',(w,h),(0,0,0,0)); q=im.load()
    for y in range(h):
        for x in range(w):
            c=fn(x,y)
            if c: q[x,y]=c
    os.makedirs(os.path.dirname(path),exist_ok=True); im.save(path)
def glyph(path,rows,cols):
    """16x16 icon from a row map: chars map to colours in `cols` ('.' = clear)."""
    sprite(path,16,16,lambda x,y: cols.get(rows[y][x]) if y<len(rows) and x<len(rows[y]) else None)
def field_fn(w,border): return lambda x,y: SLOT[0] if (x==0 or y==0) else SLOT[3] if (x==w-1 or y==11) else (border if (x==1 or y==1 or x==w-2 or y==10) else C('#1C1C1C'))
W_=C('#E6E6E6'); M_=C('#9A9A9A'); R_=C('#E5483C'); R2=C('#8E231C'); A_=C('#00D992'); D_=C('#555555')
PORT={'redstone':(8,18),'filter':(61,18),'modules_x':151,'modules_y':[18,36,54,72],'inv':(7,93),'h':176}
TAP_={'priority_field':(8,31,36,12),'up':(46,31),'down':(46,37),'access':(8,56),'filter':(97,18),'inv':(7,93),'h':176}
SENS={'slot':(25,32),'field':(52,35,70,12),'mode':(130,31),'inv':(7,83),'h':166}
def port_gui(T):
    W,Hh=176,PORT['h']; im=Image.new('RGBA',(256,256),(0,0,0,0)); p=im.load()
    panel(p,0,0,W,Hh); corners(p,W,Hh); header(p,W)
    fx,fy=PORT['filter']
    for r in range(3):
        for c in range(3): slot(p,fx+c*18,fy+r*18)
    for y in PORT['modules_y']: slot(p,PORT['modules_x'],y)
    for y in range(18,90): p[146,y]=C('#3A3A3A')                    # divider before the module column
    inventory(p,*PORT['inv']); im.save(T+'gui/port.png')
    S=T+'gui/sprites/port/'
    glyph(S+'redstone_ignore.png',['................']*5+['...##########...','...##########...']+['................']*9,{'#':M_})
    torch=['................','.......##.......','......####......','.......##.......','.......##.......','.......##.......','.......##.......',
           '.......##.......','.......##.......','.......##.......','.......##.......','.....######.....','................']
    glyph(S+'redstone_high.png',torch,{'#':R_}); glyph(S+'redstone_low.png',torch,{'#':R2})
    glyph(S+'redstone_pulse.png',['................']*4+['..####....####..','..#..#....#..#..','..#..#....#..#..','..#..#....#..#..','###..######..###']+['................']*7,{'#':R_})
    sprite(S+'ghost_module.png',16,16,lambda x,y:(255,255,255,70) if ((x in (3,12) and 2<=y<=13) or (y in (2,13) and 3<=x<=12) or (y==11 and 4<=x<=11 and x%2==0)) else None)
def tap_gui(T):
    W,Hh=176,TAP_['h']; im=Image.new('RGBA',(256,256),(0,0,0,0)); p=im.load()
    panel(p,0,0,W,Hh); corners(p,W,Hh); header(p,W)
    fx,fy=TAP_['filter']
    for r in range(3):
        for c in range(3): slot(p,fx+c*18,fy+r*18)
    for y in range(18,90): p[88,y]=C('#3A3A3A')
    inventory(p,*TAP_['inv']); im.save(T+'gui/inventory_tap.png')
    S=T+'gui/sprites/inventory_tap/'
    sprite(S+'number_field.png',36,12,field_fn(36,C('#232323'))); sprite(S+'number_field_focused.png',36,12,field_fn(36,C('#00A06B')))
    up=lambda x,y: (W_ if abs(x-4)<=y else None) if y<5 else None
    sprite(S+'step_up.png',9,6,lambda x,y: W_ if (y>=1 and abs(x-4)<=y-1) else None)
    sprite(S+'step_down.png',9,6,lambda x,y: W_ if (y<=4 and abs(x-4)<=4-y) else None)
    sprite(S+'step_up_hover.png',9,6,lambda x,y: A_ if (y>=1 and abs(x-4)<=y-1) else None)
    sprite(S+'step_down_hover.png',9,6,lambda x,y: A_ if (y<=4 and abs(x-4)<=4-y) else None)
    rw=['................','..#####..#####..','..#...#..#...#..','..#.#.#..#.#.#..','..#...#..#...#..','..#####..#####..','.....##..##.....','......####......',
        '.......##.......','................','..############..','................']
    glyph(S+'access_read_write.png',rw,{'#':W_})
    glyph(S+'access_read.png',['................','....######......','....#....#......','....#.##.#......','....#....#......','....######......','.......#........','......###.......',
                               '.....#####......','.......#........','.......#........','................'],{'#':C('#4A8FE0')})
    glyph(S+'access_write.png',['................','.......#........','.......#........','.....#####......','......###.......','.......#........','....######......','....#....#......',
                                '....#.##.#......','....#....#......','....######......','................'],{'#':C('#E8913A')})
def sensor_gui(T):
    W,Hh=176,SENS['h']; im=Image.new('RGBA',(256,256),(0,0,0,0)); p=im.load()
    panel(p,0,0,W,Hh); corners(p,W,Hh); header(p,W)
    slot(p,*SENS['slot'],26,26) if False else slot(p,SENS['slot'][0],SENS['slot'][1])
    inventory(p,*SENS['inv']); im.save(T+'gui/threshold_sensor.png')
    S=T+'gui/sprites/threshold_sensor/'
    sprite(S+'number_field.png',70,12,field_fn(70,C('#232323'))); sprite(S+'number_field_focused.png',70,12,field_fn(70,C('#00A06B')))
    glyph(S+'compare_above.png',['................','................','....##..........','.....##.........','......##........','.......##.......','......##........','.....##.........','....##..........'],{'#':W_})
    glyph(S+'compare_below.png',['................','................','..........##....','.........##.....','........##......','.......##.......','........##......','.........##.....','..........##....'],{'#':W_})
    glyph(S+'compare_equal.png',['................','................','................','....########....','................','................','....########....'],{'#':W_})
CRAFT={'h':76,'grid':(30,8),'arrow':(92,26),'output':(122,21),'clear':(84,8)}
def terminal_crafting(T):
    """Fabrication Terminal section: inserted between the item-grid rows and the bottom piece (195 x 76)."""
    im=Image.new('RGBA',(256,128),(0,0,0,0)); p=im.load(); panel(p,0,0,TW,CRAFT['h'],'lr')
    for x in range(8,170): p[x,0]=SLOT[3]                           # closes the item-grid well (as bottom.png does)
    for x in range(175,187): p[x,0]=SLOT[3]
    for x in range(8,187): p[x,3]=C('#3A3A3A')                      # divider under the item grid
    gx,gy=CRAFT['grid']
    for r in range(3):
        for c in range(3): slot(p,gx+c*18,gy+r*18)
    ax,ay=CRAFT['arrow']
    for y in range(17):
        for x in range(24):
            if (x<15 and 6<=y<=10) or (x>=15 and abs(y-8)<=8-(x-15)): p[ax+x,ay+y]=C('#2A2A2A')
    slot(p,*CRAFT['output'],26,26)
    os.makedirs(T+'gui/terminal',exist_ok=True); im.save(T+'gui/terminal/crafting.png')
    # bottom piece without the well lip, drawn after any section (the section has already closed the well)
    import importlib
    src=Image.open(T+'gui/terminal/bottom.png') if os.path.exists(T+'gui/terminal/bottom.png') else None
    if src is None:
        import sys as _s; _s.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
        from p1_gui import term_kit
    b=Image.new('RGBA',(256,128),(0,0,0,0)); q=b.load(); panel(q,0,0,TW,99,'blr'); inventory(q,8,16)
    q[0,98]=(0,0,0,0); q[TW-1,98]=(0,0,0,0); b.save(T+'gui/terminal/bottom_plain.png')
    S=T+'gui/sprites/terminal/'
    xm=lambda col: (lambda x,y: OUT if (x in (0,8) or y in (0,8)) else (col if (x==y or x==8-y) and 1<x<7 else C('#4A4A4A')))
    sprite(S+'clear_grid.png',9,9,xm(C('#C8C8C8'))); sprite(S+'clear_grid_hover.png',9,9,xm(C('#E5483C')))
def screens(A):
    os.makedirs(A+'screens',exist_ok=True)
    pal={'includes':['common/palette.json']}
    port={**pal,'background':{'texture':'gui/port.png','width':176,'height':176},
          'slots':{'filter':{'left':62,'top':19,'columns':3,'rows':3,'ghost_items':True},
                   'modules':{'left':152,'top':[19,37,55,73],'accepts':'#encodedlogistics:port_modules','ghost':'port/ghost_module'}},
          'widgets':{'redstone_mode':{'left':8,'top':18,'button':'terminal/button','button_hover':'terminal/button_hover',
                     'icons':['port/redstone_ignore','port/redstone_high','port/redstone_low','port/redstone_pulse']}},
          'player_inventory':{'left':8,'top':94},
          'text':{'title':{'left':8,'top':5,'color':'TEXT'},'filter_label':{'key':'gui.encodedlogistics.port.filter','left':62,'top':5,'color':'TEXT_MUTED','hidden':True},
                  'inventory':{'key':'container.inventory','left':8,'top':82,'color':'TEXT_MUTED'}}}
    for kind in ('ingress_port','egress_port'):
        d=dict(port); d['text']=dict(port['text']); d['text']['title']={'key':f'item.encodedlogistics.{kind}','left':8,'top':5,'color':'TEXT'}
        json.dump(d,open(A+f'screens/{kind}.json','w',newline='\n'),indent=1)
    json.dump({**pal,'background':{'texture':'gui/inventory_tap.png','width':176,'height':176},
      'slots':{'filter':{'left':98,'top':19,'columns':3,'rows':3,'ghost_items':True}},
      'widgets':{'priority':{'field':{'left':8,'top':31,'sprite':'inventory_tap/number_field','sprite_focused':'inventory_tap/number_field_focused','min':-999,'max':999},
                             'up':{'left':46,'top':31,'sprite':'inventory_tap/step_up','hover':'inventory_tap/step_up_hover'},
                             'down':{'left':46,'top':37,'sprite':'inventory_tap/step_down','hover':'inventory_tap/step_down_hover'}},
                 'access_mode':{'left':8,'top':56,'button':'terminal/button','button_hover':'terminal/button_hover',
                                'icons':['inventory_tap/access_read_write','inventory_tap/access_read','inventory_tap/access_write']}},
      'player_inventory':{'left':8,'top':94},
      'text':{'title':{'key':'item.encodedlogistics.inventory_tap','left':8,'top':5,'color':'TEXT'},
              'priority':{'key':'gui.encodedlogistics.tap.priority','left':8,'top':21,'color':'TEXT_MUTED'},
              'access':{'key':'gui.encodedlogistics.tap.access','left':30,'top':61,'color':'TEXT_MUTED'},
              'inventory':{'key':'container.inventory','left':8,'top':82,'color':'TEXT_MUTED'}}},open(A+'screens/inventory_tap.json','w',newline='\n'),indent=1)
    json.dump({**pal,'background':{'texture':'gui/threshold_sensor.png','width':176,'height':166},
      'slots':{'item':{'left':26,'top':33,'ghost_item':True}},
      'widgets':{'threshold':{'left':52,'top':35,'sprite':'threshold_sensor/number_field','sprite_focused':'threshold_sensor/number_field_focused','min':0,'max':2147483647},
                 'compare_mode':{'left':130,'top':31,'button':'terminal/button','button_hover':'terminal/button_hover',
                                 'icons':['threshold_sensor/compare_above','threshold_sensor/compare_below','threshold_sensor/compare_equal']}},
      'player_inventory':{'left':8,'top':84},
      'text':{'title':{'key':'item.encodedlogistics.threshold_sensor','left':8,'top':5,'color':'TEXT'},
              'item_label':{'key':'gui.encodedlogistics.sensor.item','left':26,'top':22,'color':'TEXT_MUTED'},
              'threshold_label':{'key':'gui.encodedlogistics.sensor.threshold','left':52,'top':22,'color':'TEXT_MUTED'},
              'state':{'left':52,'top':52,'color':'ACCENT'},
              'inventory':{'key':'container.inventory','left':8,'top':72,'color':'TEXT_MUTED'}}},open(A+'screens/threshold_sensor.json','w',newline='\n'),indent=1)
    os.makedirs(A+'screens/terminal',exist_ok=True)
    json.dump({'$comment':'Optional section for terminals with features.crafting_grid: drawn between the item-grid rows and the bottom piece.',
      'texture':'gui/terminal/crafting.png','height':76,'bottom_texture':'gui/terminal/bottom_plain.png',
      'grid':{'left':31,'top':9,'columns':3,'rows':3},'output':{'left':127,'top':26,'well':[122,21,26,26]},
      'arrow':{'left':92,'top':26},'clear':{'left':84,'top':8,'sprite':'terminal/clear_grid','hover':'terminal/clear_grid_hover'}},
      open(A+'screens/terminal/crafting_section.json','w',newline='\n'),indent=1)
    json.dump({'includes':['terminal/base_terminal.json'],'text':{'title':{'key':'gui.encodedlogistics.fabrication_terminal'}},
               'features':{'crafting_grid':True},'sections':{'crafting':'terminal/crafting_section.json'}},
              open(A+'screens/fabrication_terminal.json','w',newline='\n'),indent=1)
if __name__=='__main__':
    A=sys.argv[1]; T=A+'textures/'
    os.makedirs(T+'gui',exist_ok=True); port_gui(T); tap_gui(T); sensor_gui(T); terminal_crafting(T); screens(A); print('gui ok')
