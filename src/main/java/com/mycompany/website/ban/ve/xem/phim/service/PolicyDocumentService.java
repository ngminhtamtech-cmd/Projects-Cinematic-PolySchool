package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.dao.PolicyDocumentDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcPolicyDocumentDAO;
import com.mycompany.website.ban.ve.xem.phim.model.PolicyDocument;
import java.util.Optional;

public class PolicyDocumentService {
    public static final String REFUND_POLICY = "refund-policy";
    public static final String TERMS_OF_USE = "terms-of-use";
    private final PolicyDocumentDAO dao;

    public PolicyDocumentService() { this(new JdbcPolicyDocumentDAO()); }
    public PolicyDocumentService(PolicyDocumentDAO dao) { this.dao = dao; }

    public PolicyDocument publishedRefundPolicy() {
        return dao.findPublished(REFUND_POLICY).orElseThrow(() -> new BookingException(503, "Chính sách hoàn tiền chưa sẵn sàng."));
    }
    public Optional<PolicyDocument> draftRefundPolicy() { return dao.findLatestDraft(REFUND_POLICY); }
    public PolicyDocument saveDraft(String title, String body, int actorId) { return dao.saveDraft(REFUND_POLICY, title, body, actorId); }
    public PolicyDocument publish(String title, String body, int actorId) { return dao.publish(REFUND_POLICY, title, body, actorId); }

    public PolicyDocument publishedTermsOfUse() {
        return dao.findPublished(TERMS_OF_USE).orElseGet(this::defaultTermsOfUse);
    }
    public Optional<PolicyDocument> draftTermsOfUse() { return dao.findLatestDraft(TERMS_OF_USE); }
    public PolicyDocument saveDraftTerms(String title, String body, int actorId) { return dao.saveDraft(TERMS_OF_USE, title, body, actorId); }
    public PolicyDocument publishTerms(String title, String body, int actorId) { return dao.publish(TERMS_OF_USE, title, body, actorId); }

    private PolicyDocument defaultTermsOfUse() {
        PolicyDocument doc = new PolicyDocument();
        doc.setPolicyKey(TERMS_OF_USE);
        doc.setVersionNumber(1);
        doc.setTitle("Thỏa Thuận Sử Dụng Trang Web An Toàn");
        doc.setBodyText("1. Quyền sở hữu và chấp nhận điều khoản: Khi truy cập và sử dụng hệ thống đặt vé trực tuyến CineBook, quý khách đồng ý tuân thủ các quy định và điều khoản sử dụng này.\n\n"
                + "2. An toàn tài khoản và thông tin cá nhân: Quý khách có trách nhiệm bảo mật thông tin tài khoản, mật khẩu và mã OTP. CineBook cam kết bảo vệ dữ liệu cá nhân theo tiêu chuẩn an toàn bảo mật hiện hành.\n\n"
                + "3. Quy định đặt vé và thanh toán: Mọi giao dịch mua vé, chọn ghế và thanh toán trực tuyến qua các cổng thanh toán hợp pháp đều được bảo mật. Quý khách vui lòng kiểm tra kỹ thông tin suất chiếu trước khi hoàn tất giao dịch.\n\n"
                + "4. Hành vi bị nghiêm cấm: Sử dụng công cụ tự động (bot, crawler), can thiệp hệ thống hoặc phát tán nội dung độc hại trên nền tảng CineBook bị nghiêm cấm hoàn toàn.\n\n"
                + "5. Quyền thay đổi điều khoản: CineBook có quyền cập nhật, bổ sung nội dung Thỏa thuận sử dụng này để phù hợp với quy định pháp luật và nâng cao chất lượng dịch vụ.");
        doc.setStatus("published");
        doc.setUpdatedAt(java.time.LocalDateTime.now());
        doc.setPublishedAt(java.time.LocalDateTime.now());
        return doc;
    }
}
