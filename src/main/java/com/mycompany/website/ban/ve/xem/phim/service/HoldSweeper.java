package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Don rac nen cho luong dat ve (B5).
 *
 * <p>
 * Truoc phase nay he thong chi don rac khi co nguoi <b>tinh co</b> mo so do
 * ghe:
 * {@code getSeatMap} goi {@code releaseExpiredHolds} cho dung suat chieu do.
 * Suat chieu vang
 * khach thi ghe treo mai, va don nhap ({@code pending}) khong bao gio bi don -
 * chung nam lai
 * trong {@code Orders} vinh vien.
 * </p>
 *
 * <h2>Ba viec moi vong</h2>
 * <ol>
 * <li><b>Ghe giu qua han</b> tren toan he thong tro ve {@code available}.</li>
 * <li><b>Don tai quay qua han</b> ({@code CounterExpiresAt} da troi qua) bi huy
 * va <b>tra ghe</b>.</li>
 * <li><b>Don nhap mo coi</b> ({@code pending}/{@code pending} qua lau) bi huy
 * va tra not ghe con treo.</li>
 * </ol>
 *
 * <h2>Vi sao an toan khi chay hai ban song song</h2>
 * <p>
 * Moi buoc deu <b>idempotent</b>: dieu kien loc chinh la trang thai can doi,
 * nen chay lai
 * khong doi them gi. Hai buoc huy don "gianh" don bang
 * {@code WITH (UPDLOCK, ROWLOCK, READPAST)} trong mot transaction - ban chay
 * sau <em>bo qua</em>
 * cac dong ban chay truoc dang giu, khong cho khoa va khong huy trung.
 * </p>
 *
 * <p>
 * Moi moc thoi gian so bang {@code GETDATE()} cua SQL Server, khong dung gio
 * may chu ung dung
 * (quy tac chot o P03). Ca vong chay tren <b>dung mot connection</b> muon tu
 * pool.
 * </p>
 */
public class HoldSweeper {
    private static final Logger LOG = Logger.getLogger(HoldSweeper.class.getName());

    /**
     * Ly do huy ma sweeper ghi vao {@code Orders.CancelReason} - bat buoc theo dac
     * ta P04.
     */
    public static final String CANCEL_REASON = "auto-expired";

    /** Cong tat trong {@code SystemSettings}; thieu khoa nay thi coi nhu bat. */
    public static final String SETTING_ENABLED = "sweeper.enabled";

    /** Don nhap qua bao nhieu phut thi coi la mo coi. Mac dinh 30. */
    public static final String SETTING_ORPHAN_MINUTES = "sweeper.orphanOrderMinutes";

    private static final int DEFAULT_ORPHAN_MINUTES = 30;

    /**
     * Cho khoa toi da 5 giay roi bo cuoc — sweeper khong duoc chan luong dat ve.
     */
    private static final int LOCK_TIMEOUT_MS = 5000;

    private static final int LOCK_TIMEOUT_ERROR = 1222;
    private static final int DEADLOCK_VICTIM_ERROR = 1205;

    /** Ket qua mot vong quet, de servlet chay tay va test doc duoc so lieu. */
    public static final class SweepResult {
        private final int releasedSeats;
        private final int cancelledCounterOrders;
        private final int cancelledOrphanOrders;
        private final int releasedSeatsFromOrders;
        private final boolean skipped;

        SweepResult(int releasedSeats, int cancelledCounterOrders, int cancelledOrphanOrders,
                int releasedSeatsFromOrders, boolean skipped) {
            this.releasedSeats = releasedSeats;
            this.cancelledCounterOrders = cancelledCounterOrders;
            this.cancelledOrphanOrders = cancelledOrphanOrders;
            this.releasedSeatsFromOrders = releasedSeatsFromOrders;
            this.skipped = skipped;
        }

        public int getReleasedSeats() {
            return releasedSeats;
        }

        public int getCancelledCounterOrders() {
            return cancelledCounterOrders;
        }

        public int getCancelledOrphanOrders() {
            return cancelledOrphanOrders;
        }

        public int getReleasedSeatsFromOrders() {
            return releasedSeatsFromOrders;
        }

        public boolean isSkipped() {
            return skipped;
        }

        public int getTotalTouched() {
            return releasedSeats + cancelledCounterOrders + cancelledOrphanOrders + releasedSeatsFromOrders;
        }

        @Override
        public String toString() {
            return "ghe giu qua han da tra=" + releasedSeats
                    + ", don tai quay qua han da huy=" + cancelledCounterOrders
                    + ", don nhap mo coi da huy=" + cancelledOrphanOrders
                    + ", ghe tra theo don da huy=" + releasedSeatsFromOrders;
        }
    }

    /**
     * Chay mot vong quet. Khong bao gio nem ra ngoai: sweeper chay trong
     * {@code ScheduledExecutorService}, mot exception thoat ra se lam lich chay
     * <b>ngung han</b>.
     * Loi duoc log muc SEVERE kem ngu canh, va vong sau van chay lai binh thuong.
     */
    public SweepResult sweepOnce() {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(true);
            // Sweeper la viec nen: khong bao gio duoc bat khach dat ve cho no. Gap khoa thi
            // bo
            // vong nay va thu lai sau 30 giay, thay vi chan luong dat ve.
            try (PreparedStatement ps = connection.prepareStatement("SET LOCK_TIMEOUT " + LOCK_TIMEOUT_MS)) {
                ps.execute();
            }
            if (!isEnabled(connection)) {
                LOG.fine("HoldSweeper: bi tat qua SystemSettings '" + SETTING_ENABLED + "', bo qua vong nay.");
                return new SweepResult(0, 0, 0, 0, true);
            }

            int releasedSeats = releaseExpiredHolds(connection);
            sendCounterReminders(connection);
            int[] counter = cancelExpiredCounterOrders(connection);
            int[] orphan = cancelOrphanDraftOrders(connection, orphanMinutes(connection));
            int cleanedAttempts = cleanupLoginAttempts(connection);
            if (cleanedAttempts > 0) {
                LOG.info("HoldSweeper: da don " + cleanedAttempts + " LoginAttempts cu hon 90 ngay.");
            }

            SweepResult result = new SweepResult(releasedSeats + counter[1] + orphan[1],
                    counter[0], orphan[0], counter[1] + orphan[1], false);
            // Im lang la vi pham dac ta P04: moi vong phai noi ro no da lam gi.
            if (result.getTotalTouched() > 0) {
                LOG.info("HoldSweeper: " + result);
            } else {
                LOG.fine("HoldSweeper: khong co gi de don.");
            }
            return result;
        } catch (SQLException ex) {
            if (ex.getErrorCode() == LOCK_TIMEOUT_ERROR || ex.getErrorCode() == DEADLOCK_VICTIM_ERROR) {
                LOG.warning("HoldSweeper: nhuong khoa cho luong dat ve (SQL error " + ex.getErrorCode()
                        + "), bo qua vong nay va thu lai sau.");
            } else {
                LOG.log(Level.SEVERE, "HoldSweeper: vong quet that bai, se thu lai o vong sau.", ex);
            }
            return new SweepResult(0, 0, 0, 0, true);
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "HoldSweeper: vong quet that bai, se thu lai o vong sau.", ex);
            return new SweepResult(0, 0, 0, 0, true);
        }
    }

    // --------------------------------------------------------------- buoc 1

    /**
     * Ghe con {@code held} nhung {@code HeldUntil} da troi qua thi tra ve
     * {@code available}.
     */
    private int releaseExpiredHolds(Connection connection) throws SQLException {
        String sql = """
                UPDATE ShowtimeSeats
                SET Status = 'available', HeldByUserId = NULL, HeldAt = NULL, HeldUntil = NULL,
                    ClaimedByOrderId = NULL
                WHERE Status = 'held' AND HeldUntil IS NOT NULL AND HeldUntil < GETDATE()
                  AND ClaimedByOrderId IS NULL
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    // --------------------------------------------------------------- buoc 2

    /**
     * Huy don "thanh toan tai quay" qua han va tra ghe.
     *
     * @return {@code [so don da huy, so ghe da tra]}
     */
    private int[] cancelExpiredCounterOrders(Connection connection) throws SQLException {
        String claimSql = """
                SELECT Id
                FROM Orders WITH (UPDLOCK, ROWLOCK, READPAST)
                WHERE PaymentMethod = 'counter'
                AND PaymentStatus = 'pending'
                AND OrderStatus = 'confirmed'
                AND CounterExpiresAt IS NOT NULL
                AND CounterExpiresAt <= GETDATE()
                """;
        // Ghe cua don tai quay dang o trang thai 'booked' (xem
        // JdbcOrderDAO.confirmCounterOrder).
        // Chi ghe thuoc dung don nay moi bi dung toi, nen khong the cuop ghe cua nguoi
        // khac.
        String releaseSql = """
                UPDATE ss
                SET ss.Status = 'available', ss.HeldByUserId = NULL, ss.HeldAt = NULL,
                    ss.HeldUntil = NULL, ss.ClaimedByOrderId = NULL
                FROM ShowtimeSeats ss
                JOIN OrderSeats os ON os.ShowtimeSeatId = ss.Id
                WHERE os.OrderId IN (%s) AND ss.Status IN ('booked', 'held')
                  AND (ss.ClaimedByOrderId = os.OrderId OR ss.ClaimedByOrderId IS NULL)
                """;
        return cancelClaimedOrders(connection, claimSql, releaseSql);
    }

    private void sendCounterReminders(Connection connection) throws SQLException {
        String select = """
                SELECT o.Id, o.TicketCode, o.CounterExpiresAt, u.Email, u.FullName
                FROM Orders o WITH (UPDLOCK, READPAST, ROWLOCK)
                JOIN Users u ON u.Id=o.UserId
                WHERE o.PaymentMethod='counter' AND o.PaymentStatus='pending'
                AND o.OrderStatus='confirmed' AND o.CounterReminderSentAt IS NULL
                AND o.CounterExpiresAt>GETDATE()
                AND o.CounterExpiresAt<=DATEADD(MINUTE,5,GETDATE())
                """;
        List<CounterReminder> reminders = new ArrayList<>();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement ps = connection.prepareStatement(select);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    reminders.add(new CounterReminder(rs.getInt("Id"), rs.getString("TicketCode"),
                            rs.getString("CounterExpiresAt"), rs.getString("Email"),
                            rs.getString("FullName")));
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE Orders SET CounterReminderSentAt=GETDATE() WHERE Id=? AND CounterReminderSentAt IS NULL")) {
                for (CounterReminder reminder : reminders) {
                    ps.setInt(1, reminder.orderId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
        EmailService email = new EmailService();
        for (CounterReminder reminder : reminders) {
            email.sendCounterReminder(reminder.email, reminder.fullName, reminder.orderId,
                    reminder.ticketCode, reminder.expiresAt);
        }
    }

    private record CounterReminder(int orderId, String ticketCode, String expiresAt,
            String email, String fullName) {
    }

    // --------------------------------------------------------------- buoc 3

    /**
     * Huy don nhap bi bo do qua lau. Don nhap giu ghe 10 phut; qua nguong nay (mac
     * dinh 30 phut)
     * thi ghe chac chan da het han giu, con ban ghi don thi nam lai vo ich.
     *
     * @return {@code [so don da huy, so ghe da tra]}
     */
    private int[] cancelOrphanDraftOrders(Connection connection, int orphanMinutes) throws SQLException {
        String claimSql = """
                SELECT Id
                FROM Orders WITH (UPDLOCK, ROWLOCK, READPAST)
                WHERE OrderStatus IN ('created', 'pending')
                  AND PaymentStatus = 'pending'
                  AND (CreatedAt < DATEADD(MINUTE, -%d, GETDATE())
                       OR EXISTS (
                           SELECT 1
                           FROM OrderSeats eos
                           JOIN ShowtimeSeats ess ON ess.Id=eos.ShowtimeSeatId
                           WHERE eos.OrderId=Orders.Id AND ess.Status='held'
                             AND ess.ClaimedByOrderId=Orders.Id
                             AND ess.HeldUntil IS NOT NULL AND ess.HeldUntil < GETDATE()
                       ))
                """.formatted(orphanMinutes);
        // Dieu kien HeldByUserId = o.UserId va HeldUntil da qua la hang rao chong "cuop
        // ghe":
        // sau khi hold het han, nguoi khac co the da giu lai chinh ghe do bang mot don
        // moi.
        String releaseSql = """
                UPDATE ss
                SET ss.Status = 'available', ss.HeldByUserId = NULL, ss.HeldAt = NULL,
                    ss.HeldUntil = NULL, ss.ClaimedByOrderId = NULL
                FROM ShowtimeSeats ss
                JOIN OrderSeats os ON os.ShowtimeSeatId = ss.Id
                JOIN Orders o ON o.Id = os.OrderId
                WHERE o.Id IN (%s)
                  AND ss.Status = 'held'
                  AND ss.HeldByUserId = o.UserId
                  AND (ss.ClaimedByOrderId = o.Id OR ss.ClaimedByOrderId IS NULL)
                  AND (ss.HeldUntil IS NULL OR ss.HeldUntil < GETDATE())
                """;
        return cancelClaimedOrders(connection, claimSql, releaseSql);
    }

    // ------------------------------------------------------------- dung chung

    /**
     * Gianh don theo {@code claimSql}, tra ghe theo {@code releaseSql} roi danh dau
     * don huy —
     * tat ca trong <b>mot</b> transaction tren <b>mot</b> connection.
     *
     * @param releaseSql cau lenh co dung mot cho {@code %s} de chen danh sach dau
     *                   hoi
     */
    private int[] cancelClaimedOrders(Connection connection, String claimSql, String releaseSql) throws SQLException {
        connection.setAutoCommit(false);
        try {
            List<Integer> orderIds = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(claimSql);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orderIds.add(rs.getInt("Id"));
                }
            }
            if (orderIds.isEmpty()) {
                connection.commit();
                return new int[] { 0, 0 };
            }

            String placeholders = orderIds.stream().map(id -> "?").collect(Collectors.joining(", "));

            int releasedSeats;
            try (PreparedStatement ps = connection.prepareStatement(releaseSql.formatted(placeholders))) {
                bind(ps, orderIds);
                releasedSeats = ps.executeUpdate();
            }

            String cancelSql = """
                    UPDATE Orders
                    SET OrderStatus = 'cancelled', PaymentStatus = 'cancelled',
                        CancelledAt = GETDATE(), CounterExpiresAt = NULL,
                        CancelReason = ?, UpdatedAt = GETDATE()
                    WHERE Id IN (
                    """ + placeholders + ")";
            int cancelled;
            try (PreparedStatement ps = connection.prepareStatement(cancelSql)) {
                ps.setString(1, CANCEL_REASON);
                for (int i = 0; i < orderIds.size(); i++) {
                    ps.setInt(i + 2, orderIds.get(i));
                }
                cancelled = ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE UserVouchers SET IsUsed=0, UsedAt=NULL, UsedOrderId=NULL
                    WHERE UsedOrderId IN (
                    """ + placeholders + ")")) {
                bind(ps, orderIds);
                ps.executeUpdate();
            }

            // Promotion usage is part of the same terminal transition.  Releasing
            // it here closes the old window where a cancelled/expired order kept
            // consuming quota after its seats were returned.
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE p
                    SET UsedCount = CASE WHEN p.UsedCount >= x.ReleasedCount
                                         THEN p.UsedCount - x.ReleasedCount ELSE 0 END,
                        UpdatedAt = GETDATE()
                    FROM Promotions p
                    JOIN (
                        SELECT o.PromotionId, COUNT(*) AS ReleasedCount
                        FROM Orders o
                        WHERE o.Id IN (%s) AND o.PromotionId IS NOT NULL
                        GROUP BY o.PromotionId
                    ) x ON x.PromotionId = p.Id
                    """.formatted(placeholders))) {
                bind(ps, orderIds);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                    DELETE FROM PromotionUsage WHERE OrderId IN (
                    """ + placeholders + ")")) {
                bind(ps, orderIds);
                ps.executeUpdate();
            }

            connection.commit();
            return new int[] { cancelled, releasedSeats };
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void bind(PreparedStatement ps, List<Integer> ids) throws SQLException {
        for (int i = 0; i < ids.size(); i++) {
            ps.setInt(i + 1, ids.get(i));
        }
    }

    private int cleanupLoginAttempts(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM LoginAttempts WHERE AttemptAt < DATEADD(DAY,-90,GETDATE())")) {
            return ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------- cau hinh

    private boolean isEnabled(Connection connection) throws SQLException {
        String raw = setting(connection, SETTING_ENABLED);
        return raw == null || raw.isBlank() || !"false".equalsIgnoreCase(raw.trim());
    }

    private int orphanMinutes(Connection connection) throws SQLException {
        String raw = setting(connection, SETTING_ORPHAN_MINUTES);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_ORPHAN_MINUTES;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : DEFAULT_ORPHAN_MINUTES;
        } catch (NumberFormatException ex) {
            // Cau hinh sai thi quay ve mac dinh an toan, nhung phai noi ra chu khong nuot.
            LOG.warning("HoldSweeper: gia tri '" + SETTING_ORPHAN_MINUTES + "' khong phai so ('" + raw
                    + "'), dung mac dinh " + DEFAULT_ORPHAN_MINUTES + " phut.");
            return DEFAULT_ORPHAN_MINUTES;
        }
    }

    private String setting(Connection connection, String key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT SettingValue FROM SystemSettings WHERE SettingKey = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
