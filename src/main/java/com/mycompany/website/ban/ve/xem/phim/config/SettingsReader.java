package com.mycompany.website.ban.ve.xem.phim.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Doc mot gia tri so tu {@code SystemSettings}, co cache ngan trong process.
 *
 * <p>Cung nguyen tac voi {@link SecuritySettings}: cau hinh doc duoc thi dung, khong doc duoc
 * thi dung mac dinh <b>an toan hon</b> va ghi log kem nguyen nhan — khong nuot loi, cung khong
 * nem ra ngoai lam chet trang hien thi.</p>
 *
 * <p>Tach rieng khoi {@code SecuritySettings} vi lop do chi noi ve nguong dang nhap; cac cau
 * hinh nghiep vu khac (cua so "sap het chieu", buffer don phong...) dung lop nay.</p>
 */
public final class SettingsReader {
    private static final Logger LOG = Logger.getLogger(SettingsReader.class.getName());
    private static final long CACHE_TTL_MS = 60_000L;
    private static final ConcurrentHashMap<String, CachedValue> CACHE = new ConcurrentHashMap<>();

    private SettingsReader() {
    }

    /**
     * Doc mot so nguyen trong khoang {@code [min, max]}.
     *
     * <p>Gia tri ngoai khoang hoac khong parse duoc bi tu choi va thay bang {@code defaultValue},
     * kem log {@code WARNING} — mot o cau hinh go nham khong duoc lam doi hanh vi nghiep vu
     * theo huong khong ai luong truoc.</p>
     */
    public static int readInt(String key, int defaultValue, int min, int max) {
        long now = System.currentTimeMillis();
        CachedValue cached = CACHE.get(key);
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
                        if (parsed >= min && parsed <= max) {
                            value = parsed;
                        } else {
                            LOG.warning("SettingsReader: '" + key + "' = '" + raw + "' ngoai khoang ["
                                    + min + ", " + max + "], dung mac dinh " + defaultValue + ".");
                        }
                    } catch (NumberFormatException ex) {
                        LOG.warning("SettingsReader: '" + key + "' = '" + raw
                                + "' khong phai so, dung mac dinh " + defaultValue + ".");
                    }
                }
            }
        } catch (SQLException | RuntimeException ex) {
            LOG.warning("SettingsReader: khong doc duoc '" + key + "' (" + ex.getMessage()
                    + "), dung mac dinh " + defaultValue + ".");
        }

        CACHE.put(key, new CachedValue(value, now));
        return value;
    }

    /** Xoa cache — test doi setting xong goi ham nay de khong phai cho het TTL. */
    public static void clearCache() {
        CACHE.clear();
    }

    private record CachedValue(int value, long readAtMs) {}
}
