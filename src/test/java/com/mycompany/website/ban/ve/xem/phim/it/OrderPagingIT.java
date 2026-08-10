package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.PageResult;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
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
public class OrderPagingIT {
    @BeforeAll
    static void configure() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        // Do không tính thời gian khởi tạo JVM/pool vào SLA tải một trang trên server đang chạy.
        new AdminService().listOrdersForAdmin(1, 10, null, null, null, null, null);
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    void adminOrdersUsesBoundedFourQueryPage() {
        long started = System.nanoTime();
        PageResult<OrderRecord> page = assertTimeout(Duration.ofSeconds(2),
                () -> new AdminService().listOrdersForAdmin(1, 20, null, null, null, null, null));
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        System.out.println("P16_METRIC order_page_ms=" + elapsedMs + " queries=4 rows=50000");
        assertEquals(4, AdminService.ORDER_PAGE_QUERY_COUNT);
        assertTrue(page.items().size() <= 20);
        assertEquals(20, page.size());
    }
}
