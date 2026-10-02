# Writes the redrawn Phase 2 + 3 textures at the repo's own paths (drop-in). Run from the project root.
import os, sys, shutil
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import p23_v2 as N
T='src/main/resources/assets/encodedlogistics/textures/block/'
def save(im,p): os.makedirs(os.path.dirname(T+p),exist_ok=True); im.save(T+p)
def run(repo_tex):
    paint()
    return check(repo_tex)
def paint():                                                    # the exporters for Phases 1-3 call this last, so these win
    # Phase 2
    for kind,acc,inward in (('ingress_port',N.INGRESS,True),('egress_port',N.EGRESS,False)):
        save(N.port_mouth(acc),f'part/{kind}_mouth.png'); save(N.port_side(acc,inward),f'part/{kind}_side.png'); save(N.port_side(acc,inward,True),f'part/{kind}_side_glow.png')
    save(N.tap_plate(),'part/inventory_tap_plate.png'); save(N.tap_body(),'part/inventory_tap_body.png'); save(N.tap_body(True),'part/inventory_tap_body_glow.png')
    save(N.sensor_panel(),'part/threshold_sensor_panel.png'); save(N.sensor_lamp(False),'part/threshold_sensor_lamp_off.png'); save(N.sensor_lamp(True),'part/threshold_sensor_lamp_on.png')
    save(N.terminal_front(N.GOLD),'fabrication_terminal/front.png')
    # Phase 3
    save(N.fab_front(),'fabricator/front.png'); save(N.fab_side(580),'fabricator/side.png'); save(N.fab_side(590),'fabricator/top.png')
    save(N.fab_lights(False),'fabricator/lights.png'); save(N.fab_lights(True),'fabricator/lights_on.png'); save(N.fab_wash(),'fabricator/wash.png')
    save(N.gateway_face(),'gateway/face.png'); save(N.gateway_face(True,True),'gateway/face_glow.png')
    for k in ('scheduler_core','job_buffer','thread_unit'):
        for m in range(16): save(N.sched_tex(k,m),f'scheduler/{k}_ctm_{m:02d}.png')
    save(N.core_display(8,True),'scheduler/core_display.png'); save(N.core_display(8,False),'scheduler/core_display_unformed.png')
    save(N.thread_glow(),'scheduler/thread_unit_glow.png')
    save(N.terminal_front(N.INDIGO),'schematic_encoder/front.png')
    # Phase 1 touch (shared base): Access Terminal glass gets the same reflection, no stripe
    save(N.terminal_front(None),'access_terminal/front.png')
    # Drive Bay casing: clean repaint of the shipped (grainy) art, same layout
    save(N.drive_bay_front(),'drive_bay/front.png'); save(N.drive_bay_casing(False),'drive_bay/side.png'); save(N.drive_bay_casing(True),'drive_bay/top.png')
def check(repo_tex):
    # sanity: every file must already exist in the repo with the same size
    from PIL import Image
    bad=[]
    for dp,_,fs in os.walk(T):
        for f in [f for f in fs if f.endswith('.png')]:
            rel=os.path.relpath(os.path.join(dp,f),T); rp=os.path.join(repo_tex,rel)
            if not os.path.exists(rp): bad.append(('missing in repo',rel))
            elif Image.open(rp).size!=Image.open(os.path.join(dp,f)).size: bad.append(('size',rel,Image.open(rp).size))
    return bad
if __name__=='__main__':
    bad=run(sys.argv[1]); print('files',sum(len(f) for _,_,f in os.walk(T)),'problems',bad)
