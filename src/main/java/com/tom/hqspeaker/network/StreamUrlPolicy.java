package com.tom.hqspeaker.network;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/** Shared safety policy for server-admitted and client-fetched MP3/ICY radio URLs. */
public final class StreamUrlPolicy {
    public static final int MAX_URL_CHARS = 512;

    private StreamUrlPolicy() {}

    public static URI validate(String rawUrl) throws IOException {
        if (rawUrl == null || rawUrl.isBlank()) throw new IOException("URL cannot be empty");
        if (rawUrl.length() > MAX_URL_CHARS) {
            throw new IOException("URL too long (max " + MAX_URL_CHARS + " chars)");
        }
        for (int i = 0; i < rawUrl.length(); i++) {
            char c = rawUrl.charAt(i);
            if (c < 0x20 || c == 0x7F) throw new IOException("URL contains illegal character at index " + i);
        }

        final URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException exception) {
            throw new IOException("malformed URL", exception);
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IOException("URL must use http:// or https://");
        }
        if (uri.getUserInfo() != null) throw new IOException("URL userinfo is not allowed");

        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new IOException("URL has no host");
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".local")) {
            throw new IOException("URL targets a local host");
        }

        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443 && port != 8000 && port != 8080 && port != 8443) {
            throw new IOException("URL uses a blocked port (" + port + ")");
        }

        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new IOException("cannot resolve host", exception);
        }
        if (addresses.length == 0) throw new IOException("cannot resolve host");

        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new IOException("URL resolves to a private/reserved address");
            }
        }
        return uri;
    }

    static boolean isBlockedAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            // Java's isSiteLocalAddress covers deprecated fec0::/10, not RFC 4193 fc00::/7 unique-local space.
            return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) return false;
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        // Carrier-grade NAT is not public-routable and should not be a client radio target either.
        return first == 100 && second >= 64 && second <= 127;
    }
}
