package com.mycompany.website.ban.ve.xem.phim.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mycompany.website.ban.ve.xem.phim.model.Cinema;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Public cinema context")
class PublicCinemaContextTest {

    @Test
    @DisplayName("Explicit cinema selection is reused by the next request")
    void selectedCinemaPersistsInSession() {
        Map<String, Object> sessionValues = new HashMap<>();
        HttpSession session = session(sessionValues);
        List<Cinema> cinemas = List.of(cinema(7, "Cinema 1"), cinema(8, "Cinema 2"));

        assertEquals(8, PublicCinemaContext.resolve(request("8", session), cinemas));
        assertEquals(8, sessionValues.get(PublicCinemaContext.SESSION_CINEMA_ID));
        assertEquals(8, PublicCinemaContext.resolve(request(null, session), cinemas));
    }

    @Test
    @DisplayName("Stale session selection falls back to the first active cinema")
    void staleSessionSelectionFallsBackSafely() {
        Map<String, Object> sessionValues = new HashMap<>();
        sessionValues.put(PublicCinemaContext.SESSION_CINEMA_ID, 999);
        HttpSession session = session(sessionValues);

        assertEquals(7, PublicCinemaContext.resolve(
                request(null, session), List.of(cinema(7, "Cinema 1"), cinema(8, "Cinema 2"))));
        assertEquals(7, sessionValues.get(PublicCinemaContext.SESSION_CINEMA_ID));
    }

    private static Cinema cinema(int id, String name) {
        Cinema cinema = new Cinema();
        cinema.setId(id);
        cinema.setName(name);
        return cinema;
    }

    private static HttpSession session(Map<String, Object> values) {
        return (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> values.get((String) args[0]);
                    case "setAttribute" -> {
                        values.put((String) args[0], args[1]);
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static HttpServletRequest request(String cinemaId, HttpSession session) {
        Map<String, Object> attributes = new HashMap<>();
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getParameter" -> "cinemaId".equals(args[0]) ? cinemaId : null;
                    case "getSession" -> session;
                    case "getAttribute" -> attributes.get((String) args[0]);
                    case "setAttribute" -> {
                        attributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                });
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
