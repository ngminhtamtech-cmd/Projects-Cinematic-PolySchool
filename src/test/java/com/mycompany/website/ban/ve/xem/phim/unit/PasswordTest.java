package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.util.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PasswordTest {

    @Test
    @DisplayName("Password hashing and matching with correct raw password")
    public void testPasswordHashAndMatch() {
        String raw = "secretPass123!";
        String hash = PasswordUtil.hash(raw);

        assertNotNull(hash);
        assertTrue(PasswordUtil.matches(raw, hash));
    }

    @Test
    @DisplayName("Password match should return false for incorrect password")
    public void testPasswordMismatch() {
        String raw = "correctPassword";
        String hash = PasswordUtil.hash(raw);

        assertFalse(PasswordUtil.matches("wrongPassword", hash));
    }

    @Test
    @DisplayName("Password match should handle null or blank parameters safely")
    public void testPasswordNullOrBlank() {
        assertFalse(PasswordUtil.matches(null, "hash"));
        assertFalse(PasswordUtil.matches("pass", null));
        assertFalse(PasswordUtil.matches("pass", ""));
        assertFalse(PasswordUtil.matches("pass", "   "));
    }
}
