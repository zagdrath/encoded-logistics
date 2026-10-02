# Encoded Logistics - Network Cable asset exporter.
# Writes textures, block models, item models, blockstates and item definitions for every cable.
# Run from the project root: python tools/export_cables.py
# After writing the base textures it runs the Minecraft-style shading pass (tools/run_pass.py, see
# docs/TEXTURE_STYLE.md), so what it leaves on disk is the shipped art.
#
# Shape (AE2-style): a straight run is one continuous tube; an end, bend or junction is a cube, joined to its
# neighbours by a thinner core, and a cable against a network block ends in a flange plate. So a run reads
# cube - core - tube ... tube - core - cube.
#                    tube   core   junction cube   flange
#   Normal           6x6    4x4    6x6x6           8x8 x1
#   Dense            8x8    6x6    10x10x10        12x12 x2
#
# Colours: dyes in Minecraft's wool colours, with the controller's steel and groove (tools/ctrl.py). Dyed bodies are lit
# like any block; only their small accents (connector dots, cube centres) glow.
#   Normal tube: dye with darker edges and a steel stripe down the middle; core: steel; cube: dye with light dots.
#   Dense tube:  two dye strands (light outside, darker inside) with a black gap down the middle; core: the same,
#                narrower; cube: concentric dye rings round a bright centre.
# Neutral cables use the controller's hue cycle for their colour, all of it glowing and animated in step with it.
import os, json, colorsys
from PIL import Image
OUT='src/main/resources/assets/encodedlogistics'
def H(s): return tuple(int(s[i:i+2],16) for i in (1,3,5))
DYES={'white':'#F9FFFE','orange':'#F9801D','magenta':'#C74EBD','light_blue':'#3AB3DA','yellow':'#FED83D','lime':'#80C71F',
      'pink':'#F38BAA','cyan':'#169C9C','purple':'#8932B8','blue':'#3C44AA','green':'#5E7C16','red':'#B02E26'}
COLOURS=['neutral']+list(DYES)
GROOVE=H('#15181C')
SHEEN,EDGE_END=H('#AEB6C0'),H('#7C8692')
STEEL_DARK=H('#5A636E')
def lerp(a,b,t): return tuple(round(a[i]+(b[i]-a[i])*t) for i in range(3))
def lift(c,k): return tuple(int(v+(255-v)*k) for v in c)
def dim(c,k): return tuple(int(v*k) for v in c)
def tri16(v): return abs((v%16)-7.5)/7.5           # 0 at the middle of a block, 1 at its ends: tiles seamlessly
def tri_n(v,n): return abs(v-(n-1)/2)/((n-1)/2) if n>1 else 0
def steel(along): return lerp(SHEEN,EDGE_END,tri16(along)**1.5)
SIZES={'normal':dict(tube=6,core=4,junction=6,flange=8,flange_t=1),
       'dense': dict(tube=8,core=6,junction=10,flange=12,flange_t=2)}
NEUTRAL_FRAMES=16

# Minecraft's wool colours (the average of each wool block's texture), so dyed cables sit with vanilla's palette.
WOOL={'white':'#E9ECEC','orange':'#F07613','magenta':'#BD44B3','light_blue':'#3AAFD9','yellow':'#F8C627','lime':'#70B919',
      'pink':'#ED8DAC','cyan':'#158991','purple':'#792AAC','blue':'#35399D','green':'#546D1B','red':'#A12722'}
def brighten(hexcol):
    """A wool colour, lifted: brighter (the darkest colours most), saturation eased only slightly, no white mixed in."""
    r,g,b=(c/255 for c in H(hexcol)); h,sat,val=colorsys.rgb_to_hsv(r,g,b)
    val=min(1.0,val*1.12+0.08); sat*=0.92
    r,g,b=colorsys.hsv_to_rgb(h,sat,val); return (int(r*255),int(g*255),int(b*255))
def wool(colour,t):
    """A dye as its (lifted) wool colour, shaded gently: a touch lighter in the middle of a piece, a little darker at its
    ends."""
    c=brighten(WOOL[colour]); return lerp(lift(c,0.05),dim(c,0.88),t)
def hue_rgb(f,t=0.0):
    """The controller's emissive: a two-hue gradient rotating round the wheel, brighter toward the middle."""
    h=((0.62+f/NEUTRAL_FRAMES)+0.14*t)%1.0; v=1.0 if t<0.35 else 0.88 if t<0.7 else 0.76
    r,g,b=colorsys.hsv_to_rgb(h,0.72,v); return (int(r*255),int(g*255),int(b*255))
def dye(colour,frame=0,t=0.0):
    """The cable's colour, shaded smoothly: t is 0 in the middle of a piece and 1 at its ends."""
    if colour=='neutral': return hue_rgb(frame,t)
    return wool(colour,t)

# ---------- texel painters: return (rgb, glow); glow = the texel also goes on the full-bright _glow overlay ----------
def glows(colour): return colour=='neutral'
def tone(colour,key,along,frame):
    t=tri16(along)*0.6
    c=dye(colour,frame,t); g=glows(colour)
    if key=='D': return dim(c,0.72),g             # dye, darker (edges)
    if key=='C': return c,g                       # dye
    if key=='L': return lift(c,0.12),g            # dye, a touch lighter
    if key=='S': return steel(along),False        # steel with its sheen
    if key=='s': return lerp(STEEL_DARK,EDGE_END,0.4),False
    return GROOVE,False                           # 'K': the black gap
PROFILES={
    'normal':{'tube':'DCSSCD','core':'sSSs'},
    'dense': {'tube':'DLCKKCLD','core':'DCKKCD'}}
def profile_px(colour,tier,part,along,across,frame=0):
    return tone(colour,PROFILES[tier][part][across],along,frame)
def junction_px(colour,tier,u,v,n,frame=0):
    r=min(u,v,n-1-u,n-1-v); t=max(tri_n(u,n),tri_n(v,n)); c=dye(colour,frame,t*0.6)
    if tier=='normal':
        # Dye with a darker rim and two light dots across the middle.
        if r==0: return dim(c,0.72),glows(colour)
        if (u,v) in ((2,2),(3,3)): return lift(c,0.75),True
        if (u,v) in ((3,2),(2,3)): return lift(c,0.12),True
        return c,glows(colour)
    # Concentric rings: darker rim, dye, lighter, dye, bright centre (the centre glows).
    ring=[dim(c,0.72),c,lift(c,0.12),c,lift(c,0.30)]
    return ring[min(r,4)],r>=4 or glows(colour)
def flange_px(colour,u,v,n,frame=0):
    r=min(u,v,n-1-u,n-1-v); along=(u if r in (v,n-1-v) else v)*16//max(1,n)
    if r==0: return GROOVE,False
    if r==1: return steel(along),False
    t=max(tri_n(u,n),tri_n(v,n)); c=dye(colour,frame,t*0.6)
    ring=[None,None,dim(c,0.72),c,lift(c,0.12),lift(c,0.30)]
    return ring[min(r,5)],r>=5 or glows(colour)

# ---------- texture sheets ----------
# _h : the tube profile across rows 0..tube-1 and the core profile across rows 8..8+core-1, both running along u
# _v : the same with u and v swapped (up/down faces, so the stripes follow the cable)
# _j : junction cube face at (0,0)
# _f : flange face at (0,0)
# solid texels on _h row 15: (15,15) end cap (dark steel), (14,15) flange edge (steel), (13,15) groove
CORE_ROW=8
SOLID={'end':(15,15),'flange_edge':(14,15),'groove':(13,15)}
def sheets(colour,tier,frame=0):
    S=SIZES[tier]
    T={k:Image.new('RGBA',(16,16),(0,0,0,0)) for k in 'hvjf'}; G={k:Image.new('RGBA',(16,16),(0,0,0,0)) for k in 'hvjf'}
    def put(k,x,y,px):
        c,g=px; T[k].putpixel((x,y),c+(255,))
        if g: G[k].putpixel((x,y),c+(255,))
    for a in range(16):
        for b in range(S['tube']):
            px=profile_px(colour,tier,'tube',a,b,frame); put('h',a,b,px); put('v',b,a,px)
        for b in range(S['core']):
            px=profile_px(colour,tier,'core',a,b,frame); put('h',a,CORE_ROW+b,px); put('v',CORE_ROW+b,a,px)
    j=S['junction']
    for y in range(j):
        for x in range(j): put('j',x,y,junction_px(colour,tier,x,y,j,frame))
    f=S['flange']
    for y in range(f):
        for x in range(f): put('f',x,y,flange_px(colour,x,y,f,frame))
    T['h'].putpixel(SOLID['end'],lerp(STEEL_DARK,EDGE_END,0.4)+(255,))
    T['h'].putpixel(SOLID['flange_edge'],steel(4)+(255,))
    T['h'].putpixel(SOLID['groove'],GROOVE+(255,))
    return T,G
def tex_name(tier,colour,k): return f'cable/{tier}/{colour}_{k}'

def write_textures():
    for tier in SIZES:
        d=f'{OUT}/textures/block/cable/{tier}'; os.makedirs(d,exist_ok=True)
        for old in os.listdir(d): os.remove(os.path.join(d,old))
        for colour in COLOURS:
            T,G=sheets(colour,tier)
            for k in 'hvjf':
                T[k].save(f'{d}/{colour}_{k}.png')
                if colour=='neutral':      # the controller's hue cycle, same frames and timing
                    strip_img=Image.new('RGBA',(16,16*NEUTRAL_FRAMES),(0,0,0,0))
                    for fr in range(NEUTRAL_FRAMES): strip_img.paste(sheets(colour,tier,fr)[1][k],(0,16*fr))
                    strip_img.save(f'{d}/{colour}_{k}_glow.png')
                    open(f'{d}/{colour}_{k}_glow.png.mcmeta','w',newline='\n').write(
                        '{\n  "animation": {\n    "frametime": 6,\n    "interpolate": true\n  }\n}\n')
                else:
                    G[k].save(f'{d}/{colour}_{k}_glow.png')

# ---------- geometry (pixel coords; arms are modelled pointing NORTH = -Z, the blockstate rotates them) ----------
def box(w,z0,z1):
    lo,hi=8-w/2,8+w/2; return [lo,lo,z0],[hi,hi,z1]
def solid_uv(name): x,y=SOLID[name]; return [x,y,x+1,y+1]
def run_faces(z0,z1,w,row):
    """Side faces of a tube or core along Z: the profile across, running along the cable on all four sides."""
    return {
      'east': {'texture':'#h','uv':[16-z1,row,16-z0,row+w]},
      'west': {'texture':'#h','uv':[z0,row,z1,row+w]},
      'up':   {'texture':'#v','uv':[row,z0,row+w,z1]},
      'down': {'texture':'#v','uv':[row,16-z1,row+w,16-z0]},
      'north':{'texture':'#h','uv':solid_uv('end')},'south':{'texture':'#h','uv':solid_uv('end')}}
def cube_faces(tex,n):
    r=[0,0,n,n]; return {f:{'texture':tex,'uv':r} for f in ('north','south','east','west','up','down')}
def flange_faces(n):
    fc={'north':{'texture':'#f','uv':[0,0,n,n]},'south':{'texture':'#f','uv':[0,0,n,n]}}
    for f in ('east','west','up','down'): fc[f]={'texture':'#h','uv':solid_uv('flange_edge')}
    return fc
def el(frm,to,faces): return {'from':frm,'to':to,'faces':faces}
def with_glow(elements):
    """Duplicate every element as a full-bright overlay using the matching _glow textures (Arcforge's method)."""
    out=list(elements)
    for e in elements:
        g={'from':e['from'],'to':e['to'],'faces':{k:{**v,'texture':v['texture']+'_glow'} for k,v in e['faces'].items()},
           'neoforge_data':{'block_light':15,'sky_light':15},'shade':False}
        out.append(g)
    return out
def part_elements(tier,part):
    S=SIZES[tier]; w,n,j,f,ft=S['tube'],S['core'],S['junction'],S['flange'],S['flange_t']; h=j/2
    if part=='cube_straight':                       # the middle of a straight tube, along Z
        return [el(*box(w,5,11),run_faces(5,11,w,0))]
    if part=='arm_straight':                        # the tube out to the block edge
        return [el(*box(w,0,5),run_faces(0,5,w,0))]
    if part=='cube_junction':
        return [el([8-h]*3,[8+h]*3,cube_faces('#j',j))]
    if part=='arm_junction':                        # the core from the junction cube to the block edge
        return [el(*box(n,0,8-h),run_faces(0,8-h,n,CORE_ROW))]
    if part=='arm_block':                           # the core, then a flange plate against the block
        return [el(*box(n,ft,8-h),run_faces(ft,8-h,n,CORE_ROW)), el(*box(f,0,ft),flange_faces(f))]
PARTS=['cube_straight','cube_junction','arm_straight','arm_junction','arm_block']
def textures_for(tier,colour):
    t={k:f'encodedlogistics:block/{tex_name(tier,colour,k)}' for k in 'hvjf'}
    t.update({k+'_glow':f'encodedlogistics:block/{tex_name(tier,colour,k)}_glow' for k in 'hvjf'})
    t['particle']=f'encodedlogistics:block/{tex_name(tier,colour,"j")}'; return t
def item_elements(tier):
    """The item: cube - core - tube - core - cube along Z, the way a short run looks placed (cubes 4px long)."""
    S=SIZES[tier]; w,n,j=S['tube'],S['core'],S['junction']; hj=j/2
    def end_cube(z0,z1):
        faces={'north':{'texture':'#j','uv':[0,0,j,j]},'south':{'texture':'#j','uv':[0,0,j,j]}}
        for f in ('east','west','up','down'): faces[f]={'texture':'#j','uv':[0,0,z1-z0,j] if f in ('east','west') else [0,0,j,z1-z0]}
        return el([8-hj,8-hj,z0],[8+hj,8+hj,z1],faces)
    return [end_cube(0,4), end_cube(12,16),
            el(*box(n,4,5),run_faces(4,5,n,CORE_ROW)), el(*box(n,11,12),run_faces(11,12,n,CORE_ROW)),
            el(*box(w,5,11),run_faces(5,11,w,0))]
def write_models():
    for tier in SIZES:
        base=f'{OUT}/models/block/cable/{tier}'; os.makedirs(base,exist_ok=True)
        for part in PARTS:      # template models (geometry + texture variables)
            json.dump({'render_type':'minecraft:cutout','textures':{'particle':'#j'},
                       'elements':with_glow(part_elements(tier,part))},open(f'{base}/template_{part}.json','w',newline='\n'),indent=1)
        json.dump({'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':{'particle':'#j'},
                   'display':{'gui':{'rotation':[30,45,0],'scale':[0.9,0.9,0.9]}},
                   'elements':with_glow(item_elements(tier))},open(f'{base}/template_item.json','w',newline='\n'),indent=1)
        for colour in COLOURS:
            d=f'{base}/{colour}'; os.makedirs(d,exist_ok=True)
            for part in PARTS+['item']:
                json.dump({'parent':f'encodedlogistics:block/cable/{tier}/template_{part}','textures':textures_for(tier,colour)},
                          open(f'{d}/{part}.json','w',newline='\n'),indent=1)

# ---------- blockstates ----------
DIRS=['north','south','east','west','up','down']
ROT={'north':{},'south':{'y':180},'east':{'y':90},'west':{'y':270},'up':{'x':270},'down':{'x':90}}
AXES={'z':('north','south'),'x':('east','west'),'y':('up','down')}
AXIS_ROT={'z':{},'x':{'y':90},'y':{'x':90}}
def straight_when(axis):
    a,b=AXES[axis]; return {d:('cable' if d in (a,b) else 'none') for d in DIRS}
def not_straight(axis):
    a,b=AXES[axis]; return {'OR':[{a:'!cable'},{b:'!cable'}]+[{d:'!none'} for d in DIRS if d not in (a,b)]}
JUNCTION={'AND':[not_straight(ax) for ax in 'zxy']}
def block_id(tier,colour):
    base='network_cable' if tier=='normal' else 'dense_network_cable'
    return base if colour=='neutral' else f'{colour}_{base}'
def write_blockstates_and_items():
    os.makedirs(f'{OUT}/blockstates',exist_ok=True); os.makedirs(f'{OUT}/items',exist_ok=True)
    for tier in SIZES:
        for colour in COLOURS:
            m=lambda part: f'encodedlogistics:block/cable/{tier}/{colour}/{part}'
            mp=[]
            for ax in 'zxy': mp.append({'when':straight_when(ax),'apply':{'model':m('cube_straight'),**AXIS_ROT[ax]}})
            mp.append({'when':JUNCTION,'apply':{'model':m('cube_junction')}})
            for d in DIRS:
                ax=[k for k,v in AXES.items() if d in v][0]
                mp.append({'when':{'AND':[straight_when(ax),{d:'cable'}]},'apply':{'model':m('arm_straight'),**ROT[d]}})
                mp.append({'when':{'AND':[JUNCTION,{d:'cable'}]},'apply':{'model':m('arm_junction'),**ROT[d]}})
                mp.append({'when':{d:'block'},'apply':{'model':m('arm_block'),**ROT[d]}})
            bid=block_id(tier,colour)
            json.dump({'multipart':mp},open(f'{OUT}/blockstates/{bid}.json','w',newline='\n'),indent=1)
            json.dump({'model':{'type':'minecraft:model','model':m('item')}},open(f'{OUT}/items/{bid}.json','w',newline='\n'),indent=1)

def shapes_reference():
    """The collision boxes of each part (pixels), for CableShapes.java."""
    out={}
    for tier in SIZES:
        out[tier]={part:[e['from']+e['to'] for e in part_elements(tier,part)] for part in PARTS}
    return out

if __name__=='__main__':
    import sys; sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
    from run_pass import run_cables
    write_textures(); run_cables(); write_models(); write_blockstates_and_items()
    print(json.dumps(shapes_reference()))
