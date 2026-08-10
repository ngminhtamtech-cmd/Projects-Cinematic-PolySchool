<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Yêu cầu phê duyệt - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260810g">
    <style>
        .request-actions{display:flex;flex-wrap:wrap;gap:6px;align-items:center;min-width:250px}
        .request-actions form{display:flex;gap:6px;align-items:center;margin:0}
        .request-actions input{width:150px;min-height:34px}
        .request-subject{display:flex;flex-direction:column;gap:3px;min-width:190px}
        .request-subject small{color:#6e7180}
        .request-status{display:inline-flex;align-items:center;border-radius:999px;padding:4px 9px;font-size:12px;font-weight:650;border:1px solid #ddd}
        .request-status.pending{background:#fff7e6;color:#9a5b00;border-color:#f0d39d}
        .request-status.approved{background:#eaf8f0;color:#147448;border-color:#bae2cc}
        .request-status.rejected{background:#fff0ef;color:#a7302a;border-color:#efc2bf}
        .request-status.cancelled{background:#f3f3f6;color:#666979;border-color:#dddde6}
        @media(max-width:900px){.request-actions form{width:100%;flex-wrap:wrap}.request-actions input{flex:1}}
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
                    <h1>Yêu cầu phê duyệt</h1>
                    <p class="muted">
                        <c:choose>
                            <c:when test="${sessionScope.currentUser.role eq 'admin'}">Duyệt phim và phòng theo đúng rạp đang chọn.</c:when>
                            <c:otherwise>Theo dõi đề xuất gửi đến admin cho ${fn:escapeXml(cinemaContextName)}.</c:otherwise>
                        </c:choose>
                    </p>
                </div>
                <c:if test="${sessionScope.currentUser.role eq 'manager'}">
                    <div style="display:flex;gap:8px;flex-wrap:wrap">
                        <a class="button secondary" href="${pageContext.request.contextPath}/admin/requests?action=room">Đề nghị phòng mới</a>
                        <a class="btn-primary" href="${pageContext.request.contextPath}/admin/requests?action=film">Đề nghị phim</a>
                    </div>
                </c:if>
            </section>

            <nav class="lifecycle-tabs" aria-label="Lọc trạng thái yêu cầu">
                <a href="${pageContext.request.contextPath}/admin/requests" aria-current="${empty statusFilter ? 'page' : 'false'}">Tất cả</a>
                <a href="${pageContext.request.contextPath}/admin/requests?status=PENDING" aria-current="${statusFilter eq 'PENDING' ? 'page' : 'false'}">Chờ duyệt</a>
                <a href="${pageContext.request.contextPath}/admin/requests?status=APPROVED" aria-current="${statusFilter eq 'APPROVED' ? 'page' : 'false'}">Đã duyệt</a>
                <a href="${pageContext.request.contextPath}/admin/requests?status=REJECTED" aria-current="${statusFilter eq 'REJECTED' ? 'page' : 'false'}">Từ chối</a>
            </nav>

            <c:choose>
                <c:when test="${not empty requests}">
                    <div class="admin-table-wrap">
                        <table class="admin-data-table">
                            <thead><tr>
                                <th>Loại</th><th>Nội dung</th><th>Rạp</th><th>Người gửi</th>
                                <th>Thời gian</th><th>Trạng thái</th><th>Thao tác</th>
                            </tr></thead>
                            <tbody>
                            <c:forEach var="approval" items="${requests}">
                                <tr data-admin-search-item>
                                    <td><strong>${fn:escapeXml(approval.typeDisplay)}</strong><br><small>#${fn:escapeXml(approval.id)}</small></td>
                                    <td>
                                        <span class="request-subject">
                                            <strong>${fn:escapeXml(approval.subjectName)}</strong>
                                            <c:if test="${approval.requestType eq 'ROOM_CREATE'}">
                                                <small>${fn:escapeXml(approval.roomType)} · ${fn:escapeXml(approval.layoutRows)} hàng × ${fn:escapeXml(approval.seatsPerRow)} ghế</small>
                                            </c:if>
                                            <c:if test="${not empty approval.reviewNote}"><small>${fn:escapeXml(approval.reviewNote)}</small></c:if>
                                        </span>
                                    </td>
                                    <td>${fn:escapeXml(approval.cinemaName)}</td>
                                    <td>${fn:escapeXml(approval.requestedByName)}</td>
                                    <td>${fn:escapeXml(approval.requestedAt)}</td>
                                    <td><span class="request-status ${fn:toLowerCase(approval.status)}">${fn:escapeXml(approval.status)}</span></td>
                                    <td>
                                        <div class="request-actions">
                                            <c:if test="${approval.pending and sessionScope.currentUser.role eq 'admin'}">
                                                <form method="post" action="${pageContext.request.contextPath}/admin/requests">
                                                    <cb:csrf/>
                                                    <input type="hidden" name="action" value="approve">
                                                    <input type="hidden" name="id" value="${fn:escapeXml(approval.id)}">
                                                    <c:if test="${approval.requestType eq 'FILM_CREATE'}">
                                                        <input name="duplicateFilmId" type="number" min="1" placeholder="ID phim trùng (nếu có)" aria-label="ID phim hiện có nếu đề xuất bị trùng">
                                                    </c:if>
                                                    <button class="button success" type="submit">Duyệt</button>
                                                </form>
                                                <form method="post" action="${pageContext.request.contextPath}/admin/requests">
                                                    <cb:csrf/>
                                                    <input type="hidden" name="action" value="reject">
                                                    <input type="hidden" name="id" value="${fn:escapeXml(approval.id)}">
                                                    <input name="reviewNote" maxlength="1000" required placeholder="Lý do từ chối" aria-label="Lý do từ chối">
                                                    <button class="button danger" type="submit">Từ chối</button>
                                                </form>
                                            </c:if>
                                            <c:if test="${approval.pending and sessionScope.currentUser.role eq 'manager'}">
                                                <form method="post" action="${pageContext.request.contextPath}/admin/requests">
                                                    <cb:csrf/>
                                                    <input type="hidden" name="action" value="cancel">
                                                    <input type="hidden" name="id" value="${fn:escapeXml(approval.id)}">
                                                    <button class="button danger" type="submit">Hủy yêu cầu</button>
                                                </form>
                                            </c:if>
                                            <c:if test="${not approval.pending}"><span class="muted">Đã kết thúc</span></c:if>
                                        </div>
                                    </td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:when>
                <c:otherwise>
                    <article class="empty-state-block">
                        <h3>Không có yêu cầu phù hợp</h3>
                        <p class="muted">Các yêu cầu mới sẽ xuất hiện ở đây theo phạm vi rạp.</p>
                    </article>
                </c:otherwise>
            </c:choose>
        </div>
    </main>
</div>
<script src="${pageContext.request.contextPath}/assets/js/admin-ui.js"></script>
</body>
</html>
