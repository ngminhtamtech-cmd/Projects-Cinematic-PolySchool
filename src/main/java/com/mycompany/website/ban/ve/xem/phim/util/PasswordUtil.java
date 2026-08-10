package com.mycompany.website.ban.ve.xem.phim.util;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordUtil {
    private static final int COST = 12;

    private PasswordUtil() {
    }

    public static String hash(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(COST));
    }

    public static boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        return BCrypt.checkpw(rawPassword, passwordHash);
    }
}
