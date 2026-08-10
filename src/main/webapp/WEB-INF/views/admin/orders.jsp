<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="cbf" uri="https://cinebook.local/functions" %>
<c:set var="activeTab" value="${not empty param.tab ? param.tab : 'pending'}" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Quản lý đơn vé - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
    <style>
        .ticket-tab-btn {
            padding: 10px 18px;
            font-size: 14px;
            font-weight: 600;
            border-radius: 8px;
            border: 1px solid #cbd5e1;
            background-color: #f8fafc;
            color: #475569;
            cursor: pointer;
            transition: all 0.2s ease-in-out;
            display: inline-flex;
            align-items: center;
            gap: 8px;
            text-decoration: none;
            outline: none !important;
        }
        .ticket-tab-btn:hover {
            background-color: #f1f5f9;
            border-color: #94a3b8;
            color: #0f172a;
            text-decoration: none;
        }
        .ticket-tab-btn:focus, .ticket-tab-btn:focus-visible {
            outline: none !important;
            box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.35);
        }
        .ticket-tab-btn.active {
            background-color: #ffedd5 !important;
            border-color: #f97316 !important;
            color: #c2410c !important;
            font-weight: 700;
            box-shadow: 0 2px 4px rgba(249, 115, 22, 0.15);
        }
        .ticket-tab-btn .tab-count {
            background: #e2e8f0;
            color: #334155;
            padding: 2px 8px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: 700;
        }
        .ticket-tab-btn.active .tab-count {
            background: #ea580c;
            color: #ffffff;
        }
    </style>
    <script>
        function switchAdminOrderTab(tabName) {
            var navigationUrl = new URL(window.location.href);
            navigationUrl.searchParams.set('tab', tabName);
            navigationUrl.searchParams.set('page', '1');
            window.location.assign(navigationUrl.toString());
            return;
            var tabs = ['pending', 'late', 'refund', 'rejected', 'redeemed', 'cancelled'];
            tabs.forEach(function(t) {
                var content = document.getElementById('adminTabContent_' + t);
                var btn = document.getElementById('adminTabBtn_' + t);
                if (content) {
                    content.style.display = (t === tabName) ? '' : 'none';
                }
                if (btn) {
                    if (t === tabName) {
                        btn.classList.add('active');
                    } else {
                        btn.classList.remove('active');
                    }
                }
            });
            var tabInput = document.getElementById('adminSelectedTabInput');
            if (tabInput) {
                tabInput.value = tabName;
            }
            if (window.history && window.history.replaceState) {
                var url = new URL(window.location.href);
                url.searchParams.set('tab', tabName);
                window.history.replaceState(null, '', url.toString());
            }
        }
    </script>
</head>
<body class="admin-body">
    <div class="dashboard">
        <%@ include file="/WEB-INF/views/admin/sidebar.jspf" %>
        <main class="dashboard-main">
            <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
            <div class="dashboard-content">
                <%@ include file="/WEB-INF/views/shared/flash.jspf" %>
                <div class="portal-head">
                    <div>
                        <h1>Quản lý đơn vé</h1>
                        <p class="muted">Xem danh sách order, quản lý vé đợi check-in, vé quá giờ đang chiếu, vé xem xét hoàn tiền và lịch sử check-in.</p>
                    </div>
                </div>

                <section class="panel" style="margin-bottom:20px;">
                    <h2>Check-in nhanh bằng mã vé</h2>
                    <form method="post" action="${pageContext.request.contextPath}/admin/orders" class="toolbar">
                        <cb:csrf/>
                        <label>Ticket code (Mã vé)
                            <input type="text" name="ticketCode" placeholder="CB-..." required style="width:240px;">
                        </label>
                        <div class="form-actions" style="margin-top:10px;">
                            <button type="submit" name="action" value="redeem">Redeem (Check-in)</button>
                        </div>
                    </form>
                </section>

                <section class="panel" style="margin-bottom:20px;">
                    <form method="get" action="${pageContext.request.contextPath}/admin/orders" class="toolbar">
                        <label>Trạng thái
                            <select name="status">
                                <option value="">Tất cả</option>
                                <option value="pending" ${selectedStatus eq 'pending' ? 'selected' : ''}>Chờ thanh toán</option>
                                <option value="paid" ${selectedStatus eq 'paid' ? 'selected' : ''}>Đã thanh toán</option>
                                <option value="redeemed" ${selectedStatus eq 'redeemed' ? 'selected' : ''}>Đã check-in</option>
                                <option value="cancelled" ${selectedStatus eq 'cancelled' ? 'selected' : ''}>Đã hủy</option>
                                <option value="refunded" ${selectedStatus eq 'refunded' ? 'selected' : ''}>Đã hoàn tiền</option>
                            </select>
                        </label>
                        <label>Từ ngày <input type="date" name="from" value="${fn:escapeXml(selectedFrom)}"></label>
                        <label>Đến ngày <input type="date" name="to" value="${fn:escapeXml(selectedTo)}"></label>
                        <label>Rạp
                            <select name="cinemaId">
                                <option value="">Tất cả</option>
                                <c:forEach var="cinema" items="${cinemas}">
                                    <option value="${fn:escapeXml(cinema.id)}" ${selectedCinemaId eq cinema.id ? 'selected' : ''}>${fn:escapeXml(cinema.name)}</option>
                                </c:forEach>
                            </select>
                        </label>
                        <label>Mã vé <input name="ticketCode" value="${fn:escapeXml(selectedTicketCode)}" maxlength="32"></label>
                        <input type="hidden" name="tab" id="adminSelectedTabInput" value="${fn:escapeXml(activeTab)}">
                        <button type="submit">Lọc</button>
                    </form>
                </section>

                <section class="portal-stack" style="margin-top:20px;">
                    <h2>Danh sách đơn vé</h2>

                    <!-- 6 ADMIN ORDER TABS -->
                    <div class="ticket-tabs" style="margin-top:14px; display:flex; gap:8px; flex-wrap:wrap;">
                        <a href="${pageContext.request.contextPath}/admin/orders?tab=pending" id="adminTabBtn_pending" class="ticket-tab-btn ${activeTab eq 'pending' ? 'active' : ''}" onclick="switchAdminOrderTab('pending'); return false;">
                            Vé đợi check-in <span class="tab-count">${fn:escapeXml(pendingCheckInOrders.size())}</span>
                        </a>
                        <a href="${pageContext.request.contextPath}/admin/orders?tab=late" id="adminTabBtn_late" class="ticket-tab-btn ${activeTab eq 'late' ? 'active' : ''}" onclick="switchAdminOrderTab('late'); return false;">
                            Vé quá giờ check-in (Đang chiếu) <span class="tab-count">${fn:escapeXml(lateCheckInOrders.size())}</span>
                        </a>
                        <a href="${pageContext.request.contextPath}/admin/orders?tab=refund" id="adminTabBtn_refund" class="ticket-tab-btn ${activeTab eq 'refund' ? 'active' : ''}" onclick="switchAdminOrderTab('refund'); return false;">
                            Vé xem xét hoàn tiền <span class="tab-count">${fn:escapeXml(refundReviewOrders.size())}</span>
                        </a>
                        <a href="${pageContext.request.contextPath}/admin/orders?tab=redeemed" id="adminTabBtn_redeemed" class="ticket-tab-btn ${activeTab eq 'redeemed' ? 'active' : ''}" onclick="switchAdminOrderTab('redeemed'); return false;">
                            Vé đã check-in <span class="tab-count">${fn:escapeXml(redeemedOrders.size())}</span>
                        </a>
                        <a href="${pageContext.request.contextPath}/admin/orders?tab=cancelled" id="adminTabBtn_cancelled" class="ticket-tab-btn ${activeTab eq 'cancelled' ? 'active' : ''}" onclick="switchAdminOrderTab('cancelled'); return false;">
                            Vé đã hủy <span class="tab-count">${fn:escapeXml(cancelledOrders.size())}</span>
                        </a>
                        <a href="${pageContext.request.contextPath}/admin/orders?tab=rejected" id="adminTabBtn_rejected" class="ticket-tab-btn ${activeTab eq 'rejected' ? 'active' : ''}" onclick="switchAdminOrderTab('rejected'); return false;">
                            Đã từ chối hoàn tiền <span class="tab-count">${fn:escapeXml(rejectedRefundOrders.size())}</span>
                        </a>
                    </div>

                    <!-- TAB 1: VÉ ĐỢI CHECK-IN (ĐÚNG GIỜ) -->
                    <div id="adminTabContent_pending" <c:if test="${activeTab ne 'pending'}">style="display:none;"</c:if>>
                        <div class="table-wrap">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>Mã Đơn / Ticket Code</th>
                                        <th>Phim & Suất</th>
                                        <th>Ghế & Combo</th>
                                        <th>Thành tiền</th>
                                        <th>Trạng thái</th>
                                        <th>Thời gian</th>
                                        <th>Hành động</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="order" items="${pendingCheckInOrders}">
                                        <tr>
                                            <td>
                                                <div class="mono" style="font-weight:700;">#${fn:escapeXml(order.id)}</div>
                                                <div class="mono muted" style="font-size:12px;">Code: ${fn:escapeXml(order.ticketCode)}</div>
                                                <div style="font-size:12px; color:#475569;">${fn:escapeXml(order.userEmail)}</div>
                                            </td>
                                            <td>
                                                <strong>${fn:escapeXml(order.filmTitle)}</strong>
                                                <div class="muted" style="font-size:12px;">Rạp: ${fn:escapeXml(order.cinemaName)} (${fn:escapeXml(order.roomName)})</div>
                                                <div class="muted" style="font-size:12px;">Suất: ${fn:escapeXml(order.startTimeDisplay)}</div>
                                            </td>
                                            <td>
                                                <div>Ghế: <span class="mono">${fn:escapeXml(order.seatsDisplay)}</span></div>
                                                <c:if test="${not empty order.combosDisplay}">
                                                    <div class="muted" style="font-size:12px;">Combos: ${fn:escapeXml(order.combosDisplay)}</div>
                                                </c:if>
                                            </td>
                                            <td>
                                                <strong>${cbf:whole(order.totalAmount)} vnđ</strong>
                                                <c:if test="${order.paymentStatus eq 'paid' or order.paymentStatus eq 'refunded'}"><div style="margin-top:3px;"><a href="${pageContext.request.contextPath}/invoices/${fn:escapeXml(order.id)}" target="_blank" class="invoice-pdf-link" title="Tải / Xem Hóa đơn PDF"><svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg><span>Hóa đơn PDF</span></a></div></c:if>
                                                <div><span class="border-tag tag-payment tag-payment-${order.paymentStatus}">Payment: ${fn:escapeXml(order.paymentStatus)}</span></div>
                                            </td>
                                            <td>
                                                <span class="status-pill info">Đợi check-in</span>
                                            </td>
                                            <td><span class="border-tag tag-time">${fn:escapeXml(order.createdAtDisplay)}</span></td>
                                            <td>
                                                <div class="inline-actions" style="display:flex; gap:8px;">
                                                    <c:if test="${order.counterPaymentCollectable}">
                                                        <form method="post" action="${pageContext.request.contextPath}/admin/orders">
                                                            <cb:csrf/>
                                                            <input type="hidden" name="id" value="${fn:escapeXml(order.id)}">
                                                            <button type="submit" name="action" value="markPaid" onclick="return confirm('Xác nhận đã thu tiền mặt cho đơn này?')" style="min-height:30px; padding: 4px 10px; font-size:12px;">Thu tiền</button>
                                                        </form>
                                                    </c:if>
                                                    <c:if test="${order.pendingCounterPayment and not order.counterPaymentCollectable}">
                                                        <span class="muted" style="font-size:12px; color:#b91c1c;">
                                                            Đã hết hạn thu tiền — không nhận tiền, hãy tạo đơn mới.
                                                        </span>
                                                    </c:if>
                                                    <c:if test="${order.paymentStatus eq 'paid' and order.orderStatus eq 'confirmed'}">
                                                        <form method="post" action="${pageContext.request.contextPath}/admin/orders">
                                                            <cb:csrf/>
                                                            <input type="hidden" name="ticketCode" value="${fn:escapeXml(order.ticketCode)}">
                                                            <button type="submit" name="action" value="redeem" style="min-height:30px; padding: 4px 10px; font-size:12px;">Check-in</button>
                                                        </form>
                                                    </c:if>
                                                    <c:if test="${order.adminCancellable}">
                                                        <form method="post" action="${pageContext.request.contextPath}/admin/orders">
                                                            <cb:csrf/>
                                                            <input type="hidden" name="id" value="${fn:escapeXml(order.id)}">
                                                            <button class="danger" type="submit" name="action" value="cancel" onclick="return confirm('Hủy đơn hàng này?')" style="min-height:30px; padding: 4px 10px; font-size:12px;">Hủy đơn</button>
                                                        </form>
                                                    </c:if>
                                                    <c:if test="${order.paidConfirmed}">
                                                        <span class="muted" style="font-size:12px; color:#92400e; max-width:210px;">
                                                            Đơn đã thu tiền: không dùng Hủy; chuyển sang quy trình Duyệt/Từ chối hoàn tiền khi đủ điều kiện.
                                                        </span>
                                                    </c:if>
                                                </div>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty pendingCheckInOrders}">
                                        <tr>
                                            <td colspan="7" class="text-center" style="padding: 24px;">Không có đơn vé nào đang đợi check-in.</td>
                                        </tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <!-- TAB 2: VÉ QUÁ GIỜ CHECK-IN (ĐANG CHIẾU — VẪN CHO CHECK-IN) -->
                    <div id="adminTabContent_late" <c:if test="${activeTab ne 'late'}">style="display:none;"</c:if>>
                        <div class="table-wrap">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>Mã Đơn / Ticket Code</th>
                                        <th>Phim & Suất</th>
                                        <th>Ghế & Combo</th>
                                        <th>Thành tiền</th>
                                        <th>Trạng thái</th>
                                        <th>Thời gian</th>
                                        <th>Hành động</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="order" items="${lateCheckInOrders}">
                                        <tr>
                                            <td>
                                                <div class="mono" style="font-weight:700;">#${fn:escapeXml(order.id)}</div>
                                                <div class="mono muted" style="font-size:12px;">Code: ${fn:escapeXml(order.ticketCode)}</div>
                                                <div style="font-size:12px; color:#475569;">${fn:escapeXml(order.userEmail)}</div>
                                            </td>
                                            <td>
                                                <strong>${fn:escapeXml(order.filmTitle)}</strong>
                                                <div class="muted" style="font-size:12px;">Rạp: ${fn:escapeXml(order.cinemaName)} (${fn:escapeXml(order.roomName)})</div>
                                                <div class="muted" style="font-size:12px; color:#d97706;">Suất: ${fn:escapeXml(order.startTimeDisplay)} - ${fn:escapeXml(order.endTimeDisplay)}</div>
                                            </td>
                                            <td>
                                                <div>Ghế: <span class="mono">${fn:escapeXml(order.seatsDisplay)}</span></div>
                                                <c:if test="${not empty order.combosDisplay}">
                                                    <div class="muted" style="font-size:12px;">Combos: ${fn:escapeXml(order.combosDisplay)}</div>
                                                </c:if>
                                            </td>
                                            <td>
                                                <strong>${cbf:whole(order.totalAmount)} vnđ</strong>
                                                <c:if test="${order.paymentStatus eq 'paid' or order.paymentStatus eq 'refunded'}"><div style="margin-top:3px;"><a href="${pageContext.request.contextPath}/invoices/${fn:escapeXml(order.id)}" target="_blank" class="invoice-pdf-link" title="Tải / Xem Hóa đơn PDF"><svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg><span>Hóa đơn PDF</span></a></div></c:if>
                                                <div><span class="border-tag tag-payment tag-payment-${order.paymentStatus}">Payment: ${fn:escapeXml(order.paymentStatus)}</span></div>
                                            </td>
                                            <td>
                                                <span class="status-pill warning">Trễ giờ (Đang chiếu)</span>
                                            </td>
                                            <td><span class="border-tag tag-time">${fn:escapeXml(order.createdAtDisplay)}</span></td>
                                            <td>
                                                <div class="inline-actions" style="display:flex; gap:8px;">
                                                    <c:if test="${order.paymentStatus eq 'paid' and order.orderStatus eq 'confirmed'}">
                                                        <form method="post" action="${pageContext.request.contextPath}/admin/orders">
                                                            <cb:csrf/>
                                                            <input type="hidden" name="ticketCode" value="${fn:escapeXml(order.ticketCode)}">
                                                            <button type="submit" name="action" value="redeem" style="min-height:30px; padding: 4px 10px; font-size:12px; background:#d97706; color:#fff; border:none;">Cho vào (Check-in)</button>
                                                        </form>
                                                    </c:if>
                                                    <c:if test="${order.adminCancellable}">
                                                        <form method="post" action="${pageContext.request.contextPath}/admin/orders">
                                                            <cb:csrf/>
                                                            <input type="hidden" name="id" value="${fn:escapeXml(order.id)}">
                                                            <button class="danger" type="submit" name="action" value="cancel" onclick="return confirm('Hủy đơn hàng này?')" style="min-height:30px; padding: 4px 10px; font-size:12px;">Hủy đơn</button>
                                                        </form>
                                                    </c:if>
                                                    <c:if test="${order.paidConfirmed}">
                                                        <span class="muted" style="font-size:12px; color:#92400e; max-width:210px;">
                                                            Đơn đã thu tiền: xử lý bằng workflow hoàn tiền, không hủy trực tiếp.
                                                        </span>
                                                    </c:if>
                                                </div>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty lateCheckInOrders}">
                                        <tr>
                                            <td colspan="7" class="text-center" style="padding: 24px;">Không có đơn vé nào bị quá giờ check-in trong suất chiếu hiện tại.</td>
                                        </tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <!-- TAB 3: VÉ XEM XÉT HOÀN TIỀN (ĐÃ HẾT GIỜ CHIẾU — CHƯA CHECK-IN) -->
                    <div id="adminTabContent_refund" <c:if test="${activeTab ne 'refund'}">style="display:none;"</c:if>>
                        <div class="table-wrap">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>Mã Đơn / Ticket Code</th>
                                        <th>Phim & Suất</th>
                                        <th>Ghế & Combo</th>
                                        <th>Thành tiền</th>
                                        <th>Trạng thái</th>
                                        <th>Thời gian</th>
                                        <th>Hành động</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="order" items="${refundReviewOrders}">
                                        <tr>
                                            <td>
                                                <div class="mono" style="font-weight:700;">#${fn:escapeXml(order.id)}</div>
                                                <div class="mono muted" style="font-size:12px;">Code: ${fn:escapeXml(order.ticketCode)}</div>
                                                <div style="font-size:12px; color:#475569;">${fn:escapeXml(order.userEmail)}</div>
                                            </td>
                                            <td>
                                                <strong>${fn:escapeXml(order.filmTitle)}</strong>
                                                <div class="muted" style="font-size:12px;">Rạp: ${fn:escapeXml(order.cinemaName)} (${fn:escapeXml(order.roomName)})</div>
                                                <div class="muted" style="font-size:12px; color:#dc2626;">Kết thúc: ${fn:escapeXml(order.endTimeDisplay)}</div>
                                            </td>
                                            <td>
                                                <div>Ghế: <span class="mono">${fn:escapeXml(order.seatsDisplay)}</span></div>
                                                <c:if test="${not empty order.combosDisplay}">
                                                    <div class="muted" style="font-size:12px;">Combos: ${fn:escapeXml(order.combosDisplay)}</div>
                                                </c:if>
                                            </td>
                                            <td>
                                                <strong>${cbf:whole(order.totalAmount)} vnđ</strong>
                                                <c:if test="${order.paymentStatus eq 'paid' or order.paymentStatus eq 'refunded'}"><div style="margin-top:3px;"><a href="${pageContext.request.contextPath}/invoices/${fn:escapeXml(order.id)}" target="_blank" class="invoice-pdf-link" title="Tải / Xem Hóa đơn PDF"><svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg><span>Hóa đơn PDF</span></a></div></c:if>
                                                <div><span class="border-tag tag-payment tag-payment-${order.paymentStatus}">Payment: ${fn:escapeXml(order.paymentStatus)}</span></div>
                                            </td>
                                            <td>
                                                <span class="status-pill danger">Hết giờ - Chờ hoàn tiền</span>
                                            </td>
                                            <td><span class="border-tag tag-time">${fn:escapeXml(order.createdAtDisplay)}</span></td>
                                            <td>
                                                <%-- BUG-11 / BUG-10: duyet hay tu choi hoan tien deu la quyet dinh dung tien.
                                                     Ly do la bat buoc va duoc ghi vao audit — khong co no thi khong
                                                     doi chat duoc khi khach khieu nai.

                                                     A.1: o tick "Bo qua dieu kien hoan tien" mac dinh KHONG duoc chon.
                                                     Khong tick thi ve da check-in hoac don qua han se bi chan (400 kem
                                                     ly do); tick thi ghi them mot dong audit REFUND_OVERRIDE. --%>
                                                <form method="post" action="${pageContext.request.contextPath}/admin/orders" style="display:flex; flex-direction:column; gap:6px;">
                                                    <cb:csrf/>
                                                    <input type="hidden" name="id" value="${fn:escapeXml(order.id)}">
                                                    <input type="text" name="refundReason" required maxlength="255" placeholder="Lý do (bắt buộc)" style="min-height:30px; padding: 4px 8px; font-size:12px; border:1px solid #cbd5e1; border-radius:4px;">
                                                    <label for="overrideRefund_${fn:escapeXml(order.id)}" style="display:flex; align-items:center; gap:6px; font-size:12px; color:#b45309; cursor:pointer;">
                                                        <input type="checkbox" id="overrideRefund_${fn:escapeXml(order.id)}" name="overrideRefundRestrictions" value="on" style="margin:0;">
                                                        Bỏ qua điều kiện hoàn tiền
                                                    </label>
                                                    <c:choose><c:when test="${sessionScope.currentUser.role eq 'admin'}"><a href="${pageContext.request.contextPath}/admin/content/refund-policy" style="font-size:12px;">Đọc/chỉnh sửa điều kiện hoàn tiền</a></c:when><c:otherwise><a href="${pageContext.request.contextPath}/refund-policy" style="font-size:12px;">Đọc điều kiện hoàn tiền</a></c:otherwise></c:choose>
                                                    <div class="inline-actions" style="display:flex; gap:8px;">
                                                        <button type="submit" name="action" value="approveRefund" onclick="return confirm('Xác nhận duyệt hoàn tiền cho đơn #${fn:escapeXml(order.id)}?')" style="min-height:30px; padding: 4px 10px; font-size:12px; background:#059669; color:#fff; border:none; border-radius:4px;">Duyệt hoàn tiền</button>
                                                        <button class="danger" type="submit" name="action" value="rejectRefund" onclick="return confirm('Từ chối yêu cầu hoàn tiền cho đơn #${fn:escapeXml(order.id)}?')" style="min-height:30px; padding: 4px 10px; font-size:12px;">Từ chối</button>
                                                    </div>
                                                </form>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty refundReviewOrders}">
                                        <tr>
                                            <td colspan="7" class="text-center" style="padding: 24px;">Chưa có đơn vé nào trong danh sách xem xét hoàn tiền.</td>
                                        </tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
                    </div>



                    <!-- TAB 4: VÉ ĐÃ CHECK-IN -->
                    <div id="adminTabContent_redeemed" <c:if test="${activeTab ne 'redeemed'}">style="display:none;"</c:if>>
                        <div class="table-wrap">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>Mã Đơn / Ticket Code</th>
                                        <th>Phim & Suất</th>
                                        <th>Ghế & Combo</th>
                                        <th>Thành tiền</th>
                                        <th>Trạng thái</th>
                                        <th>Thời gian Check-in</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="order" items="${redeemedOrders}">
                                        <tr>
                                            <td>
                                                <div class="mono" style="font-weight:700;">#${fn:escapeXml(order.id)}</div>
                                                <div class="mono muted" style="font-size:12px;">Code: ${fn:escapeXml(order.ticketCode)}</div>
                                                <div style="font-size:12px; color:#475569;">${fn:escapeXml(order.userEmail)}</div>
                                            </td>
                                            <td>
                                                <strong>${fn:escapeXml(order.filmTitle)}</strong>
                                                <div class="muted" style="font-size:12px;">Rạp: ${fn:escapeXml(order.cinemaName)} (${fn:escapeXml(order.roomName)})</div>
                                                <div class="muted" style="font-size:12px;">Suất: ${fn:escapeXml(order.startTimeDisplay)}</div>
                                            </td>
                                            <td>
                                                <div>Ghế: <span class="mono">${fn:escapeXml(order.seatsDisplay)}</span></div>
                                                <c:if test="${not empty order.combosDisplay}">
                                                    <div class="muted" style="font-size:12px;">Combos: ${fn:escapeXml(order.combosDisplay)}</div>
                                                </c:if>
                                            </td>
                                            <td>
                                                <strong>${cbf:whole(order.totalAmount)} vnđ</strong>
                                                <c:if test="${order.paymentStatus eq 'paid' or order.paymentStatus eq 'refunded'}"><div style="margin-top:3px;"><a href="${pageContext.request.contextPath}/invoices/${fn:escapeXml(order.id)}" target="_blank" class="invoice-pdf-link" title="Tải / Xem Hóa đơn PDF"><svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg><span>Hóa đơn PDF</span></a></div></c:if>
                                                <div><span class="border-tag tag-payment tag-payment-${order.paymentStatus}">Payment: ${fn:escapeXml(order.paymentStatus)}</span></div>
                                            </td>
                                            <td>
                                                <span class="status-pill success">Đã check-in</span>
                                            </td>
                                            <td><span class="border-tag tag-time">${fn:escapeXml(empty order.redeemedAtDisplay ? order.createdAtDisplay : order.redeemedAtDisplay)}</span></td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty redeemedOrders}">
                                        <tr>
                                            <td colspan="6" class="text-center" style="padding: 24px;">Chưa có đơn vé nào đã check-in.</td>
                                        </tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <!-- TAB 5: VÉ ĐÃ HỦY -->
                    <div id="adminTabContent_cancelled" <c:if test="${activeTab ne 'cancelled'}">style="display:none;"</c:if>>
                        <div class="table-wrap">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>Mã Đơn / Ticket Code</th>
                                        <th>Phim & Suất</th>
                                        <th>Ghế & Combo</th>
                                        <th>Thành tiền</th>
                                        <th>Trạng thái</th>
                                        <th>Thời gian</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="order" items="${cancelledOrders}">
                                        <tr>
                                            <td>
                                                <div class="mono" style="font-weight:700;">#${fn:escapeXml(order.id)}</div>
                                                <div class="mono muted" style="font-size:12px;">Code: ${fn:escapeXml(order.ticketCode)}</div>
                                                <div style="font-size:12px; color:#475569;">${fn:escapeXml(order.userEmail)}</div>
                                            </td>
                                            <td>
                                                <strong>${fn:escapeXml(order.filmTitle)}</strong>
                                                <div class="muted" style="font-size:12px;">Rạp: ${fn:escapeXml(order.cinemaName)} (${fn:escapeXml(order.roomName)})</div>
                                                <div class="muted" style="font-size:12px;">Suất: ${fn:escapeXml(order.startTimeDisplay)}</div>
                                            </td>
                                            <td>
                                                <div>Ghế: <span class="mono">${fn:escapeXml(order.seatsDisplay)}</span></div>
                                                <c:if test="${not empty order.combosDisplay}">
                                                    <div class="muted" style="font-size:12px;">Combos: ${fn:escapeXml(order.combosDisplay)}</div>
                                                </c:if>
                                            </td>
                                            <td>
                                                <strong>${cbf:whole(order.totalAmount)} vnđ</strong>
                                                <c:if test="${order.paymentStatus eq 'paid' or order.paymentStatus eq 'refunded'}"><div style="margin-top:3px;"><a href="${pageContext.request.contextPath}/invoices/${fn:escapeXml(order.id)}" target="_blank" class="invoice-pdf-link" title="Tải / Xem Hóa đơn PDF"><svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg><span>Hóa đơn PDF</span></a></div></c:if>
                                                <div><span class="border-tag tag-payment tag-payment-${order.paymentStatus}">Payment: ${fn:escapeXml(order.paymentStatus)}</span></div>
                                            </td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${order.paymentStatus eq 'refunded' or order.statusLabel eq 'Đã hoàn tiền'}">
                                                        <span class="status-pill success">Đã hoàn tiền</span>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span class="status-pill danger">${fn:escapeXml(order.statusLabel)}</span>
                                                    </c:otherwise>
                                                </c:choose>
                                                <c:if test="${not empty order.cancelReason}">
                                                    <div><span class="border-tag tag-reason">Lý do: <c:out value="${order.cancelReason}"/></span></div>
                                                </c:if>
                                            </td>
                                            <td><span class="border-tag tag-time">${fn:escapeXml(order.createdAtDisplay)}</span></td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty cancelledOrders}">
                                        <tr>
                                            <td colspan="6" class="text-center" style="padding: 24px;">Chưa có đơn vé nào bị hủy.</td>
                                        </tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <!-- TAB 6: ĐÃ TỪ CHỐI HOÀN TIỀN -->
                    <div id="adminTabContent_rejected" <c:if test="${activeTab ne 'rejected'}">style="display:none;"</c:if>>
                        <div class="table-wrap">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>Mã Đơn / Ticket Code</th>
                                        <th>Phim & Suất</th>
                                        <th>Ghế & Combo</th>
                                        <th>Thành tiền</th>
                                        <th>Quyết định</th>
                                        <th>Thời gian</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="order" items="${rejectedRefundOrders}">
                                        <tr>
                                            <td>
                                                <div class="mono" style="font-weight:700;">#${fn:escapeXml(order.id)}</div>
                                                <div class="mono muted" style="font-size:12px;">Code: ${fn:escapeXml(order.ticketCode)}</div>
                                                <div style="font-size:12px; color:#475569;">${fn:escapeXml(order.userEmail)}</div>
                                            </td>
                                            <td>
                                                <strong>${fn:escapeXml(order.filmTitle)}</strong>
                                                <div class="muted" style="font-size:12px;">Rạp: ${fn:escapeXml(order.cinemaName)} (${fn:escapeXml(order.roomName)})</div>
                                                <div class="muted" style="font-size:12px;">Suất: ${fn:escapeXml(order.startTimeDisplay)}</div>
                                            </td>
                                            <td>
                                                <div>Ghế: <span class="mono">${fn:escapeXml(order.seatsDisplay)}</span></div>
                                                <c:if test="${not empty order.combosDisplay}">
                                                    <div class="muted" style="font-size:12px;">Combos: ${fn:escapeXml(order.combosDisplay)}</div>
                                                </c:if>
                                            </td>
                                            <td>
                                                <strong>${cbf:whole(order.totalAmount)} vnđ</strong>
                                                <c:if test="${order.paymentStatus eq 'paid' or order.paymentStatus eq 'refunded'}"><div style="margin-top:3px;"><a href="${pageContext.request.contextPath}/invoices/${fn:escapeXml(order.id)}" target="_blank" class="invoice-pdf-link" title="Tải / Xem Hóa đơn PDF"><svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg><span>Hóa đơn PDF</span></a></div></c:if>
                                                <div><span class="border-tag tag-payment tag-payment-${order.paymentStatus}">Payment: ${fn:escapeXml(order.paymentStatus)}</span></div>
                                            </td>
                                            <td>
                                                <span class="status-pill danger">Đã từ chối</span>
                                                <c:if test="${not empty order.refundRejectReason}">
                                                    <div><span class="border-tag tag-reason">Lý do: <c:out value="${order.refundRejectReason}"/></span></div>
                                                </c:if>
                                            </td>
                                            <td><span class="border-tag tag-time">${fn:escapeXml(order.createdAtDisplay)}</span></td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty rejectedRefundOrders}">
                                        <tr>
                                            <td colspan="6" class="text-center" style="padding: 24px;">Chưa có đơn bị từ chối hoàn tiền.</td>
                                        </tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
                    </div>
                    <form method="get" action="${pageContext.request.contextPath}/admin/orders" class="toolbar" style="justify-content:center;">
                        <input type="hidden" name="status" value="${fn:escapeXml(selectedStatus)}">
                        <input type="hidden" name="from" value="${fn:escapeXml(selectedFrom)}">
                        <input type="hidden" name="to" value="${fn:escapeXml(selectedTo)}">
                        <input type="hidden" name="cinemaId" value="${fn:escapeXml(selectedCinemaId)}">
                        <input type="hidden" name="ticketCode" value="${fn:escapeXml(selectedTicketCode)}">
                        <button name="page" value="${orderPage.page - 1}" ${orderPage.hasPrevious ? '' : 'disabled'}>Trang trước</button>
                        <span>Trang ${fn:escapeXml(orderPage.page)} / ${fn:escapeXml(orderPage.totalPages)} · ${fn:escapeXml(orderPage.totalItems)} đơn</span>
                        <button name="page" value="${orderPage.page + 1}" ${orderPage.hasNext ? '' : 'disabled'}>Trang sau</button>
                    </form>
                </section>
            </div>
        </main>
    </div>

    <script>
        function switchAdminOrderTab(tabName) {
            var navigationUrl = new URL(window.location.href);
            navigationUrl.searchParams.set('tab', tabName);
            navigationUrl.searchParams.set('page', '1');
            window.location.assign(navigationUrl.toString());
            return;
            var tabs = ['pending', 'late', 'refund', 'rejected', 'redeemed', 'cancelled'];
            tabs.forEach(function(t) {
                var content = document.getElementById('adminTabContent_' + t);
                var btn = document.getElementById('adminTabBtn_' + t);
                if (content) {
                    content.style.display = (t === tabName) ? 'block' : 'none';
                }
                if (btn) {
                    if (t === tabName) {
                        btn.classList.add('active');
                    } else {
                        btn.classList.remove('active');
                    }
                }
            });
            var tabInput = document.getElementById('adminSelectedTabInput');
            if (tabInput) {
                tabInput.value = tabName;
            }
            if (window.history && window.history.replaceState) {
                var url = new URL(window.location.href);
                url.searchParams.set('tab', tabName);
                window.history.replaceState(null, '', url.toString());
            }
        }

    </script>
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            var counts = {
                pending: '${fn:escapeXml(orderTabCounts["pending"])}',
                late: '${fn:escapeXml(orderTabCounts["late"])}',
                refund: '${fn:escapeXml(orderTabCounts["refund"])}',
                rejected: '${fn:escapeXml(orderTabCounts["rejected"])}',
                redeemed: '${fn:escapeXml(orderTabCounts["redeemed"])}',
                cancelled: '${fn:escapeXml(orderTabCounts["cancelled"])}'
            };
            Object.keys(counts).forEach(function(tab) {
                var button = document.getElementById('adminTabBtn_' + tab);
                var badge = button && button.querySelector('.tab-count');
                if (badge) badge.textContent = counts[tab];
            });
        });
    </script>
</body>
</html>
