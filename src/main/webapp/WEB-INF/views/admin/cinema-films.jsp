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
    <title>Quản lý Phim Chiếu - ${fn:escapeXml(cinema.name)} - CineBook Admin</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
    <style>
        .cf-header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            margin-bottom: 24px;
        }
        .cf-breadcrumb {
            font-size: 0.85rem;
            color: #64748b;
            margin-bottom: 6px;
        }
        .cf-breadcrumb a {
            color: #64748b;
            text-decoration: none;
        }
        .cf-breadcrumb a:hover {
            color: #38bdf8;
        }
        .cinema-info-banner {
            background: linear-gradient(135deg, #0f172a, #1e293b);
            border: 1px solid #334155;
            border-radius: 12px;
            padding: 20px 24px;
            margin-bottom: 24px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 20px;
        }
        .cinema-info-title {
            font-size: 1.3rem;
            font-weight: 800;
            color: #f8fafc;
            margin: 0 0 6px 0;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .cinema-info-meta {
            font-size: 0.88rem;
            color: #94a3b8;
            display: flex;
            gap: 16px;
            flex-wrap: wrap;
        }
        .films-selection-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
            gap: 20px;
            margin-bottom: 24px;
        }
        .film-select-card {
            background: #ffffff;
            border: 2px solid #e2e8f0;
            border-radius: 12px;
            padding: 16px;
            display: flex;
            gap: 14px;
            align-items: center;
            cursor: pointer;
            transition: all 0.2s ease;
            position: relative;
            user-select: none;
        }
        .film-select-card:hover {
            border-color: #38bdf8;
            box-shadow: 0 4px 12px rgba(2, 132, 199, 0.12);
        }
        .film-select-card.is-selected {
            border-color: #0284c7;
            background: #f0f9ff;
            box-shadow: 0 4px 14px rgba(2, 132, 199, 0.18);
        }
        .film-card-thumb {
            width: 70px;
            height: 98px;
            border-radius: 8px;
            object-fit: cover;
            flex-shrink: 0;
            background: #f1f5f9;
            border: 1px solid #cbd5e1;
        }
        .film-card-details {
            flex: 1;
            min-width: 0;
        }
        .film-card-title {
            font-size: 0.98rem;
            font-weight: 800;
            color: #0f172a;
            margin: 0 0 6px 0;
            line-height: 1.3;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .film-card-sub {
            font-size: 0.8rem;
            color: #64748b;
            margin-bottom: 8px;
        }
        .film-check-box {
            width: 22px;
            height: 22px;
            accent-color: #0284c7;
            cursor: pointer;
        }
        .cf-actions-bar {
            display: flex;
            justify-content: flex-end;
            gap: 12px;
            padding: 16px 24px;
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 12px;
        }
    </style>
</head>
<body class="admin-body">
    <div class="dashboard">
        <%@ include file="/WEB-INF/views/admin/sidebar.jspf" %>
        <main class="dashboard-main">
            <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
            <div class="dashboard-content">
                <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

                <!-- HEADER & BREADCRUMB -->
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;flex-wrap:wrap;gap:14px;">
                    <div>
                        <h1 style="font-size: 22px; font-weight: 600; color: #1A1A21; margin: 0 0 4px;">
                            Quản lý danh sách phim chiếu tại rạp
                        </h1>
                        <p class="muted" style="margin: 0; font-size: 13px; color: #6E6E7A;">
                            Tích chọn các bộ phim được phép khởi chiếu và tạo lịch chiếu tại cụm rạp này.
                        </p>
                    </div>
                    <div>
                        <a href="${pageContext.request.contextPath}/admin/cinemas" class="btn-secondary">
                            ← Quay lại danh sách rạp
                        </a>
                    </div>
                </div>

                <!-- CINEMA INFO BANNER -->
                <div class="panel-card" style="padding: 16px 20px; margin-bottom: 20px; display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; background: #FFFFFF;">
                    <div>
                        <h2 style="font-size: 16px; font-weight: 600; color: #1A1A21; margin: 0 0 4px; display: flex; align-items: center; gap: 8px;">
                            <span>${fn:escapeXml(cinema.name)}</span>
                        </h2>
                        <div style="font-size: 13px; color: #6E6E7A; display: flex; gap: 16px; flex-wrap: wrap;">
                            <span>${fn:escapeXml(cinema.address)}</span>
                            <span>Hotline: ${fn:escapeXml(empty cinema.phone ? '—' : cinema.phone)}</span>
                        </div>
                    </div>
                    <div>
                        <span class="badge-status status-brand" style="font-size: 12px; padding: 4px 10px; background: #F5F2FF; border-color: #DDD3FE; color: #6D28D9;">
                            Mã rạp: #${fn:escapeXml(cinema.id)}
                        </span>
                    </div>
                </div>

                <!-- FORM ASSIGN FILMS -->
                <form method="post" action="${pageContext.request.contextPath}/admin/cinemas/films">
                    <cb:csrf/>
                    <input type="hidden" name="cinemaId" value="${fn:escapeXml(cinema.id)}">

                    <div style="margin-bottom: 14px; display: flex; justify-content: space-between; align-items: center;">
                        <h2 style="font-size: 14px; font-weight: 600; color: #1A1A21; margin: 0;">
                            Danh sách phim có trong hệ thống (${fn:escapeXml(fn:length(allFilms))} bộ phim)
                        </h2>
                        <div style="font-size: 12px; color: #8A8A96;">
                            Tích chọn phim để gán cho rạp này
                        </div>
                    </div>

                    <div style="display:grid;grid-template-columns:repeat(auto-fill, minmax(280px, 1fr));gap:14px;margin-bottom:20px;">
                        <c:forEach var="film" items="${allFilms}">
                            <c:set var="isFilmSelected" value="false" />
                            <c:forEach var="sfId" items="${selectedFilmIds}">
                                <c:if test="${sfId eq film.id}">
                                    <c:set var="isFilmSelected" value="true" />
                                </c:if>
                            </c:forEach>

                            <label id="filmCard_${fn:escapeXml(film.id)}" style="background:#FFFFFF;border:1px solid ${fn:escapeXml(isFilmSelected ? '#DDD3FE' : '#E8E8EE')};border-radius:12px;padding:14px;display:flex;gap:12px;align-items:center;cursor:pointer;transition:all 0.15s ease;user-select:none;${fn:escapeXml(isFilmSelected ? 'background:#F5F2FF;' : '')}">
                                <c:choose>
                                    <c:when test="${not empty film.thumbnail}">
                                        <img src="${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, film.thumbnail))}" style="width:64px;height:90px;border-radius:8px;object-fit:cover;flex-shrink:0;background:#F5F5F8;border:1px solid #E8E8EE;" alt="${fn:escapeXml(film.title)}">
                                    </c:when>
                                    <c:otherwise>
                                        <div style="width:64px;height:90px;border-radius:8px;background:#F5F5F8;border:1px solid #E8E8EE;display:flex;align-items:center;justify-content:center;color:#8A8A96;font-size:10px;flex-shrink:0;text-align:center;">Chưa có ảnh</div>
                                    </c:otherwise>
                                </c:choose>
                                <div style="flex:1;min-width:0;">
                                    <h4 style="font-size:13px;font-weight:600;color:#1A1A21;margin:0 0 4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${fn:escapeXml(film.title)}</h4>
                                    <div style="font-size:12px;color:#6E6E7A;margin-bottom:6px;line-height:1.3;">
                                        ${fn:escapeXml(film.duration)} phút<br>
                                        ${empty film.genre ? '—' : fn:escapeXml(film.genre)}
                                    </div>
                                    <span class="badge-age">
                                        ${fn:escapeXml(empty film.ageRating ? '—' : film.ageRating)}
                                    </span>
                                </div>
                                <div>
                                    <input type="checkbox" name="filmIds" value="${fn:escapeXml(film.id)}" ${fn:escapeXml(isFilmSelected ? 'checked' : '')} onchange="toggleCardSelect(this, 'filmCard_${fn:escapeXml(film.id)}')" style="width:18px;height:18px;accent-color:#6D28D9;cursor:pointer;">
                                </div>
                            </label>
                        </c:forEach>
                    </div>

                    <div style="display:flex;justify-content:flex-end;gap:10px;padding:16px 20px;background:#FFFFFF;border:1px solid #E8E8EE;border-radius:12px;">
                        <a href="${pageContext.request.contextPath}/admin/cinemas" class="btn-secondary">
                            Hủy bỏ
                        </a>
                        <button type="submit" class="btn-primary">
                            <span>Hoàn thành Quản lý Phim</span>
                        </button>
                    </div>
                </form>
            </div>
        </main>
    </div>

    <script>
        function toggleCardSelect(chk, cardId) {
            var card = document.getElementById(cardId);
            if (card) {
                if (chk.checked) {
                    card.style.borderColor = '#DDD3FE';
                    card.style.background = '#F5F2FF';
                } else {
                    card.style.borderColor = '#E8E8EE';
                    card.style.background = '#FFFFFF';
                }
            }
        }
    </script>
</body>
</html>
