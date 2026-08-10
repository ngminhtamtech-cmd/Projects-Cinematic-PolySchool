package com.mycompany.website.ban.ve.xem.phim.it;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Muc 0.2 — don du lieu dang chan fix25.
 *
 * <p><b>Van de.</b> {@code fix25} co chu y khong xoa du lieu nguoi dung: gap nhom
 * {@code (UserId, FilmId)} trung thi no bao cao roi bo qua buoc tao unique index. Tren
 * {@code CineBookDB} co ba dong Id 9/10/11 cua {@code khanh.linh@cinebook.local} cho phim 21,
 * nen {@code UQ_Comments_UserId_FilmId} chua bao gio ton tai — luat "mot nguoi mot danh gia"
 * chi duoc tang service giu, khong co lop chan o CSDL.</p>
 *
 * <p>Chu du an da quyet: giu Id 11 (moi nhat), xoa Id 9 va 10. {@code fix26} lam dung viec do
 * va <b>chi</b> viec do — xoa theo Id nêu ten kem chu ky (dung chu, dung phim, dong giu lai con
 * nguyen), khong quet rong theo dieu kien.</p>
 *
 * <p>Test chay tren DB rac rieng co prefix {@code CineBookIT_}, dung va xoa trong cung mot lan
 * chay — khong dung {@code CineBookDB} lan {@code CineBookDB_Test}.</p>
 */
@Tag("it")
@DisplayName("0.2 — fix26 don danh gia trung de fix25 tao duoc unique index")
public class Batch02CommentsUniqueIndexIT {

    private static final String PROBE_DB = "CineBookIT_"
            + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
            + "_fix26";
    private static final Path FIX25 = Path.of("database", "fix25_comments_one_review_per_film.sql");
    private static final Path FIX26 = Path.of("database", "fix26_comments_duplicate_reviews.sql");

    private static String masterUrl;
    private static String user;
    private static String password;

    @BeforeAll
    public static void setUp() throws Exception {
        Properties props = loadTestDbProperties();
        String url = props.getProperty("db.url");
        assertNotNull(url, "db.test.properties phai khai bao db.url");
        masterUrl = url.replaceFirst("databaseName=[^;]+", "databaseName=master");
        user = props.getProperty("db.username");
        password = props.getProperty("db.password");
        Class.forName(props.getProperty("db.driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver"));
    }

    @AfterAll
    public static void tearDown() throws SQLException {
        dropProbe();
    }

    @Test
    @DisplayName("fix26 xoa dung Id 9 va 10, giu Id 11, roi fix25 tao duoc unique index")
    public void fix26ClearsTheDuplicatesSoFix25CanCreateTheIndex() throws Exception {
        buildProbe();

        try (Connection probe = connectTo(PROBE_DB)) {
            // Tien de: dung nguyen si trang thai cua CineBookDB — fix25 bo qua vi con trung.
            runScript(probe, FIX25);
            assertEquals(0, uniqueIndexCount(probe),
                    "tien de: con nhom trung thi fix25 phai BO QUA viec tao index");

            runScript(probe, FIX26);

            assertEquals(0, scalar(probe, "SELECT COUNT(*) FROM dbo.Comments WHERE Id IN (9, 10)"),
                    "fix26 phai xoa hai dong cu");
            assertEquals(1, scalar(probe, "SELECT COUNT(*) FROM dbo.Comments WHERE Id = 11"),
                    "fix26 phai GIU dong moi nhat (Id 11)");
            assertEquals(2, scalar(probe, "SELECT COUNT(*) FROM dbo.Comments WHERE Id NOT IN (9, 10, 11)"),
                    "danh gia cua nguoi khac / phim khac khong duoc dung toi");

            runScript(probe, FIX25);
            assertEquals(1, uniqueIndexCount(probe),
                    "het trung thi fix25 phai tao UQ_Comments_UserId_FilmId");

            // Idempotent: ca hai script chay lai deu khong doi gi.
            runScript(probe, FIX26);
            runScript(probe, FIX25);
            assertEquals(1, uniqueIndexCount(probe), "chay lai khong duoc nhan doi index");
            assertEquals(3, scalar(probe, "SELECT COUNT(*) FROM dbo.Comments"),
                    "chay lai khong duoc xoa them dong nao");

            // Index phai thuc su chan duoc danh gia thu hai.
            SQLException rejected = assertThrows(SQLException.class,
                    () -> exec(probe, "INSERT INTO dbo.Comments (Id, UserId, FilmId, Rate, Content, Report, CreatedAt) "
                            + "VALUES (12, 2, 21, 4, N'danh gia lan hai', 0, GETDATE())"));
            assertTrue(rejected.getMessage().contains("UQ_Comments_UserId_FilmId"), rejected.getMessage());
        }
    }

    @Test
    @DisplayName("fix26 khong xoa gi khi dong giu lai (Id 11) khong dung chu/phim")
    public void fix26RefusesToDeleteWhenTheKeeperRowDoesNotMatch() throws Exception {
        buildProbe();

        try (Connection probe = connectTo(PROBE_DB)) {
            // Dong Id 11 thuoc ve nguoi khac -> khong con la tinh huong da duoc quyet dinh.
            exec(probe, "UPDATE dbo.Comments SET UserId = 3 WHERE Id = 11");

            runScript(probe, FIX26);

            assertEquals(2, scalar(probe, "SELECT COUNT(*) FROM dbo.Comments WHERE Id IN (9, 10)"),
                    "chu ky khong khop thi fix26 phai bo qua, khong duoc xoa mu");
        }
    }

    // ------------------------------------------------------------------- fixtures

    /**
     * DB rac toi thieu: dung nhom trung cua CineBookDB (Id 9/10/11 — user 2, phim 21) cong
     * hai dong doi chung de bat truong hop script xoa rong tay.
     */
    private static void buildProbe() throws SQLException {
        try (Connection master = connectTo("master")) {
            dropProbe();
            exec(master, "CREATE DATABASE " + PROBE_DB);
        }
        try (Connection probe = connectTo(PROBE_DB)) {
            exec(probe, "CREATE TABLE dbo.Users (Id INT PRIMARY KEY, Email NVARCHAR(255) NOT NULL)");
            exec(probe, "CREATE TABLE dbo.Films (Id INT PRIMARY KEY, Title NVARCHAR(255) NOT NULL)");
            exec(probe, "CREATE TABLE dbo.Comments (Id INT PRIMARY KEY, UserId INT NOT NULL, "
                    + "FilmId INT NOT NULL, Rate INT NOT NULL, Content NVARCHAR(1000) NULL, "
                    + "Report BIT NOT NULL DEFAULT 0, CreatedAt DATETIME NOT NULL)");

            exec(probe, "INSERT INTO dbo.Users (Id, Email) VALUES "
                    + "(2, N'khanh.linh@cinebook.local'), (3, N'nguoi.khac@cinebook.local')");
            exec(probe, "INSERT INTO dbo.Films (Id, Title) VALUES (21, N'Phim 21'), (22, N'Phim 22')");
            exec(probe, "INSERT INTO dbo.Comments (Id, UserId, FilmId, Rate, Content, Report, CreatedAt) VALUES "
                    + "(9,  2, 21, 5, N'QA danh gia lan 1', 0, '2026-07-31T22:43:11.767'), "
                    + "(10, 2, 21, 5, N'QA danh gia lan 2', 0, '2026-07-31T22:43:11.817'), "
                    + "(11, 2, 21, 5, N'QA danh gia lan 3', 0, '2026-07-31T22:43:11.850'), "
                    + "(20, 3, 21, 4, N'nguoi khac, cung phim', 0, '2026-07-30T10:00:00'), "
                    + "(21, 2, 22, 3, N'cung nguoi, phim khac', 0, '2026-07-30T11:00:00')");
        }
    }

    private static int uniqueIndexCount(Connection probe) throws SQLException {
        return scalar(probe, "SELECT COUNT(*) FROM sys.indexes WHERE name = 'UQ_Comments_UserId_FilmId' "
                + "AND object_id = OBJECT_ID('dbo.Comments')");
    }

    private static void dropProbe() throws SQLException {
        if (!PROBE_DB.matches("^CineBookIT_[0-9]{14}_[0-9a-fA-F]{8}_fix26$")) {
            throw new SQLException("Refusing unsafe fix26 probe cleanup: " + PROBE_DB);
        }
        try (Connection master = connectTo("master")) {
            exec(master, "IF DB_ID('" + PROBE_DB + "') IS NOT NULL BEGIN "
                    + "ALTER DATABASE " + PROBE_DB + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE; "
                    + "DROP DATABASE " + PROBE_DB + "; END");
        }
    }

    private static Connection connectTo(String databaseName) throws SQLException {
        return DriverManager.getConnection(
                masterUrl.replaceFirst("databaseName=[^;]+", "databaseName=" + databaseName), user, password);
    }

    private static void runScript(Connection connection, Path script) throws IOException, SQLException {
        assertTrue(Files.exists(script), "khong tim thay " + script);
        for (String batch : splitOnGo(Files.readString(script, StandardCharsets.UTF_8))) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(batch);
                while (statement.getMoreResults() || statement.getUpdateCount() != -1) {
                    // Vet het ket qua de loi o batch sau khong bi che.
                }
            }
        }
    }

    private static List<String> splitOnGo(String script) {
        List<String> batches = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\r?\n")) {
            if (line.trim().equalsIgnoreCase("GO")) {
                if (!current.toString().isBlank()) {
                    batches.add(current.toString());
                }
                current.setLength(0);
            } else {
                current.append(line).append(System.lineSeparator());
            }
        }
        if (!current.toString().isBlank()) {
            batches.add(current.toString());
        }
        return batches;
    }

    private static Properties loadTestDbProperties() throws IOException {
        Properties props = new Properties();
        Path onDisk = Path.of(System.getProperty("cinebook.it.config", "target/db.it.properties"));
        if (Files.exists(onDisk)) {
            try (InputStream in = Files.newInputStream(onDisk)) {
                props.load(in);
            }
            return props;
        }
        try (InputStream in = Batch02CommentsUniqueIndexIT.class.getClassLoader()
                .getResourceAsStream(System.getProperty("cinebook.it.config", "target/db.it.properties"))) {
            assertNotNull(in, "khong tim thay db.test.properties");
            props.load(in);
        }
        return props;
    }

    private static int scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void exec(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
