/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.signal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.zagdrath.encodedlogistics.client.screen.TerminalSettings;

// The client's own check of a Speaker's web URL (WebAudioFetch), whatever the server sent: http(s) only, no user info,
// public addresses only (judged on the one lookup it connects with), and the HTTP answer read with no redirects and
// within the size cap. And web audio is off until the player opts in.
class WebAudioFetchTest {
    // The URL a server could send; refused here before anything is connected to.
    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/a.ogg", "http://127.8.9.10:8080/a.ogg", "http://localhost/a.ogg", "http://[::1]/a.ogg",
            "http://10.0.0.5/a.ogg", "http://172.16.3.4/a.ogg", "http://172.31.255.1/a.ogg", "http://192.168.1.10/a.ogg", "http://[fd12:3456::1]/a.ogg",
            "http://169.254.169.254/latest/meta-data/", "http://[fe80::1]/a.ogg",
            "http://224.0.0.1/a.ogg", "http://[ff02::1]/a.ogg",
            "http://0.0.0.0/a.ogg", "http://0.1.2.3/a.ogg", "http://[::]/a.ogg",
            "http://[::ffff:10.0.0.1]/a.ogg", "http://[::ffff:127.0.0.1]/a.ogg", "http://[64:ff9b::a00:1]/a.ogg" })
    void refusesAddressesThatArentPublic(String url) {
        IOException refused = assertThrows(IOException.class, () -> WebAudioFetch.fetch(url, 1024));
        assertEquals("web: address not allowed", refused.getMessage(), url);
    }

    // A public-looking name whose lookup has a private address in it is refused too: every address is checked.
    @Test
    void refusesANameThatResolvesPrivately() throws IOException {
        InetAddress open = InetAddress.getByName("93.184.215.14"), inside = InetAddress.getByName("10.0.0.7");
        IOException refused = assertThrows(IOException.class,
                () -> WebAudioFetch.check("https://audio.example.com/a.ogg", host -> new InetAddress[] { open, inside }));
        assertEquals("web: address not allowed", refused.getMessage());
        assertThrows(IOException.class, () -> WebAudioFetch.check("https://audio.example.com/a.ogg", host -> new InetAddress[] { inside }));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp://example.com/a.ogg", "file:///etc/passwd", "https://user:pass@example.com/a.ogg", "https://user@example.com/a.ogg",
            "jar:https://example.com/a.jar!/a.ogg", "https:///a.ogg", "not a url" })
    void refusesWhatIsntAPlainWebAddress(String url) {
        IOException refused = assertThrows(IOException.class, () -> WebAudioFetch.check(url, host -> new InetAddress[] { InetAddress.getByName("93.184.215.14") }));
        assertTrue(refused.getMessage().startsWith("web:"), refused.getMessage());
        assertFalse(refused.getMessage().equals("web: address not allowed"), url);
    }

    // A public address passes, and the fetch is to that address (the one lookup), with the request's path and query.
    @Test
    void acceptsAPublicAddress() throws IOException {
        InetAddress open = InetAddress.getByName("93.184.215.14");
        WebAudioFetch.Target target = WebAudioFetch.check("https://Audio.Example.com/music/a.ogg?v=2#start", host -> new InetAddress[] { open });
        assertEquals(open, target.address());
        assertEquals(443, target.port());
        assertTrue(target.https());
        assertEquals("/music/a.ogg?v=2", target.path());
        assertEquals("Audio.Example.com", target.authority());
        assertEquals(8080, WebAudioFetch.check("http://example.com:8080", host -> new InetAddress[] { open }).port());
        assertEquals("/", WebAudioFetch.check("http://example.com", host -> new InetAddress[] { open }).path());
        assertTrue(WebAudioFetch.allowed(InetAddress.getByName("2606:4700::1111")));
    }

    @Test
    void readsABody() throws IOException {
        assertArrayEquals(bytes("hello"), WebAudioFetch.response(answer("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello"), 10));
        assertArrayEquals(bytes("hello world"),
                WebAudioFetch.response(answer("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n6;x=y\r\n world\r\n0\r\n\r\n"), 20));
        assertArrayEquals(bytes("until close"), WebAudioFetch.response(answer("HTTP/1.0 200 OK\r\n\r\nuntil close"), 20));
    }

    @Test
    void refusesRedirectsAndOtherAnswers() {
        IOException redirect = assertThrows(IOException.class,
                () -> WebAudioFetch.response(answer("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1/\r\n\r\n"), 10));
        assertEquals("web: HTTP 302", redirect.getMessage());
        assertThrows(IOException.class, () -> WebAudioFetch.response(answer("SSH-2.0-OpenSSH\r\n\r\n"), 10));
    }

    @Test
    void capsTheSize() {
        assertEquals("web: too large",
                assertThrows(IOException.class, () -> WebAudioFetch.response(answer("HTTP/1.1 200 OK\r\nContent-Length: 11\r\n\r\nhello world"), 10)).getMessage());
        assertEquals("web: too large", assertThrows(IOException.class,
                () -> WebAudioFetch.response(answer("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n6\r\nhello \r\n5\r\nworld\r\n0\r\n\r\n"), 10)).getMessage());
        assertEquals("web: too large",
                assertThrows(IOException.class, () -> WebAudioFetch.response(answer("HTTP/1.0 200 OK\r\n\r\nhello world"), 10)).getMessage());
    }

    // Web audio plays only for a player who has opted in.
    @Test
    void webAudioIsOffUntilThePlayerOptsIn() {
        assertFalse(TerminalSettings.allowWebAudio());
    }

    private static ByteArrayInputStream answer(String text) {
        return new ByteArrayInputStream(bytes(text));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }
}
