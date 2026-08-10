package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * C.3 — khong con nhanh loc {@code isRoomActive} chet o cac trang cong khai.
 *
 * <p>{@code JdbcShowtimeDAO.findByFilmAndCinema} da loc
 * {@code AND ISNULL(r.Status,'active') = 'active'} ngay trong SQL, nen moi suat toi duoc trang
 * cong khai deu thuoc phong dang hoat dong va {@code ${st.roomActive}} luon {@code true}. Ba JSP
 * van giu ~40 dong JS loc khong bao gio chay: nut "(Ngưng)", canh bao "phong tam ngung", nhanh
 * bo chon suat. Doc vao tuong con mot luoi an toan o tang JS — trong khi luoi that nam trong
 * cau SQL.</p>
 *
 * <p>Ma chet kieu nay nguy hiem hon ma sai: no lam nguoi sua sau tin rang co hai lop bao ve, roi
 * bo lop SQL di ma khong biet lop JS chua bao gio chay.</p>
 */
@DisplayName("C.3 — khong con nhanh JS loc phong ngung hoat dong")
public class C3DeadRoomActiveBranchTest {

    private static final List<Path> PUBLIC_VIEWS = List.of(
            Path.of("src", "main", "webapp", "WEB-INF", "views", "booking", "page.jsp"),
            Path.of("src", "main", "webapp", "WEB-INF", "views", "film", "detail.jsp"),
            Path.of("src", "main", "webapp", "WEB-INF", "views", "showtime", "list.jsp"));

    @Test
    @DisplayName("khong JSP cong khai nao con doc isRoomActive")
    public void noPublicViewStillReadsTheFlag() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path view : PUBLIC_VIEWS) {
            String source = Files.readString(view, StandardCharsets.UTF_8);
            if (source.contains("isRoomActive") || source.contains("st.roomActive")) {
                offenders.add(view.toString());
            }
        }
        assertTrue(offenders.isEmpty(),
                "Con nhanh loc phong ngung hoat dong o: " + offenders
                + "\nSQL da loc phong inactive tu truoc, nen nhanh nay khong bao gio chay.");
    }

    @Test
    @DisplayName("khong con nut '(Ngưng)' / canh bao phong tam ngung o trang cong khai")
    public void noDeadDisabledSlotMarkupRemains() throws IOException {
        for (Path view : PUBLIC_VIEWS) {
            String source = Files.readString(view, StandardCharsets.UTF_8);
            if (source.contains("(Ngưng)") || source.contains("đang tạm ngưng hoạt động")) {
                fail(view + " con markup cho phong ngung hoat dong — nhanh sinh ra no da chet.");
            }
        }
    }

    @Test
    @DisplayName("Showtime.isRoomActive() phai GIU LAI — BookingPageServlet con dung that")
    public void theModelAccessorSurvives() {
        assertDoesNotThrow(() -> Showtime.class.getMethod("isRoomActive"),
                "BookingPageServlet:22 van goi isRoomActive() de chan mo trang dat ve cua suat"
                + " thuoc phong da ngung — day la chot THAT, khong duoc xoa theo.");
    }
}
