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
    <title>Quản lý Đơn Kháng Cáo - Admin CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
    <style>
        .appeal-card {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 12px;
            padding: 20px;
            margin-bottom: 16px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.02);
        }
        .appeal-card.pending {
            border-left: 4px solid #3b82f6;
        }
        .appeal-card.approved {
            border-left: 4px solid #10b981;
            background: #f0fdf4;
        }
        .appeal-card.rejected {
            border-left: 4px solid #ef4444;
            background: #fef2f2;
        }
        .appeal-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 12px;
        }
        .appeal-user {
            font-weight: 700;
            font-size: 1.05rem;
            color: #0f172a;
        }
        .appeal-reason-box {
            background: #f8fafc;
            border-radius: 8px;
            padding: 12px 16px;
            font-size: 0.93rem;
            color: #334155;
            margin: 10px 0;
            border: 1px dashed #cbd5e1;
        }
        .appeal-actions {
            display: flex;
            gap: 10px;
            margin-top: 14px;
        }
        .badge-status {
            padding: 4px 10px;
            border-radius: 6px;
            font-size: 0.78rem;
            font-weight: 700;
        }
        .badge-pending { background: #dbeafe; color: #1e40af; }
        .badge-approved { background: #d1fae5; color: #065f46; }
        .badge-rejected { background: #fee2e2; color: #991b1b; }
        .appeal-modal-backdrop {
            position: fixed; inset: 0; z-index: 1200; display: none;
            align-items: center; justify-content: center; padding: 20px;
            background: rgba(15, 23, 42, 0.62);
        }
        .appeal-modal-backdrop.open { display: flex; }
        .appeal-modal {
            width: min(520px, 100%); border-radius: 14px; background: #fff;
            padding: 24px; box-shadow: 0 24px 60px rgba(15, 23, 42, 0.28);
        }
        .appeal-modal-actions { display:flex; justify-content:flex-end; gap:10px; margin-top:20px; }
    </style>
</head>
<body class="admin-body">
    <div class="dashboard">
        <%@ include file="/WEB-INF/views/admin/sidebar.jspf" %>
        <main class="dashboard-main">
            <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
            <div class="dashboard-content">
                <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

                <section class="admin-page-head">
                    <div>
                        <h1>Trung tâm xét duyệt đơn kháng cáo</h1>
                        <p class="muted">Xét duyệt mở khóa tài khoản và theo dõi yêu cầu hoàn tiền vé.</p>
                    </div>
                </section>

                <!-- F-005: khong doc duoc hang doi thi phai noi ro, khong duoc hien nhu hang doi trong -->
                <c:if test="${appealsUnavailable}">
                    <article class="panel" style="border-left:4px solid #f59e0b; background:#fffbeb; padding:20px 24px; margin-bottom:20px;">
                        <h3 style="margin:0 0 6px 0; color:#92400e;">Không đọc được danh sách đơn kháng cáo</h3>
                        <p class="muted" style="margin:0 0 12px 0;">
                            Hệ thống không truy vấn được hàng đợi kháng cáo nên <strong>không thể kết luận là không có đơn nào</strong>.
                            Có thể đang có đơn chờ xử lý mà trang này chưa hiển thị được. Vui lòng thử lại; nếu vẫn lỗi, kiểm tra log máy chủ.
                        </p>
                        <a class="ent-btn-action ent-btn-action-primary" style="padding:7px 14px; font-size:0.88rem;"
                           href="${pageContext.request.contextPath}/admin/appeals${empty selectedStatus ? '' : '?status='}${fn:escapeXml(empty selectedStatus ? '' : selectedStatus)}">
                            Thử lại
                        </a>
                    </article>
                </c:if>

                <!-- FILTER TABS -->
                <div class="ticket-tabs" style="margin-bottom:20px;">
                    <a href="${pageContext.request.contextPath}/admin/appeals" class="ticket-tab-btn ${fn:escapeXml(empty selectedStatus ? 'active' : '')}">
                        Tất cả đơn (${appealsUnavailable ? '—' : fn:escapeXml(appeals.size())})
                    </a>
                    <a href="${pageContext.request.contextPath}/admin/appeals?status=pending" class="ticket-tab-btn ${fn:escapeXml(selectedStatus eq 'pending' ? 'active' : '')}">
                        Đang chờ xét duyệt
                    </a>
                    <a href="${pageContext.request.contextPath}/admin/appeals?status=approved" class="ticket-tab-btn ${fn:escapeXml(selectedStatus eq 'approved' ? 'active' : '')}">
                        Đã phê duyệt
                    </a>
                    <a href="${pageContext.request.contextPath}/admin/appeals?status=rejected" class="ticket-tab-btn ${fn:escapeXml(selectedStatus eq 'rejected' ? 'active' : '')}">
                        Đã từ chối
                    </a>
                </div>

                <c:if test="${not empty appeals}">
                    <div class="admin-table-wrap"><div class="admin-table-scroll" tabindex="0" aria-label="Danh sách đơn kháng cáo">
                        <table class="admin-data-table admin-entity-table admin-appeal-table">
                            <thead><tr><th scope="col">Khách hàng</th><th scope="col">Loại yêu cầu</th><th scope="col">Mã vé / Đơn</th><th scope="col">Phim / Rạp</th><th scope="col">Suất chiếu</th><th scope="col">Hoàn tiền / Trạng thái đơn</th><th scope="col">Trạng thái xử lý</th><th scope="col">Thời gian gửi</th><th scope="col">Hành động</th></tr></thead>
                            <tbody>
                                <c:forEach var="app" items="${appeals}">
                                    <tr data-admin-search-item>
                                        <td><strong>${fn:escapeXml(app.userFullName)}</strong><small>${fn:escapeXml(app.email)}</small></td>
                                        <td><span class="badge-status status-info">${fn:escapeXml(app.appealTypeLabel)}</span></td>
                                        <td><c:choose><c:when test="${app.refundAppeal}"><strong>#${fn:escapeXml(app.ticketCode)}</strong><small>Đơn #${fn:escapeXml(app.orderId)}</small></c:when><c:otherwise><span>Yêu cầu mở khóa</span></c:otherwise></c:choose></td>
                                        <td><c:choose><c:when test="${app.refundAppeal}"><strong>${fn:escapeXml(empty app.filmTitle ? '—' : app.filmTitle)}</strong><small>${fn:escapeXml(empty app.cinemaName ? '—' : app.cinemaName)}</small></c:when><c:otherwise><span>—</span></c:otherwise></c:choose></td>
                                        <td><c:choose><c:when test="${app.refundAppeal}"><strong class="admin-table-primary">${fn:escapeXml(app.showtimeStartDisplay)}</strong><small>đến ${fn:escapeXml(app.showtimeEndDisplay)}</small></c:when><c:otherwise><span>—</span></c:otherwise></c:choose></td>
                                        <td><c:choose><c:when test="${app.refundAppeal}"><strong>${fn:escapeXml(app.orderTotalAmountDisplay)} đ</strong><small>${fn:escapeXml(app.orderPaymentStatus)} / ${fn:escapeXml(app.orderStatus)}</small></c:when><c:otherwise><span>${fn:escapeXml(app.warningCount)}/3 cảnh cáo</span></c:otherwise></c:choose></td>
                                        <td><c:choose><c:when test="${app.status eq 'pending'}"><span class="badge-status status-warning"><span class="status-dot"></span>Đang chờ</span></c:when><c:when test="${app.status eq 'approved'}"><span class="badge-status status-success"><span class="status-dot"></span>${app.refundAppeal ? 'Đã hoàn tiền' : 'Đã mở khóa'}</span></c:when><c:otherwise><span class="badge-status status-danger"><span class="status-dot"></span>Đã từ chối</span></c:otherwise></c:choose></td>
                                        <td>${fn:escapeXml(app.createdAtDisplay)}</td>
                                        <td><a class="button secondary" href="#appeal-${fn:escapeXml(app.id)}" onclick="document.getElementById('appeal-${fn:escapeXml(app.id)}').open=true">Xem chi tiết</a></td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div></div>
                    <h3 class="admin-editor-heading">Chi tiết và xử lý kháng cáo</h3>
                </c:if>

                <!-- APPEALS LIST -->
                <div id="appealsList">
                    <c:forEach var="app" items="${appeals}">
                        <details class="appeal-card ${fn:escapeXml(app.status)}" id="appeal-${fn:escapeXml(app.id)}">
                            <summary class="appeal-card-summary"><strong>${fn:escapeXml(app.userFullName)}</strong><span>${fn:escapeXml(app.appealTypeLabel)} · ${fn:escapeXml(app.createdAtDisplay)}</span></summary>
                            <div class="appeal-header">
                                <div>
                                    <span class="appeal-user">${fn:escapeXml(app.userFullName)} (${fn:escapeXml(app.email)})</span>
                                    <span class="rf-badge" style="font-size:0.75rem; margin-left:8px; color:#6d28d9;">
                                        ${fn:escapeXml(app.appealTypeLabel)}
                                    </span>
                                    <span style="font-size:0.85rem; color:#64748b; margin-left:12px;">${fn:escapeXml(app.createdAtDisplay)}</span>
                                </div>
                                <div>
                                    <c:choose>
                                        <c:when test="${app.status eq 'pending'}"><span class="badge-status badge-pending">ĐANG CHỜ</span></c:when>
                                        <c:when test="${app.status eq 'approved'}">
                                            <span class="badge-status badge-approved">
                                                ${app.refundAppeal ? 'ĐÃ HOÀN TIỀN' : 'ĐÃ MỞ KHÓA'}
                                            </span>
                                        </c:when>
                                        <c:otherwise><span class="badge-status badge-rejected">ĐÃ TỪ CHỐI</span></c:otherwise>
                                    </c:choose>
                                </div>
                            </div>

                            <c:choose>
                                <c:when test="${app.refundAppeal}">
                                    <div style="font-size:0.88rem; color:#334155; margin-bottom:8px; line-height:1.7;">
                                        <strong>Mã vé:</strong> #${fn:escapeXml(app.ticketCode)}
                                        · <strong>Đơn:</strong> #${fn:escapeXml(app.orderId)}<br>
                                        <strong>Phim:</strong> ${fn:escapeXml(app.filmTitle)}
                                        · <strong>Rạp:</strong> ${fn:escapeXml(app.cinemaName)}<br>
                                        <strong>Suất chiếu:</strong> ${fn:escapeXml(app.showtimeStartDisplay)}
                                        – ${fn:escapeXml(app.showtimeEndDisplay)}<br>
                                        <strong>Hoàn toàn bộ:</strong> ${fn:escapeXml(app.orderTotalAmountDisplay)} đ
                                        · <strong>Trạng thái đơn:</strong>
                                        ${fn:escapeXml(app.orderPaymentStatus)} / ${fn:escapeXml(app.orderStatus)}
                                    </div>
                                    <div class="appeal-reason-box">
                                        <strong>Thông tin nhận hoàn tiền:</strong><br>
                                        ${fn:escapeXml(empty app.bankAccountInfo ? 'Chưa cung cấp' : app.bankAccountInfo)}
                                    </div>
                                </c:when>
                                <c:otherwise>
                                    <div style="font-size:0.85rem; color:#dc2626; margin-bottom:6px;">
                                        <strong>Lý do tài khoản bị khóa:</strong> ${fn:escapeXml(empty app.lockReason ? 'Chưa cập nhật' : app.lockReason)}
                                        (Số lượt cảnh cáo: <strong>${fn:escapeXml(app.warningCount)}/3</strong>)
                                    </div>
                                </c:otherwise>
                            </c:choose>

                            <div class="appeal-reason-box">
                                <strong>Nội dung giải trình / kháng cáo:</strong><br>
                                "${fn:escapeXml(app.reason)}"
                            </div>

                            <c:if test="${not empty app.adminResponse}">
                                <div style="font-size:0.88rem; color:#475569; margin-top:8px; font-style:italic;">
                                    <strong>Phản hồi từ Admin (${fn:escapeXml(app.resolvedAtDisplay)}):</strong> ${fn:escapeXml(app.adminResponse)}
                                </div>
                            </c:if>

                            <c:if test="${app.status eq 'pending'}">
                                <c:choose>
                                    <c:when test="${app.refundAppeal}">
                                        <c:url var="refundTargetUrl" value="/admin/orders">
                                            <c:param name="tab" value="refund"/>
                                            <c:param name="ticketCode" value="${app.ticketCode}"/>
                                        </c:url>
                                        <div class="appeal-actions">
                                            <button type="button" class="ent-btn-action ent-btn-action-primary js-refund-route"
                                                    data-refund-url="${fn:escapeXml(refundTargetUrl)}"
                                                    data-order-id="${fn:escapeXml(app.orderId)}"
                                                    style="padding:7px 14px; font-size:0.88rem;">
                                                Xử lý tại tab Hoàn tiền
                                            </button>
                                            <noscript>
                                                <a class="ent-btn-action ent-btn-action-primary"
                                                   href="${fn:escapeXml(refundTargetUrl)}">Mở tab Hoàn tiền</a>
                                            </noscript>
                                        </div>
                                    </c:when>
                                    <c:otherwise>
                                        <div class="appeal-actions">
                                            <form method="post" action="${pageContext.request.contextPath}/admin/appeals" style="display:inline;" onsubmit="return confirm('Phê duyệt mở khóa tài khoản này?')">
                                                <cb:csrf/>
                                                <input type="hidden" name="action" value="approve">
                                                <input type="hidden" name="id" value="${fn:escapeXml(app.id)}">
                                                <input type="text" name="adminResponse" placeholder="Ghi chú phản hồi cho user..." style="padding:6px 10px; border-radius:6px; border:1px solid #cbd5e1; font-size:0.85rem; width:220px;">
                                                <button type="submit" class="ent-btn-action ent-btn-action-primary" style="padding:7px 14px; font-size:0.88rem;">Phê duyệt & Mở khóa tài khoản</button>
                                            </form>
                                            <form method="post" action="${pageContext.request.contextPath}/admin/appeals" style="display:inline;" onsubmit="return confirm('Từ chối đơn mở khóa này?')">
                                                <cb:csrf/>
                                                <input type="hidden" name="action" value="reject">
                                                <input type="hidden" name="id" value="${fn:escapeXml(app.id)}">
                                                <input type="text" name="adminResponse" placeholder="Lý do từ chối..." style="padding:6px 10px; border-radius:6px; border:1px solid #cbd5e1; font-size:0.85rem; width:220px;">
                                                <button type="submit" class="ent-btn-action ent-btn-action-danger" style="padding:7px 14px; font-size:0.88rem;">Từ chối mở khóa</button>
                                            </form>
                                        </div>
                                    </c:otherwise>
                                </c:choose>
                            </c:if>
                        </details>
                    </c:forEach>

                    <c:if test="${empty appeals and not appealsUnavailable}">
                        <article class="panel" style="text-align: center; padding: 48px 24px; background: #ffffff;">
                            <h3 style="margin: 0 0 6px 0; color: #0f172a;">Không có đơn kháng cáo nào</h3>
                            <p class="muted">Các yêu cầu mở khóa và yêu cầu hoàn tiền cần theo dõi sẽ hiển thị tại đây.</p>
                        </article>
                    </c:if>
                </div>
            </div>
        </main>
    </div>
    <div id="refundRouteModal" class="appeal-modal-backdrop" aria-hidden="true">
        <section class="appeal-modal" role="dialog" aria-modal="true"
                 aria-labelledby="refundRouteTitle" aria-describedby="refundRouteDescription">
            <h2 id="refundRouteTitle" tabindex="-1">Không thể hoàn tiền tại trang Kháng cáo</h2>
            <p id="refundRouteDescription">
                Giao dịch hoàn tiền phải được kiểm tra và thực hiện tại tab Hoàn tiền trong Quản lý đơn
                để bảo toàn order, vé, ghế, doanh thu và audit.
            </p>
            <p id="refundRouteLive" class="muted" aria-live="polite"></p>
            <div class="appeal-modal-actions">
                <button type="button" class="ent-btn-action" id="refundRouteCancel">Ở lại trang này</button>
                <a class="ent-btn-action ent-btn-action-primary" id="refundRouteContinue" href="#">
                    Đi tới tab Hoàn tiền
                </a>
            </div>
        </section>
    </div>
    <script>
        (function() {
            var modal = document.getElementById('refundRouteModal');
            var cancelButton = document.getElementById('refundRouteCancel');
            var continueLink = document.getElementById('refundRouteContinue');
            var title = document.getElementById('refundRouteTitle');
            var live = document.getElementById('refundRouteLive');
            var opener = null;

            function focusable() {
                return Array.prototype.slice.call(modal.querySelectorAll('button:not([disabled]), a[href]'));
            }
            function closeModal() {
                modal.classList.remove('open');
                modal.setAttribute('aria-hidden', 'true');
                live.textContent = '';
                if (opener) opener.focus();
            }
            document.querySelectorAll('.js-refund-route').forEach(function(button) {
                button.addEventListener('click', function() {
                    opener = button;
                    continueLink.href = button.getAttribute('data-refund-url');
                    live.textContent = 'Đã xác định đơn #' + button.getAttribute('data-order-id') + '.';
                    modal.classList.add('open');
                    modal.setAttribute('aria-hidden', 'false');
                    title.focus();
                });
            });
            cancelButton.addEventListener('click', closeModal);
            modal.addEventListener('click', function(event) {
                if (event.target === modal) closeModal();
            });
            document.addEventListener('keydown', function(event) {
                if (!modal.classList.contains('open')) return;
                if (event.key === 'Escape') {
                    event.preventDefault();
                    closeModal();
                    return;
                }
                if (event.key === 'Tab') {
                    var items = focusable();
                    if (!items.length) return;
                    var first = items[0];
                    var last = items[items.length - 1];
                    if (event.shiftKey && (document.activeElement === first
                            || document.activeElement === title)) {
                        event.preventDefault();
                        last.focus();
                    } else if (!event.shiftKey && document.activeElement === last) {
                        event.preventDefault();
                        first.focus();
                    }
                }
            });
        })();
    </script>
</body>
</html>
