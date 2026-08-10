package com.mycompany.website.ban.ve.xem.phim.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * ObjectMapper dung chung cho toan bo lop API.
 *
 * <p>Thay the hoan toan viec noi chuoi JSON bang tay o cac servlet cu
 * (ShowtimeSeatServlet, OrderServlet, PromotionServlet) - von lam vo payload
 * khi du lieu chua dau nhay kep.</p>
 */
public final class Json {
    private static final ObjectMapper MAPPER = build();

    private Json() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    private static ObjectMapper build() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // LocalDateTime -> "2026-07-24T14:30:00" thay vi mang so.
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Bo qua field null cho payload gon.
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // Client gui thua field thi bo qua, khong nem loi.
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
