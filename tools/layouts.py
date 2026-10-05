# Character-grid layouts for the Terminal OS screens. Each screen is built with the same put()/field() calls the Java
# panels will mirror (CrtGrid.put(row, col, text, attr)), and emitted as: a numbered 80 x 24 text grid, a field table,
# and a rendered preview (green phosphor, existing renderer conventions).
SYS='ELNET01'; CLOCK='Day 2  07:14:22'
BOX=dict(h='\u2500',v='\u2502',tl='\u250c',tr='\u2510',bl='\u2514',br='\u2518',lt='\u251c',rt='\u2524')
class Screen:
    def __init__(s,sid,title,prompt='Selection or command',keys='F3=Exit  F12=Cancel',msg='',mw=False,frame=True):
        s.rows=[[' ']*80 for _ in range(24)]; s.attr=[['n']*80 for _ in range(24)]; s.ul=[[False]*80 for _ in range(24)]; s.rv=[[False]*80 for _ in range(24)]
        s.fields=[]; s.sid=sid; s.title=title
        if frame:
            s.put(0,1,sid); s.put(0,(80-len(title))//2,title,'b'); r=f'System:  {SYS}'; s.put(0,79-len(r),r); s.put(1,79-len(CLOCK),CLOCK)
            if prompt: s.put(20,1,prompt); s.put(21,1,'===>'); s.field(21,5,72,'','command line (CrtScreen.command)')
            if msg: s.put(22,1,msg,'b')
            if mw: s.put(22,76,'MW','b')
            s.put(23,1,keys,'b')
    def put(s,r,c,t,a='n'):
        if c+len(t.rstrip())>80: raise ValueError(f'{s.sid} row {r}: text past column 79: {t!r}')
        for i,ch in enumerate(t):
            if 0<=c+i<80: s.rows[r][c+i]=ch; s.attr[r][c+i]=a
    def right(s,r,t,a='n'): s.put(r,79-len(t),t,a)
    def center(s,r,t,a='n'): s.put(r,(80-len(t))//2,t,a)
    def field(s,r,c,n,val='',note='',a='b'):
        s.put(r,c,(val+' '*n)[:n],a)
        for i in range(n):
            if c+i<80: s.ul[r][c+i]=True
        s.fields.append((r,c,n,note or 'input'))
    def reverse(s,r,c,n):
        for i in range(n): s.rv[r][c+i]=True
    def box(s,r0,c0,h,w,div=None):
        s.put(r0,c0,BOX['tl']+BOX['h']*(w-2)+BOX['tr'])
        for r in range(1,h-1): s.put(r0+r,c0,BOX['v']+' '*(w-2)+BOX['v'])
        s.put(r0+h-1,c0,BOX['bl']+BOX['h']*(w-2)+BOX['br'])
        if div: s.put(r0+div,c0,BOX['lt']+BOX['h']*(w-2)+BOX['rt'])
    def text(s):
        ruler='     '+''.join(str(i//10%10) if i%10==0 else ' ' for i in range(80))
        ruler2='     '+''.join(str(i%10) for i in range(80))
        out=[ruler,ruler2]
        for r in range(24): out.append(f'{r:02d} | '+''.join(s.rows[r]).rstrip())
        if s.fields:
            out.append('   fields (row, col, length): '+'; '.join(f'({r},{c},{n}) {note}' for r,c,n,note in s.fields))
        return '\n'.join(out)
def pad(t,n): return (t+' '*n)[:n]
L={}
# 1 SIGN ON (EXTEND)
s=Screen('SIGNON','Sign On',prompt=None,keys='F1=Help  F3=Exit  F12=Cancel')
s.put(3,0,'                              System  . . . . . :   '+SYS)
s.put(5,0,'Firewall installed: sign on with your player name.','d')
s.put(8,0,pad('   User  . . . . . . . . . . . .',34)); s.field(8,34,16,'ZAGDRATH','user (existing field)')
s.put(9,0,pad('   Password  . . . . . . . . . .',34)); s.put(9,34,'(no password needed)','d')
s.put(10,0,pad('   Program/procedure . . . . . .',34)); s.field(10,34,10,'','initial program, *NONE (NEW)')
s.put(11,0,pad('   Menu  . . . . . . . . . . . .',34)); s.field(11,34,10,'MAIN','initial menu (NEW)')
s.put(12,0,pad('   Current library . . . . . . .',34)); s.field(12,34,10,'*USRPRF','current library (NEW)')
s.box(14,18,5,44); s.center(15,'E N C O D E D   L O G I S T I C S','b'); s.center(16,'Terminal OS  -  '+SYS); s.center(17,'(C) Zagdrath. All rights reserved.','d')
s.put(20,0,'Press Enter to continue.')
L['01_signon']=s
# 2 MAIN MENU (EXTEND)
s=Screen('MAIN','ENCODED LOGISTICS MAIN MENU',keys='F1=Help  F3=Exit  F5=Refresh  F9=Command Entry  F12=Cancel  F24=More keys',mw=True)
s.put(3,0,'Select one of the following:')
for r,(n,t) in zip((6,7,8,9,10,11,12,13,15),((1,'Work with Inventory'),(2,'Work with Jobs'),(3,'Work with Devices'),(4,'Display Network Status'),
        (5,'Work with Libraries'),(6,'Work with Active Jobs'),(7,'Display Messages'),(8,'Work with Output'),(90,'Sign Off'))):
    num=f'{n}.'; s.put(r,8-len(num),num+' '+t,'n')
L['02_main_menu']=s
def legend(s,opts,r=4): s.put(3,1,'Type options, press Enter.'); s.put(r,3,'   '.join(opts))
# 3 WRKLIB
s=Screen('WRKLIB','Work with Libraries',prompt='Parameters or command',keys='F1=Help  F3=Exit  F5=Refresh  F6=Create  F9=Command Entry  F12=Cancel')
legend(s,['2=Change','4=Delete','5=Display','12=Work with members'])
s.put(6,0,'Opt  Library     Type      Text','b')
for i,(l,t,x) in enumerate((('ELGPL','*PROD','General purpose library'),('ELSYS','*SYS','System library - samples (read-only)'),('ZAGLIB','*PROD','Zagdrath automation scripts'),('TESTLIB','*TEST','Scratch'))):
    s.field(7+i,0,3,'12' if i==2 else '',f'Opt row {i}' if i==0 else ''); s.put(7+i,5,pad(l,10)); s.put(7+i,17,pad(t,8)); s.put(7+i,27,x)
s.right(20,'Bottom')
L['03_wrklib']=s
# 4 WRKMBR
s=Screen('WRKMBR','Work with Members',prompt='Parameters or command',keys='F1=Help  F3=Exit  F5=Refresh  F6=Create  F9=Command Entry  F11=Sort  F12=Cancel')
s.put(2,1,'Library . . . . :   ZAGLIB')
s.put(3,1,'Type options, press Enter.'); s.put(4,3,'2=Edit  3=Copy  4=Delete  5=Display  6=Print  7=Rename  14=Compile')
s.put(6,0,'Opt  Member      Type    Chg  Text','b')
for i,(m,c,t) in enumerate((('ARCHIVE','',"Move old items to tape"),('NOCWALL','*',"Refresh the NOC status display"),('RESTOCK','',"Keep logic dies stocked"),
                            ('SORTDEMO','',"List and sort demo"),('UPSALERT','*',"Alert operators on UPS power"))):
    s.field(7+i,0,3,'14' if m=='NOCWALL' else ('2' if m=='RESTOCK' else ''),'Opt' if i==0 else ''); s.put(7+i,5,pad(m,10)); s.put(7+i,17,'ELCLP'); s.put(7+i,26,c,'b'); s.put(7+i,30,t)
s.put(13,30,"Chg * = changed since last compile",'d'); s.right(20,'Bottom')
L['04_wrkmbr']=s
# 5 SOURCE EDITOR
src=open('/home/claude/elcl/elcl/examples/RESTOCK.elclp').read().rstrip('\n').split('\n')
s=Screen('EDTMBR','Edit Member',prompt='Edit command  (FIND CHANGE TOP BOTTOM SAVE FILE CANCEL)',
         keys='F1=Help F3=Exit F4=Prompt F5=Refresh F10=Cursor F11=Full screen F12=Cancel',msg='ELC0002: Variable &CONT is not declared.')
s.put(1,1,'ZAGLIB/RESTOCK   ELCLP'); s.put(2,1,'Columns . . . :    1  72     Line . . :   0009     Col . . :   34')
s.put(3,0,'FMT **  ...+... 1 ...+... 2 ...+... 3 ...+... 4 ...+... 5 ...+... 6 ...+... 7 ..','d')
s.put(4,0,'       ************** Beginning of data *************************************','d')
r=5; n=0
EXCL=(10,11,12)                       # sequence numbers excluded by X3 on line 10
while r<19 and n<len(src):
    seq_n=n+1
    if seq_n==EXCL[0]:
        s.put(r,0,'       - - - - - - - - - - - - - - - - - - - - - - - -  3 data records excluded','d'); r+=1; n+=len(EXCL); continue
    line=src[n]; bad=seq_n==9
    if bad: line=line.replace('&COUNT','&CONT',1)
    s.field(r,0,7,f'{seq_n:04d}.00','line command / sequence margin (one per row)' if r==5 else '',a='n')
    s.put(r,8,line[:72])
    if bad: s.reverse(r,8,72)
    r+=1; n+=1
if n>=len(src) and r<20: s.put(r,0,'       ****************** End of data ****************************************','d')
else: s.right(20,'More...')
L['05_source_editor']=s
s=Screen('EDTMBR','Edit Member - Full screen',prompt=None,keys='F3=Exit  F11=Margin  F12=Cancel',msg='')
s.put(1,1,'ZAGLIB/RESTOCK   ELCLP     full-screen edit (no margin commands)'); s.put(2,1,'Columns . . . :    1  80')
for i,l in enumerate([l for l in src][:17]): s.put(3+i,0,l[:80])
L['05b_source_editor_fullscreen']=s
# 6 PROMPTER
s=Screen('PROMPT','Start Crafting (STRCRAFT)',prompt=None,
         keys='F3=Exit F4=Prompt F5=Refresh F10=Additional parameters F12=Cancel F24=More keys')
s.put(2,1,'Type choices, press Enter.')
req=[('Item  . . . . . . . .','ITEM','logic_die',24,'Name, F4 for list'),('Quantity  . . . . . .','QTY','64',10,'1-999999')]
opt=[('Scheduler . . . . . .','SCHEDULER','*ANY',10,'*ANY, name'),('Missing ingredients .','MISSING','*FAIL',8,'*FAIL, *PARTIAL'),
     ('Wait for completion .','WAIT','*NO',4,'*NO, *YES'),('Return craft job ID .','RTNCRFJOB','',10,'*CHAR variable')]
def prow(r,lab,kw,val,n,hint,a):
    s.put(r,1,lab,a); s.put(r,24,pad(kw,10),'d'); s.field(r,35,n,val,'field (col 35, len = schema length, max 24)' if r==4 else ''); s.put(r,61,hint[:18],'d')
for i,p in enumerate(req): prow(4+i,*p,'b')
s.put(7,1,'Additional Parameters','b')
for i,p in enumerate(opt): prow(9+i,*p,'n')
s.right(20,'Bottom'); s.put(22,1,'F4 on ITEM opens the item list. Required parameters are shown bright.','d')
L['06_prompter']=s
# 7 COMPILE LISTING
s=Screen('DSPSPLF','Display Spooled File',prompt=None,keys='F3=Exit  F12=Cancel  F19=Left  F20=Right  F24=More keys')
s.put(2,1,'File  . . . . . :   NOCWALL      Page/Line   1/1'); s.put(3,1,'Control . . . . .   ____         Columns     1 - 78'); s.put(4,1,'Find  . . . . . .   ______________________________')
s.put(5,0,'*...+....1....+....2....+....3....+....4....+....5....+....6....+....7....+...','d')
lst=['ELCL Compile Listing   ZAGLIB/NOCWALL   Day 2 07:13   ELNET01',' SEQNBR  *...+... 1 ...+... 2 ...+... 3 ...+... 4 ...+... 5',
     ' 0001.00 /* NOCWALL - refresh a status display every 30 seconds   */',' 0003.00 PGM',' 0004.00   DCL VAR(&USED)  TYPE(*INT)',
     ' ...','                     Cross Reference','  Variable   Type    Length  References',
     '  &PCT       *DEC    5 1      0006   0016*','  &USED      *INT             0004   0014*   0017',
     '                     Message Summary','  Seq      Msg ID   Sev  Text','  0019.00  ELC0008   10  Value truncated to length 32.',
     '  Total 1  Info 0  Warning 1  Error 0  Severe 0   Program NOCWALL created.']
for i,t in enumerate(lst): s.put(6+i,0,t)
L['07_compile_listing']=s
# 8 WRKACTJOB
s=Screen('WRKACTJOB','Work with Active Jobs',prompt='Parameters or command',keys='F1=Help F3=Exit F5=Refresh F9=Command Entry F11=Sort F12=Cancel F24=More keys')
s.put(2,1,'Budget used:   37%    Elapsed:  00:05:12    Active jobs:   5    Hosts:  2/2')
s.put(3,1,'Type options, press Enter.'); s.put(4,3,'2=Change  3=Hold  4=End  5=Work with  6=Release  8=Spooled files')
s.put(6,0,'Opt  Job         User        Type   Host          Status     Budget','b')
for i,(j,u,t,h,st,b) in enumerate((('QINTER','ZAGDRATH','INT','ELDESK01','*ACTIVE','12%'),('RESTOCK','ZAGDRATH','BCH','MIDRANGE01','*ACTIVE','31%'),
        ('NOCWALL','ZAGDRATH','BCH','CMPSRV01','*WAIT','0%'),('ARCHIVE','OPERATOR','BCH','CMPSRV01','*JOBQ',''),('UPSALERT','ZAGDRATH','BCH','CMPSRV01','*HELD',''))):
    s.field(7+i,0,3,'5' if j=='RESTOCK' else ''); s.put(7+i,5,pad(j,10)); s.put(7+i,17,pad(u,10)); s.put(7+i,29,t); s.put(7+i,36,pad(h,12)); s.put(7+i,50,pad(st,9),'b' if st=='*ACTIVE' else 'n'); s.put(7+i,61,b.rjust(5))
s.right(20,'Bottom')
L['08_wrkactjob']=s
# 9 WRKJOB / DSPJOBLOG
s=Screen('WRKJOB','Work with Job',keys='F1=Help  F3=Exit  F5=Refresh  F12=Cancel')
s.put(2,1,'Job:   RESTOCK      User:   ZAGDRATH      Number:   000123'); s.put(3,1,'Host:  MIDRANGE01   Status: *ACTIVE       Budget:   31%')
s.put(5,1,'Select one of the following:')
for i,(n,t) in enumerate(((1,'Display job status attributes'),(2,'Display job definition attributes'),(4,'Work with spooled files'),(10,'Display job log'),(11,'Display call stack'),(30,'All of the above'))):
    num=f'{n}.'; s.put(7+i,8-len(num),num+' '+t)
L['09_wrkjob']=s
s=Screen('DSPJOBLOG','Display Job Log',prompt=None,keys='F1=Help  F3=Exit  F5=Refresh  F10=Display detailed messages  F12=Cancel')
s.put(2,1,'Job . . :   RESTOCK      User  . . :   ZAGDRATH      Number  . . :   000123')
for i,(t,a) in enumerate((('>> CALL PGM(ZAGLIB/RESTOCK) PARM(LOGIC_DIE 64)','b'),('   ELC1203  Item LOGIC_DIE is being recalled from tape.','n'),
        ('>> STRCRAFT ITEM(&ITEM) QTY(64) RTNCRFJOB(&JOB)','b'),('   ELC1403  Missing ingredients for LOGIC_DIE.','n'),
        ('   (monitored: MONMSG MSGID(ELC1400))','d'),('>> SNDMSG MSG(''RESTOCK: cannot craft LOGIC_DIE'') TOUSR(*SYSOPR)','b'),('>> RETURN','b'),('   ELC0307  Job 000123/ZAGDRATH/RESTOCK ended normally.','n'))):
    s.put(4+i,1,t,a)
s.right(20,'Bottom')
L['09b_dspjoblog']=s
# 10 WRKJOBSCDE
s=Screen('WRKJOBSCDE','Work with Job Schedule Entries',prompt='Parameters or command',keys='F1=Help  F3=Exit  F5=Refresh  F6=Add  F9=Command Entry  F12=Cancel')
legend(s,['2=Change','3=Hold','4=Remove','10=Submit now'])
s.put(6,0,'Opt  Job         Status    Frequency   Next run         Command','b')
for i,(j,st,f,n,c) in enumerate((('NOCWALL','*SCD','*INTERVAL','Day 2  07:15:00','CALL PGM(ZAGLIB/NOCWALL)'),('ARCHIVE','*SCD','*DAILY','Day 3  02:00:00','CALL PGM(ZAGLIB/ARCHIVE)'),
        ('RESTOCK','*HLD','*ONCE','Day 2  12:00:00','CALL PGM(ZAGLIB/RESTOCK)'))):
    s.field(7+i,0,3,''); s.put(7+i,5,pad(j,10)); s.put(7+i,17,pad(st,8)); s.put(7+i,27,pad(f,10)); s.put(7+i,39,pad(n,15)); s.put(7+i,56,c[:23])
s.right(20,'Bottom')
L['10_wrkjobscde']=s
# 11 WRKTRGEVT
s=Screen('WRKTRGEVT','Work with Trigger Events',prompt='Parameters or command',keys='F1=Help  F3=Exit  F5=Refresh  F6=Add  F9=Command Entry  F12=Cancel')
legend(s,['2=Change','3=Hold','4=Remove'])
s.put(6,0,'Opt  Trigger     Event       Item/Device      Value  Program            Status','b')
for i,(t,e,d,v,p,st) in enumerate((('LOWDIES','*ITMBELOW','LOGIC_DIE','64','ZAGLIB/RESTOCK','*ACTIVE'),('UPSPWR','*PWRUPS','UPS01','','ZAGLIB/UPSALERT','*ACTIVE'),
        ('LEVER','*RSCHANGE','CTLIF01','*UP','ZAGLIB/DOORS','*HELD'))):
    s.field(7+i,0,3,''); s.put(7+i,5,pad(t,10)); s.put(7+i,17,pad(e,11)); s.put(7+i,29,pad(d,16)); s.put(7+i,46,pad(v,6)); s.put(7+i,53,pad(p,17)); s.put(7+i,72,st)
s.right(20,'Bottom')
L['11_wrktrgevt']=s
# 12 DSPMSG
s=Screen('DSPMSG','Display Messages',prompt='Parameters or command',keys='F1=Help  F3=Exit  F5=Refresh  F11=Remove all  F12=Cancel  F24=More keys')
s.put(2,1,'Queue . . . . :   ZAGDRATH                 Program . . . . :   *DSPMSG')
s.put(3,1,'Type options, press Enter.'); s.put(4,3,'4=Remove   5=Display details')
s.put(6,0,'Opt  Sev  From        Sent             Message','b')
for i,(sv,f,t,m,new) in enumerate((('30','RESTOCK','Day 2  07:10:44','Called program ZAGLIB/RESTOCK ended abnormally.',True),('00','UPSALERT','Day 2  06:58:02','Utility power restored. UPS charge 82%.',True),
        ('10','QSYSOPR','Day 2  06:40:15','Storage 91% used (hot tier).',False))):
    a='b' if new else 'n'; s.field(7+i,0,3,''); s.put(7+i,5,sv,a); s.put(7+i,10,pad(f,10),a); s.put(7+i,22,pad(t,15),a); s.put(7+i,39,m[:40],a)
s.put(11,39,'Newest first; unread messages are bright.','d'); s.right(20,'Bottom')
L['12_dspmsg']=s
# 13 WRKSPLF + DSPSPLF
s=Screen('WRKSPLF','Work with Output',prompt='Parameters or command',keys='F1=Help  F3=Exit  F5=Refresh  F9=Command Entry  F11=Sort  F12=Cancel')
legend(s,['4=Delete','5=Display','6=Print'])
s.put(6,0,'Opt  File        Job                   User        Pages  Status  Created','b')
for i,(f,j,u,p,st,c) in enumerate((('NOCWALL','000118/NOCWALL','ZAGDRATH','1','*RDY','Day 2  07:13'),('QPJOBLOG','000123/RESTOCK','ZAGDRATH','2','*RDY','Day 2  07:10'),('INVLIST','000099/QPRTRPT','OPERATOR','6','*PRT','Day 1  18:44'))):
    s.field(7+i,0,3,''); s.put(7+i,5,pad(f,10)); s.put(7+i,17,pad(j,20)); s.put(7+i,39,pad(u,10)); s.put(7+i,51,p.rjust(5)); s.put(7+i,58,pad(st,6)); s.put(7+i,66,c)
s.right(20,'Bottom')
L['13_wrksplf']=s
# 14 WRKSYSVAL
s=Screen('WRKSYSVAL','Work with System Values',prompt='Parameters or command',keys='F1=Help  F3=Exit  F5=Refresh  F9=Command Entry  F12=Cancel')
legend(s,['2=Change','5=Display'])
s.put(6,0,'Opt  System value  Value                     Description','b')
for i,(v,val,d) in enumerate((('SYSNAME','ELNET01','System name'),('DATFMT','*DAY','Date display format'),('SECLVL','30','Security level (10 / 30)'),
        ('QMAXJOB','16','Maximum batch jobs'),('LOGRTN','50','Job logs retained'),('PHOSPHOR','*GREEN','Default screen colour'))):
    s.field(7+i,0,3,''); s.put(7+i,5,pad(v,12)); s.put(7+i,19,pad(val,24),'b'); s.put(7+i,45,d)
s.right(20,'Bottom')
L['14_wrksysval']=s
# 15 HELP pop-up (over WRKLIB)
s=L['03_wrklib']; import copy; h=copy.deepcopy(s); h.sid='HELP'
h.box(5,12,13,56,div=2); h.put(6,14,'Work with Libraries - Help','b')
for i,t in enumerate(('Shows the libraries on this system. Type an option','in the Opt column next to a library and press Enter.','',
                      '  2=Change   Change the text or authority.','  4=Delete   Delete an empty library you own.','  5=Display  Show its attributes.','  12=Work with members  List the source members.')):
    h.put(8+i,14,t)
h.put(16,14,'F3=Exit help   F12=Cancel','b'); h.put(15,58,'Bottom')
L['15_help_popup']=h
# 16 CMDENT (EXTEND)
s=Screen('CMDENT','Command Entry',prompt='Type command, press Enter.',keys='F1=Help F3=Exit F4=Prompt F9=Retrieve F12=Cancel F13=Clear F24=More keys',mw=True)
s.put(2,1,'All previous commands and messages:','d')
for i,(t,a) in enumerate((('> CRTLIB LIB(ZAGLIB) TEXT(''Zagdrath automation scripts'')','n'),('  ELC0210  Library ZAGLIB created.','b'),
        ('> SBMJOB CMD(CALL PGM(ZAGLIB/RESTOCK)) JOB(RESTOCK)','n'),('  ELC0304  Job 000123/ZAGDRATH/RESTOCK submitted to job queue on MIDRANGE01.','b'),
        ('> CHGRSOUT DEV(CTLIF01) SIDE(*UP) LVL(20)','n'),('  ELC0004  Value 20 is outside the allowed range.','b'),('> show drives','n'),('  (alias: WRKDEV filtered to storage)','d'))):
    s.put(3+i,1,t,a)
L['16_cmdent']=s
if __name__=='__main__':
    import os
    for k,v in L.items(): open(f'/home/claude/ctlif/layouts/{k}.txt','w').write(v.text()+'\n')
    print(len(L),'layouts')
