package com.inf1nlty.shop.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe global generator for unique listing IDs.
 * Used to assign incrementing IDs to shop listings, such as product offers or trades.
 */
public final class ListingIdGenerator {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    private ListingIdGenerator() {}

    /**
     * Returns the next unique listing ID.
     */
    public static int nextId() {
        return SEQ.getAndIncrement();
    }

    /**
     * Seeds the current sequence to avoid conflicts with existing IDs.
     * Call this when restoring listings from persistent storage.
     * @param maxExisting The highest ID already used.
     */
    public static void seed(int maxExisting) {
        SEQ.set(Math.max(maxExisting + 1, SEQ.get()));
    }
}