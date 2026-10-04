/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.List;

import net.zagdrath.encodedlogistics.elcl.ElclException;

// User message queues (OS.md 5, Display Messages): newest first; "MW" on every screen while any are unread.
public interface MessageService {
    record Message(long id, String msgId, int severity, String from, String sent, String text, boolean unread) {}

    List<Message> messages(ElclSystem system, String user);

    int unread(ElclSystem system, String user);

    void markRead(ElclSystem system, String user);

    // to: a user, *SYSOPR (the system operator's queue) or *ALL (every queue known on the system).
    void send(ElclSystem system, String from, String to, String msgId, int severity, String text);

    void remove(ElclSystem system, String user, long id) throws ElclException;

    void removeAll(ElclSystem system, String user);
}
