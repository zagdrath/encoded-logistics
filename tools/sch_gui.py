# Schematic Terminal GUI mockups on the game's real terminal layout (screens/terminal/base_terminal.json) with a text-fit
# checker: every string must fit its area WITHOUT truncation and must not overlap another string. Exit code 1 on any issue.
import sys,os; sys.path.insert(0,'/home/claude/rack2/tools'); os.chdir('/home/claude/sch')
src=open('/home/claude/rack2/tools/gui2_preview.py').read()
exec(src.split("P='/home/claude/rack2/previews/'")[0].replace("ROOTS=['/home/claude/rack2/","ROOTS=['/home/claude/sch/src/main/resources/assets/encodedlogistics/textures/','/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/','/home/claude/rack2/"))
from PIL import Image
R='/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/'
TEXT,MUTED,DIS,ACC,WARN,RED,BLUE='#F0F0F0','#B4B4B4','#7A7A7A','#00D992','#E8C24A','#FF6B6B','#50C2EC'
class Canvas:
    def __init__(s,im): s.im=im; s.boxes=[]; s.issues=[]
    def text(s,x,y,t,col,area,right=None,hint=False):
        w=tw(t); x=right-w if right is not None else x; box=(x,y,x+w,y+8)
        if '...' in t and not hint: s.issues.append(f'TRUNCATED {t!r}')
        if box[0]<area[0] or box[2]>area[2] or box[1]<area[1] or box[3]>area[3]: s.issues.append(f'OVERFLOW {t!r} {box} area {area}')
        for (b,t2) in s.boxes:
            if box[0]<b[2] and b[0]<box[2] and box[1]<b[3] and b[1]<box[3]: s.issues.append(f'OVERLAP {t!r} / {t2!r}')
        s.boxes.append((box,t)); T(s.im,x,y,t,col)
def png16(p):
    try: return Image.open(R+p+'.png').convert('RGBA').crop((0,0,16,16))
    except Exception: return None
top=tex('gui/terminal/top.png').crop((0,0,195,19)); row=tex('gui/terminal/row.png').crop((0,0,195,18)); bot=tex('gui/terminal/bottom.png').crop((0,0,195,99))
OFF,TABH=24,12
def build(tab):
    im=Image.new('RGBA',(OFF+195,TABH+19+108+12+99),(0,0,0,0)); c=Canvas(im); X0,Y0=OFF,TABH
    im.alpha_composite(top,(X0,Y0))
    for i in range(6): im.alpha_composite(row if tab==0 else tex('gui/terminal/row_list.png').crop((0,0,195,18)),(X0,Y0+19+i*18))
    im.alpha_composite(tex('gui/terminal/summary_section.png').crop((0,0,195,12)),(X0,Y0+127)); im.alpha_composite(bot,(X0,Y0+139))
    for i,label in enumerate(('Schematics','Jobs')):
        tx=X0+8+i*65; im.alpha_composite(tex('gui/sprites/schematic/'+('tab_active' if i==tab else 'tab_inactive')+'.png'),(tx,0 if i==tab else 1))
        c.text(tx+32-tw(label)//2,3,label,TEXT if i==tab else MUTED,(tx+2,0,tx+62,13))
    tb=tex('gui/sprites/terminal/toolbar.png'); th=4*20+4
    im.alpha_composite(tb.crop((0,0,24,th-3)),(X0-24,Y0+16)); im.alpha_composite(tb.crop((0,125,24,128)),(X0-24,Y0+16+th-3))
    for k,ic in enumerate(('icon_sort_name','icon_dir_asc' if tab==0 else 'icon_dir_desc','icon_type_all','icon_height_medium')):
        bx,by=X0-21,Y0+19+k*20; im.alpha_composite(tex('gui/sprites/terminal/button.png'),(bx,by)); im.alpha_composite(tex('gui/sprites/terminal/'+ic+'.png'),(bx+1,by+1))
    c.text(X0+8,Y0+5,'Schematic Terminal' if tab==0 else 'Jobs: RACKSCHED',TEXT,(X0+8,Y0,X0+115,Y0+19))
    im.alpha_composite(tex('gui/sprites/terminal/search_field.png'),(X0+119,Y0+4)); c.text(X0+123,Y0+6,'Search...',DIS,(X0+119,Y0+4,X0+183,Y0+16),hint=True)
    im.alpha_composite(tex('gui/sprites/controller/scroll_thumb.png'),(X0+176,Y0+19))
    c.text(X0+9,Y0+145,'Inventory',MUTED,(X0+8,Y0+139,X0+172,Y0+156))
    if tab==0:
        y=Y0+19
        for (ic,name,loc,sch,badges) in (('block/fabricator/front','FAB01','212 64 -88',['encoded_schematic_crafting']*5,[]),
                                          ('block/gateway/face','GW02 > Etcher','218 64 -90',['encoded_schematic_processing']*2,['gateway']),
                                          ('item/fabrication_server','FABSRV01','RACK01 U14',['encoded_schematic_crafting']*4+['encoded_schematic_processing']*3,['parallel','conflict'])):
            im.alpha_composite(tex('gui/terminal/row_header.png').crop((0,0,195,18)),(X0,y))
            i16=png16(ic)
            if i16: im.alpha_composite(i16,(X0+10,y+1))
            bx=X0+170
            for b in reversed(badges): im.alpha_composite(tex(f'gui/sprites/schematic/badge_{b}.png'),(bx-7,y+5)); bx-=9
            c.text(X0+28,y+5,name,TEXT,(X0+28,y,bx,y+18))
            lx=X0+28+tw(name)+5
            if lx+tw(loc)<=bx-2: c.text(lx,y+5,loc,DIS,(X0+28,y,bx,y+18))     # the location only when it fits whole (else in the tooltip)
            y+=18
            for s,n in enumerate(sch[:9]):
                i16=png16('item/'+n)
                if i16: im.alpha_composite(i16,(X0+9+s*18,y+1))
            y+=18
        c.text(X0+9,Y0+129,'11 outputs  18/32 slots used',MUTED,(X0+8,Y0+127,X0+172,Y0+139))
    else:
        y=Y0+19
        def job(jid,out,st,p,meta,y):
            col={'Active':BLUE,'Stalled':WARN,'Queued':MUTED}[st]; ix=X0+10; nx=ix+tw('0000')+4; sw=tw(st)
            c.text(ix,y+1,jid,DIS,(X0+9,y,X0+171,y+18)); c.text(0,y+1,st,col,(X0+9,y,X0+171,y+18),right=X0+169)
            c.text(nx,y+1,out,TEXT,(nx,y,X0+169-sw-3,y+18))
            im.alpha_composite(tex('gui/sprites/jobs/progress_bg.png'),(nx,y+12))
            if p: im.alpha_composite(tex('gui/sprites/jobs/progress_'+('stalled' if st=='Stalled' else 'fill')+'.png').crop((0,0,max(1,int(40*p)),5)),(nx,y+12))
            c.text(nx+44,y+10,meta,DIS,(nx+44,y,X0+171,y+18)); return y+18
        y=job('0012','Logic Die x64','Active',0.62,'MIDRANGE01 4:12',y)
        y=job('0013','SAN x1','Stalled',0.31,'RACKSCHED 9:40',y)
        ind=X0+10+tw('0000')+4
        for (tl,ss,nm,q,why) in (('branch','done','Processor Die x4','4/4',''),('branch','stalled','Transceiver x2','0/2','Etcher output full'),('last','waiting','SAN x1','0/1','')):
            im.alpha_composite(tex(f'gui/sprites/jobs/tree_{tl}.png'),(ind,y-1)); im.alpha_composite(tex(f'gui/sprites/jobs/state_{ss}.png'),(ind+9,y+1))
            c.text(0,y+1,q,MUTED,(X0+9,y,X0+171,y+9),right=X0+169); c.text(ind+18,y+1,nm,WARN if ss=='stalled' else TEXT,(ind+18,y,X0+169-tw(q)-3,y+9)); y+=9
            if why:
                im.alpha_composite(tex('gui/sprites/jobs/tree_pipe.png'),(ind,y-1)); im.alpha_composite(tex('gui/sprites/schematic/locate.png'),(X0+161,y))
                c.text(ind+18,y+1,why,WARN,(ind+18,y,X0+159,y+9)); y+=9
        job('0014','Copper Foil x128','Queued',0,'SCHED01 0:20',y)
        c.text(X0+9,Y0+129,'28/32 threads  640K/768K',MUTED,(X0+8,Y0+127,X0+172,Y0+139))
    return im,c
issues=[]
for tab,name in ((0,'gui_schematics_tab'),(1,'gui_jobs_tab')):
    im,c=build(tab); issues+=[f'{name}: {i}' for i in c.issues]
    bg=Image.new('RGBA',im.size,(36,37,40,255)); bg.alpha_composite(im); bg.resize((im.width*3,im.height*3),Image.NEAREST).save(f'previews/{name}.png')
print('ISSUES:',len(issues)); print('\n'.join(issues))
a=Image.open('previews/gui_schematics_tab.png'); b=Image.open('previews/gui_jobs_tab.png')
o=Image.new('RGB',(a.width*2+20,a.height),(30,31,34)); o.paste(a,(0,0)); o.paste(b,(a.width+20,0)); o.resize((o.width*40//100,o.height*40//100)).save('/home/claude/sch_gui.png')
sys.exit(1 if issues else 0)
