<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="cbf" uri="https://cinebook.local/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Vé của tôi - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <style>
        /* Modern Ticket Card Redesign */
        .ticket-card {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 18px;
            padding: 24px;
            margin-bottom: 20px;
            box-shadow: 0 4px 20px rgba(15, 23, 42, 0.05);
            display: flex;
            justify-content: space-between;
            gap: 24px;
            align-items: flex-start;
            transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
            position: relative;
        }

        .ticket-card:hover {
            border-color: #cbd5e1;
            box-shadow: 0 10px 30px rgba(15, 23, 42, 0.09);
        }

        .ticket-main-col {
            flex: 1;
            display: flex;
            flex-direction: column;
            gap: 12px;
            min-width: 0;
        }

        .ticket-tag-eyebrow {
            display: inline-block;
            font-size: 11px;
            font-weight: 800;
            text-transform: uppercase;
            letter-spacing: 0.8px;
            color: #ea580c;
            background: #fff7ed;
            padding: 3px 10px;
            border-radius: 6px;
            border: 1px solid #ffedd5;
            width: fit-content;
        }

        .ticket-film-title {
            font-size: 20px;
            font-weight: 800;
            color: #0f172a;
            margin: 0;
            line-height: 1.3;
            letter-spacing: -0.01em;
        }

        .ticket-showtime-meta {
            font-size: 13.5px;
            font-weight: 600;
            color: #475569;
            display: flex;
            align-items: center;
            gap: 8px;
            flex-wrap: wrap;
        }

        .meta-pill {
            background: #f1f5f9;
            padding: 3px 10px;
            border-radius: 6px;
            color: #334155;
        }

        .ticket-meta-badges {
            display: flex;
            align-items: center;
            gap: 10px;
            flex-wrap: wrap;
        }

        .ticket-code-pill {
            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
            font-size: 12.5px;
            font-weight: 700;
            color: #1e293b;
            background: #f8fafc;
            border: 1px solid #e2e8f0;
            padding: 4px 12px;
            border-radius: 8px;
        }

        .ticket-details-grid {
            display: flex;
            flex-direction: column;
            gap: 10px;
            background: #f8fafc;
            border: 1px solid #f1f5f9;
            border-radius: 14px;
            padding: 16px;
            margin: 4px 0;
        }

        .detail-item {
            display: flex;
            align-items: center;
            gap: 10px;
            font-size: 13.5px;
            color: #334155;
        }

        .detail-label {
            font-weight: 700;
            color: #64748b;
            min-width: 85px;
        }

        .seat-badge {
            background: #ffffff;
            border: 1px solid #cbd5e1;
            color: #0f172a;
            font-weight: 700;
            padding: 2px 8px;
            border-radius: 6px;
            font-size: 13px;
        }

        .price-highlight-box {
            display: inline-flex;
            align-items: center;
            gap: 6px;
        }

        .price-value {
            font-size: 16px;
            font-weight: 800;
            color: #ea580c;
            background: #fff7ed;
            border: 1px solid #ffedd5;
            padding: 3px 10px;
            border-radius: 8px;
            letter-spacing: -0.01em;
        }

        /* Right QR Column */
        .ticket-qr-col {
            flex-shrink: 0;
        }

        .ticket-qr-card {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 16px;
            padding: 16px;
            text-align: center;
            box-shadow: 0 4px 14px rgba(0, 0, 0, 0.04);
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 10px;
            width: 160px;
        }

        .ticket-qr-img {
            width: 130px;
            height: 130px;
            border-radius: 8px;
            object-fit: contain;
            display: block;
        }

        .qr-subtext {
            font-size: 11px;
            font-weight: 600;
            color: #64748b;
            line-height: 1.3;
        }

        .btn-download-qr {
            width: 100%;
            background: #f1f5f9;
            color: #0f172a;
            border: 1px solid #cbd5e1;
            padding: 7px 12px;
            border-radius: 8px;
            font-size: 12px;
            font-weight: 700;
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            transition: all 0.2s ease;
            text-decoration: none;
        }

        .btn-download-qr:hover {
            background: #ea580c;
            color: #ffffff;
            border-color: #ea580c;
            box-shadow: 0 4px 12px rgba(234, 88, 12, 0.25);
        }

        /* Sleek Custom Confirm Modal */
        .custom-modal-backdrop {
            position: fixed;
            inset: 0;
            background: rgba(15, 23, 42, 0.65);
            backdrop-filter: blur(5px);
            z-index: 99999;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.2s cubic-bezier(0.16, 1, 0.3, 1);
        }

        .custom-modal-backdrop.is-active {
            opacity: 1;
            pointer-events: auto;
        }

        .custom-modal-box {
            background: #ffffff;
            width: 100%;
            max-width: 420px;
            border-radius: 22px;
            padding: 28px 24px 22px 24px;
            text-align: center;
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.28);
            transform: scale(0.92);
            transition: transform 0.22s cubic-bezier(0.175, 0.885, 0.32, 1.275);
        }

        .custom-modal-backdrop.is-active .custom-modal-box {
            transform: scale(1);
        }

        .custom-modal-icon-wrap {
            width: 58px;
            height: 58px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            margin: 0 auto 16px auto;
            background: #fff1f2;
            color: #e11d48;
            box-shadow: 0 4px 14px rgba(225, 29, 72, 0.15);
        }

        .custom-modal-title {
            margin: 0 0 8px 0;
            color: #0f172a;
            font-size: 19px;
            font-weight: 800;
            letter-spacing: -0.01em;
        }

        .custom-modal-desc {
            margin: 0 0 6px 0;
            color: #334155;
            font-size: 14px;
            font-weight: 600;
            line-height: 1.5;
        }

        .custom-modal-subnote {
            margin: 6px 0 16px 0;
            color: #64748b;
            font-size: 12.5px;
            line-height: 1.4;
            background: #f8fafc;
            border: 1px solid #f1f5f9;
            padding: 10px 14px;
            border-radius: 12px;
            text-align: left;
        }

        .custom-modal-actions {
            display: flex;
            gap: 10px;
            margin-top: 18px;
        }

        .custom-modal-btn {
            flex: 1;
            padding: 11px 16px;
            font-size: 14px;
            font-weight: 700;
            border-radius: 12px;
            cursor: pointer;
            border: none;
            transition: all 0.18s ease;
        }

        .custom-modal-btn.cancel {
            background: #f1f5f9;
            color: #475569;
            border: 1px solid #cbd5e1;
        }

        .custom-modal-btn.cancel:hover {
            background: #e2e8f0;
            color: #0f172a;
        }

        .custom-modal-btn.confirm {
            background: #ea580c;
            color: #ffffff;
            box-shadow: 0 4px 14px rgba(234, 88, 12, 0.25);
        }

        .custom-modal-btn.confirm:hover {
            background: #c2410c;
            box-shadow: 0 6px 18px rgba(234, 88, 12, 0.35);
        }

        .custom-modal-btn.confirm.danger {
            background: #e11d48;
            box-shadow: 0 4px 14px rgba(225, 29, 72, 0.25);
        }

        .custom-modal-btn.confirm.danger:hover {
            background: #be123c;
            box-shadow: 0 6px 18px rgba(225, 29, 72, 0.35);
        }
    </style>
</head>
<body class="public-page">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="container public-main">
        <%@ include file="/WEB-INF/views/shared/flash.jspf" %>
        <c:if test="${not empty flashSuccess}">
            <!-- SLEEK POPUP NOTIFICATION MODAL -->
            <div id="successNoticeModal" style="position: fixed; inset: 0; background: rgba(15, 23, 42, 0.65); backdrop-filter: blur(4px); z-index: 99999; display: flex; align-items: center; justify-content: center; opacity: 1; transition: opacity 0.25s ease;">
                <div style="background: #ffffff; width: 90%; max-width: 400px; border-radius: 20px; padding: 32px 24px 24px 24px; text-align: center; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25); transform: scale(1); animation: modalPop 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);">
                    <div style="width: 64px; height: 64px; background: #dcfce7; border-radius: 50%; color: #16a34a; font-size: 32px; display: flex; align-items: center; justify-content: center; margin: 0 auto 18px auto; box-shadow: 0 4px 12px rgba(22, 163, 74, 0.15);">
                        ✓
                    </div>
                    <h3 style="margin: 0 0 10px 0; color: #0f172a; font-size: 20px; font-weight: 700; letter-spacing: -0.02em;">Đã xóa thành công!</h3>
                    <p style="margin: 0 0 24px 0; color: #64748b; font-size: 14px; line-height: 1.5;">${fn:escapeXml(flashSuccess)}</p>
                    <button type="button" onclick="closeSuccessNoticeModal()" style="width: 100%; padding: 12px; font-size: 15px; font-weight: 700; background: #ea580c; color: #ffffff; border: none; border-radius: 12px; cursor: pointer; transition: all 0.2s ease; box-shadow: 0 4px 12px rgba(234, 88, 12, 0.25);">
                        Đồng ý
                    </button>
                </div>
            </div>
            <script>
                function closeSuccessNoticeModal() {
                    var modal = document.getElementById('successNoticeModal');
                    if (modal) {
                        modal.style.opacity = '0';
                        setTimeout(function() { modal.remove(); }, 250);
                    }
                }
            </script>
        </c:if>
        <section class="page-hero">
            <span class="eyebrow">My tickets</span>
            <h1 class="section-title">Vé của tôi</h1>
            <p class="lead-copy">Theo dõi vé sắp xem, đơn đã giữ ghế và lịch sử vé đã xem/đã qua thời gian check-in.</p>
        </section>

        <section class="section">
            <p><a href="${pageContext.request.contextPath}/refund-policy">Điều kiện hoàn tiền</a></p>
            <!-- TICKET CATEGORY TABS -->
            <div class="ticket-tabs">
                <button id="tabBtnCurrent" class="ticket-tab-btn active" onclick="switchTicketTab('current')">
                    🎟️ Vé hiện tại <span class="tab-count">${fn:escapeXml(currentOrders.size())}</span>
                </button>
                <button id="tabBtnPast" class="ticket-tab-btn" onclick="switchTicketTab('past')">
                    📜 Vé đã sử dụng / Lịch sử <span class="tab-count">${fn:escapeXml(pastOrders.size())}</span>
                </button>
            </div>

            <!-- TAB 1: VÉ HIỆN TẠI (CURRENT TICKETS) -->
            <div id="currentTicketsTab" class="history-list">
                <c:forEach var="order" items="${currentOrders}">
                    <article class="ticket-card reveal">
                        <div class="ticket-main-col">
                            <div class="ticket-header-row">
                                <span class="ticket-tag-eyebrow">TICKET</span>
                                <h3 class="ticket-film-title">${fn:escapeXml(order.filmTitle)}</h3>
                            </div>

                            <div class="ticket-showtime-meta">
                                <span class="meta-pill">📍 ${fn:escapeXml(order.cinemaName)} · ${fn:escapeXml(order.roomName)}</span>
                                <span class="meta-pill">🕒 ${fn:escapeXml(order.startTimeDisplay)}</span>
                            </div>

                            <div class="ticket-meta-badges">
                                <span class="ticket-code-pill">Mã vé: ${fn:escapeXml(order.ticketCode)}</span>
                                <span class="badge ${fn:escapeXml(order.statusBadgeClass)}">${fn:escapeXml(order.statusLabel)}</span>
                            </div>

                            <div class="ticket-details-grid">
                                <div class="detail-item">
                                    <span class="detail-label">Ghế ngồi:</span>
                                    <span class="detail-value"><span class="seat-badge">${fn:escapeXml(order.seatSummary)}</span></span>
                                </div>
                                <c:if test="${not empty order.comboSummary}">
                                    <div class="detail-item">
                                        <span class="detail-label">Combo:</span>
                                        <span class="detail-value">${fn:escapeXml(order.comboSummary)}</span>
                                    </div>
                                </c:if>
                                <div class="detail-item">
                                    <span class="detail-label">Tổng tiền:</span>
                                    <span class="price-highlight-box">
                                        <span class="price-value">${cbf:whole(order.totalAmount)} VNĐ</span>
                                    </span>
                                </div>
                            </div>

                            <c:if test="${order.paymentStatus eq 'paid' or order.paymentStatus eq 'refunded'}">
                                <div>
                                    <a href="${pageContext.request.contextPath}/invoices/${fn:escapeXml(order.id)}" target="_blank" style="display:inline-flex; align-items:center; gap:6px; color:#ea580c; font-weight:700; text-decoration:none; font-size:13px;" title="Tải / Xem Hóa đơn PDF">
                                        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;">
                                            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                                            <polyline points="7 10 12 15 17 10"/>
                                            <line x1="12" y1="15" x2="12" y2="3"/>
                                        </svg>
                                        <span style="text-decoration:underline; text-underline-offset:3px;">Tải hóa đơn PDF</span>
                                    </a>
                                </div>
                            </c:if>

                            <c:if test="${order.paymentStatus eq 'pending' and order.orderStatus ne 'cancelled'}">
                                <div style="margin-top: 8px; padding: 12px 14px; background: #fff7ed; border: 1px solid #ffedd5; border-radius: 12px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px;">
                                    <div style="font-size: 13px; color: #c2410c; font-weight: 600;">
                                        ⏳ Vé đang chờ thanh toán (Giữ chỗ có thời hạn)
                                    </div>
                                    <div style="display: flex; gap: 8px; align-items: center;">
                                        <a href="${pageContext.request.contextPath}/booking?showtimeId=${fn:escapeXml(order.showtimeId)}&orderId=${fn:escapeXml(order.id)}" class="btn" style="padding: 6px 14px; font-size: 13px; font-weight: 700; background: #ea580c; color: #ffffff; text-decoration: none; border-radius: 8px; display: inline-block;">💳 Thanh toán ngay</a>
                                        <form id="cancelOrderForm_${order.id}" method="post" action="${pageContext.request.contextPath}/orders/${fn:escapeXml(order.id)}/cancel" style="display: inline;">
                                            <%@ include file="/WEB-INF/views/shared/csrf.jspf" %>
                                            <button type="button" onclick="confirmCancelPending('${order.id}')" class="btn" style="padding: 6px 14px; font-size: 13px; font-weight: 600; background: #ef4444; color: #ffffff; border: none; border-radius: 8px; cursor: pointer;">❌ Hủy vé</button>
                                        </form>
                                    </div>
                                </div>
                            </c:if>

                            <div style="font-size: 12px; color: #94a3b8; font-weight: 500;">Đặt lúc ${fn:escapeXml(order.createdAtDisplay)}</div>
                        </div>

                        <div class="ticket-qr-col">
                            <c:if test="${(order.paymentStatus eq 'paid' or order.paymentMethod eq 'counter') and order.orderStatus ne 'cancelled'}">
                                <div class="ticket-qr-card">
                                    <img class="ticket-qr-img" src="${pageContext.request.contextPath}/tickets/qr/${fn:escapeXml(order.ticketCode)}" alt="QR ${fn:escapeXml(order.ticketCode)}">
                                    <span class="qr-subtext">Đưa mã QR cho nhân viên quét</span>
                                    <button type="button" class="btn-download-qr" onclick="downloadQRCode('${pageContext.request.contextPath}/tickets/qr/${fn:escapeXml(order.ticketCode)}', 'CineBook_QR_${fn:escapeXml(order.ticketCode)}.png')" title="Lưu mã QR xuống máy">
                                        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                                            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                                            <polyline points="7 10 12 15 17 10"/>
                                            <line x1="12" y1="15" x2="12" y2="3"/>
                                        </svg>
                                        <span>Lưu mã QR</span>
                                    </button>
                                </div>
                            </c:if>
                        </div>
                    </article>
                </c:forEach>
                <c:if test="${empty currentOrders}">
                    <article class="panel" style="text-align: center; padding: 48px 24px; background: #ffffff; border-radius: 18px;">
                        <div style="font-size: 40px; margin-bottom: 8px;">🎟️</div>
                        <h3 style="margin: 0 0 6px 0; color: #0f172a;">Không có vé hiện tại nào</h3>
                        <p class="muted" style="margin: 0 0 16px 0;">Bạn chưa có vé mới mua hoặc vé chưa đến giờ chiếu.</p>
                        <a href="${pageContext.request.contextPath}/booking" class="btn btn-primary" style="padding: 8px 18px;">➕ Mua vé ngay</a>
                    </article>
                </c:if>
            </div>

            <!-- TAB 2: VÉ ĐÃ SỬ DỤNG / LỊCH SỬ (PAST / USED TICKETS) -->
            <div id="pastTicketsTab" class="history-list" style="display: none;">
                <c:if test="${not empty pastOrders}">
                    <!-- EMAIL-STYLE BULK ACTION TOOLBAR -->
                    <form id="batchDeleteForm" method="post" action="${pageContext.request.contextPath}/orders/batch-hide-history" onsubmit="return confirmBatchDelete(event);">
                        <%@ include file="/WEB-INF/views/shared/csrf.jspf" %>
                        <div style="background: #ffffff; border: 1px solid #e2e8f0; border-radius: 14px; padding: 14px 18px; margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <label style="display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 14px; color: #334155; cursor: pointer; user-select: none;">
                                    <input type="checkbox" id="selectAllHistoryCbs" onchange="toggleSelectAllHistory(this)" style="width: 18px; height: 18px; cursor: pointer; accent-color: #ea580c;">
                                    <span>Chọn tất cả (${fn:escapeXml(pastOrders.size())})</span>
                                </label>
                            </div>
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button type="submit" id="btnDeleteSelected" disabled style="padding: 8px 16px; font-size: 13px; font-weight: 600; border-radius: 8px; border: none; background: #cbd5e1; color: #64748b; cursor: not-allowed; transition: all 0.2s ease;">
                                    🗑️ Xóa các vé đã chọn (<span id="selectedCount">0</span>)
                                </button>
                                <button type="button" onclick="confirmDeleteAllHistory()" style="padding: 8px 16px; font-size: 13px; font-weight: 600; border-radius: 8px; border: 1px solid #fecdd3; background: #fff1f2; color: #e11d48; cursor: pointer; transition: all 0.2s ease;">
                                    🧹 Xóa tất cả lịch sử vé
                                </button>
                            </div>
                        </div>

                        <c:forEach var="order" items="${pastOrders}">
                            <article class="ticket-card reveal" style="opacity: 0.94; position: relative;">
                                <div style="position: absolute; top: 20px; left: 20px; z-index: 2;">
                                    <input type="checkbox" name="orderIds" value="${order.id}" class="history-item-cb" onchange="updateHistorySelectionCount()" style="width: 20px; height: 20px; cursor: pointer; accent-color: #ea580c;">
                                </div>
                                <div class="ticket-main-col" style="padding-left: 32px;">
                                    <div class="ticket-header-row">
                                        <span class="ticket-tag-eyebrow" style="background:#f1f5f9; color:#64748b; border-color:#e2e8f0;">HISTORY TICKET</span>
                                        <h3 class="ticket-film-title">${fn:escapeXml(order.filmTitle)}</h3>
                                    </div>

                                    <div class="ticket-showtime-meta">
                                        <span class="meta-pill">📍 ${fn:escapeXml(order.cinemaName)} · ${fn:escapeXml(order.roomName)}</span>
                                        <span class="meta-pill">🕒 ${fn:escapeXml(order.startTimeDisplay)}</span>
                                    </div>

                                    <div class="ticket-meta-badges">
                                        <span class="ticket-code-pill">Mã vé: ${fn:escapeXml(order.ticketCode)}</span>
                                        <span class="badge ${fn:escapeXml(order.statusBadgeClass)}">${fn:escapeXml(order.statusLabel)}</span>
                                    </div>

                                    <div class="ticket-details-grid">
                                        <div class="detail-item">
                                            <span class="detail-label">Ghế ngồi:</span>
                                            <span class="detail-value"><span class="seat-badge">${fn:escapeXml(order.seatSummary)}</span></span>
                                        </div>
                                        <c:if test="${not empty order.comboSummary}">
                                            <div class="detail-item">
                                                <span class="detail-label">Combo:</span>
                                                <span class="detail-value">${fn:escapeXml(order.comboSummary)}</span>
                                            </div>
                                        </c:if>
                                        <div class="detail-item">
                                            <span class="detail-label">Tổng tiền:</span>
                                            <span class="price-highlight-box">
                                                <span class="price-value" style="background:#f8fafc; color:#334155; border-color:#e2e8f0;">${cbf:whole(order.totalAmount)} VNĐ</span>
                                            </span>
                                        </div>
                                    </div>

                                    <c:if test="${order.paymentStatus eq 'paid' or order.paymentStatus eq 'refunded'}">
                                        <div>
                                            <a href="${pageContext.request.contextPath}/invoices/${fn:escapeXml(order.id)}" target="_blank" style="display:inline-flex; align-items:center; gap:6px; color:#ea580c; font-weight:700; text-decoration:none; font-size:13px;" title="Tải / Xem Hóa đơn PDF">
                                                <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;">
                                                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                                                    <polyline points="7 10 12 15 17 10"/>
                                                    <line x1="12" y1="15" x2="12" y2="3"/>
                                                </svg>
                                                <span style="text-decoration:underline; text-underline-offset:3px;">Tải hóa đơn PDF</span>
                                            </a>
                                        </div>
                                    </c:if>

                                    <c:if test="${order.refundAppealEligible}">
                                        <div style="margin-top: 8px; padding: 10px 14px; background: #fff1f2; border: 1px solid #fecdd3; border-radius: 10px;">
                                            <div style="font-size: 12.5px; color: #9f1239; margin-bottom: 6px; font-weight: 500;">
                                                ⚠️ Bạn chưa kịp check-in cho suất chiếu này?
                                            </div>
                                            <a href="${pageContext.request.contextPath}/ticket-refund-appeal?ticketCode=${fn:escapeXml(order.ticketCode)}" class="btn" style="font-size:12px; padding:6px 14px; background:#e11d48; color:#ffffff; border:none; border-radius:8px; display:inline-block; text-decoration:none; font-weight:600;">
                                                📩 Gửi xin hoàn tiền (Trường hợp đặc biệt)
                                            </a>
                                        </div>
                                    </c:if>

                                    <c:if test="${order.orderStatus eq 'confirmed' and order.paymentStatus eq 'paid' and empty order.redeemedAt and not order.refundAppealEligible}">
                                        <div role="status" style="margin-top:8px;padding:10px 14px;background:#fff7ed;border:1px solid #fed7aa;border-radius:10px;color:#9a3412;font-size:13px;font-weight:600;">
                                            ${fn:escapeXml(order.refundAppealMessage)}
                                        </div>
                                    </c:if>

                                    <div style="display: flex; align-items: center; justify-content: space-between; margin-top: 4px; flex-wrap: wrap; gap: 8px;">
                                        <span style="font-size: 12px; color: #94a3b8; font-weight: 500;">Đặt lúc ${fn:escapeXml(order.createdAtDisplay)}</span>
                                        <button type="button" onclick="hideSingleHistoryOrder('${order.id}')" class="btn" style="font-size: 12px; padding: 5px 12px; background: #f8fafc; color: #64748b; border: 1px solid #cbd5e1; border-radius: 8px; font-weight: 600; cursor: pointer;">
                                            🗑️ Xóa vé này
                                        </button>
                                    </div>
                                </div>

                                <div class="ticket-qr-col">
                                    <c:if test="${order.paymentStatus eq 'paid' and order.orderStatus ne 'cancelled'}">
                                        <div class="ticket-qr-card">
                                            <c:set var="qrSrc" value="${empty order.ticketQrUrl ? pageContext.request.contextPath.concat('/tickets/qr/').concat(order.ticketCode) : (fn:startsWith(order.ticketQrUrl, '/') ? pageContext.request.contextPath.concat(order.ticketQrUrl) : order.ticketQrUrl)}" />
                                            <img class="ticket-qr-img" style="filter: grayscale(80%);" src="${fn:escapeXml(qrSrc)}" alt="QR ${fn:escapeXml(order.ticketCode)}">
                                            <span class="qr-subtext">Mã QR đã lưu</span>
                                            <button type="button" class="btn-download-qr" onclick="downloadQRCode('${fn:escapeXml(qrSrc)}', 'CineBook_QR_${fn:escapeXml(order.ticketCode)}.png')" title="Lưu mã QR xuống máy">
                                                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                                                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                                                    <polyline points="7 10 12 15 17 10"/>
                                                    <line x1="12" y1="15" x2="12" y2="3"/>
                                                </svg>
                                                <span>Lưu mã QR</span>
                                            </button>
                                        </div>
                                    </c:if>
                                </div>
                            </article>
                        </c:forEach>
                    </form>

                    <!-- HIDDEN FORM FOR SINGLE & ALL DELETE -->
                    <form id="singleDeleteForm" method="post" action="" style="display:none;">
                        <%@ include file="/WEB-INF/views/shared/csrf.jspf" %>
                    </form>
                    <form id="deleteAllHistoryForm" method="post" action="${pageContext.request.contextPath}/orders/batch-hide-history?action=all" style="display:none;">
                        <%@ include file="/WEB-INF/views/shared/csrf.jspf" %>
                    </form>
                </c:if>

                <c:if test="${empty pastOrders}">
                    <article class="panel" style="text-align: center; padding: 48px 24px; background: #ffffff; border-radius: 18px;">
                        <div style="font-size: 40px; margin-bottom: 8px;">📜</div>
                        <h3 style="margin: 0 0 6px 0; color: #0f172a;">Chưa có vé nào trong lịch sử</h3>
                        <p class="muted">Các vé đã qua giờ chiếu, đã check-in hoặc bị hủy sẽ xuất hiện tại đây.</p>
                    </article>
                </c:if>
            </div>
        </section>
    </main>

    <!-- SLEEK MINIMALIST CUSTOM CONFIRM MODAL -->
    <div id="customConfirmModal" class="custom-modal-backdrop" style="display: none;" onclick="if(event.target===this) closeCustomConfirm(false);">
        <div class="custom-modal-box">
            <div class="custom-modal-icon-wrap">
                <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 1-1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                    <line x1="12" y1="9" x2="12" y2="13"/>
                    <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
            </div>
            <h3 id="customConfirmTitle" class="custom-modal-title">Xác nhận thao tác</h3>
            <p id="customConfirmMessage" class="custom-modal-desc">Bạn có chắc chắn muốn thực hiện thao tác này?</p>
            <p id="customConfirmSubnote" class="custom-modal-subnote" style="display:none;"></p>
            <div class="custom-modal-actions">
                <button type="button" id="customConfirmCancelBtn" class="custom-modal-btn cancel" onclick="closeCustomConfirm(false)">Hủy bỏ</button>
                <button type="button" id="customConfirmOkBtn" class="custom-modal-btn confirm" onclick="closeCustomConfirm(true)">Xác nhận</button>
            </div>
        </div>
    </div>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>
    <script src="${pageContext.request.contextPath}/assets/js/main.js"></script>
    <script>
        function switchTicketTab(tab) {
            var currentTab = document.getElementById('currentTicketsTab');
            var pastTab = document.getElementById('pastTicketsTab');
            var btnCurrent = document.getElementById('tabBtnCurrent');
            var btnPast = document.getElementById('tabBtnPast');

            if (tab === 'current') {
                currentTab.style.display = 'block';
                pastTab.style.display = 'none';
                btnCurrent.classList.add('active');
                btnPast.classList.remove('active');
            } else {
                currentTab.style.display = 'none';
                pastTab.style.display = 'block';
                btnCurrent.classList.remove('active');
                btnPast.classList.add('active');
            }
        }

        function toggleSelectAllHistory(masterCb) {
            var cbs = document.querySelectorAll('.history-item-cb');
            cbs.forEach(function(cb) { cb.checked = masterCb.checked; });
            updateHistorySelectionCount();
        }

        function updateHistorySelectionCount() {
            var cbs = document.querySelectorAll('.history-item-cb:checked');
            var totalCbs = document.querySelectorAll('.history-item-cb');
            var masterCb = document.getElementById('selectAllHistoryCbs');
            var countSpan = document.getElementById('selectedCount');
            var deleteBtn = document.getElementById('btnDeleteSelected');

            if (countSpan) countSpan.textContent = cbs.length;
            if (masterCb && totalCbs.length > 0) {
                masterCb.checked = (cbs.length === totalCbs.length);
            }

            if (deleteBtn) {
                if (cbs.length > 0) {
                    deleteBtn.disabled = false;
                    deleteBtn.style.background = '#ef4444';
                    deleteBtn.style.color = '#ffffff';
                    deleteBtn.style.cursor = 'pointer';
                } else {
                    deleteBtn.disabled = true;
                    deleteBtn.style.background = '#cbd5e1';
                    deleteBtn.style.color = '#64748b';
                    deleteBtn.style.cursor = 'not-allowed';
                }
            }
        }

        var confirmCallback = null;

        function showCustomConfirm(options) {
            var modal = document.getElementById('customConfirmModal');
            var titleEl = document.getElementById('customConfirmTitle');
            var descEl = document.getElementById('customConfirmMessage');
            var subnoteEl = document.getElementById('customConfirmSubnote');
            var okBtn = document.getElementById('customConfirmOkBtn');
            var cancelBtn = document.getElementById('customConfirmCancelBtn');

            if (!modal) return;

            titleEl.textContent = options.title || 'Xác nhận thao tác';
            descEl.textContent = options.message || 'Bạn có chắc chắn muốn thực hiện thao tác này?';

            if (options.subnote) {
                subnoteEl.textContent = options.subnote;
                subnoteEl.style.display = 'block';
            } else {
                subnoteEl.style.display = 'none';
            }

            okBtn.textContent = options.confirmText || 'Xác nhận';
            cancelBtn.textContent = options.cancelText || 'Hủy bỏ';

            if (options.isDanger !== false) {
                okBtn.className = 'custom-modal-btn confirm danger';
            } else {
                okBtn.className = 'custom-modal-btn confirm';
            }

            confirmCallback = options.onConfirm || null;

            modal.style.display = 'flex';
            requestAnimationFrame(function() {
                modal.classList.add('is-active');
            });
        }

        function closeCustomConfirm(result) {
            var modal = document.getElementById('customConfirmModal');
            if (!modal) return;

            modal.classList.remove('is-active');
            setTimeout(function() {
                modal.style.display = 'none';
                if (result && typeof confirmCallback === 'function') {
                    confirmCallback();
                }
                confirmCallback = null;
            }, 200);
        }

        function confirmCancelPending(orderId) {
            showCustomConfirm({
                title: 'Hủy đơn giữ chỗ',
                message: 'Bạn có chắc chắn muốn hủy đơn giữ chỗ này không?',
                subnote: 'Lưu ý: Ghế giữ chỗ sẽ được giải phóng ngay lập tức cho các khách hàng khác chọn mua.',
                confirmText: 'Hủy vé ngay',
                cancelText: 'Quay lại',
                isDanger: true,
                onConfirm: function() {
                    var form = document.getElementById('cancelOrderForm_' + orderId);
                    if (form) form.submit();
                }
            });
        }

        function confirmBatchDelete(event) {
            if (event) event.preventDefault();
            var cbs = document.querySelectorAll('.history-item-cb:checked');
            if (cbs.length === 0) return false;

            showCustomConfirm({
                title: 'Xóa vé khỏi lịch sử',
                message: 'Bạn có chắc chắn muốn xóa ' + cbs.length + ' vé đã chọn khỏi lịch sử hiển thị của bạn không?',
                subnote: 'Lưu ý: Thao tác này chỉ ẩn vé khỏi danh sách lịch sử cá nhân. Mọi hóa đơn và báo cáo doanh thu hệ thống vẫn được lưu giữ an toàn.',
                confirmText: 'Xóa ' + cbs.length + ' vé',
                cancelText: 'Hủy bỏ',
                isDanger: true,
                onConfirm: function() {
                    document.getElementById('batchDeleteForm').submit();
                }
            });
            return false;
        }

        function hideSingleHistoryOrder(orderId) {
            showCustomConfirm({
                title: 'Xóa vé khỏi lịch sử',
                message: 'Bạn có chắc chắn muốn xóa vé này khỏi lịch sử hiển thị của bạn không?',
                subnote: 'Lưu ý: Thao tác này chỉ ẩn vé khỏi danh sách lịch sử cá nhân. Mọi hóa đơn và báo cáo doanh thu hệ thống vẫn được lưu giữ an toàn.',
                confirmText: 'Xóa vé này',
                cancelText: 'Hủy bỏ',
                isDanger: true,
                onConfirm: function() {
                    var form = document.getElementById('singleDeleteForm');
                    form.action = '${pageContext.request.contextPath}/orders/' + orderId + '/hide-history';
                    form.submit();
                }
            });
        }

        function confirmDeleteAllHistory() {
            showCustomConfirm({
                title: 'Xóa toàn bộ lịch sử vé',
                message: 'Bạn có chắc chắn muốn XÓA TOÀN BỘ LỊCH SỬ VÉ không?',
                subnote: 'Lưu ý: Tất cả vé trong lịch sử sẽ bị ẩn khỏi trang cá nhân của bạn. Dữ liệu kế toán doanh thu hệ thống vẫn được bảo lưu 100%.',
                confirmText: 'Xóa tất cả',
                cancelText: 'Hủy bỏ',
                isDanger: true,
                onConfirm: function() {
                    document.getElementById('deleteAllHistoryForm').submit();
                }
            });
        }

        function downloadQRCode(qrUrl, fileName) {
            if (!qrUrl) return;
            fetch(qrUrl)
                .then(function(res) {
                    if (!res.ok) throw new Error('Network error');
                    return res.blob();
                })
                .then(function(blob) {
                    var url = URL.createObjectURL(blob);
                    var a = document.createElement('a');
                    a.href = url;
                    a.download = fileName || 'CineBook_QRCode.png';
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                    setTimeout(function() { URL.revokeObjectURL(url); }, 1000);
                })
                .catch(function() {
                    var a = document.createElement('a');
                    a.href = qrUrl;
                    a.download = fileName || 'CineBook_QRCode.png';
                    a.target = '_blank';
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                });
        }

        document.addEventListener('DOMContentLoaded', function() {
            var currentCards = document.querySelectorAll('#currentTicketsTab .ticket-card');
            var pastCards = document.querySelectorAll('#pastTicketsTab .ticket-card');
            if (currentCards.length === 0 && pastCards.length > 0) {
                switchTicketTab('past');
            }
        });
    </script>
</body>
</html>
