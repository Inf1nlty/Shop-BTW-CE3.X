package com.inf1nlty.shop.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Provides stable offline UUIDs (1.6.4 compatibility, no GameProfile available).
 */
public final class PlayerIdentityUtil {

    private PlayerIdentityUtil() {}

    public static UUID getOfflineUUID(String username) {
        String base = "OfflinePlayer:" + username;
        return UUID.nameUUIDFromBytes(base.getBytes(StandardCharsets.UTF_8));
    }
}