/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.store.ElclConfig;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.elcl.store.StoredLibraryService;
import net.zagdrath.encodedlogistics.elcl.store.SystemData;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// Folder sync (elcl.sync, OS.md 4): each system's libraries as files in the world save -
//   <world>/encodedlogistics/libraries/<SYSNAME>/<LIB>/<MEMBER>.elclp
// Out: saving a member (or making, copying, renaming, restoring one) writes its file. In: every two seconds the folder
// is read; a new or changed file becomes the member (unchanged lines keep their sequence numbers: Resequence), a new
// folder a library. What was last synced is remembered per member (SystemData.syncHashes), so a file and a member that
// both changed since are a conflict: the last write wins - an in-game save is always the later one - and the losing
// version is kept as <MEMBER>.elclp.bak. Deleting a file never deletes its member; deleting a member moves its file to
// <LIB>/.deleted/. ELSYS is never synced in (nor written out: it's rebuilt from the mod's own copy).
// On only where allowFolderSync says (AUTO: single-player yes, dedicated servers no).
public final class FolderSync implements StoredLibraryService.MemberListener {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int POLL_TICKS = 40;
    private static final String EXTENSION = ".elclp";
    private static @Nullable Boolean override;
    private static int ticks;

    // The game tests turn it on (their server counts as dedicated); null: the config's.
    public static void setEnabled(@Nullable Boolean enabled) {
        override = enabled;
    }

    public static boolean enabled(MinecraftServer server) {
        return override != null ? override : ElclConfig.folderSync(server.isDedicatedServer());
    }

    public static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("encodedlogistics").resolve("libraries");
    }

    public static Path folder(ElclSystem system) {
        return root(system.server()).resolve(system.name());
    }

    public static Path file(ElclSystem system, String library, String member) {
        return folder(system).resolve(library).resolve(member + EXTENSION);
    }

    private static boolean synced(String library) {
        return !library.equals(SystemData.SYSTEM_LIBRARY);
    }

    // The text a member's file holds, and the hash remembered for it (line ends evened out).
    static String text(List<SourceLine> lines) {
        return SourceLine.join(SourceLine.texts(lines));
    }

    static String hash(String text) {
        String even = text.replace("\r\n", "\n");
        return Integer.toHexString(even.hashCode()) + ":" + even.length();
    }

    private static String key(String library, String member) {
        return library + "/" + member;
    }

    // --- Out ---

    @Override
    public void saved(ElclSystem system, String library, String member, List<SourceLine> lines) {
        if (!enabled(system.server()) || !synced(library)) {
            return;
        }
        SystemData data = ElclStore.of(system);
        Path file = file(system, library, member);
        try {
            Files.createDirectories(file.getParent());
            String known = data.syncHashes.get(key(library, member));
            if (Files.exists(file)) {
                String onDisk = Files.readString(file, StandardCharsets.UTF_8);
                // Changed on disk since it was last synced: this save is the later write; the file's version is kept.
                if (known == null ? !hash(onDisk).equals(hash(text(lines))) : !hash(onDisk).equals(known)) {
                    Files.copy(file, file.resolveSibling(member + EXTENSION + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            write(file, text(lines));
            data.syncHashes.put(key(library, member), hash(text(lines)));
            data.changed();
        } catch (IOException e) {
            LOGGER.warn("Folder sync couldn't write {}: {}", file, e.toString());
        }
    }

    @Override
    public void deleted(ElclSystem system, String library, String member) {
        if (!enabled(system.server()) || !synced(library)) {
            return;
        }
        Path file = file(system, library, member);
        try {
            if (Files.exists(file)) {
                Path deleted = file.getParent().resolve(".deleted");
                Files.createDirectories(deleted);
                Files.move(file, deleted.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("Folder sync couldn't move {} to .deleted: {}", file, e.toString());
        }
        SystemData data = ElclStore.of(system);
        if (data.syncHashes.remove(key(library, member)) != null) {
            data.changed();
        }
    }

    @Override
    public void libraryCreated(ElclSystem system, String library) {
        if (!enabled(system.server()) || !synced(library)) {
            return;
        }
        try {
            Files.createDirectories(folder(system).resolve(library));
        } catch (IOException e) {
            LOGGER.warn("Folder sync couldn't make {}: {}", folder(system).resolve(library), e.toString());
        }
    }

    private static void write(Path file, String text) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // --- In ---

    public static void tick(MinecraftServer server) {
        if (++ticks % POLL_TICKS != 0 || !enabled(server)) {
            return;
        }
        for (Map.Entry<NetworkRef, SystemData> entry : ElclStore.get(server).systems().entrySet()) {
            if (ControllerStructures.loaded(server, entry.getKey())) {
                poll(new ElclSystem(server, entry.getKey()));
            }
        }
    }

    // One look at a system's folder: members it hasn't written yet go out; new and changed files come in.
    public static void poll(ElclSystem system) {
        SystemData data = ElclStore.of(system);
        Path folder = folder(system);
        StoredLibraryService libraries = (StoredLibraryService) net.zagdrath.encodedlogistics.elcl.screen.ElclServices.libraries();
        FolderSync out = new FolderSync();
        // Out: members never synced (the first time, or made before sync was on).
        for (SystemData.Library library : List.copyOf(data.libraries.values())) {
            if (!synced(library.name)) {
                continue;
            }
            for (SystemData.Member member : List.copyOf(library.members.values())) {
                if (!data.syncHashes.containsKey(key(library.name, member.name))) {
                    out.saved(system, library.name, member.name, member.lines);
                }
            }
        }
        if (!Files.isDirectory(folder)) {
            return;
        }
        // In: each library folder and its member files.
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(folder)) {
            for (Path dir : dirs) {
                String library = dir.getFileName().toString().toUpperCase(Locale.ROOT);
                if (!Files.isDirectory(dir) || library.startsWith(".") || !synced(library) || !library.matches("[A-Z][A-Z0-9_@#$]{0,9}")) {
                    continue;
                }
                if (!data.libraries.containsKey(library)) {
                    libraries.makeLibrary(system, library);
                }
                try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*" + EXTENSION)) {
                    for (Path file : files) {
                        String name = file.getFileName().toString();
                        String member = name.substring(0, name.length() - EXTENSION.length()).toUpperCase(Locale.ROOT);
                        if (member.matches("[A-Z][A-Z0-9_@#$]{0,9}")) {
                            read(system, data, libraries, library, member, file);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Folder sync couldn't read {}: {}", folder, e.toString());
        }
    }

    private static void read(ElclSystem system, SystemData data, StoredLibraryService libraries, String library, String member, Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String hash = hash(text), known = data.syncHashes.get(key(library, member));
        if (hash.equals(known)) {
            return;
        }
        SystemData.Library lib = data.libraries.get(library);
        SystemData.Member current = lib != null ? lib.members.get(member) : null;
        if (current != null && hash(text(current.lines)).equals(hash)) {
            data.syncHashes.put(key(library, member), hash);
            data.changed();
            return;
        }
        List<SourceLine> old = current != null ? current.lines : List.of();
        List<SourceLine> lines = Resequence.merge(old, SourceLine.split(text.replace("\r\n", "\n")), system.day());
        try {
            libraries.put(system, library, member, lines);
            data.syncHashes.put(key(library, member), hash);
            data.changed();
        } catch (ElclException e) {
            LOGGER.warn("Folder sync couldn't take in {}: {}", file, e.getMessage());
        }
    }
}
