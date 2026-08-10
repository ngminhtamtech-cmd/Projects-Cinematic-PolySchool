package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.util.JsonUtil;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalDataExportTest {
    @Test
    void exportedShapeDoesNotExposeAuthenticationSecrets() {
        String json = JsonUtil.toJson(Map.of(
                "profile", Map.of("Email", "member@example.test", "FullName", "Nguyễn Văn A"),
                "orders", java.util.List.of()));
        assertTrue(json.contains("member@example.test"));
        assertFalse(json.toLowerCase().contains("password"));
        assertFalse(json.toLowerCase().contains("tokenhash"));
    }
}
