# Terminal OS-style screens for the Midrange line: text only - option fields, F-keys, the command line; no buttons, no
# item slots, no player inventory (items go in by using them on the machine).
import sys; sys.path.insert(0,'/home/claude/mr3/tools')
import layouts as LY
from layouts import Screen, pad
L={}
def opts(s,r,text): s.put(3,1,'Type options, press Enter.'); s.put(4,3,text)
s=Screen('MRCTL','Midrange Control Panel',prompt='Selection or command',keys='F1=Help F3=Exit F5=Refresh F7=IPL F10=Hold queue F11=Release F12=Cancel')
s.put(2,1,'System:  MIDRANGE01   A6 RUNNING   Threads 2   Max job 128   Batch 2')
opts(s,3,'4=Eject   5=Display recipes   8=Make default library')
s.put(6,0,'Opt  Drive  Library     Recipes  Status','b')
for i,(d,l,r,st) in enumerate((('1','PRODLIB','5 of 8','*DFT'),('2','TOOLLIB','3 of 8','*READY'))):
    s.field(7+i,0,3,''); s.put(7+i,5,d); s.put(7+i,12,pad(l,10)); s.put(7+i,24,r); s.put(7+i,33,st)
s.put(9,5,'Insert a diskette by using it on the drive slot.','d')
s.put(11,1,'Opt  Job   Item                 Qty  Status     Progress','b')
for i,(j,it,q,st,p) in enumerate((('0007','Logic Die','16','*ACTIVE','41%'),('0008','Copper Foil','32','*QUEUED',''),('0010','Heatsink','4','*HELD','missing Ferrite'))):
    s.field(12+i,0,3,''); s.put(12+i,5,j); s.put(12+i,11,pad(it,20)); s.put(12+i,32,q.rjust(3)); s.put(12+i,37,pad(st,9),'b' if st=='*ACTIVE' else 'n'); s.put(12+i,48,p)
s.put(16,3,'3=Hold   4=End   6=Release')
L['mrctl']=s
s=Screen('IMCTL','Integrated System Control Panel',prompt='Selection or command',keys='F1=Help F3=Exit F5=Refresh F7=IPL F10=Hold queue F11=Release F12=Cancel')
s.put(2,1,'System:  MIDRANGE02   A6 RUNNING   Threads 4   Max job 512   Batch 4')
opts(s,3,'4=Remove from magazine   5=Display recipes   8=Make default library')
s.put(6,0,'Opt  Pos  Library     Recipes  Status','b')
for i,(l,r,st) in enumerate((('PRODLIB','8 of 8','*DFT'),('TOOLLIB','3 of 8','*READY'),('RACKLIB','6 of 8','*READY'),('',' ','*EMPTY'))):
    s.field(7+i,0,3,''); s.put(7+i,5,str(i+1)); s.put(7+i,10,pad(l or '(empty)',10)); s.put(7+i,22,r); s.put(7+i,31,st,'d' if st=='*EMPTY' else 'n')
s.put(11,5,'Magazine: use a Diskette Magazine on the drive unit.','d')
s.put(13,1,'Opt  Job   Item                 Qty  Status     Progress','b')
for i,(j,it,q,st,p) in enumerate((('0007','Logic Die','16','*ACTIVE','41%'),('0011','Heatsink','32','*ACTIVE','12%'),('0012','Circuit Substrate','64','*QUEUED',''))):
    s.field(14+i,0,3,''); s.put(14+i,5,j); s.put(14+i,11,pad(it,20)); s.put(14+i,32,q.rjust(3)); s.put(14+i,37,pad(st,9),'b' if st=='*ACTIVE' else 'n'); s.put(14+i,48,p)
L['imctl']=s
s=Screen('KEYPUNCH','Keypunch - Punch a Card',prompt=None,keys='F1=Help F3=Exit F4=Prompt F6=Punch F12=Cancel F13=Clear')
s.put(2,1,'Type an item in each position (F4 for a list), press F6 to punch.')
for r in range(3):
    for c in range(3):
        v=[['circuit_substrate','logic_die','circuit_substrate'],['copper_foil','ferrite','copper_foil'],['','iron_ingot','']][r][c]
        s.field(5+r*2,4+c*24,22,v,'grid cell (row, col)' if r==c==0 else '')
s.put(12,1,'Result . . . . . :   Midrange System x1','b')
s.put(14,1,'Blank cards  . . :   12   (use cards on the hopper to load)')
s.put(15,1,'Punched cards  . :   3    (take them from the stacker)')
s.put(17,1,'Card image (col 1-40):','d'); s.put(18,2,''.join('\x7f' if (i*7)%5<2 else '.' for i in range(40)))
L['keypunch']=s
s=Screen('CARDRDR','Card Reader',prompt=None,keys='F1=Help F3=Exit F5=Refresh F6=Read F12=Cancel')
s.put(2,1,'Deck in the hopper is read onto the diskette in the reader slot.')
s.put(4,1,'Seq  Card recipe                      Status','b')
for i,(r,st) in enumerate((('Midrange System x1','new'),('Circuit Substrate x2','new'),('Copper Foil x2','replaces 3'),('Keypunch x1','new'),('Logic Die x1','new'))):
    s.put(5+i,2,str(i+1)); s.put(5+i,6,pad(r,32)); s.put(5+i,39,st)
s.put(11,1,'Diskette . . . . :   TOOLLIB   3 of 8   ->  7 of 8 after reading')
s.put(13,1,'Load cards on the hopper; insert a diskette in the slot.','d')
L['cardrdr']=s
s=Screen('PRINTER','Line Printer',prompt=None,keys='F1=Help F3=Exit F4=Prompt F5=Refresh F6=Print F12=Cancel')
s.put(2,1,'Select a report, press F6 to print.')
s.put(4,1,'Report . . . . . :'); s.field(4,22,1,'1'); s.put(4,26,'1=Network inventory listing')
for i,t in enumerate(('2=Job log','3=Device list','4=ELCL spooled file')): s.put(5+i,26,t)
s.put(9,1,'Spooled file . . :'); s.field(9,22,10,'*LAST'); s.put(9,34,'Name, *LAST, F4 for list','d')
s.put(11,1,'Pages (est.) . . :   4          Paper loaded . :   38 sheets')
s.put(12,1,'Printer status . :   *READY'); s.put(14,1,'Load paper by using it on the printer.','d')
L['printer']=s
s=Screen('DSKDRV','Disk Drive',prompt='Selection or command',keys='F1=Help F3=Exit F5=Refresh F12=Cancel')
s.put(2,1,'Device . . . . :   DISK01      Status . . :   *ONLINE  (spinning)')
s.put(4,1,'Drive pack . . :   2M Storage Drive'); s.put(5,1,'Capacity . . . :   1,284,550 / 2,097,152 items  (61%)'); s.put(6,1,'Types  . . . . :   412 / 1,024')
s.put(8,1,'Opt'); s.field(8,6,1,''); s.put(8,9,'4=Unload pack (spins down first)   5=Display contents')
s.put(10,1,'Insert a Storage Drive by using it on the drive.','d')
L['dskdrv']=s
s=Screen('TAPDRV','Tape Drive',prompt='Selection or command',keys='F1=Help F3=Exit F5=Refresh F12=Cancel')
s.put(2,1,'Device . . . . :   TAPE01      Status . . :   *READY  (load point)')
s.put(4,1,'Reel . . . . . :   VOL004   7-track reel'); s.put(5,1,'Used . . . . . :   18,420 / 65,536 items  (28%)  - cold storage')
s.put(6,1,'Last access  . :   Day 2  06:12:44')
s.put(8,1,'Opt'); s.field(8,6,1,''); s.put(8,9,'4=Unload (rewinds first)   5=Display contents   7=Rewind')
s.put(10,1,'Mount a reel by using it on the drive.','d')
L['tapdrv']=s
