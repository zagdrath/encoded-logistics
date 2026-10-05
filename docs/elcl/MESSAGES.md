# ELCL Message IDs

Severity: 00 info, 10 warning, 20 error, 30 severe, 40 abnormal end.
Monitoring `ELCnn00` covers the whole `ELCnnxx` range; `ELC0000` covers all.
`&1`, `&2` are message data substitutions.

## ELC00xx — Language and runtime

| ID | Sev | Text |
|----|-----|------|
| ELC0001 | 30 | Syntax error at '&1'. |
| ELC0002 | 30 | Variable &1 is not declared. |
| ELC0003 | 30 | Value '&1' is not valid for type &2. |
| ELC0004 | 30 | Value &1 is outside the allowed range. |
| ELC0005 | 30 | Division by zero. |
| ELC0006 | 30 | List index &1 is out of range (size &2). |
| ELC0007 | 30 | Numeric overflow. |
| ELC0008 | 10 | Value truncated to length &1. |
| ELC0009 | 30 | &1 without matching &2. |
| ELC0010 | 30 | Label &1 not found. |
| ELC0011 | 30 | Call depth exceeded (max &1). |
| ELC0012 | 30 | Parameter count mismatch calling &1: expected &2, got &3. |
| ELC0013 | 30 | Called program &1 ended abnormally. |
| ELC0014 | 30 | GOTO into a loop or DO group is not allowed. |
| ELC0015 | 30 | List limit of &1 elements reached. |
| ELC0016 | 30 | DCL must come before other commands. |

## ELC01xx — Commands

| ID | Sev | Text |
|----|-----|------|
| ELC0101 | 30 | Command &1 not found. |
| ELC0102 | 30 | Required parameter &1 missing. |
| ELC0103 | 30 | Value '&1' not valid for parameter &2. |
| ELC0104 | 30 | Parameter &1 specified more than once. |
| ELC0105 | 30 | Command &1 is not allowed in a batch job. |
| ELC0106 | 30 | Command &1 is not allowed in an interactive job. |
| ELC0107 | 30 | Command &1 is not available yet. | *(added: a command with no implementation)*
| ELC0108 | 00 | Program &1 running in job &2. | *(added: CALL on a command line)*
| ELC0109 | 30 | Job &1 is already running program &2. | *(added)*
| ELC0110 | 00 | Program &1 ended normally. | *(added: an interactive CALL's end)*

## ELC02xx — Objects

| ID | Sev | Text |
|----|-----|------|
| ELC0201 | 30 | Library &1 not found. |
| ELC0202 | 30 | Member &1 not found in library &2. |
| ELC0203 | 30 | Program &1 not found in library &2. |
| ELC0204 | 30 | Object &1 already exists in library &2. |
| ELC0205 | 30 | Library &1 is read-only. |
| ELC0206 | 30 | Program &1 was not created: compile errors. |
| ELC0207 | 30 | Not enough network storage to save &1. |
| ELC0208 | 30 | Member &1 is locked by another user. |
| ELC0210 | 00 | Library &1 created. |
| ELC0211 | 00 | Library &1 deleted. |
| ELC0212 | 00 | Library &1 changed. |
| ELC0213 | 00 | Member &1 saved in library &2. |
| ELC0214 | 00 | Member &1 created in library &2. |
| ELC0215 | 00 | Member &1 copied to &2. |
| ELC0216 | 00 | Member &1 renamed to &2. |
| ELC0217 | 00 | Member &1 deleted from library &2. |
| ELC0218 | 00 | Program &1 created in library &2. |
| ELC0219 | 00 | Program &1 deleted from library &2. |
| ELC0220 | 00 | Library &1 saved to &2. |
| ELC0221 | 00 | Library &1 restored from &2. |
| ELC0222 | 00 | System value &1 changed. |

## ELC03xx — Jobs

| ID | Sev | Text |
|----|-----|------|
| ELC0301 | 30 | No job host available to run batch job &1. |
| ELC0302 | 30 | Job &1 not found. |
| ELC0303 | 40 | Job ended by operator. |
| ELC0304 | 00 | Job &1 submitted to job queue on &2. |
| ELC0305 | 30 | Schedule entry &1 already exists. |
| ELC0306 | 30 | Trigger &1 already exists. |
| ELC0307 | 00 | Job &1 ended normally. |
| ELC0308 | 00 | Job &1 held. |
| ELC0309 | 00 | Job &1 released. |
| ELC0310 | 40 | Job ended: job host &1 was unloaded or lost power. |
| ELC0311 | 00 | Job &1 ending (&2). |
| ELC0312 | 00 | Schedule entry &1 added. |
| ELC0313 | 00 | Schedule entry &1 removed. |
| ELC0314 | 00 | Trigger &1 added. |
| ELC0315 | 00 | Trigger &1 removed. |

## ELC04xx — Security

| ID | Sev | Text |
|----|-----|------|
| ELC0401 | 30 | Not authorized: user &1 lacks &2 permission. |
| ELC0402 | 30 | Sign-on failed for user &1. |

## ELC12xx — Inventory and storage

| ID | Sev | Text |
|----|-----|------|
| ELC1201 | 30 | Item &1 not found in network. |
| ELC1202 | 30 | Only &1 of &2 items available. |
| ELC1203 | 00 | Item &1 is being recalled from tape. |
| ELC1204 | 30 | Network storage is full. |
| ELC1205 | 30 | Item name &1 is ambiguous; use a qualified ID. |
| ELC1206 | 30 | No Tape Library on the network. |
| ELC1207 | 30 | &1 is a &2 resource, not &3. | *(added: resource types - the ID names a fluid asked for as a gas, or the other way round)* |
| ELC1208 | 30 | Resource type &1 is not valid for device &2. | *(added: resource types - the device's faced block has no tanks for that type)* |

## ELC13xx — Devices

| ID | Sev | Text |
|----|-----|------|
| ELC1301 | 30 | Device &1 not found. |
| ELC1302 | 30 | Device &1 is offline. |
| ELC1303 | 30 | Device &1 (type &2) does not support this operation. |
| ELC1304 | 30 | Device &1 is not facing an inventory. |
| ELC1305 | 30 | Filter on device &1 is full. |
| ELC1306 | 30 | Printer &1 is out of paper. |
| ELC1307 | 00 | Spooled file &1 printed on &2. | *(added: the completion message of printing)*
| ELC1308 | 30 | Device name &1 is already used on &2. | *(added: RNMDEV)*
| ELC1309 | 00 | Device &1 renamed to &2. | *(added: RNMDEV)*
| ELC1310 | 30 | No diskette in device &1. | *(added: SAVLIB / RSTLIB)*
| ELC1311 | 30 | Library &1 needs &2 bytes; the diskette has &3 free. | *(added: SAVLIB)*
| ELC1312 | 30 | Image &1 not found in the images folder. | *(added: Display Panels)*
| ELC1313 | 30 | Image &1 is larger than the allowed size (&2). | *(added: Display Panels)*
| ELC1314 | 30 | Region &1 is outside the screen or overlaps another region. | *(added: Display Panels)*
| ELC1315 | 30 | Images are disabled on this server. | *(added: Display Panels)*
| ELC1316 | 30 | Data source &1 is not available for this widget. | *(added: Display Panels)*
| ELC1317 | 10 | Colour mode &1 is above the server's limit; &2 used. | *(added: Display Panels; a diagnostic, the image still shows)*
| ELC1318 | 30 | Device &1 is not an Arcforge machine. | *(added: machine commands, `*MCHOPS` / `*MCHFE` graphs)*
| ELC1319 | 30 | Machine &1 is not formed. | *(added: machine commands)*
| ELC1320 | 30 | Machine &1 does not support &2. | *(added: machine commands; &2 the setting)*
| ELC1321 | 30 | Machine &1 rejected &2(&3). | *(added: machine commands; the setting and the value refused)*
| ELC1322 | 00 | Machine &1 changed. | *(added: `CHGMCHCFG` completed)*

## ELC14xx — Crafting

| ID | Sev | Text |
|----|-----|------|
| ELC1401 | 30 | No Scheduler available. |
| ELC1402 | 30 | No recipe known for &1. |
| ELC1403 | 30 | Missing ingredients for &1. |
| ELC1404 | 30 | Craft job &1 not found. |

## ELC15xx — PLCs *(added)*

| ID | Sev | Text |
|----|-----|------|
| ELC1501 | 30 | Module &1 is not installed or is not a &2. | *(`RTVSNSVAL` without `RTNSTS`: the slot is empty, or not the `TYPE()` asked for)*
| ELC1502 | 30 | Command &1 needs a network; this PLC is not cabled to one. | *(when the editor's save compiles the program for a PLC on its own, and at run time)*
| ELC1503 | 20 | Sensor module &1 has no target (&2). | *(`RTVSNSVAL` without `RTNSTS`: nothing to read on its face)*
| ELC1504 | 30 | Program &1 was not compiled for a PLC. | *(`SNDPLCPGM` of a program not made with `CRTELPGM TGT(*PLC)`)*
| ELC1505 | 30 | PLC &1 has no program loaded. | *(`STRPLC`, F6, option 4)*
| ELC1506 | 10 | RETAIN is only meaningful in a PLC program; ignored. | *(a compile warning: the program is still created)*
| ELC1507 | 00 | PLC &1 started. |
| ELC1508 | 00 | PLC &1 stopped. |
| ELC1509 | 00 | Program &1 loaded into PLC &2. | *(added: `SNDPLCPGM` completed)*

## ELC22xx — Database files *(added)*

Reading and writing files (`ELC2201` is how `RCVF` says the file has no more records; monitor it, or the range with
`ELC2200`):

| ID | Sev | Text |
|----|-----|------|
| ELC2201 | 30 | End of file &1 reached. |
| ELC2202 | 30 | Record with key &1 not found in file &2. |
| ELC2203 | 30 | Duplicate key &1 in file &2. |
| ELC2204 | 30 | No record read from file &1 to change or delete. |
| ELC2205 | 30 | File &1 not found in library &2. |
| ELC2206 | 30 | File with open identifier &1 not declared. |
| ELC2207 | 30 | Level check on file &1: &2 changed since the program was compiled. |
| ELC2208 | 30 | File &1 is full: limit of &2 records. |
| ELC2209 | 30 | Value '&1' not valid for field &2. |

A file's definition (a `PF` member: the editor's check and CRTPF's listing, with the line):

| ID | Sev | Text |
|----|-----|------|
| ELC2220 | 30 | Definition statement not valid at '&1'. |
| ELC2221 | 30 | Field &1 defined more than once. |
| ELC2222 | 30 | Length &1 not valid for field &2 (type &3). |
| ELC2223 | 30 | Key field &1 is not a field of the record. |
| ELC2224 | 30 | Too many &1 (max &2). |
| ELC2225 | 30 | No record format (R) defined before the fields. |
| ELC2226 | 30 | Keyword &1 not valid here. |
| ELC2227 | 30 | Type &1 not valid for field &2. |
| ELC2228 | 30 | Name &1 not valid. |

The commands on files:

| ID | Sev | Text |
|----|-----|------|
| ELC2230 | 00 | File &1 created in library &2. |
| ELC2231 | 00 | File &1 changed in library &2: &3 records kept. |
| ELC2232 | 10 | Field &1 dropped from file &2: its values are lost. |
| ELC2233 | 00 | File &1 deleted from library &2. |
| ELC2234 | 00 | File &1 cleared: &2 records removed. |
| ELC2235 | 00 | &1 records copied to file &2. |
| ELC2236 | 00 | &1 records copied to &2. |
| ELC2237 | 00 | &1 records copied from &2. |
| ELC2238 | 00 | Query selected &1 of &2 records. |
| ELC2239 | 30 | File &1 was not created: definition errors. |
| ELC2240 | 30 | Stream file &1 not found. |
| ELC2241 | 30 | Folder sync is off: &1 needs the system's folder. |
| ELC2242 | 30 | Field &1 not found in file &2. |
| ELC2243 | 30 | Row &1: value '&2' not valid for field &3. |
| ELC2244 | 30 | Files &1 and &2 have no fields in common. |
| ELC2245 | 30 | Stream file name &1 not valid. |
| ELC2246 | 30 | File &1 was not changed: definition errors. |
| ELC2247 | 30 | Member &1 is not a &2 source member. |

## USRxxxx — User messages

Raised with `SNDPGMMSG MSGTYPE(*ESCAPE)`; default `USR0001`.
