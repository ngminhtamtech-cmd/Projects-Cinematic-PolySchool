package com.mycompany.website.ban.ve.xem.phim.controller.staff;

import com.mycompany.website.ban.ve.xem.phim.controller.BasePortalServlet;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.StaffService;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Man hinh quay ve: soat ve / check-in va thu tien tai quay.
 *
 * <p>Quyen truy cap do {@code AuthFilter} chan o tien to {@code /staff} (yeu cau vai tro
 * {@code staff} tro len), servlet nay khong tu kiem tra lai.</p>
 *
 * <p>Controller mong: khong chua nghiep vu, moi quyet dinh nam o {@link StaffService}.</p>
 */
public class StaffPortalServlet extends BasePortalServlet {
    private static final long serialVersionUID = 1L;

    private final StaffService staffService = new StaffService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String ticketCode = ServletUtil.param(request, "ticketCode");
        if (!ticketCode.isBlank()) {
            // BUG-07: tra cuu phai gioi han trong cum rap cua nhan vien dang dang nhap.
            request.setAttribute("lookup", staffService.lookupTicket(ticketCode, currentUser(request)));
            request.setAttribute("ticketCode", ticketCode);
        }
        forward(request, response, "/WEB-INF/views/staff/checkin.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User actor = currentUser(request);
        String action = ServletUtil.param(request, "action");
        String ticketCode = ServletUtil.param(request, "ticketCode");

        StaffService.TicketLookup result = null;
        try {
            if ("redeem".equals(action)) {
                // Giu lai ket qua: checkIn() tra ve CHECKED_IN chinh vi muc dich nay.
                result = staffService.checkIn(ticketCode, actor);
                ServletUtil.flashSuccess(request,
                        "✅ Đã check-in vé " + ticketCode + ". Mời khách vào phòng chiếu.");
            } else if ("markPaid".equals(action)) {
                staffService.collectPayment(intParam(request, "orderId", -1), actor);
                ServletUtil.flashSuccess(request,
                        "💵 Đã xác nhận thu tiền tại quầy. Khách đã được cộng điểm loyalty.");
            }
        } catch (BookingException ex) {
            ServletUtil.flashError(request, ex.getMessage());
        }

        // Luon tra cuu lai sau thao tac de nhan vien thay ngay trang thai moi — TRU truong hop
        // vua check-in thanh cong.
        //
        // Loi da sua: ban cu vut bo ket qua cua checkIn() roi goi lookupTicket() lai, ma lan tra
        // cuu thu hai chi thay mot don da 'redeemed' nen tra ve ALREADY_USED. Nhan vien thay dong
        // thoi bang xanh "✅ Đã check-in" va bang do "⛔ Vé đã sử dụng" cho cung mot thao tac —
        // dung tinh huong ma verdict CHECKED_IN duoc tao ra de tranh (xem javadoc cua
        // StaffService.Verdict.CHECKED_IN), va cung la ly do verdict do khong bao gio hien ra
        // duoc tren giao dien.
        if (result != null) {
            request.setAttribute("lookup", result);
            request.setAttribute("ticketCode", ticketCode);
        } else if (!ticketCode.isBlank()) {
            request.setAttribute("lookup", staffService.lookupTicket(ticketCode, actor));
            request.setAttribute("ticketCode", ticketCode);
        }
        forward(request, response, "/WEB-INF/views/staff/checkin.jsp");
    }
}
