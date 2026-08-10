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
    <title>Quan ly binh luan - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
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
                        <h1>Quản lý bình luận</h1>
                        <p class="muted">Lọc bình luận theo phim và xử lý báo cáo vi phạm.</p>
                    </div>
                </div>

                <section class="panel" style="margin-bottom:20px;">
                    <form method="get" action="${pageContext.request.contextPath}/admin/comments" class="toolbar">
                        <label>Lọc theo phim
                            <select name="filmId">
                                <option value="">Tất cả phim</option>
                                <c:forEach var="film" items="${films}">
                                    <option value="${fn:escapeXml(film.id)}" ${fn:escapeXml(film.id == selectedCommentFilmId ? 'selected' : '')}>${fn:escapeXml(film.title)}${film.deleted ? ' — đã xóa' : ''}</option>
                                </c:forEach>
                            </select>
                        </label>
                        <div class="checkbox-line" style="margin-top:10px;">
                            <input type="checkbox" name="reportedOnly" id="repOnly" value="true" ${fn:escapeXml(reportedOnly ? 'checked' : '')}>
                            <label for="repOnly" style="display:inline; font-weight:normal;">Chỉ hiển thị bình luận bị báo cáo vi phạm</label>
                        </div>
                        <div class="form-actions">
                            <button type="submit">Lọc dữ liệu</button>
                            <a class="button secondary" href="${pageContext.request.contextPath}/admin/comments" style="min-height:40px; padding: 10px 16px; font-size:14px; display:inline-flex; align-items:center;">Xóa bộ lọc</a>
                        </div>
                    </form>
                </section>

                <section class="portal-stack" style="margin-top:20px;">
                    <h2>Danh sách bình luận</h2>
                    <div class="table-wrap">
                        <table class="data-table">
                            <thead>
                                <tr>
                                    <th>Mã</th>
                                    <th>Phim</th>
                                    <th>Tác giả</th>
                                    <th>Trạng thái</th>
                                    <th>Nội dung</th>
                                    <th>Ngày đăng</th>
                                    <th>Hành động</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="comment" items="${comments}">
                                    <tr>
                                        <td class="mono">#${fn:escapeXml(comment.id)}</td>
                                        <td><strong>${fn:escapeXml(comment.filmTitle)}</strong>
                                            <c:if test="${comment.filmDeleted}"><br><span class="status-pill danger">Phim đã xóa</span></c:if>
                                        </td>
                                        <td>
                                            <strong>${fn:escapeXml(comment.userFullName)}</strong><br>
                                            <small class="muted">${fn:escapeXml(comment.userEmail)}</small><br>
                                            <c:choose>
                                                <c:when test="${comment.userWarningCount >= 2}">
                                                    <span style="font-size:0.78rem; font-weight:700; color:#ef4444;">
                                                        Cảnh cáo: ${fn:escapeXml(comment.userWarningCount)}/3
                                                    </span>
                                                </c:when>
                                                <c:otherwise>
                                                    <span style="font-size:0.78rem; font-weight:700; color:#f59e0b;">
                                                        Cảnh cáo: ${fn:escapeXml(comment.userWarningCount)}/3
                                                    </span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${comment.userIsLocked}">
                                                    <span class="status-pill danger" style="background:#fee2e2; color:#991b1b; font-weight:700;">ĐÃ KHÓA TK</span>
                                                </c:when>
                                                <c:when test="${comment.report}">
                                                    <span class="status-pill warning" style="background:#fff7ed; color:#c2410c; font-weight:700;">BỊ REPORT</span>
                                                </c:when>
                                                <c:otherwise>
                                                    <span class="status-pill success">Bình thường</span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td style="max-width:260px;">${fn:escapeXml(comment.content)}</td>
                                        <td>${fn:escapeXml(comment.createdAtDisplay)}</td>
                                        <td>
                                            <div class="inline-actions" style="display:flex; flex-wrap:wrap; gap:6px;">
                                                <c:if test="${not comment.userIsLocked}">
                                                    <form method="post" action="${pageContext.request.contextPath}/admin/comments">
                                                        <cb:csrf/>
                                                        <input type="hidden" name="id" value="${fn:escapeXml(comment.id)}">
                                                        <input type="hidden" name="userId" value="${fn:escapeXml(comment.userId)}">
                                                        <button class="button warning" type="submit" name="action" value="warn" onclick="return confirm('Xử lý CẢNH CÁO người dùng cho bình luận này? (Đạt 3 lần cảnh cáo sẽ TỰ ĐỘNG KHÓA TÀI KHOẢN)')" style="min-height:28px; padding: 4px 8px; font-size:11px; background:#f59e0b; color:#fff; border:none; border-radius:4px; font-weight:700; cursor:pointer;" title="Cảnh cáo người dùng (+1 warning)">
                                                            Cảnh cáo (${fn:escapeXml(comment.userWarningCount)}/3)
                                                        </button>
                                                    </form>
                                                    <form method="post" action="${pageContext.request.contextPath}/admin/comments">
                                                        <cb:csrf/>
                                                        <input type="hidden" name="id" value="${fn:escapeXml(comment.id)}">
                                                        <input type="hidden" name="userId" value="${fn:escapeXml(comment.userId)}">
                                                        <input type="hidden" name="lockReason" value="Khóa tài khoản do vi phạm nghiêm trọng quy định bình luận.">
                                                        <button class="danger" type="submit" name="action" value="lock" onclick="return confirm('KHÓA TÀI KHOẢN người dùng này ngay lập tức?')" style="min-height:28px; padding: 4px 8px; font-size:11px; background:#ef4444; color:#fff; border:none; border-radius:4px; font-weight:700; cursor:pointer;">
                                                            Khóa TK
                                                        </button>
                                                    </form>
                                                </c:if>
                                                <c:if test="${comment.report}">
                                                    <form method="post" action="${pageContext.request.contextPath}/admin/comments">
                                                        <cb:csrf/>
                                                        <input type="hidden" name="id" value="${fn:escapeXml(comment.id)}">
                                                        <button class="secondary" type="submit" name="action" value="clear" style="min-height:28px; padding: 4px 8px; font-size:11px;">Gỡ report</button>
                                                    </form>
                                                </c:if>
                                                <form method="post" action="${pageContext.request.contextPath}/admin/comments">
                                                    <cb:csrf/>
                                                    <input type="hidden" name="id" value="${fn:escapeXml(comment.id)}">
                                                    <button class="danger" type="submit" name="action" value="delete" onclick="return confirm('Xóa bình luận này?')" style="min-height:28px; padding: 4px 8px; font-size:11px;">Xóa</button>
                                                </form>
                                            </div>
                                        </td>
                                    </tr>
                                </c:forEach>
                                <c:if test="${empty comments}">
                                    <tr>
                                        <td colspan="7" class="text-center">Không có bình luận phù hợp bộ lọc.</td>
                                    </tr>
                                </c:if>
                            </tbody>
                        </table>
                    </div>
                </section>
            </div>
        </main>
    </div>
</body>
</html>
