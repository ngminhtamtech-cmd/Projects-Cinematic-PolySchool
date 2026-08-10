package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F-004 — dong ho nghiep vu phai la gio DB, khong phai gio JVM.
 */
class BusinessClockTest {

    @AfterEach
    void restoreDefaultClock() {
        BusinessClock.resetForTesting();
    }

    @Test
    @DisplayName("now() theo gio DB du gio JVM lech 15 phut")
    void nowFollowsDatabaseClock() {
        BusinessClock.useTimeSourceForTesting(() -> LocalDateTime.now().plusMinutes(15));

        long aheadSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), BusinessClock.now());

        assertTrue(aheadSeconds >= 14 * 60 && aheadSeconds <= 16 * 60,
                "Phai lech ~+15 phut so voi gio JVM, thuc te " + aheadSeconds + "s");
        assertTrue(BusinessClock.isSyncedWithDatabase());
    }

    @Test
    @DisplayName("Khong query gio DB moi lan goi: mot chu ky chi dong bo mot lan")
    void syncsAtMostOncePerInterval() {
        AtomicInteger reads = new AtomicInteger();
        BusinessClock.useTimeSourceForTesting(() -> {
            reads.incrementAndGet();
            return LocalDateTime.now();
        });

        for (int i = 0; i < 200; i++) {
            assertNotNull(BusinessClock.now());
        }

        assertEquals(1, reads.get(), "Chi duoc doc gio DB mot lan trong mot chu ky resync");
    }

    @Test
    @DisplayName("DB loi giua duong: giu do lech da biet, khong quay ve gio JVM")
    void keepsLastKnownOffsetWhenDatabaseReadFails() {
        AtomicBoolean broken = new AtomicBoolean(false);
        BusinessClock.useTimeSourceForTesting(() -> {
            if (broken.get()) {
                throw new IllegalStateException("simulated database outage");
            }
            return LocalDateTime.now().plusMinutes(30);
        });

        BusinessClock.now();
        long syncedOffset = BusinessClock.getOffsetMillis();
        assertTrue(syncedOffset > 29 * 60_000L, "Do lech sau dong bo phai ~+30 phut");

        broken.set(true);
        BusinessClock.forceResyncForTesting();
        LocalDateTime afterOutage = assertDoesNotThrow(BusinessClock::now);

        assertEquals(syncedOffset, BusinessClock.getOffsetMillis(), "Phai giu do lech cu");
        assertTrue(ChronoUnit.SECONDS.between(LocalDateTime.now(), afterOutage) > 29 * 60L,
                "Sau su co van phai tra gio DB cu, khong phai gio JVM");
    }

    @Test
    @DisplayName("Chua bao gio doc duoc gio DB: dung gio JVM tam thoi, khong nem loi")
    void degradesToJvmClockWithoutThrowing() {
        BusinessClock.useTimeSourceForTesting(() -> {
            throw new IllegalStateException("simulated database outage");
        });

        LocalDateTime now = assertDoesNotThrow(BusinessClock::now);

        assertFalse(BusinessClock.isSyncedWithDatabase());
        assertTrue(Math.abs(ChronoUnit.SECONDS.between(LocalDateTime.now(), now)) < 5,
                "Khi chua tung dong bo, gio nghiep vu tam bang gio JVM");
    }
}
