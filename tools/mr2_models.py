# Models (facing north: front toward -z; flush to the back of the footprint at z = 16; centred across its width),
# collision/selection boxes derived from the same boxes, blockstates and export.
import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import mr2_tex as T, mid_tex as M
from el_style import put
A_='src/main/resources/assets/encodedlogistics/'; D_='src/main/resources/data/encodedlogistics/'; RL=lambda p:'encodedlogistics:'+p
GLOW={'neoforge_data':{'block_light':15,'sky_light':15},'shade_direction_override':'up'}
def save(im,p): os.makedirs(os.path.dirname(A_+p),exist_ok=True); im.save(A_+p)
def jd(o,p,root=A_): os.makedirs(os.path.dirname(root+p),exist_ok=True); json.dump(o,open(root+p,'w'),indent=1)
def mc(p,ft,w=None,h=None):
    a={'frametime':ft}
    if w: a['width']=w; a['height']=h
    open(A_+p+'.mcmeta','w').write(json.dumps({'animation':a},indent=2)+'\n')
def el(n,a,b,faces,solid=True,**k):
    d={'name':n,'from':[round(v,4) for v in a],'to':[round(v,4) for v in b],'faces':faces}; d.update(k); d['_solid']=solid; return d
def F(tex,uv,**k): d={'texture':tex,'uv':uv}; d.update(k); return d
def box(n,a,b,tex,A,m,solid=True,**k):
    """m: {dir: atlas name} - any dir not given uses m['*'] (or is omitted when '*' missing)."""
    fs={}
    for d in ('north','south','east','west','up','down'):
        nm=m.get(d,m.get('*'))
        if nm: fs[d]=F(tex,A.uv(nm,flip=d in ('south','west')))
    return el(n,a,b,fs,solid,**k)
def model(tx,E,rt=None):
    m={'parent':'minecraft:block/block','textures':{**{k:RL(v) for k,v in tx.items()},'particle':RL(list(tx.values())[0])},
       'elements':[{k:v for k,v in e.items() if k!='_solid'} for e in E]}
    if rt: m['render_type']=rt
    return m
def shapes(E,blocks):
    """Per footprint block (dx, dy, dz in blocks from the master), the solid element boxes clipped to that block, in the
       block's own local px (0..16). Rotated plates count as their unrotated box."""
    out={}
    for (bx,by,bz) in blocks:
        lo=(bx*16,by*16,bz*16); hi=(lo[0]+16,lo[1]+16,lo[2]+16); bl=[]
        for e in E:
            if not e['_solid']: continue
            a,b=e['from'],e['to']
            c0=[max(a[i],lo[i]) for i in range(3)]; c1=[min(b[i],hi[i]) for i in range(3)]
            if all(c1[i]-c0[i]>0.2 for i in range(3)): bl.append([round(c0[i]-lo[i],3) for i in range(3)]+[round(c1[i]-lo[i],3) for i in range(3)])
        out[f'{bx},{by},{bz}']=bl
    return out
# ---------------- Midrange System ----------------
def midrange(exp):
    """18 x 16 x 12 in a 2 x 1 x 1 footprint (master x 0-16, dummy x 16-32). exp: none (centred x 7-25), pos (flush
       to +x: 14-32, cabinet beside at +x), neg (flush to -x: 0-18, cabinet at -x)."""
    A=T.ms_atlas(); P=T.ms_panels(); x0={'none':7,'pos':14,'neg':0}[exp]; X=lambda v:x0+v
    t,p='#cab','#pan'
    E=[box('body',(X(0),1,4),(X(18),13,16),t,A,{'north':'front','south':'rear','east':'side','west':'side','up':'top'}),
       box('plinth',(X(0.5),0,4.5),(X(17.5),1,15.5),t,A,{'north':'plinth','south':'plinth','east':'plinth_side','west':'plinth_side'}),
       box('house_l',(X(9),13,6.4),(X(18),16,16),t,A,{'up':'house_l_top','east':'house_side','west':'house_side','south':'house_back','north':'house_back'}),
       box('fill_l',(X(9),13,5),(X(18),13.6,6.4),t,A,{'up':'deck','east':'house_side','west':'house_side'}),
       box('deck',(X(7),13,5),(X(9),13.5,16),t,A,{'up':'deck','north':'plinth'}),
       box('house_r',(X(0),13,7.4),(X(7),16,16),t,A,{'up':'house_r_top','east':'house_side','west':'house_side','south':'house_back','north':'house_back'}),
       box('fill_r',(X(0),13,6),(X(7),13.6,7.4),t,A,{'up':'deck','east':'house_side','west':'house_side'})]
    disk='disk2' if exp!='none' else 'disk1'
    E.append(el('disk_plate',(X(9),13,5.0),(X(18),16.2,5.4),{'north':F(p,P.uv(disk))},False,rotation={'angle':22.5,'axis':'x','origin':[X(13.5),13,5.2]}))
    E.append(el('oper_plate',(X(0),13,6.0),(X(7),16.2,6.4),{'north':F(p,P.uv('oper'))},False,rotation={'angle':22.5,'axis':'x','origin':[X(3.5),13,6.2]}))
    E.append(box('epo',(X(18),9.5,5.5),(X(18.35),10.5,6.5),t,A,{'*':'epo'},False))                          # emergency power off (left side panel)
    return A,P,E
def midrange_glow(exp,state):
    x0={'none':7,'pos':14,'neg':0}[exp]
    return [el('oper_glow',(x0+0,13,5.98),(x0+7,16.2,5.98),{'north':F('#glow',[0,0,16,16])},False,
               rotation={'angle':22.5,'axis':'x','origin':[x0+3.5,13,6.2]},**GLOW)]
# ---------------- Expansion Cabinet ----------------
def expansion(att):
    """10 x 16 x 12 in one block; att = the side its Midrange System is on: pos (flush to +x: 6-16), neg (0-10), none
       (centred 3-13)."""
    A=T.ec_atlas(); x0={'none':3,'pos':6,'neg':0}[att]; X=lambda v:x0+v; t='#cab'
    E=[box('body',(X(0),1,4),(X(10),13,16),t,A,{'north':'front','south':'rear','east':'side','west':'side','up':'top'}),
       box('plinth',(X(0.5),0,4.5),(X(9.5),1,15.5),t,A,{'north':'plinth','south':'plinth','east':'plinth_side','west':'plinth_side'}),
       box('cap',(X(0),13,5),(X(10),16,16),t,A,{'north':'cap_front','up':'top','east':'cap_side','west':'cap_side','south':'cap_front'})]
    lamp=el('lamp',(X(2),9.5,3.98),(X(3),10.5,3.98),{'north':F('#lamp',[0,0,16,16])},False,**GLOW)
    return A,E,lamp
# ---------------- Integrated Midrange System ----------------
def integrated():
    """28 x 20 x 12 in a 2 x 2 x 1 footprint (x 2-30). Two front panels with vent bands; diskette magazine unit on the
       top left; console on the right: hood with the slanted fascia (screen + lamps), keyboard tray in front."""
    A=T.ims_atlas(); P=T.ims_panels(); t,p='#cab','#pan'
    E=[box('body_l',(2,1,4),(16,13,16),t,A,{'north':'front_l','west':'side','south':'rear','up':'deck'}),
       box('body_r',(16,1,4),(30,13,16),t,A,{'north':'front_r','east':'side','south':'rear','up':'deck'}),
       box('plinth',(2.5,0,4.5),(29.5,1,15.5),t,A,{'north':'plinth','south':'plinth','east':'plinth_side','west':'plinth_side'}),
       box('magazine_unit',(19,13,6),(29,16,14),t,A,{'up':'mag_top','east':'mag_side','west':'mag_side','south':'mag_back','north':'mag_back'}),
       box('hood',(2,13,10.6),(16,20,16),t,A,{'up':'hood_top','east':'hood_side','west':'hood_side','south':'hood_back'}),
       box('tray',(3,13,4.5),(15,13.5,9),t,A,{'up':'tray','north':'plinth'})]
    E.append(el('mag_plate',(19,13,5.98),(29,15.5,5.98),{'north':F(p,P.uv('magazine'))},False))
    E.append(el('fascia',(2.2,13.5,8.8),(15.8,19.8,9.2),{'north':F(p,P.uv('fascia'))},False,rotation={'angle':22.5,'axis':'x','origin':[9,13.5,9]}))
    E.append(el('keyboard',(4,13.5,5),(14,13.9,8.5),{'up':F('#kb',[0,0,16,16]),'north':F('#kb',[0,14,16,16])},False))
    return A,P,E
def integrated_glow(state):
    return [el('fascia_glow',(2.2,13.5,8.78),(15.8,19.8,8.78),{'north':F('#glow',[0,0,16,16])},False,
               rotation={'angle':22.5,'axis':'x','origin':[9,13.5,9]},**GLOW)]
def integrated_screen(st):
    """The screen sits at fascia texels (4..24, 4..20) of 56 x 24 = 1..6 px from the fascia's texture-left edge, which on a
       north face is its HIGH-x end (15.8): x 9.8..14.8, y 1..5 px up the fascia."""
    return [el('screen',(9.8,13.5+1.0,8.76),(14.8,13.5+5.0,8.76),{'north':F('#screen',[0,0,16,16])},False,
               rotation={'angle':22.5,'axis':'x','origin':[9,13.5,9]},**GLOW)]
# ---------------- Peripherals ----------------
def keypunch():
    """16 x 18 x 12 (1 x 2 x 1): desk 12 high (0.75 m) with a pedestal on the right, kneehole left; raised card-path unit
       at the back up to 17; hopper (top right, tilted) and stacker (top left); keyboard on the desk."""
    t='#kp'; A=T.Atlas(64,64)
    for n,w,h,base in (('ped_front',8,11,3),('ped_side',12,11,3),('desk_top',16,12,5),('desk_edge',16,1,5),('desk_side',12,1,5),('modesty',8,9,2),
                       ('unit_front',15,5,4),('unit_side',7,5,3),('unit_top',15,7,5),('hop',4,5,3),('hop_top',4,4,5)):
        A.add(n,T.edge(T.face(w,h,M.CONSOLE if n.startswith(('ped','modesty','unit','hop')) and n!='hop_top' else M.BEIGE,base,len(n))))
    uf=A.r['unit_front']; im=A.im
    for x in range(uf[0]+1,uf[0]+14): put(im,x,uf[1]+2,T.c(T.DARK,1)); put(im,x,uf[1]+3,T.c(M.CONSOLE,5))    # card bed
    for x in range(uf[0]+6,uf[0]+9): put(im,x,uf[1]+1,T.c(T.DARK,0))                                        # punch station window
    ht=A.r['hop_top']
    for y in range(ht[1],ht[1]+4):
        for x in range(ht[0],ht[0]+4): put(im,x,y,(0xE8,0xDF,0xC4) if y%2==0 else (0xD8,0xCE,0xB0))
    E=[box('pedestal',(0,0,4.5),(8,11,16),t,A,{'north':'ped_front','east':'ped_side','west':'ped_side','south':'ped_front'}),
       box('modesty',(8,2,14.5),(15.5,11,15.5),t,A,{'north':'modesty','south':'modesty'}),
       box('leg',(14.5,0,4.5),(15.5,11,5.5),t,A,{'*':'desk_edge'}),
       box('desk',(0,11,4),(16,12,16),t,A,{'up':'desk_top','north':'desk_edge','south':'desk_edge','east':'desk_side','west':'desk_side'}),
       box('unit',(0.5,12,9),(15.5,17,16),t,A,{'north':'unit_front','up':'unit_top','east':'unit_side','west':'unit_side','south':'unit_front'}),
       box('hopper',(1,17,10),(5,18.5,14),t,A,{'up':'hop_top','north':'hop','south':'hop','east':'hop','west':'hop'},rotation={'angle':-22.5,'axis':'x','origin':[3,17,14]}),
       box('stacker',(10.5,17,10),(14.5,18,14),t,A,{'up':'hop_top','north':'hop','south':'hop','east':'hop','west':'hop'}),
       el('keyboard',(5,12,5),(13,12.6,8.5),{'up':F('#kb',[0,0,16,16]),'north':F('#kb',[0,14,16,16])},False)]
    feed=el('card',(0.5,12,8.98),(15.5,17,8.98),{'north':F('#feed',[0.5,0,15.5,5])},False)
    return A,E,feed
def card_reader():
    """10 x 16 x 10 (one block, x 3-13, z 6-16): body 12 high, top unit with the input hopper (right) and the output
       stacker (left) either side of the feed track."""
    t='#cr'; A=T.Atlas(64,64)
    f=T.edge(T.face(10,12,M.BEIGE,4,61)); T.vents(f,2,9,6,2,M.BEIGE)
    for y in range(3,6):
        for x in range(2,8): put(f,x,y,T.c(T.DARK,1) if y in (3,5) else T.c(T.DARK,2))
    A.add('front',f); A.add('side',T.edge(T.face(10,12,M.BEIGE,3,62))); A.add('top',T.edge(T.face(10,10,M.BEIGE,5,63)))
    A.add('box',T.edge(T.face(4,4,M.BEIGE,4,64))); A.add('deck',T.face(4,8,M.BEIGE,3,65))
    dk=T.face(4,8,M.BEIGE,3,66)
    for y in range(8):
        for x in range(4): put(dk,x,y,(0xE8,0xDF,0xC4) if y%2==0 else (0xD8,0xCE,0xB0))
    A.add('cards',dk); A.add('track',T.face(2,8,T.DARK,1,67,bands=False))
    E=[box('body',(3,0,6),(13,12,16),t,A,{'north':'front','south':'side','east':'side','west':'side','up':'top'}),
       box('hopper',(3,12,7),(7,16,15),t,A,{'up':'cards','north':'box','south':'box','east':'box','west':'box'}),
       box('stacker',(9,12,7),(13,14,15),t,A,{'up':'cards','north':'box','south':'box','east':'box','west':'box'}),
       box('track',(7,12,10),(9,12.3,12),t,A,{'up':'track'},False)]
    feed=el('feed',(7,12.32,7),(9,12.32,15),{'up':F('#feed',[7,7,9,9])},False)
    lamps=el('lamps',(3,0,5.98),(13,12,5.98),{'north':F('#lamps',[3,4,13,16])},False,**GLOW)
    return A,E,feed,lamps
def line_printer():
    """20 x 17 x 12 (2 x 2 x 1, x 6-26): stand cabinet 12 high, printer unit 12-15 with the control strip, the smoked
       lid raised at the rear hinge, fan-fold paper rising to 21, tractor ends, the wire basket at the back."""
    t='#lp'; A=T.Atlas(64,64)
    f=T.edge(T.face(20,12,M.BEIGE,4,71)); T.vents(f,3,8,14,2,M.BEIGE); A.add('front',f)
    A.add('side',T.edge(T.face(12,12,M.BEIGE,3,72))); A.add('rear',T.edge(T.face(20,12,M.BEIGE,3,73)))
    A.add('unit_front',T.edge(T.face(20,3,M.BEIGE,5,74))); A.add('unit_side',T.edge(T.face(12,3,M.BEIGE,4,75)))
    A.add('unit_top',T.edge(T.face(20,12,M.BEIGE,5,76,bands=False))); A.add('trac',T.edge(T.face(3,3,M.BEIGE,4,77)))
    A.add('wire',Image.new('RGBA',(2,2),(170,176,184,255)))
    E=[box('cabinet',(6,0,4),(26,12,16),t,A,{'north':'front','south':'rear','east':'side','west':'side'}),
       box('unit',(6,12,4),(26,15,16),t,A,{'north':'unit_front','south':'unit_front','east':'unit_side','west':'unit_side','up':'unit_top'}),
       box('tractor_l',(8,15,7),(11,17,11),t,A,{'*':'trac'}),box('tractor_r',(21,15,7),(24,17,11),t,A,{'*':'trac'}),
       el('panel',(7,12.5,3.98),(25,14.5,3.98),{'north':F('#panel',[0,0,16,16])},False),
       el('lid',(7,15,5),(25,15.4,10),{'up':F('#lid',[0,0,16,16]),'down':F('#lid',[0,0,16,16]),'north':F('#lid',[0,0,16,1])},False,
          rotation={'angle':45,'axis':'x','origin':[16,15,10]}),
       el('paper',(10.5,15,9),(21.5,21,9),{'north':F('#paper',[0,0,16,16]),'south':F('#paper',[0,0,16,16])},False),
       el('paper_fold',(10.5,21,9),(21.5,21,14),{'up':F('#paper',[0,0,16,16]),'down':F('#paper',[0,0,16,16])},False,rotation={'angle':-22.5,'axis':'x','origin':[16,21,9]})]
    for i,z in enumerate((11,13,15)):
        E.append(box(f'wire{i}',(9,15,z),(23,15+2.5+i*1.5,z+0.25),t,A,{'*':'wire'},False))
    glow=el('panel_glow',(7,12.5,3.96),(25,14.5,3.96),{'north':F('#glow',[0,0,16,16])},False,**GLOW)
    return A,E,glow
