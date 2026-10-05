/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.compile;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.db.FieldDef;
import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;

// A file a program declares (DCLF): its open ID ("" for none: OPNID(*NONE)), the library and file as written (*LIBL
// when unqualified), and the record format it was compiled against (null only in the editor's lenient check). Each
// field is a variable: &FIELD, or &OPNID_FIELD with an open ID.
public record DeclaredFile(String opnid, String library, String file, @Nullable RecordFormat format) {
    public String prefix() {
        return opnid.isEmpty() ? "&" : "&" + opnid + "_";
    }

    public String variable(FieldDef field) {
        return prefix() + field.name();
    }

    public String qualified() {
        return library + "/" + file;
    }
}
