# Mockup content for every green-screen screen (80 x 24). Original wording, era style.
SYS='ELNET01'; DATE='10/04/26'; TIME='14:32:07'
def pad(s,n): return (s+' '*n)[:n]
def head(title,left='',right=None):
    """Row 1: screen id (left), title (centred, bright), system name (right, 1-column margin). Row 2: date + time."""
    right=right or f'System:  {SYS}'
    row=[' ']*80
    for i,ch in enumerate(left[:20]): row[i]=ch
    t0=(80-len(title))//2
    r0=79-len(right)
    for i,ch in enumerate(right): row[r0+i]=ch
    line=''.join(row[:t0])+'{b}'+title+'{n}'+''.join(row[t0+len(title):])
    date=f'{DATE}  {TIME}'
    return ['{n}'+line, '{n}'+' '*(79-len(date))+date]
def fkeys(s): return '{b}'+s
def cmdline(text='',prompt='===> '): return f'{{n}}{prompt}{{u}}{pad(text,72)}{{/u}}'
def main_menu():
    L=head('ENCODED LOGISTICS MAIN MENU','MAIN')
    L+=['','{n}Select one of the following:','',
        '{n}     1. Work with Inventory','{n}     2. Work with Jobs','{n}     3. Work with Devices','{n}     4. Display Network Status','',
        '{n}    90. Sign Off']+['']*9
    L+=['{n}Selection or command',cmdline('1'),'',fkeys('F3=Exit   F5=Refresh   F9=Command Entry   F12=Cancel   F24=More keys')]
    return L,(6,21)
INV=[('Circuit Substrate','1,284','Hot'),('Copper Foil','6,912','Hot'),('Ferrite','12,470','Cold'),('Gallium Ingot','318','Hot'),
     ('Heatsink','96','Hot'),('Iron Ingot','48,209','Hot'),('Logic Die','2,048','Hot'),('Memory Die','640','Cold'),
     ('Neodymium Ingot','1,152','Cold'),('Processor Die','256','Hot'),('Silicon Wafer','3,320','Hot'),('Tantalum Capacitor','512','Hot')]
def inventory():
    L=head('Work with Inventory','WRKINV')
    L+=['','{n}Position to  . . . . . .   {u}'+pad('',30)+'{/u}  Starting characters','',
        '{n}Type options, press Enter.','{n}  1=Withdraw   5=Display details   7=Craft','',
        '{b}Opt  Item                                       Quantity   Location']
    opts={0:'1',5:'7'}
    for i,(n,q,loc) in enumerate(INV[:11]):
        o=opts.get(i,'')
        L.append('{u}'+pad(o,3)+'{/u}  {n}'+pad(n,40)+'{n}'+q.rjust(11)+'   '+('{b}' if loc=='Cold' else '{n}')+loc)
    L+=['{n}'+' '*72+'More...','{n}Parameters or command',cmdline(),fkeys('F3=Exit   F5=Refresh   F9=Command Entry   F11=Sort   F12=Cancel')]
    return L[:24],None
def withdraw(kind='Withdraw'):
    cmd='WITHDRAW' if kind=='Withdraw' else 'CRAFT'
    L=head(f'{kind} Item ({cmd})','')
    L+=['','{n}Type choices, press Enter.','',
        '{n}Item . . . . . . . . . . . . .   {u}'+pad('encodedlogistics:ferrite',40)+'{/u}',
        '{n}Quantity . . . . . . . . . . .   {u}'+pad('256',10)+'{/u}  1-999999']
    if kind=='Withdraw':
        L+=['{n}Destination  . . . . . . . . .   {u}'+pad('*DRAWER',10)+'{/u}  *DRAWER, *INV','',
            '{d}  On hand . . . :   12,470   (12,470 on tape - recall ~14 s)']
    else:
        L+=['{n}Scheduler  . . . . . . . . . .   {u}'+pad('*AUTO',10)+'{/u}  *AUTO, name','{n}Destination  . . . . . . . . .   {u}'+pad('*NETWORK',10)+'{/u}  *NETWORK, *DRAWER, *INV','',
            '{d}  Plan  . . . . :   2 steps, 0 missing, recall +14 s']
    L+=['']*(20-len(L))+['{b}Ferrite recall started - 256 to desk drawer in ~14 s.' if kind=='Withdraw' else '{b}Job 0042 submitted.','','',
        fkeys('F3=Exit   F4=Prompt   F5=Refresh   F12=Cancel')]
    return L[:24],(37,6)
JOBS=[('0039','Processor Die','64','Active','62%','Rack R1'),('0040','Heatsink','16','Active','25%','Rack R1'),
      ('0041','2M Storage Drive','2','Waiting','0%','Core'),('0042','Ferrite','256','Recall','10%','Core')]
def jobs():
    L=head('Work with Jobs','WRKJOB')
    L+=['','{n}Type options, press Enter.','{n}  4=Cancel   5=Display','',
        '{b}Opt  Job   Item                        Qty  Status    Progress  Scheduler']
    for i,(j,n,q,s,p,sc) in enumerate(JOBS):
        bar='#'*int(int(p[:-1])/10)
        L.append('{u}'+pad('4' if i==1 else '',3)+'{/u}  {n}'+j+'  '+pad(n,26)+q.rjust(5)+'  '+('{b}' if s=='Active' else '{n}')+pad(s,9)+'{n} '+pad(bar,10)+p.rjust(4)+'  '+sc)
    L+=['']*(20-len(L))+['{n}Parameters or command',cmdline(),'{b}Job 0042 submitted.',fkeys('F3=Exit   F5=Refresh   F9=Command Entry   F12=Cancel')]
    return L[:24],None
DEV=[('Controller','Network Controller','120, 64, -40','4/32','Online'),('Drive Bay','Drive Bay','122, 64, -40','1','Online'),
     ('Rack','Server Rack R1','126, 64, -38','9','Online'),('  U3-U6','SAN','R1 U3','1','Online'),('  U9-U12','4U Tape Library','R1 U9','1','Recalling'),
     ('  U15','L3 Switch','R1 U15','1 / 32','Online'),('Terminal','Terminal Desk','118, 64, -36','1','Online'),('Port','Ingress Port','124, 63, -42','1','Online'),
     ('Port','Egress Port','125, 63, -42','1','Offline')]
def devices():
    L=head('Work with Devices','WRKDEV')
    L+=['','{n}Type options, press Enter.','{n}  5=Display   8=Locate','',
        '{b}Opt  Type        Device                  Location           Lanes    Status']
    for (t,n,loc,lanes,st) in DEV:
        L.append('{u}   {/u}  {n}'+pad(t,10)+'  '+pad(n,22)+'  '+pad(loc,17)+'  '+pad(lanes,7)+'  '+('{d}' if st=='Offline' else '{b}' if st!='Online' else '{n}')+st)
    L+=['']*(20-len(L))+['{n}Parameters or command',cmdline(),'',fkeys('F3=Exit   F5=Refresh   F9=Command Entry   F11=Sort   F12=Cancel')]
    return L[:24],None
def status():
    L=head('Display Network Status','DSPNETSTS')
    bar=lambda f,n=30: '['+'#'*int(f*n)+'.'*(n-int(f*n))+']'
    L+=['',
        '{b}Storage','{n}  Hot  . . . . . . . . :   '+bar(0.86)+'  18.4M / 21.4M   86%',
        '{n}  Cold . . . . . . . . :   '+bar(0.67)+'  14.8G / 22.0G   67%','',
        '{b}Energy','{n}  Stored . . . . . . . :   '+bar(0.54)+'  1.08M / 2.0M FE',
        '{n}  In / out . . . . . . :   +2,400 / -1,862 FE/t','',
        '{b}Lanes','{n}  Used . . . . . . . . :   '+bar(23/32)+'  23 / 32','',
        '{b}Crafting','{n}  Active jobs  . . . . :   2      Queued  . . :   2      Schedulers  . . :   2','',
        '{b}Devices','{n}  Online . . . . . . . :   41     Offline . . :   1      Fault . . . . . :   0']
    L+=['']*(21-len(L))+['{n}Press Enter to continue.','',fkeys('F3=Exit   F5=Refresh   F12=Cancel')]
    return L[:24],None
def command_entry():
    L=head('Command Entry','CMDENT',f'Request level:  1')
    L+=['','{d}  All previous commands and messages:',
        '{n}  > show drives',
        '{n}    BAY            SLOT  DRIVE     USED       TOTAL',
        '{n}    Drive Bay       1    2M        1.62M      2.10M',
        '{n}    Drive Bay       2    512K      388K       524K',
        '{n}    R1 SAN          1-18 mixed     12.9M      18.6M',
        '{n}  > show lanes',
        '{n}    Controller 23 / 32 lanes   (rack R1 pool 9 / 32 via L3 Switch)',
        '{n}  > withdraw ferrite 256',
        '{b}    Recall started: 256 Ferrite from tape, ETA 14 s -> desk drawer',
        '{n}  > craft processor_die 64',
        '{b}    Job 0043 submitted (Rack R1, 3 threads)',
        '{n}  > help',
        '{n}    help  show inventory|drives|lanes|jobs|devices|power  withdraw',
        '{n}    craft  cancel job  clear   (type help <command> for details)']
    L+=['']*(20-len(L))+['{n}Type command, press Enter.',cmdline('show jobs_'),'',fkeys('F3=Exit   F4=Prompt   F9=Retrieve   F12=Cancel   F13=Clear')]
    return L[:24],None
SCREENS={'main_menu':main_menu,'work_with_inventory':inventory,'withdraw_prompt':lambda: withdraw('Withdraw'),'craft_prompt':lambda: withdraw('Craft'),
         'work_with_jobs':jobs,'work_with_devices':devices,'network_status':status,'command_entry':command_entry}
