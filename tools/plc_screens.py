import sys; sys.path.insert(0,'/home/claude/plc/tools')
from layouts import Screen, pad
L={}
s=Screen('PLCSTS','PLC Status',prompt='Selection or command',keys='F1=Help F3=Exit F5=Refresh F6=Run F7=Stop F9=Clear fault F10=I/O F11=Modules')
s.put(2,1,'PLC  . . . . . :   PLC01          Network . . :   ELNET01 (cabled)')
s.put(3,1,'Program  . . . :   DOORCTL        Size  . . :   1,284 bytes  (Day 2 14:02)')
s.put(5,1,'Mode . . . . . :'); s.put(5,20,'RUN','b'); s.put(5,36,'Scan time . :   3 ticks')
s.put(6,1,'Instructions . :   38 / 50 per tick   (76%)')
s.put(7,1,'Loaded by  . . :   ZAGDRATH  (authority *CONFIGURE)')
s.put(9,1,'Last error . . :   ELC1501  Line 0014')
s.put(10,3,'Module 2 is not an Inventory Sensor.','d')
s.put(12,1,'Select one of the following:')
for i,t in enumerate(('1. Edit program (source editor)','2. Display I/O table','3. Display modules','4. Save program to cartridge','5. Load program from cartridge','6. Display retained variables')):
    s.put(14+i,5,t)
L['plcsts']=s
s=Screen('PLCIO','PLC I/O Table',prompt=None,keys='F1=Help F3=Exit F5=Refresh F12=Cancel     (live, refreshes every 10 ticks)')
s.put(2,1,'PLC01   DOORCTL   RUN   scan 3 ticks')
s.put(4,1,'Face      Input  Output  Driven by','b')
for i,(f_,i_,o,d) in enumerate((('*UP','0','0',''),('*DOWN','15','0','lever'),('*NORTH','0','15','CHGRSOUT line 0022'),('*SOUTH','7','0','comparator'),('*EAST','0','4','CHGRSOUT line 0031'),('*WEST','0','0',''))):
    s.put(5+i,1,pad(f_,8)); s.put(5+i,11,i_.rjust(5),'b' if i_!='0' else 'n'); s.put(5+i,18,o.rjust(6),'b' if o!='0' else 'n'); s.put(5+i,27,d,'d')
s.put(12,1,'Bars  ',); s.put(13,1,'*DOWN   in  ###############'); s.put(14,1,'*NORTH  out ###############'); s.put(15,1,'*SOUTH  in  #######'); s.put(16,1,'*EAST   out ####')
L['plcio']=s
s=Screen('PLCMOD','PLC Modules',prompt=None,keys='F1=Help F3=Exit F5=Refresh F12=Cancel')
s.put(2,1,'PLC01   4 module slots   (insert modules by using them on the PLC)')
s.put(4,1,'Slot  Module              Setting          Value          Status','b')
for i,(m,set_,v,st) in enumerate((('Presence Sensor','r 8 *PLAYERS','2','*OK'),('Inventory Sensor','face *WEST','64% (1,284)','*OK'),('Light Sensor','','11, day','*OK'),('Timer Module','','Day 2 14:32','*OK'))):
    s.put(5+i,2,str(i+1)); s.put(5+i,7,pad(m,18)); s.put(5+i,27,pad(set_,16)); s.put(5+i,44,pad(v,14),'b'); s.put(5+i,59,st)
s.put(10,1,'Type options, press Enter.  2=Change setting  4=Remove module')
L['plcmod']=s
