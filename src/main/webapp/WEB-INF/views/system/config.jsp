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
    <title>Cau hinh he thong - CineBook</title>
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
                        <h1>Cấu hình hệ thống</h1>
                        <p class="muted">Cập nhật các tham số key/value quan trọng cho website (site name, support email, thời gian giữ ghế...).</p>
                    </div>
                    <form method="post" action="${pageContext.request.contextPath}/system/backup">
                        <cb:csrf/>
                        <button type="submit" class="danger">Backup Database</button>
                    </form>
                </div>

                <c:set var="securitySettingCount" value="0" />
                <c:set var="operationSettingCount" value="0" />
                <c:set var="automationSettingCount" value="0" />
                <c:forEach var="settingMetric" items="${settings}">
                    <c:if test="${fn:startsWith(settingMetric.settingKey, 'security.')}"><c:set var="securitySettingCount" value="${securitySettingCount + 1}" /></c:if>
                    <c:if test="${fn:startsWith(settingMetric.settingKey, 'booking.') or fn:startsWith(settingMetric.settingKey, 'counter.') or fn:startsWith(settingMetric.settingKey, 'seat_') or fn:startsWith(settingMetric.settingKey, 'showtime.')}"><c:set var="operationSettingCount" value="${operationSettingCount + 1}" /></c:if>
                    <c:if test="${fn:startsWith(settingMetric.settingKey, 'sweeper.') or fn:startsWith(settingMetric.settingKey, 'backup.')}"><c:set var="automationSettingCount" value="${automationSettingCount + 1}" /></c:if>
                </c:forEach>
                <section class="overview-system-grid" aria-label="Số liệu cấu hình hệ thống">
                    <div class="overview-metric-card"><span>Tổng tham số</span><strong>${fn:length(settings)}</strong><small>Dữ liệu cấu hình thực tế</small></div>
                    <div class="overview-metric-card"><span>Bảo mật</span><strong>${securitySettingCount}</strong><small>Nhóm security.*</small></div>
                    <div class="overview-metric-card"><span>Vận hành</span><strong>${operationSettingCount}</strong><small>Đặt vé, ghế và suất chiếu</small></div>
                    <div class="overview-metric-card"><span>Tự động hóa</span><strong>${automationSettingCount}</strong><small>Sweeper và sao lưu</small></div>
                </section>

                <section class="portal-stack">
                    <article class="panel">
                        <h2>Danh sách tham số cấu hình</h2>
                        <div class="table-wrap">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>Tham số (Setting Key)</th>
                                        <th>Nhóm</th>
                                        <th>Giá trị (Setting Value)</th>
                                        <th>Cập nhật lúc</th>
                                        <th>Hành động</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="setting" items="${settings}">
                                        <tr>
                                            <td class="mono font-bold" style="color: #0f172a;">${fn:escapeXml(setting.settingKey)}</td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${fn:startsWith(setting.settingKey, 'backup.')}"><span class="badge-status status-info">Sao lưu</span></c:when>
                                                    <c:when test="${fn:startsWith(setting.settingKey, 'security.')}"><span class="badge-status status-danger">Bảo mật</span></c:when>
                                                    <c:when test="${fn:startsWith(setting.settingKey, 'mail.') or fn:startsWith(setting.settingKey, 'company.')}"><span class="badge-status status-info">Liên hệ</span></c:when>
                                                    <c:when test="${fn:startsWith(setting.settingKey, 'sweeper.')}"><span class="badge-status status-warning">Tự động hóa</span></c:when>
                                                    <c:when test="${fn:startsWith(setting.settingKey, 'booking.') or fn:startsWith(setting.settingKey, 'counter.') or fn:startsWith(setting.settingKey, 'seat_') or fn:startsWith(setting.settingKey, 'showtime.')}"><span class="badge-status status-success">Vận hành</span></c:when>
                                                    <c:otherwise><span class="badge-status status-neutral">Chung</span></c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td>
                                                <form method="post" action="${pageContext.request.contextPath}/system/config" class="inline-actions" style="display:flex; gap:8px; width:100%;">
                                                    <cb:csrf/>
                                                    <input type="hidden" name="settingKey" value="${fn:escapeXml(setting.settingKey)}">
                                                    <input type="text" name="settingValue" value="${fn:escapeXml(setting.settingValue)}" style="flex:1;" required>
                                                    <button type="submit">Lưu</button>
                                                </form>
                                            </td>
                                            <td class="mono muted" style="font-size:12px;">${fn:escapeXml(setting.updatedAtDisplay)}</td>
                                            <td class="muted" style="font-size:12px;">Lưu từng tham số</td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty settings}">
                                        <tr><td colspan="5" class="text-center">Chưa có cấu hình nào.</td></tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
                    </article>

                    <article class="panel" style="margin-top: 24px;">
                        <h2>Giải phóng bộ nhớ giữ ghế</h2>
                        <p class="muted" style="margin-bottom:12px;">
                            Giải phóng các ghế bị kẹt ở trạng thái <span class="mono" style="background:#fee2e2; color:#ef4444; padding:2px 6px; border-radius:4px;">held</span> quá thời gian giữ ghế quy định (do người dùng hủy giao dịch giữa chừng).
                        </p>
                        <form method="post" action="${pageContext.request.contextPath}/system/config">
                            <cb:csrf/>
                            <input type="hidden" name="action" value="releaseHeldSeats">
                            <button type="submit" class="secondary">Giải phóng toàn bộ ghế held hết hạn</button>
                        </form>
                    </article>

                    <article class="panel" style="margin-top: 24px;">
                        <h2>Chạy dọn rác nền ngay</h2>
                        <p class="muted" style="margin-bottom:12px;">
                            Sweeper tự chạy mỗi 30 giây: trả ghế giữ quá hạn, huỷ đơn tại quầy quá hạn và
                            đơn nháp bị bỏ dở. Nút này chạy ngay một vòng mà không cần chờ.
                            Tắt/bật bằng khoá <span class="mono">sweeper.enabled</span>, ngưỡng đơn mồ côi ở
                            <span class="mono">sweeper.orphanOrderMinutes</span>.
                        </p>
                        <form method="post" action="${pageContext.request.contextPath}/system/config">
                            <cb:csrf/>
                            <input type="hidden" name="action" value="runSweeper">
                            <button type="submit" class="secondary">Chạy sweeper một vòng</button>
                        </form>
                    </article>
                </section>
            </div>
        </main>
    </div>
</body>
</html>
