package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.controller.admin.ManagerPortalServlet;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Cinema;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import com.mycompany.website.ban.ve.xem.phim.model.Room;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.math.BigDecimal;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Reconciles the same persisted catalog from the public/user, manager and admin
 * paths. The fixture deliberately spans two cinemas so a green result cannot be
 * produced by an empty list.
 */
@Tag("it")
@DisplayName("User - manager - admin catalog and cinema scope reconciliation")
public class ManagerAdminUserReconciliationIT {
    private static final String PREFIX = "FLOW-SCOPE-";
    private static final String PASSWORD_HASH =
            "$2a$12$Cja7B.jV5kPjNnjZfPWAR.5lZPcgJ9Z/mTRrIUTaSBxpO6iTyfoBm";
    private static final int CINEMA_A = 1;
    private static final int CINEMA_B = 2;
    private static final int ROOM_A = 1;
    private static final int ROOM_B = 2;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final AdminService adminService = new AdminService();
    private final JdbcShowtimeDAO publicShowtimeDao = new JdbcShowtimeDAO();
    private final User managerA = actor(4, "manager", CINEMA_A);
    private final User admin = actor(5, "admin", null);
    private int filmA;
    private int filmB;
    private int showtimeA;
    private int showtimeB;

    @BeforeAll
    static void configureDatabase() {
        DBConnection.shutdown();
    }

    @BeforeEach
    void createCrossCinemaFixture() throws SQLException {
        assertTestDatabase();
        cleanupFixture();
        filmA = insertFilm(PREFIX + "A");
        filmB = insertFilm(PREFIX + "B");
        execute("INSERT INTO CinemaFilms (CinemaId,FilmId) VALUES (?,?)", CINEMA_A, filmA);
        execute("INSERT INTO CinemaFilms (CinemaId,FilmId) VALUES (?,?)", CINEMA_B, filmB);
        showtimeA = insertShowtime(filmA, CINEMA_A, ROOM_A);
        showtimeB = insertShowtime(filmB, CINEMA_B, ROOM_B);
    }

    @AfterEach
    void cleanup() throws SQLException {
        cleanupFixture();
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("FLOW-SCOPE-001: manager lists only its films/cinema/rooms/showtimes")
    void managerListsOnlyItsCinemaResources() {
        List<Film> films = adminService.listFilms(managerA);
        List<Cinema> cinemas = adminService.listCinemas(managerA);
        List<Room> rooms = adminService.listRooms(managerA);
        List<Showtime> showtimes = adminService.listShowtimes(managerA);

        assertTrue(films.stream().anyMatch(film -> film.getId() == filmA));
        assertFalse(films.stream().anyMatch(film -> film.getId() == filmB));
        assertEquals(List.of(CINEMA_A), cinemas.stream().map(Cinema::getId).toList());
        assertTrue(rooms.stream().allMatch(room -> room.getCinemaId() == CINEMA_A));
        assertTrue(showtimes.stream().anyMatch(item -> item.getId() == showtimeA));
        assertFalse(showtimes.stream().anyMatch(item -> item.getId() == showtimeB));
    }

    @Test
    @DisplayName("FLOW-SCOPE-002: manager direct-object access to another cinema is 403")
    void managerCannotReadOtherCinemaObjectsByForgedId() {
        assertForbidden(() -> adminService.findFilmById(filmB, managerA));
        assertForbidden(() -> adminService.findCinemaById(CINEMA_B, managerA));
        assertForbidden(() -> adminService.findRoomById(ROOM_B, managerA));
    }

    @Test
    @DisplayName("FLOW-SCOPE-003: admin sees both cinemas and manager creates a global promotion")
    void adminIsGlobalAndManagerCannotMutateGlobalCatalog() throws SQLException {
        assertTrue(adminService.listFilms(admin).stream().anyMatch(film -> film.getId() == filmA));
        assertTrue(adminService.listFilms(admin).stream().anyMatch(film -> film.getId() == filmB));
        assertTrue(adminService.listCinemas(admin).stream().anyMatch(cinema -> cinema.getId() == CINEMA_A));
        assertTrue(adminService.listCinemas(admin).stream().anyMatch(cinema -> cinema.getId() == CINEMA_B));

        Promotion promotion = new Promotion();
        promotion.setCode(PREFIX + "PROMO");
        promotion.setStartDate(LocalDate.now());
        promotion.setEndDate(LocalDate.now().plusDays(1));
        promotion.setDiscountPercent(10d);
        promotion.setMaxDiscount(new BigDecimal("10000"));
        promotion.setStatus("active");
        adminService.savePromotion(promotion, managerA);
        assertTrue(promotion.getId() > 0);
        assertEquals(managerA.getId(),
                queryInt("SELECT CreatedByUserId FROM Promotions WHERE Id=?", promotion.getId()));
    }

    @Test
    @DisplayName("FLOW-CATALOG-001: user/public showtimes match admin cinema-film assignment")
    void publicShowtimesFollowAdminCinemaAssignment() {
        List<Showtime> publicA = publicShowtimeDao.findByFilmAndCinema(filmA, CINEMA_A);
        List<Showtime> forgedCrossCinema = publicShowtimeDao.findByFilmAndCinema(filmB, CINEMA_A);

        assertTrue(publicA.stream().anyMatch(item -> item.getId() == showtimeA),
                "public user path must expose the active future showtime created for cinema A");
        assertTrue(forgedCrossCinema.stream().noneMatch(item -> item.getId() == showtimeB),
                "a showtime assigned to cinema B must not leak under cinema A");
    }

    @Test
    @DisplayName("D.3: manager-created member is owned, visible and counted immediately")
    void managerCreatedMemberIsImmediatelyInScope() throws SQLException {
        int visibleBefore = adminService.listUsers("member", managerA).size();
        long countBefore = adminService.dashboardCounts(managerA).get("memberCount");
        User member = new User();
        member.setUsername("flow-scope-member");
        member.setFullName("Flow Scope Member");
        member.setEmail("flow-scope-member@test.local");
        member.setPasswordHash("StrongPass123!");
        member.setPhone("0900000001");

        adminService.createMember(member, managerA);

        assertEquals(CINEMA_A, member.getCinemaId());
        assertEquals(CINEMA_A, queryInt("SELECT CinemaId FROM Users WHERE Id=?", member.getId()));
        List<User> visibleAfter = adminService.listUsers("member", managerA);
        long countAfter = adminService.dashboardCounts(managerA).get("memberCount");
        assertTrue(visibleAfter.stream().anyMatch(user -> user.getId() == member.getId()));
        assertEquals(visibleBefore + 1, visibleAfter.size());
        assertEquals(countBefore + 1, countAfter);
        assertEquals(visibleAfter.size(), countAfter,
                "dashboard and member list must use the same manager visibility predicate");
    }

    @Test
    @DisplayName("D.3: manager scope is applied before the 200-user list limit")
    void managerScopeIsAppliedBeforeListLimit() throws SQLException {
        int scopedMemberId = insert("""
                INSERT INTO Users (FullName,Email,PasswordHash,Role,CinemaId,CreatedAt)
                VALUES (?,?,?,'member',?,DATEADD(DAY,-365,GETDATE()))
                """, "Flow Scope Pagination Target", "flow-scope-page-target@test.local",
                PASSWORD_HASH, CINEMA_A);
        execute("""
                WITH Numbers AS (
                  SELECT TOP (205) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS RowNumber
                  FROM sys.all_objects
                )
                INSERT INTO Users (FullName,Email,PasswordHash,Role,CinemaId,CreatedAt)
                SELECT CONCAT('Flow Scope Outside ',RowNumber),
                       CONCAT('flow-scope-page-outside-',RowNumber,'@test.local'),
                       ?,'member',?,DATEADD(MINUTE,RowNumber,GETDATE())
                FROM Numbers
                """, PASSWORD_HASH, CINEMA_B);

        List<User> managerVisible = adminService.listUsers("member", managerA);
        long scopedCount = adminService.dashboardCounts(managerA).get("memberCount");
        List<User> adminVisible = adminService.listUsers("member", admin);

        assertTrue(managerVisible.stream().anyMatch(user -> user.getId() == scopedMemberId),
                "scope must be filtered in SQL before TOP (200)");
        assertEquals(scopedCount, (long) managerVisible.size(),
                "manager dashboard count and scoped list must stay synchronized");
        assertEquals(200, adminVisible.size(), "global admin list must retain its existing cap");
        assertTrue(adminVisible.stream().allMatch(user -> Integer.valueOf(CINEMA_B).equals(user.getCinemaId())),
                "global ordering must remain unchanged for admin");
    }

    @Test
    @DisplayName("D.3: manager list and dashboard count agree above the global 200-user cap")
    void managerScopedListIsNotTruncatedAtGlobalLimit() throws SQLException {
        execute("""
                WITH Numbers AS (
                  SELECT TOP (205) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS RowNumber
                  FROM sys.all_objects
                )
                INSERT INTO Users (FullName,Email,PasswordHash,Role,CinemaId,CreatedAt)
                SELECT CONCAT('Flow Scope Owned ',RowNumber),
                       CONCAT('flow-scope-owned-',RowNumber,'@test.local'),
                       ?,'member',?,DATEADD(SECOND,RowNumber,GETDATE())
                FROM Numbers
                """, PASSWORD_HASH, CINEMA_A);

        List<User> managerVisible = adminService.listUsers("member", managerA);
        long scopedCount = adminService.dashboardCounts(managerA).get("memberCount");
        List<User> adminVisible = adminService.listUsers("member", admin);

        assertTrue(scopedCount >= 205, "fixture must cross the global admin list cap");
        assertEquals(scopedCount, (long) managerVisible.size(),
                "manager UI has no pagination, so its scoped list must match the dashboard total");
        assertEquals(200, adminVisible.size(), "admin global list retains its explicit legacy cap");
    }

    @Test
    @DisplayName("P1: manager film/showtime scope is applied before global list caps")
    void managerCatalogScopeIsAppliedBeforeGlobalListCaps() throws SQLException {
        execute("UPDATE Films SET CreatedAt=DATEADD(DAY,-365,GETDATE()) WHERE Id=?", filmA);
        execute("""
                WITH Numbers AS (
                  SELECT TOP (205) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS RowNumber
                  FROM sys.all_objects
                )
                INSERT INTO Films
                    (Title,ReleaseDate,EndDate,DurationMinutes,Status,CreatedAt,UpdatedAt)
                SELECT CONCAT(?,RowNumber),
                       DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
                       DATEADD(DAY,60,CAST(GETDATE() AS DATE)),
                       120,'showing',DATEADD(MINUTE,RowNumber,GETDATE()),GETDATE()
                FROM Numbers
                """, PREFIX + "FILM-PAGE-OUTSIDE-");
        execute("""
                INSERT INTO CinemaFilms (CinemaId,FilmId)
                SELECT ?,Id FROM Films WHERE Title LIKE ?
                """, CINEMA_B, PREFIX + "FILM-PAGE-OUTSIDE-%");

        execute("""
                UPDATE Showtimes
                SET StartTime=DATEADD(DAY,-365,GETDATE()),
                    EndTime=DATEADD(MINUTE,180,DATEADD(DAY,-365,GETDATE()))
                WHERE Id=?
                """, showtimeA);
        execute("""
                WITH Numbers AS (
                  SELECT TOP (505) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS RowNumber
                  FROM sys.all_objects
                )
                INSERT INTO Showtimes
                    (FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice,Format,Version,Language)
                SELECT ?,?,?,
                       DATEADD(MINUTE,RowNumber,DATEADD(DAY,30,GETDATE())),
                       DATEADD(MINUTE,RowNumber + 180,DATEADD(DAY,30,GETDATE())),
                       90000,'2D','Subtitle','Vietnamese'
                FROM Numbers
                """, filmB, CINEMA_B, ROOM_B);

        List<Film> managerFilms = adminService.listFilms(managerA);
        List<Showtime> managerShowtimes = adminService.listShowtimes(managerA);
        Map<String, Long> dashboard = adminService.dashboardCounts(managerA);

        assertTrue(managerFilms.stream().anyMatch(film -> film.getId() == filmA),
                "205 newer foreign films must not push the manager's own film past TOP (200)");
        assertTrue(managerShowtimes.stream().anyMatch(showtime -> showtime.getId() == showtimeA),
                "505 newer foreign showtimes must not push the manager's own showtime past TOP (500)");
        assertEquals(dashboard.get("filmCount"), (long) managerFilms.size(),
                "manager film list has no pagination and must match its scoped dashboard count");
        assertEquals(dashboard.get("showtimeCount"), (long) managerShowtimes.size(),
                "manager showtime list has no pagination and must match its scoped dashboard count");
    }

    @Test
    @DisplayName("D.3: member POST accepts only members allowed by the actor policy")
    void memberPostRejectsForgedPrivilegedAndCrossCinemaTargets() throws Exception {
        int scopedMember = insertUser("member-scoped", "member", CINEMA_A);
        int foreignMember = insertUser("member-foreign", "member", CINEMA_B);
        int sameCinemaStaff = insertUser("staff-target", "staff", CINEMA_A);
        int sameCinemaManager = insertUser("manager-target", "manager", CINEMA_A);
        int sameCinemaAdmin = insertUser("admin-target", "admin", CINEMA_A);
        int managerSelfId = insertUser("manager-self", "manager", CINEMA_A);
        User managerSelf = actor(managerSelfId, "manager", CINEMA_A);

        assertEquals(302, postUser(managerA, scopedMember, "lock", Map.of("lockReason", "D3 test")).status());
        assertEquals(1, queryInt("SELECT CAST(IsLocked AS INT) FROM Users WHERE Id=?", scopedMember));
        assertEquals(302, postUser(managerA, scopedMember, "unlock", Map.of()).status());
        assertEquals(0, queryInt("SELECT CAST(IsLocked AS INT) FROM Users WHERE Id=?", scopedMember));
        assertEquals(302, postUser(managerA, scopedMember, "changeTier",
                Map.of("membershipTier", "EMERALD")).status());
        assertEquals("EMERALD", queryString("SELECT MembershipTier FROM Users WHERE Id=?", scopedMember));

        assertMemberPostForbidden(managerA, foreignMember);
        assertMemberPostForbidden(managerA, sameCinemaStaff);
        assertMemberPostForbidden(managerA, sameCinemaManager);
        assertMemberPostForbidden(managerA, sameCinemaAdmin);
        assertMemberPostForbidden(managerSelf, managerSelfId);

        assertEquals(302, postUser(admin, foreignMember, "lock", Map.of("lockReason", "D3 admin")).status());
        assertEquals(302, postUser(admin, foreignMember, "unlock", Map.of()).status());
        assertEquals(302, postUser(admin, foreignMember, "changeTier",
                Map.of("membershipTier", "DIAMOND")).status());
        assertMemberPostForbidden(admin, sameCinemaStaff);
        assertMemberPostForbidden(admin, sameCinemaManager);
        assertMemberPostForbidden(admin, sameCinemaAdmin);
        int adminSelfId = insertUser("admin-self", "admin", CINEMA_A);
        assertMemberPostForbidden(actor(adminSelfId, "admin", null), adminSelfId);
    }

    @Test
    @DisplayName("P1: staff lock/unlock POST derives and enforces the target role")
    void staffPostRejectsForgedNonStaffTargetsWithoutMutation() throws Exception {
        int ownStaff = insertUser("staff-owned", "staff", CINEMA_A);
        int foreignStaff = insertUser("staff-foreign", "staff", CINEMA_B);
        int sameCinemaMember = insertUser("staff-route-member", "member", CINEMA_A);
        int sameCinemaManager = insertUser("staff-route-manager", "manager", CINEMA_A);
        int sameCinemaAdmin = insertUser("staff-route-admin", "admin", CINEMA_A);

        execute("""
                INSERT INTO Orders
                    (UserId,ShowtimeId,SeatSubtotal,ComboSubtotal,DiscountAmount,TotalAmount,
                     TicketCode,PaymentMethod,PaymentStatus,OrderStatus)
                VALUES (?,?,100000,0,0,100000,?,'card','paid','confirmed')
                """, foreignStaff, showtimeA, PREFIX + "STAFF-FOREIGN-ORDER");
        List<User> visibleStaff = adminService.listUsers(AppConstants.ROLE_STAFF, managerA);
        assertTrue(visibleStaff.stream().anyMatch(user -> user.getId() == ownStaff));
        assertFalse(visibleStaff.stream().anyMatch(user -> user.getId() == foreignStaff),
                "A personal order at cinema A must not transfer staff ownership from cinema B");
        assertForbidden(() -> adminService.deleteStaff(foreignStaff, managerA));

        assertEquals(302, postStaff(managerA, ownStaff, "lock", Map.of("lockReason", "P1 staff")).status());
        assertEquals(1, queryInt("SELECT CAST(IsLocked AS INT) FROM Users WHERE Id=?", ownStaff));
        assertEquals(302, postStaff(managerA, ownStaff, "unlock", Map.of()).status());
        assertEquals(0, queryInt("SELECT CAST(IsLocked AS INT) FROM Users WHERE Id=?", ownStaff));

        assertStaffPostForbidden(managerA, foreignStaff);
        assertStaffPostForbidden(managerA, sameCinemaMember);
        assertStaffPostForbidden(managerA, sameCinemaManager);
        assertStaffPostForbidden(managerA, sameCinemaAdmin);

        assertEquals(302, postStaff(admin, foreignStaff, "lock", Map.of("lockReason", "P1 admin")).status());
        assertEquals(302, postStaff(admin, foreignStaff, "unlock", Map.of()).status());
        assertStaffPostForbidden(admin, sameCinemaMember);
        assertStaffPostForbidden(admin, sameCinemaManager);
        assertStaffPostForbidden(admin, sameCinemaAdmin);
    }

    private void assertForbidden(Runnable call) {
        BookingException exception = assertThrows(BookingException.class, call::run);
        assertEquals(403, exception.getStatusCode());
    }

    private int insertFilm(String title) throws SQLException {
        return insert("""
                INSERT INTO Films (Title,ReleaseDate,EndDate,DurationMinutes,Status)
                VALUES (?,DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
                        DATEADD(DAY,30,CAST(GETDATE() AS DATE)),120,'showing')
                """, title);
    }

    private int insertShowtime(int filmId, int cinemaId, int roomId) throws SQLException {
        return insert("""
                INSERT INTO Showtimes
                    (FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice,Format,Version,Language)
                VALUES (?,?,?,DATEADD(DAY,7,GETDATE()),DATEADD(MINUTE,180,DATEADD(DAY,7,GETDATE())),
                        90000,'2D','Subtitle','Vietnamese')
                """, filmId, cinemaId, roomId);
    }

    private int insertUser(String suffix, String role, int cinemaId) throws SQLException {
        return insert("""
                INSERT INTO Users (FullName,Email,PasswordHash,Role,CinemaId)
                VALUES (?,?,?,?,?)
                """, "Flow Scope " + suffix, "flow-scope-" + suffix + "@test.local",
                PASSWORD_HASH, role, cinemaId);
    }

    private void assertMemberPostForbidden(User actor, int targetId) throws Exception {
        assertEquals(403, postUser(actor, targetId, "lock", Map.of("lockReason", "forged")).status());
        assertEquals(403, postUser(actor, targetId, "unlock", Map.of()).status());
        assertEquals(403, postUser(actor, targetId, "changeTier",
                Map.of("membershipTier", "SILVER")).status());
    }

    private void assertStaffPostForbidden(User actor, int targetId) throws Exception {
        execute("UPDATE Users SET IsLocked=0,LockReason=NULL WHERE Id=?", targetId);
        assertEquals(403, postStaff(actor, targetId, "lock", Map.of("lockReason", "forged")).status());
        assertEquals(0, queryInt("SELECT CAST(IsLocked AS INT) FROM Users WHERE Id=?", targetId));

        execute("UPDATE Users SET IsLocked=1,LockReason='fixture' WHERE Id=?", targetId);
        assertEquals(403, postStaff(actor, targetId, "unlock", Map.of()).status());
        assertEquals(1, queryInt("SELECT CAST(IsLocked AS INT) FROM Users WHERE Id=?", targetId));
    }

    private PostResult postUser(User actor, int targetId, String action, Map<String, String> extras)
            throws Exception {
        return postPortal(actor, targetId, action, extras, "/admin/users");
    }

    private PostResult postStaff(User actor, int targetId, String action, Map<String, String> extras)
            throws Exception {
        return postPortal(actor, targetId, action, extras, "/admin/staff");
    }

    private PostResult postPortal(User actor, int targetId, String action, Map<String, String> extras,
            String servletPath) throws Exception {
        Map<String, String> parameters = new HashMap<>(extras);
        parameters.put("action", action);
        parameters.put("id", String.valueOf(targetId));
        PostExchange exchange = new PostExchange(actor, parameters, servletPath);
        new ManagerPortalServlet().service(exchange.request(), exchange.response());
        return new PostResult(exchange.status, exchange.redirectedTo);
    }

    private void cleanupFixture() throws SQLException {
        assertTestDatabase();
        execute("""
                DELETE FROM Orders WHERE TicketCode LIKE ?;
                DELETE FROM ShowtimeSeats WHERE ShowtimeId IN (
                  SELECT s.Id FROM Showtimes s JOIN Films f ON f.Id=s.FilmId WHERE f.Title LIKE ?
                );
                DELETE FROM Showtimes WHERE FilmId IN (SELECT Id FROM Films WHERE Title LIKE ?);
                DELETE FROM CinemaFilms WHERE FilmId IN (SELECT Id FROM Films WHERE Title LIKE ?);
                DELETE FROM Films WHERE Title LIKE ?;
                DELETE FROM Promotions WHERE Code=?;
                DELETE FROM AuditLogs WHERE TargetType='User' AND TRY_CONVERT(INT,TargetId) IN (
                  SELECT Id FROM Users WHERE Email LIKE ?
                );
                DELETE FROM Users WHERE Email LIKE ?;
                """, PREFIX + "%", PREFIX + "%", PREFIX + "%", PREFIX + "%", PREFIX + "%", PREFIX + "PROMO",
                "flow-scope-%", "flow-scope-%");
    }

    private int queryInt(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private String queryString(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private int insert(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            bind(statement, values);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
        }
    }

    private void assertTestDatabase() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT DB_NAME()");
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals(System.getProperty("cinebook.it.database", "CineBookIT_REQUIRED"), result.getString(1));
        }
    }

    private static User actor(int id, String role, Integer cinemaId) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setCinemaId(cinemaId);
        return user;
    }

    private record PostResult(int status, String redirect) {
    }

    private static final class PostExchange {
        private final Map<String, Object> sessionAttributes = new HashMap<>();
        private final Map<String, String> parameters;
        private final String servletPath;
        private int status = 200;
        private String redirectedTo;

        private PostExchange(User actor, Map<String, String> parameters, String servletPath) {
            this.parameters = parameters;
            this.servletPath = servletPath;
            sessionAttributes.put(AppConstants.SESSION_USER, actor);
        }

        private HttpServletRequest request() {
            HttpSession session = session();
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "getMethod" -> "POST";
                case "getServletPath" -> servletPath;
                case "getContextPath" -> "/Website-ban-ve-xem-phim";
                case "getSession" -> session;
                case "getParameter" -> parameters.get((String) args[0]);
                case "getHeader" -> null;
                default -> defaultValue(method.getReturnType());
            };
            return (HttpServletRequest) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {HttpServletRequest.class}, handler);
        }

        private HttpSession session() {
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "getAttribute" -> sessionAttributes.get((String) args[0]);
                case "setAttribute" -> {
                    sessionAttributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "removeAttribute" -> {
                    sessionAttributes.remove((String) args[0]);
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            };
            return (HttpSession) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {HttpSession.class}, handler);
        }

        private HttpServletResponse response() {
            InvocationHandler handler = (proxy, method, args) -> {
                if ("sendError".equals(method.getName()) || "setStatus".equals(method.getName())) {
                    status = (int) args[0];
                    return null;
                }
                if ("sendRedirect".equals(method.getName())) {
                    status = 302;
                    redirectedTo = (String) args[0];
                    return null;
                }
                return defaultValue(method.getReturnType());
            };
            return (HttpServletResponse) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {HttpServletResponse.class}, handler);
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive() || type == void.class) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == long.class) {
                return 0L;
            }
            return 0;
        }
    }
}
