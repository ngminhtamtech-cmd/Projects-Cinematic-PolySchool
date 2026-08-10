package com.mycompany.website.ban.ve.xem.phim.util;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;

/**
 * Thang bac vai tro, xep TUYEN TINH: quyen cao bao ham quyen thap.
 *
 * <pre>
 *   member (1)  &lt;  staff (2)  &lt;  manager (3)  &lt;  admin (4)
 * </pre>
 *
 * <p>Moi kiem tra deu di qua {@link #isAtLeast} nen chi so tuyet doi khong quan trong,
 * chi thu tu tuong doi moi quan trong. Vi vay khi chen {@code staff} vao giua,
 * ngu nghia cua tat ca cac kiem tra san co giu nguyen.</p>
 *
 * <p>He qua co chu y: manager/admin cung vao duoc khu vuc cua staff ({@code /staff/*}),
 * dung thuc te van hanh - quan ly phai lam duoc viec cua nhan vien quay.</p>
 */
public final class RoleUtil {
    private RoleUtil() {
    }

    public static boolean isAtLeast(String actualRole, String requiredRole) {
        return rank(actualRole) >= rank(requiredRole);
    }

    public static int rank(String role) {
        if (AppConstants.ROLE_ADMIN.equalsIgnoreCase(role)) {
            return 4;
        }
        if (AppConstants.ROLE_MANAGER.equalsIgnoreCase(role)) {
            return 3;
        }
        if (AppConstants.ROLE_STAFF.equalsIgnoreCase(role)) {
            return 2;
        }
        if (AppConstants.ROLE_MEMBER.equalsIgnoreCase(role)) {
            return 1;
        }
        return 0;
    }

    public static boolean isValidRole(String role) {
        return rank(role) > 0;
    }

    /** Ten hien thi tieng Viet, dung cho bang danh sach tai khoan va thanh dieu huong. */
    public static String displayName(String role) {
        if (AppConstants.ROLE_ADMIN.equalsIgnoreCase(role)) {
            return "Quản trị hệ thống";
        }
        if (AppConstants.ROLE_MANAGER.equalsIgnoreCase(role)) {
            return "Quản lý rạp";
        }
        if (AppConstants.ROLE_STAFF.equalsIgnoreCase(role)) {
            return "Nhân viên quầy vé";
        }
        if (AppConstants.ROLE_MEMBER.equalsIgnoreCase(role)) {
            return "Thành viên";
        }
        return "Không xác định";
    }
}
