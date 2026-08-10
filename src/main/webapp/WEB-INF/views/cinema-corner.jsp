<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="cbf" uri="https://cinebook.local/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${fn:escapeXml(displaySection)} - CineBook Góc Điện Ảnh</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css?v=1.0.2">
    <style>
        .layout-2cols {
            display: grid;
            grid-template-columns: 1.8fr 1fr;
            gap: 28px;
            margin-top: 24px;
            margin-bottom: 50px;
        }
        
        /* Filters */
        .corner-filters {
            display: flex;
            gap: 16px;
            margin-bottom: 24px;
            border-bottom: 2px solid var(--color-gray-100);
            padding-bottom: 12px;
        }
        .filter-select {
            padding: 8px 12px;
            border-radius: var(--radius-sm);
            border: 1px solid var(--color-gray-300);
            font-size: 13px;
            font-weight: 600;
            outline: none;
        }

        /* Items List */
        .items-list {
            display: flex;
            flex-direction: column;
            gap: 24px;
        }
        .item-row {
            display: flex;
            gap: 20px;
            padding: 20px;
            border: 1px solid var(--color-gray-200);
            border-radius: var(--radius-md);
            background: var(--surface);
            box-shadow: var(--shadow-card);
        }
        .item-thumbnail {
            width: 140px;
            height: 140px;
            border-radius: var(--radius-md);
            background-size: cover;
            background-position: center;
            background-color: var(--color-gray-50);
            flex-shrink: 0;
        }
        .item-details {
            display: flex;
            flex-direction: column;
            gap: 8px;
            flex: 1;
        }
        .item-title-row {
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .item-title {
            font-size: 18px;
            font-weight: 800;
            color: var(--color-secondary);
            margin: 0;
        }
        .item-prefix {
            color: var(--color-primary);
            font-weight: 700;
            font-size: 14px;
        }
        .item-meta {
            font-size: 12px;
            color: var(--color-gray-500);
            display: flex;
            align-items: center;
            gap: 16px;
        }
        .btn-like {
            background: #034EA2;
            color: white;
            border: none;
            padding: 4px 10px;
            border-radius: var(--radius-sm);
            font-size: 11px;
            font-weight: 700;
            cursor: pointer;
        }
        .btn-like:hover {
            background: #003366;
        }
        .item-desc {
            font-size: 13px;
            color: var(--ink);
            line-height: 1.5;
        }

        /* Sidebar Widgets */
        .sidebar-widget {
            border: 1px solid var(--color-gray-200);
            border-radius: var(--radius-md);
            background: var(--surface);
            box-shadow: var(--shadow-card);
            overflow: hidden;
            margin-bottom: 24px;
        }
        .widget-header-blue {
            background: var(--color-secondary);
            color: white;
            padding: 12px 20px;
            font-weight: 700;
            font-size: 15px;
        }
        .widget-body {
            padding: 20px;
        }
        .widget-select {
            width: 100%;
            padding: 10px;
            border-radius: var(--radius-sm);
            border: 1px solid var(--color-gray-300);
            margin-bottom: 12px;
            outline: none;
            font-weight: 600;
        }
        .widget-btn {
            width: 100%;
            background: var(--color-primary);
            color: white;
            border: none;
            padding: 12px;
            border-radius: var(--radius-sm);
            font-weight: 700;
            cursor: pointer;
        }

        .side-film-card {
            display: flex;
            gap: 12px;
            text-decoration: none;
            color: var(--ink);
            margin-bottom: 16px;
        }
        .side-film-card:last-child {
            margin-bottom: 0;
        }
        .side-film-img {
            width: 50px;
            height: 75px;
            border-radius: var(--radius-sm);
            background-size: cover;
            background-position: center;
            background-color: var(--color-gray-100);
        }
        .side-film-info {
            display: flex;
            flex-direction: column;
            gap: 4px;
            justify-content: center;
        }
        .side-film-title {
            font-size: 13px;
            font-weight: 700;
            margin: 0;
        }
    </style>
</head>
<body class="public-page">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="container">
        <!-- Breadcrumb -->
        <nav style="margin-top:20px; font-size:13px; color:var(--color-gray-500);">
            ${fn:escapeXml(breadcrumb)}
        </nav>

        <div class="layout-2cols">
            <!-- Cột trái: Nội dung chính -->
            <div>
                <h1 style="font-size: 24px; font-weight: 800; border-left: 4px solid var(--color-secondary); padding-left: 12px; margin-bottom: 20px;">
                    ${fn:escapeXml(displaySection)}
                </h1>

                <!-- Hàng filter dropdown -->
                <div class="corner-filters">
                    <select class="filter-select">
                        <option>Thể Loại: Tất cả</option>
                        <option>Hành động</option>
                        <option>Tình cảm</option>
                        <option>Hoạt hình</option>
                    </select>
                    <select class="filter-select">
                        <option>Quốc gia: Tất cả</option>
                        <option>Việt Nam</option>
                        <option>Mỹ</option>
                        <option>Nhật Bản</option>
                    </select>
                    <select class="filter-select">
                        <option>Sắp xếp: Xem nhiều nhất</option>
                        <option>Mới nhất</option>
                        <option>Yêu thích nhất</option>
                    </select>
                </div>

                <!-- Danh sách item dọc -->
                <div class="items-list">
                    <c:forEach var="item" items="${items}">
                        <div class="item-row">
                            <div class="item-thumbnail" style="background-image: url('${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, item.imageUrl))}')"></div>
                            <div class="item-details">
                                <div class="item-title-row">
                                    <c:if test="${not empty item.prefix}">
                                        <span class="item-prefix">${fn:escapeXml(item.prefix)}</span>
                                    </c:if>
                                    <h3 class="item-title">${fn:escapeXml(item.title)}</h3>
                                </div>
                                <div class="item-meta">
                                    <span style="font-weight:700; color:var(--color-gray-500);">${fn:escapeXml(item.subtitle)}</span>
                                    <span>👁️ ${fn:escapeXml(item.views)} lượt xem</span>
                                    <span>👍 <span id="likes-count-${fn:escapeXml(item.title.hashCode())}">${fn:escapeXml(item.likes)}</span> thích</span>
                                    <button type="button" class="btn-like" onclick="likeItem('${fn:escapeXml(item.title.hashCode())}')">👍 Thích</button>
                                </div>
                                <p class="item-desc">${fn:escapeXml(item.description)}</p>
                            </div>
                        </div>
                    </c:forEach>
                </div>
            </div>

            <!-- Cột phải: Sidebar -->
            <aside>
                <!-- Box Mua vé nhanh -->
                <div class="sidebar-widget">
                    <div class="widget-header-blue">MUA VÉ NHANH</div>
                    <form class="widget-body" method="get" action="${pageContext.request.contextPath}/booking">
                        <select class="widget-select" name="filmId" id="sideFilm" required>
                            <option value="">Chọn phim...</option>
                            <c:forEach var="f" items="${featuredFilms}">
                                <option value="${fn:escapeXml(f.id)}">${fn:escapeXml(f.title)}</option>
                            </c:forEach>
                        </select>
                        
                        <select class="widget-select" name="cinemaId" id="sideCinema" disabled required>
                            <option value="">Chọn rạp...</option>
                            <c:forEach var="c" items="${cinemas}">
                                <option value="${fn:escapeXml(c.id)}">${fn:escapeXml(c.name)}</option>
                            </c:forEach>
                        </select>

                        <select class="widget-select" name="showtimeId" id="sideShowtime" disabled required>
                            <option value="">Chọn suất...</option>
                        </select>

                        <button type="submit" class="widget-btn" id="sideSubmit" disabled>Đặt Vé Ngay</button>
                    </form>
                </div>

                <!-- Phim đang chiếu -->
                <div class="sidebar-widget">
                    <div class="widget-header-blue" style="background:var(--color-primary);">PHIM ĐANG CHIẾU</div>
                    <div class="widget-body" style="display:flex; flex-direction:column; gap:16px;">
                        <c:forEach var="f" items="${featuredFilms}" varStatus="status">
                            <c:if test="${status.index < 2}">
                                <c:set var="sideThumb" value="${cbf:assetUrl(pageContext.request.contextPath, empty f.thumbnail ? '/assets/img/default-film.jpg' : f.thumbnail)}" />
                                <a href="${pageContext.request.contextPath}/films/${fn:escapeXml(f.id)}" class="side-film-card">
                                    <div class="side-film-img" style="background-image: url('${fn:escapeXml(sideThumb)}');"></div>
                                    <div class="side-film-info">
                                        <h4 class="side-film-title">${fn:escapeXml(f.title)}</h4>
                                        <span style="font-size:11px; color:var(--color-gold);">⭐ ${fn:escapeXml(f.rating)}</span>
                                    </div>
                                </a>
                            </c:if>
                        </c:forEach>
                    </div>
                </div>
            </aside>
        </div>
    </main>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>

    <script id="sideShowtimesJson" type="application/json">
        [
            <c:forEach var="st" items="${showtimes}" varStatus="status">
                {
                    "id": ${st.id},
                    "filmId": ${st.filmId},
                    "cinemaId": ${st.cinemaId},
                    "cinemaName": "${st.cinemaName}",
                    "displayTime": "${st.startTimeDisplay}"
                }${not status.last ? ',' : ''}
            </c:forEach>
        ]
    </script>
    <script>
        function likeItem(hashCode) {
            const el = document.getElementById('likes-count-' + hashCode);
            if (el) {
                let current = Number(el.textContent);
                el.textContent = current + 1;
                alert('Cảm ơn bạn đã thích nội dung này!');
            }
        }

        // Quick buy widget logic
        const sideFilm = document.getElementById('sideFilm');
        const sideCinema = document.getElementById('sideCinema');
        const sideShowtime = document.getElementById('sideShowtime');
        const sideSubmit = document.getElementById('sideSubmit');

        const sideShowtimes = JSON.parse(document.getElementById('sideShowtimesJson').textContent || '[]');

        sideFilm.addEventListener('change', () => {
            const filmId = sideFilm.value;
            if (!filmId) {
                sideCinema.disabled = true;
                sideShowtime.disabled = true;
                sideSubmit.disabled = true;
                return;
            }
            const cinemaIds = [...new Set(sideShowtimes.filter(x => x.filmId == filmId).map(x => x.cinemaId))];
            Array.from(sideCinema.options).forEach(opt => {
                if (opt.value === "") return;
                opt.disabled = !cinemaIds.includes(Number(opt.value));
            });
            sideCinema.disabled = false;
            sideCinema.value = "";
            sideShowtime.disabled = true;
            sideSubmit.disabled = true;
        });

        sideCinema.addEventListener('change', () => {
            const filmId = sideFilm.value;
            const cinemaId = sideCinema.value;
            if (!cinemaId) {
                sideShowtime.disabled = true;
                sideSubmit.disabled = true;
                return;
            }
            const sts = sideShowtimes.filter(x => x.filmId == filmId && x.cinemaId == cinemaId);
            sideShowtime.innerHTML = '<option value="">Chọn suất...</option>';
            sts.forEach(st => {
                const opt = document.createElement('option');
                opt.value = st.id;
                opt.textContent = st.displayTime;
                sideShowtime.appendChild(opt);
            });
            sideShowtime.disabled = false;
            sideSubmit.disabled = true;
        });

        sideShowtime.addEventListener('change', () => {
            sideSubmit.disabled = !sideShowtime.value;
        });
    </script>
</body>
</html>
