package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Hop dong giua ma nguon va schema CSDL (muc 11 cua ban quet).
 *
 * <p><b>Vi sao co lop test nay.</b> Truoc do bo test bao 204/204 xanh trong khi hai trang
 * quan tri deu tra 500 tren production:</p>
 * <ul>
 *   <li>{@code ComboFoods.Image} khong ton tai tren DB production (DB-01) — trang Combo chet;</li>
 *   <li>{@code OrderSeats.Id} khong ton tai tren production (RP-01) — trang Bao cao chet.</li>
 * </ul>
 * <p>Test khong bat duoc vi chung chi chay tren {@code CineBookDB_Test}, ma DB do lai duoc dung
 * tu {@code schema.sql} nen <i>co du</i> cac cot con thieu. Noi cach khac: bo test dang do mot
 * schema khac voi schema that.</p>
 *
 * <p><b>Lop test lam gi.</b> Liet ke tuong minh moi cot ma ma nguon truy van, roi doi chieu voi
 * metadata that cua CSDL dang ket noi. Chay tren DB nao thi kiem DB do, nen chay tren
 * production se phat hien drift ngay. Cung liet ke cac cot ma code <b>khong duoc phep</b> gia
 * dinh la co.</p>
 *
 * <p>Khi them cot moi vao mot truy van, phai bo sung vao bang duoi day. Do la co y: no bien
 * "quen cap nhat mapper sau migration" — cai bay da lam hong tinh nang hai lan theo CLAUDE.md —
 * thanh mot test do.</p>
 */
@Tag("it")
public class SchemaContractIT {

    /**
     * Mac dinh chay tren DB test, nhung ton trong cau hinh do nguoi goi chi dinh.
     *
     * <p>Nho vay chinh lop test nay dung duoc nhu mot buoc kiem tra van hanh trước khi deploy:</p>
     * <pre>mvn -B test -Dtest=SchemaContractIT -Dcinebook.db.config=db.properties</pre>
     * <p>Day la diem mau chot: drift chi lo ra khi do dung CSDL that. Neu test bi ghim cung vao
     * {@code CineBookDB_Test} thi no se lap lai dung sai lam cu — bao xanh trong khi production hong.</p>
     */
    private static void useConfiguredDatabase() {
        if (System.getProperty("cinebook.db.config") == null) {
            System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        }
    }

    static {
        useConfiguredDatabase();
    }

    @BeforeAll
    public static void setUpTestDb() {
        useConfiguredDatabase();
        DBConnection.shutdown();
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.shutdown();
    }

    /**
     * Cot bat buoc phai ton tai, theo bang.
     *
     * <p>Danh sach nay duoc rut ra tu chinh cac cau SQL trong {@code AdminService},
     * {@code Jdbc*DAO} va {@code BookingService}.</p>
     */
    private static Map<String, Set<String>> requiredColumns() {
        Map<String, Set<String>> required = new LinkedHashMap<>();
        required.put("Films", cols(
                "Id", "Title", "OtherTitles", "Actors", "Directors", "Rating",
                "ReleaseDate", "EndDate", "DurationMinutes", "AgeRating", "TrailerUrl",
                "Thumbnail", "Language", "Subtitles", "Description", "Country", "Format",
                "Status", "Banner", "CreatedAt", "UpdatedAt"));
        required.put("ComboFoods", cols(
                "Id", "Name", "Image", "Price", "Description", "Status", "CinemaId"));
        required.put("OrderComboFoods", cols(
                "OrderId", "ComboFoodId", "Quantity", "UnitPrice"));
        required.put("OrderSeats", cols(
                "OrderId", "ShowtimeSeatId", "SeatKey", "SeatType", "UnitPrice"));
        required.put("CinemaFilms", cols("CinemaId", "FilmId"));
        required.put("Showtimes", cols(
                "Id", "FilmId", "CinemaId", "RoomId", "StartTime", "EndTime", "BasePrice",
                "Format", "Version", "Language"));
        required.put("Orders", cols(
                "Id", "UserId", "ShowtimeId", "PromotionId", "SeatSubtotal", "ComboSubtotal",
                "DiscountAmount", "TotalAmount", "TicketCode", "TicketQrUrl", "PaymentMethod",
                "PaymentStatus", "TransactionId", "PayRedirectUrl", "OrderStatus",
                "PaymentProvider", "IdempotencyKey", "CounterExpiresAt", "RefundedAt", "CreatedAt"));
        required.put("AdminNotifications", cols(
                "Id", "Title", "Message", "Category", "Severity", "TargetType", "TargetId",
                "ActionUrl", "IsRead", "CreatedAt"));
        required.put("Users", cols(
                "Id", "Email", "FullName", "Role", "CinemaId", "IsLocked",
                "EmailVerifiedAt", "EmailVerifyTokenHash", "EmailVerifySentAt"));
        required.put("Rooms", cols("Id", "CinemaId", "Name", "Status"));
        required.put("Cinemas", cols("Id", "Name", "Status"));
        return required;
    }

    /**
     * Cot ma ma nguon <b>khong duoc</b> gia dinh la co — va tu N-05 tro di la cot khong
     * duoc phep TON TAI.
     *
     * <p>{@code OrderSeats}/{@code OrderComboFoods} dung khoa kep chu khong co cot dinh danh
     * rieng. Truy van dem so ghe vi vay phai dung {@code COUNT(*)} hoac {@code COUNT_BIG(*)},
     * khong duoc dung {@code COUNT(os.Id)} — chinh la loi RP-01.</p>
     */
    private static Map<String, Set<String>> forbiddenColumnAssumptions() {
        Map<String, Set<String>> forbidden = new LinkedHashMap<>();
        forbidden.put("OrderSeats", cols("Id"));
        forbidden.put("OrderComboFoods", cols("Id"));
        return forbidden;
    }

    /**
     * Khoa chinh bat buoc, theo bang — hai bang nay phai la khoa kep.
     *
     * <p>Khoa kep khong chi la chuyen hinh thuc: no chan mot ghe duoc dua vao cung mot don
     * hai lan. Cot {@code Id IDENTITY} khong chan duoc dieu do.</p>
     */
    private static Map<String, List<String>> requiredPrimaryKeys() {
        Map<String, List<String>> keys = new LinkedHashMap<>();
        keys.put("OrderSeats", List.of("orderid", "showtimeseatid"));
        keys.put("OrderComboFoods", List.of("orderid", "combofoodid"));
        return keys;
    }

    private static Set<String> cols(String... names) {
        return new LinkedHashSet<>(List.of(names));
    }

    @Test
    @DisplayName("Moi cot ma ma nguon truy van deu ton tai trong CSDL")
    public void requiredColumnsExist() throws SQLException {
        List<String> missing = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection()) {
            for (Map.Entry<String, Set<String>> entry : requiredColumns().entrySet()) {
                String table = entry.getKey();
                Set<String> actual = actualColumns(connection, table);
                if (actual.isEmpty()) {
                    missing.add("BANG THIEU HAN: " + table);
                    continue;
                }
                for (String column : entry.getValue()) {
                    if (!actual.contains(column.toLowerCase(Locale.ROOT))) {
                        missing.add(table + "." + column);
                    }
                }
            }
        }
        if (!missing.isEmpty()) {
            fail("Schema thieu " + missing.size() + " cot ma ma nguon dang truy van.\n"
                    + "Chay lai migration trong database/ (theo thu tu ten file) roi thu lai.\n"
                    + "Thieu: " + String.join(", ", missing));
        }
    }

    /**
     * Cot bi cam phai thuc su KHONG ton tai.
     *
     * <p><b>Loi da sua (N-05).</b> Dong khang dinh cua test nay truoc day la
     * {@code assertTrue(present || !present, ...)} — mot tautology khong bao gio fail duoc.
     * Day chinh la "hop dong schema" duoc cho la bao ve khoi RP-01, va no la mot test rong.
     * Ly do bien ho luc do ("DB test co the co, production thi khong") chinh la van de: hai
     * script tao ra hai hinh dang khac nhau. Nay {@code schema.sql} va
     * {@code fix00_create_missing_tables_cinebookdb.sql} deu khai khoa kep, nen test co
     * quyen khang dinh that: cot {@code Id} khong duoc ton tai o bat ky DB nao.</p>
     */
    @Test
    @DisplayName("OrderSeats/OrderComboFoods KHONG duoc co cot Id — hai bang dung khoa kep")
    public void forbiddenAssumptionsAreNotRelied() throws SQLException {
        List<String> unexpected = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection()) {
            for (Map.Entry<String, Set<String>> entry : forbiddenColumnAssumptions().entrySet()) {
                String table = entry.getKey();
                Set<String> actual = actualColumns(connection, table);
                for (String column : entry.getValue()) {
                    if (actual.contains(column.toLowerCase(Locale.ROOT))) {
                        unexpected.add(table + "." + column);
                    }
                }
            }
            if (!unexpected.isEmpty()) {
                fail("Schema con cot bi cam: " + String.join(", ", unexpected)
                        + ".\nHai bang nay phai dung khoa kep giong production; co cot Id nghia la DB "
                        + "nay duoc dung tu mot ban schema.sql cu. Dung lai DB bang scripts\\init-test-db.ps1.");
            }

            // Cac cau dem that su duoc dung trong AdminService.topFilms() va getReportSummary().
            // Neu ai do doi lai thanh COUNT(os.Id), cau duoi van chay tren DB test nhung se
            // hong tren production — nen o day kiem chinh cau SQL khong tham chieu cot Id.
            assertQueryRuns(connection,
                    "SELECT COUNT_BIG(*) FROM OrderSeats os WHERE os.OrderId = 0");
            assertQueryRuns(connection,
                    "SELECT COUNT(*) FROM OrderSeats os JOIN Orders o ON o.Id = os.OrderId "
                    + "WHERE o.PaymentStatus = 'paid'");
        }
    }

    /**
     * Khoa chinh phai la khoa kep — day la nua con lai cua "cung mot hinh dang bang".
     *
     * <p>Chi khang dinh "khong co cot Id" thi van chua du: mot bang co the bo cot Id ma van
     * khong co khoa chinh nao, va luc do khong gi chan mot ghe vao cung mot don hai lan.</p>
     */
    @Test
    @DisplayName("Khoa chinh cua OrderSeats/OrderComboFoods la khoa kep dung cap cot")
    public void orderChildTablesUseCompositePrimaryKeys() throws SQLException {
        List<String> problems = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection()) {
            for (Map.Entry<String, List<String>> entry : requiredPrimaryKeys().entrySet()) {
                List<String> actual = primaryKeyColumns(connection, entry.getKey());
                if (!actual.equals(entry.getValue())) {
                    problems.add(entry.getKey() + ": mong doi " + entry.getValue() + " nhung dang la " + actual);
                }
            }
        }
        if (!problems.isEmpty()) {
            fail("Khoa chinh khong dung hop dong schema:\n" + String.join("\n", problems));
        }
    }

    /** Cot khoa chinh theo dung thu tu, viet thuong; rong neu bang khong co khoa chinh. */
    private List<String> primaryKeyColumns(Connection connection, String table) throws SQLException {
        Map<Short, String> ordered = new java.util.TreeMap<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getPrimaryKeys(null, null, table)) {
            while (rs.next()) {
                ordered.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(ordered.values());
    }

    @Test
    @DisplayName("Truy van cua trang Combo va trang Bao cao chay duoc tren schema hien tai")
    public void adminQueriesCompileAgainstSchema() throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            // DB-01: dung cau cua AdminService.listCombos().
            assertQueryRuns(connection, """
                    SELECT c.Id, c.Name, c.Image, c.Price, c.Description, c.Status, c.CinemaId,
                           cin.Name AS CinemaName, ISNULL(SUM(ocf.Quantity), 0) AS SoldQuantity
                    FROM ComboFoods c
                    LEFT JOIN Cinemas cin ON cin.Id = c.CinemaId
                    LEFT JOIN OrderComboFoods ocf ON ocf.ComboFoodId = c.Id
                    GROUP BY c.Id, c.Name, c.Image, c.Price, c.Description, c.Status, c.CinemaId, cin.Name
                    """);

            // RP-01: dung cau cua AdminService.topFilms().
            assertQueryRuns(connection, """
                    SELECT TOP (10) f.Id AS FilmId, f.Title,
                           SUM(seatAgg.SeatCount) AS SoldSeats,
                           SUM(o.TotalAmount) AS TotalRevenue
                    FROM Orders o
                    JOIN Showtimes s ON s.Id = o.ShowtimeId
                    JOIN Films f ON f.Id = s.FilmId
                    OUTER APPLY (
                        SELECT COUNT_BIG(*) AS SeatCount FROM OrderSeats os WHERE os.OrderId = o.Id
                    ) seatAgg
                    WHERE o.PaymentStatus = 'paid' AND o.OrderStatus <> 'cancelled' AND o.RefundedAt IS NULL
                    GROUP BY f.Id, f.Title
                    ORDER BY SoldSeats DESC, TotalRevenue DESC
                    """);

            // EX-01: vong doi phim doc EndDate.
            assertQueryRuns(connection,
                    "SELECT TOP (1) Id, Title, ReleaseDate, EndDate, Status FROM Films");
        }
    }

    /**
     * Doanh thu khong duoc nhan len theo so ghe.
     *
     * <p>Ban cu {@code LEFT JOIN OrderSeats} lam moi don xuat hien mot lan cho MOI ghe, nen
     * {@code SUM(TotalAmount)} bi cong lap. Kiem bang cach doi chieu tong doanh thu tinh theo
     * hai cach doc lap.</p>
     */
    @Test
    @DisplayName("Doanh thu top phim khong bi nhan len theo so ghe cua don")
    public void topFilmRevenueIsNotMultipliedBySeatCount() throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            java.math.BigDecimal viaReportQuery = scalarDecimal(connection, """
                    SELECT ISNULL(SUM(x.TotalRevenue), 0) FROM (
                        SELECT f.Id, SUM(o.TotalAmount) AS TotalRevenue
                        FROM Orders o
                        JOIN Showtimes s ON s.Id = o.ShowtimeId
                        JOIN Films f ON f.Id = s.FilmId
                        OUTER APPLY (SELECT COUNT_BIG(*) AS SeatCount FROM OrderSeats os WHERE os.OrderId = o.Id) seatAgg
                        WHERE o.PaymentStatus = 'paid' AND o.OrderStatus <> 'cancelled' AND o.RefundedAt IS NULL
                        GROUP BY f.Id
                    ) x
                    """);

            // Doi chieu doc lap: cong thang tren Orders, khong join sang OrderSeats.
            java.math.BigDecimal viaOrdersOnly = scalarDecimal(connection, """
                    SELECT ISNULL(SUM(o.TotalAmount), 0)
                    FROM Orders o JOIN Showtimes s ON s.Id = o.ShowtimeId
                    WHERE o.PaymentStatus = 'paid' AND o.OrderStatus <> 'cancelled' AND o.RefundedAt IS NULL
                    """);

            assertTrue(viaReportQuery.compareTo(viaOrdersOnly) == 0,
                    "Doanh thu top phim (" + viaReportQuery + ") phai khop voi tong tren Orders ("
                    + viaOrdersOnly + "). Lech nghia la truy van dang nhan ban dong theo so ghe.");
        }
    }

    private java.math.BigDecimal scalarDecimal(Connection connection, String sql) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            java.math.BigDecimal value = rs.getBigDecimal(1);
            return value == null ? java.math.BigDecimal.ZERO : value;
        }
    }

    private void assertQueryRuns(Connection connection, String sql) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeQuery().close();
        } catch (SQLException ex) {
            fail("Truy van khong chay duoc tren schema hien tai: " + ex.getMessage()
                    + "\nSQL: " + sql.strip());
        }
    }

    private Set<String> actualColumns(Connection connection, String table) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getColumns(null, null, table, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }
}
