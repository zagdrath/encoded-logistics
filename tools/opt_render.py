import sys,os,json,numpy as np; sys.path.insert(0,'/home/claude/opt/tools'); os.chdir('/home/claude/opt')
exec(open('tools/opt_preview.py').read().split("def scene(")[0])
from mcrender import model_quads, block_quads
B='encodedlogistics:block/'
def shift(Q,d): return [(v+np.array(d),uv,t,sh,br) for v,uv,t,sh,br in Q]
def dev(x,u,name,st='on'):
    o=(x,1+(-13+u-1)/16,0); Q=model_quads(B+f'rack_device/{name}',0,0,o)
    try: Q+=model_quads(B+f'rack_device/{name}_{st}',0,0,o,0)
    except Exception: pass
    return Q
def card(x,u,U,slot,name,on=True):
    tx,ty,w,h=slot; dx=14.45-(tx+w)/8; dy=U-(ty+h)/8
    o=(x+dx/16,1+(-13+u-1)/16+dy/16,1.5/16); Q=model_quads(B+f'rack_card/{name}',0,0,o)
    if on: Q+=model_quads(B+f'rack_card/{name}_on',0,0,o,0)
    return Q
SP=json.load(open('/home/claude/opt/src/main/resources/assets/encodedlogistics/models/block/rack_card/slot_positions.json'))
def full_rack(x):
    o=(x,1,0); Q=model_quads(B+'rack/rack_frame',0,0,o)+model_quads(B+'rack/rack_door_rear_left',0,0,o)+model_quads(B+'rack/rack_door_rear_right',0,0,o)
    Q+=dev(x,42,'optical_multiplexer')+dev(x,41,'optical_amplifier')
    Q+=dev(x,31,'chassis_switch','degraded')
    for i,s in enumerate(SP['switch']['sup']): Q+=card(x,31,10,s,'supervisor_card',on=(i==0))
    for i,(s,c) in enumerate(zip(SP['switch']['line'],['uplink_card','access_card','access_card','routing_card','trunk_card','access_card'])): Q+=card(x,31,10,s,c)
    for i,s in enumerate(SP['switch']['psu'][:3]): Q+=card(x,31,10,s,'chassis_psu')
    Q+=dev(x,19,'blade_enclosure')
    for i,s in enumerate(SP['blade_front'][:13]): Q+=card(x,19,10,s,['compute_blade','compute_blade','memory_blade','fabrication_blade','storage_blade'][i%5])
    Q+=dev(x,11,'optical_transport_shelf')
    for i,s in enumerate(SP['shelf']): Q+=card(x,11,6,s,['controller_card','controller_card','transponder_card','transponder_card','mux_card','amplifier_card','protection_card','transponder_card','transponder_card','transponder_card'][i])
    return Q
F=floor(-3,5,-4,3)
Q=full_rack(0)
render(Q+F,(0.5,1.5,-2.6),(0.5,1.4,0.5),W=900,Hh=1100,fov=60,ss=1).save(OUT+'full_rack.png')
render(Q+F,(0.5,0.85,-1.3),(0.5,0.85,0.5),W=900,Hh=600,fov=45,ss=1).save(OUT+'transport_shelf_close.png')
render(Q+F,(0.5,1.95,-1.5),(0.5,1.95,0.5),W=900,Hh=700,fov=50,ss=1).save(OUT+'chassis_switch_close.png')
Q2=block_quads('optical_multiplexer',{'facing':'north','state':'on'},(2,0,0),0)+block_quads('optical_amplifier',{'facing':'east','state':'on'},(3.4,0,0),0)
render(Q+Q2+F,(2.0,1.2,-2.3),(2.6,0.5,0.5),W=1000,Hh=650,fov=55,ss=1).save(OUT+'blocks.png')
