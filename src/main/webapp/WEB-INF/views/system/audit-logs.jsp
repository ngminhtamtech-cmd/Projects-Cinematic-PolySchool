<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Audit log - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
</head>
<body class="admin-body">
    <div class="dashboard">
        <%@ include file="/WEB-INF/views/system/sidebar.jspf" %>
        <main class="dashboard-main">
            <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
            <div class="dashboard-content">
                <%@ include file="/WEB-INF/views/shared/flash.jspf" %>
                
                <div class="portal-head">
                    <div>
                        <h1>Nhật ký hệ thống (Audit Logs)</h1>
                        <p class="muted">Theo dõi và kiểm tra lịch sử các thao tác nhạy cảm, thay đổi dữ liệu của Admin và Manager.</p>
                    </div>
                </div>

                <section class="overview-system-grid" aria-label="Số liệu nhật ký hệ thống">
                    <div class="overview-metric-card"><span>Tổng sự kiện</span><strong>${fn:escapeXml(auditPage.totalItems)}</strong><small>Toàn bộ bản ghi thực tế</small></div>
                    <div class="overview-metric-card"><span>Đang hiển thị</span><strong>${fn:length(logs)}</strong><small>Số dòng trên trang này</small></div>
                    <div class="overview-metric-card"><span>Trang hiện tại</span><strong>${fn:escapeXml(auditPage.page)}</strong><small>Trong ${fn:escapeXml(auditPage.totalPages)} trang</small></div>
                    <div class="overview-metric-card"><span>Phạm vi</span><strong>Audit</strong><small>Dữ liệu hệ thống đã ghi nhận</small></div>
                </section>

                <section class="portal-grid" style="grid-template-columns: 1fr 3fr; gap: 20px;">
                    <article class="panel" style="height: fit-content;">
                        <h2>Dọn dẹp logs cũ</h2>
                        <p class="muted" style="margin-bottom:12px; font-size:13px;">Dọn dẹp tự động các dòng log đã cũ để tối ưu dung lượng cơ sở dữ liệu.</p>
                        <form method="post" action="${pageContext.request.contextPath}/system/audit-logs" class="form-grid">
                            <cb:csrf/>
                            <label>Dọn log cũ hơn (số ngày)
                                <input type="number" name="olderThanDays" min="1" value="90" required>
                            </label>
                            <div class="form-actions" style="margin-top: 12px;">
                                <button class="danger" type="submit" onclick="return confirm('Dọn dẹp audit log cũ?')">Tiến hành dọn dẹp</button>
                            </div>
                        </form>
                    </article>

                    <section class="panel">
                        <h2>Lịch sử hoạt động</h2>
                        <div class="table-wrap">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>Thời gian</th>
                                        <th>Tác nhân (Actor)</th>
                                        <th>Hành động (Action)</th>
                                        <th>Đối tượng (Target)</th>
                                        <th>Chi tiết dữ liệu (Detail)</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="log" items="${logs}">
                                        <tr>
                                            <td class="mono muted" style="font-size: 12px; white-space: nowrap;">${fn:escapeXml(log.createdAtDisplay)}</td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${not empty log.actorEmail}">
                                                        <strong>${fn:escapeXml(log.actorEmail)}</strong>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span class="status-pill info">System</span>
                                                    </c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td style="white-space: nowrap;">
                                                <c:choose>
                                                    <c:when test="${log.action eq 'REFUND_ORDER'}"><span class="audit-action-badge action">Hoàn tiền đơn vé</span></c:when>
                                                    <c:when test="${log.action eq 'REDEEM_TICKET'}"><span class="audit-action-badge action">Check-in (Soát vé)</span></c:when>
                                                    <c:when test="${log.action eq 'CANCEL_ORDER'}"><span class="audit-action-badge delete">Hủy đơn hàng</span></c:when>
                                                    <c:when test="${log.action eq 'MARK_COUNTER_PAID'}"><span class="audit-action-badge update">Thanh toán đơn quầy</span></c:when>
                                                    <c:when test="${log.action eq 'CREATE_COMBO'}"><span class="audit-action-badge create">Thêm mới Combo</span></c:when>
                                                    <c:when test="${log.action eq 'UPDATE_COMBO' or log.action eq 'UPDATE_COMBO_STATUS'}"><span class="audit-action-badge update">Cập nhật Combo</span></c:when>
                                                    <c:when test="${log.action eq 'DELETE_COMBO'}"><span class="audit-action-badge delete">Xóa Combo</span></c:when>
                                                    <c:when test="${log.action eq 'CREATE_FILM'}"><span class="audit-action-badge create">Thêm mới phim</span></c:when>
                                                    <c:when test="${log.action eq 'UPDATE_FILM'}"><span class="audit-action-badge update">Cập nhật phim</span></c:when>
                                                    <c:when test="${log.action eq 'DELETE_FILM'}"><span class="audit-action-badge delete">Xóa bộ phim</span></c:when>
                                                    <c:when test="${log.action eq 'EXTEND_FILM_ENDDATE'}"><span class="audit-action-badge update">Gia hạn chiếu phim</span></c:when>
                                                    <c:when test="${log.action eq 'CREATE_SHOWTIME'}"><span class="audit-action-badge create">Thêm suất chiếu</span></c:when>
                                                    <c:when test="${log.action eq 'UPDATE_SHOWTIME'}"><span class="audit-action-badge update">Chỉnh sửa suất chiếu</span></c:when>
                                                    <c:when test="${log.action eq 'DELETE_SHOWTIME'}"><span class="audit-action-badge delete">Xóa suất chiếu</span></c:when>
                                                    <c:when test="${log.action eq 'CREATE_ROOM'}"><span class="audit-action-badge create">Thêm phòng chiếu</span></c:when>
                                                    <c:when test="${log.action eq 'UPDATE_ROOM' or log.action eq 'UPDATE_SEAT_LAYOUT'}"><span class="audit-action-badge update">Cập nhật phòng chiếu</span></c:when>
                                                    <c:when test="${log.action eq 'DELETE_ROOM' or log.action eq 'SOFT_DELETE_ROOM'}"><span class="audit-action-badge delete">Xóa phòng chiếu</span></c:when>
                                                    <c:when test="${log.action eq 'CREATE_CINEMA'}"><span class="audit-action-badge create">Thêm mới rạp</span></c:when>
                                                    <c:when test="${log.action eq 'UPDATE_CINEMA'}"><span class="audit-action-badge update">Cập nhật rạp</span></c:when>
                                                    <c:when test="${log.action eq 'CREATE_PROMOTION'}"><span class="audit-action-badge create">Thêm mã khuyến mãi</span></c:when>
                                                    <c:when test="${log.action eq 'UPDATE_PROMOTION'}"><span class="audit-action-badge update">Cập nhật khuyến mãi</span></c:when>
                                                    <c:when test="${log.action eq 'APPROVE_APPEAL'}"><span class="audit-action-badge create">Duyệt đơn kháng cáo</span></c:when>
                                                    <c:when test="${log.action eq 'REJECT_APPEAL'}"><span class="audit-action-badge delete">Từ chối kháng cáo</span></c:when>
                                                    <c:when test="${log.action eq 'LOCK_USER'}"><span class="audit-action-badge delete">Khóa tài khoản</span></c:when>
                                                    <c:when test="${log.action eq 'UNLOCK_USER'}"><span class="audit-action-badge action">Mở khóa tài khoản</span></c:when>
                                                    <c:when test="${log.action eq 'WARN_USER_COMMENT'}"><span class="audit-action-badge warning">Cảnh cáo bình luận</span></c:when>
                                                    <c:when test="${log.action eq 'CLEAR_AUDIT_LOGS'}"><span class="audit-action-badge delete">Dọn dẹp nhật ký</span></c:when>
                                                    <c:when test="${log.action eq 'SAVE_SETTING'}"><span class="audit-action-badge update">Thay đổi cấu hình</span></c:when>
                                                    <c:when test="${fn:startsWith(log.action, 'CREATE_')}"><span class="audit-action-badge create">${fn:escapeXml(log.action)}</span></c:when>
                                                    <c:when test="${fn:startsWith(log.action, 'UPDATE_')}"><span class="audit-action-badge update">${fn:escapeXml(log.action)}</span></c:when>
                                                    <c:when test="${fn:startsWith(log.action, 'DELETE_')}"><span class="audit-action-badge delete">${fn:escapeXml(log.action)}</span></c:when>
                                                    <c:otherwise><span class="audit-action-badge action">${fn:escapeXml(log.action)}</span></c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td style="white-space: nowrap;">
                                                <c:choose>
                                                    <c:when test="${log.targetType eq 'Order'}"><span class="target-chip">Đơn hàng #${fn:escapeXml(log.targetId)}</span></c:when>
                                                    <c:when test="${log.targetType eq 'Film'}"><span class="target-chip">Phim #${fn:escapeXml(log.targetId)}</span></c:when>
                                                    <c:when test="${log.targetType eq 'Showtime'}"><span class="target-chip">Suất #${fn:escapeXml(log.targetId)}</span></c:when>
                                                    <c:when test="${log.targetType eq 'User'}"><span class="target-chip">Thành viên #${fn:escapeXml(log.targetId)}</span></c:when>
                                                    <c:when test="${log.targetType eq 'ComboFood'}"><span class="target-chip">Combo #${fn:escapeXml(log.targetId)}</span></c:when>
                                                    <c:when test="${log.targetType eq 'Cinema'}"><span class="target-chip">Rạp #${fn:escapeXml(log.targetId)}</span></c:when>
                                                    <c:when test="${log.targetType eq 'Room'}"><span class="target-chip">Phòng #${fn:escapeXml(log.targetId)}</span></c:when>
                                                    <c:when test="${log.targetType eq 'Promotion'}"><span class="target-chip">Mã KM #${fn:escapeXml(log.targetId)}</span></c:when>
                                                    <c:when test="${log.targetType eq 'UserAppeal'}"><span class="target-chip">Kháng cáo #${fn:escapeXml(log.targetId)}</span></c:when>
                                                    <c:otherwise><span class="target-chip">${fn:escapeXml(log.targetType)} #${fn:escapeXml(log.targetId)}</span></c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td class="audit-detail-cell">
                                                <c:choose>
                                                    <c:when test="${empty log.detailJson}">
                                                        <span class="muted" style="font-size: 11px;">—</span>
                                                    </c:when>
                                                    <c:when test="${fn:startsWith(log.detailJson, '{') or fn:startsWith(log.detailJson, '[')}">
                                                        <details class="audit-detail-expand">
                                                            <summary>Xem chi tiết JSON (${fn:length(log.detailJson)} ký tự)</summary>
                                                            <pre><c:out value="${log.detailJson}"/></pre>
                                                        </details>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span class="audit-detail-text" title="${fn:escapeXml(log.detailJson)}"><c:out value="${log.detailJson}"/></span>
                                                    </c:otherwise>
                                                </c:choose>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty logs}">
                                        <tr><td colspan="5" class="text-center">Chưa có dòng nhật ký nào được ghi nhận.</td></tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
                        <nav class="toolbar" aria-label="Phân trang audit log" style="justify-content:center;margin-top:16px;">
                            <c:if test="${auditPage.hasPrevious}">
                                <a class="btn" href="${pageContext.request.contextPath}/system/audit-logs?page=${auditPage.page - 1}">Trang trước</a>
                            </c:if>
                            <span>Trang ${fn:escapeXml(auditPage.page)} / ${fn:escapeXml(auditPage.totalPages)} · ${fn:escapeXml(auditPage.totalItems)} bản ghi</span>
                            <c:if test="${auditPage.hasNext}">
                                <a class="btn" href="${pageContext.request.contextPath}/system/audit-logs?page=${auditPage.page + 1}">Trang sau</a>
                            </c:if>
                        </nav>
                    </section>
                </section>
            </div>
        </main>
    </div>
</body>
</html>
