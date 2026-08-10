<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="cbf" uri="https://cinebook.local/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Danh sách phim - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
</head>
<body class="public-page">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="container public-main">
        <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

        <section class="page-hero">
            <div class="page-head">
                <div>
                    <span class="eyebrow">Movie catalog</span>
                    <h1 class="section-title">Phim đang chiếu</h1>
                    <p class="lead-copy">Tìm nhanh theo tên phim, diễn viên hoặc đạo diễn. Mỗi poster dẫn thẳng tới chi tiết phim và lịch suất chiếu.</p>
                </div>
                <form class="search-bar" method="get" action="${pageContext.request.contextPath}/films">
                    <input type="search" name="q" value="${fn:escapeXml(keyword)}" placeholder="Nhập tên phim hoặc diễn viên">
                    <button type="submit">Tìm kiếm</button>
                </form>
            </div>
        </section>

        <section class="section">
            <div class="section-row">
                <div>
                    <span class="eyebrow dark">Browse</span>
                    <h2 class="section-title">Tất cả phim</h2>
                </div>
                <div class="movie-tabs">
                    <span class="movie-tab is-active">Đang chiếu</span>
                    <span class="movie-tab">Sắp chiếu</span>
                </div>
            </div>

            <div class="movie-grid">
                <c:forEach var="film" items="${films}">
                    <a class="movie-card reveal" href="${pageContext.request.contextPath}/films/${fn:escapeXml(film.id)}">
                        <c:choose>
                            <c:when test="${not empty film.thumbnail}">
                                <div class="poster" style="background-image:url('${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, film.thumbnail))}');">
                                    <div class="poster-copy">
                                        <span class="poster-kicker">${fn:escapeXml(film.ageRating)}</span>
                                        <strong>${fn:escapeXml(film.title)}</strong>
                                    </div>
                                </div>
                            </c:when>
                            <c:otherwise>
                                <div class="poster">
                                    <div class="poster-copy">
                                        <span class="poster-kicker">${fn:escapeXml(film.ageRating)}</span>
                                        <strong>${fn:escapeXml(film.title)}</strong>
                                    </div>
                                </div>
                            </c:otherwise>
                        </c:choose>
                        <div class="movie-card-body">
                            <%-- EX-01: badge tren poster, cung rule voi banner o trang chi tiet. --%>
                            <cb:filmExpiring film="${film}"/>
                            <h3>${fn:escapeXml(film.title)}</h3>
                            <p class="muted">${fn:escapeXml(film.durationMinutes)} phút · ${fn:escapeXml(film.releaseDate)}</p>
                            <p class="muted">Đạo diễn: ${fn:escapeXml(film.directors)}</p>
                            <div class="meta-line">
                                <span class="badge">Rating ${fn:escapeXml(film.rating)}</span>
                                <span class="badge">${fn:escapeXml(film.language)}</span>
                            </div>
                        </div>
                    </a>
                </c:forEach>

                <c:if test="${empty films}">
                    <article class="movie-card">
                        <div class="poster">
                            <div class="poster-copy">
                                <span class="poster-kicker">No result</span>
                                <strong>Không tìm thấy phim</strong>
                            </div>
                        </div>
                        <div class="movie-card-body">
                            <h3>Không có kết quả phù hợp</h3>
                            <p class="muted">Thử từ khóa khác hoặc thêm phim mới từ dashboard quản lý.</p>
                        </div>
                    </article>
                </c:if>
            </div>
        </section>
    </main>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>
    <script src="${pageContext.request.contextPath}/assets/js/main.js"></script>
</body>
</html>
