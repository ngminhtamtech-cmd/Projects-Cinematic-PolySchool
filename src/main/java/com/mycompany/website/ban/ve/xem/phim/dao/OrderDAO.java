package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.model.OrderComboItem;
import com.mycompany.website.ban.ve.xem.phim.model.OrderHoldStatus;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.OrderSeatItem;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface OrderDAO {
    int insertDraftOrder(Connection connection, OrderRecord order);
    void insertOrderSeats(Connection connection, int orderId, List<OrderSeatItem> seats);
    void insertOrderCombos(Connection connection, int orderId, List<OrderComboItem> combos);
    Optional<OrderRecord> findPendingOrderForUpdate(Connection connection, int orderId, int userId);
    void markPaid(Connection connection, int orderId, String ticketCode, String ticketQrUrl, String transactionId);
    void markPaid(Connection connection, int orderId, String ticketCode, String ticketQrUrl, String transactionId, String paymentProvider);
    void deleteOrderCombos(Connection connection, int orderId);
    void updateOrderFinancials(Connection connection, int orderId, java.math.BigDecimal seatSubtotal, java.math.BigDecimal comboSubtotal, java.math.BigDecimal discountAmount, java.math.BigDecimal totalAmount, Integer promotionId, String paymentMethod);
    /**
     * Chot don "thanh toan tai quay": giu ghe, chua thu tien, va <b>dat han thu tien</b>.
     *
     * <p>{@code counterExpiryMinutes} sinh ra {@code CounterExpiresAt} bang gio DB. Truoc day cot
     * nay khong bao gio duoc ghi, nen vong quet cua {@code HoldSweeper} (dieu kien
     * {@code CounterExpiresAt IS NOT NULL}) khong bao gio khop va don tai quay khong het han —
     * ghe bi giu vo thoi han (CB-ISS-004/A3).</p>
     */
    void confirmCounterOrder(Connection connection, int orderId, String ticketCode, String ticketQrUrl,
            int counterExpiryMinutes);
    List<OrderRecord> findHistoryByUserId(int userId);
    void hideOrderFromUserHistory(int orderId, int userId);
    void hideBatchOrdersFromUserHistory(List<Integer> orderIds, int userId);
    void hideAllHistoryOrdersFromUserHistory(int userId);
    void redeemTicketByCode(String ticketCode);
    void cancelOrderById(int orderId);
    Optional<OrderRecord> findById(int id);
    Optional<OrderRecord> findByIdempotencyKey(Connection connection, String idempotencyKey);
    Optional<OrderRecord> findByIdempotencyKey(String idempotencyKey);

    /** Don theo id <b>va</b> chu don, doc bang connection cua transaction dang chay. */
    Optional<OrderRecord> findByIdAndUserId(Connection connection, int orderId, int userId);

    /**
     * Gan khoa idempotency cua lan thanh toan vao don (BUG-02).
     *
     * <p>Chi ghi khi cot dang NULL. Don co the da mang khoa cua buoc <b>tao don</b> — de idempotency
     * cua buoc tao con hoat dong thi khoa do khong duoc de len. Tra ve {@code true} neu vua ghi.</p>
     */
    boolean applyIdempotencyKey(Connection connection, int orderId, String idempotencyKey);
    List<OrderSeatItem> findSeatsByOrderId(int orderId);
    List<OrderSeatItem> findSeatsByOrderId(Connection connection, int orderId);
    List<OrderComboItem> findCombosByOrderId(int orderId);
    List<OrderComboItem> findCombosByOrderId(Connection connection, int orderId);
    Optional<OrderRecord> findByTicketCodeAndUserId(String ticketCode, int userId);
    Optional<OrderRecord> findByTicketCode(String ticketCode);
    void updateOrderRefund(Connection connection, int orderId, java.math.BigDecimal refundAmount, String reason, int refundedBy);
    void updateOrderCancel(Connection connection, int orderId, String reason);

    /**
     * Trang thai giu ghe that cua don, kem so giay con lai do <b>SQL Server</b> tinh.
     * Rang buoc {@code UserId} nam trong cau lenh nen khong the xem don cua nguoi khac.
     */
    Optional<OrderHoldStatus> findHoldStatus(int orderId, int userId);
}
