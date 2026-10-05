/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.store;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.zagdrath.encodedlogistics.Config;

// The ELCL settings (Config's elcl section), each falling back to its default where the config isn't loaded (unit
// tests).
public final class ElclConfig {
    private ElclConfig() {}

    private static int value(ModConfigSpec.IntValue value) {
        try {
            return value.getAsInt();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    private static boolean value(ModConfigSpec.BooleanValue value) {
        try {
            return value.getAsBoolean();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static int spooledFileCap() {
        return value(Config.ELCL_SPOOLED_FILE_CAP);
    }

    public static int messageCap() {
        return value(Config.ELCL_MESSAGE_CAP);
    }

    public static int charsPerStorageByte() {
        return value(Config.ELCL_CHARS_PER_STORAGE_BYTE);
    }

    public static boolean messageChatNotice() {
        return value(Config.ELCL_MESSAGE_CHAT_NOTICE);
    }

    public static int interactiveBudget() {
        return value(Config.ELCL_INTERACTIVE_BUDGET);
    }

    public static int batchBudget() {
        return value(Config.ELCL_BATCH_BUDGET);
    }

    public static int globalBudget() {
        return value(Config.ELCL_GLOBAL_BUDGET);
    }

    public static int maxSourceLines() {
        return value(Config.ELCL_MAX_SOURCE_LINES);
    }

    public static int maxListSize() {
        return value(Config.ELCL_MAX_LIST_SIZE);
    }

    public static int computeServerJobs() {
        return value(Config.ELCL_COMPUTE_SERVER_JOBS);
    }

    public static int craftLogRetention() {
        return value(Config.ELCL_CRAFT_LOG_RETENTION);
    }

    public static int disketteBytes() {
        return value(Config.ELCL_DISKETTE_BYTES);
    }

    public static int maxRecordsPerFile() {
        return value(Config.ELCL_MAX_RECORDS_PER_FILE);
    }

    // allowFolderSync: AUTO is on unless the server is dedicated.
    public static boolean folderSync(boolean dedicated) {
        Config.FolderSync setting;
        try {
            setting = Config.ELCL_ALLOW_FOLDER_SYNC.get();
        } catch (IllegalStateException e) {
            setting = Config.ELCL_ALLOW_FOLDER_SYNC.getDefault();
        }
        return switch (setting) {
            case AUTO -> !dedicated;
            case TRUE -> true;
            case FALSE -> false;
        };
    }
}
