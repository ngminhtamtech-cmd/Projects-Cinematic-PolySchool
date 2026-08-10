package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.service.EmailService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("it")
public class EmailLogDeliveryIT {
    @Test
    void allFiveOperationalMessagesReachUtf8Log(@TempDir Path tempDir) throws Exception {
        String requestedBase = System.getProperty("cinebook.mail.smoke.catalinaBase");
        Path catalinaBase = requestedBase == null || requestedBase.isBlank()
                ? tempDir : Path.of(requestedBase);
        String previousBase = System.getProperty("catalina.base");
        System.setProperty("catalina.base", catalinaBase.toString());
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        try {
            EmailService email = new EmailService();
            email.sendVerification("probe@test.local", "Nguyễn An", "https://cinebook/verify");
            email.sendTicket("probe@test.local", "Nguyễn An", 7, "CBABC",
                    "Mắt biếc", "CineBook Huế", "20:00");
            email.sendShowtimeCancellation("probe@test.local", "Nguyễn An", 7,
                    "Mắt biếc", "CineBook Huế", "20:00");
            email.sendPasswordReset("probe@test.local", "Nguyễn An", "https://cinebook/reset", 30);
            email.sendCounterReminder("probe@test.local", "Nguyễn An", 7, "CBABC", "20:00");

            String log = Files.readString(catalinaBase.resolve("logs/cinebook-mail.log"),
                    StandardCharsets.UTF_8);
            assertTrue(log.contains("Xác thực email"));
            assertTrue(log.contains("Vé điện tử #7"));
            assertTrue(log.contains("Thông báo hủy suất chiếu"));
            assertTrue(log.contains("Đặt lại mật khẩu"));
            assertTrue(log.contains("Đơn tại quầy sắp hết hạn"));
            assertTrue(log.contains("Nguyễn An"));
            // BUG-14: chinh cum tu bao cao noi bi mojibake ("Suáº¥t chiáº¿u"). Doc file bang UTF-8
            // ma van thay dung nghia la byte tren dia dung — mojibake khi do la do CONG CU DOC
            // dung codepage ANSI, khong phai do duong ghi. Chot lai o day de mot thay doi sau nay
            // (bo StandardCharsets.UTF_8 o EmailService.writeLog) khong the am tham lot qua.
            assertTrue(log.contains("Suất chiếu"),
                    "Log phai chua 'Suất chiếu' dung dau khi doc bang UTF-8");
        } finally {
            DBConnection.shutdown();
            if (previousBase == null) System.clearProperty("catalina.base");
            else System.setProperty("catalina.base", previousBase);
        }
    }
}
