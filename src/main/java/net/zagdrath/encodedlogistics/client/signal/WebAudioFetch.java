/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.signal;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

// A Speaker's web audio, fetched by this game. The server checks a URL against its allowWebAudio and webAudioHosts before
// it sends it, but the client doesn't take the server's word for it: before fetching, it checks the URL itself (check):
// - http or https only, with a host and no user info;
// - the host resolved once, here, and every address it has must be public: not loopback, private (10/8, 172.16/12,
//   192.168/16, fc00::/7), link-local (169.254/16, fe80::/10), multicast, or unspecified (0.0.0.0/8, ::), with
//   IPv4-mapped, IPv4-compatible and NAT64 IPv6 addresses judged as the IPv4 address in them;
// - the connection is to that checked address only (no second lookup, so DNS can't change in between). For https the
//   certificate is still checked against the host's name.
// Then a plain HTTP/1.1 GET: 200 or nothing (no redirects), at most maxBytes (Content-Length or chunked), all within
// TOTAL_MS. The bytes are only ever kept in memory (AudioCache). Failures are IOExceptions whose message starts "web:",
// which is what the Speaker's popup and ELCL are told.
final class WebAudioFetch {
    private static final int CONNECT_MS = 10_000, READ_MS = 15_000;
    private static final long TOTAL_MS = 30_000;
    private static final int MAX_LINE = 8 * 1024, MAX_HEADERS = 100;
    // The most any server may ask for (the server config's audioMaxBytes tops out at 64 MB).
    static final int CEILING = 64 * 1024 * 1024;

    // Looks a host up (InetAddress.getAllByName; tests give their own).
    interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    // A URL that passed: what to connect to, and what to ask for.
    record Target(String host, InetAddress address, int port, boolean https, String authority, String path) {}

    private WebAudioFetch() {}

    // The URL's bytes, at most maxBytes (capped at CEILING).
    static byte[] fetch(String url, int maxBytes) throws IOException {
        return fetch(check(url, InetAddress::getAllByName), Math.clamp(maxBytes, 0, CEILING));
    }

    // The URL, checked: an http(s) URL with a host and no user info, whose host's addresses are all public.
    static Target check(String url, Resolver resolver) throws IOException {
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw web("not a web address");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean https = scheme.equals("https");
        if (!https && !scheme.equals("http")) {
            throw web("only http and https");
        }
        if (uri.getRawUserInfo() != null) {
            throw web("user info not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty() || uri.getRawAuthority() == null) {
            throw web("no host");
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        int port = uri.getPort() == -1 ? https ? 443 : 80 : uri.getPort();
        if (port < 1 || port > 65_535) {
            throw web("bad port");
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw web("unknown host");
        }
        if (addresses == null || addresses.length == 0) {
            throw web("unknown host");
        }
        for (InetAddress address : addresses) {
            if (!allowed(address)) {
                throw web("address not allowed");
            }
        }
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }
        return new Target(host, addresses[0], port, https, uri.getRawAuthority(), path);
    }

    // Whether an address is one a Speaker may fetch from: a public one.
    static boolean allowed(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (address instanceof Inet6Address) {
            if (embedsIpv4(bytes)) {
                try {
                    return allowed(InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16)));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
            // fc00::/7: unique local.
            if ((bytes[0] & 0xFE) == 0xFC) {
                return false;
            }
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        // 0.0.0.0/8: "this network", unspecified.
        return !(address instanceof Inet4Address && bytes[0] == 0);
    }

    // ::ffff:a.b.c.d (mapped), ::a.b.c.d (compatible, which takes in :: and ::1) and 64:ff9b::a.b.c.d (NAT64).
    private static boolean embedsIpv4(byte[] b) {
        boolean zeros = true;
        for (int i = 0; i < 10; i++) {
            zeros &= b[i] == 0;
        }
        boolean mapped = zeros && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
        boolean compatible = zeros && b[10] == 0 && b[11] == 0;
        boolean nat64 = b[0] == 0 && b[1] == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B;
        for (int i = 4; nat64 && i < 12; i++) {
            nat64 = b[i] == 0;
        }
        return mapped || compatible || nat64;
    }

    private static byte[] fetch(Target target, int maxBytes) throws IOException {
        long deadline = System.nanoTime() + TOTAL_MS * 1_000_000L;
        Socket raw = new Socket();
        try {
            raw.connect(new InetSocketAddress(target.address(), target.port()), CONNECT_MS);
            raw.setSoTimeout(READ_MS);
            Socket socket = raw;
            if (target.https()) {
                // Over the connection to the checked address; the certificate is checked against the host's name.
                SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, target.host(), target.port(), true);
                SSLParameters parameters = ssl.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                ssl.setSSLParameters(parameters);
                ssl.startHandshake();
                socket = ssl;
            }
            try (Socket open = socket) {
                OutputStream out = open.getOutputStream();
                out.write(("GET " + target.path() + " HTTP/1.1\r\nHost: " + target.authority() + "\r\nUser-Agent: EncodedLogistics-Speaker\r\n"
                        + "Accept: */*\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                return response(new Deadline(new BufferedInputStream(open.getInputStream()), deadline), maxBytes);
            }
        } catch (IOException e) {
            raw.close();
            throw e.getMessage() != null && e.getMessage().startsWith("web:") ? e : new IOException("web: no answer", e);
        }
    }

    // A response: 200, then its body (Content-Length, chunked, or up to the close), at most maxBytes.
    static byte[] response(InputStream in, int maxBytes) throws IOException {
        String status = line(in);
        String[] parts = status.split(" ", 3);
        if (parts.length < 2 || !parts[0].startsWith("HTTP/1.")) {
            throw web("not an HTTP answer");
        }
        if (!parts[1].equals("200")) {
            throw web("HTTP " + parts[1]);
        }
        long length = -1;
        boolean chunked = false;
        for (int count = 0;; count++) {
            String header = line(in);
            if (header.isEmpty()) {
                break;
            }
            if (count >= MAX_HEADERS) {
                throw web("not an HTTP answer");
            }
            int colon = header.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = header.substring(0, colon).trim().toLowerCase(Locale.ROOT), value = header.substring(colon + 1).trim();
            if (name.equals("content-length")) {
                try {
                    length = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw web("not an HTTP answer");
                }
            } else if (name.equals("transfer-encoding")) {
                chunked = value.toLowerCase(Locale.ROOT).contains("chunked");
            }
        }
        if (chunked) {
            return chunked(in, maxBytes);
        }
        if (length > maxBytes) {
            throw web("too large");
        }
        if (length >= 0) {
            byte[] body = in.readNBytes((int) length);
            if (body.length != length) {
                throw web("no answer");
            }
            return body;
        }
        byte[] body = in.readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            throw web("too large");
        }
        return body;
    }

    private static byte[] chunked(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = line(in);
            int semicolon = sizeLine.indexOf(';');
            long size;
            try {
                size = Long.parseLong((semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine).trim(), 16);
            } catch (NumberFormatException e) {
                throw web("not an HTTP answer");
            }
            if (size < 0) {
                throw web("not an HTTP answer");
            }
            if (size == 0) {
                // Trailers, to the blank line.
                for (int count = 0; !line(in).isEmpty(); count++) {
                    if (count >= MAX_HEADERS) {
                        throw web("not an HTTP answer");
                    }
                }
                return body.toByteArray();
            }
            if (body.size() + size > maxBytes) {
                throw web("too large");
            }
            byte[] chunk = in.readNBytes((int) size);
            if (chunk.length != size) {
                throw web("no answer");
            }
            body.writeBytes(chunk);
            line(in);
        }
    }

    // A line (to LF, CR dropped), at most MAX_LINE bytes.
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != '\n') {
            if (b < 0) {
                throw web("no answer");
            }
            if (line.size() >= MAX_LINE) {
                throw web("not an HTTP answer");
            }
            line.write(b);
        }
        String text = line.toString(StandardCharsets.ISO_8859_1);
        return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
    }

    private static IOException web(String problem) {
        return new IOException("web: " + problem);
    }

    // Reads that fail once the whole fetch has run out of time (a server sending a byte at a time).
    private static final class Deadline extends FilterInputStream {
        private final long deadline;

        Deadline(InputStream in, long deadline) {
            super(in);
            this.deadline = deadline;
        }

        private void check() throws IOException {
            if (System.nanoTime() - deadline > 0) {
                throw web("no answer");
            }
        }

        @Override
        public int read() throws IOException {
            check();
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            check();
            return super.read(b, off, len);
        }
    }
}
