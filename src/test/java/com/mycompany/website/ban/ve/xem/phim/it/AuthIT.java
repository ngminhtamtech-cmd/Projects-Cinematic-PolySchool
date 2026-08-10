package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.UserDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserDAO;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.util.PasswordUtil;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("it")
public class AuthIT {

    static {
        File f = new File(System.getProperty("cinebook.it.config", "target/db.it.properties"));
        System.setProperty("cinebook.db.config", f.getAbsolutePath());
        DBConnection.shutdown();
    }

    private UserDAO userDAO;

    @BeforeAll
    public static void initConnection() {
        File f = new File(System.getProperty("cinebook.it.config", "target/db.it.properties"));
        System.setProperty("cinebook.db.config", f.getAbsolutePath());
        DBConnection.shutdown();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE Users SET MembershipTier = 'BRONZE', TotalSpent = 0, LoyaltyPoints = 0 WHERE Email = 'member_bronze@test.com'")) {
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    @AfterAll
    public static void cleanUp() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("Verify seeded users exist in CineBookDB_Test and passwords match")
    public void testSeededUsersExist() {
        userDAO = new JdbcUserDAO();

        Optional<User> memberOpt = userDAO.findByEmail("member_bronze@test.com");
        assertTrue(memberOpt.isPresent(), "member_bronze@test.com should be present in CineBookDB_Test");
        User member = memberOpt.get();
        assertEquals("member", member.getRole());
        assertEquals("BRONZE", member.getMembershipTier());
        assertTrue(PasswordUtil.matches("password123", member.getPasswordHash()));

        Optional<User> staffOpt = userDAO.findByEmail("staff@test.com");
        assertTrue(staffOpt.isPresent());
        User staff = staffOpt.get();
        assertEquals("staff", staff.getRole());

        Optional<User> adminOpt = userDAO.findByEmail("admin@test.com");
        assertTrue(adminOpt.isPresent());
        assertEquals("admin", adminOpt.get().getRole());
    }

    @Test
    @DisplayName("Non-existent user email should return empty optional")
    public void testNonExistentUser() {
        userDAO = new JdbcUserDAO();
        Optional<User> opt = userDAO.findByEmail("non_existent_email@test.com");
        assertTrue(opt.isEmpty());
    }
}
