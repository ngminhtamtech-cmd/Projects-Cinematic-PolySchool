package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.util.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D10 — chinh sach do manh mat khau. Ham thuan nen day la unit test, luon chay, khong can DB.
 */
public class PasswordPolicyTest {

    @Test
    @DisplayName("'123456' — mat khau cua dinh nghia hoan thanh P10 — bi tu choi")
    public void testWeakClassicPasswordRejected() {
        PasswordPolicy.Result result = PasswordPolicy.validate("123456");
        assertFalse(result.isValid(), "'123456' phai bi tu choi");
        assertFalse(result.getMessage().isBlank(), "Phai co thong bao giai thich cho nguoi dung");
    }

    @ParameterizedTest
    @DisplayName("Mat khau ngan hon 10 ky tu bi tu choi du du nhom ky tu")
    @ValueSource(strings = {"Ab1!", "Abc123!@", "Xy9#Zq2", "P@ss1", "aB3$"})
    public void testTooShortRejected(String password) {
        assertFalse(PasswordPolicy.validate(password).isValid(), password + " ngan hon 10 ky tu");
    }

    @ParameterizedTest
    @DisplayName("Du dai nhung chi co 2 nhom ky tu thi van bi tu choi")
    @ValueSource(strings = {"abcdefghijkl", "ABCDEFGHIJKL", "abcdefgh1234", "ABCDEFGH1234"})
    public void testNotEnoughCharacterGroupsRejected(String password) {
        assertFalse(PasswordPolicy.validate(password).isValid(), password + " chi co 2 nhom ky tu");
    }

    @ParameterizedTest
    @DisplayName("Mat khau pho bien trong blacklist bi tu choi du dat do dai va so nhom")
    @ValueSource(strings = {"Password123", "Matkhau123", "Welcome123", "Qwerty1234", "Admin12345"})
    public void testBlacklistedRejected(String password) {
        assertFalse(PasswordPolicy.validate(password).isValid(), password + " nam trong danh sach pho bien");
    }

    @Test
    @DisplayName("Them chu so vao duoi mat khau pho bien khong lach duoc blacklist")
    public void testBlacklistIgnoresTrailingDigits() {
        assertFalse(PasswordPolicy.validate("Password2026").isValid());
        assertFalse(PasswordPolicy.validate("Matkhau20261").isValid());
    }

    @Test
    @DisplayName("Day ky tu lien tiep bi tu choi")
    public void testSequentialRejected() {
        assertFalse(PasswordPolicy.validate("1234567890").isValid());
        assertFalse(PasswordPolicy.validate("abcdefghij").isValid());
    }

    @Test
    @DisplayName("Mat khau chua phan dinh danh cua email bi tu choi")
    public void testPasswordContainingIdentityRejected() {
        PasswordPolicy.Result result =
                PasswordPolicy.validate("KhanhLinh@2026", PasswordPolicy.DEFAULT_MIN_LENGTH, "khanhlinh@cinebook.local");
        assertFalse(result.isValid(), "Mat khau chua 'khanhlinh' — phan truoc dau @ cua email");
    }

    @ParameterizedTest
    @DisplayName("Mat khau dat chuan duoc chap nhan")
    @ValueSource(strings = {"Tr0ngRap!2026", "Xem-Phim#88x", "cN9$rutBaoLau", "Ghe*Doi*A12z"})
    public void testStrongPasswordAccepted(String password) {
        PasswordPolicy.Result result = PasswordPolicy.validate(password);
        assertTrue(result.isValid(), password + " le ra phai hop le, nhung bi tu choi voi ly do: "
                + result.getMessage());
        assertEquals("", result.getMessage());
    }

    @Test
    @DisplayName("Nguong do dai lay tu tham so, khong hardcode")
    public void testMinLengthIsConfigurable() {
        String password = "Rap*Phim9";
        assertFalse(PasswordPolicy.validate(password, 10).isValid(), "9 ky tu < nguong 10");
        assertTrue(PasswordPolicy.validate(password, 8).isValid(), "9 ky tu >= nguong 8");
    }

    @Test
    @DisplayName("Null va chuoi rong bi tu choi, khong nem exception")
    public void testNullAndEmpty() {
        assertFalse(PasswordPolicy.validate(null).isValid());
        assertFalse(PasswordPolicy.validate("").isValid());
    }

    @Test
    @DisplayName("Blacklist doc duoc tu WAR, khong roi ve danh sach du phong")
    public void testBlacklistLoadedFromResource() {
        // File common-passwords.txt co hon 200 muc; danh sach du phong trong code chi 28.
        assertTrue(PasswordPolicy.blacklistSize() > 200,
                "Chi thay " + PasswordPolicy.blacklistSize() + " muc — nhieu kha nang khong doc duoc "
                        + "/common-passwords.txt tu classpath");
    }
}
