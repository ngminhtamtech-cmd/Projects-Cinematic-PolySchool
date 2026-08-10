package com.mycompany.website.ban.ve.xem.phim.util;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dong ho nghiep vu duy nhat cua he thong (F-004).
 *
 * <p><b>Van de goc.</b> Moi moc thoi gian nghiep vu duoc GHI bang gio SQL Server
 * ({@code GETDATE()}): {@code Showtimes.StartTime}, {@code HeldUntil}, {@code RedeemedAt},
 * {@code CounterExpiresAt}. Nhung phan loai ve, khung gio check-in va dieu kien xem xet hoan tien
 * lai DOC bang {@code LocalDateTime.now()} cua JVM. Hai nguon nay lech nhau khi may ung dung sai
 * gio hoac dat timezone khac may DB, nen cung mot don co the cho hai ket qua khac nhau tuy node
 * nao tra loi request. Day la loi da co that o {@code markHeld} vs {@code releaseExpiredHolds}.</p>
 *
 * <p><b>Cach chot.</b> Gio DB la nguon su that duy nhat. Lop nay do <i>do lech</i> giua gio JVM va
 * gio DB mot lan moi {@value #RESYNC_INTERVAL_MS} ms, roi cong do lech do vao gio JVM. Nho vay moi
 * lan doc gio khong ton mot query, nhung ket qua van la gio DB: hai dong ho vat ly khong the troi
 * xa nhau dang ke trong 60 giay.</p>
 *
 * <p><b>Khi khong doc duoc gio DB.</b> Neu da tung dong bo, do lech cu duoc giu nguyen — van la
 * gio DB, chi kem tuoi hon. Neu chua tung dong bo duoc lan nao, gio JVM duoc dung tam va ghi log
 * {@code WARNING} kem nguyen nhan. Khong nem exception: cac ham goi den day la getter hien thi cua
 * JSP, nem loi o do se lam trang trang thay vi hien sai vai giay. Cac chot nghiep vu that su
 * (khoa ghe, cutoff dat ve, {@code redeemTicket}) van so sanh thoi gian <b>trong SQL</b> nen khong
 * phu thuoc lop nay.</p>
 *
 * <p>Khong dung {@code LocalDateTime.now()} cho bat ky quyet dinh nghiep vu nao nua — dung
 * {@link #now()}.</p>
 */
public final class BusinessClock {
    private static final Logger LOG = Logger.getLogger(BusinessClock.class.getName());

    /** Do lai do lech gio voi DB sau moi khoang nay. */
    public static final long RESYNC_INTERVAL_MS = 60_000L;

    private static final Supplier<LocalDateTime> DATABASE_TIME = DBConnection::dbNow;

    private static volatile Supplier<LocalDateTime> timeSource = DATABASE_TIME;
    private static volatile LocalDateTime fixedNow;
    private static volatile long offsetMillis;
    private static volatile long lastAttemptAtMs;
    private static volatile boolean attempted;
    private static volatile boolean syncedWithDatabase;

    private BusinessClock() {
    }

    /**
     * Gio nghiep vu hien tai theo dong ho cua SQL Server.
     *
     * <p>Khong bao gio nem exception va khong bao gio tra null.</p>
     */
    public static LocalDateTime now() {
        LocalDateTime fixed = fixedNow;
        if (fixed != null) {
            return fixed;
        }
        long jvmMs = System.currentTimeMillis();
        if (!attempted || jvmMs - lastAttemptAtMs >= RESYNC_INTERVAL_MS) {
            resync(jvmMs);
        }
        return LocalDateTime.now().plus(offsetMillis, ChronoUnit.MILLIS);
    }

    /**
     * Do lech dang ap dung, tinh bang milli giay ({@code gioDB - gioJVM}).
     *
     * <p>Chi dung cho log, health check va test.</p>
     */
    public static long getOffsetMillis() {
        return offsetMillis;
    }

    /** Da doc duoc gio DB it nhat mot lan hay chua. */
    public static boolean isSyncedWithDatabase() {
        return syncedWithDatabase;
    }

    private static synchronized void resync(long jvmMs) {
        if (attempted && jvmMs - lastAttemptAtMs < RESYNC_INTERVAL_MS) {
            return;
        }
        attempted = true;
        lastAttemptAtMs = jvmMs;
        try {
            LocalDateTime databaseNow = timeSource.get();
            if (databaseNow == null) {
                throw new IllegalStateException("Time source returned null");
            }
            offsetMillis = ChronoUnit.MILLIS.between(LocalDateTime.now(), databaseNow);
            syncedWithDatabase = true;
        } catch (RuntimeException ex) {
            if (syncedWithDatabase) {
                LOG.log(Level.WARNING,
                        "BusinessClock: khong doc lai duoc gio DB, giu do lech da biet "
                                + offsetMillis + "ms. Nguyen nhan: {0}",
                        ex.toString());
            } else {
                LOG.log(Level.WARNING,
                        "BusinessClock: chua dong bo duoc voi gio DB lan nao, tam dung gio JVM. "
                                + "Phan loai ve va khung gio check-in co the lech neu may ung dung sai gio. "
                                + "Nguyen nhan: {0}",
                        ex.toString());
            }
        }
    }

    /** Chi dung cho test: coi mot moc thoi gian co dinh la gio nghiep vu. */
    public static void useFixedTimeForTesting(LocalDateTime fixed) {
        fixedNow = fixed;
    }

    /** Chi dung cho test: thay nguon gio DB. */
    public static void useTimeSourceForTesting(Supplier<LocalDateTime> source) {
        timeSource = source == null ? DATABASE_TIME : source;
        fixedNow = null;
        attempted = false;
        syncedWithDatabase = false;
        offsetMillis = 0L;
        lastAttemptAtMs = 0L;
    }

    /** Chi dung cho test: buoc lan doc gio ke tiep phai dong bo lai thay vi cho het chu ky. */
    public static void forceResyncForTesting() {
        attempted = false;
        lastAttemptAtMs = 0L;
    }

    /** Chi dung cho test: tra lop ve trang thai mac dinh (doc gio tu DB). */
    public static void resetForTesting() {
        useTimeSourceForTesting(null);
    }
}
