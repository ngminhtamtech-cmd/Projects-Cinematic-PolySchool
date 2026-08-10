package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.ComboFood;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pham vi quan ly combo theo cum rap (CB-01).
 *
 * <p>Truoc ban sua, {@code listCombos(actor)} tra thang {@code List.of()} cho manager va moi
 * thao tac ghi bi {@code requireGlobalCatalogAdmin} chan — manager mo trang Combo thay trang
 * trang. Bo test nay khoa lai hanh vi moi:</p>
 * <ul>
 *   <li>manager chi thay combo cua rap minh, khong thay combo legacy/global hoac rap khac;</li>
 *   <li>manager sua duoc combo cua rap minh;</li>
 *   <li>manager KHONG sua duoc combo dung chung va combo cua rap khac (403).</li>
 * </ul>
 */
@Tag("it")
@DisplayName("Combo — pham vi theo cum rap")
public class ComboScopeIT {

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private static final int CINEMA_A = 1;
    private static final int CINEMA_B = 2;

    private static AdminService adminService;
    private static User admin;
    private static User managerOfA;

    private int globalComboId;
    private int comboOfAId;
    private int comboOfBId;

    @BeforeAll
    public static void setUp() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        adminService = new AdminService();

        admin = new User();
        admin.setId(5);
        admin.setRole("admin");

        managerOfA = new User();
        managerOfA.setId(4);
        managerOfA.setRole("manager");
        managerOfA.setCinemaId(CINEMA_A);
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.shutdown();
    }

    @BeforeEach
    public void seedCombos() throws SQLException {
        exec("DELETE FROM ComboFoods WHERE Name LIKE N'IT combo%'");
        globalComboId = insertCombo("IT combo legacy dung chung", null, "inactive");
        comboOfAId = insertCombo("IT combo rap A", CINEMA_A);
        comboOfBId = insertCombo("IT combo rap B", CINEMA_B);
    }

    @Test
    @DisplayName("Manager chi thay combo rap minh")
    public void managerSeesOwnCinemaCombosOnly() {
        List<ComboFood> visible = adminService.listCombos(managerOfA);

        assertFalse(visible.isEmpty(),
                "Manager phai thay danh sach combo — truoc day ham nay tra ve List.of()");
        assertFalse(containsId(visible, globalComboId), "combo legacy khong duoc lo vao danh muc rap");
        assertTrue(containsId(visible, comboOfAId), "phai thay combo cua rap minh");
        assertFalse(containsId(visible, comboOfBId), "KHONG duoc thay combo cua rap khac");
    }

    @Test
    @DisplayName("Admin context toan he thong thay combo moi rap, khong thay legacy khong rap")
    public void adminSeesEverything() {
        List<ComboFood> visible = adminService.listCombos(admin);
        assertFalse(containsId(visible, globalComboId));
        assertTrue(containsId(visible, comboOfAId));
        assertTrue(containsId(visible, comboOfBId));
    }

    @Test
    @DisplayName("Manager sua duoc combo cua rap minh")
    public void managerCanEditOwnCinemaCombo() {
        ComboFood combo = adminService.findComboById(comboOfAId).orElseThrow();
        combo.setName("IT combo rap A - da sua");
        combo.setPrice(new BigDecimal("99000"));
        adminService.saveCombo(combo, managerOfA);

        ComboFood reloaded = adminService.findComboById(comboOfAId).orElseThrow();
        assertEquals("IT combo rap A - da sua", reloaded.getName());
        assertEquals(CINEMA_A, reloaded.getCinemaId(),
                "Pham vi phai giu nguyen sau khi sua");
    }

    @Test
    @DisplayName("Manager KHONG sua duoc combo dung chung toan he thong")
    public void managerCannotEditGlobalCombo() {
        ComboFood combo = adminService.findComboById(globalComboId).orElseThrow();
        combo.setName("IT combo dung chung - cuop quyen");

        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.saveCombo(combo, managerOfA));
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    @DisplayName("Manager KHONG doi trang thai combo cua rap khac")
    public void managerCannotToggleOtherCinemaCombo() {
        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.updateComboStatus(comboOfBId, "inactive", managerOfA));
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    @DisplayName("Manager KHONG xoa duoc combo cua rap khac")
    public void managerCannotDeleteOtherCinemaCombo() {
        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.deleteCombo(comboOfBId, managerOfA));
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    @DisplayName("Manager tao combo moi luon thuoc rap cua minh, du form gui rap khac")
    public void managerCreatedComboIsForcedToOwnCinema() {
        ComboFood forged = new ComboFood();
        forged.setName("IT combo gia mao");
        forged.setPrice(new BigDecimal("50000"));
        forged.setStatus("active");
        // Gia lap request bi sua tay: gui CinemaId cua rap khac.
        forged.setCinemaId(CINEMA_B);

        adminService.saveCombo(forged, managerOfA);

        ComboFood saved = adminService.findComboById(forged.getId()).orElseThrow();
        assertEquals(CINEMA_A, saved.getCinemaId(),
                "Server phai ep ve rap cua manager, khong tin CinemaId tu client");
    }

    @Test
    @DisplayName("Admin cung phai chon rap khi tao combo")
    public void adminCannotCreateGlobalCombo() {
        ComboFood combo = new ComboFood();
        combo.setName("IT combo dung chung moi");
        combo.setPrice(new BigDecimal("75000"));
        combo.setStatus("active");
        combo.setCinemaId(null);

        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.saveCombo(combo, admin));
        assertEquals(400, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- fixtures

    private boolean containsId(List<ComboFood> combos, int id) {
        return combos.stream().anyMatch(combo -> combo.getId() == id);
    }

    private static int insertCombo(String name, Integer cinemaId) throws SQLException {
        return insertCombo(name, cinemaId, "active");
    }

    private static int insertCombo(String name, Integer cinemaId, String status) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO ComboFoods (Name, Price, Status, CinemaId) VALUES (?, ?, ?, ?)",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setBigDecimal(2, new BigDecimal("69000"));
            ps.setString(3, status);
            if (cinemaId == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, cinemaId);
            }
            ps.executeUpdate();
            try (java.sql.ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private static void exec(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}
