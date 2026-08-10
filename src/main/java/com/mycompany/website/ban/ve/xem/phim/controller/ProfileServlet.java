package com.mycompany.website.ban.ve.xem.phim.controller;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.config.SecuritySettings;
import com.mycompany.website.ban.ve.xem.phim.dao.UserDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserDAO;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.PersonalDataService;
import com.mycompany.website.ban.ve.xem.phim.util.JsonUtil;
import com.mycompany.website.ban.ve.xem.phim.util.PasswordPolicy;
import com.mycompany.website.ban.ve.xem.phim.util.PasswordUtil;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ProfileServlet extends HttpServlet {
    private final UserDAO userDAO = new JdbcUserDAO();
    private final PersonalDataService personalDataService = new PersonalDataService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getSession().getAttribute(AppConstants.SESSION_USER);
        if ("/export".equals(request.getPathInfo())) {
            if (currentUser == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"cinebook-personal-data-" + currentUser.getId() + ".json\"");
            response.getWriter().write(JsonUtil.toJson(personalDataService.exportForUser(currentUser.getId())));
            return;
        }
        ServletUtil.consumeFlash(request);
        request.getRequestDispatcher("/WEB-INF/views/member/profile.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getSession().getAttribute(AppConstants.SESSION_USER);
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String action = ServletUtil.param(request, "action");
        if ("password".equals(action)) {
            changePassword(request, response, currentUser);
            return;
        }
        if ("delete".equals(action)) {
            deleteAccount(request, response, currentUser);
            return;
        }
        updateProfile(request, response, currentUser);
    }

    private void deleteAccount(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws IOException {
        String password = ServletUtil.param(request, "currentPassword");
        String confirmation = ServletUtil.param(request, "confirmation");
        if (!"XOA TAI KHOAN".equals(confirmation)
                || !PasswordUtil.matches(password, currentUser.getPasswordHash())) {
            ServletUtil.flashError(request, "Mật khẩu hoặc cụm từ xác nhận không đúng.");
            response.sendRedirect(request.getContextPath() + "/profile");
            return;
        }
        personalDataService.anonymize(currentUser.getId(), currentUser.getEmail());
        request.getSession().invalidate();
        response.sendRedirect(request.getContextPath() + "/login?accountDeleted=1");
    }

    private void updateProfile(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws IOException {
        String fullName = ServletUtil.param(request, "fullName");
        if (fullName.isBlank()) {
            ServletUtil.flashError(request, "Ho ten khong duoc de trong.");
            response.sendRedirect(request.getContextPath() + "/profile");
            return;
        }

        String avatarParam = ServletUtil.param(request, "avatar");
        String avatarUrl = avatarParam;

        if (avatarParam != null && avatarParam.startsWith("data:image/")) {
            try {
                int commaIndex = avatarParam.indexOf(',');
                if (commaIndex > 0) {
                    String header = avatarParam.substring(0, commaIndex);
                    String base64Data = avatarParam.substring(commaIndex + 1);

                    String ext = "png";
                    if (header.contains("jpeg") || header.contains("jpg")) {
                        ext = "jpg";
                    } else if (header.contains("webp")) {
                        ext = "webp";
                    }

                    byte[] imageBytes = Base64.getDecoder().decode(base64Data.trim());
                    if (imageBytes.length > 5 * 1024 * 1024) {
                        ServletUtil.flashError(request, "Dung luong anh tai len qua lon (toi da 5MB).");
                        response.sendRedirect(request.getContextPath() + "/profile");
                        return;
                    }

                    String fileName = "avatar-" + currentUser.getId() + "-" + System.currentTimeMillis() + "." + ext;
                    Path uploadDir = UploadServlet.uploadDirectory();
                    Files.createDirectories(uploadDir);
                    Path targetFile = uploadDir.resolve(fileName);
                    Files.write(targetFile, imageBytes);

                    avatarUrl = request.getContextPath() + "/uploads/" + fileName;
                }
            } catch (Exception ex) {
                ServletUtil.flashError(request, "Khong the luu tep anh tai len.");
                response.sendRedirect(request.getContextPath() + "/profile");
                return;
            }
        } else if (avatarUrl != null && avatarUrl.length() > 255) {
            ServletUtil.flashError(request, "Duong dan URL anh qua dai (toi da 255 ky tu).");
            response.sendRedirect(request.getContextPath() + "/profile");
            return;
        }

        User update = new User();
        update.setId(currentUser.getId());
        update.setUsername(ServletUtil.param(request, "username"));
        update.setFullName(fullName);
        update.setPhone(ServletUtil.param(request, "phone"));
        update.setAddress(ServletUtil.param(request, "address"));
        update.setAvatar(avatarUrl);

        if (!userDAO.updateProfile(update)) {
            ServletUtil.flashError(request, "Khong the cap nhat thong tin ca nhan.");
            response.sendRedirect(request.getContextPath() + "/profile");
            return;
        }

        currentUser.setUsername(update.getUsername());
        currentUser.setFullName(update.getFullName());
        currentUser.setPhone(update.getPhone());
        currentUser.setAddress(update.getAddress());
        currentUser.setAvatar(update.getAvatar());
        request.getSession().setAttribute(AppConstants.SESSION_USER, currentUser);
        ServletUtil.flashSuccess(request, "Da cap nhat thong tin ca nhan.");
        response.sendRedirect(request.getContextPath() + "/profile");
    }

    private void changePassword(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws IOException {
        String currentPassword = ServletUtil.param(request, "currentPassword");
        String newPassword = ServletUtil.param(request, "newPassword");
        String confirmPassword = ServletUtil.param(request, "confirmPassword");

        if (!PasswordUtil.matches(currentPassword, currentUser.getPasswordHash())) {
            ServletUtil.flashError(request, "Mat khau hien tai khong dung.");
            response.sendRedirect(request.getContextPath() + "/profile");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            ServletUtil.flashError(request, "Mat khau xac nhan khong khop.");
            response.sendRedirect(request.getContextPath() + "/profile");
            return;
        }
        // D10 — doi mat khau la "mat khau moi", nen phai qua cung mot chinh sach voi dang ky va
        // dat lai mat khau. Bo qua o day thi nguoi dung chi can vao /profile la ha duoc do manh.
        PasswordPolicy.Result policy = PasswordPolicy.validate(
                newPassword, SecuritySettings.passwordMinLength(), currentUser.getEmail());
        if (!policy.isValid()) {
            ServletUtil.flashError(request, policy.getMessage());
            response.sendRedirect(request.getContextPath() + "/profile");
            return;
        }

        String hash = PasswordUtil.hash(newPassword);
        if (!userDAO.changePassword(currentUser.getId(), hash)) {
            ServletUtil.flashError(request, "Khong the doi mat khau luc nay.");
            response.sendRedirect(request.getContextPath() + "/profile");
            return;
        }

        Optional<User> refreshedUser = userDAO.findById(currentUser.getId());
        refreshedUser.ifPresent(user -> request.getSession().setAttribute(AppConstants.SESSION_USER, user));
        ServletUtil.flashSuccess(request, "Da doi mat khau thanh cong.");
        response.sendRedirect(request.getContextPath() + "/profile");
    }
}
