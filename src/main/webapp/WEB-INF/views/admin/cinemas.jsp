<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="cbf" uri="https://cinebook.local/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Quản Lý Rạp Chiếu - CineBook Admin</title>
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

                <section class="admin-page-head">
                    <div>
                        <h1>Quản lý danh sách Rạp / Cơ sở</h1>
                        <p class="muted">Quản lý hệ thống các cụm rạp/cơ sở chiếu, địa chỉ, hotline và danh sách phòng chiếu.</p>
                    </div>
                    <c:if test="${sessionScope.currentUser.role eq 'admin'}">
                        <a href="${pageContext.request.contextPath}/admin/cinemas?action=create" class="btn-primary">Thêm rạp chiếu mới</a>
                    </c:if>
                </section>

                <nav class="lifecycle-tabs" aria-label="Vòng đời rạp chiếu">
                    <a href="${pageContext.request.contextPath}/admin/cinemas?lifecycle=active" aria-current="${lifecycle eq 'deleted' ? 'false' : 'page'}">Đang quản lý</a>
                    <c:if test="${sessionScope.currentUser.role eq 'admin'}">
                        <a href="${pageContext.request.contextPath}/admin/cinemas?lifecycle=deleted" aria-current="${lifecycle eq 'deleted' ? 'page' : 'false'}">Đã bị xóa</a>
                    </c:if>
                </nav>

                <c:if test="${lifecycle eq 'deleted'}">
                    <div class="alert-banner alert-info" role="status">
                        Rạp đã xóa không còn xuất hiện ở giao diện khách hàng, nhưng toàn bộ suất chiếu,
                        vé đã bán và số liệu doanh thu của rạp <strong>được giữ nguyên 100%</strong> trong Báo cáo.
                    </div>
                </c:if>

                <c:if test="${not empty cinemas}">
                    <div class="admin-table-wrap">
                        <div class="admin-table-scroll" tabindex="0" aria-label="Danh sách rạp và cơ sở chiếu">
                            <table class="admin-data-table admin-entity-table">
                                <thead>
                                    <tr>
                                        <th scope="col">Rạp / Cơ sở</th>
                                        <th scope="col">Khu vực</th>
                                        <th scope="col">Trạng thái</th>
                                        <th scope="col">Loại rạp</th>
                                        <th scope="col">Địa chỉ / Hotline</th>
                                        <th scope="col">Phòng chiếu</th>
                                        <th scope="col">Mô tả</th>
                                        <th scope="col">Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="cinema" items="${cinemas}">
                                        <c:set var="isSpecial" value="false" />
                                        <c:forEach var="sc" items="${specialCinemas}">
                                            <c:if test="${sc.title eq cinema.name}"><c:set var="isSpecial" value="true" /></c:if>
                                        </c:forEach>
                                        <tr data-admin-search-item>
                                            <td>
                                                <div class="admin-record-cell">
                                                    <c:choose>
                                                        <c:when test="${not empty cinema.avatar}">
                                                            <img class="admin-record-thumb admin-record-thumb-cinema"
                                                                 src="${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, cinema.avatar))}"
                                                                 alt="Ảnh ${fn:escapeXml(cinema.name)}">
                                                        </c:when>
                                                        <c:otherwise><span class="admin-record-thumb admin-record-placeholder">Không có ảnh</span></c:otherwise>
                                                    </c:choose>
                                                    <span><strong>${fn:escapeXml(cinema.name)}</strong><small>#${fn:escapeXml(cinema.id)}</small></span>
                                                </div>
                                            </td>
                                            <td>${fn:escapeXml(empty cinema.cityName ? '—' : cinema.cityName)}</td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${cinema.status eq 'deleted'}"><span class="badge-status status-neutral"><span class="status-dot"></span>Đã bị xóa</span></c:when>
                                                    <c:when test="${cinema.status eq 'maintenance'}"><span class="badge-status status-warning"><span class="status-dot"></span>Đang bảo trì</span></c:when>
                                                    <c:when test="${cinema.status eq 'closed' or cinema.status eq 'inactive'}"><span class="badge-status status-danger"><span class="status-dot"></span>Tạm đóng cửa</span></c:when>
                                                    <c:otherwise><span class="badge-status status-success"><span class="status-dot"></span>Đang hoạt động</span></c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td><span class="badge-status ${isSpecial ? 'status-warning' : 'status-neutral'}">${isSpecial ? 'Rạp đặc biệt' : 'Rạp thường'}</span></td>
                                            <td><strong class="admin-table-primary">${fn:escapeXml(empty cinema.address ? '—' : cinema.address)}</strong><small>${fn:escapeXml(empty cinema.phone ? '—' : cinema.phone)}</small></td>
                                            <td><strong>${fn:escapeXml(cinema.roomCount)}</strong> phòng</td>
                                            <td><span class="admin-clamp-2">${fn:escapeXml(empty cinema.description ? '—' : cinema.description)}</span></td>
                                            <td>
                                                <div class="admin-row-actions">
                                                    <c:choose>
                                                        <c:when test="${cinema.status eq 'deleted'}">
                                                            <span class="muted">Chỉ xem — dữ liệu doanh thu được giữ nguyên</span>
                                                        </c:when>
                                                        <c:otherwise>
                                                            <a href="${pageContext.request.contextPath}/admin/cinemas/films?cinemaId=${fn:escapeXml(cinema.id)}" class="button secondary">Quản lý phim</a>
                                                            <a href="${pageContext.request.contextPath}/admin/cinemas?action=edit&id=${fn:escapeXml(cinema.id)}" class="button secondary">Chỉnh sửa</a>
                                                            <c:if test="${sessionScope.currentUser.role eq 'admin'}">
                                                                <button type="button" class="button danger" onclick="openConfirmDeleteCinemaModal('${fn:escapeXml(cinema.id)}', '${fn:escapeXml(cinema.name)}')">Xóa</button>
                                                            </c:if>
                                                        </c:otherwise>
                                                    </c:choose>
                                                </div>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </c:if>

                <c:if test="${empty cinemas}">
                    <div class="empty-state-block">
                        <div class="empty-state-icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:28px;height:28px;"><path d="M4 21V5a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v16M15 10h4a1 1 0 0 1 1 1v10M8 8h3M8 12h3M8 16h3M3 21h18"/></svg>
                        </div>
                        <c:choose>
                            <c:when test="${lifecycle eq 'deleted'}">
                                <h2>Chưa có rạp chiếu nào bị xóa</h2>
                                <p class="muted">Rạp bị xóa sẽ được liệt kê ở đây thay vì biến mất khỏi hệ thống.</p>
                            </c:when>
                            <c:otherwise>
                                <h2>Chưa có rạp chiếu nào</h2>
                                <p class="muted">Bấm nút bên dưới để tạo cụm rạp chiếu đầu tiên vào hệ thống CineBook.</p>
                                <c:if test="${sessionScope.currentUser.role eq 'admin'}"><a href="${pageContext.request.contextPath}/admin/cinemas?action=create" class="btn-primary">
                                    <span>Thêm rạp chiếu mới ngay</span>
                                </a></c:if>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </c:if>
            </div>
        </main>
    </div>

    <c:if test="${sessionScope.currentUser.role eq 'admin'}">
    <!-- MINIMAL CONFIRM DELETE CINEMA MODAL -->
    <div id="confirmDeleteCinemaModal" class="modal-overlay" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(15,23,42,0.5); backdrop-filter:blur(4px); z-index:99999; align-items:center; justify-content:center;">
        <div class="modal-card" style="background:#ffffff; border-radius:16px; width:90%; max-width:440px; padding:24px; box-shadow:0 20px 25px -5px rgba(0,0,0,0.1), 0 8px 10px -6px rgba(0,0,0,0.1); text-align:left;">
            <div style="display:flex; align-items:center; gap:12px; margin-bottom:14px;">
                <div style="width:40px; height:40px; border-radius:10px; background:#fef2f2; border:1px solid #fee2e2; display:flex; align-items:center; justify-content:center; flex-shrink:0;">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#dc2626" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M10 11v6M14 11v6"/>
                    </svg>
                </div>
                <div>
                    <h3 style="margin:0; font-size:1.1rem; font-weight:700; color:#0f172a;">Xóa rạp / cơ sở chiếu</h3>
                    <span style="font-size:0.8rem; color:#64748b;">Xác nhận thực hiện thao tác xóa</span>
                </div>
            </div>
            
            <p style="font-size:0.92rem; color:#334155; line-height:1.55; margin:0 0 20px 0;">
                Bạn có chắc chắn muốn xóa rạp chiếu <strong id="deleteCinemaNameText" style="color:#0f172a;"></strong>?
            </p>

            <form method="post" action="${pageContext.request.contextPath}/admin/cinemas" id="confirmDeleteCinemaForm">
                <cb:csrf/>
                <input type="hidden" name="action" value="delete">
                <input type="hidden" name="id" id="confirmDeleteCinemaIdInput" value="">
                <div style="display:flex; justify-content:flex-end; gap:10px;">
                    <button type="button" class="button secondary" onclick="closeConfirmDeleteCinemaModal()" style="padding:9px 16px; border-radius:8px; font-weight:600;">Hủy bỏ</button>
                    <button type="submit" class="button danger" style="background:#dc2626; color:#ffffff; padding:9px 18px; border-radius:8px; font-weight:600; border:none; cursor:pointer;">
                        Xác nhận xóa
                    </button>
                </div>
            </form>
        </div>
    </div>

    <script>
        function openConfirmDeleteCinemaModal(cinemaId, cinemaName) {
            document.getElementById('confirmDeleteCinemaIdInput').value = cinemaId;
            document.getElementById('deleteCinemaNameText').textContent = "'" + cinemaName + "'";
            document.getElementById('confirmDeleteCinemaModal').style.display = 'flex';
        }

        function closeConfirmDeleteCinemaModal() {
            document.getElementById('confirmDeleteCinemaModal').style.display = 'none';
        }
    </script>
    </c:if>
</body>
</html>
