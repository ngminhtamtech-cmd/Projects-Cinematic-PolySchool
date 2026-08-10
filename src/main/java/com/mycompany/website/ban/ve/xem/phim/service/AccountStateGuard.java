package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.dao.UserDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserDAO;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.model.UserAuthState;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Doi chieu phien dang chay voi trang thai that trong DB (D5).
 *
 * <p>Truoc P10, doi tuong {@link User} duoc cache trong session ngay luc dang nhap va khong bao gio
 * duoc doc lai. Hau qua: khoa mot tai khoan <b>khong</b> cat duoc phien dang chay cua no —
 * nguoi bi khoa cu the dat ve, binh luan, thao tac admin cho toi khi tu dang xuat.</p>
 *
 * <h2>Vi sao co cache</h2>
 * <p>Doc DB o <b>moi</b> request se them mot query cho tung anh, tung file CSS di qua filter —
 * dung vao vet thuong F2 ma P16 sap phai chua. Nen ket qua duoc nho {@value #CACHE_TTL_MS} ms
 * cho moi userId. Doi lai la <b>do tre toi da 30 giay</b> ke tu luc khoa den luc phien bi cat;
 * day la danh doi co chu y, khong phai so sot.</p>
 *
 * <p>Thao tac khoa/xoa tai khoan nen goi {@link #invalidate(int)} de cat ngay lap tuc thay vi cho
 * het 30 giay.</p>
 */
public class AccountStateGuard {
    private static final Logger LOG = Logger.getLogger(AccountStateGuard.class.getName());

    /** Thoi gian nho ket qua kiem tra cho moi nguoi dung. */
    public static final long CACHE_TTL_MS = 30_000L;

    private static final ConcurrentHashMap<Integer, CachedVerdict> CACHE = new ConcurrentHashMap<>();

    private final UserDAO userDAO;

    public AccountStateGuard() {
        this(new JdbcUserDAO());
    }

    public AccountStateGuard(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /** Ket luan ve mot phien. */
    public enum Verdict {
        /** Trang thai trong DB van khop voi phien. */
        OK("", false),
        /** Tai khoan da bi khoa. */
        LOCKED("Tài khoản của bạn đã bị khóa.", true),
        /** Tai khoan da bi xoa. */
        DELETED("Tài khoản của bạn không còn hiệu lực.", true),
        /** Vai tro hoac pham vi rap da doi — phien cu mang quyen cu, phai dang nhap lai. */
        CHANGED("Quyền của tài khoản đã thay đổi, vui lòng đăng nhập lại.", true),
        /** Khong xac minh duoc live state; route bao ve phai tam dung, nhung khong logout user. */
        UNAVAILABLE("Dịch vụ xác thực tạm thời không khả dụng. Vui lòng thử lại sau.", false);

        private final String message;
        private final boolean revoked;

        Verdict(String message, boolean revoked) {
            this.message = message;
            this.revoked = revoked;
        }

        public String getMessage() {
            return message;
        }

        public boolean isRevoked() {
            return revoked;
        }

        public boolean isUnavailable() {
            return this == UNAVAILABLE;
        }
    }

    /**
     * Phien nay con duoc chay tiep khong.
     *
     * <p>Khi DB loi, ham tra ve {@link Verdict#UNAVAILABLE}. Ket qua nay khong bi cache va khong
     * thu hoi phien; {@code AuthFilter} se dung route bao ve voi HTTP 503. Nhu vay he thong khong
     * fail-open, dong thoi cung khong bien su co DB thanh su kien logout hang loat.</p>
     */
    public Verdict evaluate(User sessionUser) {
        if (sessionUser == null) {
            return Verdict.OK;
        }

        int userId = sessionUser.getId();
        long now = System.currentTimeMillis();
        CachedVerdict cached = CACHE.get(userId);
        if (cached != null && now - cached.checkedAtMs < CACHE_TTL_MS) {
            return cached.verdict;
        }

        Verdict verdict;
        try {
            Optional<UserAuthState> state = userDAO.findAuthState(userId);
            if (state.isEmpty()) {
                verdict = Verdict.DELETED;
            } else if (state.get().isDeleted()) {
                verdict = Verdict.DELETED;
            } else if (state.get().isLocked()) {
                verdict = Verdict.LOCKED;
            } else if (!state.get().matchesSession(sessionUser)) {
                verdict = Verdict.CHANGED;
            } else {
                verdict = Verdict.OK;
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING,
                    "AccountStateGuard: khong doc duoc trang thai userId=" + userId
                            + "; route bao ve se tam dung voi HTTP 503. Nguyen nhan: {0}",
                    ex.toString());
            return Verdict.UNAVAILABLE;
        }

        if (verdict.isRevoked()) {
            LOG.info("AccountStateGuard: cat phien userId=" + userId + " — ly do " + verdict.name() + ".");
        }
        CACHE.put(userId, new CachedVerdict(verdict, now));
        return verdict;
    }

    /** Buoc lan kiem tra ke tiep cua mot nguoi dung phai doc lai DB. */
    public static void invalidate(int userId) {
        CACHE.remove(userId);
    }

    /** Xoa toan bo cache — dung cho test va khi can cat phien tuc thi tren dien rong. */
    public static void clearCache() {
        CACHE.clear();
    }

    private static final class CachedVerdict {
        private final Verdict verdict;
        private final long checkedAtMs;

        private CachedVerdict(Verdict verdict, long checkedAtMs) {
            this.verdict = verdict;
            this.checkedAtMs = checkedAtMs;
        }
    }
}
