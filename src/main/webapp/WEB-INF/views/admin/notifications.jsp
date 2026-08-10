<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="roomNotificationCount" value="0" />
<c:forEach var="notification" items="${notifications}">
    <c:if test="${notification.category eq 'room'}"><c:set var="roomNotificationCount" value="${roomNotificationCount + 1}" /></c:if>
</c:forEach>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Thông báo - Admin CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
</head>
<body class="admin-body admin-notifications-page">
<div class="dashboard">
    <%@ include file="/WEB-INF/views/admin/sidebar.jspf" %>
    <main class="dashboard-main">
        <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
        <div class="dashboard-content">
            <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

            <section class="portal-head notification-heading">
                <div>
                    <h1>Thông báo</h1>
                    <p class="muted">Theo dõi và xử lý các thông báo quan trọng của hệ thống.</p>
                </div>
            </section>

            <div class="notification-toolbar">
                <div class="notification-tabs" role="tablist" aria-label="Lọc nhanh thông báo">
                    <button type="button" class="notification-tab is-active" data-notification-filter="all" role="tab" aria-selected="true">
                        Tất cả
                    </button>
                    <button type="button" class="notification-tab" data-notification-filter="unread" role="tab" aria-selected="false">
                        Chưa đọc
                    </button>
                    <button type="button" class="notification-tab" data-notification-filter="room" role="tab" aria-selected="false">
                        Phòng chiếu
                    </button>
                </div>
                <div class="notification-toolbar-actions">
                    <div class="notification-search-wrap">
                        <svg class="notification-search-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="11" cy="11" r="8"></circle>
                            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                        </svg>
                        <input type="search" id="notificationSearchInput" placeholder="Tìm theo tiêu đề hoặc nội dung..." aria-label="Tìm kiếm thông báo">
                    </div>
                    <label class="notification-select-wrap">
                        <span class="visually-hidden">Lọc theo nguồn</span>
                        <select id="notificationCategoryFilter" aria-label="Lọc thông báo theo nguồn">
                            <option value="all">Tất cả nguồn</option>
                            <option value="room">Phòng chiếu</option>
                            <option value="showtime">Suất chiếu</option>
                            <option value="booking">Đặt vé</option>
                            <option value="system">Hệ thống</option>
                        </select>
                    </label>
                </div>
            </div>

            <section class="notification-table-panel" aria-label="Danh sách thông báo">
                <div class="table-responsive">
                    <table class="notification-table">
                        <thead>
                            <tr>
                                <th scope="col">Tiêu đề</th>
                                <th scope="col">Nội dung</th>
                                <th scope="col">Thời gian</th>
                                <th scope="col">Nguồn</th>
                                <th scope="col">Trạng thái</th>
                                <th scope="col">Thao tác</th>
                            </tr>
                        </thead>
                        <tbody id="notificationList">
                            <c:forEach var="note" items="${notifications}">
                                <tr class="notification-row ${note.read ? '' : 'is-unread'}"
                                    data-category="${fn:escapeXml(note.category)}" data-read="${fn:escapeXml(note.read)}">
                                    <td data-label="Tiêu đề">
                                        <div class="notification-title-cell">
                                            <span class="notification-status-dot" aria-label="${note.read ? 'Đã đọc' : 'Chưa đọc'}"></span>
                                            <c:set var="displayTitle" value="${note.title}" />
                                            <c:set var="displayTitle" value="${fn:replace(displayTitle, '💸 ', '')}" />
                                            <c:set var="displayTitle" value="${fn:replace(displayTitle, '🔥 ', '')}" />
                                            <c:set var="displayTitle" value="${fn:replace(displayTitle, '⚖️ ', '')}" />
                                            <c:set var="displayTitle" value="${fn:replace(displayTitle, '📽️ ', '')}" />
                                            <c:set var="displayTitle" value="${fn:replace(displayTitle, '🚩 ', '')}" />
                                            <c:set var="displayTitle" value="${fn:replace(displayTitle, '🚨 ', '')}" />
                                            <c:if test="${fn:contains(displayTitle, 'YÊU CẦU HOÀN TIỀN VÉ #')}">
                                                <c:set var="msgText" value="${note.message}" />
                                                <c:if test="${fn:contains(msgText, '(') and fn:contains(msgText, ')')}">
                                                    <c:set var="emailWithSuffix" value="${fn:substringAfter(msgText, '(')}" />
                                                    <c:set var="extractedEmail" value="${fn:substringBefore(emailWithSuffix, ')')}" />
                                                    <c:if test="${fn:contains(extractedEmail, '@')}">
                                                        <c:set var="displayTitle" value="YÊU CẦU HOÀN TIỀN VÉ từ ${extractedEmail}" />
                                                    </c:if>
                                                </c:if>
                                            </c:if>
                                            <strong>${fn:escapeXml(displayTitle)}</strong>
                                        </div>
                                    </td>
                                    <td data-label="Nội dung"><p class="notification-copy">${fn:escapeXml(note.message)}</p></td>
                                    <td data-label="Thời gian"><time>${fn:escapeXml(note.createdAtDisplay)}</time></td>
                                    <td data-label="Nguồn"><span class="notification-source">${fn:escapeXml(note.category)}</span></td>
                                    <td data-label="Trạng thái">
                                        <c:choose>
                                            <c:when test="${not note.read}">
                                                <span class="notification-badge badge-unread">Chưa đọc</span>
                                            </c:when>
                                            <c:when test="${note.targetType eq 'UserAppeal' and note.read}">
                                                <span class="notification-badge badge-resolved">Đã xử lý</span>
                                            </c:when>
                                            <c:otherwise>
                                                <span class="notification-badge badge-read">Đã đọc</span>
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                    <td data-label="Thao tác">
                                        <div class="notification-actions">
                                            <c:if test="${not empty note.actionUrl}">
                                                <a href="${pageContext.request.contextPath}${fn:escapeXml(note.actionUrl)}" class="notification-detail-link">Xem chi tiết</a>
                                            </c:if>
                                            <c:if test="${note.targetType eq 'Room'}">
                                                <form method="post" action="${pageContext.request.contextPath}/admin/notifications" onsubmit="return confirm('Bạn có chắc chắn muốn XÓA VĨNH VIỄN phòng chiếu này không?')">
                                                    <cb:csrf/>
                                                    <input type="hidden" name="action" value="deleteRoom">
                                                    <input type="hidden" name="roomId" value="${fn:escapeXml(note.targetId)}">
                                                    <button type="submit" class="notification-action-danger">Xóa phòng</button>
                                                </form>
                                            </c:if>
                                            <c:if test="${note.targetType eq 'CommentReport'}">
                                                <form method="post" action="${pageContext.request.contextPath}/admin/comments" onsubmit="return confirm('Cảnh cáo người dùng cho bình luận này?')">
                                                    <cb:csrf/>
                                                    <input type="hidden" name="action" value="warn">
                                                    <input type="hidden" name="id" value="${fn:escapeXml(note.targetId)}">
                                                    <button type="submit" class="notification-action-secondary">Cảnh cáo</button>
                                                </form>
                                                <a href="${pageContext.request.contextPath}/admin/comments?reportedOnly=true" class="notification-detail-link">Xem bình luận</a>
                                            </c:if>
                                            <c:if test="${note.targetType eq 'UserAppeal'}">
                                                <c:choose>
                                                    <c:when test="${not note.read}">
                                                        <form method="post" action="${pageContext.request.contextPath}/admin/appeals" onsubmit="return confirm('Duyệt mở khóa tài khoản cho đơn kháng cáo này?')">
                                                            <cb:csrf/>
                                                            <input type="hidden" name="action" value="approve">
                                                            <input type="hidden" name="id" value="${fn:escapeXml(note.targetId)}">
                                                            <button type="submit" class="notification-action-success">Duyệt mở khóa</button>
                                                        </form>
                                                    </c:when>
                                                </c:choose>
                                                <a href="${pageContext.request.contextPath}/admin/appeals" class="notification-detail-link">Xem đơn</a>
                                            </c:if>
                                            <c:if test="${not note.read}">
                                                <form method="post" action="${pageContext.request.contextPath}/admin/notifications">
                                                    <cb:csrf/>
                                                    <input type="hidden" name="action" value="read">
                                                    <input type="hidden" name="id" value="${fn:escapeXml(note.id)}">
                                                    <button type="submit" class="notification-action-secondary">Đã đọc</button>
                                                </form>
                                            </c:if>
                                        </div>
                                    </td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </div>
                <div class="notification-table-footer">
                    <span id="notificationResultCount">Hiển thị 1 đến ${fn:escapeXml(notifications.size())} của ${fn:escapeXml(notifications.size())} thông báo</span>
                    <div class="notification-pagination" role="navigation" aria-label="Phân trang">
                        <button type="button" class="pagination-btn" disabled aria-label="Trang trước">&lt;</button>
                        <button type="button" class="pagination-btn is-active">1</button>
                        <button type="button" class="pagination-btn" disabled aria-label="Trang sau">&gt;</button>
                    </div>
                </div>
                <c:if test="${empty notifications}">
                    <div class="overview-empty notification-empty">Chưa có thông báo nào.</div>
                </c:if>
                <div id="notificationFilteredEmpty" class="overview-empty notification-empty" hidden>Không có thông báo phù hợp bộ lọc.</div>
            </section>
        </div>
    </main>
</div>
<script src="${pageContext.request.contextPath}/assets/js/admin-notifications.js?v=20260805b"></script>
</body>
</html>
