<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Xin Hoàn Tiền Vé Đặc Biệt - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <style>
        /* ============================================================
           Trang "Xin hoan tien ve" — tuan thu DESIGN_GUIDE.md
           Token mau, typography va component deu lay tu design guide.
           ============================================================ */
        .rf-page {
            background: #F6F6F9;
            font-family: "Be Vietnam Pro", system-ui, -apple-system, "Segoe UI", sans-serif;
            color: #1A1A21;
            padding: 28px 20px 64px;
        }

        .rf-shell {
            max-width: 940px;
            margin: 0 auto;
        }

        /* ---------- Breadcrumb + Heading ---------- */
        .rf-crumb {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 12px;
            font-weight: 400;
            color: #8A8A96;
            margin-bottom: 14px;
        }
        .rf-crumb a {
            color: #6E6E7A;
            text-decoration: none;
        }
        .rf-crumb a:hover { color: #6D28D9; }
        .rf-crumb svg { flex: none; }

        .rf-head { margin-bottom: 18px; }
        .rf-head h1 {
            font-size: 22px;
            font-weight: 600;
            letter-spacing: -0.01em;
            margin: 0 0 6px;
            color: #1A1A21;
        }
        .rf-head p {
            font-size: 13px;
            font-weight: 400;
            line-height: 1.6;
            color: #6E6E7A;
            margin: 0;
            max-width: 720px;
        }

        /* ---------- Banner canh bao (Warning tone) ---------- */
        .rf-banner {
            display: flex;
            align-items: flex-start;
            gap: 10px;
            background: #FFFAEB;
            border: 1px solid #FDE68A;
            border-radius: 12px;
            padding: 13px 15px;
            margin-bottom: 20px;
        }
        .rf-banner svg { flex: none; color: #B45309; margin-top: 1px; }
        .rf-banner-body { flex: 1; }
        .rf-banner h2 {
            font-size: 14px;
            font-weight: 600;
            color: #B45309;
            margin: 0 0 4px;
        }
        .rf-banner p {
            font-size: 13px;
            font-weight: 400;
            line-height: 1.6;
            color: #B45309;
            margin: 0;
        }
        .rf-banner-action {
            flex: none;
            align-self: center;
            background: #FFFFFF;
            border: 1px solid #FDE68A;
            border-radius: 9px;
            padding: 8px 14px;
            font-size: 13px;
            font-weight: 600;
            color: #B45309;
            text-decoration: none;
            white-space: nowrap;
        }
        .rf-banner-action:hover { background: #FFFDF5; }

        /* ---------- Card / Panel ---------- */
        .rf-card {
            background: #FFFFFF;
            border: 1px solid #E8E8EE;
            border-radius: 12px;
            margin-bottom: 16px;
            overflow: hidden;
        }
        .rf-card-head {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 14px 18px;
            border-bottom: 1px solid #F3F3F7;
        }
        .rf-card-head svg { flex: none; color: #6E6E7A; }
        .rf-card-head h2 {
            font-size: 14px;
            font-weight: 600;
            color: #1A1A21;
            margin: 0;
        }
        .rf-card-head .rf-step {
            flex: none;
            width: 22px;
            height: 22px;
            border-radius: 999px;
            background: #F5F2FF;
            border: 1px solid #DDD3FE;
            color: #6D28D9;
            font-size: 12px;
            font-weight: 600;
            display: inline-flex;
            align-items: center;
            justify-content: center;
        }
        .rf-card-body { padding: 18px; }

        .rf-section-label {
            font-size: 11px;
            font-weight: 600;
            letter-spacing: 0.04em;
            text-transform: uppercase;
            color: #8A8A96;
            margin: 0 0 10px;
        }

        /* ---------- Badge trang thai ---------- */
        .rf-badge {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            border-radius: 999px;
            padding: 3px 9px;
            font-size: 12px;
            font-weight: 600;
            border: 1px solid transparent;
            white-space: nowrap;
        }
        .rf-badge .rf-dot {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: currentColor;
            flex: none;
        }
        .rf-badge.is-brand   { background: #F5F2FF; border-color: #DDD3FE; color: #6D28D9; }
        .rf-badge.is-success { background: #ECFDF3; border-color: #BBF7D0; color: #15803D; }
        .rf-badge.is-warning { background: #FFFAEB; border-color: #FDE68A; color: #B45309; }
        .rf-badge.is-danger  { background: #FEF2F2; border-color: #FBCFCF; color: #C81E1E; }
        .rf-badge.is-info    { background: #EFF4FF; border-color: #C7D7FE; color: #1D4ED8; }
        .rf-badge.is-neutral { background: #F5F5F8; border-color: #E4E4EA; color: #52525E; }
        .rf-badge.is-plain   { padding-left: 9px; }

        /* ---------- The ve da chon ---------- */
        .rf-ticket {
            display: flex;
            align-items: stretch;
            gap: 16px;
            border: 1px solid #E8E8EE;
            border-radius: 12px;
            padding: 16px;
            background: #FFFFFF;
        }
        .rf-ticket-main { flex: 1; min-width: 0; }
        .rf-ticket-tags {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: 6px;
            margin-bottom: 8px;
        }
        .rf-ticket h3 {
            font-size: 13px;
            font-weight: 600;
            color: #1A1A21;
            margin: 0 0 10px;
        }
        .rf-ticket-meta {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
            gap: 8px 18px;
            margin: 0;
        }
        .rf-meta-item {
            display: flex;
            align-items: flex-start;
            gap: 7px;
            font-size: 13px;
            font-weight: 400;
            color: #1A1A21;
            min-width: 0;
        }
        .rf-meta-item svg { flex: none; color: #8A8A96; margin-top: 2px; }
        .rf-meta-item dt {
            font-size: 12px;
            font-weight: 400;
            color: #6E6E7A;
            margin: 0 0 1px;
        }
        .rf-meta-item dd { margin: 0; }

        .rf-ticket-amount {
            flex: none;
            align-self: flex-start;
            text-align: right;
            border-left: 1px dashed #E4E4EA;
            padding-left: 16px;
            min-width: 148px;
        }
        .rf-amount-label {
            font-size: 11px;
            font-weight: 600;
            letter-spacing: 0.04em;
            text-transform: uppercase;
            color: #8A8A96;
            margin-bottom: 4px;
        }
        .rf-amount-value {
            font-size: 26px;
            font-weight: 600;
            letter-spacing: -0.02em;
            color: #1A1A21;
            line-height: 1.15;
        }
        .rf-amount-value span { font-size: 14px; font-weight: 600; color: #6E6E7A; }
        .rf-amount-note { font-size: 12px; font-weight: 400; color: #6E6E7A; margin-top: 6px; }

        /* ---------- Form ---------- */
        .rf-field { margin-bottom: 16px; }
        .rf-field:last-child { margin-bottom: 0; }
        .rf-field > label {
            display: block;
            font-size: 13px;
            font-weight: 600;
            color: #1A1A21;
            margin-bottom: 6px;
        }
        .rf-req { color: #C81E1E; font-weight: 600; }

        .rf-input {
            width: 100%;
            box-sizing: border-box;
            padding: 9px 12px;
            font-family: inherit;
            font-size: 13px;
            font-weight: 400;
            color: #1A1A21;
            background: #FFFFFF;
            border: 1px solid #E4E4EA;
            border-radius: 9px;
            transition: border-color .15s ease, box-shadow .15s ease;
        }
        .rf-input::placeholder { color: #8A8A96; }
        .rf-input:hover { border-color: #DDD3FE; }
        .rf-input:focus {
            outline: none;
            border-color: #6D28D9;
            box-shadow: 0 0 0 3px rgba(109, 40, 217, 0.12);
        }
        select.rf-input {
            appearance: none;
            -webkit-appearance: none;
            background-image: url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%236E6E7A' stroke-width='1.75' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E");
            background-repeat: no-repeat;
            background-position: right 11px center;
            padding-right: 34px;
        }
        textarea.rf-input {
            min-height: 116px;
            resize: vertical;
            line-height: 1.6;
        }
        .rf-hint {
            display: flex;
            align-items: flex-start;
            gap: 6px;
            font-size: 12px;
            font-weight: 400;
            color: #6E6E7A;
            margin-top: 6px;
        }
        .rf-hint svg { flex: none; color: #8A8A96; margin-top: 1px; }

        /* ---------- Empty state ---------- */
        .rf-empty {
            text-align: center;
            padding: 26px 18px;
            border: 1px dashed #E4E4EA;
            border-radius: 12px;
            background: #FAFAFC;
        }
        .rf-empty svg { color: #8A8A96; margin-bottom: 10px; }
        .rf-empty h3 {
            font-size: 13px;
            font-weight: 600;
            color: #1A1A21;
            margin: 0 0 4px;
        }
        .rf-empty p {
            font-size: 12px;
            font-weight: 400;
            color: #6E6E7A;
            margin: 0 0 14px;
        }

        /* ---------- Bang quy dinh ---------- */
        .rf-table-wrap { overflow-x: auto; }
        table.rf-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
        }
        table.rf-table thead th {
            background: #FAFAFC;
            font-size: 12px;
            font-weight: 600;
            color: #71717E;
            text-align: left;
            padding: 9px 14px;
            border-bottom: 1px solid #F3F3F7;
            white-space: nowrap;
        }
        table.rf-table tbody td {
            padding: 9px 14px;
            font-size: 13px;
            font-weight: 400;
            color: #1A1A21;
            border-bottom: 1px solid #F3F3F7;
            vertical-align: top;
        }
        table.rf-table tbody tr:hover { background: #FCFCFD; }
        table.rf-table tbody tr:last-child td { border-bottom: none; }

        /* ---------- Footer hanh dong ---------- */
        .rf-actions {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 14px;
            flex-wrap: wrap;
            padding: 14px 18px;
            border-top: 1px solid #F3F3F7;
            background: #FAFAFC;
        }
        .rf-actions-note {
            font-size: 12px;
            font-weight: 400;
            color: #6E6E7A;
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .rf-actions-note svg { flex: none; color: #8A8A96; }
        .rf-actions-btns { display: flex; align-items: center; gap: 8px; }

        .rf-btn {
            display: inline-flex;
            align-items: center;
            gap: 7px;
            border-radius: 9px;
            padding: 8px 14px;
            font-family: inherit;
            font-size: 13px;
            font-weight: 600;
            line-height: 1.3;
            cursor: pointer;
            text-decoration: none;
            border: 1px solid transparent;
            transition: background-color .15s ease, border-color .15s ease;
        }
        .rf-btn-primary { background: #6D28D9; color: #FFFFFF; }
        .rf-btn-primary:hover { background: #5B21B6; }
        .rf-btn-secondary {
            background: #FFFFFF;
            border-color: #E4E4EA;
            color: #2A2A33;
        }
        .rf-btn-secondary:hover { background: #FAFAFC; }
        .rf-btn:disabled { opacity: .55; cursor: not-allowed; }

        /* ---------- Flash (dong bo tone semantic) ---------- */
        .rf-shell .flash {
            border-radius: 12px;
            padding: 12px 15px;
            font-size: 13px;
            font-weight: 400;
            margin-bottom: 16px;
            border: 1px solid transparent;
        }
        .rf-shell .flash.success { background: #ECFDF3; border-color: #BBF7D0; color: #15803D; }
        .rf-shell .flash.error   { background: #FEF2F2; border-color: #FBCFCF; color: #C81E1E; }

        @media (max-width: 720px) {
            .rf-ticket { flex-direction: column; }
            .rf-ticket-amount {
                border-left: none;
                border-top: 1px dashed #E4E4EA;
                padding-left: 0;
                padding-top: 14px;
                text-align: left;
                width: 100%;
            }
            .rf-banner { flex-wrap: wrap; }
            .rf-banner-action { width: 100%; text-align: center; }
            .rf-actions { flex-direction: column; align-items: stretch; }
            .rf-actions-btns { justify-content: stretch; }
            .rf-actions-btns .rf-btn { flex: 1; justify-content: center; }
        }
    </style>
</head>
<body>

    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="rf-page">
        <div class="rf-shell">

            <nav class="rf-crumb" aria-label="Đường dẫn">
                <a href="${pageContext.request.contextPath}/home">Trang chủ</a>
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg>
                <a href="${pageContext.request.contextPath}/orders">Vé của tôi</a>
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg>
                <span>Xin hoàn tiền</span>
            </nav>

            <header class="rf-head">
                <h1>Xin hoàn tiền vé (trường hợp đặc biệt)</h1>
                <p>Bạn đã mua vé nhưng gặp sự cố đặc biệt nên không thể đến rạp check-in đúng suất chiếu? Hãy gửi giải trình đầy đủ thông tin bên dưới để Ban Quản Lý CineBook kiểm tra và hỗ trợ hoàn tiền.</p>
            </header>

            <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

            <div class="rf-banner" role="note">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M12 8v4"/><path d="M12 16h.01"/></svg>
                <div class="rf-banner-body">
                    <h2>Điều kiện xét duyệt hoàn tiền</h2>
                    <p><a href="${pageContext.request.contextPath}/refund-policy">Đọc toàn bộ điều kiện hoàn tiền</a></p>
                    <p>Chỉ áp dụng cho vé <strong>đã thanh toán thành công</strong>, <strong>chưa check-in</strong> và suất chiếu đã trôi qua. Ban quản lý xử lý trong vòng 24h&nbsp;–&nbsp;48h làm việc.</p>
                </div>
                <a class="rf-banner-action" href="${pageContext.request.contextPath}/orders">Xem vé của tôi</a>
            </div>

            <form action="${pageContext.request.contextPath}/ticket-refund-appeal" method="post">
                <cb:csrf/>

                <section class="rf-card">
                    <div class="rf-card-head">
                        <span class="rf-step" aria-hidden="true">1</span>
                        <h2>Vé cần xin hoàn tiền</h2>
                    </div>
                    <div class="rf-card-body">

                        <c:choose>
                            <c:when test="${not empty targetOrder}">
                                <article class="rf-ticket">
                                    <div class="rf-ticket-main">
                                        <div class="rf-ticket-tags">
                                            <span class="rf-badge is-neutral is-plain">Mã vé #<c:out value="${targetOrder.ticketCode}"/></span>
                                            <%--
                                                Design guide 3.5: khong tron emoji voi icon vector.
                                                getStatusLabel() co the tra ve tien to emoji ("⚠️ ", "⏰ ") —
                                                chi cat phan emoji o tang trinh bay, KHONG sua model.
                                            --%>
                                            <c:set var="rfRawStatus" value="${targetOrder.statusLabel}"/>
                                            <c:set var="rfStatus" value="${fn:startsWith(rfRawStatus, '⚠') or fn:startsWith(rfRawStatus, '⏰')
                                                ? fn:substringAfter(rfRawStatus, ' ') : rfRawStatus}"/>
                                            <span class="rf-badge ${fn:escapeXml(targetOrder.statusBadgeClass eq 'badge-status-cancelled'
                                                    or targetOrder.statusBadgeClass eq 'badge-status-expired' ? 'is-danger'
                                                    : targetOrder.statusBadgeClass eq 'badge-status-redeemed'
                                                      or targetOrder.statusBadgeClass eq 'badge-status-valid' ? 'is-success'
                                                    : targetOrder.statusBadgeClass eq 'badge-status-warning' ? 'is-warning'
                                                    : 'is-neutral')}">
                                                <span class="rf-dot" aria-hidden="true"></span>
                                                <c:out value="${rfStatus}"/>
                                            </span>
                                        </div>
                                        <h3><c:out value="${targetOrder.filmTitle}"/></h3>
                                        <dl class="rf-ticket-meta">
                                            <div class="rf-meta-item">
                                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/></svg>
                                                <div>
                                                    <dt>Rạp / Phòng chiếu</dt>
                                                    <dd><c:out value="${targetOrder.cinemaName}"/> &middot; <c:out value="${targetOrder.roomName}"/></dd>
                                                </div>
                                            </div>
                                            <div class="rf-meta-item">
                                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
                                                <div>
                                                    <dt>Suất chiếu</dt>
                                                    <dd><c:out value="${targetOrder.startTimeDisplay}"/></dd>
                                                </div>
                                            </div>
                                            <div class="rf-meta-item">
                                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 11V6a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v5"/><path d="M3 13a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v4H3Z"/><path d="M6 17v3"/><path d="M18 17v3"/></svg>
                                                <div>
                                                    <dt>Ghế đã đặt</dt>
                                                    <dd><c:out value="${targetOrder.seatsDisplay}"/></dd>
                                                </div>
                                            </div>
                                        </dl>
                                    </div>
                                    <div class="rf-ticket-amount">
                                        <div class="rf-amount-label">Đã thanh toán</div>
                                        <div class="rf-amount-value"><c:out value="${targetOrder.formattedTotalAmount}"/> <span>đ</span></div>
                                        <div class="rf-amount-note">Số tiền tối đa được xem xét hoàn</div>
                                    </div>
                                </article>
                                <input type="hidden" name="ticketCode" value="<c:out value="${targetOrder.ticketCode}"/>">
                            </c:when>

                            <c:when test="${empty missedOrders}">
                                <div class="rf-empty">
                                    <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 9a3 3 0 0 1 0 6v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-2a3 3 0 0 1 0-6V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2Z"/><path d="M13 5v14"/></svg>
                                    <h3>Chưa có vé nào đủ điều kiện xin hoàn tiền</h3>
                                    <p>Chỉ những vé đã thanh toán, chưa check-in và đã qua suất chiếu mới hiện ở đây.</p>
                                    <a class="rf-btn rf-btn-secondary" href="${pageContext.request.contextPath}/orders">
                                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m12 19-7-7 7-7"/><path d="M19 12H5"/></svg>
                                        Quay lại danh sách vé
                                    </a>
                                </div>
                            </c:when>

                            <c:otherwise>
                                <div class="rf-field">
                                    <label for="ticketCodeSelect"><span class="rf-req" aria-hidden="true">*</span>Chọn mã vé cần xin hoàn tiền</label>
                                    <select id="ticketCodeSelect" name="ticketCode" class="rf-input" required>
                                        <option value="">-- Chọn vé trong lịch sử đặt --</option>
                                        <c:forEach var="mo" items="${missedOrders}">
                                            <option value="<c:out value="${mo.ticketCode}"/>" ${fn:escapeXml(mo.ticketCode eq ticketCode ? 'selected' : '')}>
                                                #<c:out value="${mo.ticketCode}"/> - <c:out value="${mo.filmTitle}"/> (<c:out value="${mo.startTimeDisplay}"/>) - <c:out value="${mo.formattedTotalAmount}"/>đ
                                            </option>
                                        </c:forEach>
                                    </select>
                                    <p class="rf-hint">
                                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>
                                        Danh sách chỉ hiển thị các vé đã thanh toán nhưng chưa được check-in.
                                    </p>
                                </div>
                            </c:otherwise>
                        </c:choose>

                    </div>
                </section>

                <section class="rf-card">
                    <div class="rf-card-head">
                        <span class="rf-step" aria-hidden="true">2</span>
                        <h2>Thông tin liên hệ &amp; nhận tiền hoàn</h2>
                    </div>
                    <div class="rf-card-body">

                        <div class="rf-field">
                            <label for="contactPhone"><span class="rf-req" aria-hidden="true">*</span>Số điện thoại liên hệ</label>
                            <input type="tel" id="contactPhone" name="contactPhone" class="rf-input"
                                   placeholder="Nhập SĐT để BQL gọi xác nhận khi cần" required
                                   value="<c:out value="${currentUser.phone}"/>">
                        </div>

                        <div class="rf-field">
                            <label for="bankAccountInfo"><span class="rf-req" aria-hidden="true">*</span>Thông tin tài khoản nhận tiền hoàn</label>
                            <input type="text" id="bankAccountInfo" name="bankAccountInfo" class="rf-input"
                                   placeholder="Ví dụ: MBBank - 0987654321 - NGUYEN VAN A" required>
                            <p class="rf-hint">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect width="20" height="14" x="2" y="5" rx="2"/><path d="M2 10h20"/></svg>
                                Nếu đơn hoàn tiền được duyệt, số tiền sẽ được chuyển khoản trực tiếp vào STK này.
                            </p>
                        </div>

                        <div class="rf-field">
                            <label for="reason"><span class="rf-req" aria-hidden="true">*</span>Lý do sự cố chi tiết không thể check-in</label>
                            <textarea id="reason" name="reason" class="rf-input"
                                      placeholder="Mô tả lý do cá nhân khiến bạn không thể đến rạp đúng giờ (ví dụ: Sự cố xe trên đường, lý do sức khỏe cấp bách, sự cố thời tiết...)" required></textarea>
                            <p class="rf-hint">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>
                                Lý do càng cụ thể, hồ sơ càng được xét duyệt nhanh.
                            </p>
                        </div>

                    </div>

                    <div class="rf-actions">
                        <span class="rf-actions-note">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect width="18" height="11" x="3" y="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                            Thông tin tài khoản chỉ dùng cho mục đích hoàn tiền.
                        </span>
                        <div class="rf-actions-btns">
                            <a class="rf-btn rf-btn-secondary" href="${pageContext.request.contextPath}/orders">Hủy bỏ</a>
                            <button type="submit" class="rf-btn rf-btn-primary" ${fn:escapeXml(empty targetOrder and empty missedOrders ? 'disabled' : '')}>
                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M22 2 11 13"/><path d="M22 2 15 22l-4-9-9-4Z"/></svg>
                                Gửi yêu cầu cho BQL xem xét
                            </button>
                        </div>
                    </div>
                </section>

            </form>

            <section class="rf-card">
                <div class="rf-card-head">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6"/><path d="M9 13h6"/><path d="M9 17h4"/></svg>
                    <h2>Quy trình xử lý yêu cầu</h2>
                </div>
                <div class="rf-table-wrap">
                    <table class="rf-table">
                        <thead>
                            <tr>
                                <th scope="col">Bước</th>
                                <th scope="col">Nội dung</th>
                                <th scope="col">Thời gian dự kiến</th>
                                <th scope="col">Trạng thái</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td>1</td>
                                <td>Bạn gửi yêu cầu kèm lý do và thông tin tài khoản</td>
                                <td>Ngay lập tức</td>
                                <td><span class="rf-badge is-info"><span class="rf-dot" aria-hidden="true"></span>Đang thực hiện</span></td>
                            </tr>
                            <tr>
                                <td>2</td>
                                <td>Ban quản lý đối chiếu mã vé và lịch sử check-in</td>
                                <td>Trong 24h làm việc</td>
                                <td><span class="rf-badge is-warning"><span class="rf-dot" aria-hidden="true"></span>Chờ duyệt</span></td>
                            </tr>
                            <tr>
                                <td>3</td>
                                <td>Phản hồi kết quả qua email và số điện thoại đã đăng ký</td>
                                <td>24h – 48h làm việc</td>
                                <td><span class="rf-badge is-neutral"><span class="rf-dot" aria-hidden="true"></span>Chưa bắt đầu</span></td>
                            </tr>
                            <tr>
                                <td>4</td>
                                <td>Chuyển khoản hoàn tiền nếu yêu cầu được chấp thuận</td>
                                <td>Sau khi duyệt</td>
                                <td><span class="rf-badge is-success"><span class="rf-dot" aria-hidden="true"></span>Hoàn tất</span></td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </section>

        </div>
    </main>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>

</body>
</html>
