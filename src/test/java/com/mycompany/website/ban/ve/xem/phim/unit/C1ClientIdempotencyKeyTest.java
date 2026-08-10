package com.mycompany.website.ban.ve.xem.phim.unit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C.1 (BUG-02 la ma chet neu khong co buoc nay) — client phai thuc su gui khoa idempotency.
 *
 * <p><b>Van de.</b> Tang service nhan {@code X-Idempotency-Key} o ca duong tao don lan duong
 * thanh toan, va A.2 vua siet cho khoa phai khop chu don. Nhung {@code seat-map.js} — duong dat
 * ve that cua nguoi dung — chi gui {@code Accept}/{@code Content-Type}/{@code X-CSRF-Token}. Nen
 * {@code OrderServlet.getIdempotencyKey} luon tra {@code null}, {@code Orders.IdempotencyKey}
 * luon NULL, va toan bo lop chong trung khong bao gio chay: khach bam hai lan hoac rot mang roi
 * thu lai van gap dung trieu chung cu.</p>
 *
 * <p>Do bang cach doc chinh file JS: day la hop dong giua hai tang ma khong co test JS nao
 * canh giu. Cung cach {@code NoSwallowedExceptionTest} quet ma nguon Java.</p>
 */
@DisplayName("C.1 — seat-map.js phai gui X-Idempotency-Key o ca create va pay")
public class C1ClientIdempotencyKeyTest {

    private static final Path SEAT_MAP = Path.of("src", "main", "webapp", "assets", "js", "seat-map.js");
    private static final Path BOOKING_PAGE = Path.of(
            "src", "main", "webapp", "WEB-INF", "views", "booking", "page.jsp");

    @Test
    @DisplayName("lenh tao don gui khoa")
    public void createOrderRequestSendsTheKey() throws IOException {
        assertTrue(headersOfFetchTo("'/orders'").contains("X-Idempotency-Key"),
                "POST /orders khong gui khoa -> Orders.IdempotencyKey se mai NULL. Header dang gui: "
                + headersOfFetchTo("'/orders'"));
    }

    @Test
    @DisplayName("lenh thanh toan gui khoa")
    public void payRequestSendsTheKey() throws IOException {
        assertTrue(headersOfFetchTo("/pay'").contains("X-Idempotency-Key"),
                "POST /orders/{id}/pay khong gui khoa -> bam Pay hai lan van tao hai lan tinh tien."
                + " Header dang gui: " + headersOfFetchTo("/pay'"));
    }

    @Test
    @DisplayName("mot lan dat ve dung MOT khoa, va khoa song qua F5")
    public void theKeyIsStableAcrossTheWholeBooking() throws IOException {
        String script = Files.readString(SEAT_MAP, StandardCharsets.UTF_8);

        assertTrue(script.contains("sessionStorage.getItem(IDEMPOTENCY_STORE_KEY)"),
                "Khoa phai duoc nho lai, khong sinh moi moi lan gui — sinh moi thi server khong"
                + " nhan ra day la lan thu lai va ta khong chan duoc gi.");
        assertEquals(2, countOccurrences(script, "'X-Idempotency-Key': bookingKey()"),
                "Ca hai lenh (create va pay) phai dung CUNG mot khoa cua lan dat ve nay");
        assertTrue(script.contains("sessionStorage.removeItem(IDEMPOTENCY_STORE_KEY)"),
                "Dat ve xong phai quen khoa di, neu khong lan dat sau bi coi la lan thu lai cua lan truoc");
    }

    @Test
    @DisplayName("input an idempotencyKeyNew da bi xoa (khong ai doc)")
    public void deadHiddenInputIsGone() throws IOException {
        String page = Files.readString(BOOKING_PAGE, StandardCharsets.UTF_8);
        assertFalse(page.contains("idempotencyKeyNew"),
                "booking/page.jsp con input an idempotencyKeyNew ma khong doan ma nao doc — rac");
    }

    // ------------------------------------------------------------------- helpers

    /**
     * Khoi {@code headers: { ... }} cua loi goi {@code fetch} toi duong dan chua {@code marker}.
     */
    private static String headersOfFetchTo(String marker) throws IOException {
        String script = Files.readString(SEAT_MAP, StandardCharsets.UTF_8);
        int callIndex = -1;
        Matcher fetches = Pattern.compile("fetch\\(").matcher(script);
        while (fetches.find()) {
            int end = Math.min(script.length(), fetches.end() + 120);
            if (script.substring(fetches.end(), end).contains(marker)) {
                callIndex = fetches.end();
            }
        }
        assertTrue(callIndex > 0, "khong tim thay loi goi fetch toi " + marker + " trong seat-map.js");

        int headersIndex = script.indexOf("headers:", callIndex);
        assertTrue(headersIndex > 0, "loi goi fetch toi " + marker + " khong co khoi headers");
        int close = script.indexOf('}', headersIndex);
        return script.substring(headersIndex, close < 0 ? script.length() : close);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int index = haystack.indexOf(needle); index >= 0; index = haystack.indexOf(needle, index + 1)) {
            count++;
        }
        return count;
    }
}
