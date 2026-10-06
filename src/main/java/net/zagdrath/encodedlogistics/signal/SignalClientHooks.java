/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

// What the client does for signal devices (client.signal.SignalSounds sets it): a device's client tick plays and stops its
// sounds, and a note a Speaker was told to play (a block event) is played there. Nothing on a dedicated server.
public interface SignalClientHooks {
    SignalClientHooks NONE = new SignalClientHooks() {};

    final class Installed {
        static volatile SignalClientHooks hooks = NONE;

        private Installed() {}
    }

    static void set(SignalClientHooks hooks) {
        Installed.hooks = hooks;
    }

    static SignalClientHooks get() {
        return Installed.hooks;
    }

    default void tick(SignalBlockEntity device) {}

    default void note(SpeakerBlockEntity speaker, int instrument, int note) {}

    // A chunk of an audio file the client asked for (total 0: the server hasn't got it).
    default void audioChunk(String hash, int index, int total, byte[] bytes) {}
}
