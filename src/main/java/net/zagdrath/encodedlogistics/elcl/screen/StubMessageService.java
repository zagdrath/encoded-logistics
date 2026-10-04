/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.zagdrath.encodedlogistics.elcl.ElclException;

// STUB: waiting on elcl.job (message queues kept in system data, chat notices to online recipients). In memory per
// system: a queue per user (QSYSOPR for *SYSOPR), newest first.
final class StubMessageService implements MessageService {
    public static final String SYSOPR = "QSYSOPR";

    private static final class Queues {
        final Map<String, List<Message>> byUser = new LinkedHashMap<>();
        long nextId = 1;
    }

    private final ElclServices.Store<Queues> store = new ElclServices.Store<>(system -> new Queues());

    private static String queue(String user) {
        String name = user.toUpperCase(Locale.ROOT);
        return name.equals("*SYSOPR") ? SYSOPR : name;
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized List<Message> messages(ElclSystem system, String user) {
        return List.copyOf(store.of(system).byUser.getOrDefault(queue(user), List.of()));
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized int unread(ElclSystem system, String user) {
        return (int) messages(system, user).stream().filter(Message::unread).count();
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized void markRead(ElclSystem system, String user) {
        List<Message> queue = store.of(system).byUser.get(queue(user));
        if (queue != null) {
            queue.replaceAll(m -> new Message(m.id(), m.msgId(), m.severity(), m.from(), m.sent(), m.text(), false));
        }
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized void send(ElclSystem system, String from, String to, String msgId, int severity, String text) {
        Queues queues = store.of(system);
        List<String> recipients = new ArrayList<>();
        if (to.equalsIgnoreCase("*ALL")) {
            recipients.addAll(queues.byUser.keySet());
            if (!recipients.contains(SYSOPR)) {
                recipients.add(SYSOPR);
            }
        } else {
            recipients.add(queue(to));
        }
        for (String user : recipients) {
            queues.byUser.computeIfAbsent(user, k -> new ArrayList<>()).addFirst(new Message(queues.nextId++, msgId, severity, from, system.nowShort(), text,
                    true));
        }
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized void remove(ElclSystem system, String user, long id) throws ElclException {
        List<Message> queue = store.of(system).byUser.get(queue(user));
        if (queue == null || !queue.removeIf(m -> m.id() == id)) {
            throw new ElclException("ELC0103", id, "MSG");
        }
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized void removeAll(ElclSystem system, String user) {
        store.of(system).byUser.remove(queue(user));
    }
}
