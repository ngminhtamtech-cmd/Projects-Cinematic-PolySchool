package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C.4 (INV-6) — nguong khong duoc chay bang hang so mac dinh ma khong ai nhin thay.
 *
 * <p>{@code booking.maxOpenDraftsPerShowtime} dang chay bang {@code DEFAULT_MAX_OPEN_DRAFTS = 3}:
 * khong co dong nao trong {@code SystemSettings}, nen no khong xuat hien tren
 * {@code /system/config} va nguoi van hanh khong biet no ton tai, cung khong doi duoc. Day dung
 * la lop loi INV-6 ma BUG-01 vua sua cho {@code seat_hold_minutes}.</p>
 *
 * <p>Man hinh cau hinh liet ke <b>moi</b> dong cua {@code SystemSettings}, nen chi can co dong
 * seed la nguong len duoc man hinh — dieu kien can va du la dong do phai ton tai o DB dung tu
 * chuoi migration, khong phai do ai do them tay.</p>
 */
@Tag("it")
@DisplayName("C.4 — booking.maxOpenDraftsPerShowtime phai co trong SystemSettings")
public class C4MaxOpenDraftsSettingIT {

    private static final String SETTING_KEY = "booking.maxOpenDraftsPerShowtime";

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    @BeforeAll
    public static void setUpTestDb() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
    }

    @AfterAll
    public static void tearDownAll() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("dong seed ton tai tren DB dung tu chuoi migration")
    public void theSettingRowExists() throws SQLException {
        assertEquals(1, scalar("SELECT COUNT(*) FROM SystemSettings WHERE SettingKey=?", SETTING_KEY),
                "Khong co dong nay thi nguong khong hien tren /system/config va khong ai doi duoc"
                + " — dung lop loi INV-6.");
    }

    @Test
    @DisplayName("gia tri seed khop mac dinh dang chay trong ma")
    public void theSeededValueMatchesTheCodeDefault() throws SQLException {
        String value = text("SELECT SettingValue FROM SystemSettings WHERE SettingKey=?", SETTING_KEY);
        assertEquals("3", value == null ? null : value.trim(),
                "Dong seed phai bang DEFAULT_MAX_OPEN_DRAFTS, neu khong thi bat cong tac len la"
                + " doi hanh vi ma khong ai chu y");
    }

    /**
     * Dua mot nguong len man hinh nghia la ai cung go duoc so vao do, ke ca {@code 0} hay
     * {@code 999999}. Gia tri ngoai khoang phai bi kep lai chu khong duoc tin nguyen si: {@code 0}
     * thi khong ai dat duoc ve nao, con mot so rat lon thi tran chong lam dung bien mat.
     */
    @Test
    @DisplayName("gia tri ngoai khoang bi kep ve mac dinh, khong tin nguyen si")
    public void outOfRangeValuesAreClamped() {
        assertEquals(3, BookingService.clampMaxOpenDrafts(3), "gia tri hop le thi giu nguyen");
        assertEquals(10, BookingService.clampMaxOpenDrafts(10), "gia tri hop le thi giu nguyen");

        assertEquals(3, BookingService.clampMaxOpenDrafts(0),
                "0 nghia la khong ai dat duoc ve nao — phai ve mac dinh");
        assertEquals(3, BookingService.clampMaxOpenDrafts(-5), "so am phai ve mac dinh");
        assertEquals(3, BookingService.clampMaxOpenDrafts(999999),
                "so qua lon lam tran chong lam dung (BUG-04b) bien mat — phai ve mac dinh");
    }

    @Test
    @DisplayName("script seed nam trong chuoi migration va idempotent")
    public void theSeedScriptIsPartOfTheChain() throws IOException {
        Path chain = Path.of("scripts", "init-test-db.ps1");
        String script = Files.readString(chain, StandardCharsets.UTF_8);
        assertTrue(script.contains("fix27_max_open_drafts_setting.sql"),
                "Script seed phai duoc dang ky vao $migrations, neu khong may dung DB tu dau se"
                + " lai thieu dong cau hinh nay");
    }

    // ------------------------------------------------------------------- helpers

    private static int scalar(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                ps.setObject(index + 1, values[index]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private static String text(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                ps.setObject(index + 1, values[index]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
