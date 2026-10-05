/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.compile;

import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;

// Where the compiler finds the record formats DCLF declares variables from: a file's format at compile time (library
// as written: *LIBL / *CURLIB or a name), null when there's no such file (ELC2205). CRTELPGM asks the system; a
// compiled program keeps the formats it used (of) so it compiles again the same; the editor's syntax check is lenient,
// knowing only the formats the server has sent it.
public interface FileResolver {
    FileResolver NONE = (library, file) -> null;

    @Nullable RecordFormat format(String library, String file);

    // Lenient: a file it doesn't know may be there all the same - its variables aren't checked (the editor's check).
    default boolean lenient() {
        return false;
    }

    // "LIB/FILE" as DCLF writes it: an unqualified name is *LIBL/NAME.
    static String key(String library, String file) {
        return library.toUpperCase(Locale.ROOT) + "/" + file.toUpperCase(Locale.ROOT);
    }

    // The formats a program was compiled with, by key.
    static FileResolver of(Map<String, RecordFormat> formats) {
        return (library, file) -> formats.get(key(library, file));
    }
}
