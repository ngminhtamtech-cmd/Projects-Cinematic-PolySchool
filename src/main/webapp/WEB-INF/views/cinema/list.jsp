<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Cụm rạp - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
</head>
<body class="public-page">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="container public-main">
        <%@ include file="/WEB-INF/views/shared/flash.jspf" %>
        <section class="page-hero">
            <span class="eyebrow">Cinema clusters</span>
            <h1 class="section-title">Danh sách cụm rạp</h1>
            <p class="lead-copy">Chọn rạp gần bạn để xem lịch chiếu, phòng chiếu và các suất đang mở bán.</p>
        </section>

        <section class="section">
            <div class="cinema-list">
                <c:forEach var="cinema" items="${cinemas}">
                    <article class="cinema-card reveal">
                        <div class="cinema-visual"></div>
                        <h3>${fn:escapeXml(cinema.name)}</h3>
                        <p class="muted">${fn:escapeXml(cinema.cityName)}</p>
                        <p>${fn:escapeXml(cinema.address)}</p>
                        <p class="muted">${fn:escapeXml(cinema.description)}</p>
                        <a class="time-chip" href="${pageContext.request.contextPath}/showtimes?cinemaId=${fn:escapeXml(cinema.id)}">Xem lịch chiếu</a>
                    </article>
                </c:forEach>
                <c:if test="${empty cinemas}">
                    <article class="cinema-card">
                        <h3>Chưa có rạp</h3>
                        <p class="muted">Hãy chạy seed hoặc tạo rạp mới từ dashboard manager.</p>
                    </article>
                </c:if>
            </div>
        </section>
    </main>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>
    <script src="${pageContext.request.contextPath}/assets/js/main.js"></script>
</body>
</html>
