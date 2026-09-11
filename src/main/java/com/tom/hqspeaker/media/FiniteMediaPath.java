package com.tom.hqspeaker.media;

/** Path rules for files inside the HQ writable ComputerCraft mount. */
public final class FiniteMediaPath {
    private FiniteMediaPath() {}

    public static String normalize(String input) {
        if (input == null) throw new IllegalArgumentException("path cannot be null");
        String path = input.trim().replace('\\', '/');
        while (path.startsWith("/")) path = path.substring(1);
        if (path.isEmpty()) throw new IllegalArgumentException("path cannot be empty");
        if (path.length() > 512) throw new IllegalArgumentException("path is too long");

        StringBuilder out = new StringBuilder(path.length());
        for (String part : path.split("/")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) throw new IllegalArgumentException("parent traversal is not allowed");
            if (part.indexOf('\0') >= 0 || part.indexOf(':') >= 0) {
                throw new IllegalArgumentException("path contains an illegal character");
            }
            if (out.length() > 0) out.append('/');
            out.append(part);
        }
        if (out.length() == 0) throw new IllegalArgumentException("path cannot be empty");
        return out.toString();
    }
}
