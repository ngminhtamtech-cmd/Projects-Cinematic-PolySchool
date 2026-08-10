package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class NoSwallowedExceptionTest {
    private static final Pattern FORBIDDEN = Pattern.compile(
            "catch\\s*\\([^)]*(ignored|ignore)\\b|//\\s*ignore\\b|printStackTrace\\s*\\(");

    /**
     * Bien loi doc thanh "khong co du lieu" (F-005).
     *
     * <p>Mot {@code catch} chi tra ve tap rong lam hang doi khang cao, hang cho kiem duyet binh luan
     * hay danh sach phim thuoc rap trong y het nhu du lieu that su khong co. Admin khong the biet
     * minh dang xem mot hang doi rong hay mot hang doi khong doc duoc.</p>
     */
    private static final Pattern SILENT_EMPTY_RESULT = Pattern.compile(
            "catch\\s*\\([^)]*\\)\\s*\\{\\s*return\\s+(?:new\\s+"
                    + "(?:ArrayList|LinkedList|HashSet|LinkedHashSet|HashMap|LinkedHashMap|TreeMap|TreeSet)"
                    + "\\s*<[^>]*>\\s*\\(\\s*\\)|(?:List|Set|Map)\\.of\\(\\s*\\)"
                    + "|Collections\\.empty(?:List|Set|Map)\\(\\s*\\))\\s*;\\s*\\}");

    @Test
    void productionSourceContainsNoKnownSwallowedExceptionPattern() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    int line = 0;
                    for (String text : Files.readAllLines(path)) {
                        line++;
                        if (FORBIDDEN.matcher(text).find()) {
                            violations.add(path + ":" + line + ": " + text.trim());
                        }
                    }
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });
        }
        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    @Test
    void noCatchBlockTurnsReadFailureIntoEmptyResult() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path path : productionSources()) {
            String source = Files.readString(path);
            var matcher = SILENT_EMPTY_RESULT.matcher(source);
            while (matcher.find()) {
                long line = source.substring(0, matcher.start()).chars().filter(ch -> ch == '\n').count() + 1;
                violations.add(path + ":" + line + ": " + matcher.group().replaceAll("\\s+", " "));
            }
        }
        assertTrue(violations.isEmpty(),
                "Loi doc du lieu bi bien thanh ket qua rong:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations));
    }

    /**
     * Mat nguyen nhan loi CSDL (N-12 / OBS-01).
     *
     * <p><b>Khoang trong da duoc bit.</b> Lop test nay truoc day chi bat
     * {@code catch (… ignored)}, {@code // ignore}, {@code printStackTrace} va
     * {@code catch → collection rong}. No <b>khong</b> phat hien duoc mau pho bien nhat trong
     * {@code AdminService}:</p>
     *
     * <pre>catch (SQLException ex) { throw new BookingException(500, "..."); }</pre>
     *
     * <p>{@code ex} bi bo di hoan toan: stack trace that — deadlock, sai ten cot, timeout — bien
     * mat, con nguoi van hanh chi thay mot cau tieng Viet chung chung. Dung 58 diem nhu vay ton
     * tai khi N-12 duoc phat hien, va {@code topFilms()} (mat cause) nam ngay canh
     * {@code topFilms(User)} (co cause) — duong admin, tuc dung duong RP-01 bao loi, la duong bi
     * bo sot.</p>
     *
     * <p>Luat: mot khoi {@code catch} bat {@code SQLException} hay {@code DaoException} thi than
     * khoi PHAI nhac den bien da bat — de rethrow, de lam cause, hoac de log. Khong nhac den
     * nghia la nguyen nhan da bi vut bo.</p>
     */
    @Test
    void noDatabaseCatchDropsItsCause() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path path : productionSources()) {
            String source = Files.readString(path);
            var matcher = DATABASE_CATCH.matcher(source);
            while (matcher.find()) {
                String variable = matcher.group(2);
                String body = blockBody(source, matcher.end() - 1);
                if (Pattern.compile("\\b" + Pattern.quote(variable) + "\\b").matcher(body).find()) {
                    continue;
                }
                long line = source.substring(0, matcher.start()).chars()
                        .filter(ch -> ch == '\n').count() + 1;
                violations.add(path + ":" + line + ": catch (" + matcher.group(1).trim() + " "
                        + variable + ") khong dung toi " + variable);
            }
        }
        assertTrue(violations.isEmpty(),
                "Nguyen nhan loi CSDL bi vut bo — hay truyen lam cause (new BookingException(500, msg, ex))"
                        + " hoac log kem ngu canh:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations));
    }

    private static final Pattern DATABASE_CATCH = Pattern.compile(
            "catch\\s*\\(\\s*((?:[\\w.]+\\s*\\|\\s*)*[\\w.]*(?:SQLException|DaoException)"
                    + "(?:\\s*\\|\\s*[\\w.]+)*)\\s+([A-Za-z_$][\\w$]*)\\s*\\)\\s*\\{");

    /** Than cua khoi bat dau tai dau {@code '{'} o {@code openBrace}, khong ke hai dau ngoac. */
    private static String blockBody(String source, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, i);
                }
            }
        }
        return source.substring(openBrace);
    }

    private static List<Path> productionSources() throws IOException {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
