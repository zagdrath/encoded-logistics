/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.device;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.store.ElclConfig;

// An 8" Diskette in a drive: its label, how much source it holds (OS.md 4 proposes 64 KB: config disketteBytes, unless
// the diskette says otherwise) and the libraries saved on it. The Midrange line keeps these in the diskette item's data
// (LibraryImage.save()).
public interface Diskette {
    String label();

    default long capacity() {
        return ElclConfig.disketteBytes();
    }

    List<LibraryImage> libraries();

    default @Nullable LibraryImage library(String name) {
        for (LibraryImage image : libraries()) {
            if (image.library().equalsIgnoreCase(name)) {
                return image;
            }
        }
        return null;
    }

    // Bytes used by the libraries on it other than `except` (the one about to be written over).
    default long used(String except) {
        long used = 0;
        for (LibraryImage image : libraries()) {
            if (!image.library().equalsIgnoreCase(except)) {
                used += image.bytes();
            }
        }
        return used;
    }

    // Writes a library onto it, replacing one of the same name. Only called once it fits.
    void write(LibraryImage image);
}
