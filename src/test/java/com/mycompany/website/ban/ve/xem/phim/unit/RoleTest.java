package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.util.RoleUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RoleTest {

    @Test
    @DisplayName("Role hierarchy checks: guest(0) < member(1) < staff(2) < manager(3) < admin(4)")
    public void testRoleHierarchy() {
        assertEquals(0, RoleUtil.rank("guest"));
        assertEquals(1, RoleUtil.rank("member"));
        assertEquals(2, RoleUtil.rank("staff"));
        assertEquals(3, RoleUtil.rank("manager"));
        assertEquals(4, RoleUtil.rank("admin"));
    }

    @Test
    @DisplayName("RoleUtil.isAtLeast should verify cumulative hierarchy")
    public void testIsAtLeast() {
        assertTrue(RoleUtil.isAtLeast("admin", "manager"));
        assertTrue(RoleUtil.isAtLeast("manager", "staff"));
        assertTrue(RoleUtil.isAtLeast("staff", "member"));
        assertTrue(RoleUtil.isAtLeast("member", "member"));

        assertFalse(RoleUtil.isAtLeast("member", "staff"));
        assertFalse(RoleUtil.isAtLeast("staff", "admin"));
    }

    @Test
    @DisplayName("Role validation and display names")
    public void testRoleValidationAndDisplayNames() {
        assertTrue(RoleUtil.isValidRole("admin"));
        assertTrue(RoleUtil.isValidRole("staff"));
        assertFalse(RoleUtil.isValidRole("unknown"));

        assertEquals("Quản trị hệ thống", RoleUtil.displayName("admin"));
        assertEquals("Thành viên", RoleUtil.displayName("member"));
        assertEquals("Nhân viên quầy vé", RoleUtil.displayName("staff"));
    }
}
