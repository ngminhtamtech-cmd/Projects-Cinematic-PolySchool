package com.mycompany.website.ban.ve.xem.phim.api.v1;

import com.mycompany.website.ban.ve.xem.phim.api.ApiException;
import com.mycompany.website.ban.ve.xem.phim.api.BaseApiServlet;
import com.mycompany.website.ban.ve.xem.phim.api.dto.DtoMapper;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.StaffService;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Endpoint cua quay ve, phuc vu tang Next.js o GD 4.
 *
 * <ul>
 *   <li>{@code GET  /api/v1/staff/tickets/{code}} - tra cuu ve + cham diem check-in</li>
 *   <li>{@code POST /api/v1/staff/tickets/{code}/redeem} - check-in ve</li>
 *   <li>{@code POST /api/v1/staff/orders/{id}/markPaid} - thu tien tai quay</li>
 * </ul>
 *
 * <p>Dung chung {@link StaffService} voi portal JSP nen luat khung gio check-in la MOT,
 * khong bi lech giua hai tang. Moi thao tac ghi da nam trong pham vi {@code CsrfFilter}
 * (web.xml map san {@code /api/v1/*}) nen bat buoc co header {@code X-CSRF-Token}.</p>
 */
public class StaffApiServlet extends BaseApiServlet {
    private final StaffService staffService = new StaffService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User actor = requireStaff(request);
        String[] segments = segments(request);

        if (segments.length == 2 && "tickets".equals(segments[0])) {
            // BUG-07: duong doc phai chiu cung pham vi cum rap nhu duong ghi.
            writeData(response, DtoMapper.staffLookup(staffService.lookupTicket(segments[1], actor)));
            return;
        }
        throw ApiException.notFound("Khong tim thay endpoint.");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User actor = requireStaff(request);
        String[] segments = segments(request);

        if (segments.length == 3 && "tickets".equals(segments[0]) && "redeem".equals(segments[2])) {
            // Khung gio + moi kiem tra khac do StaffService/AdminService dam nhiem;
            // loi nem ra duoc BaseApiServlet dich sang HTTP status tuong ung.
            // checkIn tra ve verdict CHECKED_IN (khong phai ALREADY_USED) de client
            // phan biet duoc "vua check-in xong" voi "ve nay nguoi khac dung roi".
            writeData(response, DtoMapper.staffLookup(staffService.checkIn(segments[1], actor)));
            return;
        }

        if (segments.length == 3 && "orders".equals(segments[0]) && "markPaid".equals(segments[2])) {
            int orderId;
            try {
                orderId = Integer.parseInt(segments[1]);
            } catch (NumberFormatException ex) {
                throw ApiException.badRequest("Ma don phai la so nguyen.");
            }
            staffService.collectPayment(orderId, actor);
            writeNoContent(response);
            return;
        }

        throw ApiException.notFound("Khong tim thay endpoint.");
    }
}
