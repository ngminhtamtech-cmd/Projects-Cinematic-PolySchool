package com.mycompany.website.ban.ve.xem.phim.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Doc cac nguong bao mat tu {@code SystemSettings} (P10 — D6, D10, D11).
 *
 * <p>Khong hardcode nguong trong Java: quan tri vien phai chinh duoc o {@code /system/config}
 * ma khong can build lai. Gia tri duoc cache {@value #CACHE_TTL_MS} ms trong process de luong
 * dang nhap khong bien thanh mot chuoi query cau hinh.</p>
 *
 * <p>Khi DB loi hoac gia tri khong phai so, ham tra ve <b>mac dinh chat hon</b> chu khong phai
 * mo hon, va log ro rang. Do la fail-safe: cau hinh hong thi he thong sieu chat, khong phai sieu long.</p>
 */
public final class SecuritySettings {
    private static final Logger LOG = Logger.getLogger(SecuritySettings.class.getName());

    public static final String KEY_MAX_LOGIN_ATTEMPTS = "security.maxLoginAttempts";
    public static final String KEY_LOCK_MINUTES = "security.lockMinutes";
    public static final String KEY_PASSWORD_MIN_LENGTH = "security.passwordMinLength";
    public static final String KEY_RESET_TOKEN_MINUTES = "security.resetTokenMinutes";

    public static final int DEFAULT_MAX_LOGIN_ATTEMPTS = 5;
    public static final int DEFAULT_LOCK_MINUTES = 15;
    public static final int DEFAULT_PASSWORD_MIN_LENGTH = 10;
    public static final int DEFAULT_RESET_TOKEN_MINUTES = 30;

    private static final long CACHE_TTL_MS = 60_000L;

    private static final ConcurrentHashMap<String, CachedValue> CACHE = new ConcurrentHashMap<>();

    private SecuritySettings() {
    }

    public static int maxLoginAttempts() {
        return readInt(KEY_MAX_LOGIN_ATTEMPTS, DEFAULT_MAX_LOGIN_ATTEMPTS);
    }

    public static int lockMinutes() {
        return readInt(KEY_LOCK_MINUTES, DEFAULT_LOCK_MINUTES);
    }

    public static int passwordMinLength() {
        return readInt(KEY_PASSWORD_MIN_LENGTH, DEFAULT_PASSWORD_MIN_LENGTH);
    }

    public static int resetTokenMinutes() {
        return readInt(KEY_RESET_TOKEN_MINUTES, DEFAULT_RESET_TOKEN_MINUTES);
    }

    /** Xoa cache — test doi setting xong goi ham nay de khong phai cho het TTL. */
    public static void clearCache() {
        CACHE.clear();
    }

    private static int readInt(String key, int defaultValue) {
        CachedValue cached = CACHE.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.readAtMs < CACHE_TTL_MS) {
            return cached.value;
        }

        int value = defaultValue;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT SettingValue FROM SystemSettings WHERE SettingKey = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String raw = rs.getString(1);
                    try {
                        int parsed = Integer.parseInt(raw == null ? "" : raw.trim());
                        if (parsed > 0) {
                            value = parsed;
                        } else {
                            LOG.warning("SecuritySettings: '" + key + "' = '" + raw
                                    + "' khong duong, dung mac dinh " + defaultValue + ".");
                        }
                    } catch (NumberFormatException ex) {
                        LOG.warning("SecuritySettings: '" + key + "' = '" + raw
                                + "' khong phai so, dung mac dinh " + defaultValue + ".");
                    }
                }
            }
        } catch (SQLException ex) {
            // Khong nuot: co log kem ngu canh, va hanh vi thay the la mac dinh CHAT (5 lan / 15 phut /
            // 10 ky tu), tuc phia an toan. Khong ban ra ngoai de mot su co DB khong lam chet trang login
            // — trang do con phai bao loi tu te cho nguoi dung.
            LOG.warning("SecuritySettings: khong doc duoc '" + key + "' (" + ex.getMessage()
                    + "), dung mac dinh " + defaultValue + ".");
        }

        CACHE.put(key, new CachedValue(value, now));
        return value;
    }

    private static final class CachedValue {
        private final int value;
        private final long readAtMs;

        private CachedValue(int value, long readAtMs) {
            this.value = value;
            this.readAtMs = readAtMs;
        }
    }
}
