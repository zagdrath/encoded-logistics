# Server Rack models. MASTER = the middle block of the front column; model space x 0..16, y -16..32, z 0..32
# (front face at z = 0, facing NORTH), inside Minecraft's -16..32 element limit, so the whole frame is ONE static block model.
#   y -16..-15 casters / feet (rack sits 1 px off the ground) | -15..-13 plinth | -13..29 42U (1 px per U) | 29..32 roof
import json
U0=-13            # bottom of U1
def U_y(u): return U0+(u-1)
def uvf(px,tw,th):
    """texel rect (x0,y0,x1,y1) on a tw x th texture -> 0..16 UV."""
    x0,y0,x1,y1=px; return [x0*16/tw,y0*16/th,x1*16/tw,y1*16/th]
def up16(n): return -(-n//16)*16
def pad16(im):
    """A texture padded (transparent, right and bottom) to multiples of 16, as rack_export saves the odd-sized ones."""
    from PIL import Image
    out=Image.new('RGBA',(up16(im.width),up16(im.height)),(0,0,0,0)); out.alpha_composite(im.convert('RGBA'),(0,0)); return out
def face(t,uv,**k): d={'texture':t,'uv':[round(v,4) for v in uv]}; d.update(k); return d
def el(name,frm,to,faces,**k): d={'name':name,'from':list(frm),'to':list(to),'faces':faces}; d.update(k); return d
def all_faces(t,uv): return {f:face(t,uv) for f in ('north','south','east','west','up','down')}
# ---------------- frame (static block model of the master) ----------------
def frame_model():
    F='#frame'; I='#interior'; P='#plinth'
    E=[]
    # vertical corner posts (rounded edges via the frame texture's stepped crest)
    for (x0,z0,n) in ((0,0,'post_fl'),(14.5,0,'post_fr'),(0,30.5,'post_rl'),(14.5,30.5,'post_rr')):
        E.append(el(n,(x0,-15,z0),(x0+1.5,29,z0+1.5),all_faces(F,[0,0,16,16])))
    # side panels: two stacked per side with a 0.5 px seam between them (inner faces show the dark interior)
    for side,x0,x1 in (('left',0,0.5),('right',15.5,16)):
        for part,y0,y1 in (('lower',-14,7.75),('upper',8.25,29)):
            out='west' if side=='left' else 'east'; inn='east' if side=='left' else 'west'
            tex='#side_upper' if part=='upper' else '#side_lower'
            E.append(el(f'side_{side}_{part}',(x0,y0,1.5),(x1,y1,30.5),{out:face(tex,uvf((0,0,29,y1-y0),32,32)),inn:face(I,[0,0,16,16]),
                     'up':face(F,[0,0,1,16]),'down':face(F,[0,0,1,16])}))
        E.append(el(f'seam_{side}',((0.1 if side=='left' else 15.6),7.75,1.5),((0.4 if side=='left' else 15.9),8.25,30.5),all_faces(I,[0,0,16,16])))
    # roof (vented, cable-entry slots) and plinth
    RE='#roof_edge'
    E.append(el('roof',(0,29,0),(16,32,32),{'up':face('#roof',uvf((0,0,16,32),32,32)),'down':face(I,[0,0,16,16]),
             'north':face(RE,uvf((0,0,16,3),32,16)),'south':face(RE,uvf((0,0,16,3),32,16)),
             'east':face(RE,uvf((0,0,32,3),32,16)),'west':face(RE,uvf((0,0,32,3),32,16))}))
    E.append(el('plinth',(0,-15,0),(16,-13,32),{'up':face(I,[0,0,16,16]),'down':face(P,[0,2,16,14]),'north':face(P,[0,0,16,2]),
             'south':face(P,[0,0,16,2]),'east':face(P,[0,0,16,2]),'west':face(P,[0,0,16,2])}))
    for (x,z) in ((1,1),(13.5,1),(1,29.5),(13.5,29.5)):                      # casters
        E.append(el('caster',(x,-16,z),(x+1.5,-15,z+1.5),all_faces(P,[0,8,2,10])))
    for (x,z) in ((3.5,2),(11.5,2),(3.5,29),(11.5,29)):                       # levelling feet
        E.append(el('foot',(x,-16,z),(x+1,-15,z+1),all_faces(P,[4,8,6,10])))
    # 19" rails, front (z 2..2.5) and rear (z 29.5..30): flange 2 px wide, numbered 1..42 from the bottom
    for z0,face_dir,n in ((2,'north','front'),(29.5,'south','rear')):
        for x0,s in ((1.5,'l'),(12.5,'r')):
            E.append(el(f'rail_{n}_{s}',(x0,-13,z0),(x0+2,29,z0+0.5),{face_dir:face('#rail',uvf((0,24,16,360),16,384)),
                     'east':face('#rail',[0,0,2,16]),'west':face('#rail',[0,0,2,16])}))
    # dark interior back plane between the rails so the cabinet reads deep (not a wall: devices sit in front of it)
    return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','ambientocclusion':False,
            'textures':{'frame':'encodedlogistics:block/rack/frame','interior':'encodedlogistics:block/rack/interior',
                        'plinth':'encodedlogistics:block/rack/plinth','roof':'encodedlogistics:block/rack/roof','roof_edge':'encodedlogistics:block/rack/roof_edge',
                        'side_upper':'encodedlogistics:block/rack/side_upper','side_lower':'encodedlogistics:block/rack/side_lower',
                        'rail':'encodedlogistics:block/rack/rail','particle':'encodedlogistics:block/rack/frame'},'elements':E}
# ---------------- doors (BER parts; rotated about their hinge pivot) ----------------
FRONT_PIVOT=[15.5,0,0.5]          # front door: hinged on the LEFT as seen from the front (+x side), swings out to -z
# Rear leaves sit 0.4 px in from the back (z 30.6..31.6) so their handles end at z 32, the furthest a model element may
# reach; their pivots are on the leaves' centre line.
REAR_PIVOTS={'left':[15.5,0,31.1],'right':[0.5,0,31.1]}   # rear leaves as seen from BEHIND: left leaf hinged at +x? see HANDOFF
def door_model(name,x0,x1,z0,z1,tex,tw,th,handle_x=None,handle_face='north'):
    w=x1-x0; E=[el(name,(x0,-14.5,z0),(x1,29,z1),{'north':face('#mesh',uvf((0,0,tw,th),up16(tw),up16(th))),'south':face('#mesh',uvf((tw,0,0,th),up16(tw),up16(th))),
                'east':face('#frame',[0,0,1,16]),'west':face('#frame',[0,0,1,16]),'up':face('#frame',[0,0,16,1]),'down':face('#frame',[0,0,16,1])})]
    if handle_x is not None:
        zf=z0-0.4 if handle_face=='north' else z1; zb=z0 if handle_face=='north' else z1+0.4
        E.append(el('handle',(handle_x,5,zf),(handle_x+0.75,10,zb),{f:face('#parts',[0,0,4,8]) for f in ('north','south','east','west','up','down')}))
        E.append(el('lock',(handle_x,3.5,zf),(handle_x+0.75,4.25,zb),{f:face('#parts',[6,1,8,3]) for f in ('north','south','east','west','up','down')}))
    return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','ambientocclusion':False,
            'textures':{'mesh':f'encodedlogistics:block/rack/{tex}','frame':'encodedlogistics:block/rack/frame','parts':'encodedlogistics:block/rack/door_parts',
                        'particle':'encodedlogistics:block/rack/frame'},'elements':E}
def door_models():
    return {'rack_door_front':door_model('door_front',0.5,15.5,0,1,'door_front',60,180,handle_x=1.75,handle_face='north'),
            'rack_door_rear_left':door_model('door_rear_left',8,15.5,30.6,31.6,'door_rear',32,180,handle_x=8.6,handle_face='south'),
            'rack_door_rear_right':door_model('door_rear_right',0.5,8,30.6,31.6,'door_rear',32,180,handle_x=6.65,handle_face='south')}
# ---------------- devices (BER parts; drawn at their U, y 0..n in model space) ----------------
def device_model(name,n,glow=None):
    """13 px wide (x 1.5..14.5, ears over the rails), n px tall (1U = 1 px), front plate at z 1.75, rear at z 29.25.
       Texture 128x128 at 8 texels/px: front (0,0)-(104,8n), rear (0,32)-(104,32+8n), chassis texels at (112,0)-(128,16)."""
    T=128; fr=uvf((0,0,104,8*n),T,T); rr=uvf((104,32,0,32+8*n),T,T); ch=uvf((112,0,128,16),T,T)
    E=[el('chassis',(1.5,0,1.75),(14.5,n,29.25),{'north':face('#tex',fr),'south':face('#tex',rr),'east':face('#tex',ch),'west':face('#tex',ch),
          'up':face('#tex',ch),'down':face('#tex',ch)})]
    tx={'tex':f'encodedlogistics:block/rack_device/{name}','particle':f'encodedlogistics:block/rack_device/{name}'}
    if glow:
        tx['glow']=f'encodedlogistics:block/rack_device/{name}_{glow}'
        E.append(el('leds',(1.5,0,1.74),(14.5,n,29.26),{'north':face('#glow',fr),'south':face('#glow',rr)},
                    neoforge_data={'block_light':15,'sky_light':15},shade=False))
    return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':E}
