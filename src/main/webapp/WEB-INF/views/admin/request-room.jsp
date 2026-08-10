<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Đề nghị phòng mới - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260810g">
    <style>
        .room-request-card{max-width:820px;margin:0 auto}.room-request-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}
        .room-request-grid label{display:flex;flex-direction:column;gap:6px}.room-request-grid .wide{grid-column:1/-1}
        .layout-note{background:#f6f3ff;border:1px solid #ddd3fa;border-radius:10px;padding:12px;color:#4c2c86;font-size:13px}
        @media(max-width:650px){.room-request-grid{grid-template-columns:1fr}.room-request-grid .wide{grid-column:auto}}
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
                <div><h1>Đề nghị phòng chiếu mới</h1><p class="muted">Phòng và toàn bộ sơ đồ ghế sẽ được tạo nguyên tử sau khi admin duyệt.</p></div>
                <a class="button secondary" href="${pageContext.request.contextPath}/admin/requests">Quay lại</a>
            </section>
            <section class="panel-card room-request-card">
                <form method="post" action="${pageContext.request.contextPath}/admin/requests">
                    <cb:csrf/><input type="hidden" name="action" value="create-room">
                    <div class="room-request-grid">
                        <label class="wide">Rạp<input value="${fn:escapeXml(cinemaContextName)}" readonly aria-readonly="true"></label>
                        <label>Tên phòng<input name="name" maxlength="50" required placeholder="Ví dụ: Phòng 05"></label>
                        <label>Loại phòng<select name="roomType"><option value="STANDARD">Standard</option><option value="VIP">VIP</option><option value="IMAX">IMAX</option><option value="COUPLE">Couple</option></select></label>
                        <label>Số hàng<input type="number" name="layoutRows" min="1" max="26" value="10" required></label>
                        <label>Số ghế mỗi hàng<input type="number" name="seatsPerRow" min="1" max="50" value="12" required></label>
                        <label>Hàng VIP<input name="vipRows" maxlength="80" value="C,D,E,F,G" placeholder="C,D,E"></label>
                        <label>Phụ phí VIP<input type="number" name="vipSurcharge" min="0" step="1000" value="20000"></label>
                        <label>Hàng ghế đôi<input name="coupleRows" maxlength="80" placeholder="H,I"></label>
                        <label>Phụ phí ghế đôi<input type="number" name="coupleSurcharge" min="0" step="1000" value="40000"></label>
                        <div class="layout-note wide">Mỗi hàng nhập bằng một chữ cái A–Z, phân cách bằng dấu phẩy. Admin duyệt đúng tên, loại phòng và sơ đồ ghế này; không chỉnh âm thầm.</div>
                    </div>
                    <div style="display:flex;justify-content:flex-end;margin-top:18px"><button class="btn-primary" type="submit">Gửi admin duyệt</button></div>
                </form>
            </section>
        </div>
    </main>
</div>
<script src="${pageContext.request.contextPath}/assets/js/admin-ui.js"></script>
</body>
</html>
