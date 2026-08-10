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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N-03 · N-06 — migration phai nguyen tu: gap chan thi KHONG de lai thay doi nua voi.
 *
 * <p>Test chay tren cac DB rac rieng co prefix {@code CineBookIT_}, duoc dung va xoa
 * trong cung mot lan chay. KHONG dung
 * {@code CineBookDB} hay {@code CineBookDB_Test}: chay migration go cot len chung se
 * doi schema that.</p>
 */
@Tag("it")
@DisplayName("N-03/N-06 — migration nguyen tu, gap chan thi khong sua gi")
public class MigrationSafetyIT {

    private static final String PROBE_RUN_ID = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String FIX14_PROBE_DB = "CineBookIT_" + PROBE_RUN_ID + "_fix14";
    private static final String FIX22_PROBE_DB = "CineBookIT_" + PROBE_RUN_ID + "_fix22";

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
        dropProbe(FIX14_PROBE_DB);
        dropProbe(FIX22_PROBE_DB);
    }

    // ------------------------------------------------------------------------ N-03

    /**
     * Ban cu khong co transaction nao va moi khoi B1..B4 nam trong mot batch {@code GO}
     * rieng, nen {@code RAISERROR} muc 16 o B1 khong chan duoc B2a/B2b/B3/B4 — chung van
     * go cot cua chung. Day la khang dinh "Done khi" cua N-03: co MOT dong
     * {@code OrderCode <> TicketCode} thi KHONG cot nao duoc go.
     */
    @Test
    @DisplayName("N-03: 1 dong OrderCode <> TicketCode -> fix14 dung, khong cot nao bi go")
    public void fix14StopsWholeScriptWhenAnyLegacyColumnStillHasDivergentData() throws Exception {
        buildFix14Probe();

        try (Connection probe = connectTo(FIX14_PROBE_DB)) {
            SQLException failure = assertThrows(SQLException.class,
                    () -> runScript(probe, Path.of("database/fix14_schema_drift_alignment.sql")),
                    "co xung dot du lieu thi fix14 phai nem loi, khong duoc chay tiep");
            assertEquals(51414, failure.getErrorCode(),
                    "phai la loi cong chan cua fix14, khong phai loi ky thuat khac: " + failure.getMessage());

            // Diem cot loi cua N-03: ca NAM cot van con, ke ca cac cot cua khoi PHIA SAU
            // khoi bao loi. Ban cu chi giu lai Orders.OrderCode.
            for (String[] target : legacyColumns()) {
                assertNotNull(columnLength(probe, target[0], target[1]),
                        target[0] + "." + target[1] + " phai con nguyen khi fix14 dung");
            }
            assertEquals(2, scalar(probe, "SELECT COUNT(*) FROM Orders"), "khong duoc mat dong don hang nao");
        }
    }

    @Test
    @DisplayName("N-03: het xung dot -> fix14 go du 5 cot, ke ca cot trong UNIQUE/CHECK constraint")
    public void fix14DropsEveryLegacyColumnOnceDataIsReconciled() throws Exception {
        buildFix14Probe();

        try (Connection probe = connectTo(FIX14_PROBE_DB)) {
            exec(probe, "UPDATE Orders SET TicketCode = OrderCode WHERE TicketCode <> OrderCode");

            runScript(probe, Path.of("database/fix14_schema_drift_alignment.sql"));

            for (String[] target : legacyColumns()) {
                assertNull(columnLength(probe, target[0], target[1]),
                        target[0] + "." + target[1] + " phai da bi go");
            }
            // OrderSeats.Price nam trong UNIQUE constraint va OrderComboFoods.Price nam
            // trong CHECK constraint — ban cu loc bo unique constraint va khong xu ly
            // check constraint nen DROP COLUMN cua hai cot nay chac chan loi.
            assertEquals(0, scalar(probe,
                    "SELECT COUNT(*) FROM sys.objects WHERE name = 'UQ_OrderSeats_Price'"));
            assertEquals(0, scalar(probe,
                    "SELECT COUNT(*) FROM sys.objects WHERE name = 'CK_OrderComboFoods_Price'"));
            assertEquals(2, scalar(probe, "SELECT COUNT(*) FROM Orders"), "khong duoc mat dong don hang nao");

            // Idempotent: chay lai khong nem loi va khong doi gi.
            runScript(probe, Path.of("database/fix14_schema_drift_alignment.sql"));
            assertEquals(2, scalar(probe, "SELECT COUNT(*) FROM Orders"));
        }
    }

    // ------------------------------------------------------------------------ N-06

    /**
     * Ban cu {@code DROP} moi check constraint co {@code definition LIKE '%OrderStatus%'}
     * roi moi {@code ADD CONSTRAINT} — khong transaction, khong {@code WITH NOCHECK}. Mot
     * don mang gia tri cu ngoai danh sach lam buoc ADD loi Msg 547, va bang {@code Orders}
     * mat han rang buoc trang thai ma khong ai biet.
     */
    @Test
    @DisplayName("N-06: don co OrderStatus la 'paid' -> fix22 dung, Orders KHONG mat rang buoc")
    public void fix22KeepsStatusConstraintWhenLegacyValueWouldBreakIt() throws Exception {
        buildFix22Probe();

        try (Connection probe = connectTo(FIX22_PROBE_DB)) {
            assertEquals(1, scalar(probe, statusConstraintCountSql()),
                    "tien de: bang phai dang co dung mot rang buoc trang thai");

            SQLException failure = assertThrows(SQLException.class,
                    () -> runScript(probe, Path.of("database/fix22_orders_status_constraint.sql")),
                    "con gia tri trang thai ngoai danh sach thi fix22 phai dung lai");
            assertEquals(51418, failure.getErrorCode(),
                    "phai la loi cong chan cua fix22: " + failure.getMessage());

            assertEquals(1, scalar(probe, statusConstraintCountSql()),
                    "Orders phai VAN con rang buoc trang thai — day la loi N-06");
            assertEquals(1, scalar(probe, "SELECT COUNT(*) FROM Orders WHERE OrderStatus = 'paid'"),
                    "khong duoc sua du lieu don hang");
        }
    }

    @Test
    @DisplayName("N-06: het gia tri la -> fix22 thay rang buoc thanh cong va idempotent")
    public void fix22ReplacesConstraintOnceLegacyValuesAreReconciled() throws Exception {
        buildFix22Probe();

        try (Connection probe = connectTo(FIX22_PROBE_DB)) {
            exec(probe, "UPDATE Orders SET OrderStatus = 'confirmed' WHERE OrderStatus = 'paid'");

            runScript(probe, Path.of("database/fix22_orders_status_constraint.sql"));

            assertEquals(1, scalar(probe, statusConstraintCountSql()),
                    "phai co dung mot rang buoc trang thai sau khi chay");
            assertTrue(scalar(probe, "SELECT COUNT(*) FROM sys.check_constraints "
                    + "WHERE parent_object_id = OBJECT_ID('Orders') AND name = 'CK_Orders_OrderStatus'") == 1,
                    "rang buoc moi phai mang dung ten CK_Orders_OrderStatus");

            // Rang buoc phai thuc su chan duoc gia tri la.
            SQLException rejected = assertThrows(SQLException.class,
                    () -> exec(probe, "INSERT INTO Orders (OrderStatus) VALUES ('paid')"));
            assertTrue(rejected.getMessage().contains("CK_Orders_OrderStatus"), rejected.getMessage());

            runScript(probe, Path.of("database/fix22_orders_status_constraint.sql"));
            assertEquals(1, scalar(probe, statusConstraintCountSql()), "chay lai khong duoc nhan doi rang buoc");
        }
    }

    private static String statusConstraintCountSql() {
        return "SELECT COUNT(*) FROM sys.check_constraints "
                + "WHERE parent_object_id = OBJECT_ID('Orders') AND definition LIKE '%OrderStatus%'";
    }

    // ------------------------------------------------------------------- fixtures

    private static String[][] legacyColumns() {
        return new String[][] {
                {"Orders", "OrderCode"},
                {"Orders", "FinalPrice"},
                {"Orders", "OriginalPrice"},
                {"OrderSeats", "Price"},
                {"OrderComboFoods", "Price"},
        };
    }

    private static void buildFix14Probe() throws Exception {
        try (Connection master = connectTo("master")) {
            String setup = Files.readString(Path.of("scripts/fix14-probe-setup.sql"),
                    StandardCharsets.UTF_8).replace("CineBookDB_Fix14Probe", FIX14_PROBE_DB);
            runScript(master, setup);
        }
    }

    /**
     * DB rac toi thieu cho N-06: mot bang {@code Orders} co san rang buoc trang thai kieu
     * cu va mot dong mang gia tri {@code 'paid'} nam ngoai danh sach ma fix22 muon ap.
     */
    private static void buildFix22Probe() throws SQLException {
        try (Connection master = connectTo("master")) {
            dropProbe(FIX22_PROBE_DB);
            exec(master, "CREATE DATABASE " + FIX22_PROBE_DB);
        }
        try (Connection probe = connectTo(FIX22_PROBE_DB)) {
            exec(probe, "CREATE TABLE Orders (Id INT IDENTITY PRIMARY KEY, "
                    + "OrderStatus NVARCHAR(20) NOT NULL DEFAULT 'pending', "
                    + "CONSTRAINT CK_Orders_Status_Legacy CHECK (OrderStatus IN "
                    + "('pending','confirmed','cancelled','redeemed','paid')))");
            exec(probe, "INSERT INTO Orders (OrderStatus) VALUES ('paid')");
        }
    }

    private static void dropProbe(String databaseName) throws SQLException {
        if (!databaseName.matches(
                "^CineBookIT_[0-9]{14}_[0-9a-fA-F]{8}_(fix14|fix22)$")) {
            throw new SQLException("Refusing unsafe migration probe cleanup: " + databaseName);
        }
        try (Connection master = connectTo("master")) {
            exec(master, "IF DB_ID('" + databaseName + "') IS NOT NULL BEGIN "
                    + "ALTER DATABASE " + databaseName + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE; "
                    + "DROP DATABASE " + databaseName + "; END");
        }
    }

    private static Connection connectTo(String databaseName) throws SQLException {
        return DriverManager.getConnection(
                masterUrl.replaceFirst("databaseName=[^;]+", "databaseName=" + databaseName), user, password);
    }

    /**
     * Chay mot script .sql theo dung cach sqlcmd lam: cat theo dong {@code GO} roi gui
     * tung batch mot. Cat sai cho nay la mat y nghia cua test — {@code GO} chinh la thu
     * pha vo tinh nguyen tu cua ban fix14 cu.
     */
    private static void runScript(Connection connection, Path script) throws IOException, SQLException {
        runScript(connection, Files.readString(script, StandardCharsets.UTF_8));
    }

    private static void runScript(Connection connection, String script) throws SQLException {
        for (String batch : splitOnGo(script)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(batch);
                while (statement.getMoreResults() || statement.getUpdateCount() != -1) {
                    // Vet het ket qua de loi o batch sau khong bi che boi ket qua con treo.
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
        try (InputStream in = MigrationSafetyIT.class.getClassLoader()
                .getResourceAsStream(System.getProperty("cinebook.it.config", "target/db.it.properties"))) {
            assertNotNull(in, "khong tim thay db.test.properties");
            props.load(in);
        }
        return props;
    }

    private static Integer columnLength(Connection connection, String table, String column)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COL_LENGTH('" + table + "', '" + column + "')")) {
            rs.next();
            int value = rs.getInt(1);
            return rs.wasNull() ? null : value;
        }
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
