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
    <title>Lịch Chiếu & Giá Vé - ${fn:escapeXml(selectedCinema != null ? selectedCinema.name : 'CineBook')}</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <style>
        /* Cinema Banner Carousel (Matching Image 4) */
        .cinema-banner-wrapper {
            position: relative;
            width: 100%;
            height: 380px;
            border-radius: 16px;
            overflow: hidden;
            margin-top: 16px;
            margin-bottom: 24px;
            box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18);
            background: #0f172a;
        }
        .cinema-banner-img {
            width: 100%;
            height: 100%;
            object-fit: cover;
            object-position: center;
        }
        .cinema-banner-overlay {
            position: absolute;
            inset: 0;
            background: linear-gradient(180deg, rgba(15, 23, 42, 0.2) 0%, rgba(15, 23, 42, 0.88) 100%);
            display: flex;
            align-items: flex-end;
            padding: 32px 40px;
            color: #ffffff;
        }
        .cinema-banner-title {
            font-size: 2rem;
            font-weight: 800;
            margin: 0 0 6px 0;
            color: #ffffff;
            text-shadow: 0 2px 8px rgba(0, 0, 0, 0.6);
        }
        .cinema-banner-sub {
            font-size: 0.95rem;
            color: rgba(255, 255, 255, 0.85);
            margin: 0;
        }

        /* Cinema Info Bar & Filters */
        .cinema-info-bar {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 12px;
            padding: 20px 28px;
            margin-bottom: 30px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 20px;
            flex-wrap: wrap;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
        }
        .cinema-details h2 {
            font-size: 1.2rem;
            font-weight: 800;
            color: #0f172a;
            margin: 0 0 4px 0;
        }
        .cinema-details p {
            font-size: 0.85rem;
            color: #64748b;
            margin: 0;
        }

        .cinema-filter-selects {
            display: flex;
            gap: 12px;
        }
        .cinema-select-box {
            padding: 10px 16px;
            border-radius: 6px;
            border: 1px solid #cbd5e1;
            background: #ffffff;
            font-size: 0.88rem;
            font-weight: 600;
            color: #0f172a;
            outline: none;
            cursor: pointer;
        }
        .cinema-select-box:focus {
            border-color: #034EA2;
        }

        /* Movies & Dates Section (Matching Image 2) */
        .st-section-header {
            font-size: 1.25rem;
            font-weight: 800;
            color: #034EA2;
            margin-bottom: 16px;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .st-section-header::before {
            content: '';
            display: inline-block;
            width: 4px;
            height: 20px;
            background: #034EA2;
            border-radius: 2px;
        }

        .st-dates-navbar {
            display: flex;
            gap: 10px;
            overflow-x: auto;
            padding-bottom: 4px;
            border-bottom: 2px solid #034EA2;
            margin-bottom: 24px;
        }
        .st-date-nav-item {
            padding: 12px 24px;
            border-radius: 8px 8px 0 0;
            background: #f8fafc;
            color: #475569;
            font-size: 0.88rem;
            font-weight: 700;
            cursor: pointer;
            text-align: center;
            transition: all 0.15s ease;
            white-space: nowrap;
        }
        .st-date-nav-item:hover {
            color: #034EA2;
            background: #eff6ff;
        }
        .st-date-nav-item.is-active {
            background: #034EA2;
            color: #ffffff;
        }

        /* Movie Poster Grid (Matching Image 2) */
        .st-movie-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
            gap: 20px;
            margin-bottom: 24px;
        }
        .st-movie-card {
            cursor: pointer;
            transition: transform 0.2s ease;
            position: relative;
        }
        .st-movie-card:hover {
            transform: translateY(-4px);
        }
        .st-poster-box {
            position: relative;
            width: 100%;
            height: 250px;
            border-radius: 10px;
            overflow: hidden;
            background-size: cover;
            background-position: center;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
            border: 3px solid transparent;
            transition: border-color 0.2s ease;
        }
        .st-movie-card.is-selected .st-poster-box {
            border-color: #f97316;
        }
        .st-check-badge {
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            width: 46px;
            height: 46px;
            border-radius: 50%;
            background: #f97316;
            color: #ffffff;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 24px;
            font-weight: bold;
            box-shadow: 0 4px 16px rgba(249, 115, 22, 0.6);
            opacity: 0;
            transition: opacity 0.2s ease;
        }
        .st-movie-card.is-selected .st-check-badge {
            opacity: 1;
        }
        .st-rating-tag {
            position: absolute;
            bottom: 8px;
            right: 8px;
            background: rgba(15, 23, 42, 0.88);
            color: #facc15;
            font-size: 0.78rem;
            font-weight: 800;
            padding: 3px 8px;
            border-radius: 4px;
            display: flex;
            align-items: center;
            gap: 3px;
        }
        .st-age-tag {
            position: absolute;
            bottom: 8px;
            left: 8px;
            background: #f97316;
            color: #ffffff;
            font-size: 0.75rem;
            font-weight: 800;
            padding: 3px 6px;
            border-radius: 4px;
        }
        .st-movie-title {
            font-size: 0.92rem;
            font-weight: 700;
            color: #0f172a;
            margin-top: 10px;
            text-align: center;
            line-height: 1.3;
        }

        /* Expandable Showtimes Panel (Matching Image 3) */
        .st-expand-panel {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 12px;
            padding: 24px;
            margin-top: 20px;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
            display: none;
        }
        .st-expand-panel.is-open {
            display: block;
            animation: fadeIn 0.25s ease;
        }
        .st-panel-title {
            font-size: 1.1rem;
            font-weight: 800;
            color: #0f172a;
            margin-bottom: 20px;
            padding-bottom: 10px;
            border-bottom: 1px solid #f1f5f9;
        }
        .st-room-row {
            display: flex;
            align-items: center;
            margin-bottom: 16px;
            gap: 20px;
            flex-wrap: wrap;
        }
        .st-room-info {
            min-width: 220px;
            font-size: 0.9rem;
            font-weight: 700;
            color: #475569;
        }
        .st-time-slots {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
        }
        .st-time-btn {
            padding: 9px 20px;
            border-radius: 6px;
            border: 1px solid #cbd5e1;
            background: #ffffff;
            color: #0f172a;
            font-size: 0.9rem;
            font-weight: 700;
            cursor: pointer;
            text-decoration: none;
            transition: all 0.15s ease;
            display: inline-block;
        }
        .st-time-btn:hover {
            background: #034EA2;
            border-color: #034EA2;
            color: #ffffff;
            transform: translateY(-2px);
        }
    </style>
</head>
<body class="public-page">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="container public-main">
        <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

        <!-- 1. CINEMA CAROUSEL BANNER (Image 4 Match) -->
        <div class="cinema-banner-wrapper">
            <c:choose>
                <c:when test="${not empty selectedCinema.bannerUrl}">
                    <img src="${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, selectedCinema.bannerUrl))}" class="cinema-banner-img" alt="${fn:escapeXml(selectedCinema.name)}">
                </c:when>
                <c:otherwise>
                    <img src="${pageContext.request.contextPath}/assets/img/cinemas/fpt-center-banner.jpg" class="cinema-banner-img" alt="${fn:escapeXml(selectedCinema.name)}">
                </c:otherwise>
            </c:choose>
            <div class="cinema-banner-overlay">
                <div>
                    <h1 class="cinema-banner-title">${fn:escapeXml(selectedCinema.name)}</h1>
                    <p class="cinema-banner-sub">Hệ thống rạp chiếu phim hiện đại chuẩn quốc tế - Màn hình IMAX & Âm thanh Dolby Atmos</p>
                </div>
            </div>
        </div>

        <!-- 2. CINEMA INFO BAR & SELECTORS -->
        <div class="cinema-info-bar">
            <div class="cinema-details">
                <h2>${fn:escapeXml(selectedCinema.name)}</h2>
                <p>📍 ${fn:escapeXml(selectedCinema.address)} &nbsp; | &nbsp; 📞 Hotline: ${fn:escapeXml(empty selectedCinema.phone ? '1900 1234' : selectedCinema.phone)}</p>
            </div>
            <div class="cinema-filter-selects">
                <select class="cinema-select-box" onchange="location.href='${pageContext.request.contextPath}/showtimes?cinemaId=' + this.value">
                    <c:forEach var="c" items="${cinemas}">
                        <option value="${fn:escapeXml(c.id)}" ${fn:escapeXml(c.id eq selectedCinema.id ? 'selected' : '')}>${fn:escapeXml(c.name)}</option>
                    </c:forEach>
                </select>
            </div>
        </div>

        <!-- 3. DATES NAVBAR & MOVIES CATALOG (Image 2 Match) -->
        <div class="st-section-header">PHIM</div>

        <!-- DATES TABS NAVBAR -->
        <div class="st-dates-navbar" id="stDatesNavbar">
            <!-- Dynamic Date Tabs rendered via JS -->
        </div>

        <!-- MOVIE POSTERS GRID (Image 2 Match) -->
        <div class="st-movie-grid" id="stMovieGrid">
            <!-- Dynamic Movie Posters rendered via JS -->
        </div>

        <!-- 4. EXPANDABLE SHOWTIMES PANEL (Image 3 Match) -->
        <div class="st-expand-panel" id="stExpandPanel">
            <div class="st-panel-title">Suất chiếu</div>
            <div id="stPanelContent">
                <!-- Room rows and time slot buttons rendered via JS -->
            </div>
        </div>

    </main>

    <!-- INTERACTIVE SCRIPT (Image 2 & 3 Logic) -->
    <script id="showtimeListJson" type="application/json">
        [
            <c:forEach var="st" items="${showtimes}" varStatus="status">
                {
                    "id": ${st.id},
                    "filmId": ${st.filmId},
                    "filmTitle": "${fn:escapeXml(st.filmTitle)}",
                    "cinemaName": "${fn:escapeXml(st.cinemaName)}",
                    "roomName": "${fn:escapeXml(st.roomName)}",
                    "roomStatus": "${st.roomStatus != null ? st.roomStatus : 'active'}",
                    "dateStr": "${st.dateDisplay}",
                    "dayOfWeekStr": "${st.dayOfWeekDisplay}",
                    "timeStr": "${st.timeDisplay}",
                    "formatVersion": "${fn:escapeXml(st.formatVersionDisplay)}",
                    "basePrice": ${st.basePrice != null ? st.basePrice : 100000},
                    "availableSeats": ${st.availableSeats},
                    "totalSeats": ${st.totalSeats},
                    "isSoldOut": ${st.soldOut}
                }${not status.last ? ',' : ''}
            </c:forEach>
        ]
    </script>
    <script id="filmListJson" type="application/json">
        [
            <c:forEach var="f" items="${films}" varStatus="status">
                {
                    "id": ${f.id},
                    "title": "${fn:escapeXml(f.title)}",
                    "thumbnail": "${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, f.thumbnail))}",
                    "rating": "${fn:escapeXml(f.rating)}",
                    "ageRating": "${fn:escapeXml(f.ageRating)}"
                }${not status.last ? ',' : ''}
            </c:forEach>
        ]
    </script>
    <script>
        var showtimeList = JSON.parse(document.getElementById('showtimeListJson').textContent || '[]');
        var filmList = JSON.parse(document.getElementById('filmListJson').textContent || '[]');
        var selectedDateStr = "";
        var selectedFilmId = null;

        document.addEventListener('DOMContentLoaded', function() {
            initDateTabs();
            renderMovieGrid();
        });

        function initDateTabs() {
            var tabsNav = document.getElementById('stDatesNavbar');
            tabsNav.innerHTML = '';

            var uniqueDates = [];
            showtimeList.forEach(function(st) {
                if (st.dateStr && uniqueDates.indexOf(st.dateStr) === -1) {
                    uniqueDates.push(st.dateStr);
                }
            });

            if (uniqueDates.length === 0) {
                var today = new Date();
                var day = String(today.getDate()).padStart(2, '0');
                var month = String(today.getMonth() + 1).padStart(2, '0');
                var year = today.getFullYear();
                uniqueDates.push(day + '/' + month + '/' + year);
            }

            selectedDateStr = uniqueDates[0];

            uniqueDates.forEach(function(dStr, idx) {
                var item = document.createElement('div');
                item.className = 'st-date-nav-item' + (idx === 0 ? ' is-active' : '');

                var matchingSt = showtimeList.find(function(s) { return s.dateStr === dStr; });
                var dayOfWeek = matchingSt ? matchingSt.dayOfWeekStr : 'Thứ';

                item.innerHTML = '<div>' + dayOfWeek + '</div><div style="font-size:0.95rem; font-weight:800; margin-top:2px;">' + dStr.substring(0, 5) + '</div>';
                item.onclick = function() {
                    document.querySelectorAll('.st-date-nav-item').forEach(function(t) { t.classList.remove('is-active'); });
                    item.classList.add('is-active');
                    selectedDateStr = dStr;
                    selectedFilmId = null; // Reset selection on date change
                    renderMovieGrid();
                    closeShowtimePanel();
                };
                tabsNav.appendChild(item);
            });
        }

        function renderMovieGrid() {
            var grid = document.getElementById('stMovieGrid');
            grid.innerHTML = '';

            // Find films that have showtimes on selectedDateStr
            var showingFilmIds = [];
            showtimeList.forEach(function(st) {
                if (st.dateStr === selectedDateStr && showingFilmIds.indexOf(st.filmId) === -1) {
                    showingFilmIds.push(st.filmId);
                }
            });

            var activeFilms = filmList.filter(function(f) {
                return showingFilmIds.length === 0 || showingFilmIds.indexOf(f.id) !== -1;
            });

            if (activeFilms.length === 0) {
                grid.innerHTML = '<div style="padding:40px; text-align:center; color:#94a3b8; font-weight:600; grid-column:1/-1;">Không có phim nào chiếu vào ngày đã chọn.</div>';
                return;
            }

            activeFilms.forEach(function(film, idx) {
                var card = document.createElement('div');
                card.className = 'st-movie-card' + (selectedFilmId === film.id ? ' is-selected' : '');
                
                var posterBox = document.createElement('div');
                posterBox.className = 'st-poster-box';
                posterBox.style.backgroundImage = "url('" + (film.thumbnail || '${pageContext.request.contextPath}/assets/img/films/fpt-test-poster.jpg') + "')";

                var checkBadge = document.createElement('div');
                checkBadge.className = 'st-check-badge';
                checkBadge.innerHTML = '✓';
                posterBox.appendChild(checkBadge);

                if (film.ageRating) {
                    var ageTag = document.createElement('div');
                    ageTag.className = 'st-age-tag';
                    ageTag.textContent = film.ageRating;
                    posterBox.appendChild(ageTag);
                }

                if (film.rating) {
                    var ratingTag = document.createElement('div');
                    ratingTag.className = 'st-rating-tag';
                    ratingTag.innerHTML = '★ ' + film.rating;
                    posterBox.appendChild(ratingTag);
                }

                card.appendChild(posterBox);

                var titleEl = document.createElement('div');
                titleEl.className = 'st-movie-title';
                titleEl.textContent = film.title;
                card.appendChild(titleEl);

                card.onclick = function() {
                    document.querySelectorAll('.st-movie-card').forEach(function(c) { c.classList.remove('is-selected'); });
                    card.classList.add('is-selected');
                    selectedFilmId = film.id;
                    openShowtimePanel(film);
                };

                grid.appendChild(card);
            });
        }

        function openShowtimePanel(film) {
            var panel = document.getElementById('stExpandPanel');
            var content = document.getElementById('stPanelContent');
            content.innerHTML = '';

            // Filter showtimes for this film and selectedDateStr
            var matchingShowtimes = showtimeList.filter(function(st) {
                return st.filmId === film.id && st.dateStr === selectedDateStr;
            });

            if (matchingShowtimes.length === 0) {
                content.innerHTML = '<div style="color:#94a3b8; padding: 20px;">Không có suất chiếu nào.</div>';
                panel.classList.add('is-open');
                return;
            }

            // Group showtimes by Room / Format
            var roomGroups = {};
            matchingShowtimes.forEach(function(st) {
                var key = st.roomName;
                if (!roomGroups[key]) roomGroups[key] = [];
                roomGroups[key].push(st);
            });

            Object.keys(roomGroups).forEach(function(rName) {
                var firstStInRoom = roomGroups[rName][0];

                var roomRow = document.createElement('div');
                roomRow.className = 'st-room-row';

                var roomInfo = document.createElement('div');
                roomInfo.className = 'st-room-info';
                roomInfo.style.display = 'flex';
                roomInfo.style.alignItems = 'center';
                roomInfo.style.justifyContent = 'space-between';
                roomInfo.style.flexWrap = 'wrap';

                var fmtVer = firstStInRoom && firstStInRoom.formatVersion ? firstStInRoom.formatVersion : '2D Phụ Đề';
                var roomTextSpan = document.createElement('span');
                roomTextSpan.textContent = rName + ' (' + fmtVer + ')';
                roomInfo.appendChild(roomTextSpan);

                // C.3: JdbcShowtimeDAO.findByFilmAndCinema loc `ISNULL(r.Status,'active') = 'active'`
                // ngay trong SQL, nen moi suat toi duoc trang cong khai deu thuoc phong dang hoat
                // dong. Nhanh "phong ngung hoat dong" o day khong bao gio chay — giu lai chi lam
                // nguoi doc tuong con mot luoi an toan o tang JS.
                var statusBadge = document.createElement('span');
                statusBadge.style.fontSize = '0.75rem';
                statusBadge.style.padding = '2px 8px';
                statusBadge.style.borderRadius = '4px';
                statusBadge.style.fontWeight = '700';
                statusBadge.style.background = '#dcfce7';
                statusBadge.style.color = '#15803d';
                statusBadge.style.border = '1px solid #86efac';
                statusBadge.textContent = 'Status: Đang Hoạt Động';
                roomInfo.appendChild(statusBadge);
                roomRow.appendChild(roomInfo);

                var timeSlots = document.createElement('div');
                timeSlots.className = 'st-time-slots';

                roomGroups[rName].forEach(function(st) {
                    var timeBtn = document.createElement('a');
                    if (st.isSoldOut) {
                        timeBtn.className = 'st-time-btn is-soldout-slot';
                        timeBtn.removeAttribute('href');
                        timeBtn.style.opacity = '0.75';
                        timeBtn.style.cursor = 'not-allowed';
                        timeBtn.style.background = '#fef2f2';
                        timeBtn.style.borderColor = '#fca5a5';
                        timeBtn.style.color = '#dc2626';
                        timeBtn.title = 'Suất chiếu lúc ' + st.timeStr + ' đã HẾT GHẾ!';
                        timeBtn.innerHTML = st.timeStr + '<div style="font-size:0.7rem; font-weight:800; color:#dc2626;">Hết ghế</div>';
                        timeBtn.onclick = function(e) {
                            e.preventDefault();
                            alert('Suất chiếu lúc ' + st.timeStr + ' đã HẾT GHẾ!');
                        };
                    } else {
                        timeBtn.className = 'st-time-btn';
                        timeBtn.href = '${pageContext.request.contextPath}/booking?showtimeId=' + st.id;
                        timeBtn.innerHTML = st.timeStr + (st.availableSeats > 0 ? '<div style="font-size:0.68rem; font-weight:600; color:#64748b;">Còn ' + st.availableSeats + '/' + st.totalSeats + '</div>' : '');
                        timeBtn.title = 'Giá từ ' + new Intl.NumberFormat('vi-VN').format(st.basePrice) + ' đ';
                    }
                    timeSlots.appendChild(timeBtn);
                });

                roomRow.appendChild(timeSlots);
                content.appendChild(roomRow);
            });

            panel.classList.add('is-open');
        }

        function closeShowtimePanel() {
            var panel = document.getElementById('stExpandPanel');
            panel.classList.remove('is-open');
        }
    </script>

    <%@ include file="/WEB-INF/views/shared/trailer-modal.jspf" %>
    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>
    <script src="${pageContext.request.contextPath}/assets/js/main.js"></script>
</body>
</html>
