package com.mycompany.website.ban.ve.xem.phim.api.v1;

import com.mycompany.website.ban.ve.xem.phim.api.ApiException;
import com.mycompany.website.ban.ve.xem.phim.api.BaseApiServlet;
import com.mycompany.website.ban.ve.xem.phim.api.dto.DtoMapper;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * {@code GET /api/v1/orders/{id}/hold} — han giu ghe that cua don, do SQL Server tinh.
 *
 * <p>Ton tai de sua B3: dong ho dem nguoc o buoc 3-4 cua trang dat ve truoc day chay
 * doc lap 600 giay, khong doc {@code HeldUntil} cua DB lan nao. Khach F5 lai trang la dong ho
 * chay lai tu dau du ghe sap het han; nguoc lai sweeper co the da thu ghe ma UI van dem tiep.</p>
 *
 * <p>Rang buoc {@code UserId} nam trong cau lenh SQL nen khong xem duoc don cua nguoi khac
 * (tra 404, khong phai 403 — khong xac nhan don do co ton tai hay khong).</p>
 */
public class OrderApiServlet extends BaseApiServlet {
    private final BookingService bookingService = new BookingService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] segments = segments(request);
        int orderId = requireIntSegment(segments, 0, "orderId");
        if (segments.length < 2 || !"hold".equals(segments[1])) {
            throw ApiException.notFound("Khong tim thay tai nguyen con.");
        }
        User viewer = requireUser(request);
        writeData(response, DtoMapper.holdStatus(bookingService.getHoldStatus(orderId, viewer.getId())));
    }
}
