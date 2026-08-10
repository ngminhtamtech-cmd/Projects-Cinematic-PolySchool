<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Báo cáo & Thống kê Thu Chi - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805f">
</head>
<body class="admin-body">
    <div class="dashboard">
        <%@ include file="/WEB-INF/views/admin/sidebar.jspf" %>
        <main class="dashboard-main">
            <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
            <div class="dashboard-content" style="padding: 16px 24px;">
                <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

                <!-- HEADER & EXCEL EXPORT BUTTON -->
                <section class="admin-page-head" style="margin-bottom: 16px;">
                    <div>
                        <h1 style="font-size: 22px; font-weight: 700; color: #0F172A; margin: 0 0 2px;">Báo cáo Thu Chi & Thống Kê</h1>
                        <p class="muted" style="margin: 0; font-size: 13px; color: #64748B;">
                            Kỳ báo cáo: <strong>${fn:escapeXml(reportSummary.currentMonthLabel)}</strong> (So sánh với ${fn:escapeXml(reportSummary.prevMonthLabel)})
                        </p>
                    </div>
                    <div>
                        <a href="${pageContext.request.contextPath}/admin/reports?action=exportExcel" class="report-pill-btn active" style="padding: 8px 16px !important; text-decoration: none;">
                            <svg style="width:16px;height:16px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                                <polyline points="7 10 12 15 17 10"/>
                                <line x1="12" y1="15" x2="12" y2="3"/>
                            </svg>
                            <span>Xuất Báo Cáo Excel</span>
                        </a>
                    </div>
                </section>

                <!-- TOP KPI CARDS (4 COMPACT CARDS IN 1 ROW) -->
                <div style="display:grid;grid-template-columns:repeat(4, 1fr);gap:14px;margin-bottom:16px;">
                    <!-- CARD 1: TỔNG DOANH THU -->
                    <div class="panel-card" style="padding: 14px 16px; background: #FFFFFF; border: 1px solid #E2E8F0; border-radius: 12px;">
                        <div style="font-size: 12px; color: #64748B; margin-bottom: 4px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;">
                            Tổng doanh thu
                        </div>
                        <div style="font-size: 22px; font-weight: 800; color: #0F172A; margin-bottom: 6px; letter-spacing: -0.5px;">
                            ${fn:escapeXml(reportSummary.formattedTotalRevenueCurrent)}
                        </div>
                        <div>
                            <c:choose>
                                <c:when test="${reportSummary.totalRevenueDiffPercent >= 0}">
                                    <span style="display:inline-flex;align-items:center;gap:4px;color:#15803D;font-size:11px;font-weight:600;">
                                        ↗ ${fn:escapeXml(reportSummary.formattedTotalRevenueDiffPercent)} so với tháng trước
                                    </span>
                                </c:when>
                                <c:otherwise>
                                    <span style="display:inline-flex;align-items:center;gap:4px;color:#C81E1E;font-size:11px;font-weight:600;">
                                        ↘ ${fn:escapeXml(reportSummary.formattedTotalRevenueDiffPercent)} so với tháng trước
                                    </span>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </div>

                    <!-- CARD 2: VÉ BÁN / NGÀY -->
                    <div class="panel-card" style="padding: 14px 16px; background: #FFFFFF; border: 1px solid #E2E8F0; border-radius: 12px;">
                        <div style="font-size: 12px; color: #64748B; margin-bottom: 4px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;">
                            Vé bán TB / Ngày
                        </div>
                        <div style="font-size: 22px; font-weight: 800; color: #0F172A; margin-bottom: 6px; letter-spacing: -0.5px;">
                            ${fn:escapeXml(reportSummary.avgTicketsPerDayCurrent)} <span style="font-size:13px;font-weight:600;color:#64748B;">vé</span>
                        </div>
                        <div>
                            <c:choose>
                                <c:when test="${reportSummary.avgTicketsPerDayDiffPercent >= 0}">
                                    <span style="display:inline-flex;align-items:center;gap:4px;color:#15803D;font-size:11px;font-weight:600;">
                                        ↗ ${fn:escapeXml(reportSummary.formattedAvgTicketsDiffPercent)} so với tháng trước
                                    </span>
                                </c:when>
                                <c:otherwise>
                                    <span style="display:inline-flex;align-items:center;gap:4px;color:#C81E1E;font-size:11px;font-weight:600;">
                                        ↘ ${fn:escapeXml(reportSummary.formattedAvgTicketsDiffPercent)} so với tháng trước
                                    </span>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </div>

                    <!-- CARD 3: TỈ LỆ LẤP GHẾ -->
                    <div class="panel-card" style="padding: 14px 16px; background: #FFFFFF; border: 1px solid #E2E8F0; border-radius: 12px;">
                        <div style="font-size: 12px; color: #64748B; margin-bottom: 4px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;">
                            Tỉ lệ lấp ghế
                        </div>
                        <div style="font-size: 22px; font-weight: 800; color: #6D28D9; margin-bottom: 6px; letter-spacing: -0.5px;">
                            <c:forEach var="row" items="${reportSummary.metrics}">
                                <c:if test="${row.name eq 'Tỉ lệ lấp ghế'}">${fn:escapeXml(row.currentValue)}</c:if>
                            </c:forEach>
                        </div>
                        <div style="font-size:11px;color:#64748B;font-weight:500;">
                            Công suất phòng chiếu
                        </div>
                    </div>

                    <!-- CARD 4: TỈ LỆ HUỶ ĐƠN -->
                    <div class="panel-card" style="padding: 14px 16px; background: #FFFFFF; border: 1px solid #E2E8F0; border-radius: 12px;">
                        <div style="font-size: 12px; color: #64748B; margin-bottom: 4px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;">
                            Tỉ lệ huỷ đơn
                        </div>
                        <div style="font-size: 22px; font-weight: 800; color: #C81E1E; margin-bottom: 6px; letter-spacing: -0.5px;">
                            ${fn:escapeXml(reportSummary.formattedCancelRateCurrent)}
                        </div>
                        <div>
                            <c:choose>
                                <c:when test="${reportSummary.cancelRateDiffPoint <= 0}">
                                    <span style="display:inline-flex;align-items:center;gap:4px;color:#15803D;font-size:11px;font-weight:600;">
                                        ↘ ${fn:escapeXml(reportSummary.formattedCancelRateDiffPoint)} so với tháng trước
                                    </span>
                                </c:when>
                                <c:otherwise>
                                    <span style="display:inline-flex;align-items:center;gap:4px;color:#C81E1E;font-size:11px;font-weight:600;">
                                        ↗ ${fn:escapeXml(reportSummary.formattedCancelRateDiffPoint)} so với tháng trước
                                    </span>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </div>
                </div>

                <!-- TOP NAV PILLS BAR (ZERO COLLISION) -->
                <div class="report-nav-pills">
                    <button type="button" class="report-pill-btn active" id="btnReportTab_daily" onclick="switchReportPeriodTab('daily')">
                        📅 Báo cáo theo Ngày
                    </button>
                    <button type="button" class="report-pill-btn" id="btnReportTab_monthly" onclick="switchReportPeriodTab('monthly')">
                        🗓️ Báo cáo theo Tháng
                    </button>
                    <button type="button" class="report-pill-btn" id="btnReportTab_yearly" onclick="switchReportPeriodTab('yearly')">
                        📊 Báo cáo theo Năm
                    </button>
                    <button type="button" class="report-pill-btn" id="btnReportTab_metrics" onclick="switchReportPeriodTab('metrics')">
                        📈 Chi tiết Thu Chi & Chỉ số
                    </button>
                </div>

                <!-- TAB 1: THEO NGÀY -->
                <div id="reportTab_daily">
                    <div style="display:grid;grid-template-columns:1.2fr 0.8fr;gap:16px;">
                        <article class="panel-card" style="padding: 16px; background:#FFFFFF; border:1px solid #E2E8F0; border-radius:12px;">
                            <h2 style="font-size: 14px; font-weight: 700; color: #0F172A; margin: 0 0 12px; display:flex; align-items:center; justify-content:space-between;">
                                <span>📅 Doanh thu theo Ngày (Thực tế)</span>
                                <small style="font-weight:500;color:#64748B;font-size:12px;">Tất cả các ngày phát sinh</small>
                            </h2>
                            <div class="table-responsive" style="max-height: 360px; overflow-y: auto;">
                                <table class="table-data" style="font-size: 13px;">
                                    <thead>
                                        <tr>
                                            <th scope="col" style="padding: 8px 12px;">Ngày phát sinh</th>
                                            <th scope="col" style="padding: 8px 12px;">Số đơn Paid</th>
                                            <th scope="col" style="padding: 8px 12px; text-align:right;">Doanh thu</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <c:forEach var="row" items="${dailyRevenue}">
                                            <tr>
                                                <td style="padding: 8px 12px;"><span class="border-tag tag-time">${fn:escapeXml(row.label)}</span></td>
                                                <td style="padding: 8px 12px; color:#475569; font-weight:600;">${fn:escapeXml(row.orderCount)} đơn</td>
                                                <td style="padding: 8px 12px; text-align:right;"><strong style="color:#15803D;font-weight:700;">${fn:escapeXml(row.formattedTotalRevenue)}</strong></td>
                                            </tr>
                                        </c:forEach>
                                        <c:if test="${empty dailyRevenue}">
                                            <tr><td colspan="3" style="text-align:center;color:#94A3B8;padding:16px;">Chưa có dữ liệu doanh thu ngày.</td></tr>
                                        </c:if>
                                    </tbody>
                                </table>
                            </div>
                        </article>

                        <article class="panel-card" style="padding: 16px; background:#FFFFFF; border:1px solid #E2E8F0; border-radius:12px;">
                            <h2 style="font-size: 14px; font-weight: 700; color: #0F172A; margin: 0 0 12px;">🎬 Top Phim Bán Chạy Nhất</h2>
                            <div class="table-responsive" style="max-height: 360px; overflow-y: auto;">
                                <table class="table-data" style="font-size: 13px;">
                                    <thead>
                                        <tr>
                                            <th scope="col" style="padding: 8px 12px;">Tên phim</th>
                                            <th scope="col" style="padding: 8px 12px;">Số ghế</th>
                                            <th scope="col" style="padding: 8px 12px; text-align:right;">Doanh thu</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <c:forEach var="row" items="${topFilms}">
                                            <tr>
                                                <td style="padding: 8px 12px;"><strong style="color:#0F172A;font-weight:600;">${fn:escapeXml(row.filmTitle)}</strong></td>
                                                <td style="padding: 8px 12px; color:#64748B;">${fn:escapeXml(row.soldSeats)} ghế</td>
                                                <td style="padding: 8px 12px; text-align:right;"><strong style="color:#6D28D9;font-weight:700;">${fn:escapeXml(row.formattedTotalRevenue)}</strong></td>
                                            </tr>
                                        </c:forEach>
                                        <c:if test="${empty topFilms}">
                                            <tr><td colspan="3" style="text-align:center;color:#94A3B8;padding:16px;">Chưa có dữ liệu top phim.</td></tr>
                                        </c:if>
                                    </tbody>
                                </table>
                            </div>
                        </article>
                    </div>
                </div>

                <!-- TAB 2: THEO THÁNG -->
                <div id="reportTab_monthly" style="display:none;">
                    <div style="display:grid;grid-template-columns:1.2fr 0.8fr;gap:16px;">
                        <article class="panel-card" style="padding: 16px; background:#FFFFFF; border:1px solid #E2E8F0; border-radius:12px;">
                            <h2 style="font-size: 14px; font-weight: 700; color: #0F172A; margin: 0 0 12px;">🗓️ Doanh thu Tổng hợp theo Tháng</h2>
                            <div class="table-responsive" style="max-height: 360px; overflow-y: auto;">
                                <table class="table-data" style="font-size: 13px;">
                                    <thead>
                                        <tr>
                                            <th scope="col" style="padding: 8px 12px;">Tháng</th>
                                            <th scope="col" style="padding: 8px 12px;">Số đơn Paid</th>
                                            <th scope="col" style="padding: 8px 12px; text-align:right;">Doanh thu</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <c:forEach var="row" items="${monthlyRevenue}">
                                            <tr>
                                                <td style="padding: 8px 12px;"><span class="border-tag tag-time">${fn:escapeXml(row.label)}</span></td>
                                                <td style="padding: 8px 12px; color:#475569; font-weight:600;">${fn:escapeXml(row.orderCount)} đơn</td>
                                                <td style="padding: 8px 12px; text-align:right;"><strong style="color:#15803D;font-weight:700;">${fn:escapeXml(row.formattedTotalRevenue)}</strong></td>
                                            </tr>
                                        </c:forEach>
                                        <c:if test="${empty monthlyRevenue}">
                                            <tr><td colspan="3" style="text-align:center;color:#94A3B8;padding:16px;">Chưa có dữ liệu doanh thu tháng.</td></tr>
                                        </c:if>
                                    </tbody>
                                </table>
                            </div>
                        </article>

                        <article class="panel-card" style="padding: 16px; background:#FFFFFF; border:1px solid #E2E8F0; border-radius:12px;">
                            <h2 style="font-size: 14px; font-weight: 700; color: #0F172A; margin: 0 0 12px;">🎬 Top Phim Bán Chạy Nhất</h2>
                            <div class="table-responsive" style="max-height: 360px; overflow-y: auto;">
                                <table class="table-data" style="font-size: 13px;">
                                    <thead>
                                        <tr>
                                            <th scope="col" style="padding: 8px 12px;">Tên phim</th>
                                            <th scope="col" style="padding: 8px 12px;">Số ghế</th>
                                            <th scope="col" style="padding: 8px 12px; text-align:right;">Doanh thu</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <c:forEach var="row" items="${topFilms}">
                                            <tr>
                                                <td style="padding: 8px 12px;"><strong style="color:#0F172A;font-weight:600;">${fn:escapeXml(row.filmTitle)}</strong></td>
                                                <td style="padding: 8px 12px; color:#64748B;">${fn:escapeXml(row.soldSeats)} ghế</td>
                                                <td style="padding: 8px 12px; text-align:right;"><strong style="color:#6D28D9;font-weight:700;">${fn:escapeXml(row.formattedTotalRevenue)}</strong></td>
                                            </tr>
                                        </c:forEach>
                                        <c:if test="${empty topFilms}">
                                            <tr><td colspan="3" style="text-align:center;color:#94A3B8;padding:16px;">Chưa có dữ liệu top phim.</td></tr>
                                        </c:if>
                                    </tbody>
                                </table>
                            </div>
                        </article>
                    </div>
                </div>

                <!-- TAB 3: THEO NĂM -->
                <div id="reportTab_yearly" style="display:none;">
                    <div style="display:grid;grid-template-columns:1.2fr 0.8fr;gap:16px;">
                        <article class="panel-card" style="padding: 16px; background:#FFFFFF; border:1px solid #E2E8F0; border-radius:12px;">
                            <h2 style="font-size: 14px; font-weight: 700; color: #0F172A; margin: 0 0 12px;">📊 Doanh thu Tổng hợp theo Năm</h2>
                            <div class="table-responsive" style="max-height: 360px; overflow-y: auto;">
                                <table class="table-data" style="font-size: 13px;">
                                    <thead>
                                        <tr>
                                            <th scope="col" style="padding: 8px 12px;">Năm</th>
                                            <th scope="col" style="padding: 8px 12px;">Số đơn Paid</th>
                                            <th scope="col" style="padding: 8px 12px; text-align:right;">Doanh thu</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <c:forEach var="row" items="${yearlyRevenue}">
                                            <tr>
                                                <td style="padding: 8px 12px;"><span class="border-tag tag-time">${fn:escapeXml(row.label)}</span></td>
                                                <td style="padding: 8px 12px; color:#475569; font-weight:600;">${fn:escapeXml(row.orderCount)} đơn</td>
                                                <td style="padding: 8px 12px; text-align:right;"><strong style="color:#15803D;font-weight:700;">${fn:escapeXml(row.formattedTotalRevenue)}</strong></td>
                                            </tr>
                                        </c:forEach>
                                        <c:if test="${empty yearlyRevenue}">
                                            <tr><td colspan="3" style="text-align:center;color:#94A3B8;padding:16px;">Chưa có dữ liệu doanh thu năm.</td></tr>
                                        </c:if>
                                    </tbody>
                                </table>
                            </div>
                        </article>

                        <article class="panel-card" style="padding: 16px; background:#FFFFFF; border:1px solid #E2E8F0; border-radius:12px;">
                            <h2 style="font-size: 14px; font-weight: 700; color: #0F172A; margin: 0 0 12px;">🎬 Top Phim Bán Chạy Nhất</h2>
                            <div class="table-responsive" style="max-height: 360px; overflow-y: auto;">
                                <table class="table-data" style="font-size: 13px;">
                                    <thead>
                                        <tr>
                                            <th scope="col" style="padding: 8px 12px;">Tên phim</th>
                                            <th scope="col" style="padding: 8px 12px;">Số ghế</th>
                                            <th scope="col" style="padding: 8px 12px; text-align:right;">Doanh thu</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <c:forEach var="row" items="${topFilms}">
                                            <tr>
                                                <td style="padding: 8px 12px;"><strong style="color:#0F172A;font-weight:600;">${fn:escapeXml(row.filmTitle)}</strong></td>
                                                <td style="padding: 8px 12px; color:#64748B;">${fn:escapeXml(row.soldSeats)} ghế</td>
                                                <td style="padding: 8px 12px; text-align:right;"><strong style="color:#6D28D9;font-weight:700;">${fn:escapeXml(row.formattedTotalRevenue)}</strong></td>
                                            </tr>
                                        </c:forEach>
                                        <c:if test="${empty topFilms}">
                                            <tr><td colspan="3" style="text-align:center;color:#94A3B8;padding:16px;">Chưa có dữ liệu top phim.</td></tr>
                                        </c:if>
                                    </tbody>
                                </table>
                            </div>
                        </article>
                    </div>
                </div>

                <!-- TAB 4: CHI TIẾT THU CHI & CHỈ SỐ -->
                <div id="reportTab_metrics" style="display:none;">
                    <article class="panel-card" style="padding: 16px; background:#FFFFFF; border:1px solid #E2E8F0; border-radius:12px;">
                        <h2 style="font-size: 14px; font-weight: 700; color: #0F172A; margin: 0 0 12px;">📈 Bảng Phân Tích Chi Tiết Nguồn Thu Chi & Tăng Trưởng</h2>
                        <div class="table-responsive">
                            <table class="table-data" style="font-size: 13px;">
                                <thead>
                                    <tr>
                                        <th scope="col" style="padding: 8px 12px;">Chỉ số tài chính / Vận hành</th>
                                        <th scope="col" style="padding: 8px 12px;">${fn:escapeXml(reportSummary.currentMonthLabel)}</th>
                                        <th scope="col" style="padding: 8px 12px;">${fn:escapeXml(reportSummary.prevMonthLabel)}</th>
                                        <th scope="col" style="padding: 8px 12px; text-align:right;">Tỷ lệ biến động</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="row" items="${reportSummary.metrics}">
                                        <tr>
                                            <td style="padding: 8px 12px;"><strong style="color:#0F172A;font-weight:600;">${fn:escapeXml(row.name)}</strong></td>
                                            <td style="padding: 8px 12px; color:#0F172A; font-weight:700;">${fn:escapeXml(row.currentValue)}</td>
                                            <td style="padding: 8px 12px; color:#64748B;">${fn:escapeXml(row.prevValue)}</td>
                                            <td style="padding: 8px 12px; text-align:right;">
                                                <c:choose>
                                                    <c:when test="${row.trendUp}">
                                                        <span style="color:#15803D;font-weight:700;display:inline-flex;align-items:center;gap:4px;">
                                                            ↗ ${fn:escapeXml(row.diffText)}
                                                        </span>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span style="color:#C81E1E;font-weight:700;display:inline-flex;align-items:center;gap:4px;">
                                                            ↘ ${fn:escapeXml(row.diffText)}
                                                        </span>
                                                    </c:otherwise>
                                                </c:choose>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                </tbody>
                            </table>
                        </div>
                    </article>
                </div>
            </div>
        </main>
    </div>

    <script>
        function switchReportPeriodTab(tabName) {
            var tabs = ['daily', 'monthly', 'yearly', 'metrics'];
            tabs.forEach(function(t) {
                var btn = document.getElementById('btnReportTab_' + t);
                var content = document.getElementById('reportTab_' + t);
                if (btn) {
                    if (t === tabName) {
                        btn.classList.add('active');
                    } else {
                        btn.classList.remove('active');
                    }
                }
                if (content) {
                    content.style.display = (t === tabName) ? 'block' : 'none';
                }
            });
        }
    </script>
</body>
</html>
