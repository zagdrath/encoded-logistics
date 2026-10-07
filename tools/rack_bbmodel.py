# Writes Blockbench (.bbmodel, java_block, per-face UV) sources from the same element lists as the exported JSON.
import json, uuid, base64, io, os
from PIL import Image
def _u(): return str(uuid.uuid4())
def bbmodel(name,model,tex_paths,pivot=None,out_dir='blockbench'):
    """model: the MC model dict; tex_paths: {'#key': png path}. Pivot -> group origin (door hinge) for Blockbench."""
    texs=[]; idx={}
    for i,(k,p) in enumerate(tex_paths.items()):
        im=Image.open(p); buf=io.BytesIO(); im.save(buf,'PNG')
        texs.append({'name':os.path.basename(p),'id':str(i),'uuid':_u(),'relative_path':'../'+p.replace('\\','/'),
                     'width':im.width,'height':im.height,'uv_width':16,'uv_height':16,'particle':i==0,
                     'render_mode':'default','source':'data:image/png;base64,'+base64.b64encode(buf.getvalue()).decode()})
        idx[k.lstrip('#')]=i
    els=[]
    for e in model['elements']:
        faces={}
        for f,fd in e['faces'].items():
            faces[f]={'uv':fd.get('uv',[0,0,16,16]),'texture':idx.get(fd['texture'].lstrip('#'),0)}
            if 'rotation' in fd: faces[f]['rotation']=fd['rotation']
        els.append({'name':e.get('name','cube'),'type':'cube','uuid':_u(),'from':e['from'],'to':e['to'],
                    'origin':pivot or [8,8,8],'faces':faces,'box_uv':False,'rescale':False,'shade':e.get('shade_direction_override')!='up'})
    group={'name':name,'origin':pivot or [8,8,8],'uuid':_u(),'export':True,'isOpen':True,'children':[e['uuid'] for e in els]}
    bb={'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':name,'model_identifier':'',
        'visible_box':[1,1,0],'resolution':{'width':16,'height':16},'elements':els,'outliner':[group],'textures':texs,
        'display':model.get('display',{})}
    os.makedirs(out_dir,exist_ok=True); json.dump(bb,open(f'{out_dir}/{name}.bbmodel','w'))
