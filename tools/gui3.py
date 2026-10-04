# Batch 3 GUIs + terminal integration sprites (lighter grey kit).
import os, json, sys
from PIL import Image
from p1_gui import C, FILL, OUT, SLOT, panel, slot
from gui_p3 import inset, bar_track, sprite, icon
from rack_gui import top_panel
import devices3 as D
W_=C('#E6E6E6'); M_=C('#9A9A9A'); MINT=[C('#127A57'),C('#1FB582'),C('#5CF0B8')]; AMB=C('#F5B23A'); BLU=C('#4A8FE0')
def base(): im=Image.new('RGBA',(256,256),(0,0,0,0)); p=im.load(); top_panel(p); return im,p
def wireless_gui(T):
    im,p=base(); inset(p,8,20,160,118)
    for r in range(1,9):
        for x in range(10,166): p[x,20+r*13]=C('#232323')
    inset(p,8,142,160,20); im.save(T+'gui/rack/wireless_controller.png')
def library_gui(T):
    # Magazine tab: 8x3 visible tape slots (scroll for 24/48), fill bar under each, 4 drive bays + status chips, cold bar
    im,p=base()
    for r in range(3):
        for c in range(8): slot(p,8+c*18,32+r*20)
    for y in range(32,90):
        for x in range(156,166): p[x,y]=SLOT[0] if x==156 or y==32 else SLOT[3] if x==165 or y==89 else C('#2A2A2A')
    for i in range(4): slot(p,8+i*40,98); inset(p,27+i*40,99,18,16)
    bar_track(p,8,124,160,8); inset(p,8,138,160,24)
    im.save(T+'gui/rack/tape_library.png')
    im,p=base()
    inset(p,8,32,160,30)                                                   # age + free-space rows
    for r in range(1): 
        for c in range(9): slot(p,8+c*18,80)                               # keep-hot filters
    for c in range(9): slot(p,8+c*18,116)                                  # pinned
    im.save(T+'gui/rack/tape_library_policy.png')
    S=T+'gui/sprites/rack/tape/'
    sprite(S+'slot_fill_track.png',16,2,lambda x,y: C('#0E0E0E') if y==0 else C('#3A3A3A'))
    for n,col in (('slot_fill_low',MINT[2]),('slot_fill_mid',C('#F0D030')),('slot_fill_high',C('#F08A2A')),('slot_fill_full',C('#E5483C'))):
        sprite(S+n+'.png',16,1,lambda x,y,c=col: c)
    for n,col,rows in (('drive_idle',MINT[2],['........','.######.','.#....#.','.#....#.','.######.']),
                       ('drive_read',BLU,['...##...','..####..','.######.','...##...','...##...']),
                       ('drive_write',AMB,['...##...','...##...','.######.','..####..','...##...']),
                       ('drive_empty',C('#6A6A6A'),['........','.#.#.#..','........','.#.#.#..','........'])):
        sprite(S+n+'.png',8,5,lambda x,y,c=col,rows=rows: c if rows[y][x]=='#' else None)
    sprite(S+'ghost_tape.png',16,16,lambda x,y:(255,255,255,70) if ((x in (2,13) and 2<=y<=13) or (y in (2,13) and 2<=x<=13) or (5<=x<=10 and y==6)) else None)
    sprite(S+'ghost_drive.png',16,16,lambda x,y:(255,255,255,70) if ((y in (5,11) and 2<=x<=13) or (x in (2,13) and 5<=y<=11) or (5<=x<=10 and y==8)) else None)
def terminal_sprites(T):
    S=T+'gui/sprites/terminal/'
    TAPE=[C('#2F5A8C'),C('#4A8FE0'),C('#A8CCF6')]
    def badge(x,y):                                                        # 7x5 tape cartridge badge (bottom-left of a slot)
        if x in (0,6) or y in (0,4): return OUT
        if y==2 and 1<x<5: return C('#FFFFFF')
        return TAPE[2] if y==1 else TAPE[1]
    sprite(S+'tape_badge.png',7,5,badge)
    sprite(S+'recall_track.png',16,2,lambda x,y: C('#0E0E0E') if y==0 else C('#2A2A2A'))
    sprite(S+'recall_fill.png',16,1,lambda x,y: TAPE[2])
    st=Image.new('RGBA',(8,32),(0,0,0,0))                                  # recall spinner: 4 frames, 8x8 (reel turning)
    import math
    for f in range(4):
        for y in range(8):
            for x in range(8):
                d=math.hypot(x-3.5,y-3.5)
                if d<=3.6:
                    a=(math.degrees(math.atan2(y-3.5,x-3.5))+f*30)%90
                    st.putpixel((x,f*8+y),(TAPE[2] if a<30 else TAPE[1]) if d>1.2 else OUT)
    os.makedirs(S,exist_ok=True); st.save(S+'recall_spinner.png'); open(S+'recall_spinner.png.mcmeta','w').write('{\n  "animation": {\n    "frametime": 3\n  }\n}\n')
    sprite(T+'gui/sprites/tooltip/tape.png',9,7,lambda x,y: OUT if (x in (0,8) or y in (0,6)) else (C('#F4F4EE') if y==3 and 2<x<6 else (TAPE[1] if y<3 else TAPE[0])))
    sprite(T+'gui/sprites/rack/craft_plan_recall.png',9,9,lambda x,y: None if (x in (0,8) and y in (0,8)) else (OUT if (x in (0,8) or y in (0,8)) else (TAPE[2] if (x+y)%3==0 else TAPE[1])))
def screens(A):
    S=A+'screens/rack/'; os.makedirs(S,exist_ok=True); pal={'includes':['common/palette.json']}
    back={'left':150,'top':2,'sprite':'rack/back','button':'terminal/button','tooltip':'gui.encodedlogistics.rack.back'}
    json.dump({**pal,'top':{'texture':'gui/rack/wireless_controller.png','width':176,'height':168},'back':back,
      'widgets':{'list':{'left':10,'top':22,'rows':9,'row_height':13,'head':[1,2],'name':[11,3],'where':{'right':130,'top':3,'color':'TEXT_MUTED','text':'dimension / "here"'},
                         'dot':{'right':140,'top':4,'sprites':['hud/dot_online','hud/dot_offline']},'unlink':{'right':152,'top':2,'sprite':'common/cancel_small','tooltip':'gui.encodedlogistics.wireless.unlink'}},
                 'summary':{'left':12,'top':148}},
      'text':{'title':{'key':'item.encodedlogistics.wireless_controller','left':8,'top':5,'color':'TEXT'}}},open(S+'wireless_controller.json','w'),indent=1)
    tabs={'left':8,'top':17,'spacing':53,'active':'rack/switch/tab_active','inactive':'rack/switch/tab_inactive',
          'items':['gui.encodedlogistics.tape.tab.magazine','gui.encodedlogistics.tape.tab.policy']}
    for n,cap,drv in (('tape_library_4u',24,2),('tape_library_6u',48,4)):
        json.dump({**pal,'back':back,'tabs':tabs,
          'pages':{'magazine':{'top':{'texture':'gui/rack/tape_library.png','width':176,'height':168},
                     'slots':{'left':9,'top':33,'columns':8,'rows_visible':3,'row_pitch':20,'total':cap,'accepts':'#encodedlogistics:lto_tapes','ghost':'rack/tape/ghost_tape',
                              'fill':{'below':17,'track':'rack/tape/slot_fill_track','fills':['rack/tape/slot_fill_low','rack/tape/slot_fill_mid','rack/tape/slot_fill_high','rack/tape/slot_fill_full']}},
                     'scrollbar':{'track':[156,32,10,58],'thumb':'rack/scroll_thumb'},
                     'drives':{'left':[9,49,89,129][:drv],'top':99,'accepts':'encodedlogistics:lto_tape_drive','ghost':'rack/tape/ghost_drive',
                               'status':{'offset':[19,1],'sprites':{'idle':'rack/tape/drive_idle','reading':'rack/tape/drive_read','writing':'rack/tape/drive_write','empty':'rack/tape/drive_empty'}},
                               'unused_bays_hidden':drv<4},
                     'cold_bar':{'left':9,'top':125,'width':158,'sprite':'common/bar_fill_mint'},
                     'activity':{'left':12,'top':142,'rows':2,'line_height':10}},
                   'policy':{'top':{'texture':'gui/rack/tape_library_policy.png','width':176,'height':168},
                     'age':{'label':[12,36],'field':{'left':80,'top':35,'sprite':'inventory_tap/number_field','min':1,'max':9999},
                            'unit':{'left':120,'top':33,'button':'common/button_wide','width':40,'height':14,'cycle':['min','h','d']}},
                     'free_space':{'label':[12,50],'field':{'left':80,'top':49,'sprite':'inventory_tap/number_field','min':0,'max':100,'suffix':'%'},
                                   'toggle':{'left':120,'top':47,'button':'common/button_wide','width':40,'height':14,'cycle':['on','off']}},
                     'keep_hot':{'label':[8,70],'slots':{'left':9,'top':81,'columns':9,'ghost_items':True,'tags':'fuzzy rules allowed (Fuzzy Match Module semantics)'}},
                     'pinned':{'label':[8,106],'slots':{'left':9,'top':117,'columns':9,'ghost_items':True}},
                     'note':{'left':8,'top':140,'color':'TEXT_MUTED'}}},
          'text':{'title':{'key':f'item.encodedlogistics.{n}','left':8,'top':5,'color':'TEXT'}}},open(S+f'{n}.json','w'),indent=1)
    json.dump({'includes':['terminal/base_terminal.json'],'text':{'title':{'key':'item.encodedlogistics.rack_console'}},
               '$comment':'Rack Console = Access Terminal screen, opened from the rack (no new art)'},open(A+'screens/rack_console.json','w'),indent=1)
    json.dump({'$comment':'Cold (on-tape) items in terminals and the Craft Plan',
      'slot':{'tape_badge':{'sprite':'terminal/tape_badge','offset':[0,11],'note':'bottom-left of the 16x16 item; drawn above the item, below the count'},
              'recall':{'track':'terminal/recall_track','fill':'terminal/recall_fill','offset':[0,14],'spinner':'terminal/recall_spinner','spinner_offset':[8,0],
                        'note':'while a recall the player asked for is running: progress bar across the slot bottom + spinner top-right'}},
      'tooltip':{'icon':'tooltip/tape','lines':['tooltip.encodedlogistics.tape.on_tape','tooltip.encodedlogistics.tape.recall_eta','tooltip.encodedlogistics.tape.recalling'],
                 'colors':{'on_tape':'#7FB3F0','eta':'TEXT_MUTED','recalling':'ACCENT'}},
      'craft_plan':{'row_marker':'rack/craft_plan_recall','marker_column':'stored','summary_line':'gui.encodedlogistics.craft.recall_total',
                    'note':'cold items count as Have, marked with the tape icon; the plan adds a "Includes tape recall: +Ns" line and the job ETA includes it'}},
      open(A+'screens/terminal/cold_items.json','w'),indent=1)
if __name__=='__main__':
    A=sys.argv[1]; T=A+'textures/'; os.makedirs(T+'gui/rack',exist_ok=True); os.makedirs(A+'screens/terminal',exist_ok=True)
    wireless_gui(T); library_gui(T); terminal_sprites(T); screens(A); print('gui ok')
