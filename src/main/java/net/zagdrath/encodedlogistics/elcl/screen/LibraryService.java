/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.Diagnostic;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.compile.CompiledProgram;
import net.zagdrath.encodedlogistics.elcl.device.LibraryImage;

// Libraries, source members and programs (OS.md 2-3): what Work with Libraries, Work with Members, the editor and
// compile (option 14 / CRTELPGM) work on. Failures are ELCL escape messages (ELC0201-ELC0208, ELC0401).
public interface LibraryService {
    // type: *PROD, *TEST or *SYS; authority: the public's, *USE or *CHANGE.
    record Library(String name, String type, String text, String owner, String authority, int members, long size, String created) {}

    // changed: the source changed after its program was compiled (or there's no program yet: false).
    record Member(String library, String name, String type, String text, boolean changed, boolean program, int lines, String updated) {}

    record CompileOutcome(boolean created, List<String> listing, List<Diagnostic> diagnostics, int spooledFile) {}

    List<Library> libraries(ElclSystem system);

    Library library(ElclSystem system, String name) throws ElclException;

    void createLibrary(ElclSystem system, String user, String name, String type, String text) throws ElclException;

    // text / authority null: unchanged.
    void changeLibrary(ElclSystem system, String user, String name, @Nullable String text, @Nullable String authority) throws ElclException;

    void deleteLibrary(ElclSystem system, String user, String name) throws ElclException;

    List<Member> members(ElclSystem system, String library) throws ElclException;

    Member member(ElclSystem system, String library, String name) throws ElclException;

    List<SourceLine> source(ElclSystem system, String library, String member) throws ElclException;

    void save(ElclSystem system, String user, String library, String member, List<SourceLine> lines) throws ElclException;

    void createMember(ElclSystem system, String user, String library, String member, String text) throws ElclException;

    void copyMember(ElclSystem system, String user, String fromLibrary, String fromMember, String toLibrary, String toMember) throws ElclException;

    void renameMember(ElclSystem system, String user, String library, String member, String newName) throws ElclException;

    void deleteMember(ElclSystem system, String user, String library, String member) throws ElclException;

    // Takes the member's edit lock for the user: ELC0208 when another user holds it.
    void lock(ElclSystem system, String user, String library, String member) throws ElclException;

    void unlock(ElclSystem system, String user, String library, String member);

    // Every lock the user holds (their terminal closed).
    void unlockAll(ElclSystem system, String user);

    CompileOutcome compile(ElclSystem system, String user, String library, String program, String sourceLibrary, String sourceMember) throws ElclException;

    List<String> programs(ElclSystem system, String library) throws ElclException;

    // A program to run (ELC0203 when there's none, or it no longer compiles).
    CompiledProgram program(ElclSystem system, String library, String program) throws ElclException;

    // A library as SAVLIB writes it (ELC0201 when there's none).
    LibraryImage image(ElclSystem system, String library) throws ElclException;

    // RSTLIB: the library made (owned by the user) or its members and programs replaced from the image; ELC0205 for
    // ELSYS, ELC0401 without *CHANGE, ELC0207 when the network can't store it.
    void restore(ElclSystem system, String user, LibraryImage image) throws ElclException;

    // A program's source as it was compiled (what a job runs): ELC0201 / ELC0203 when there's none.
    List<SourceLine> programSource(ElclSystem system, String library, String program) throws ElclException;

    void deleteProgram(ElclSystem system, String user, String library, String program) throws ElclException;
}
