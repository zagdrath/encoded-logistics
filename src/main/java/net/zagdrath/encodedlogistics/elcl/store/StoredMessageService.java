/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.MessageService;

// Message queues in the system's saved data (OS.md 5, Display Messages): one per user (QSYSOPR for *SYSOPR), newest
// first, kept until deleted, at most messageCap per queue (the oldest go first). An online recipient is told in chat
// (messageChatNotice).
public final class StoredMessageService implements MessageService {
    public static final String SYSOPR = "QSYSOPR";

    private static String queue(String user) {
        String name = user.trim().toUpperCase(Locale.ROOT);
        return name.equals("*SYSOPR") ? SYSOPR : name;
    }

    @Override
    public synchronized List<Message> messages(ElclSystem system, String user) {
        return List.copyOf(ElclStore.of(system).queues.getOrDefault(queue(user), List.of()));
    }

    @Override
    public synchronized int unread(ElclSystem system, String user) {
        return (int) messages(system, user).stream().filter(Message::unread).count();
    }

    @Override
    public synchronized void markRead(ElclSystem system, String user) {
        SystemData data = ElclStore.of(system);
        List<Message> queue = data.queues.get(queue(user));
        if (queue != null && queue.stream().anyMatch(Message::unread)) {
            queue.replaceAll(m -> new Message(m.id(), m.msgId(), m.severity(), m.from(), m.sent(), m.text(), false));
            data.changed();
        }
    }

    @Override
    public synchronized void send(ElclSystem system, String from, String to, String msgId, int severity, String text) {
        SystemData data = ElclStore.of(system);
        List<String> recipients = new ArrayList<>();
        if (to.equalsIgnoreCase("*ALL")) {
            recipients.addAll(data.queues.keySet());
            if (!recipients.contains(SYSOPR)) {
                recipients.add(SYSOPR);
            }
        } else {
            recipients.add(queue(to));
        }
        int cap = ElclConfig.messageCap();
        for (String user : recipients) {
            List<Message> queue = data.queues.computeIfAbsent(user, k -> new ArrayList<>());
            queue.addFirst(new Message(data.nextMessage++, msgId, severity, from, system.nowShort(), text, true));
            while (queue.size() > cap) {
                queue.removeLast();
            }
            notice(system, user, from);
        }
        data.changed();
    }

    // "Message from ZAGDRATH waiting (DSPMSG)." to the recipient, if they're online (QSYSOPR: the network's owner).
    private static void notice(ElclSystem system, String user, String from) {
        if (!ElclConfig.messageChatNotice() || system.server() == null) {
            return;
        }
        String name = user.equals(SYSOPR) ? ownerName(system) : user;
        if (name.isEmpty()) {
            return;
        }
        for (ServerPlayer player : system.server().getPlayerList().getPlayers()) {
            if (player.getName().getString().equalsIgnoreCase(name)) {
                player.sendSystemMessage(Component.translatable("message.encodedlogistics.elcl.message_waiting", from, system.name()));
            }
        }
    }

    private static String ownerName(ElclSystem system) {
        var firewall = net.zagdrath.encodedlogistics.multiblock.ControllerStructures.firewall(system.server(), system.network());
        return firewall != null ? firewall.ownerName() : "";
    }

    @Override
    public synchronized void remove(ElclSystem system, String user, long id) throws ElclException {
        SystemData data = ElclStore.of(system);
        List<Message> queue = data.queues.get(queue(user));
        if (queue == null || !queue.removeIf(m -> m.id() == id)) {
            throw new ElclException("ELC0103", id, "MSG");
        }
        data.changed();
    }

    @Override
    public synchronized void removeAll(ElclSystem system, String user) {
        SystemData data = ElclStore.of(system);
        if (data.queues.remove(queue(user)) != null) {
            data.changed();
        }
    }
}
