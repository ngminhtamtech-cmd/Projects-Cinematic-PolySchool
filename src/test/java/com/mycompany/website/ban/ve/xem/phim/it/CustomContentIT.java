package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Noi dung tuy bien phai phan anh dung CSDL (CT-01).
 *
 * <p>Truoc ban sua, {@code CustomContentHelper} tu sinh mock khi setting trong. Nguoi dung bao
 * "xoa du lieu roi ma trang van hien nguyen" — chinh la mock nay. Bo test khoa lai ba dieu:
 * DB trong thi rong, co du lieu thi dung du lieu do, JSON hong thi bao loi ro rang.</p>
 */
@Tag("it")
@DisplayName("Noi dung tuy bien — khong con du lieu mock")
public class CustomContentIT {

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private static final String[] KEYS = {
        "cinetags_data", "corner_items_data", "events_data", "special_cinemas_data"
    };

    @BeforeAll
    public static void setUp() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
    }

    @AfterAll
    public static void tearDown() throws SQLException {
        clearSettings();
        DBConnection.shutdown();
    }

    @AfterEach
    public void cleanUp() throws SQLException {
        clearSettings();
    }

    @Test
    @DisplayName("CT-01: DB trong thi moi muc noi dung deu rong, khong sinh mock")
    public void emptyDatabaseYieldsEmptyLists() throws SQLException {
        clearSettings();

        assertTrue(CustomContentHelper.getCineTagProducts("movie-verse").isEmpty(),
                "CineTag phai rong khi DB trong — truoc day tra ve 4 san pham mock");
        assertTrue(CustomContentHelper.getCineTagProducts("fan-wibu").isEmpty());
        assertTrue(CustomContentHelper.getCornerItems("dien-vien").isEmpty(),
                "Goc dien anh phai rong — truoc day tra ve 4 dien vien mock");
        assertTrue(CustomContentHelper.getCornerItems("blog").isEmpty());
        assertTrue(CustomContentHelper.getEventItems("khuyen-mai").isEmpty(),
                "Su kien phai rong — truoc day tra ve 6 su kien mock");
        assertTrue(CustomContentHelper.getSpecialCinemas().isEmpty(),
                "Rap dac biet phai rong — truoc day tra ve 3 phong mock");
    }

    @Test
    @DisplayName("CT-01: mot muc that KHONG bi mock chen them vao")
    public void singleRealItemIsNotPaddedWithMocks() throws SQLException {
        setSetting("cinetags_data",
                "[{\"tag\":\"movie-verse\",\"name\":\"San pham that\",\"price\":123000,\"imageUrl\":\"/x.png\"}]");

        var products = CustomContentHelper.getCineTagProducts("movie-verse");
        assertEquals(1, products.size(),
                "Chi duoc tra ve dung mot muc admin nhap, khong duoc don them mock");
        assertEquals("San pham that", products.get(0).getName());
    }

    @Test
    @DisplayName("CT-01: section khong co muc nao thi rong, du section khac co du lieu")
    public void emptySectionStaysEmpty() throws SQLException {
        setSetting("corner_items_data",
                "[{\"section\":\"dien-vien\",\"title\":\"Dien vien that\",\"subtitle\":\"\","
                + "\"imageUrl\":\"\",\"description\":\"\",\"likes\":1,\"views\":2}]");

        assertEquals(1, CustomContentHelper.getCornerItems("dien-vien").size());
        assertTrue(CustomContentHelper.getCornerItems("dao-dien").isEmpty(),
                "Section chua co du lieu phai rong, khong duoc rot mock vao");
        assertTrue(CustomContentHelper.getCornerItems("blog").isEmpty());
    }

    @Test
    @DisplayName("CT-01: JSON hong bao loi ro rang thay vi am tham hien mock")
    public void brokenJsonFailsLoudly() throws SQLException {
        setSetting("events_data", "{ khong phai JSON hop le ");

        BookingException ex = assertThrows(BookingException.class,
                () -> CustomContentHelper.getEventItems("khuyen-mai"));
        assertEquals(500, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("events_data"),
                "Thong bao phai chi ro setting nao hong: " + ex.getMessage());
    }

    @Test
    @DisplayName("CT-01: so sanh phan loai khong phan biet hoa thuong va khoang trang")
    public void sectionMatchingIsForgiving() throws SQLException {
        setSetting("special_cinemas_data",
                "[{\"title\":\"GOLD CLASS\",\"address\":\"Q1\",\"imageUrl\":\"\",\"description\":\"d\"}]");
        assertEquals(1, CustomContentHelper.getSpecialCinemas().size());

        setSetting("cinetags_data",
                "[{\"tag\":\" Movie-Verse \",\"name\":\"A\",\"price\":1,\"imageUrl\":\"\"}]");
        assertEquals(1, CustomContentHelper.getCineTagProducts("movie-verse").size(),
                "Tag co khoang trang / khac hoa thuong van phai khop");
    }

    // ---------------------------------------------------------------- fixtures

    private static void setSetting(String key, String value) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     MERGE SystemSettings AS target
                     USING (SELECT ? AS SettingKey, ? AS SettingValue) AS src
                     ON target.SettingKey = src.SettingKey
                     WHEN MATCHED THEN UPDATE SET SettingValue = src.SettingValue
                     WHEN NOT MATCHED THEN INSERT (SettingKey, SettingValue)
                       VALUES (src.SettingKey, src.SettingValue);
                     """)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private static void clearSettings() throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            for (String key : KEYS) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM SystemSettings WHERE SettingKey = ?")) {
                    ps.setString(1, key);
                    ps.executeUpdate();
                }
            }
        }
    }
}
