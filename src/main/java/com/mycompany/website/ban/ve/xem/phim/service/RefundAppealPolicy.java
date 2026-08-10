package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.config.SettingsReader;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import java.time.LocalDateTime;

/** One shared policy for the refund-appeal form, history UI and transactional write path. */
public final class RefundAppealPolicy {
    public static final String WINDOW_SETTING = "refund.appealWindowHours";
    public static final int DEFAULT_WINDOW_HOURS = 24;
    public static final String EXPIRED_MESSAGE =
            "Bạn không đủ điều kiện hoàn tiền vì đã vượt quá 1 ngày gửi kháng cáo!";

    private RefundAppealPolicy() {
    }

    public enum Status {
        ELIGIBLE, NOT_ENDED, EXPIRED, PENDING, REJECTED, REFUNDED, REDEEMED, INVALID_STATE
    }

    public record Evaluation(Status status, String message) {
        public boolean eligible() {
            return status == Status.ELIGIBLE;
        }
    }

    public static int windowHours() {
        return SettingsReader.readInt(WINDOW_SETTING, DEFAULT_WINDOW_HOURS, 1, 168);
    }

    public static Evaluation evaluate(OrderRecord order) {
        return evaluate(order, order != null && order.isPendingRefundAppeal(), windowHours());
    }

    static Evaluation evaluate(OrderRecord order, boolean pending, int windowHours) {
        if (order == null || !order.isPaidConfirmed()) {
            return new Evaluation(Status.INVALID_STATE, "Vé không ở trạng thái hợp lệ để yêu cầu hoàn tiền.");
        }
        if (order.isRefunded()) {
            return new Evaluation(Status.REFUNDED, "Vé này đã được hoàn tiền.");
        }
        if (order.getRefundRejectedAt() != null) {
            return new Evaluation(Status.REJECTED, "Yêu cầu hoàn tiền cho vé này đã bị từ chối.");
        }
        if (order.getRedeemedAt() != null || order.isRedeemed()) {
            return new Evaluation(Status.REDEEMED, "Vé đã được check-in nên không thể xin hoàn tiền.");
        }
        if (pending) {
            return new Evaluation(Status.PENDING, "Mã vé này đã có yêu cầu hoàn tiền đang chờ xử lý.");
        }
        LocalDateTime endTime = order.getEndTime();
        if (endTime == null || order.getBusinessNow().isBefore(endTime)) {
            return new Evaluation(Status.NOT_ENDED, "Chỉ gửi yêu cầu sau khi suất chiếu đã kết thúc.");
        }
        if (order.getBusinessNow().isAfter(endTime.plusHours(windowHours))) {
            return new Evaluation(Status.EXPIRED, EXPIRED_MESSAGE);
        }
        return new Evaluation(Status.ELIGIBLE, "Có thể gửi yêu cầu hoàn tiền.");
    }
}
