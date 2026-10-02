# Encoded Logistics - Network Cable asset exporter.
# Writes textures, block models, item models, blockstates and item definitions for every cable.
# Run from the project root: python tools/export_cables.py
# The textures are painted directly in ramp steps (docs/TEXTURE_STYLE.md): clean, AE2-style - no random grain, so the
# old shading pass (tools/run_pass.py) is no longer run over them.
#
# Shape (the cable spec, shared with Fiber Cable; see tools/export_cable_geometry.py): a straight run is one continuous
# tube (sleeve 0..5 | straight body 5..11 | sleeve 11..16, all the same width, no necks), so neighbouring blocks read as
# one cable; a junction is a cube the arms run straight into; a cable against a network block ends in a flange plate.
#                    tube   junction cube   flange
#   Normal           6x6    6x6x6           8x8 x1
#   Dense            8x8    10x10x10        12x12 x1
#
# Colours: each dye's 7-step ramp (el_style.dye_ramp, docs/TEXTURE_STYLE.md 2.2); white uses the top of the steel ramp.
# Dyed bodies are lit like any block; only the junction cube's centre light glows. Neutral cables take the controller's
# hue cycle for their colour, all of it glowing and animated in step with it.
#   Normal tube: one rounded strand - dark edges, lit middle.
#   Dense tube:  two strands side by side (a darker dye seam between them, never a black gap).
#   Both: a slightly darker 2 px band every 8 px along the cable (at 0|15 and 7-8 of each block), so the rings line
#   up across block seams and between the arms and the straight body.
#   Junction faces: dark rim, flat dye, a small bevelled centre light. Flange: clean steel plate.
import os, json, colorsys, sys
from PIL import Image
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from el_style import G, DYE_BASE, dye_ramp
OUT='src/main/resources/assets/encodedlogistics'
COLOURS=['neutral','white','orange','magenta','light_blue','yellow','lime','pink','cyan','purple','blue','green','red']
SIZES={'normal':dict(tube=6,junction=6,flange=8),
       'dense': dict(tube=8,junction=10,flange=12)}
NEUTRAL_FRAMES=16
def g(i): return G[max(0,min(10,i))]

def hue_rgb(f):
    """The controller's emissive hue for frame f (same wheel position and timing as tools/ctrl.py)."""
    h=(0.62+f/NEUTRAL_FRAMES)%1.0; r,gg,b=colorsys.hsv_to_rgb(h,0.72,0.92); return (int(r*255),int(gg*255),int(b*255))
def ramp(colour,frame=0):
    """7 tones (-3..+3) for a cable colour."""
    if colour=='neutral': return dye_ramp(hue_rgb(frame))
    if colour=='white': return [g(5),g(6),g(7),g(8),g(9),g(10),g(10)]
    return dye_ramp(DYE_BASE[colour])
def glows(colour): return colour=='neutral'

# ---------- texel painters: return (rgb, glow); glow = the texel also goes on the full-bright _glow overlay ----------
PROFILES={'normal':[-2,0,1,1,0,-2],                 # one rounded strand
          'dense': [-2,0,1,-1,-1,1,0,-2]}            # two strands, darker dye seam between them
BAND={0,7,8,15}                                      # 2 px rings every 8 px along the cable, symmetric under u -> 15-u
def tube_px(colour,tier,along,across,frame=0):
    P=PROFILES[tier]; k=P[across]
    if along in BAND and 0<across<len(P)-1: k-=1
    return ramp(colour,frame)[k+3],glows(colour)
def junction_px(colour,tier,u,v,n,frame=0):
    R=ramp(colour,frame); r=min(u,v,n-1-u,n-1-v); c=n//2-1
    if (u,v)==(c,c): return R[6],True                      # centre light: bevelled 2x2, lit top-left
    if (u,v) in ((c+1,c),(c,c+1)): return R[5],True
    if (u,v)==(c+1,c+1): return R[4],True
    rings=[-2,0] if tier=='normal' else [-2,0,1,-1]
    return R[rings[min(r,len(rings)-1)]+3],glows(colour)
def flange_px(tier,u,v,n):
    r=min(u,v,n-1-u,n-1-v)
    if tier=='normal': return g(7 if r==0 else 5)                  # only the outer ring shows round the 6 px tube
    return g(6 if r==0 else 8 if r==1 else 5)
def endcap_px(colour,u,v,n,frame=0):
    """Item-only end cap: the tube's dark dye rim round a steel plug."""
    r=min(u,v,n-1-u,n-1-v)
    if r==0: return ramp(colour,frame)[1],glows(colour)
    return (g(7) if r==1 else g(5)),False

# ---------- texture sheets ----------
# _h : the tube profile across rows 0..tube-1, running along u; item end cap (tube x tube) at (0,8)
# _v : the same with u and v swapped (up/down faces, so the stripes follow the cable)
# _j : junction cube face at (0,0)
# _f : flange face at (0,0)
# solid texel on _h: (15,15) flange edge (steel)
SOLID={'flange_edge':(15,15)}
END_AT=(0,8)
def sheets(colour,tier,frame=0):
    S=SIZES[tier]
    T={k:Image.new('RGBA',(16,16),(0,0,0,0)) for k in 'hvjf'}; Gl={k:Image.new('RGBA',(16,16),(0,0,0,0)) for k in 'hvjf'}
    def put(k,x,y,px):
        c,gl=px; T[k].putpixel((x,y),c+(255,))
        if gl: Gl[k].putpixel((x,y),c+(255,))
    w=S['tube']
    for a in range(16):
        for b in range(w):
            px=tube_px(colour,tier,a,b,frame); put('h',a,b,px); put('v',b,a,px)
    for y in range(w):
        for x in range(w): put('h',END_AT[0]+x,END_AT[1]+y,endcap_px(colour,x,y,w,frame))
    j=S['junction']
    for y in range(j):
        for x in range(j): put('j',x,y,junction_px(colour,tier,x,y,j,frame))
    f=S['flange']
    for y in range(f):
        for x in range(f): put('f',x,y,(flange_px(tier,x,y,f),False))
    put('h',*SOLID['flange_edge'],(g(6),False))
    return T,Gl
def tex_name(tier,colour,k): return f'cable/{tier}/{colour}_{k}'

def write_textures():
    for tier in SIZES:
        d=f'{OUT}/textures/block/cable/{tier}'; os.makedirs(d,exist_ok=True)
        for old in os.listdir(d): os.remove(os.path.join(d,old))
        for colour in COLOURS:
            T,Gl=sheets(colour,tier)
            for k in 'hvjf':
                T[k].save(f'{d}/{colour}_{k}.png')
                if colour=='neutral':      # the controller's hue cycle, same frames and timing
                    strip_img=Image.new('RGBA',(16,16*NEUTRAL_FRAMES),(0,0,0,0))
                    for fr in range(NEUTRAL_FRAMES): strip_img.paste(sheets(colour,tier,fr)[1][k],(0,16*fr))
                    strip_img.save(f'{d}/{colour}_{k}_glow.png')
                    open(f'{d}/{colour}_{k}_glow.png.mcmeta','w',newline='\n').write(
                        '{\n  "animation": {\n    "frametime": 6,\n    "interpolate": true\n  }\n}\n')
                else:
                    Gl[k].save(f'{d}/{colour}_{k}_glow.png')

# ---------- geometry ----------
# The models come from tools/export_cable_geometry.py (the cable spec shared with Fiber Cable).

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
    base={'normal':'network_cable','dense':'dense_network_cable','fiber':'fiber_cable'}[tier]
    return base if colour=='neutral' else f'{colour}_{base}'
def multipart(m,arm_straight='arm_straight',arm_junction='arm_junction'):
    """Straight runs: the straight body turned to its axis + both arms; otherwise the junction cube + an arm per side."""
    mp=[]
    for ax in 'zxy': mp.append({'when':straight_when(ax),'apply':{'model':m('cube_straight'),**AXIS_ROT[ax]}})
    mp.append({'when':JUNCTION,'apply':{'model':m('cube_junction')}})
    for d in DIRS:
        ax=[k for k,v in AXES.items() if d in v][0]
        mp.append({'when':{'AND':[straight_when(ax),{d:'cable'}]},'apply':{'model':m(arm_straight),**ROT[d]}})
        mp.append({'when':{'AND':[JUNCTION,{d:'cable'}]},'apply':{'model':m(arm_junction),**ROT[d]}})
        mp.append({'when':{d:'block'},'apply':{'model':m('arm_block'),**ROT[d]}})
    return mp
def write_blockstates_and_items():
    os.makedirs(f'{OUT}/blockstates',exist_ok=True); os.makedirs(f'{OUT}/items',exist_ok=True)
    for tier in SIZES:
        for colour in COLOURS:
            m=lambda part: f'encodedlogistics:block/cable/{tier}/{colour}/{part}'
            bid=block_id(tier,colour)
            json.dump({'multipart':multipart(m)},open(f'{OUT}/blockstates/{bid}.json','w',newline='\n'),indent=1)
            json.dump({'model':{'type':'minecraft:model','model':m('item')}},open(f'{OUT}/items/{bid}.json','w',newline='\n'),indent=1)

if __name__=='__main__':
    import export_cable_geometry
    write_textures()
    for tier in SIZES: os.makedirs(f'{OUT}/models/block/cable/{tier}',exist_ok=True)
    shapes=export_cable_geometry.write(export_cable_geometry.TEX)
    write_blockstates_and_items()
    print(json.dumps(shapes))      # the part boxes (pixels, arms pointing north) in CableShapes.java
