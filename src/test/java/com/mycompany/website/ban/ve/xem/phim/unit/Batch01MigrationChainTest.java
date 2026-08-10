package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.config.AppContextListener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Muc 0.1 — chuoi migration khong duoc dut.
 *
 * <p><b>Vi sao co lop test nay.</b> {@code scripts/init-test-db.ps1} dung o fix23 roi seed,
 * nen fix24 (cot {@code Orders.RefundReject*}) va fix25 (unique index {@code Comments}) khong
 * bao gio chay tren mot may dung DB tu dau. Moc "380 test, 0 failure" chi dung tren DB da va
 * tay: ai clone ve chay lai se thay {@code rejectRefund} nem loi SQL "Invalid column name".</p>
 *
 * <p>Cai bay o day khong phai hai script cu the ma la <i>lop loi</i>: them script moi vao
 * {@code database/} roi quen dang ky vao chuoi. Nen test khong ghim cung ten fix24/fix25 —
 * no doi chieu toan bo thu muc voi mang {@code $migrations}, va se do lai voi fix27 neu ai do
 * lap lai dung sai lam nay.</p>
 *
 * <p>Kiem them mot lop bao ve thu hai: cot do migration them vao phai nam trong
 * {@code verifySchemaFailFast} — thieu cot phai chan ung dung ngay luc khoi dong, khong phai
 * doi den luc manager bam nut.</p>
 */
@DisplayName("0.1 — chuoi migration init-test-db.ps1 phai day du va dung thu tu")
public class Batch01MigrationChainTest {

    private static final Path INIT_SCRIPT = Path.of("scripts", "init-test-db.ps1");
    private static final Path DATABASE_DIR = Path.of("database");
    private static final Path CLAUDE_MD = Path.of("CLAUDE.md");

    /**
     * {@code fix00_backup_and_testdb.sql} chi tao DB rong nen dung ngoai chuoi — chinh
     * {@code init-test-db.ps1} ghi chu dieu do o dau mang.
     */
    private static final String OUT_OF_CHAIN = "fix00_backup_and_testdb.sql";

    @Test
    @DisplayName("moi fix*.sql trong database/ deu duoc dang ky trong $migrations")
    public void everyFixScriptIsRegisteredInTheChain() throws IOException {
        List<String> chain = migrationChain();
        List<String> missing = new ArrayList<>();
        for (String script : fixScriptsOnDisk()) {
            if (!chain.contains(script)) {
                missing.add(script);
            }
        }
        assertTrue(missing.isEmpty(),
                "scripts/init-test-db.ps1 bo sot " + missing.size() + " script: " + missing
                + "\nMay nao dung CineBookDB_Test tu dau se thieu doi tuong cua chung.");
    }

    @Test
    @DisplayName("fix*.sql chay tang dan theo so va TRUOC seed_test_fixtures.sql")
    public void fixScriptsRunInAscendingOrderBeforeSeeding() throws IOException {
        List<String> chain = migrationChain();

        int seedIndex = chain.indexOf("seed_test_fixtures.sql");
        assertTrue(seedIndex >= 0, "chuoi phai ket thuc bang seed_test_fixtures.sql");

        int previousNumber = -1;
        for (int i = 0; i < chain.size(); i++) {
            String script = chain.get(i);
            if (!script.startsWith("fix")) {
                continue;
            }
            assertTrue(i < seedIndex,
                    script + " chay SAU seed — cot do no them vao se khong co luc seed chay");
            int number = fixNumber(script);
            assertTrue(number > previousNumber,
                    "chuoi fix phai tang dan, nhung " + script + " dung sau so " + previousNumber);
            previousNumber = number;
        }
    }

    @Test
    @DisplayName("cot do fix24 them vao phai nam trong verifySchemaFailFast")
    public void refundRejectionColumnsAreCheckedAtStartup() {
        List<String> required = AppContextListener.requiredColumns();
        assertTrue(required.contains("Orders.RefundRejectedAt"),
                "thieu Orders.RefundRejectedAt trong requiredColumns — DB thieu cot se lo ra luc "
                + "manager bam Tu choi hoan tien chu khong phai luc khoi dong. Dang co: " + required);
        assertTrue(required.contains("Orders.RefundRejectReason"),
                "thieu Orders.RefundRejectReason trong requiredColumns. Dang co: " + required);
    }

    @Test
    @DisplayName("Orders.IsUserHidden is fail-fast and startup never mutates schema")
    public void orderHistoryColumnIsValidatedWithoutRuntimeDdl() throws IOException {
        List<String> required = AppContextListener.requiredColumns();
        assertTrue(required.contains("Orders.IsUserHidden"),
                "History reads Orders.IsUserHidden, so a missing migration must stop startup immediately");

        String listener = Files.readString(Path.of("src", "main", "java", "com", "mycompany",
                "website", "ban", "ve", "xem", "phim", "config", "AppContextListener.java"),
                StandardCharsets.UTF_8);
        assertTrue(!listener.contains("ensureOrdersIsUserHiddenColumn"),
                "Application startup must not ALTER production schema; fix39 is the only schema owner");
    }

    @Test
    @DisplayName("CLAUDE.md mo ta dung so script va dung fix cuoi chuoi")
    public void claudeMdDescribesTheRealChain() throws IOException {
        String doc = Files.readString(CLAUDE_MD, StandardCharsets.UTF_8);

        int highestFix = fixScriptsOnDisk().stream().mapToInt(Batch01MigrationChainTest::fixNumber)
                .max().orElseThrow();
        Matcher chainDoc = Pattern.compile("`fix01`…`fix(\\d+)`").matcher(doc);
        assertTrue(chainDoc.find(), "CLAUDE.md phai mo ta chuoi dang `fix01`…`fixNN`");
        assertEquals(highestFix, Integer.parseInt(chainDoc.group(1)),
                "CLAUDE.md noi chuoi dung o fix" + chainDoc.group(1)
                + " nhung database/ da co toi fix" + highestFix);

        long scriptCount = sqlFilesOnDisk().size();
        Matcher countDoc = Pattern.compile("\\((\\d+) script\\)").matcher(doc);
        assertTrue(countDoc.find(), "CLAUDE.md phai ghi so script trong database/");
        assertEquals(scriptCount, Integer.parseInt(countDoc.group(1)),
                "CLAUDE.md ghi " + countDoc.group(1) + " script nhung database/ dang co " + scriptCount);
    }

    // ------------------------------------------------------------------- helpers

    /** Ten file (khong ke thu muc) theo dung thu tu khai bao trong mang {@code $migrations}. */
    private static List<String> migrationChain() throws IOException {
        String script = Files.readString(INIT_SCRIPT, StandardCharsets.UTF_8);
        Matcher block = Pattern.compile("\\$migrations\\s*=\\s*@\\((.*?)\\)", Pattern.DOTALL)
                .matcher(script);
        if (!block.find()) {
            fail("khong doc duoc mang $migrations trong " + INIT_SCRIPT);
        }
        List<String> files = new ArrayList<>();
        Matcher entry = Pattern.compile("\"database\\\\([^\"]+)\"").matcher(block.group(1));
        while (entry.find()) {
            files.add(entry.group(1));
        }
        assertTrue(files.size() > 10, "mang $migrations doc ra qua ngan: " + files);
        return files;
    }

    private static List<String> fixScriptsOnDisk() throws IOException {
        List<String> scripts = new ArrayList<>();
        for (String name : sqlFilesOnDisk()) {
            if (name.startsWith("fix") && !name.equals(OUT_OF_CHAIN)) {
                scripts.add(name);
            }
        }
        return scripts;
    }

    private static List<String> sqlFilesOnDisk() throws IOException {
        try (Stream<Path> files = Files.list(DATABASE_DIR)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static int fixNumber(String scriptName) {
        Matcher matcher = Pattern.compile("^fix(\\d+)").matcher(scriptName);
        if (!matcher.find()) {
            throw new IllegalArgumentException("khong phai script fix: " + scriptName);
        }
        return Integer.parseInt(matcher.group(1));
    }
}
