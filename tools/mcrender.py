# Renders blocks by loading the exported blockstate/model JSON and PNG textures, following vanilla model rules:
# multipart 'when' (AND/OR, '|' and '!' values), parent models + #texture variables, per-face uv,
# blockstate x-then-y rotation about the block centre, directional face shading, neoforge_data full-bright.
import json, math, os, numpy as np
from PIL import Image
ROOT='assets/encodedlogistics'
SHADE={'up':1.0,'down':0.5,'north':0.8,'south':0.8,'east':0.6,'west':0.6}
_tex={}
def load_tex(rl,frame=0):
    ns,path=rl.split(':'); p=f'assets/{ns}/textures/{path}.png'
    key=(p,frame)
    if key not in _tex:
        im=Image.open(p).convert('RGBA')
        if im.height>im.width: n=im.height//im.width; im=im.crop((0,16*(frame%n),16,16*(frame%n)+16))
        _tex[key]=np.array(im)
    return _tex[key]
VANILLA={'minecraft:block/orientable_with_bottom':{'textures':{},'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{
    'north':{'texture':'#front','uv':[0,0,16,16]},'east':{'texture':'#side','uv':[0,0,16,16]},'south':{'texture':'#side','uv':[0,0,16,16]},
    'west':{'texture':'#side','uv':[0,0,16,16]},'up':{'texture':'#top','uv':[0,0,16,16]},'down':{'texture':'#bottom','uv':[0,0,16,16]}}}]}}
def load_model(rl):
    if rl in VANILLA: return json.loads(json.dumps(VANILLA[rl]))
    ns,path=rl.split(':'); m=json.load(open(f'assets/{ns}/models/{path}.json'))
    if m.get('parent') in VANILLA:
        p=load_model(m['parent']); p['textures'].update(m.get('textures',{})); return p
    if 'parent' in m and m['parent'].startswith('encodedlogistics:'):
        p=load_model(m['parent']); t=dict(p.get('textures',{})); t.update(m.get('textures',{}))
        out=dict(p); out.update({k:v for k,v in m.items() if k not in ('parent','textures')}); out['textures']=t; return out
    return m
def resolve(tex,textures):
    seen=0
    while tex.startswith('#') and seen<8: tex=textures[tex[1:]]; seen+=1
    return tex
def match_value(state_val,cond):
    neg=cond.startswith('!'); opts=cond.lstrip('!').split('|'); hit=state_val in opts
    return hit!=neg
def when_ok(w,state):
    if 'OR' in w: return any(when_ok(x,state) for x in w['OR'])
    if 'AND' in w: return all(when_ok(x,state) for x in w['AND'])
    return all(match_value(state[k],v) for k,v in w.items())
def rot_x(p,deg):           # x:90 maps +y -> -z (north -> down)
    x,y,z=p
    for _ in range((deg//90)%4): y,z=z,-y
    return (x,y,z)
def rot_y(p,deg):           # y:90 maps north -> east
    x,y,z=p
    for _ in range((deg//90)%4): x,z=-z,x
    return (x,y,z)
DIRV={'up':(0,1,0),'down':(0,-1,0),'north':(0,0,-1),'south':(0,0,1),'east':(1,0,0),'west':(-1,0,0)}
def vec_dir(v): return min(DIRV,key=lambda k:-sum(a*b for a,b in zip(DIRV[k],v)))
def face_corners(face,f,t):
    """Returns 4 (pos, (s,t)) corners: s=0 -> u1 side, t=0 -> v1 side (vanilla FaceBakery orientation)."""
    (x0,y0,z0),(x1,y1,z1)=f,t
    if face=='up':    P=lambda s,tt:(x0+s*(x1-x0), y1, z0+tt*(z1-z0))
    if face=='down':  P=lambda s,tt:(x0+s*(x1-x0), y0, z1-tt*(z1-z0))
    if face=='north': P=lambda s,tt:(x1-s*(x1-x0), y1-tt*(y1-y0), z0)
    if face=='south': P=lambda s,tt:(x0+s*(x1-x0), y1-tt*(y1-y0), z1)
    if face=='west':  P=lambda s,tt:(x0, y1-tt*(y1-y0), z0+s*(z1-z0))
    if face=='east':  P=lambda s,tt:(x1, y1-tt*(y1-y0), z1-s*(z1-z0))
    return [(P(s,tt),(s,tt)) for s,tt in ((0,0),(1,0),(1,1),(0,1))]
def model_quads(rl,rx,ry,origin,frame=0):
    m=load_model(rl); T=m.get('textures',{}); out=[]
    for e in m['elements']:
        bright=bool(e.get('neoforge_data',{}).get('block_light',0)>=15) or e.get('shade',True) is False
        for face,fd in e['faces'].items():
            tex=load_tex(resolve(fd['texture'],T),frame)
            if 'uv' in fd: u1,v1,u2,v2=fd['uv']
            else:                                   # vanilla default UVs from the element bounds
                (x0,y0,z0),(x1,y1,z1)=e['from'],e['to']
                u1,v1,u2,v2={'down':(x0,16-z1,x1,16-z0),'up':(x0,z0,x1,z1),'north':(16-x1,16-y1,16-x0,16-y0),
                             'south':(x0,16-y1,x1,16-y0),'west':(z0,16-y1,z1,16-y0),'east':(16-z1,16-y1,16-z0,16-y0)}[face]
            verts=[];uvs=[]
            rot=fd.get('rotation',0)//90
            for (p,(s,tt)) in face_corners(face,e['from'],e['to']):
                for _ in range(rot): s,tt=tt,1-s                # face texture rotation, 90 deg steps (clockwise)
                q=(p[0]-8,p[1]-8,p[2]-8); q=rot_x(q,rx); q=rot_y(q,ry)
                verts.append((origin[0]+(q[0]+8)/16,origin[1]+(q[1]+8)/16,origin[2]+(q[2]+8)/16))
                uvs.append((u1+s*(u2-u1),v1+tt*(v2-v1)))
            n=rot_y(rot_x(DIRV[face],rx),ry)
            out.append((np.array(verts),np.array(uvs),tex,1.0 if bright else SHADE[vec_dir(n)],bright))
    return out
def block_quads(block_id,state,origin,frame=0):
    bs=json.load(open(f'{ROOT}/blockstates/{block_id}.json')); out=[]
    if 'variants' in bs:
        for key,a in bs['variants'].items():          # like the game: properties in any order
            want=dict(kv.split('=') for kv in key.split(',') if kv)
            if all(state.get(k)==v for k,v in want.items()):
                return model_quads(a['model'],a.get('x',0),a.get('y',0),origin,frame)
        raise KeyError(state)
    for part in bs['multipart']:
        if 'when' in part and not when_ok(part['when'],state): continue
        a=part['apply']; out+=model_quads(a['model'],a.get('x',0),a.get('y',0),origin,frame)
    return out

def render(quads,cam,target,W=1600,Hh=900,fov=70,ss=2):
    W2,H2=W*ss,Hh*ss; cam=np.array(cam,float); tgt=np.array(target,float)
    f=tgt-cam; f/=np.linalg.norm(f); r=np.cross(f,[0,1,0]); r/=np.linalg.norm(r); u=np.cross(r,f)
    fl=(H2/2)/math.tan(math.radians(fov/2))
    img=np.zeros((H2,W2,3),np.float32)
    for y in range(H2): img[y,:]=np.array((120,167,255))*(1-y/H2)+np.array((196,218,255))*(y/H2)
    zb=np.full((H2,W2),np.inf,np.float32)
    def proj(p):
        d=p-cam; return np.array([W2/2+fl*(d@r)/(d@f), H2/2-fl*(d@u)/(d@f), d@f])
    def translucent(tex): a=tex[...,3]; return bool(((a>0)&(a<255)).any())
    def cdist(verts): c=verts.mean(0)-cam; return -float(c@c)
    opaque=[q for q in quads if not translucent(q[2])]
    glass=sorted([q for q in quads if translucent(q[2])],key=lambda q:cdist(q[0]))   # far to near
    for verts,uvs,tex,shade,bright in opaque+glass:
        blend=translucent(tex)
        V=np.array([proj(v) for v in verts])
        if (V[:,2]<=0.05).any(): continue
        th,tw=tex.shape[:2]
        for tri in ((0,1,2),(0,2,3)):
            a,b,c=V[list(tri)]; ta,tb,tc=uvs[list(tri)]
            area=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
            if abs(area)<1e-9: continue
            x0=int(max(0,math.floor(min(a[0],b[0],c[0])))); x1=int(min(W2-1,math.ceil(max(a[0],b[0],c[0]))))
            y0=int(max(0,math.floor(min(a[1],b[1],c[1])))); y1=int(min(H2-1,math.ceil(max(a[1],b[1],c[1]))))
            if x0>x1 or y0>y1: continue
            xs,ys=np.meshgrid(np.arange(x0,x1+1)+0.5,np.arange(y0,y1+1)+0.5)
            w0=((b[0]-xs)*(c[1]-ys)-(b[1]-ys)*(c[0]-xs))/area; w1=((c[0]-xs)*(a[1]-ys)-(c[1]-ys)*(a[0]-xs))/area; w2=1-w0-w1
            m=(w0>=-1e-6)&(w1>=-1e-6)&(w2>=-1e-6)
            if not m.any(): continue
            z=1/(w0/a[2]+w1/b[2]+w2/c[2])
            uu=(w0*ta[0]/a[2]+w1*tb[0]/b[2]+w2*tc[0]/c[2])*z; vv=(w0*ta[1]/a[2]+w1*tb[1]/b[2]+w2*tc[1]/c[2])*z
            sub=zb[y0:y1+1,x0:x1+1]; m&=z<=sub+1e-5
            ui=np.clip((uu*tw/16).astype(int),0,tw-1); vi=np.clip((vv*th/16).astype(int),0,th-1)
            px=tex[vi,ui]; m&=px[...,3]>0
            if not m.any(): continue
            col=px[...,:3].astype(np.float32)*shade
            reg=img[y0:y1+1,x0:x1+1]
            if blend:                                   # translucent: blend, no depth write (like the game's translucent layer)
                a=(px[...,3:4].astype(np.float32)/255.0)
                reg[m]=(col*a+reg*(1-a))[m]
            else:
                reg[m]=col[m]; sub[m]=z[m]
    out=Image.fromarray(np.clip(img,0,255).astype(np.uint8))
    return out.resize((W,Hh),Image.LANCZOS) if ss>1 else out
