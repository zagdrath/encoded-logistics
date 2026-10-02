# Item display transforms that keep an off-centre model (a cable part or facade, modelled against one face of the block)
# centred in its slot, in item frames and on the ground: each transform gets the translation that moves the model's
# bounding-box centre back to the middle after its rotation and scale. Minecraft applies translate, then rotate (X, then
# Y, then Z: Quaternionf.rotationXYZ), then scale, about the block's centre; translations are in pixels (1/16 block).
import json, math, os

CENTRED=('gui','ground','fixed')

def _bounds(path):
    d=json.load(open(path,encoding='utf-8'))
    if 'elements' not in d and d.get('parent','').startswith('encodedlogistics:'):
        root=path[:path.index('/models/')+len('/models/')]
        return _bounds(root+d['parent'].split(':',1)[1]+'.json')
    lo=[min(min(e['from'][i],e['to'][i]) for e in d['elements']) for i in range(3)]
    hi=[max(max(e['from'][i],e['to'][i]) for e in d['elements']) for i in range(3)]
    return lo,hi

def _rotate(v,rot):
    x,y,z=v
    ax,ay,az=(math.radians(a) for a in rot)
    # rotationXYZ = Rx * Ry * Rz: Z first, then Y, then X
    x,y=x*math.cos(az)-y*math.sin(az),x*math.sin(az)+y*math.cos(az)
    x,z=x*math.cos(ay)+z*math.sin(ay),-x*math.sin(ay)+z*math.cos(ay)
    y,z=y*math.cos(ax)-z*math.sin(ax),y*math.sin(ax)+z*math.cos(ax)
    return x,y,z

def centred(display,model_path):
    """display with a translation on its gui, ground and fixed transforms that centres the model at model_path."""
    lo,hi=_bounds(model_path)
    c=[(lo[i]+hi[i])/2-8 for i in range(3)]
    out={}
    for k,t in display.items():
        t=dict(t)
        if k in CENTRED:
            s=t.get('scale',[1,1,1])
            r=_rotate([c[i]*s[i] for i in range(3)],t.get('rotation',[0,0,0]))
            t['translation']=[round(-a,2)+0.0 for a in r]
            # keep the key order the other transforms use: rotation, translation, scale
            t={k2:t[k2] for k2 in ('rotation','translation','scale') if k2 in t}
        out[k]=t
    return out
