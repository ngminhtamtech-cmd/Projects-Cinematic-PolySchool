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
    <title>Tổng quan hệ thống - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
</head>
<body class="admin-body admin-overview-page">
<div class="dashboard">
    <%@ include file="/WEB-INF/views/admin/sidebar.jspf" %>
    <main class="dashboard-main">
        <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
        <div class="dashboard-content">
            <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

            <section class="portal-head overview-heading">
                <div>
                    <h1>Tổng quan hệ thống</h1>
                    <p class="muted">Theo dõi hoạt động và hiệu suất kinh doanh của hệ thống CineBook.</p>
                </div>
            </section>

            <c:if test="${countsUnavailable}">
                <article class="alert-banner alert-warning" role="alert">
                    <div>
                        <h3>Không đọc được số liệu tổng quan</h3>
                        <p class="muted">Các ô số liệu đang để trống vì truy vấn thất bại. Đây không phải số 0 thật.</p>
                    </div>
                    <a class="ent-btn-action ent-btn-action-primary" href="${pageContext.request.contextPath}/admin/dashboard">Thử lại</a>
                </article>
            </c:if>

            <c:if test="${sessionScope.currentUser.role eq 'admin'}">
                <section class="overview-system-grid" aria-label="Số liệu quản trị hệ thống">
                    <a class="overview-metric-card" href="${pageContext.request.contextPath}/system/managers">
                        <span>Tài khoản Manager</span>
                        <strong>${fn:escapeXml(managerCount)}</strong>
                        <small>Tổng số tài khoản quản lý</small>
                    </a>
                    <a class="overview-metric-card" href="${pageContext.request.contextPath}/system/config">
                        <span>Cài đặt hệ thống</span>
                        <strong>${fn:escapeXml(settingCount)}</strong>
                        <small>Thông số cấu hình</small>
                    </a>
                    <a class="overview-metric-card" href="${pageContext.request.contextPath}/system/audit-logs">
                        <span>Dòng audit log</span>
                        <strong>${fn:escapeXml(auditCount)}</strong>
                        <small>Bản ghi hoạt động</small>
                    </a>
                    <article class="overview-backup-card">
                        <div>
                            <strong>Sao lưu Database</strong>
                            <p>Chức năng sao lưu được thực thi trực tiếp qua Transact-SQL.</p>
                        </div>
                        <form method="post" action="${pageContext.request.contextPath}/system/backup">
                            <cb:csrf/>
                            <button type="submit" class="overview-text-action">Thực hiện sao lưu</button>
                        </form>
                    </article>
                </section>
            </c:if>

            <div class="overview-columns">
                <div class="overview-primary-column">
                    <section class="overview-panel operations-panel">
                        <header class="overview-panel-head">
                            <h2>Vận hành rạp chiếu &amp; khách hàng</h2>
                        </header>
                        <div class="operations-strip">
                            <a href="${pageContext.request.contextPath}/admin/films"><span>Phim đang quản lý</span><strong>${fn:escapeXml(filmCount)}</strong></a>
                            <a href="${pageContext.request.contextPath}/admin/cinemas"><span>Rạp chiếu</span><strong>${fn:escapeXml(cinemaCount)}</strong></a>
                            <a href="${pageContext.request.contextPath}/admin/rooms"><span>Phòng chiếu</span><strong>${fn:escapeXml(roomCount)}</strong></a>
                            <a href="${pageContext.request.contextPath}/admin/showtimes"><span>Suất chiếu</span><strong>${fn:escapeXml(showtimeCount)}</strong></a>
                            <a href="${pageContext.request.contextPath}/admin/users"><span>Tài khoản Member</span><strong>${fn:escapeXml(memberCount)}</strong></a>
                            <a href="${pageContext.request.contextPath}/admin/orders"><span>Đơn đặt vé</span><strong>${fn:escapeXml(orderCount)}</strong></a>
                        </div>

                        <div class="revenue-layout">
                            <div class="revenue-chart-block">
                                <div class="overview-subhead">
                                    <h3>Doanh thu theo ngày</h3>
                                    <span>7 ngày có phát sinh gần nhất</span>
                                </div>
                                <c:choose>
                                    <c:when test="${revenueUnavailable}">
                                        <div class="overview-empty is-error">Không tải được dữ liệu doanh thu.</div>
                                    </c:when>
                                    <c:when test="${empty dailyRevenue}">
                                        <div class="overview-empty">Chưa có đơn đã thanh toán để lập biểu đồ.</div>
                                    </c:when>
                                    <c:otherwise>
                                        <div class="revenue-chart" id="revenueChart" role="img" aria-label="Biểu đồ doanh thu thật theo ngày">
                                            <svg viewBox="0 0 720 230" preserveAspectRatio="none" aria-hidden="true"></svg>
                                        </div>
                                        <ol id="revenueChartData" class="visually-hidden">
                                            <c:forEach var="row" items="${dailyRevenue}" varStatus="status">
                                                <c:if test="${status.index lt 7}">
                                                    <li data-label="${fn:escapeXml(row.label)}"
                                                        data-revenue="${fn:escapeXml(row.totalRevenue)}"
                                                        data-orders="${fn:escapeXml(row.orderCount)}"></li>
                                                </c:if>
                                            </c:forEach>
                                        </ol>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                            <aside class="revenue-summary" aria-label="Tổng hợp dữ liệu biểu đồ">
                                <span>Tổng doanh thu</span>
                                <strong id="dashboardRevenueTotal">—</strong>
                                <hr>
                                <span>Tổng đơn đã thanh toán</span>
                                <strong id="dashboardOrderTotal">—</strong>
                                <small>Trong các ngày đang hiển thị</small>
                            </aside>
                        </div>
                    </section>

                    <section class="overview-panel quick-panel">
                        <header class="overview-panel-head"><h2>Lối tắt nghiệp vụ thường dùng</h2></header>
                        <div class="overview-quick-grid">
                            <a href="${pageContext.request.contextPath}/admin/films"><strong>Quản lý phim</strong><span>${sessionScope.currentUser.role eq 'manager' ? 'Xem phim và gửi đề nghị' : 'Thêm, sửa, phân phim theo rạp'}</span></a>
                            <a href="${pageContext.request.contextPath}/admin/showtimes"><strong>Quản lý suất chiếu</strong><span>Tạo và phân bổ suất chiếu</span></a>
                            <a href="${pageContext.request.contextPath}/admin/comments"><strong>Duyệt bình luận</strong><span>Kiểm duyệt đánh giá</span></a>
                            <a href="${pageContext.request.contextPath}/admin/promotions"><strong>Khuyến mãi</strong><span>Tạo mã voucher dùng toàn hệ thống</span></a>
                            <a href="${pageContext.request.contextPath}/admin/orders"><strong>Check-in vé</strong><span>Xác nhận vé và tra cứu</span></a>
                            <a href="${pageContext.request.contextPath}/admin/reports"><strong>Báo cáo doanh thu</strong><span>Thống kê doanh thu thực tế</span></a>
                        </div>
                    </section>
                </div>

                <aside class="overview-secondary-column">
                    <c:if test="${sessionScope.currentUser.role eq 'admin'}">
                        <section class="overview-panel activity-panel">
                            <header class="overview-panel-head">
                                <h2>Nhật ký hoạt động gần đây</h2>
                                <a href="${pageContext.request.contextPath}/system/audit-logs">Xem tất cả</a>
                            </header>
                            <c:choose>
                                <c:when test="${auditLogsUnavailable}"><div class="overview-empty is-error">Không tải được nhật ký hoạt động.</div></c:when>
                                <c:when test="${empty recentAuditLogs}"><div class="overview-empty">Chưa có hoạt động nào được ghi nhận.</div></c:when>
                                <c:otherwise>
                                    <ol class="activity-list">
                                        <c:forEach var="entry" items="${recentAuditLogs}">
                                            <li>
                                                <span class="activity-dot" aria-hidden="true"></span>
                                                <div>
                                                    <strong>${fn:escapeXml(entry.action)}</strong>
                                                    <c:if test="${not empty entry.targetType}"><p>${fn:escapeXml(entry.targetType)}<c:if test="${not empty entry.targetId}"> · ${fn:escapeXml(entry.targetId)}</c:if></p></c:if>
                                                    <small>${fn:escapeXml(entry.actorEmail)} · ${fn:escapeXml(entry.createdAtDisplay)}</small>
                                                </div>
                                            </li>
                                        </c:forEach>
                                    </ol>
                                </c:otherwise>
                            </c:choose>
                        </section>
                    </c:if>

                    <section class="overview-panel top-films-panel">
                        <header class="overview-panel-head">
                            <h2>Top phim bán chạy</h2>
                            <a href="${pageContext.request.contextPath}/admin/reports">Xem báo cáo</a>
                        </header>
                        <c:choose>
                            <c:when test="${topFilmsUnavailable}"><div class="overview-empty is-error">Không tải được dữ liệu top phim.</div></c:when>
                            <c:when test="${empty topFilms}"><div class="overview-empty">Chưa có doanh thu phim để xếp hạng.</div></c:when>
                            <c:otherwise>
                                <ol class="top-film-list">
                                    <c:forEach var="film" items="${topFilms}" end="4" varStatus="status">
                                        <li><span>${status.count}</span><strong>${fn:escapeXml(film.filmTitle)}</strong><em>${fn:escapeXml(film.formattedTotalRevenue)}</em></li>
                                    </c:forEach>
                                </ol>
                            </c:otherwise>
                        </c:choose>
                    </section>
                </aside>
            </div>
        </div>
    </main>
</div>
<script src="${pageContext.request.contextPath}/assets/js/admin-dashboard.js?v=20260805"></script>
</body>
</html>
