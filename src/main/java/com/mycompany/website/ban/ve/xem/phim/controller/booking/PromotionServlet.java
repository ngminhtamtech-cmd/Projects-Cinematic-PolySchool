package com.mycompany.website.ban.ve.xem.phim.controller.booking;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import com.mycompany.website.ban.ve.xem.phim.util.JsonUtil;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class PromotionServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(PromotionServlet.class.getName());
    private final BookingService bookingService = new BookingService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String code = request.getParameter("code");
        User currentUser = (User) request.getSession().getAttribute(AppConstants.SESSION_USER);
        try {
            Promotion promotion = bookingService.resolvePromotion(
                    code, currentUser == null ? -1 : currentUser.getId());
            if (promotion == null) {
                JsonUtil.write(response, Map.of("error", true, "message", "Ma khuyen mai khong hop le."));
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", false);
            body.put("code", promotion.getUserVoucherId() == null ? promotion.getCode() : code);
            body.put("discountPercent", promotion.getDiscountPercent());
            body.put("maxDiscount", promotion.getMaxDiscount());
            JsonUtil.write(response, body);
        } catch (BookingException ex) {
            JsonUtil.write(response, Map.of("error", true, "message", ex.getMessage()));
        } catch (RuntimeException ex) {
            // Fail-safe: khong ap ma nao ca (khach tra nguyen gia), nhung phai ghi log kem ngu canh —
            // truoc day loi he thong bien thanh mot dong JSON roi mat hut.
            LOGGER.log(Level.WARNING, "Khong kiem tra duoc ma khuyen mai", ex);
            JsonUtil.write(response, Map.of(
                    "error", true, "message", "Loi he thong khi kiem tra ma khuyen mai."));
        }
    }
}
