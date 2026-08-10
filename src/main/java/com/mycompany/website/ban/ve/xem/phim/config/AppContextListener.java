package com.mycompany.website.ban.ve.xem.phim.config;

import com.mycompany.website.ban.ve.xem.phim.service.HoldSweeper;
import com.mycompany.website.ban.ve.xem.phim.service.EmailService;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class AppContextListener implements ServletContextListener {
    private static final Logger LOG = Logger.getLogger(AppContextListener.class.getName());

    /** Chu ky quet rac giu ghe, chot theo dac ta P04. */
    private static final int SWEEP_PERIOD_SECONDS = 30;

    /** Cho ung dung khoi dong xong roi hay quet vong dau. */
    private static final int SWEEP_INITIAL_DELAY_SECONDS = 20;

    private ScheduledExecutorService sweeperExecutor;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        verifySchemaFailFast();
        startHoldSweeper();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        stopHoldSweeper();
        EmailService.shutdown();
        DBConnection.shutdown();
    }

    /**
     * Bat lich don rac nen (B5). Dung {@code scheduleWithFixedDelay} chu khong phai
     * {@code scheduleAtFixedRate}: neu mot vong chay lau hon 30 giay thi cac vong sau khong
     * duoc phep don lai thanh chum.
     *
     * <p>Cong tat nam trong {@code SystemSettings.sweeper.enabled} va duoc doc lai <b>moi vong</b>,
     * nen tat/bat khong can restart Tomcat.</p>
     */
    private void startHoldSweeper() {
        HoldSweeper sweeper = new HoldSweeper();
        sweeperExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cinebook-hold-sweeper");
            // Daemon de khong giu JVM song neu contextDestroyed khong kip chay.
            thread.setDaemon(true);
            return thread;
        });
        sweeperExecutor.scheduleWithFixedDelay(
                sweeper::sweepOnce,
                SWEEP_INITIAL_DELAY_SECONDS,
                SWEEP_PERIOD_SECONDS,
                TimeUnit.SECONDS);
        LOG.info("HoldSweeper: da bat lich, chay moi " + SWEEP_PERIOD_SECONDS + " giay.");
    }

    /** Dung lich sach se khi undeploy — neu khong, hot-redeploy se de lai thread mo coi. */
    private void stopHoldSweeper() {
        if (sweeperExecutor == null) {
            return;
        }
        sweeperExecutor.shutdown();
        try {
            if (!sweeperExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                sweeperExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            sweeperExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        sweeperExecutor = null;
        LOG.info("HoldSweeper: da dung lich.");
    }

    private static final String[] REQUIRED_TABLES = {
        "Users", "Films", "Cinemas", "Rooms", "Seats",
        "Showtimes", "ShowtimeSeats", "Orders", "OrderSeats",
        "Promotions", "UserVouchers", "PromotionUsage", "Invoices", "InvoiceSequence",
        "BookingCommandExecutions", "BookingStateTransitions", "BookingOutbox",
        "UserAppeals", "AdminNotifications", "PolicyDocuments",
        "ApprovalRequests", "FilmRequestDetails", "FilmRequestCategories",
        "RoomRequestDetails", "RoomRequestSeats", "CinemaContents"
    };

    private static final String[][] REQUIRED_COLUMNS = {
        {"Users", "Email"},
        {"Users", "PasswordHash"},
        {"Users", "Role"},
        {"Users", "MembershipTier"},
        {"Users", "CinemaId"},
        {"Users", "AnonymizedAt"},
        {"Films", "DeletedAt"},
        {"Films", "DeletedByUserId"},
        {"Films", "DeletionMode"},
        {"Cinemas", "CinemaType"},
        {"Rooms", "RoomType"},
        {"Orders", "UserId"},
        {"Orders", "ShowtimeId"},
        {"Orders", "OrderStatus"},
        {"Orders", "PaymentStatus"},
        {"Orders", "CounterReminderSentAt"},
        {"Orders", "IsUserHidden"},
        {"Orders", "StateVersion"},
        {"Orders", "RefundRejectedAt"},
        {"Orders", "RefundRejectReason"},
        {"ShowtimeSeats", "ShowtimeId"},
        {"ShowtimeSeats", "SeatId"},
        {"ShowtimeSeats", "Status"},
        {"ShowtimeSeats", "ClaimedByOrderId"},
        {"Showtimes", "SaleStatus"},
        {"Showtimes", "DeleteRequestedAt"},
        {"Showtimes", "DeleteNotBefore"},
        {"Showtimes", "DeleteRequestedByUserId"},
        {"Seats", "IsActive"},
        {"Seats", "LayoutVersion"},
        {"UserAppeals", "AppealType"},
        {"UserAppeals", "OrderId"},
        {"UserAppeals", "CinemaId"},
        {"AdminNotifications", "ResolvedAt"},
        {"AdminNotifications", "ResolvedBy"},
        {"AdminNotifications", "Resolution"},
        {"AdminNotifications", "CinemaId"},
        {"AdminNotifications", "CreatedByUserId"},
        {"AdminNotifications", "EventType"},
        {"AdminNotifications", "ApprovalRequestId"},
        {"CinemaFilms", "Status"},
        {"CinemaFilms", "AssignedAt"},
        {"Promotions", "CreatedByUserId"},
        {"ComboFoods", "CinemaId"},
        {"ApprovalRequests", "RequestType"},
        {"ApprovalRequests", "CinemaId"},
        {"ApprovalRequests", "Status"},
        {"ApprovalRequests", "RowVersion"},
        {"CinemaContents", "ContentJson"}
    };

    private static final String[] REQUIRED_VIEWS = {
        "vw_OrderLifecycle", "vw_ShowtimeAvailability"
    };

    private static final String[][] REQUIRED_INDEXES = {
        {"ShowtimeSeats", "IX_ShowtimeSeats_ClaimedByOrderId"},
        {"Seats", "UX_Seats_Active_Room_SeatKey"},
        {"Films", "IX_Films_AdminLifecycle"},
        {"Showtimes", "IX_Showtimes_DeletionQueue"},
        {"UserAppeals", "IX_UserAppeals_Type_Status_CreatedAt"},
        {"ApprovalRequests", "UX_ApprovalRequests_PendingKey"},
        {"CinemaContents", "IX_CinemaContents_Key_Cinema"},
        {"CinemaFilms", "IX_CinemaFilms_Cinema_Status"}
    };

    /**
     * Danh sach cot bat buoc duoi dang {@code "Bang.Cot"}.
     *
     * <p>Mo ra de test hop dong doi chieu duoc voi chuoi migration: mot cot do migration
     * them vao ma khong nam trong danh sach nay thi DB thieu cot se lo ra luc nguoi dung
     * bam nut, khong phai luc khoi dong.</p>
     */
    public static List<String> requiredColumns() {
        List<String> names = new ArrayList<>(REQUIRED_COLUMNS.length);
        for (String[] col : REQUIRED_COLUMNS) {
            names.add(col[0] + "." + col[1]);
        }
        return names;
    }

    public static void verifySchemaFailFast() {
        String[] requiredTables = REQUIRED_TABLES;
        String[][] requiredColumns = REQUIRED_COLUMNS;

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            for (String table : requiredTables) {
                String sql = "SELECT OBJECT_ID(N'" + table + "')";
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    if (!rs.next() || rs.getObject(1) == null) {
                        throw new IllegalStateException("Fail-fast schema check failed: Missing table '" + table + "'");
                    }
                }
            }

            for (String[] col : requiredColumns) {
                String table = col[0];
                String column = col[1];
                String sql = "SELECT COL_LENGTH(N'" + table + "', N'" + column + "')";
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    if (!rs.next() || rs.getObject(1) == null) {
                        throw new IllegalStateException(
                            "Fail-fast schema check failed: Missing column '" + column + "' in table '" + table + "'"
                        );
                    }
                }
            }

            for (String view : REQUIRED_VIEWS) {
                String sql = "SELECT OBJECT_ID(N'dbo." + view + "', N'V')";
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    if (!rs.next() || rs.getObject(1) == null) {
                        throw new IllegalStateException(
                                "Fail-fast schema check failed: Missing view '" + view + "'");
                    }
                }
            }

            for (String[] index : REQUIRED_INDEXES) {
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(?) AND name=?")) {
                    ps.setString(1, "dbo." + index[0]);
                    ps.setString(2, index[1]);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Fail-fast schema check failed: Missing index '"
                                    + index[1] + "' on table '" + index[0] + "'");
                        }
                    }
                }
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT SettingValue FROM dbo.SystemSettings WHERE SettingKey=N'booking.stateContractVersion'")) {
                if (!rs.next() || rs.getString(1) == null || rs.getString(1).isBlank()) {
                    throw new IllegalStateException(
                            "Fail-fast schema check failed: Missing SystemSettings.booking.stateContractVersion");
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Fail-fast schema check encountered a database error: " + ex.getMessage(), ex);
        }
    }
}
