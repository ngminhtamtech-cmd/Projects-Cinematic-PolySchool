package com.mycompany.website.ban.ve.xem.phim.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chinh sach do manh mat khau (D10).
 *
 * <p>Ham thuan, khong dung DB va khong dung servlet API — de unit test chay duoc khong can gi ca.
 * Nguoi goi truyen {@code minLength} vao (lay tu {@code SystemSettings.security.passwordMinLength}),
 * neu khong biet thi dung {@link #DEFAULT_MIN_LENGTH}.</p>
 *
 * <p><b>Chinh sach nay chi ap cho mat khau MOI</b> — dang ky, doi mat khau, dat lai mat khau,
 * admin tao tai khoan. Nguoi dung cu khong bi buoc doi ngay (rang buoc cua ke hoach P10).</p>
 */
public final class PasswordPolicy {
    private static final Logger LOG = Logger.getLogger(PasswordPolicy.class.getName());

    /** Toi thieu 10 ky tu — dai hon nhieu so voi muc 6 cu. */
    public static final int DEFAULT_MIN_LENGTH = 10;

    /** Phai co it nhat 3 trong 4 nhom: thuong, hoa, so, ky tu dac biet. */
    public static final int REQUIRED_CHARACTER_GROUPS = 3;

    private static final String BLACKLIST_RESOURCE = "/common-passwords.txt";

    /**
     * Luoi do cuoi cung khi khong doc duoc file blacklist trong WAR.
     *
     * <p>Danh sach rong se lam chinh sach yeu di am tham — dung loai loi ma CLAUDE.md canh bao.
     * Nen giu san mot nhum toi thieu: mat file thi van chan duoc nhung mat khau te nhat.</p>
     */
    private static final List<String> FALLBACK_BLACKLIST = List.of(
            "password", "password1", "password123", "passw0rd", "123456", "1234567",
            "12345678", "123456789", "1234567890", "qwerty", "qwertyuiop", "iloveyou",
            "admin", "admin123", "administrator", "letmein", "welcome", "welcome123",
            "abc123", "monkey", "dragon", "sunshine", "princess", "football",
            "matkhau", "matkhau123", "vietnam", "vietnam123");

    private static final Set<String> BLACKLIST = loadBlacklist();

    private PasswordPolicy() {
    }

    /**
     * Ket qua kiem tra: hop le hay khong, kem thong bao tieng Viet de hien thang cho nguoi dung.
     */
    public static final class Result {
        private final boolean valid;
        private final String message;

        private Result(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        /** Rong khi hop le. */
        public String getMessage() {
            return message;
        }
    }

    private static final Result OK = new Result(true, "");

    public static Result validate(String password) {
        return validate(password, DEFAULT_MIN_LENGTH, null);
    }

    public static Result validate(String password, int minLength) {
        return validate(password, minLength, null);
    }

    /**
     * @param password  mat khau tho nguoi dung nhap
     * @param minLength do dai toi thieu, thuong lay tu SystemSettings
     * @param identity  email hoac ten dang nhap; mat khau khong duoc chua phan dinh danh nay.
     *                  Truyen {@code null} neu chua biet.
     */
    public static Result validate(String password, int minLength, String identity) {
        int required = minLength > 0 ? minLength : DEFAULT_MIN_LENGTH;

        if (password == null || password.isEmpty()) {
            return new Result(false, "Vui lòng nhập mật khẩu.");
        }
        if (password.length() < required) {
            return new Result(false, "Mật khẩu phải có ít nhất " + required + " ký tự.");
        }
        if (password.chars().distinct().count() < 4) {
            return new Result(false, "Mật khẩu quá đơn điệu, hãy dùng nhiều loại ký tự khác nhau.");
        }
        if (countGroups(password) < REQUIRED_CHARACTER_GROUPS) {
            return new Result(false, "Mật khẩu phải có ít nhất " + REQUIRED_CHARACTER_GROUPS
                    + " trong 4 nhóm: chữ thường, chữ hoa, chữ số, ký tự đặc biệt.");
        }

        String lower = password.toLowerCase(Locale.ROOT);
        if (BLACKLIST.contains(lower) || BLACKLIST.contains(stripTrailingDigits(lower))) {
            return new Result(false, "Mật khẩu này nằm trong danh sách mật khẩu phổ biến, hãy chọn mật khẩu khác.");
        }
        if (isSequential(lower)) {
            return new Result(false, "Mật khẩu không được là một dãy ký tự liên tiếp (ví dụ 1234567890).");
        }

        String identityPart = localPart(identity);
        if (identityPart != null && identityPart.length() >= 4 && lower.contains(identityPart)) {
            return new Result(false, "Mật khẩu không được chứa email hoặc tên đăng nhập của bạn.");
        }
        return OK;
    }

    /** Dem so nhom ky tu xuat hien: thuong, hoa, so, dac biet. */
    private static int countGroups(String password) {
        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        boolean special = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isLowerCase(ch)) {
                lower = true;
            } else if (Character.isUpperCase(ch)) {
                upper = true;
            } else if (Character.isDigit(ch)) {
                digit = true;
            } else {
                special = true;
            }
        }
        return (lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0) + (special ? 1 : 0);
    }

    /** "password2024" -> "password": bat kieu ne blacklist bang cach them nam sinh vao duoi. */
    private static String stripTrailingDigits(String value) {
        int end = value.length();
        while (end > 0 && Character.isDigit(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    /** Bat "1234567890", "abcdefghij" va ban dao nguoc cua chung. */
    private static boolean isSequential(String value) {
        if (value.length() < 4) {
            return false;
        }
        boolean ascending = true;
        boolean descending = true;
        for (int i = 1; i < value.length(); i++) {
            int delta = value.charAt(i) - value.charAt(i - 1);
            if (delta != 1) {
                ascending = false;
            }
            if (delta != -1) {
                descending = false;
            }
            if (!ascending && !descending) {
                return false;
            }
        }
        return true;
    }

    /** Lay phan truoc dau @ cua email; voi ten dang nhap thi tra ve chinh no. */
    private static String localPart(String identity) {
        if (identity == null || identity.isBlank()) {
            return null;
        }
        String value = identity.trim().toLowerCase(Locale.ROOT);
        int at = value.indexOf('@');
        return at > 0 ? value.substring(0, at) : value;
    }

    private static Set<String> loadBlacklist() {
        Set<String> result = new HashSet<>(FALLBACK_BLACKLIST);
        try (InputStream in = PasswordPolicy.class.getResourceAsStream(BLACKLIST_RESOURCE)) {
            if (in == null) {
                // Fail-safe: van con chinh sach do dai + nhom ky tu + nhum toi thieu o tren.
                // Khong nem loi de mot loi dong goi khong lam chet ca ung dung, nhung phai keu to.
                LOG.severe("PasswordPolicy: khong tim thay " + BLACKLIST_RESOURCE
                        + " trong classpath, chi dung danh sach du phong " + FALLBACK_BLACKLIST.size() + " muc.");
                return Collections.unmodifiableSet(result);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String value = line.trim().toLowerCase(Locale.ROOT);
                    if (!value.isEmpty() && !value.startsWith("#")) {
                        result.add(value);
                    }
                }
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "PasswordPolicy: loi doc " + BLACKLIST_RESOURCE
                    + ", chi dung danh sach du phong.", ex);
        }
        return Collections.unmodifiableSet(result);
    }

    /** So muc trong blacklist — dung cho test va cho log khoi dong. */
    public static int blacklistSize() {
        return BLACKLIST.size();
    }
}
