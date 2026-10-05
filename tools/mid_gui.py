# Green-screen container GUIs for the Midrange System, Keypunch, Card Reader, Line Printer + Terminal Desk additions.
# Text uses crt_gui (80 x 24 grid, tall pixels 2x3). Item slots reserve 4 cols x 2 rows of cells; the slot frame is a
# square phosphor outline drawn at true GUI pixels around the item (items are never stretched).
import os
from PIL import Image, ImageDraw
import crt_gui as G, crt_screens as S
from crt_screens import head, fkeys, cmdline, pad
PAD=24
def cell_px(col,row): return PAD+(G.MARGIN_PX[0]+col*G.CW)*G.SX, PAD+(G.MARGIN_PX[1]+row*G.CH)*G.SY
SLOT_W,SLOT_H=4,2                       # cells reserved per slot
def slot_box(img,col,row,item=None,ph='green',big=False):
    x,y=cell_px(col,row); w=SLOT_W*G.CW*G.SX; h=SLOT_H*G.CH*G.SY
    s=44 if big else 38; cx,cy=x+w//2,y+h//2; d=ImageDraw.Draw(img)
    col=G.hx(G.PHOSPHOR[ph]['normal'])
    d.rectangle((cx-s//2,cy-s//2,cx+s//2,cy+s//2),outline=col+(255,),width=2)
    if item is not None:
        img.alpha_composite(item.resize((32,32),Image.NEAREST),(cx-16,cy-16))
def compose(lines,slots,ph='green',cursor=None):
    img=G.render(lines,ph,cursor)
    for (col,row,item,big) in slots: slot_box(img,col,row,item,ph,big)
    return img
SYS=S.SYS
def blank(n): return ['']*n
def finalize(L,fk):
    """Pad/trim the body to 23 rows and put the function keys (bright) on row 24."""
    L=[l if l.startswith('{') else '{n}'+l for l in L][:23]
    return L+['']*(23-len(L))+['{b}'+fk]
# ---------------- Midrange System control panel ----------------
def midrange_panel(items):
    L=head('MIDRANGE CONTROL PANEL','MRCTL')
    L+=['',
        '{n}  System status  . . :   {r} A6b2 {/r}  {b}RUNNING - job active{n}',
        '{n}  IPL source . . . . :   Diskette         Last IPL . . :   1d 04:12',
        '',
        '{b}  Library diskette{n}                          {b}Current job',
        '',
        '{n}                PRODLIB                    {n}  Job 0007  Logic Die x16',
        '{n}                5 of 8 recipes             {n}  Step 2 of 3  [####......]  41%',
        '',
        '{b}  Job queue{n}                                    Threads 1 / 1   Max job 64',
        '{b}  Job   Item                  Qty   Status',
        '{n}  0008  Copper Foil            32   {n}Queued',
        '{n}  0009  Circuit Substrate      16   {n}Queued',
        '{n}  0010  Heatsink                4   {d}Held - missing Ferrite',
        '',
        '{n}  {r} IPL {/r}   {r} Hold queue {/r}   {r} Release {/r}']
    L=finalize(L,'F3=Exit   F5=Refresh   F7=IPL   F10=Hold   F11=Release   F12=Cancel')
    return compose(L,[(3,7,items['diskette_8in_written'],True)])
# ---------------- Keypunch ----------------
def keypunch(items):
    L=head('KEYPUNCH - Card Punch','KEYPUNCH')
    grid=[['circuit_substrate','logic_die','circuit_substrate'],['copper_foil','ferrite','copper_foil'],[None,'iron_ingot_v',None]]
    L+=['','{n}  Type the recipe in the grid, insert a blank card, press Punch.','',
        '{b}  Recipe{n}                         {b}Output{n}            {b}Cards',
        '','','','','','',
        '{n}                                                   Blank       Punched',
        '','',
        '{d}  Card image (col 1-40):',
        '{n}  '+''.join('\x7f' if (i*7)%5<2 else '.' for i in range(40)),
        '{n}  '+''.join('\x7f' if (i*3)%7<1 else '.' for i in range(40)),
        '','',
        '{n}  {r} PUNCH {/r}   {d}punches one card: Midrange System x1',
        '',
        ]
    L=finalize(L,'F3=Exit   F5=Refresh   F6=Punch   F12=Cancel   F13=Clear grid')
    sl=[]
    for r in range(3):
        for c_ in range(3):
            n=grid[r][c_]; sl.append((3+c_*5,6+r*2,items.get(n) if n else None,False))
    sl.append((33,8,items['midrange_system'],True))                     # output preview
    sl+= [(51,13,items['punch_card'],False),(63,13,items['punch_card_punched'],False)]
    return compose(L,sl)
# ---------------- Card Reader ----------------
def card_reader(items):
    L=head('CARD READER','CARDRDR')
    L+=['','{n}  Load punched cards in the hopper and a diskette, press Read.','',
        '{b}  Hopper (8 cards){n}','','','',
        '{b}  Cards in hopper:{n}',
        '{n}   1  Midrange System x1          5  Logic Die x1',
        '{n}   2  Circuit Substrate x2        6  (empty)',
        '{n}   3  Copper Foil x2              7  (empty)',
        '{n}   4  Keypunch x1                 8  (empty)','',
        '{b}  Diskette{n}                         Used . . . :   3 of 8 recipes',
        '','{n}                                   After read :   8 of 8 recipes','',
        '{n}  {r} READ {/r}   {d}reads 5 cards onto the diskette (duplicates replace)','',
        ]
    L=finalize(L,'F3=Exit   F5=Refresh   F6=Read   F12=Cancel')
    sl=[(3+i*5,6,items['punch_card_punched'] if i<5 else None,False) for i in range(8)]
    sl.append((3,16,items['diskette_8in_written'],True))
    return compose(L,sl)
# ---------------- Line Printer ----------------
def line_printer(items):
    L=head('LINE PRINTER','PRINTER')
    L+=['','{n}  Select a report, load paper, press Print.','',
        '{n}  Report . . . . . . .   {u}1{/u}   1=Network inventory listing',
        '{n}                              2=Job log',
        '{n}                              3=Device list',
        '{n}                              4=ELCL spooled file   File . . {u}*LAST     {/u}',
        '{n}  Pages (estimated)  :   4          Paper needed  :   4',
        '{b}  Paper{n}                     {b}Printed output','','','','',
        '{d}  ......................................................................',
        '{d}  NETWORK INVENTORY LISTING          ELNET01           10/04/26 14:32',
        '{d}  ITEM                                QUANTITY   LOCATION',
        '{d}  CIRCUIT SUBSTRATE                      1,284   HOT',
        '{d}  ......................................................................',
        '{n}  {r} PRINT {/r}']
    L=finalize(L,'F3=Exit   F5=Refresh   F6=Print   F12=Cancel')
    return compose(L,[(3,12,items['paper_v'],False),(30,12,items['written_book_v'],False)])
# ---------------- Terminal Desk additions ----------------
def main_menu5():
    L,cur=S.main_menu()
    L[7]='{n}     4. Display Network Status'; L.insert(8,'{n}     5. Work with Midrange Jobs'); L=L[:24]
    return G.render(L,'green',cur)
def wrk_midrange():
    L=head('Work with Midrange Jobs','WRKMRJOB')
    L+=['','{n}  System . . . . :   MIDRANGE01   Status . . :   {b}RUNNING{n}   Library . :   PRODLIB',
        '','{n}Type options, press Enter.','{n}  2=Change priority   3=Hold   4=Cancel   5=Display   6=Release   8=Job log','',
        '{b}Opt  Job   Recipe                   Qty  Status     Submitted    Progress']
    rows=[('0007','Logic Die','16','Active','14:20:31','41%'),('0008','Copper Foil','32','Queued','14:24:02',''),
          ('0009','Circuit Substrate','16','Queued','14:28:44',''),('0010','Heatsink','4','Held','14:30:10',''),
          ('0006','Ferrite','64','Ended','14:02:17','100%')]
    for i,(j,n,q,s,t,p) in enumerate(rows):
        L.append('{u}'+pad('8' if i==4 else '',3)+'{/u}  {n}'+j+'  '+pad(n,23)+q.rjust(4)+'  '+('{b}' if s=='Active' else '{d}' if s in ('Held','Ended') else '{n}')+pad(s,9)+'{n}  '+t+'     '+p)
    L+=['','{b}  Job log - 0006{n}',
        '{d}  14:02:17  Job 0006 started: Ferrite x64 (recipe 3 of PRODLIB)',
        '{d}  14:09:55  Job 0006 ended normally. 64 Ferrite stored.']
    L+=['']*(20-len(L))+['{n}Parameters or command',cmdline(),'',fkeys('F3=Exit   F5=Refresh   F9=Command Entry   F11=Job log   F12=Cancel')]
    return G.render(L[:24],'green',None)
def cli_midrange():
    L=head('Command Entry','CMDENT','Request level:  1')
    L+=['','{d}  All previous commands and messages:',
        '{n}  > show joblog',
        '{n}    14:02:17  0006  Ferrite x64           started   recipe 3',
        '{n}    14:09:55  0006  Ferrite x64           ended     64 stored',
        '{n}    14:20:31  0007  Logic Die x16         started   recipe 5',
        '{n}  > submit job logic_die 16',
        '{b}    Job 0011 submitted to MIDRANGE01 (queue position 4)',
        '{n}  > print joblog',
        '{b}    Spooled to LINE PRINTER: Job log, 2 pages (2 paper)',
        '{n}  > ipl',
        '{b}    IPL requested. Active job 0007 will be held and resumed after IPL.',
        '{n}  > submit job keypunch 1',
        '{d}    Recipe keypunch not on the library diskette (PRODLIB).']
    L+=['']*(20-len(L))+['{n}Type command, press Enter.',cmdline('show jobs_'),'',fkeys('F3=Exit   F4=Prompt   F9=Retrieve   F12=Cancel   F13=Clear')]
    return G.render(L[:24],'green',None)
