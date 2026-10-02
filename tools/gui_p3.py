# Phase 3 GUIs - same kit (lighter grey panels, inset slots, Arcforge-style bars) as the earlier screens.
import os, json, sys
from PIL import Image
from p1_gui import C, FILL, OUT, SLOT, panel, slot, inventory, header, corners, TW
def new(w,h): im=Image.new('RGBA',(256,256),(0,0,0,0)); p=im.load(); panel(p,0,0,w,h); corners(p,w,h); header(p,w); return im,p
def inset(p,x0,y0,w,h):
    for y in range(y0,y0+h):
        for x in range(x0,x0+w):
            p[x,y]=SLOT[0] if (x==x0 or y==y0) else SLOT[3] if (x==x0+w-1 or y==y0+h-1) else SLOT[1] if (x==x0+1 or y==y0+1) else C('#2A2A2A')
def bar_track(p,x0,y0,w,h=8):
    for y in range(y0,y0+h):
        for x in range(x0,x0+w): p[x,y]=C('#0E0E0E') if (x==x0 or y==y0) else C('#5C5C5C') if (x==x0+w-1 or y==y0+h-1) else C('#1F1F1F')
def arrow(p,ax,ay):
    for y in range(17):
        for x in range(24):
            if (x<15 and 6<=y<=10) or (x>=15 and abs(y-8)<=8-(x-15)): p[ax+x,ay+y]=C('#2A2A2A')
def sprite(path,w,h,fn):
    im=Image.new('RGBA',(w,h),(0,0,0,0)); q=im.load()
    for y in range(h):
        for x in range(w):
            c=fn(x,y)
            if c: q[x,y]=c
    os.makedirs(os.path.dirname(path),exist_ok=True); im.save(path)
def icon(path,rows,col):
    sprite(path,16,16,lambda x,y: col if y<len(rows) and x<len(rows[y]) and rows[y][x]=='#' else None)
W_=C('#E6E6E6'); GOLD=C('#E8C24A'); TEAL=C('#27C4B4'); MINT=[C('#127A57'),C('#1FB582'),C('#5CF0B8')]
def kit(T):
    S=T+'gui/sprites/common/'
    def btn(face,lit,dark):
        return lambda x,y: OUT if (y in (0,17) or x in (0,199)) else lit if (y==1 or x==1) else dark if (y==16 or x==198) else face
    sprite(S+'button_wide.png',200,18,btn(C('#5A5A5A'),C('#8A8A8A'),C('#333333')))
    sprite(S+'button_wide_hover.png',200,18,lambda x,y: OUT if (y in (0,17) or x in (0,199)) else C('#00A06B') if (y in (1,16) or x in (1,198)) else C('#626262'))
    sprite(S+'button_wide_pressed.png',200,18,btn(C('#444444'),C('#2A2A2A'),C('#6E6E6E')))
    sprite(S+'button_wide_disabled.png',200,18,btn(C('#4A4A4A'),C('#555555'),C('#3A3A3A')))
    sprite(S+'bar_fill_mint.png',200,6,lambda x,y: MINT[2] if y==0 else MINT[1] if y<5 else MINT[0])
    sprite(S+'bar_fill_gold.png',200,6,lambda x,y: C('#F5DE8A') if y==0 else GOLD if y<5 else C('#B88E14'))
    sprite(S+'bar_fill_red.png',200,6,lambda x,y: C('#FF9C90') if y==0 else C('#E5483C') if y<5 else C('#8E231C'))
    sprite(S+'tree_expand.png',7,7,lambda x,y: W_ if (x==3 or y==3) and 0<x<6 and 0<y<6 else C('#555555') if x in (0,6) or y in (0,6) else None)
    sprite(S+'tree_collapse.png',7,7,lambda x,y: W_ if y==3 and 0<x<6 else C('#555555') if x in (0,6) or y in (0,6) else None)
    sprite(S+'cancel_small.png',9,9,lambda x,y: OUT if (x in (0,8) or y in (0,8)) else (C('#E5483C') if (x==y or x==8-y) and 1<x<7 else C('#4A4A4A')))
    sprite(S+'row_highlight.png',200,18,lambda x,y:(0,217,146,40))
def craftable_icons(T):
    S=T+'gui/sprites/terminal/'
    rows=['................','..##########....','..#........#....','..#.##..##.#....','..#........#....','..#.##..##.#....','..#........#....','..##########....',
          '..........##....','.........####...','..........##....','................']
    icon(S+'icon_craftable_on.png',rows,GOLD); icon(S+'icon_craftable_off.png',rows,C('#7A7A7A'))
def encoder_sections(T):
    """Encoding panel, drawn between the item rows and bottom_plain (Phase 2 section mechanism). 195 x 76 each.
       Crafting: 3x3 grid -> output. Processing: 3x3 inputs -> 3 outputs (amounts drawn by code). Right column:
       blank-card slot, Encode button, encoded-card slot. Mode toggle at the left, Clear above the grid."""
    for mode in ('crafting','processing'):
        im=Image.new('RGBA',(256,128),(0,0,0,0)); p=im.load(); panel(p,0,0,TW,76,'lr')
        for x in range(8,170): p[x,0]=SLOT[3]
        for x in range(175,187): p[x,0]=SLOT[3]
        for x in range(8,187): p[x,3]=C('#3A3A3A')
        for r in range(3):
            for c in range(3): slot(p,30+c*18,8+r*18)
        arrow(p,88,26)
        if mode=='crafting': slot(p,114,21,26,26)
        else:
            for r in range(3): slot(p,116,8+r*18)
        for y in range(8,62): p[146,y]=C('#3A3A3A')
        slot(p,160,8); slot(p,160,46)
        for x in range(152,186): p[x,28]=C('#3A3A3A')
        im.save(T+f'gui/terminal/encoder_{mode}.png')
    S=T+'gui/sprites/encoder/'
    icon(S+'mode_crafting.png',['................','..###.###.###...','..#.#.#.#.#.#...','..###.###.###...','................','..###.###.###...','..#.#.#.#.#.#...','..###.###.###...','................','..###.###.###...','..#.#.#.#.#.#...','..###.###.###...'],GOLD)
    icon(S+'mode_processing.png',['................','..###...........','..#.#...........','..###..##.......','.......###......','..###..####.....','..#.#..###...###','..###..##....#.#','.............###'],TEAL)
    sprite(S+'ghost_card.png',16,16,lambda x,y:(255,255,255,70) if ((x in (3,12) and 2<=y<=13) or (y in (2,13) and 3<=x<=12) or (y==9 and 5<=x<=10)) else None)
def fabricator_gui(T):
    im,p=new(176,166)
    for r in range(3):
        for c in range(3): slot(p,44+c*18,17+r*18)
    arrow(p,108,35); slot(p,152,26); slot(p,152,44); inventory(p,7,83)
    for y in range(17,72): p[144,y]=C('#3A3A3A')
    im.save(T+'gui/fabricator.png')
def gateway_gui(T):
    im,p=new(176,206)
    for c in range(9): slot(p,7+c*18,17)          # schematics
    for c in range(9): slot(p,7+c*18,49)          # stock config (ghost + amount)
    inset(p,7,82,162,20)                          # buffer display well
    for c in range(9):
        for y in range(84,100): p[8+c*18,y]=C('#232323') if c else p[8+c*18,y]
    inventory(p,7,123)
    im.save(T+'gui/gateway.png')
def scheduler_gui(T):
    im,p=new(208,186)
    inset(p,8,28,192,64)                          # active jobs (3 rows of 20)
    for r in range(1,3):
        for x in range(10,198): p[x,28+r*20+1]=C('#232323')
    inset(p,8,108,192,44)                         # queue (2 rows)
    for x in range(10,198): p[x,129]=C('#232323')
    bar_track(p,8,162,92); bar_track(p,108,162,92)
    im.save(T+'gui/scheduler_core.png')
def craft_amount_gui(T):
    im,p=new(176,92)
    slot(p,12,30); inset(p,40,36,74,14)
    im.save(T+'gui/craft_amount.png')
def craft_plan_gui(T):
    im,p=new(220,196)
    inset(p,8,28,192,132)                         # ingredient tree, 7 rows of 18 + header line
    for x in range(10,198): p[x,29+14]=C('#232323')
    for y in range(28,160): p[204,y]=C('#161616') if y==28 else C('#2A2A2A')
    for y in range(28,160): p[205,y]=C('#2A2A2A'); p[210,y]=C('#707070')
    im.save(T+'gui/craft_plan.png')
def job_status_gui(T):
    im,p=new(220,150)
    slot(p,8,20); bar_track(p,32,30,180,10); inset(p,8,48,204,70)
    im.save(T+'gui/job_status.png')
def screens(A):
    S=A+'screens/'; os.makedirs(S+'terminal',exist_ok=True); pal={'includes':['common/palette.json']}
    wide={'sprite':'common/button_wide','hover':'common/button_wide_hover','pressed':'common/button_wide_pressed','disabled':'common/button_wide_disabled','nine_slice':[3,3]}
    for mode in ('crafting','processing'):
        sec={'texture':f'gui/terminal/encoder_{mode}.png','height':76,'bottom_texture':'gui/terminal/bottom_plain.png',
             'grid':{'left':31,'top':9,'columns':3,'rows':3,'ghost_items':True},
             'mode_toggle':{'left':8,'top':8,'button':'terminal/button','icons':['encoder/mode_crafting','encoder/mode_processing']},
             'clear':{'left':84,'top':8,'sprite':'terminal/clear_grid','hover':'terminal/clear_grid_hover'},
             'blank_card':{'left':161,'top':9,'ghost':'encoder/ghost_card'},'encode':{'left':150,'top':29,'width':38,'height':14,'button':wide,'text':'gui.encodedlogistics.encoder.encode'},
             'encoded_card':{'left':161,'top':47,'output':True}}
        if mode=='crafting': sec['output']={'left':119,'top':26,'well':[114,21,26,26],'ghost_item':True}
        else: sec['outputs']={'left':117,'top':[9,27,45],'ghost_items':True,'amounts':True}
        if mode=='processing': sec['amounts']={'note':'scroll / right-click a ghost slot to change its amount; drawn at half scale bottom-right like stack counts'}
        json.dump(sec,open(S+f'terminal/encoder_{mode}_section.json','w',newline='\n'),indent=1)
    json.dump({'includes':['terminal/base_terminal.json'],'text':{'title':{'key':'gui.encodedlogistics.schematic_encoder'}},'features':{'encoding':True},
               'sections':{'crafting':'terminal/encoder_crafting_section.json','processing':'terminal/encoder_processing_section.json'},
               'section_selector':'mode_toggle'},open(S+'schematic_encoder.json','w',newline='\n'),indent=1)
    json.dump({**pal,'background':{'texture':'gui/fabricator.png','width':176,'height':166},
      'slots':{'schematics':{'left':45,'top':18,'columns':3,'rows':3,'accepts':'encodedlogistics:encoded_schematic_crafting'},
               'modules':{'left':153,'top':[27,45],'accepts':'encodedlogistics:throughput_module','ghost':'port/ghost_module'}},
      'widgets':{'progress':{'left':108,'top':35,'sprite':'lithography_press/progress','fill':'left_to_right'}},
      'player_inventory':{'left':8,'top':84},'text':{'title':{'key':'block.encodedlogistics.fabricator','left':8,'top':5,'color':'TEXT'},
      'inventory':{'key':'container.inventory','left':8,'top':72,'color':'TEXT_MUTED'}}},open(S+'fabricator.json','w',newline='\n'),indent=1)
    json.dump({**pal,'background':{'texture':'gui/gateway.png','width':176,'height':206},
      'slots':{'schematics':{'left':8,'top':18,'columns':9,'rows':1,'accepts':'encodedlogistics:encoded_schematic_processing'},
               'stock':{'left':8,'top':50,'columns':9,'rows':1,'ghost_items':True,'amounts':True,'amount_field':{'below':True,'width':16}},
               'buffer':{'left':9,'top':84,'columns':9,'read_only':True,'note':'shows what is held for the faced machine / stocked items'}},
      'player_inventory':{'left':8,'top':124},
      'text':{'title':{'key':'block.encodedlogistics.gateway','left':8,'top':5,'color':'TEXT'},
              'stock':{'key':'gui.encodedlogistics.gateway.stock','left':8,'top':39,'color':'TEXT_MUTED'},
              'buffer':{'key':'gui.encodedlogistics.gateway.buffer','left':8,'top':72,'color':'TEXT_MUTED'},
              'inventory':{'key':'container.inventory','left':8,'top':112,'color':'TEXT_MUTED'}}},open(S+'gateway.json','w',newline='\n'),indent=1)
    json.dump({**pal,'background':{'texture':'gui/scheduler_core.png','width':208,'height':186},
      'lists':{'active':{'left':10,'top':30,'rows':3,'row_height':20,'icon':[2,2],'name':[22,2],'progress':{'left':22,'top':12,'width':120,'sprite':'common/bar_fill_mint'},
                         'cancel':{'right':6,'top':5,'sprite':'common/cancel_small'}},
               'queue':{'left':10,'top':110,'rows':2,'row_height':20,'icon':[2,2],'name':[22,6]}},
      'bars':{'threads':{'left':9,'top':163,'width':90,'sprite':'common/bar_fill_gold'},'buffer':{'left':109,'top':163,'width':90,'sprite':'common/bar_fill_mint'}},
      'text':{'title':{'key':'block.encodedlogistics.scheduler_core','left':8,'top':5,'color':'TEXT'},
              'active':{'key':'gui.encodedlogistics.scheduler.active','left':8,'top':18,'color':'TEXT_MUTED'},
              'queue':{'key':'gui.encodedlogistics.scheduler.queue','left':8,'top':98,'color':'TEXT_MUTED'},
              'threads':{'key':'gui.encodedlogistics.scheduler.threads','left':8,'top':172,'color':'TEXT_MUTED'},
              'buffer':{'key':'gui.encodedlogistics.scheduler.buffer','left':108,'top':172,'color':'TEXT_MUTED'}}},open(S+'scheduler_core.json','w',newline='\n'),indent=1)
    json.dump({**pal,'background':{'texture':'gui/craft_amount.png','width':176,'height':92},
      'widgets':{'item':{'left':13,'top':31},'amount':{'left':41,'top':37,'width':72,'min':1,'max':999999},
                 'steps':{'buttons':[['+1',40,18],['+10',66,18],['+64',92,18],['-1',40,54],['-10',66,54],['-64',92,54]],'size':[24,14],'button':wide},
                 'next':{'left':124,'top':34,'width':44,'height':18,'button':wide,'text':'gui.encodedlogistics.craft.next'}},
      'text':{'title':{'key':'gui.encodedlogistics.craft.amount','left':8,'top':5,'color':'TEXT'}}},open(S+'craft_amount.json','w',newline='\n'),indent=1)
    json.dump({**pal,'background':{'texture':'gui/craft_plan.png','width':220,'height':196},
      'tree':{'left':10,'top':44,'rows':6,'row_height':18,'indent':8,'expand':'common/tree_expand','collapse':'common/tree_collapse',
              'columns':{'name':18,'stored':112,'to_craft':142,'missing':172},'colors':{'stored':'TEXT','to_craft':'ACCENT','missing':'ERROR'},
              'header_top':31,'scrollbar':{'left':205,'top':29,'height':130}},
      'widgets':{'scheduler':{'left':8,'top':168,'width':104,'height':18,'button':wide},
                 'start':{'left':118,'top':168,'width':46,'height':18,'button':wide,'text':'gui.encodedlogistics.craft.start','disabled_when_missing':True},
                 'cancel':{'left':166,'top':168,'width':46,'height':18,'button':wide,'text':'gui.encodedlogistics.craft.cancel'}},
      'text':{'title':{'key':'gui.encodedlogistics.craft.plan','left':8,'top':5,'color':'TEXT'},'status':{'right':212,'top':18,'color':'TEXT_MUTED'}}},open(S+'craft_plan.json','w',newline='\n'),indent=1)
    json.dump({**pal,'background':{'texture':'gui/job_status.png','width':220,'height':150},
      'widgets':{'item':{'left':9,'top':21},'progress':{'left':33,'top':32,'width':178,'height':6,'sprite':'common/bar_fill_mint'},
                 'list':{'left':10,'top':50,'rows':3,'row_height':22,'columns':2,'cell':[100,22],'progress_sprite':'common/bar_fill_gold'},
                 'cancel':{'left':166,'top':124,'width':46,'height':18,'button':wide,'text':'gui.encodedlogistics.scheduler.cancel'}},
      'text':{'title':{'key':'gui.encodedlogistics.craft.status','left':8,'top':5,'color':'TEXT'},'item':{'left':32,'top':20,'color':'TEXT'}}},open(S+'job_status.json','w',newline='\n'),indent=1)
if __name__=='__main__':
    A=sys.argv[1]; T=A+'textures/'; os.makedirs(T+'gui/terminal',exist_ok=True)
    kit(T); craftable_icons(T); encoder_sections(T); fabricator_gui(T); gateway_gui(T); scheduler_gui(T); craft_amount_gui(T); craft_plan_gui(T); job_status_gui(T); screens(A); print('gui ok')
