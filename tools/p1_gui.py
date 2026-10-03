# Phase 1 GUIs: Lithography Press, Drive Bay and the modular terminal kit (Access / Fabrication / Handheld / Encoder),
# plus the GUI kit every screen shares. The kit is the Network Controller's look (textures/gui/controller.png):
# #4B4B4B panel with a 3-step bevel and transparent corners, a title strip, dark inset panels (dark top/left rim, inner
# shadow, light bottom/right rim, cut corners), #383838 separator lines, 18x18 inset slots, the Arcforge energy track,
# 8 px scroll tracks with the controller's 6x15 thumb, and empty-slot ghosts drawn as dim silhouettes of the real item.
import os, json
from PIL import Image
def C(s): return tuple(int(s[i:i+2],16) for i in (1,3,5))+(255,)
FILL,OUT=C('#4B4B4B'),C('#141414'); L1,L2,D1,D2=C('#7E7E7E'),C('#626262'),C('#262626'),C('#363636')
SLOT=(C('#161616'),C('#1F1F1F'),C('#333333'),C('#707070'))   # top/left, inner shadow, fill, bottom/right
WELL=C('#2A2A2A'); SEP=C('#383838')                          # inset fill, separator line (controller)
UV=[C('#4B2290'),C('#7A3FE0'),C('#A877FF'),C('#D9C2FF')]
CLEAR=(0,0,0,0)
def panel(p,x0,y0,w,h,sides='tblr'):
    """Panel with the controller's bevel on the listed sides (t,b,l,r): outline, 2 lit steps top/left, 2 dark steps
       bottom/right. The nearest edge wins; where a lit and a dark edge are equally near (the top-right and bottom-left
       corners) the dark one does, exactly as controller.png. Lets pieces stack seamlessly."""
    for y in range(y0,y0+h):
        for x in range(x0,x0+w):
            ds=[(d,lit) for d,s,lit in ((y-y0,'t',True),(x-x0,'l',True),(y0+h-1-y,'b',False),(x0+w-1-x,'r',False)) if s in sides and d<3]
            c=FILL
            if ds:
                d=min(d for d,_ in ds); lit=all(l for dd,l in ds if dd==d)
                c=OUT if d==0 else ((L1 if d==1 else L2) if lit else (D1 if d==1 else D2))
            p[x,y]=c
def slot(p,x0,y0,w=18,h=18):
    for y in range(y0,y0+h):
        for x in range(x0,x0+w):
            p[x,y]=SLOT[0] if (x==x0 or y==y0) else SLOT[3] if (x==x0+w-1 or y==y0+h-1) else SLOT[1] if (x==x0+1 or y==y0+1) else SLOT[2]
    p[x0+w-1,y0]=FILL; p[x0,y0+h-1]=FILL
def inset(p,x0,y0,w,h,shadow=True,fill=None):
    """The controller's inset panel: dark top/left rim, (inner shadow), #2A2A2A fill, light bottom/right rim; the
       top-right and bottom-left corner pixels are left as panel."""
    fill=fill or WELL
    for y in range(y0,y0+h):
        for x in range(x0,x0+w):
            p[x,y]=SLOT[0] if (x==x0 or y==y0) else SLOT[3] if (x==x0+w-1 or y==y0+h-1) else \
                   SLOT[1] if shadow and (x==x0+1 or y==y0+1) else fill
    p[x0+w-1,y0]=FILL; p[x0,y0+h-1]=FILL
def sep(p,x0,x1,y):
    for x in range(x0,x1): p[x,y]=SEP
def vsep(p,x,y0,y1):
    for y in range(y0,y1): p[x,y]=SEP
def scroll_track(p,x0,y0,h):
    """The controller's scroll track: 8 wide, no inner shadow; the 6x15 thumb (controller/scroll_thumb) sits at x0+1."""
    inset(p,x0,y0,8,h,shadow=False)
def inventory(p,x0,y0):
    for r in range(3):
        for c in range(9): slot(p,x0+c*18,y0+r*18)
    for c in range(9): slot(p,x0+c*18,y0+58)
def energy_track(p,x0,y0):
    for y in range(y0,y0+52):
        for x in range(x0,x0+12):
            p[x,y]=C('#0E0E0E') if (x==x0 or y==y0) else C('#5C5C5C') if (x==x0+11 or y==y0+51) else C('#1F1F1F')
    p[x0+11,y0]=C('#1F1F1F'); p[x0,y0+51]=C('#1F1F1F')
def bar_track(p,x0,y0,w,h=8):
    for y in range(y0,y0+h):
        for x in range(x0,x0+w): p[x,y]=C('#0E0E0E') if (x==x0 or y==y0) else C('#5C5C5C') if (x==x0+w-1 or y==y0+h-1) else C('#1F1F1F')
def arrow(p,ax,ay,col=C('#2A2A2A')):
    """The empty 24x17 progress arrow (the fill sprite is lithography_press/progress)."""
    for y in range(17):
        for x in range(24):
            if (x<15 and 6<=y<=10) or (x>=15 and abs(y-8)<=8-(x-15)): p[ax+x,ay+y]=col
def corners(p,W,H):
    for x,y in ((0,0),(W-1,0),(0,H-1),(W-1,H-1)): p[x,y]=CLEAR
def header(p,W):
    for y in range(3,15):
        for x in range(3,W-3): p[x,y]=C('#525252')
    for x in range(3,W-3): p[x,15]=C('#444444')
def new(w,h):
    """A whole GUI sheet: panel, transparent corners, title strip."""
    im=Image.new('RGBA',(256,256),CLEAR); p=im.load(); panel(p,0,0,w,h); corners(p,w,h); header(p,w); return im,p

# ---------------- empty-slot ghosts: dim silhouettes of the real items ----------------
# Traced from the item textures: o = outline, 1-3 = the item's dark / mid / light thirds.
GHOSTS={
 'drive':['................','....o...........','...o2oo.........','..o2333oo.......','..o333332oo.....','.o233333322oo...','o233333333322o..','o222333333332o..',
          'o112223333321o..','.oo112223322o...','...oo112222o....','.....oo1121o....','.......oo1o.....','.........o......','................','................'],
 'wafer':['................','.....oooooo.....','....o333333o....','...o33323323o...','..o3333332222o..','.o333333222322o.','.o333333222222o.','.o233333322222o.',
          '.o233233332222o.','.o332223332222o.','.o322222322222o.','..o3222222222o..','...o22222222o...','....o222222o....','.....oooooo.....','................'],
 'photomask':['................','..oooooooooooo..','.o333333333333o.','.o333333333333o.','.o322233322233o.','.o333233333233o.','.o333222333222o.','.o323332333322o.',
              '.o323332223322o.','.o322233322332o.','.o333233322233o.','.o333233222223o.','.o333332222232o.','.o322222222222o.','..oooooooooooo..','................'],
 'additive':['................','................','................','.......oo.......','......o33o......','.....o3323o.....','....o333333o....','...o33333322o...',
             '..o2332332122o..','..o3233332221o..','..o2332332121o..','...o22222111o...','....oo2211oo....','......oooo......','................','................'],
 'transceiver':['................','................','................','................','.....ooooooooo..','...oo333333333o.','...o333333332o..','..o3333333333o..',
                '..o32222222222o.','..o32222222233o.','..o32222222222o.','..o32222222222o.','...ooooooooooo..','................','................','................'],
 'module':['...oooooooooo...','..o3333333333o..','.o333332222222o.','.o323333333322o.','.o333333333332o.','.o322322322322o.','.o322232232322o.','.o322223222322o.',
           '.o322232232322o.','.o322322322322o.','.o322222222322o.','.o323333333322o.','.o322222222222o.','.o232323232322o.','..oooooooooooo..','................'],
 'card':['...oooooooooo...','..o3333333333o..','.o333333333222o.','.o323333333322o.','.o333333333332o.','.o332222222322o.','.o332222222322o.','.o332222222322o.',
         '.o332222222322o.','.o332222222322o.','.o332222222322o.','.o333333333322o.','.o322222222222o.','.o232323232322o.','..oooooooooooo..','................']}
GHOST_ALPHA={'o':110,'1':18,'2':30,'3':46}
def ghost(path,kind):
    im=Image.new('RGBA',(16,16),CLEAR)
    for y,row in enumerate(GHOSTS[kind]):
        for x,ch in enumerate(row):
            if ch in GHOST_ALPHA: im.putpixel((x,y),(255,255,255,GHOST_ALPHA[ch]))
    os.makedirs(os.path.dirname(path),exist_ok=True); im.save(path)

# ---------------- Lithography Press (176x166) ----------------
# Arcforge energy gauge on the left like the controller's; the press itself in an inset beside it: photomask, wafer and
# additive -> progress arrow -> output well. Status (Working / Idle / No power) sits in the title strip.
PRESS={'energy':(8,18),'panel':(24,18,144,52),'inputs':[(38,35),(56,35),(74,35)],'arrow':(98,35),'output':(128,31)}
def press_gui(out):
    W,H=176,166; im,p=new(W,H); energy_track(p,*PRESS['energy']); inset(p,*PRESS['panel'])
    for xy in PRESS['inputs']: slot(p,*xy)
    arrow(p,*PRESS['arrow'],col=C('#1F1F1F')); slot(p,*PRESS['output'],26,26)
    inventory(p,7,83)
    im.save(out+'gui/lithography_press.png')
    S=out+'gui/sprites/lithography_press/'; os.makedirs(S,exist_ok=True)
    arr=Image.new('RGBA',(24,17),CLEAR); q=arr.load()
    for y in range(17):
        for x in range(24):
            if (x<15 and 6<=y<=10) or (x>=15 and abs(y-8)<=8-(x-15)):
                q[x,y]=UV[3] if (y in (6,) or (x>=15 and y==8-(8-(x-15)))) else UV[2] if y<9 else UV[1]
    arr.save(S+'progress.png')
    for k in ('wafer','photomask','additive'): ghost(S+f'ghost_{k}.png',k)

# ---------------- Drive Bay (176x208) ----------------
# The ten drive bays in one inset, two columns of five like the block's front, each with its fill-bar track; the
# status (Online / Offline) sits in the title strip; empty bays show a ghost Storage Drive.
DRIVE={'panel':(8,17,160,96),'columns':(56,96),'top':20,'row':18,'inv':(7,125),'h':208}
def drive_bay_gui(out):
    W,H=176,DRIVE['h']; im,p=new(W,H); inset(p,*DRIVE['panel'])
    for fx in DRIVE['columns']:
        for r in range(5):
            fy=DRIVE['top']+r*DRIVE['row']; slot(p,fx,fy); bar_track(p,fx+19,fy,6,18)
    vsep(p,88,DRIVE['top'],DRIVE['top']+90)
    inventory(p,*DRIVE['inv']); im.save(out+'gui/drive_bay.png')
    ghost(out+'gui/sprites/drive_bay/ghost_drive.png','drive')

# ---------------- terminal kit (195 wide) ----------------
# The item grid is an inset well of slots (x 7..170), the scroll track the controller's (x 175..182), the search field
# right-aligned with it (x 119..182) so the title has the strip up to x 115.
TW=195
TERM={'top_h':19,'row_h':18,'bottom_h':99,'grid_x':9,'scroll_x':175,'search':(119,4,64,12)}
def well_rows(p,y0,h):
    """The grid well's side rims and the scroll track's sides for rows y0..y0+h-1."""
    for y in range(y0,y0+h):
        p[TERM['grid_x']-2,y]=SLOT[0]; p[TERM['grid_x']+161,y]=SLOT[3]
        sx=TERM['scroll_x']; p[sx,y]=SLOT[0]
        for x in range(sx+1,sx+7): p[x,y]=WELL
        p[sx+7,y]=SLOT[3]
def well_close(p,y=0):
    """Closes the grid well and the scroll track (their light bottom rims) at the top row of the next piece."""
    for x in range(TERM['grid_x']-1,TERM['grid_x']+162): p[x,y]=SLOT[3]
    for x in range(TERM['scroll_x']+1,TERM['scroll_x']+8): p[x,y]=SLOT[3]
def term_kit(out):
    S=out+'gui/sprites/terminal/'; os.makedirs(S,exist_ok=True); G=out+'gui/terminal/'; os.makedirs(G,exist_ok=True)
    # top: title strip + search field + the top rims of the grid well and the scroll track
    top=Image.new('RGBA',(256,32),CLEAR); p=top.load(); panel(p,0,0,TW,TERM['top_h'],'tlr'); header(p,TW)
    p[0,0]=CLEAR; p[TW-1,0]=CLEAR
    for x in range(TERM['grid_x']-2,TERM['grid_x']+161): p[x,18]=SLOT[0]
    for x in range(TERM['scroll_x'],TERM['scroll_x']+7): p[x,18]=SLOT[0]
    top.save(G+'top.png')
    # row: 9 slots + scroll track segment (repeat N times)
    row=Image.new('RGBA',(256,32),CLEAR); p=row.load(); panel(p,0,0,TW,TERM['row_h'],'lr')
    for c in range(9): slot(p,TERM['grid_x']+c*18-1,0)
    well_rows(p,0,18); row.save(G+'row.png')
    # bottom: close the grid well, inventory label space, player inventory, bottom bevel
    bot=Image.new('RGBA',(256,128),CLEAR); p=bot.load(); panel(p,0,0,TW,TERM['bottom_h'],'blr')
    well_close(p); inventory(p,TERM['grid_x']-1,16)
    p[0,TERM['bottom_h']-1]=CLEAR; p[TW-1,TERM['bottom_h']-1]=CLEAR
    bot.save(G+'bottom.png')
    # sprites
    def spr(name,w,h,fn):
        im=Image.new('RGBA',(w,h),CLEAR); q=im.load()
        for y in range(h):
            for x in range(w):
                c=fn(x,y)
                if c: q[x,y]=c
        im.save(S+name+'.png')
    sw=TERM['search'][2]
    def field_fn(border,w=sw):
        return lambda x,y: SLOT[0] if (x==0 or y==0) else SLOT[3] if (x==w-1 or y==11) else (border if (x==1 or y==1 or x==w-2 or y==10) else C('#1C1C1C'))
    spr('search_field',sw,12,field_fn(C('#232323'))); spr('search_field_focused',sw,12,field_fn(C('#00A06B')))
    # kit button: the panel's own bevel at button size (outline, lit top/left, dark bottom/right)
    def btn(face,lit,dark):
        return lambda x,y: OUT if (x in (0,17) or y in (0,17)) else lit if (x==1 or y==1) and not (x==16 or y==16) else dark if (x==16 or y==16) else face
    spr('button',18,18,btn(C('#5A5A5A'),C('#8A8A8A'),C('#333333')))
    spr('button_hover',18,18,lambda x,y: OUT if (x in (0,17) or y in (0,17)) else C('#00A06B') if (x in (1,16) or y in (1,16)) else C('#626262'))
    spr('button_pressed',18,18,btn(C('#444444'),C('#2A2A2A'),C('#6E6E6E')))
    # the toolbar tab behind the side buttons: a panel piece (outline + bevel on top, left and bottom) that butts
    # against the window's left edge; the screen draws its top and its bottom 3 rows at the toolbar's height
    tab=Image.new('RGBA',(24,128),CLEAR); q=tab.load(); panel(q,0,0,24,128,'tbl'); q[0,0]=CLEAR; q[0,127]=CLEAR
    tab.save(S+'toolbar.png')
    ic=C('#E6E6E6'); mu=C('#9A9A9A'); ac=C('#00D992')
    icons={'sort_name':['................','................','..###...###.....','.#...#..#..#....','.#####..###.....','.#...#..#..#....','.#...#..###.....','................',
                        '...........#....','...........#....','.........#####..','..........###...','...........#....','................','................','................'],
           'sort_count':['................','................','..##..###..###..','...#....#....#..','...#..###...##..','...#..#......#..','..###.###..###..','................',
                         '.###############','................','.##########.....','................','.#####..........','................','................','................'],
           'sort_mod':['................','..#######.......','..#.....#.......','..#.###.#.......','..#.#...#.......','..#.###.#.......','..#.....#.......','..#######.......',
                       '................','.......#######..','.......#.....#..','.......#.#.#.#..','.......#.###.#..','.......#.....#..','.......#######..','................'],
           'dir_asc':['................','.......##.......','......####......','.....######.....','....###..###....','...###....###...','.......##.......','.......##.......',
                      '.......##.......','.......##.......','.......##.......','.......##.......','.......##.......','................','................','................'],
           'dir_desc':['................','.......##.......','.......##.......','.......##.......','.......##.......','.......##.......','.......##.......','.......##.......',
                       '...###....###...','....###..###....','.....######.....','......####......','.......##.......','................','................','................']}
    for n,rows in icons.items():
        spr('icon_'+n,16,16,lambda x,y,rows=rows,n=n: (ac if n.startswith('dir') and y<=5 and n=='dir_asc' else ic) if rows[y][x]=='#' else None)
    # search mode: standard (a magnifier) or synced with JEI's search bar (the magnifier and two arrows, in accent)
    search_rows={'search_standard':['................','....####........','...#....#.......','..#......#......','..#......#......','..#......#......',
                                    '..#......#......','...#....#.......','....####.#......','..........#.....','...........#....','............#...',
                                    '................','................','................','................'],
                 'search_jei':['................','...####.........','..#....#........','.#......#.......','.#......#.......','.#......#.......',
                               '..#....#........','...####.#.......','.........#......','................','.....#..........','....##########..',
                               '.....#..........','..........#.....','..##########....','..........#.....']}
    for n,rows in search_rows.items():
        spr('icon_'+n,16,16,lambda x,y,rows=rows,n=n: (ac if n=='search_jei' and y>=10 else ic) if rows[y][x]=='#' else None)
    # grid height: a window outline filled to the chosen height (fill-screen in accent with end stops)
    for n,top_y in (('small',10),('medium',7),('tall',4),('fill',3)):
        def h_icon(x,y,top_y=top_y,n=n):
            if not (2<=x<=13 and 1<=y<=14): return None
            if x in (2,13) or y in (1,14): return mu
            if 4<=x<=11 and top_y<=y<=12: return ac if n=='fill' else ic
            return None
        spr('icon_height_'+n,16,16,h_icon)
    spr('slot_highlight',16,16,lambda x,y:(0,217,146,56))
def compose_terminal(out,rows=6):
    """Reference layout (what the screen draws at 6 rows): top + rows + bottom."""
    G=out+'gui/terminal/'; top=Image.open(G+'top.png'); row=Image.open(G+'row.png'); bot=Image.open(G+'bottom.png')
    H=TERM['top_h']+rows*TERM['row_h']+TERM['bottom_h']; im=Image.new('RGBA',(TW,H),CLEAR)
    im.alpha_composite(top.crop((0,0,TW,TERM['top_h'])),(0,0))
    for r in range(rows): im.alpha_composite(row.crop((0,0,TW,TERM['row_h'])),(0,TERM['top_h']+r*TERM['row_h']))
    im.alpha_composite(bot.crop((0,0,TW,TERM['bottom_h'])),(0,TERM['top_h']+rows*TERM['row_h']))
    return im
def screens(A):
    os.makedirs(A+'screens/terminal',exist_ok=True)
    json.dump({'includes':['common/palette.json'],'background':{'texture':'gui/lithography_press.png','width':176,'height':166},
      'sprites':{'energy_bar':{'sprite':'controller/energy_bar','width':10,'height':50,'fill':'bottom_up'},
                 'progress':{'sprite':'lithography_press/progress','width':24,'height':17,'fill':'left_to_right'},
                 'ghost_wafer':{'sprite':'lithography_press/ghost_wafer','width':16,'height':16},
                 'ghost_photomask':{'sprite':'lithography_press/ghost_photomask','width':16,'height':16},
                 'ghost_additive':{'sprite':'lithography_press/ghost_additive','width':16,'height':16}},
      'slots':{'photomask':{'left':39,'top':36,'ghost':'ghost_photomask'},'wafer':{'left':57,'top':36,'ghost':'ghost_wafer'},
               'additive':{'left':75,'top':36,'ghost':'ghost_additive'},'output':{'left':133,'top':36,'output':True}},
      'widgets':{'energy_bar':{'left':9,'top':19,'sprite':'energy_bar','tooltip':'energy'},'progress':{'left':98,'top':35,'sprite':'progress'},
                 'status':{'right':168,'top':5,'led':[-9,1],'states':{'working':'ACCENT','idle':'TEXT_MUTED','no_power':'WARNING'}}},
      'player_inventory':{'left':8,'top':84},
      'text':{'title':{'key':'block.encodedlogistics.lithography_press','left':8,'top':5,'color':'TEXT'},
              'inventory':{'key':'container.inventory','left':8,'top':73,'color':'TEXT_MUTED'}}},
      open(A+'screens/lithography_press.json','w',newline='\n'),indent=1)
    json.dump({'includes':['common/palette.json'],'background':{'texture':'gui/drive_bay.png','width':176,'height':DRIVE['h']},
      'sprites':{'ghost_drive':{'sprite':'drive_bay/ghost_drive','width':16,'height':16},
                 'fill':{'sprites':['drive_bay/fill_green','drive_bay/fill_yellow','drive_bay/fill_orange','drive_bay/fill_red'],'width':4,'height':16,'fill':'bottom_up'}},
      'slots':{'drives':{'left':[57,97],'top':21,'rows':5,'row_height':18,'ghost':'ghost_drive','fill_bar':{'left':19,'top':0}}},
      'widgets':{'status':{'right':168,'top':5,'led':[-9,1],'states':{'online':'ACCENT','offline':'ERROR'}}},
      'player_inventory':{'left':8,'top':126},
      'text':{'title':{'key':'block.encodedlogistics.drive_bay','left':8,'top':5,'color':'TEXT'},
              'inventory':{'key':'container.inventory','left':8,'top':115,'color':'TEXT_MUTED'}}},
      open(A+'screens/drive_bay.json','w',newline='\n'),indent=1)
    json.dump({'$comment':'Modular terminal layout. Height = top + rows*row + bottom; rows follow the height setting (small / medium / tall / fill the window) between min and max.',
      'width':195,'pieces':{'top':{'texture':'gui/terminal/top.png','height':19},'row':{'texture':'gui/terminal/row.png','height':18},
                            'bottom':{'texture':'gui/terminal/bottom.png','height':99}},
      'rows':{'min':3,'max':12,'default':6,'fit_to_window':True,'heights':{'small':3,'medium':6,'tall':9}},
      'grid':{'left':9,'top_in_row':1,'columns':9,'cell':18,'count_text':{'scale':0.5,'align':'bottom_right','color':'TEXT'}},
      'scrollbar':{'left':176,'top':19,'width':6,'thumb':'controller/scroll_thumb','thumb_hover':'controller/scroll_thumb_hover',
                   'thumb_disabled':'controller/scroll_thumb_disabled','thumb_size':[6,15]},
      'search':{'left':119,'top':4,'width':64,'height':12,'sprite':'terminal/search_field','sprite_focused':'terminal/search_field_focused',
                'text_left':4,'text_top':2,'max_length':64},
      'toolbar':{'side':'left','left':-21,'top':19,'spacing':20,'tab':'terminal/toolbar','tab_size':[24,128],
                 'button':'terminal/button','button_hover':'terminal/button_hover','button_pressed':'terminal/button_pressed',
                 'buttons':[{'id':'sort_mode','icons':['terminal/icon_sort_name','terminal/icon_sort_count','terminal/icon_sort_mod']},
                            {'id':'sort_direction','icons':['terminal/icon_dir_asc','terminal/icon_dir_desc']},
                            {'id':'craftables','icons':['terminal/icon_craftable_on','terminal/icon_craftable_off']},
                            {'id':'height','icons':['terminal/icon_height_small','terminal/icon_height_medium','terminal/icon_height_tall','terminal/icon_height_fill']},
                            {'id':'search_mode','icons':['terminal/icon_search_standard','terminal/icon_search_jei']}]},
      'player_inventory':{'left':9,'top_in_bottom':17,'label_top_in_bottom':6},
      'slot_highlight':'terminal/slot_highlight',
      'text':{'title':{'left':8,'top':5,'color':'TEXT','max_right':115},'inventory':{'key':'container.inventory','left':9,'top_in_bottom':6,'color':'TEXT_MUTED'}}},
      open(A+'screens/terminal/base_terminal.json','w',newline='\n'),indent=1)
    json.dump({'includes':['terminal/base_terminal.json'],'text':{'title':{'key':'gui.encodedlogistics.access_terminal'}},
               'features':{'crafting_grid':False}},open(A+'screens/access_terminal.json','w',newline='\n'),indent=1)
if __name__=='__main__':
    import sys
    A=sys.argv[1]; T=A+'textures/'
    os.makedirs(T+'gui',exist_ok=True); press_gui(T); drive_bay_gui(T); term_kit(T); compose_terminal(T).save(T+'gui/terminal/reference_6_rows.png'); screens(A)
    print('gui ok')
