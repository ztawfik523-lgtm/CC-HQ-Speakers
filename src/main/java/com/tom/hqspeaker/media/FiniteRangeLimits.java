package com.tom.hqspeaker.media;

/** Shared M1F bounds for finite encoded range transport. */
public final class FiniteRangeLimits {
    private FiniteRangeLimits() {}

    /** Maximum encoded bytes carried by one range response. */
    public static final int MAX_RANGE_BYTES = 128 * 1024;
    /** Temporary client encoded RAM window for one active finite source. */
    public static final int CLIENT_WINDOW_BYTES = 512 * 1024;
    /** Maximum in-flight range requests owned by one player across the server. */
    public static final int MAX_OUTSTANDING_REQUESTS_PER_PLAYER = 4;
    /** Maximum in-flight encoded bytes owned by one player across the server. */
    public static final long MAX_OUTSTANDING_BYTES_PER_PLAYER = 512L * 1024L;
    /** Small bounded server-side file IO pool. */
    public static final int SERVER_IO_THREADS = 2;
    /** Maximum queued server range reads across one Minecraft server. */
    public static final int SERVER_IO_QUEUE = 64;
}
