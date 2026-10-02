# Phase 1 GUIs: Lithography Press screen and the modular terminal kit (Access Terminal now, Fabrication/Handheld later).
# Same look as the controller GUI: #4B4B4B panel, 2-step bevels, inset slots, Arcforge energy bar.
import os, json
from PIL import Image
def C(s): return tuple(int(s[i:i+2],16) for i in (1,3,5))+(255,)
FILL,OUT=C('#4B4B4B'),C('#141414'); L1,L2,D1,D2=C('#7E7E7E'),C('#626262'),C('#262626'),C('#363636')
SLOT=(C('#161616'),C('#1F1F1F'),C('#333333'),C('#707070'))   # top/left, inner shadow, fill, bottom/right
UV=[C('#4B2290'),C('#7A3FE0'),C('#A877FF'),C('#D9C2FF')]
def panel(p,x0,y0,w,h,sides='tblr'):
    """Panel with 2-step bevel on the listed sides (t,b,l,r): lets pieces stack seamlessly."""
    for y in range(y0,y0+h):
        for x in range(x0,x0+w):
            lx,ly,rx,ry=x-x0,y-y0,x0+w-1-x,y0+h-1-y
            c=FILL
            for d,s,lit in ((ly,'t',True),(lx,'l',True),(ry,'b',False),(rx,'r',False)):
                if s in sides and d<3:
                    c=OUT if d==0 else ((L1 if d==1 else L2) if lit else (D1 if d==1 else D2)); break
            p[x,y]=c
def slot(p,x0,y0,w=18,h=18):
    for y in range(y0,y0+h):
        for x in range(x0,x0+w):
            p[x,y]=SLOT[0] if (x==x0 or y==y0) else SLOT[3] if (x==x0+w-1 or y==y0+h-1) else SLOT[1] if (x==x0+1 or y==y0+1) else SLOT[2]
    p[x0+w-1,y0]=FILL; p[x0,y0+h-1]=FILL
def inventory(p,x0,y0):
    for r in range(3):
        for c in range(9): slot(p,x0+c*18,y0+r*18)
    for c in range(9): slot(p,x0+c*18,y0+58)
def energy_track(p,x0,y0):
    for y in range(y0,y0+52):
        for x in range(x0,x0+12):
            p[x,y]=C('#0E0E0E') if (x==x0 or y==y0) else C('#5C5C5C') if (x==x0+11 or y==y0+51) else C('#1F1F1F')
    p[x0+11,y0]=C('#1F1F1F'); p[x0,y0+51]=C('#1F1F1F')
def corners(p,W,H):
    for x,y in ((0,0),(W-1,0),(0,H-1),(W-1,H-1)): p[x,y]=(0,0,0,0)
def header(p,W):
    for y in range(3,15):
        for x in range(3,W-3): p[x,y]=C('#525252')
    for x in range(3,W-3): p[x,15]=C('#444444')

# ---------------- Lithography Press (176x166) ----------------
PRESS={'energy':(8,18),'mask':(79,16),'wafer':(43,35),'additive':(79,58),'arrow':(70,36),'output':(108,31)}
def press_gui(out):
    W,H=176,166; im=Image.new('RGBA',(256,256),(0,0,0,0)); p=im.load()
    panel(p,0,0,W,H); corners(p,W,H); header(p,W); energy_track(p,*PRESS['energy'])
    for k in ('mask','wafer','additive'): slot(p,*PRESS[k])
    slot(p,*PRESS['output'],26,26)
    ax,ay=PRESS['arrow']                                    # empty progress arrow (24x17) baked in
    for y in range(17):
        for x in range(24):
            if (x<15 and 6<=y<=10) or (x>=15 and abs(y-8)<=8-(x-15)): p[ax+x,ay+y]=C('#2A2A2A')
    for x in range(88,92): p[x,34]=C('#3A3A3A'); p[x,57]=C('#3A3A3A')            # mask/additive feed lines
    inventory(p,7,83)
    im.save(out+'gui/lithography_press.png')
    S=out+'gui/sprites/lithography_press/'; os.makedirs(S,exist_ok=True)
    arr=Image.new('RGBA',(24,17),(0,0,0,0)); q=arr.load()
    for y in range(17):
        for x in range(24):
            if (x<15 and 6<=y<=10) or (x>=15 and abs(y-8)<=8-(x-15)):
                q[x,y]=UV[3] if (y in (6,) or (x>=15 and y==8-(8-(x-15)))) else UV[2] if y<9 else UV[1]
    arr.save(S+'progress.png')
    def ghost(name,pts):
        g=Image.new('RGBA',(16,16),(0,0,0,0)); gq=g.load()
        for x,y in pts: gq[x,y]=(255,255,255,70)
        g.save(S+f'ghost_{name}.png')
    import math
    ghost('wafer',[(x,y) for x in range(16) for y in range(16) if 5.6<=math.hypot(x-7.5,y-7.5)<=6.6])
    ghost('photomask',[(x,y) for x in range(2,14) for y in range(2,14) if x in (2,13) or y in (2,13)]+[(x,x) for x in range(4,12)])
    ghost('additive',[(x,y) for y in range(7,13) for x in range(8-(y-5),8+(y-5)) if y==12 or abs(x-7.5)>=(y-6)])

# ---------------- terminal kit (195 wide) ----------------
TW=195
TERM={'top_h':19,'row_h':18,'bottom_h':99,'grid_x':9,'scroll_x':175,'search':(104,4,68,12)}
def term_kit(out):
    S=out+'gui/sprites/terminal/'; os.makedirs(S,exist_ok=True); G=out+'gui/terminal/'; os.makedirs(G,exist_ok=True)
    # top: title area + search field + top of the grid well
    top=Image.new('RGBA',(256,32),(0,0,0,0)); p=top.load(); panel(p,0,0,TW,TERM['top_h'],'tlr'); header(p,TW)
    p[0,0]=(0,0,0,0); p[TW-1,0]=(0,0,0,0)
    for x in range(TERM['grid_x']-1,TERM['grid_x']+162+1): p[x,18]=SLOT[0]          # well lip
    for x in range(TERM['scroll_x'],TERM['scroll_x']+12): p[x,18]=SLOT[0]
    top.save(G+'top.png')
    # row: 9 slots + scroll track segment (repeat N times)
    row=Image.new('RGBA',(256,32),(0,0,0,0)); p=row.load(); panel(p,0,0,TW,TERM['row_h'],'lr')
    for c in range(9): slot(p,TERM['grid_x']+c*18-1,0)
    for y in range(18):
        for x in range(TERM['scroll_x'],TERM['scroll_x']+12):
            p[x,y]=SLOT[0] if x==TERM['scroll_x'] else SLOT[3] if x==TERM['scroll_x']+11 else C('#2A2A2A')
    row.save(G+'row.png')
    # bottom: close the grid well, inventory label space, player inventory, bottom bevel
    bot=Image.new('RGBA',(256,128),(0,0,0,0)); p=bot.load(); panel(p,0,0,TW,TERM['bottom_h'],'blr')
    for x in range(TERM['grid_x']-1,TERM['grid_x']+161): p[x,0]=SLOT[3]
    for x in range(TERM['scroll_x'],TERM['scroll_x']+12): p[x,0]=SLOT[3]
    inventory(p,TERM['grid_x']-1,16)
    p[0,TERM['bottom_h']-1]=(0,0,0,0); p[TW-1,TERM['bottom_h']-1]=(0,0,0,0)
    bot.save(G+'bottom.png')
    # sprites
    def spr(name,w,h,fn):
        im=Image.new('RGBA',(w,h),(0,0,0,0)); q=im.load()
        for y in range(h):
            for x in range(w):
                c=fn(x,y)
                if c: q[x,y]=c
        im.save(S+name+'.png')
    def field_fn(border,w=68):
        return lambda x,y: SLOT[0] if (x==0 or y==0) else SLOT[3] if (x==w-1 or y==11) else (border if (x==1 or y==1 or x==w-2 or y==10) else C('#1C1C1C'))
    spr('search_field',68,12,field_fn(C('#232323'))); spr('search_field_focused',68,12,field_fn(C('#00A06B')))
    def btn(face,lit,dark):
        return lambda x,y: OUT if (x in (0,17) or y in (0,17)) else lit if (x==1 or y==1) else dark if (x==16 or y==16) else face
    spr('button',18,18,btn(C('#5A5A5A'),C('#8A8A8A'),C('#333333')))
    spr('button_hover',18,18,lambda x,y: OUT if (x in (0,17) or y in (0,17)) else C('#00A06B') if (x in (1,16) or y in (1,16)) else C('#626262'))
    spr('button_pressed',18,18,btn(C('#444444'),C('#2A2A2A'),C('#6E6E6E')))
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
    spr('scroll_thumb',10,15,lambda x,y: OUT if False else (C('#B4B4B4') if (x==0 or y==0) else C('#5A5A5A') if (x==9 or y==14) else (C('#2E2E2E') if y in (5,7,9) and 1<x<8 else C('#8A8A8A'))))
    spr('scroll_thumb_disabled',10,15,lambda x,y: C('#626262') if (x==0 or y==0) else C('#3E3E3E') if (x==9 or y==14) else C('#555555'))
    spr('slot_highlight',16,16,lambda x,y:(0,217,146,56))
def compose_terminal(out,rows=6):
    """Reference layout (what the screen draws at 6 rows): top + rows + bottom."""
    G=out+'gui/terminal/'; top=Image.open(G+'top.png'); row=Image.open(G+'row.png'); bot=Image.open(G+'bottom.png')
    H=TERM['top_h']+rows*TERM['row_h']+TERM['bottom_h']; im=Image.new('RGBA',(TW,H),(0,0,0,0))
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
      'slots':{'photomask':{'left':80,'top':17,'ghost':'ghost_photomask'},'wafer':{'left':44,'top':36,'ghost':'ghost_wafer'},
               'additive':{'left':80,'top':59,'ghost':'ghost_additive'},'output':{'left':113,'top':36,'output':True}},
      'widgets':{'energy_bar':{'left':9,'top':19,'sprite':'energy_bar','tooltip':'energy'},'progress':{'left':70,'top':36,'sprite':'progress'}},
      'player_inventory':{'left':8,'top':84},
      'text':{'title':{'key':'block.encodedlogistics.lithography_press','left':8,'top':5,'color':'TEXT'},
              'inventory':{'key':'container.inventory','left':8,'top':72,'color':'TEXT_MUTED'}}},
      open(A+'screens/lithography_press.json','w',newline='\n'),indent=1)
    json.dump({'$comment':'Modular terminal layout. Height = top + rows*row + bottom; rows fill the window between min and max.',
      'width':195,'pieces':{'top':{'texture':'gui/terminal/top.png','height':19},'row':{'texture':'gui/terminal/row.png','height':18},
                            'bottom':{'texture':'gui/terminal/bottom.png','height':99}},
      'rows':{'min':3,'max':12,'default':6,'fit_to_window':True},
      'grid':{'left':9,'top_in_row':1,'columns':9,'cell':18,'count_text':{'scale':0.5,'align':'bottom_right','color':'TEXT'}},
      'scrollbar':{'left':176,'top':19,'width':10,'thumb':'terminal/scroll_thumb','thumb_disabled':'terminal/scroll_thumb_disabled','thumb_size':[10,15]},
      'search':{'left':104,'top':4,'width':68,'height':12,'sprite':'terminal/search_field','sprite_focused':'terminal/search_field_focused',
                'text_left':4,'text_top':2,'max_length':64},
      'toolbar':{'side':'left','left':-20,'top':6,'spacing':20,'button':'terminal/button','button_hover':'terminal/button_hover',
                 'button_pressed':'terminal/button_pressed',
                 'buttons':[{'id':'sort_mode','icons':['terminal/icon_sort_name','terminal/icon_sort_count','terminal/icon_sort_mod']},
                            {'id':'sort_direction','icons':['terminal/icon_dir_asc','terminal/icon_dir_desc']},
                            {'id':'craftables','icons':['terminal/icon_craftable_on','terminal/icon_craftable_off']}]},
      'player_inventory':{'left':9,'top_in_bottom':17,'label_top_in_bottom':6},
      'slot_highlight':'terminal/slot_highlight',
      'text':{'title':{'left':8,'top':6,'color':'TEXT'},'inventory':{'key':'container.inventory','left':9,'top_in_bottom':6,'color':'TEXT_MUTED'}}},
      open(A+'screens/terminal/base_terminal.json','w',newline='\n'),indent=1)
    json.dump({'includes':['terminal/base_terminal.json'],'text':{'title':{'key':'gui.encodedlogistics.access_terminal'}},
               'features':{'crafting_grid':False}},open(A+'screens/access_terminal.json','w',newline='\n'),indent=1)
if __name__=='__main__':
    import sys
    A=sys.argv[1]; T=A+'textures/'
    os.makedirs(T+'gui',exist_ok=True); press_gui(T); term_kit(T); compose_terminal(T).save(T+'gui/terminal/reference_6_rows.png'); screens(A)
    print('gui ok')
