package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.UserDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserDAO;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AccountStateGuard;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D5 — khoa tai khoan phai cat duoc phien dang chay.
 *
 * <p>Truoc P10, {@link User} nam trong session tu luc dang nhap va khong bao gio duoc doc lai,
 * nen khoa mot tai khoan khong co tac dung gi cho toi khi ho tu dang xuat.</p>
 */
@Tag("it")
public class SessionRevocationIT {

    private static final String EMAIL = "member_bronze@test.com";

    static {
        System.setProperty("cinebook.db.config", new File(System.getProperty("cinebook.it.config", "target/db.it.properties")).getAbsolutePath());
        DBConnection.shutdown();
    }

    private AccountStateGuard guard;
    private User sessionUser;

    @BeforeAll
    public static void initConnection() {
        System.setProperty("cinebook.db.config", new File(System.getProperty("cinebook.it.config", "target/db.it.properties")).getAbsolutePath());
        DBConnection.shutdown();
    }

    @AfterAll
    public static void cleanUp() throws SQLException {
        restoreAccount();
        DBConnection.shutdown();
    }

    @BeforeEach
    public void setUp() throws SQLException {
        restoreAccount();
        AccountStateGuard.clearCache();
        guard = new AccountStateGuard();
        UserDAO userDAO = new JdbcUserDAO();
        // "sessionUser" dong vai ban chup duoc cat trong HttpSession luc dang nhap.
        sessionUser = userDAO.findByEmail(EMAIL).orElseThrow(
                () -> new IllegalStateException("Fixture thieu " + EMAIL));
    }

    @AfterEach
    public void tearDown() throws SQLException {
        restoreAccount();
        AccountStateGuard.clearCache();
    }

    private static void restoreAccount() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE Users SET IsLocked = 0, Deleted = 0, LockReason = NULL, Role = 'member', "
                     + "CinemaId = NULL WHERE Email = ?")) {
            ps.setString(1, EMAIL);
            ps.executeUpdate();
        }
    }

    private void setColumn(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, EMAIL);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Tai khoan binh thuong: phien duoc di tiep")
    public void testHealthyAccountPasses() {
        assertEquals(AccountStateGuard.Verdict.OK, guard.evaluate(sessionUser));
    }

    @Test
    @DisplayName("Khoa tai khoan dang dang nhap -> lan kiem tra ke tiep cat phien")
    public void testLockedAccountIsRevoked() throws SQLException {
        assertEquals(AccountStateGuard.Verdict.OK, guard.evaluate(sessionUser), "Tien de: dang binh thuong");

        setColumn("UPDATE Users SET IsLocked = 1, LockReason = N'Test P10' WHERE Email = ?");
        AccountStateGuard.invalidate(sessionUser.getId());

        AccountStateGuard.Verdict verdict = guard.evaluate(sessionUser);
        assertEquals(AccountStateGuard.Verdict.LOCKED, verdict);
        assertTrue(verdict.isRevoked(), "LOCKED phai keo theo cat phien");
        assertFalse(verdict.getMessage().isBlank(), "Phai co thong bao cho nguoi dung");
    }

    @Test
    @DisplayName("Xoa mem tai khoan -> phien bi cat, khong phai duoc di tiep")
    public void testDeletedAccountIsRevoked() throws SQLException {
        setColumn("UPDATE Users SET Deleted = 1 WHERE Email = ?");
        AccountStateGuard.invalidate(sessionUser.getId());

        // Chinh la cai bay: findByEmail/findById deu loc Deleted = 0, nen neu findAuthState cung loc
        // thi ham nay se thay "khong tim thay" va... cho phien chay tiep.
        assertEquals(AccountStateGuard.Verdict.DELETED, guard.evaluate(sessionUser));
    }

    @Test
    @DisplayName("Doi vai tro -> phien mang quyen cu bi cat, buoc dang nhap lai")
    public void testRoleChangeIsRevoked() throws SQLException {
        // P17: manager bat buoc gan rap; thay doi quyen va scope trong cung mot lenh.
        setColumn("UPDATE Users SET Role = 'manager', CinemaId = 1 WHERE Email = ?");
        AccountStateGuard.invalidate(sessionUser.getId());

        assertEquals(AccountStateGuard.Verdict.CHANGED, guard.evaluate(sessionUser));
    }

    @Test
    @DisplayName("Doi pham vi rap (CinemaId) -> phien cu bi cat — bit lo IDOR cho P17")
    public void testCinemaScopeChangeIsRevoked() throws SQLException {
        assertEquals(AccountStateGuard.Verdict.OK, guard.evaluate(sessionUser), "Tien de: dang binh thuong");

        setColumn("UPDATE Users SET CinemaId = 1 WHERE Email = ?");
        AccountStateGuard.invalidate(sessionUser.getId());

        assertEquals(AccountStateGuard.Verdict.CHANGED, guard.evaluate(sessionUser),
                "Doi rap quan ly ma phien cu van chay tiep la mot lo IDOR mo san cho P17");
    }

    @Test
    @DisplayName("Mapper doc duoc CinemaId — thieu no thi moi staff/manager bi cat phien oan")
    public void testMapperReadsCinemaId() {
        User staff = new JdbcUserDAO().findByEmail("staff@test.com").orElseThrow();
        assertNotNull(staff.getCinemaId(),
                "staff@test.com co CinemaId = 1 trong fixture; mapper tra ve null nghia la no bo sot cot");

        AccountStateGuard.clearCache();
        assertEquals(AccountStateGuard.Verdict.OK, guard.evaluate(staff),
                "Staff co pham vi rap phai duoc lam viec binh thuong, khong bi cat phien");
    }

    @Test
    @DisplayName("Ket qua duoc cache: khong doc DB o moi request")
    public void testResultIsCached() throws SQLException {
        assertEquals(AccountStateGuard.Verdict.OK, guard.evaluate(sessionUser));

        setColumn("UPDATE Users SET IsLocked = 1 WHERE Email = ?");
        // KHONG goi invalidate: mo phong request ke tiep den trong vong 30 giay.
        assertEquals(AccountStateGuard.Verdict.OK, guard.evaluate(sessionUser),
                "Trong cua so cache, ket qua cu duoc dung lai — day la danh doi co chu y de khong "
                        + "them mot query cho moi request");

        AccountStateGuard.invalidate(sessionUser.getId());
        assertEquals(AccountStateGuard.Verdict.LOCKED, guard.evaluate(sessionUser),
                "Het cache thi phai thay trang thai that");
    }

    @Test
    @DisplayName("Nguoi dung chua dang nhap khong bi gi ca")
    public void testNullUserIsOk() {
        assertEquals(AccountStateGuard.Verdict.OK, guard.evaluate(null));
    }

    @Test
    @DisplayName("Su co DB tra UNAVAILABLE, khong logout hang loat va khong bi cache")
    public void testDatabaseFailureDoesNotRevokeEveryone() {
        AtomicInteger calls = new AtomicInteger();
        UserDAO broken = new JdbcUserDAO() {
            @Override
            public java.util.Optional<com.mycompany.website.ban.ve.xem.phim.model.UserAuthState>
                    findAuthState(int id) {
                calls.incrementAndGet();
                throw new com.mycompany.website.ban.ve.xem.phim.dao.DaoException("mo phong DB chet", null);
            }
        };
        AccountStateGuard.clearCache();
        AccountStateGuard failingGuard = new AccountStateGuard(broken);
        AccountStateGuard.Verdict first = failingGuard.evaluate(sessionUser);
        AccountStateGuard.Verdict second = failingGuard.evaluate(sessionUser);

        assertEquals(AccountStateGuard.Verdict.UNAVAILABLE, first);
        assertEquals(AccountStateGuard.Verdict.UNAVAILABLE, second);
        assertFalse(first.isRevoked(), "DB loi khong duoc logout hang loat");
        assertEquals(2, calls.get(), "UNAVAILABLE khong duoc cache; request sau phai thu lai DB");
    }
}
