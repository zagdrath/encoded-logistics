# Encoded Logistics - Phase 4 asset + data exporter. Run from the project root: python tools/p4_export.py
# Writes the Phase 4 items, blocks, parts (with the planes' connected-texture variants), recipes, tags and loot tables;
# checks that en_us.json has every Phase 4 key.
import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
from item_display import centred
from el_style import *
from items_p4 import ITEMS
import blocks_p4 as B
A='src/main/resources/assets/encodedlogistics/'; D='src/main/resources/data/'
T=A+'textures/'; M=A+'models/'; RL=lambda p:'encodedlogistics:'+p
GLOW={'neoforge_data':{'block_light':15,'sky_light':15},'shade':False}
def mc(ft,interp): return '{\n  "animation": {\n    "frametime": %d,\n    "interpolate": %s\n  }\n}\n'%(ft,'true' if interp else 'false')
def save(im,p): os.makedirs(os.path.dirname(p),exist_ok=True); im.save(p)
def jd(o,p): os.makedirs(os.path.dirname(p),exist_ok=True); json.dump(o,open(p,'w',newline='\n'),indent=1)
def merge_tag(path,values):                                     # shared tags list other phases' entries too: add, don't replace
    have=json.load(open(path))['values'] if os.path.exists(path) else []
    jd({'values':have+[v for v in values if v not in have]},path)
def face(t,uv): return {'texture':t,'uv':uv}
def cube(faces): return {'from':[0,0,0],'to':[16,16,16],'faces':faces}
PU={'lit':[0,0,2,2],'mid':[2,0,4,2],'dark':[4,0,6,2],'shadow':[6,0,8,2]}
ROT4={'north':{},'east':{'y':90},'south':{'y':180},'west':{'y':270}}
ROT6={**ROT4,'up':{'x':270},'down':{'x':90}}
def items():
    for n,fn in ITEMS.items():
        save(fn(),f'{T}item/{n}.png'); jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{n}')}},f'{M}item/{n}.json')
    simple=['optical_transceiver','fuzzy_match_module','redstone_control_module']
    for n in simple: jd({'model':{'type':'minecraft:model','model':RL(f'item/{n}')}},f'{A}items/{n}.json')
    # Link Card: written when it carries a stored address (component encodedlogistics:link_address)
    jd({'model':{'type':'minecraft:condition','property':'minecraft:has_component','component':'encodedlogistics:link_address',
        'on_true':{'type':'minecraft:model','model':RL('item/link_card_written')},'on_false':{'type':'minecraft:model','model':RL('item/link_card')}}},f'{A}items/link_card.json')
    # Handheld Terminal: state from component encodedlogistics:handheld_link_state (unlinked / linked / out_of_range)
    jd({'model':{'type':'minecraft:select','property':'minecraft:component','component':'encodedlogistics:handheld_link_state',
        'cases':[{'when':'linked','model':{'type':'minecraft:model','model':RL('item/handheld_terminal_linked')}},
                 {'when':'out_of_range','model':{'type':'minecraft:model','model':RL('item/handheld_terminal_out_of_range')}}],
        'fallback':{'type':'minecraft:model','model':RL('item/handheld_terminal')}}},f'{A}items/handheld_terminal.json')
def relay():
    P=T+'block/relay_antenna/'
    save(B.relay_front(),P+'front.png'); save(B.relay_side(),P+'side.png'); save(B.relay_top(),P+'top.png'); save(B.mast_tex(),P+'mast.png')
    save(B.tip_blink(),P+'tip_blink.png'); open(P+'tip_blink.png.mcmeta','w',newline='\n').write(mc(2,False))
    tx={k:RL(f'block/relay_antenna/{k}') for k in ('front','side','top','mast','tip_blink')}; tx['particle']=tx['side']
    base=cube({'north':face('#front',[0,0,16,16]),'south':face('#side',[0,0,16,16]),'east':face('#side',[0,0,16,16]),'west':face('#side',[0,0,16,16]),
               'up':face('#top',[0,0,16,16]),'down':face('#side',[0,0,16,16])})
    pole={'from':[7,16,7],'to':[9,30,9],'faces':{f:face('#mast',[0,0,2,14]) for f in ('north','south','east','west')}}
    arm1={'from':[4,22,7.5],'to':[12,23,8.5],'faces':{f:face('#mast',[2,4,10,5]) for f in ('north','south','up','down','east','west')}}
    arm2={'from':[7.5,26,5],'to':[8.5,27,11],'faces':{f:face('#mast',[2,4,8,5]) for f in ('north','south','up','down','east','west')}}
    tip={'from':[7,30,7],'to':[9,32,9],'faces':{f:face('#mast',[12,0,14,2]) for f in ('north','south','east','west','up')}}
    tipg={'from':[7,30,7],'to':[9,32,9],'faces':{f:face('#tip_blink',[12,0,14,2]) for f in ('north','south','east','west','up')},**GLOW}
    off=[base,pole,arm1,arm2,tip]; on=off+[tipg]
    jd({'parent':'minecraft:block/block','textures':tx,'elements':off},f'{M}block/relay_antenna.json')
    jd({'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':on},f'{M}block/relay_antenna_online.json')
    jd({'variants':{f'facing={f},online={o}':{'model':RL('block/relay_antenna'+('_online' if o=='true' else '')),**r} for f,r in ROT4.items() for o in ('false','true')}},f'{A}blockstates/relay_antenna.json')
    jd({'model':{'type':'minecraft:model','model':RL('block/relay_antenna')}},f'{A}items/relay_antenna.json')
def bridge():
    P=T+'block/network_bridge/'
    save(B.bridge_front(),P+'front.png'); save(B.bridge_side(),P+'side.png'); save(B.bridge_glow('idle'),P+'port_idle.png')
    save(B.bridge_glow('active'),P+'port_active.png'); open(P+'port_active.png.mcmeta','w',newline='\n').write(mc(3,True))
    tx={k:RL(f'block/network_bridge/{k}') for k in ('front','side','port_idle','port_active')}
    tx.update({'leds':RL('block/leds'),'leds_glow':RL('block/leds_glow'),'particle':RL('block/network_bridge/side')})
    base=cube({'north':face('#front',[0,0,16,16]),**{f:face('#side',[0,0,16,16]) for f in ('south','east','west','up','down')}})
    led=lambda t,uv,glow=False:{'from':[2,13,-0.02],'to':[4,14,0],'faces':{'north':face(t,uv)},**(GLOW if glow else {})}
    port=lambda t:{'from':[0,0,-0.01],'to':[16,16,-0.01],'faces':{'north':face(t,[0,0,16,16])},**GLOW}
    models={'network_bridge':[base,led('#leds',[6,0,8,1]),led('#leds_glow',[6,0,8,1],True)],                       # unlinked: amber light
            'network_bridge_linked':[base,port('#port_idle'),led('#leds',[2,0,4,1]),led('#leds_glow',[2,0,4,1],True)],
            'network_bridge_active':[base,port('#port_active'),led('#leds',[2,0,4,1]),led('#leds_glow',[2,0,4,1],True)]}
    for n,e in models.items(): jd({'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':e},f'{M}block/{n}.json')
    st={'unlinked':'network_bridge','linked_idle':'network_bridge_linked','linked_active':'network_bridge_active'}
    jd({'variants':{f'facing={f},status={s}':{'model':RL('block/'+m),**r} for f,r in ROT6.items() for s,m in st.items()}},f'{A}blockstates/network_bridge.json')
    jd({'model':{'type':'minecraft:model','model':RL('block/network_bridge_linked')}},f'{A}items/network_bridge.json')
def part_item(name,model):     # centred in the slot, though the part sits against one face of its block
    jd({'parent':RL(model),'display':centred({'gui':{'rotation':[30,200,0],'scale':[0.8,0.8,0.8]},'ground':{'scale':[0.4,0.4,0.4]},
        'fixed':{'rotation':[0,180,0],'scale':[0.6,0.6,0.6]},'firstperson_righthand':{'rotation':[0,200,0],'scale':[0.45,0.45,0.45]},
        'thirdperson_righthand':{'rotation':[75,200,0],'translation':[0,2.5,0],'scale':[0.4,0.4,0.4]}},f'{M}{model}.json')},f'{M}item/{name}.json')
    jd({'model':{'type':'minecraft:model','model':RL(f'item/{name}')}},f'{A}items/{name}.json')
def p2p():
    P=T+'block/part/p2p/'
    for k in B.TYPES:
        for d in ('in','out'):
            save(B.p2p_face(k,d),P+f'{k}_{d}.png'); save(B.p2p_face(k,d,True),P+f'{k}_{d}_glow.png')
            tx={'face':RL(f'block/part/p2p/{k}_{d}'),'glow':RL(f'block/part/p2p/{k}_{d}_glow'),'parts':RL('block/part/parts'),'particle':RL(f'block/part/p2p/{k}_{d}')}
            body={'from':[3,3,0],'to':[13,13,1.5],'faces':{'north':face('#face',[3,3,13,13]),'south':face('#parts',PU['dark']),'east':face('#parts',PU['mid']),
                  'west':face('#parts',PU['mid']),'up':face('#parts',PU['lit']),'down':face('#parts',PU['shadow'])}}
            collar={'from':[5,5,1.5],'to':[11,11,3],'faces':{f:face('#parts',PU['mid']) for f in ('east','west','up','down')}}
            stub={'from':[6,6,3],'to':[10,10,5],'faces':{f:face('#parts',PU['dark']) for f in ('east','west','up','down')}}
            gl={'from':[3,3,-0.02],'to':[13,13,-0.02],'faces':{'north':face('#glow',[3,3,13,13])},**GLOW}
            jd({'parent':'minecraft:block/block','textures':tx,'elements':[body,collar,stub]},f'{M}part/p2p_{k}_{d}.json')
            jd({'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':[body,collar,stub,gl]},f'{M}part/p2p_{k}_{d}_linked.json')
    part_item('point_to_point_link','part/p2p_items_in_linked')
def planes():
    for kind,acc,seed in (('collector_plane',B.COLLECT,431),('deployer_plane',B.DEPLOY,441)):
        P=T+f'block/part/{kind}/'
        for m in range(16): save(B.plane_tex(acc,m,seed),P+f'ctm_{m:02d}.png')
        save(B.plane_flash(acc),P+'flash.png'); open(P+'flash.png.mcmeta','w',newline='\n').write(mc(2,False)); save(B.plane_edge(acc),P+'edge.png')
        tx={'face':RL(f'block/part/{kind}/ctm_00'),'edge':RL(f'block/part/{kind}/edge'),'flash':RL(f'block/part/{kind}/flash'),
            'parts':RL('block/part/parts'),'particle':RL(f'block/part/{kind}/ctm_00')}
        plate={'from':[0,0,0],'to':[16,16,2],'faces':{'north':face('#face',[0,0,16,16]),'south':face('#parts',PU['dark']),
               'east':{**face('#edge',[0,0,16,2]),'rotation':90},'west':{**face('#edge',[0,0,16,2]),'rotation':90},'up':face('#edge',[0,0,16,2]),'down':face('#edge',[0,0,16,2])}}
        stub={'from':[6,6,2],'to':[10,10,5],'faces':{f:face('#parts',PU['mid']) for f in ('east','west','up','down')}}
        fl={'from':[0,0,-0.02],'to':[16,16,-0.02],'faces':{'north':face('#flash',[0,0,16,16])},**GLOW}
        jd({'parent':'minecraft:block/block','textures':tx,'elements':[plate,stub]},f'{M}part/{kind}.json')
        jd({'parent':'minecraft:block/block','render_type':'minecraft:translucent','textures':tx,'elements':[plate,stub,fl]},f'{M}part/{kind}_active.json')
        # one model per mask (the cable model picks it from the neighbouring planes): the base model with the face swapped
        for m in range(16):
            mtx={'face':RL(f'block/part/{kind}/ctm_{m:02d}'),'particle':RL(f'block/part/{kind}/ctm_{m:02d}')}
            jd({'parent':RL(f'part/{kind}'),'textures':mtx},f'{M}part/{kind}/ctm_{m:02d}.json')
            jd({'parent':RL(f'part/{kind}_active'),'textures':mtx},f'{M}part/{kind}/ctm_{m:02d}_active.json')
        part_item(kind,f'part/{kind}')
def recipes():
    R=D+'encodedlogistics/recipe/'; E=RL; V=lambda n:'minecraft:'+n
    def res(i,c=1): return {'id':i,'count':c}
    def shapeless(n,ings,r,c=1): jd({'type':'minecraft:crafting_shapeless','category':'misc','ingredients':ings,'result':res(r,c)},R+n+'.json')
    def shaped(n,pat,key,r,c=1): jd({'type':'minecraft:crafting_shaped','category':'misc','pattern':pat,'key':key,'result':res(r,c)},R+n+'.json')
    shapeless('optical_transceiver',[E('fiberglass'),E('logic_die'),V('glass'),E('copper_foil'),E('gallium_ingot')],E('optical_transceiver'))
    shapeless('link_card',[E('schematic_card'),E('memory_die')],E('link_card'),2)
    shapeless('handheld_terminal',[E('access_terminal'),E('optical_transceiver'),E('tantalum_capacitor'),E('tantalum_capacitor'),E('circuit_substrate')],E('handheld_terminal'))
    shapeless('fuzzy_match_module',[E('circuit_substrate'),E('memory_die'),V('string')],E('fuzzy_match_module'))
    shapeless('redstone_control_module',[E('circuit_substrate'),E('logic_die'),V('redstone_torch')],E('redstone_control_module'))
    shaped('relay_antenna',[' B ','POP','FBF'],{'B':V('iron_bars'),'P':E('processor_die'),'O':E('optical_transceiver'),'F':E('ferrite')},E('relay_antenna'))
    shaped('network_bridge',['TOT','PCM','EOE'],{'T':E('tantalum_ingot'),'E':V('ender_eye'),'O':E('optical_transceiver'),'P':E('processor_die'),'C':E('circuit_substrate'),'M':E('memory_die')},E('network_bridge'))
    shapeless('point_to_point_link',[E('optical_transceiver'),E('logic_die'),E('network_cable')],E('point_to_point_link'),2)
    shapeless('collector_plane',[E('logic_die'),E('circuit_substrate'),V('iron_pickaxe'),V('hopper'),E('ferrite')],E('collector_plane'))
    shapeless('deployer_plane',[E('logic_die'),E('circuit_substrate'),V('dispenser'),E('ferrite')],E('deployer_plane'))
    merge_tag(D+'encodedlogistics/tags/item/port_modules.json',[E('filter_module'),E('throughput_module'),E('fuzzy_match_module'),E('redstone_control_module')])
    jd({'values':[E('filter_module'),E('fuzzy_match_module')]},D+'encodedlogistics/tags/item/plane_modules.json')
def drops():                                                    # the Relay Antenna and Network Bridge drop themselves, need a pickaxe
    for b in ('relay_antenna','network_bridge'):
        p=f'{D}encodedlogistics/loot_table/blocks/{b}.json'; os.makedirs(os.path.dirname(p),exist_ok=True)
        json.dump({'type':'minecraft:block','pools':[{'condition':{'type':'minecraft:survives_explosion'},
            'entries':[{'type':'minecraft:item','name':RL(b)}],'rolls':1}],'random_sequence':RL(f'blocks/{b}')},open(p,'w',newline='\n'),indent=2)
    merge_tag(D+'minecraft/tags/block/mineable/pickaxe.json',[RL('relay_antenna'),RL('network_bridge')])
def lang():
    L={'item.encodedlogistics.optical_transceiver':'Optical Transceiver','item.encodedlogistics.link_card':'Link Card',
       'item.encodedlogistics.handheld_terminal':'Handheld Terminal','item.encodedlogistics.fuzzy_match_module':'Fuzzy Match Module',
       'item.encodedlogistics.redstone_control_module':'Redstone Control Module','block.encodedlogistics.relay_antenna':'Relay Antenna',
       'block.encodedlogistics.network_bridge':'Network Bridge','item.encodedlogistics.point_to_point_link':'Point-to-Point Link',
       'item.encodedlogistics.collector_plane':'Collector Plane','item.encodedlogistics.deployer_plane':'Deployer Plane',
       'gui.encodedlogistics.handheld_terminal':'Handheld Terminal','gui.encodedlogistics.handheld.linked':'Linked',
       'gui.encodedlogistics.handheld.unlinked':'Not linked','gui.encodedlogistics.handheld.out_of_range':'Out of range',
       'gui.encodedlogistics.relay.range':'Range: %s m','gui.encodedlogistics.relay.transceivers':'Transceivers',
       'gui.encodedlogistics.relay.linked':'Linked terminals','gui.encodedlogistics.relay.entry':'%s - %s m',
       'gui.encodedlogistics.bridge.status.unlinked':'Not linked','gui.encodedlogistics.bridge.status.linked':'Linked',
       'gui.encodedlogistics.bridge.status.offline':'Partner offline','gui.encodedlogistics.bridge.partner':'Partner: %s, %s, %s',
       'gui.encodedlogistics.p2p.type':'Type',
       'gui.encodedlogistics.p2p.type.items':'Items','gui.encodedlogistics.p2p.type.energy':'Energy','gui.encodedlogistics.p2p.type.redstone':'Redstone',
       'gui.encodedlogistics.p2p.type.lanes':'Lanes','gui.encodedlogistics.p2p.input':'Input','gui.encodedlogistics.p2p.output':'Output',
       'gui.encodedlogistics.p2p.partner':'Partner: %s, %s, %s','gui.encodedlogistics.deployer.mode.place':'Place blocks',
       'gui.encodedlogistics.deployer.mode.drop':'Drop items','tooltip.encodedlogistics.link_card.address':'Stored: %s at %s, %s, %s',
       'tooltip.encodedlogistics.link_card.hint':'Sneak-use on a Bridge or Point-to-Point Link to store it, then use on the partner',
       'tooltip.encodedlogistics.handheld.energy':'%s / %s FE','tooltip.encodedlogistics.module.fuzzy':'Match by tag or damage range',
       'tooltip.encodedlogistics.module.redstone':'Ports respond to redstone: high, low or pulse',
       'gui.encodedlogistics.bridge.lanes':'Lanes %s / %s','gui.encodedlogistics.bridge.hint':'Pair it with a Link Card',
       'gui.encodedlogistics.relay.none':'No terminals in range','gui.encodedlogistics.p2p.unpair':'Unpair',
       'gui.encodedlogistics.p2p.status.linked':'Linked','gui.encodedlogistics.p2p.status.outputs':'Linked to %s outputs',
       'gui.encodedlogistics.p2p.status.offline':'Partner offline','gui.encodedlogistics.p2p.status.unlinked':'Not linked',
       'gui.encodedlogistics.p2p.hint':'Pair it with a Link Card','gui.encodedlogistics.p2p.input.info':'Takes from the block it faces',
       'gui.encodedlogistics.p2p.output.info':'Gives to the block it faces','gui.encodedlogistics.p2p.locked':'Unpair to change',
       'gui.encodedlogistics.deployer.mode.place.info':'Places the first listed block the network has',
       'gui.encodedlogistics.deployer.mode.drop.info':'Drops one of the first listed item the network has',
       'gui.encodedlogistics.deployer.filter':'Deploy list','gui.encodedlogistics.deployer.filter.info':'Earlier entries go first',
       'gui.encodedlogistics.collector.filter':'Filter','gui.encodedlogistics.collector.filter.info':'Only what passes is broken or collected',
       'gui.encodedlogistics.collector.filter.no_module':'Needs a Filter or Fuzzy Match Module; without one it takes everything',
       'gui.encodedlogistics.collector.module':'Module','gui.encodedlogistics.collector.module.info':'Filter Module or Fuzzy Match Module',
       'gui.encodedlogistics.fuzzy.exact':'Exact item','gui.encodedlogistics.fuzzy.any_damage':'Any damage',
       'gui.encodedlogistics.fuzzy.damage':'Damage %s-%s%%','gui.encodedlogistics.fuzzy.matches':'Matches: %s',
       'gui.encodedlogistics.fuzzy.hint':'Right-click to change',
       'message.encodedlogistics.link_card.stored':'Address stored','message.encodedlogistics.link_card.cleared':'Link Card cleared',
       'message.encodedlogistics.link_card.paired':'Paired','message.encodedlogistics.link_card.gone':'The stored partner is gone',
       'message.encodedlogistics.link_card.same':'That is the stored one','message.encodedlogistics.link_card.mismatch':'Bridges pair only with Bridges, links only with links',
       'message.encodedlogistics.link_card.dimension':'Bridges in different dimensions are turned off in the config',
       'message.encodedlogistics.link_card.p2p_dimension':'Point-to-Point Links pair only within one dimension',
       'message.encodedlogistics.link_card.other_type':'They carry different things','message.encodedlogistics.link_card.same_direction':'Pair an input with an output',
       'message.encodedlogistics.handheld.linked':'Linked to network #%s','message.encodedlogistics.handheld.no_network':'Not on a working network',
       'message.encodedlogistics.handheld.empty':'Battery empty',
       'tooltip.encodedlogistics.handheld.network':'Network #%s (%s)','tooltip.encodedlogistics.handheld.hint':'Use on a Relay Antenna or Network Controller to link',
       'tooltip.encodedlogistics.info.optical_transceiver':'An optical module for wireless and long-range links. Each one in a Relay Antenna adds %s blocks of range.',
       'tooltip.encodedlogistics.info.link_card':'Pairs Network Bridges and Point-to-Point Links. Sneak-use on one to store it, then use on its partner. Sneak-use in the air to clear.',
       'tooltip.encodedlogistics.info.handheld_terminal':'A terminal that works in range of its network\'s Relay Antennas. Charges in FE chargers, or held against a Capacitor Bank.',
       'tooltip.encodedlogistics.info.fuzzy_match_module':'Port and Collector Plane filters match by tag or damage: right-click a filter entry to pick.',
       'tooltip.encodedlogistics.info.redstone_control_module':'Unlocks a port\'s redstone modes: active with signal, without signal, or once per pulse.',
       'tooltip.encodedlogistics.info.relay_antenna':'Wireless coverage for Handheld Terminals linked to its network: %s blocks, more with Optical Transceivers. Uses one lane.',
       'tooltip.encodedlogistics.info.network_bridge':'Joins two networks, any distance apart, with a %s-lane link. Pair two with a Link Card; both ends must be loaded.',
       'tooltip.encodedlogistics.info.point_to_point_link':'Passes items, energy, redstone or lanes straight between paired endpoints. Pick the type and direction, then pair with a Link Card.',
       'tooltip.encodedlogistics.info.collector_plane':'Breaks the block in front (as an iron pickaxe) and collects dropped items into the network. Joins neighbouring planes. Uses one lane.',
       'tooltip.encodedlogistics.info.deployer_plane':'Places blocks or drops items from the network, from its list. Joins neighbouring planes. Uses one lane.',
       'encodedlogistics.configuration.reach':'Reach','encodedlogistics.configuration.relayBaseRange':'Relay Base Range',
       'encodedlogistics.configuration.relayRangePerTransceiver':'Relay Range per Transceiver','encodedlogistics.configuration.relayDrain':'Relay Drain',
       'encodedlogistics.configuration.handheldCapacity':'Handheld Capacity','encodedlogistics.configuration.handheldDrainPerSecond':'Handheld Drain per Second',
       'encodedlogistics.configuration.handheldEnergyPerItem':'Handheld Energy per Item','encodedlogistics.configuration.handheldChargeRate':'Handheld Charge Rate',
       'encodedlogistics.configuration.bridgeLanes':'Bridge Lanes','encodedlogistics.configuration.bridgeDrain':'Bridge Drain',
       'encodedlogistics.configuration.bridgeCrossDimension':'Bridge Cross Dimension','encodedlogistics.configuration.bridgeChunkLoading':'Bridge Chunk Loading',
       'encodedlogistics.configuration.p2pLanes':'Point-to-Point Lanes','encodedlogistics.configuration.p2pDrain':'Point-to-Point Drain',
       'encodedlogistics.configuration.p2pItemsPerOperation':'Point-to-Point Items per Operation','encodedlogistics.configuration.p2pEnergyPerTick':'Point-to-Point Energy per Tick',
       'encodedlogistics.configuration.collectorTicksPerHardness':'Collector Ticks per Hardness','encodedlogistics.configuration.deployerInterval':'Deployer Interval',
    }
    have=json.load(open(A+'lang/en_us.json',encoding='utf-8'))      # en_us.json holds them; report what's missing or differs
    for k,v in L.items():
        if have.get(k)!=v: print('en_us.json:',k,'should be',repr(v))
    return len(L)
if __name__=='__main__':
    items(); relay(); bridge(); p2p(); planes(); recipes(); drops(); n=lang(); print('lang',n,'files',sum(len(f) for _,_,f in os.walk('src')))
