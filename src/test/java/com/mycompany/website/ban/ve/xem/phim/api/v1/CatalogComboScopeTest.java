package com.mycompany.website.ban.ve.xem.phim.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.mycompany.website.ban.ve.xem.phim.api.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Public combo catalog cinema scope")
class CatalogComboScopeTest {

    @Test
    @DisplayName("Missing cinema selection is a successful empty catalog")
    void missingCinemaReturnsEmptyData() throws Exception {
        Exchange exchange = invoke(Map.of());
        JsonNode envelope = Json.mapper().readTree(exchange.body());

        assertEquals(200, exchange.status);
        assertTrue(envelope.path("data").isArray());
        assertEquals(0, envelope.path("data").size());
    }

    @Test
    @DisplayName("Explicit non-positive cinema remains invalid")
    void nonPositiveCinemaIsRejected() throws Exception {
        Exchange exchange = invoke(Map.of("cinemaId", "0"));
        JsonNode envelope = Json.mapper().readTree(exchange.body());

        assertEquals(400, exchange.status);
        assertEquals("BAD_REQUEST", envelope.path("error").path("code").asText());
    }

    private static Exchange invoke(Map<String, String> parameters) throws Exception {
        Exchange exchange = new Exchange();
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "GET";
                    case "getServletPath" -> "/api/v1/combos";
                    case "getRequestURI" -> "/Website-ban-ve-xem-phim/api/v1/combos";
                    case "getParameter" -> parameters.get((String) args[0]);
                    case "getSession" -> null;
                    default -> defaultValue(method.getReturnType());
                });
        new InspectableCatalogApiServlet().invoke(request, exchange.response());
        return exchange;
    }

    private static final class InspectableCatalogApiServlet extends CatalogApiServlet {
        void invoke(HttpServletRequest request, HttpServletResponse response) throws Exception {
            super.service(request, response);
        }
    }

    private static final class Exchange {
        private int status = 200;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setStatus" -> {
                            status = (Integer) args[0];
                            yield null;
                        }
                        case "getStatus" -> status;
                        case "getOutputStream" -> outputStream(output);
                        case "reset" -> {
                            status = 200;
                            output.reset();
                            yield null;
                        }
                        case "isCommitted" -> false;
                        case "setCharacterEncoding", "setContentType" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        String body() {
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private static ServletOutputStream outputStream(ByteArrayOutputStream output) {
        return new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }

            @Override
            public void write(int value) throws IOException {
                output.write(value);
            }
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class || type == short.class || type == byte.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        return '\0';
    }
}
