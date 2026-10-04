/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.cmd;

import static net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition.Auth.CONFIGURE;
import static net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition.Auth.CRAFT;
import static net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition.Auth.EXTRACT;
import static net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition.Auth.INSERT;
import static net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition.Auth.VIEW;
import static net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition.Context.INTERACTIVE;
import static net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition.Context.PROGRAM;

import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef.Kind;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef.ValueList;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef.VarType;

// The built-in commands' schemas (COMMANDS.md 1-9, plus the few the OS screens run: CHGLIB, CRTMBR, CHGJOB, the hold
// and release of schedule entries and triggers, WRKCRFJOB, GO and CLEAR). Executors are bound by the game side.
final class BuiltinCommands {
    private static final String[] SIDES = { "*NORTH", "*SOUTH", "*EAST", "*WEST", "*UP", "*DOWN" };

    private BuiltinCommands() {}

    private static ParamDef.Builder p(String keyword, String label, Kind kind) {
        return ParamDef.of(keyword, label, kind);
    }

    private static ParamDef.Builder rtn(String keyword, String label, VarType type) {
        return ParamDef.of(keyword, label, Kind.VARIABLE).returns(type);
    }

    private static ParamDef.Builder tier() {
        return p("TIER", "Storage tier", Kind.SPECIAL).sv("*ALL", "*HOT", "*COLD").dft("*ALL");
    }

    private static ParamDef.Builder yesNo(String keyword, String label, String dft) {
        return p(keyword, label, Kind.SPECIAL).sv("*NO", "*YES").dft(dft);
    }

    private static String[] sides(String extra) {
        String[] all = new String[SIDES.length + 1];
        System.arraycopy(SIDES, 0, all, 0, SIDES.length);
        all[SIDES.length] = extra;
        return all;
    }

    private static void add(CommandDefinition.Builder command) {
        CommandRegistry.register(command.build());
    }

    static void register() {
        // --- 1. Program control ---
        add(CommandDefinition.of("PGM", "Program").context(PROGRAM).positional(1)
                .p(p("PARM", "Parameters", Kind.VARIABLE).list(255)));
        add(CommandDefinition.of("ENDPGM", "End Program").context(PROGRAM));
        add(CommandDefinition.of("DCL", "Declare Variable").context(PROGRAM).positional(4)
                .p(p("VAR", "Variable", Kind.VARIABLE).req())
                .p(p("TYPE", "Type", Kind.SPECIAL).req().sv("*CHAR", "*INT", "*DEC", "*LGL", "*LIST"))
                .p(p("LEN", "Length", Kind.INT).list(2))
                .p(p("VALUE", "Initial value", Kind.VALUE)));
        add(CommandDefinition.of("CHGVAR", "Change Variable").context(PROGRAM).positional(2)
                .p(p("VAR", "Variable", Kind.VARIABLE).req())
                .p(p("VALUE", "New value", Kind.VALUE).req()));
        add(CommandDefinition.of("IF", "If").context(PROGRAM).positional(2)
                .p(p("COND", "Condition", Kind.LGL).req())
                .p(p("THEN", "Command", Kind.COMMAND)));
        add(CommandDefinition.of("ELSE", "Else").context(PROGRAM).positional(1)
                .p(p("CMD", "Command", Kind.COMMAND)));
        add(CommandDefinition.of("DO", "Do Group").context(PROGRAM));
        add(CommandDefinition.of("ENDDO", "End Do Group").context(PROGRAM));
        add(CommandDefinition.of("DOWHILE", "Do While").context(PROGRAM).positional(1)
                .p(p("COND", "Condition", Kind.LGL).req()));
        add(CommandDefinition.of("DOUNTIL", "Do Until").context(PROGRAM).positional(1)
                .p(p("COND", "Condition", Kind.LGL).req()));
        add(CommandDefinition.of("DOFOR", "Do For").context(PROGRAM).positional(4)
                .p(rtn("VAR", "Control variable", VarType.INT).req())
                .p(p("FROM", "From value", Kind.INT).req())
                .p(p("TO", "To value", Kind.INT).req())
                .p(p("BY", "Increment", Kind.INT).dft("1")));
        add(CommandDefinition.of("FOREACH", "For Each Element").context(PROGRAM).positional(2)
                .p(rtn("VAR", "Element variable", VarType.CHAR).req())
                .p(p("IN", "List", Kind.VARIABLE).req()));
        add(CommandDefinition.of("ENDFOR", "End For Each").context(PROGRAM));
        add(CommandDefinition.of("LEAVE", "Leave Loop").context(PROGRAM).positional(1)
                .p(p("CMDLBL", "Loop label", Kind.LABEL).sv("*CURRENT").dft("*CURRENT")));
        add(CommandDefinition.of("ITERATE", "Iterate Loop").context(PROGRAM).positional(1)
                .p(p("CMDLBL", "Loop label", Kind.LABEL).sv("*CURRENT").dft("*CURRENT")));
        add(CommandDefinition.of("SELECT", "Select").context(PROGRAM));
        add(CommandDefinition.of("WHEN", "When").context(PROGRAM).positional(2)
                .p(p("COND", "Condition", Kind.LGL).req())
                .p(p("THEN", "Command", Kind.COMMAND)));
        add(CommandDefinition.of("OTHERWISE", "Otherwise").context(PROGRAM).positional(1)
                .p(p("CMD", "Command", Kind.COMMAND)));
        add(CommandDefinition.of("ENDSELECT", "End Select").context(PROGRAM));
        add(CommandDefinition.of("GOTO", "Go To").context(PROGRAM).positional(1)
                .p(p("CMDLBL", "Label", Kind.LABEL).req()));
        add(CommandDefinition.of("RETURN", "Return").context(PROGRAM));
        add(CommandDefinition.of("SUBR", "Subroutine").context(PROGRAM).positional(1)
                .p(p("SUBR", "Subroutine", Kind.NAME).req()));
        add(CommandDefinition.of("ENDSUBR", "End Subroutine").context(PROGRAM).positional(1)
                .p(p("RTNVAL", "Return value", Kind.INT).dft("0")));
        add(CommandDefinition.of("CALLSUBR", "Call Subroutine").context(PROGRAM).positional(2)
                .p(p("SUBR", "Subroutine", Kind.NAME).req())
                .p(rtn("RTNVAL", "Return value", VarType.INT)));
        add(CommandDefinition.of("MONMSG", "Monitor Message").context(PROGRAM).positional(3)
                .p(p("MSGID", "Message identifier", Kind.MSGID).req().list(50))
                .p(p("CMPDTA", "Comparison data", Kind.CHAR).dft("*NONE").sv("*NONE"))
                .p(p("EXEC", "Command to execute", Kind.COMMAND)));
        add(CommandDefinition.of("RCVMSG", "Receive Message").context(PROGRAM)
                .p(p("MSGTYPE", "Message type", Kind.SPECIAL).sv("*EXCP", "*LAST").dft("*EXCP"))
                .p(rtn("RTNMSGID", "Return message ID", VarType.CHAR))
                .p(rtn("RTNMSG", "Return message text", VarType.CHAR))
                .p(rtn("RTNMSGDTA", "Return message data", VarType.CHAR)));
        add(CommandDefinition.of("SNDPGMMSG", "Send Program Message").positional(1)
                .p(p("MSG", "Message text", Kind.CHAR).len(64))
                .p(p("MSGID", "Message identifier", Kind.MSGID).dft("USR0001"))
                .p(p("MSGTYPE", "Message type", Kind.SPECIAL).sv("*INFO", "*ESCAPE").dft("*INFO")));
        add(CommandDefinition.of("CALL", "Call Program").positional(2)
                .p(p("PGM", "Program", Kind.QUALIFIED).req().values(ValueList.PROGRAMS))
                .p(p("PARM", "Parameters", Kind.VALUE).list(255)));
        add(CommandDefinition.of("DLYJOB", "Delay Job").positional(1)
                .p(p("DLY", "Delay time, in seconds", Kind.INT).range(1, 86_400))
                .p(p("RSMTIME", "Resume time", Kind.TIME)));
        add(CommandDefinition.of("RTVJOBA", "Retrieve Job Attributes")
                .p(rtn("RTNUSR", "Return user", VarType.CHAR))
                .p(rtn("RTNJOB", "Return job name", VarType.CHAR))
                .p(rtn("RTNTYPE", "Return job type", VarType.CHAR))
                .p(rtn("RTNHOST", "Return job host", VarType.CHAR)));

        // --- 2. Lists ---
        add(CommandDefinition.of("ADDLSTE", "Add List Element").context(PROGRAM).positional(2)
                .p(rtn("LIST", "List", VarType.LIST).req())
                .p(p("VALUE", "Value", Kind.CHAR).req())
                .p(p("POS", "Position", Kind.INT).sv("*END").dft("*END").range(1, 4_096)));
        add(CommandDefinition.of("RMVLSTE", "Remove List Element").context(PROGRAM).positional(2)
                .p(rtn("LIST", "List", VarType.LIST).req())
                .p(p("POS", "Position", Kind.INT).req().range(1, 4_096)));
        add(CommandDefinition.of("CLRLST", "Clear List").context(PROGRAM).positional(1)
                .p(rtn("LIST", "List", VarType.LIST).req()));

        // --- 3. Inventory ---
        add(CommandDefinition.of("RTVITMCNT", "Retrieve Item Count").auth(VIEW).positional(1)
                .p(p("ITEM", "Item", Kind.ITEM).req().len(64))
                .p(tier())
                .p(rtn("RTNCOUNT", "Return count", VarType.INT).req())
                .p(p("NOTFND", "If not found", Kind.SPECIAL).sv("*ERROR", "*ZERO").dft("*ERROR")));
        add(CommandDefinition.of("RTVITMLST", "Retrieve Item List").auth(VIEW).positional(1)
                .p(p("FILTER", "Filter", Kind.CHAR).sv("*ALL").dft("*ALL").len(32))
                .p(tier())
                .p(p("MAX", "Maximum items", Kind.INT).sv("*NOMAX").dft("*NOMAX").range(1, 4_096))
                .p(p("SORT", "Sort by", Kind.SPECIAL).sv("*NAME", "*QTY").dft("*NAME"))
                .p(rtn("RTNLST", "Return list", VarType.LIST).req()));
        add(CommandDefinition.of("MOVITM", "Move Item").auth(EXTRACT).positional(3)
                .p(p("ITEM", "Item", Kind.ITEM).req().len(64))
                .p(p("QTY", "Quantity", Kind.INT).req().sv("*ALL").range(1, Long.MAX_VALUE))
                .p(p("TODEV", "To device", Kind.DEVICE).req().sv("*DESK"))
                .p(yesNo("PARTIAL", "Allow partial move", "*YES"))
                .p(rtn("RTNMOVED", "Return quantity moved", VarType.INT)));
        add(CommandDefinition.of("IMPITM", "Import Items").auth(INSERT).positional(1)
                .p(p("FROMDEV", "From device", Kind.DEVICE).req())
                .p(p("ITEM", "Item", Kind.ITEM).sv("*ALL").dft("*ALL").len(64))
                .p(p("QTY", "Quantity", Kind.INT).sv("*ALL").dft("*ALL").range(1, Long.MAX_VALUE))
                .p(rtn("RTNMOVED", "Return quantity moved", VarType.INT)));
        add(CommandDefinition.of("CHGITMTIER", "Change Item Tier").auth(CONFIGURE).positional(2)
                .p(p("ITEM", "Item", Kind.ITEM).req().len(64))
                .p(p("TIER", "Storage tier", Kind.SPECIAL).req().sv("*HOT", "*COLD", "*PIN", "*AUTO")));
        add(CommandDefinition.of("RTVSTGSTS", "Retrieve Storage Status").auth(VIEW)
                .p(tier())
                .p(rtn("RTNUSED", "Return used", VarType.INT))
                .p(rtn("RTNTOTAL", "Return total", VarType.INT))
                .p(rtn("RTNPCT", "Return percent used", VarType.DEC)));

        // --- 4. Crafting ---
        add(CommandDefinition.of("STRCRAFT", "Start Crafting").auth(CRAFT).positional(2)
                .p(p("ITEM", "Item", Kind.ITEM).req().len(64))
                .p(p("QTY", "Quantity", Kind.INT).req().range(1, 999_999))
                .p(p("SCHEDULER", "Scheduler", Kind.NAME).sv("*ANY").dft("*ANY"))
                .p(p("MISSING", "Missing ingredients", Kind.SPECIAL).sv("*FAIL", "*PARTIAL").dft("*FAIL"))
                .p(yesNo("WAIT", "Wait for completion", "*NO"))
                .p(rtn("RTNCRFJOB", "Return craft job ID", VarType.CHAR)));
        add(CommandDefinition.of("RTVCRFSTS", "Retrieve Craft Status").auth(VIEW).positional(1)
                .p(p("CRFJOB", "Craft job", Kind.NAME).req())
                .p(rtn("RTNSTS", "Return status", VarType.CHAR))
                .p(rtn("RTNPCT", "Return percent done", VarType.DEC)));
        add(CommandDefinition.of("ENDCRAFT", "End Crafting").auth(CRAFT).positional(1)
                .p(p("CRFJOB", "Craft job", Kind.NAME).req()));

        // --- 5. Devices ---
        add(CommandDefinition.of("RTVDEVSTS", "Retrieve Device Status").auth(VIEW).positional(1)
                .p(p("DEV", "Device", Kind.DEVICE).req())
                .p(rtn("RTNSTS", "Return status", VarType.CHAR))
                .p(rtn("RTNTYPE", "Return device type", VarType.CHAR)));
        add(CommandDefinition.of("RTVDEVLST", "Retrieve Device List").auth(VIEW)
                .p(p("TYPE", "Device type", Kind.NAME).sv("*ALL").dft("*ALL"))
                .p(p("STATUS", "Status", Kind.SPECIAL).sv("*ALL", "*ONLINE", "*OFFLINE", "*FAULT", "*DISABLED").dft("*ALL"))
                .p(rtn("RTNLST", "Return list", VarType.LIST).req()));
        add(CommandDefinition.of("CHGDEVSTS", "Change Device Status").auth(CONFIGURE).positional(2)
                .p(p("DEV", "Device", Kind.DEVICE).req())
                .p(p("STATUS", "Status", Kind.SPECIAL).req().sv("*ENABLE", "*DISABLE")));
        add(CommandDefinition.of("CHGDEVFTR", "Change Device Filter").auth(CONFIGURE).positional(2)
                .p(p("DEV", "Device", Kind.DEVICE).req())
                .p(p("ACTION", "Action", Kind.SPECIAL).req().sv("*ADD", "*RMV", "*CLR"))
                .p(p("ITEM", "Item", Kind.ITEM).len(64)));
        add(CommandDefinition.of("RTVLANES", "Retrieve Lanes").auth(VIEW)
                .p(rtn("RTNUSED", "Return lanes used", VarType.INT))
                .p(rtn("RTNTOTAL", "Return lanes total", VarType.INT)));

        // --- 6. Power ---
        add(CommandDefinition.of("RTVPWRSTS", "Retrieve Power Status").auth(VIEW)
                .p(rtn("RTNSRC", "Return power source", VarType.CHAR))
                .p(rtn("RTNCHG", "Return UPS charge", VarType.DEC))
                .p(rtn("RTNLOAD", "Return load (FE/t)", VarType.INT))
                .p(rtn("RTNSTORED", "Return energy stored", VarType.INT)));

        // --- 7. Redstone (Control Interface) ---
        add(CommandDefinition.of("RTVRSIN", "Retrieve Redstone Input").auth(VIEW).positional(2)
                .p(p("DEV", "Device", Kind.DEVICE).req())
                .p(p("SIDE", "Side", Kind.SPECIAL).req().sv(sides("*MAX")))
                .p(rtn("RTNLVL", "Return level", VarType.INT).req()));
        add(CommandDefinition.of("CHGRSOUT", "Change Redstone Output").auth(CONFIGURE).positional(3)
                .p(p("DEV", "Device", Kind.DEVICE).req())
                .p(p("SIDE", "Side", Kind.SPECIAL).req().sv(sides("*ALL")))
                .p(p("LVL", "Level", Kind.INT).req().range(0, 15)));

        // --- 8. Messages, displays and output ---
        add(CommandDefinition.of("SNDMSG", "Send Message").positional(1)
                .p(p("MSG", "Message text", Kind.CHAR).req().len(64))
                .p(p("TOUSR", "To user", Kind.NAME).sv("*REQUESTER", "*SYSOPR", "*ALL").dft("*REQUESTER"))
                .p(p("TOTRM", "To terminal", Kind.DEVICE).sv("*NONE").dft("*NONE")));
        add(CommandDefinition.of("DSPMSG", "Display Messages").context(INTERACTIVE).positional(1)
                .p(p("USR", "User", Kind.NAME).sv("*CURRENT").dft("*CURRENT")));
        add(CommandDefinition.of("SNDDSPTXT", "Send Display Text").positional(2)
                .p(p("DEV", "Device", Kind.DEVICE).req())
                .p(p("TEXT", "Text", Kind.CHAR).req().len(64))
                .p(p("LINE", "Line", Kind.INT).sv("*NEXT").dft("*NEXT").range(1, 64))
                .p(yesNo("CLEAR", "Clear first", "*NO")));
        add(CommandDefinition.of("PRTTXT", "Print Text").positional(1)
                .p(p("TEXT", "Text", Kind.CHAR).req().len(64))
                .p(p("SPLF", "Spooled file", Kind.NAME).sv("*JOB").dft("*JOB")));
        add(CommandDefinition.of("PRTRPT", "Print Report").positional(1)
                .p(p("RPT", "Report", Kind.SPECIAL).req().sv("*INV", "*DEV", "*JOBLOG", "*SPLF"))
                .p(p("SPLF", "Spooled file", Kind.NAME).sv("*LAST").dft("*LAST"))
                .p(p("DEV", "Printer", Kind.DEVICE).sv("*DFT").dft("*DFT")));

        // --- 9. OS commands ---
        add(CommandDefinition.of("WRKLIB", "Work with Libraries").context(INTERACTIVE));
        add(CommandDefinition.of("CRTLIB", "Create Library").positional(2)
                .p(p("LIB", "Library", Kind.NAME).req().values(ValueList.LIBRARIES))
                .p(p("TEXT", "Text 'description'", Kind.CHAR).sv("*BLANK").dft("*BLANK").len(50))
                .p(p("TYPE", "Library type", Kind.SPECIAL).sv("*PROD", "*TEST").dft("*PROD")));
        add(CommandDefinition.of("CHGLIB", "Change Library").positional(1)
                .p(p("LIB", "Library", Kind.NAME).req().values(ValueList.LIBRARIES))
                .p(p("TEXT", "Text 'description'", Kind.CHAR).sv("*SAME").dft("*SAME").len(50))
                .p(p("AUT", "Public authority", Kind.SPECIAL).sv("*SAME", "*USE", "*CHANGE").dft("*SAME")));
        add(CommandDefinition.of("DLTLIB", "Delete Library").positional(1)
                .p(p("LIB", "Library", Kind.NAME).req().values(ValueList.LIBRARIES)));
        add(CommandDefinition.of("WRKMBR", "Work with Members").context(INTERACTIVE).positional(1)
                .p(p("LIB", "Library", Kind.NAME).sv("*CURLIB").dft("*CURLIB").values(ValueList.LIBRARIES)));
        add(CommandDefinition.of("EDTMBR", "Edit Member").context(INTERACTIVE).positional(1)
                .p(p("MBR", "Member", Kind.QUALIFIED).req().values(ValueList.MEMBERS)));
        add(CommandDefinition.of("CRTMBR", "Create Member").positional(2)
                .p(p("MBR", "Member", Kind.QUALIFIED).req().values(ValueList.MEMBERS))
                .p(p("TEXT", "Text 'description'", Kind.CHAR).sv("*BLANK").dft("*BLANK").len(48)));
        add(CommandDefinition.of("CPYMBR", "Copy Member").positional(2)
                .p(p("FROM", "From member", Kind.QUALIFIED).req().values(ValueList.MEMBERS))
                .p(p("TO", "To member", Kind.QUALIFIED).req()));
        add(CommandDefinition.of("RNMMBR", "Rename Member").positional(2)
                .p(p("MBR", "Member", Kind.QUALIFIED).req().values(ValueList.MEMBERS))
                .p(p("NEWNAME", "New name", Kind.NAME).req()));
        add(CommandDefinition.of("DLTMBR", "Delete Member").positional(1)
                .p(p("MBR", "Member", Kind.QUALIFIED).req().values(ValueList.MEMBERS)));
        add(CommandDefinition.of("CRTELPGM", "Create ELCL Program").positional(2)
                .p(p("PGM", "Program", Kind.QUALIFIED).req().values(ValueList.MEMBERS))
                .p(p("SRCMBR", "Source member", Kind.QUALIFIED).sv("*PGM").dft("*PGM").values(ValueList.MEMBERS)));
        add(CommandDefinition.of("DLTPGM", "Delete Program").positional(1)
                .p(p("PGM", "Program", Kind.QUALIFIED).req().values(ValueList.PROGRAMS)));
        add(CommandDefinition.of("SBMJOB", "Submit Job").positional(1)
                .p(p("CMD", "Command to run", Kind.COMMAND).req().len(64))
                .p(p("JOB", "Job name", Kind.NAME).sv("*JOBD").dft("*JOBD"))
                .p(p("HOST", "Job host", Kind.DEVICE).sv("*ANY").dft("*ANY"))
                .p(yesNo("LOG", "Log commands", "*NO")));
        add(CommandDefinition.of("WRKACTJOB", "Work with Active Jobs").context(INTERACTIVE));
        add(CommandDefinition.of("WRKJOB", "Work with Job").context(INTERACTIVE).positional(1)
                .p(p("JOB", "Job name", Kind.NAME).sv("*").dft("*").len(28).values(ValueList.JOBS)));
        add(CommandDefinition.of("DSPJOBLOG", "Display Job Log").context(INTERACTIVE).positional(1)
                .p(p("JOB", "Job name", Kind.NAME).sv("*").dft("*").len(28).values(ValueList.JOBS)));
        add(CommandDefinition.of("CHGJOB", "Change Job").positional(1)
                .p(p("JOB", "Job name", Kind.NAME).req().len(28).values(ValueList.JOBS))
                .p(p("JOBPTY", "Job priority", Kind.INT).sv("*SAME").dft("*SAME").range(1, 9))
                .p(p("LOG", "Log commands", Kind.SPECIAL).sv("*SAME", "*NO", "*YES").dft("*SAME")));
        add(CommandDefinition.of("HLDJOB", "Hold Job").positional(1)
                .p(p("JOB", "Job name", Kind.NAME).req().len(28).values(ValueList.JOBS)));
        add(CommandDefinition.of("RLSJOB", "Release Job").positional(1)
                .p(p("JOB", "Job name", Kind.NAME).req().len(28).values(ValueList.JOBS)));
        add(CommandDefinition.of("ENDJOB", "End Job").positional(2)
                .p(p("JOB", "Job name", Kind.NAME).req().len(28).values(ValueList.JOBS))
                .p(p("OPTION", "How to end", Kind.SPECIAL).sv("*CNTRLD", "*IMMED").dft("*CNTRLD")));
        add(CommandDefinition.of("ADDJOBSCDE", "Add Job Schedule Entry").positional(2)
                .p(p("JOB", "Job name", Kind.NAME).req())
                .p(p("CMD", "Command to run", Kind.COMMAND).req().len(64))
                .p(p("FRQ", "Frequency", Kind.SPECIAL).sv("*ONCE", "*INTERVAL", "*DAILY").dft("*ONCE"))
                .p(p("TIME", "Schedule time", Kind.TIME).sv("*CURRENT").dft("*CURRENT"))
                .p(p("INTERVAL", "Interval, in seconds", Kind.INT).range(1, 604_800)));
        add(CommandDefinition.of("RMVJOBSCDE", "Remove Job Schedule Entry").positional(1)
                .p(p("JOB", "Job name", Kind.NAME).req()));
        add(CommandDefinition.of("HLDJOBSCDE", "Hold Job Schedule Entry").positional(1)
                .p(p("JOB", "Job name", Kind.NAME).req()));
        add(CommandDefinition.of("RLSJOBSCDE", "Release Job Schedule Entry").positional(1)
                .p(p("JOB", "Job name", Kind.NAME).req()));
        add(CommandDefinition.of("WRKJOBSCDE", "Work with Job Schedule Entries").context(INTERACTIVE));
        add(CommandDefinition.of("ADDTRGEVT", "Add Trigger Event").positional(3)
                .p(p("TRG", "Trigger", Kind.NAME).req())
                .p(p("EVENT", "Event", Kind.SPECIAL).req().sv("*ITMBELOW", "*ITMABOVE", "*STGFULL", "*DEVFAULT", "*DEVONLINE", "*DEVOFFLINE",
                        "*PWRUPS", "*PWRRESTORED", "*CRAFTEND", "*RSCHANGE"))
                .p(p("PGM", "Program", Kind.QUALIFIED).req().values(ValueList.PROGRAMS))
                .p(p("ITEM", "Item", Kind.ITEM).sv("*ANY").dft("*ANY").len(64))
                .p(p("DEV", "Device", Kind.DEVICE).sv("*ANY").dft("*ANY"))
                .p(p("VALUE", "Value", Kind.CHAR).sv("*NONE").dft("*NONE").len(16)));
        add(CommandDefinition.of("RMVTRGEVT", "Remove Trigger Event").positional(1)
                .p(p("TRG", "Trigger", Kind.NAME).req()));
        add(CommandDefinition.of("HLDTRGEVT", "Hold Trigger Event").positional(1)
                .p(p("TRG", "Trigger", Kind.NAME).req()));
        add(CommandDefinition.of("RLSTRGEVT", "Release Trigger Event").positional(1)
                .p(p("TRG", "Trigger", Kind.NAME).req()));
        add(CommandDefinition.of("WRKTRGEVT", "Work with Trigger Events").context(INTERACTIVE));
        add(CommandDefinition.of("WRKSYSVAL", "Work with System Values").context(INTERACTIVE));
        add(CommandDefinition.of("RTVSYSVAL", "Retrieve System Value").positional(2)
                .p(p("SYSVAL", "System value", Kind.NAME).req().values(ValueList.SYSVALS))
                .p(rtn("RTNVAR", "Return variable", VarType.CHAR).req()));
        add(CommandDefinition.of("CHGSYSVAL", "Change System Value").positional(2)
                .p(p("SYSVAL", "System value", Kind.NAME).req().values(ValueList.SYSVALS))
                .p(p("VALUE", "New value", Kind.CHAR).req().len(24)));
        add(CommandDefinition.of("SAVLIB", "Save Library").positional(2)
                .p(p("LIB", "Library", Kind.NAME).req().values(ValueList.LIBRARIES))
                .p(p("DEV", "Device", Kind.DEVICE).req()));
        add(CommandDefinition.of("RSTLIB", "Restore Library").positional(2)
                .p(p("LIB", "Library", Kind.NAME).req())
                .p(p("DEV", "Device", Kind.DEVICE).req()));
        add(CommandDefinition.of("WRKDEV", "Work with Devices").context(INTERACTIVE));
        add(CommandDefinition.of("WRKINV", "Work with Inventory").context(INTERACTIVE).positional(1)
                .p(p("FILTER", "Position to", Kind.CHAR).sv("*ALL").dft("*ALL").len(30)));
        add(CommandDefinition.of("WRKCRFJOB", "Work with Crafting Jobs").context(INTERACTIVE));
        add(CommandDefinition.of("DSPNETSTS", "Display Network Status").context(INTERACTIVE));
        add(CommandDefinition.of("WRKSPLF", "Work with Spooled Files").context(INTERACTIVE).positional(1)
                .p(p("JOB", "Job name", Kind.NAME).sv("*ALL").dft("*ALL").len(28)));
        add(CommandDefinition.of("SIGNOFF", "Sign Off").context(INTERACTIVE));
        add(CommandDefinition.of("GO", "Go to Menu").context(INTERACTIVE).positional(1)
                .p(p("MENU", "Menu", Kind.NAME).sv("MAIN", "HELP").dft("MAIN")));
        add(CommandDefinition.of("CLEAR", "Clear Command Entry").context(INTERACTIVE));
    }
}
