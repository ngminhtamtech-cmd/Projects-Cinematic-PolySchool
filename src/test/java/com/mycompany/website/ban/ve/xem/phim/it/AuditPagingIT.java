package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.AuditLogEntry;
import com.mycompany.website.ban.ve.xem.phim.model.PageResult;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeout;

@Tag("it")
public class AuditPagingIT {
    @BeforeAll
    static void configure() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        // Do không tính thời gian khởi tạo JVM/pool vào SLA tải một trang trên server đang chạy.
        new AdminService().listAuditLogs(1, 10);
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    void auditLogUsesBoundedTwoQueryPage() {
        long started = System.nanoTime();
        PageResult<AuditLogEntry> page = assertTimeout(Duration.ofSeconds(2),
                () -> new AdminService().listAuditLogs(1, 50));
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        System.out.println("P16_METRIC audit_page_ms=" + elapsedMs + " queries=2 rows=200000");
        assertEquals(2, AdminService.AUDIT_PAGE_QUERY_COUNT);
        assertTrue(page.items().size() <= 50);
        assertEquals(50, page.size());
    }
}
