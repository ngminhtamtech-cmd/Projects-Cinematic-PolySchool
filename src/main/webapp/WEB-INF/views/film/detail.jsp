<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="cbf" uri="https://cinebook.local/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${fn:escapeXml(film.title)} - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <style>
        /* Showtime 2-Column Selection System (Matching Image 2 & 3) */
        .st-selection-shell {
            display: grid;
            grid-template-columns: 1fr 340px;
            gap: 24px;
            margin-top: 20px;
            align-items: start;
        }
        @media (max-width: 980px) {
            .st-selection-shell {
                grid-template-columns: 1fr;
            }
        }

        .st-left-panel {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 12px;
            padding: 24px;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
        }

        .st-header-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
            padding-bottom: 12px;
            border-bottom: 1px solid #f1f5f9;
        }
        .st-header-title {
            font-size: 1.2rem;
            font-weight: 800;
            color: #0f172a;
            margin: 0;
        }

        .st-date-bar {
            display: flex;
            align-items: center;
            gap: 12px;
            margin-bottom: 24px;
            flex-wrap: wrap;
        }
        .st-date-tabs {
            display: flex;
            gap: 10px;
            overflow-x: auto;
            flex: 1;
        }
        .st-date-tab {
            padding: 10px 20px;
            border-radius: 8px;
            border: 1px solid #e2e8f0;
            background: #ffffff;
            color: #475569;
            font-size: 0.85rem;
            font-weight: 700;
            cursor: pointer;
            text-align: center;
            transition: all 0.15s ease;
            white-space: nowrap;
        }
        .st-date-tab:hover {
            border-color: #034EA2;
            color: #034EA2;
        }
        .st-date-tab.is-active {
            background: #034EA2;
            border-color: #034EA2;
            color: #ffffff;
        }

        .st-cinema-select {
            padding: 8px 14px;
            border-radius: 6px;
            border: 1px solid #cbd5e1;
            font-size: 0.85rem;
            font-weight: 600;
            color: #0f172a;
            background: #ffffff;
            outline: none;
        }

        .st-cinema-group {
            margin-bottom: 24px;
            padding-bottom: 20px;
            border-bottom: 1px solid #f1f5f9;
        }
        .st-cinema-group:last-child {
            border-bottom: none;
            margin-bottom: 0;
        }
        .st-cinema-name {
            font-size: 1.05rem;
            font-weight: 800;
            color: #0f172a;
            margin-bottom: 4px;
        }
        .st-format-label {
            font-size: 0.82rem;
            color: #64748b;
            margin-bottom: 12px;
        }

        .st-slots-grid {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
        }
        .st-slot-btn {
            padding: 9px 22px;
            border-radius: 6px;
            border: 1px solid #cbd5e1;
            background: #ffffff;
            color: #0f172a;
            font-size: 0.9rem;
            font-weight: 700;
            cursor: pointer;
            transition: all 0.15s ease;
        }
        .st-slot-btn:hover {
            border-color: #034EA2;
            color: #034EA2;
            background: #f0f7ff;
        }
        .st-slot-btn.is-selected {
            background: #034EA2;
            border-color: #034EA2;
            color: #ffffff;
        }

        /* Right Summary Banner Sidebar (Matching Image 3) */
        .st-summary-card {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 12px;
            overflow: hidden;
            position: sticky;
            top: 20px;
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.05);
            border-top: 4px solid #f97316;
        }
        .st-summary-body {
            padding: 20px;
        }
        .st-film-mini {
            display: flex;
            gap: 14px;
            margin-bottom: 18px;
        }
        .st-film-thumb {
            width: 68px;
            height: 98px;
            border-radius: 6px;
            background-size: cover;
            background-position: center;
            flex-shrink: 0;
            border: 1px solid #e2e8f0;
        }
        .st-film-title {
            font-size: 1rem;
            font-weight: 800;
            color: #0f172a;
            margin: 0 0 6px 0;
            line-height: 1.3;
        }
        .st-film-tags {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 0.8rem;
            color: #64748b;
        }
        .st-age-badge {
            background: #f97316;
            color: #ffffff;
            font-weight: 800;
            font-size: 0.72rem;
            padding: 2px 6px;
            border-radius: 4px;
        }

        .st-detail-info {
            margin-bottom: 16px;
            font-size: 0.85rem;
        }
        .st-info-row {
            color: #334155;
            margin-bottom: 6px;
            line-height: 1.4;
        }
        .st-info-row strong {
            color: #0f172a;
        }
        .st-divider {
            border-bottom: 1px dashed #cbd5e1;
            margin: 16px 0;
        }
        .st-total-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 0.95rem;
            color: #0f172a;
            font-weight: 700;
            margin-bottom: 20px;
        }
        .st-total-price {
            font-size: 1.25rem;
            font-weight: 800;
            color: #f97316;
        }

        .st-summary-actions {
            display: flex;
            gap: 12px;
        }
        .st-btn-back {
            flex: 1;
            padding: 12px;
            border-radius: 8px;
            border: 1px solid #cbd5e1;
            background: #ffffff;
            color: #f97316;
            font-weight: 700;
            font-size: 0.9rem;
            cursor: pointer;
            text-align: center;
            text-decoration: none;
            transition: all 0.15s ease;
        }
        .st-btn-back:hover {
            background: #fff7ed;
            border-color: #f97316;
        }
        .st-btn-continue {
            flex: 1.4;
            padding: 12px;
            border-radius: 8px;
            border: none;
            background: #f97316;
            color: #ffffff;
            font-weight: 800;
            font-size: 0.95rem;
            cursor: pointer;
            text-align: center;
            text-decoration: none;
            box-shadow: 0 4px 12px rgba(249, 115, 22, 0.3);
            transition: all 0.15s ease;
            display: inline-flex;
            align-items: center;
            justify-content: center;
        }
        .st-btn-continue:hover {
            background: #ea580c;
            box-shadow: 0 6px 16px rgba(249, 115, 22, 0.4);
        }
        .st-btn-continue.is-disabled {
            background: #cbd5e1;
            color: #94a3b8;
            cursor: not-allowed;
            box-shadow: none;
            pointer-events: none;
        }
    </style>
</head>
<body class="public-page">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="container public-main">
        <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

        <!-- HERO MOVIE DETAIL PANEL -->
        <section class="detail-shell">
            <div class="detail-media">
                <c:choose>
                    <c:when test="${not empty film.thumbnail}">
                        <div class="detail-poster" style="background-image:url('${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, film.thumbnail))}');background-size:cover;background-position:center;"></div>
                    </c:when>
                    <c:otherwise>
                        <div class="detail-poster poster">
                            <div class="poster-copy">
                                <span class="poster-kicker">${fn:escapeXml(film.ageRating)}</span>
                                <strong>${fn:escapeXml(film.title)}</strong>
                            </div>
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
            <div class="detail-content">
                <%-- EX-01: badge tren banner trang chi tiet, cung rule voi poster o danh sach. --%>
                <cb:filmExpiring film="${film}"/>
                <h1>${fn:escapeXml(film.title)}</h1>
                <p class="lead-copy">${fn:escapeXml(film.description)}</p>
                <div class="meta-line">
                    <span class="badge dark">${fn:escapeXml(film.durationMinutes)} phút</span>
                    <span class="badge dark">${fn:escapeXml(film.ageRating)}</span>
                    <span class="badge dark">Rating ${fn:escapeXml(film.rating)}</span>
                    <span class="badge dark">${fn:escapeXml(film.language)}</span>
                    <span class="badge dark">${fn:escapeXml(film.subtitles)}</span>
                </div>
                <div class="detail-facts">
                    <p><strong>Đạo diễn</strong><span>${fn:escapeXml(film.directors)}</span></p>
                    <p><strong>Diễn viên</strong><span>${fn:escapeXml(film.actors)}</span></p>
                    <p><strong>Khởi chiếu</strong><span>${fn:escapeXml(film.releaseDate)}</span></p>
                    <c:if test="${not empty film.endDate}">
                        <p><strong>Chiếu đến hết</strong><span>${fn:escapeXml(film.endDate)}</span></p>
                    </c:if>
                </div>
                <div class="inline-links">
                    <a class="button" href="#showtimeSelectionArea">Đặt vé ngay</a>
                    <c:if test="${not empty film.trailerUrl}">
                        <button type="button" class="button secondary btn-watch-trailer" data-trailer-url="${fn:escapeXml(film.trailerUrl)}" data-movie-title="${fn:escapeXml(film.title)}">Xem trailer</button>
                    </c:if>
                </div>
            </div>
        </section>

        <!-- SHOWTIME SELECTION & SUMMARY BANNER SECTION (Style Image 2 & 3) -->
        <section class="section" id="showtimeSelectionArea">
            <div class="st-selection-shell">
                
                <!-- LEFT COLUMN: SHOWTIME SELECTOR (Image 2 Match) -->
                <div class="st-left-panel">
                    <div class="st-header-row">
                        <h2 class="st-header-title">Chọn suất</h2>
                    </div>

                    <!-- DATE FILTER TABS & CINEMA SELECTOR -->
                    <div class="st-date-bar">
                        <div class="st-date-tabs" id="stDateTabs">
                            <!-- Dynamic Date Tabs generated via JS -->
                        </div>
                        <select id="stCinemaFilter" class="st-cinema-select" onchange="filterShowtimes()">
                            <option value="ALL">Tất cả các rạp</option>
                        </select>
                    </div>

                    <!-- SHOWTIMES CONTAINER -->
                    <div id="stShowtimeContainer">
                        <!-- Dynamic Cinema Groups & Time Slots rendered via JS -->
                    </div>
                </div>

                <!-- RIGHT COLUMN: TICKET SUMMARY BANNER (Image 3 Match) -->
                <div class="st-summary-card">
                    <div class="st-summary-body">
                        <!-- FILM MINI HEADER -->
                        <div class="st-film-mini">
                            <div class="st-film-thumb" style="background-image:url('${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, film.thumbnail))}');"></div>
                            <div>
                                <h3 class="st-film-title">${fn:escapeXml(film.title)}</h3>
                                <div class="st-film-tags">
                                    <span id="summaryFormatVersion">2D Phụ Đề</span>
                                    <span class="st-age-badge">${fn:escapeXml(film.ageRating)}</span>
                                </div>
                            </div>
                        </div>

                        <!-- SELECTED SHOWTIME DETAILS -->
                        <div class="st-detail-info">
                            <div class="st-info-row">
                                <strong id="summaryCinemaName">Chưa chọn suất chiếu</strong>
                            </div>
                            <div class="st-info-row" id="summaryShowtimeTime" style="color: #64748b;">
                                Vui lòng bấm chọn một suất chiếu bên trái
                            </div>
                        </div>

                        <div class="st-divider"></div>

                        <!-- TOTAL PRICE DISPLAY -->
                        <div class="st-total-row">
                            <span>Tổng cộng</span>
                            <span class="st-total-price" id="summaryTotalPrice">0 đ</span>
                        </div>

                        <!-- ACTION BUTTONS -->
                        <div class="st-summary-actions">
                            <a href="${pageContext.request.contextPath}/films" class="st-btn-back">Quay lại</a>
                            <a href="#" id="summaryBtnContinue" class="st-btn-continue is-disabled">Tiếp tục</a>
                        </div>
                    </div>
                </div>

            </div>
        </section>

        <!-- COMMUNITY REVIEWS SECTION -->
        <section class="section">
            <div class="section-row">
                <div>
                    <span class="eyebrow dark">Community review</span>
                    <h2 class="section-title">Đánh giá và bình luận</h2>
                </div>
            </div>

            <div class="portal-grid">
                <div>
                    <c:choose>
                        <c:when test="${not empty sessionScope.currentUser}">
                            <article class="panel">
                                <form method="post" action="${pageContext.request.contextPath}/films/${fn:escapeXml(film.id)}/comments" class="form-grid">
                                    <cb:csrf/>
                                    <label>Đánh giá
                                        <select name="rate" required>
                                            <option value="">Chọn số sao</option>
                                            <option value="5">5 sao</option>
                                            <option value="4">4 sao</option>
                                            <option value="3">3 sao</option>
                                            <option value="2">2 sao</option>
                                            <option value="1">1 sao</option>
                                        </select>
                                    </label>
                                    <label>Nội dung
                                        <textarea name="content" rows="5" placeholder="Cảm nhận của bạn về bộ phim này" required></textarea>
                                    </label>
                                    <div class="form-actions">
                                        <button type="submit">Gửi bình luận</button>
                                    </div>
                                </form>
                            </article>
                        </c:when>
                        <c:otherwise>
                            <div class="notice">
                                Vui lòng <a href="${pageContext.request.contextPath}/login"><strong>đăng nhập</strong></a> để đánh giá phim.
                            </div>
                        </c:otherwise>
                    </c:choose>
                </div>

                <div class="history-list">
                    <c:forEach var="comment" items="${comments}">
                        <article class="panel comment-card reveal">
                            <div class="showtime-row">
                                <div>
                                    <strong>${fn:escapeXml(comment.userFullName)}</strong>
                                    <p class="muted">${fn:escapeXml(comment.createdAtDisplay)}</p>
                                </div>
                                <div style="display: flex; align-items: center; gap: 8px;">
                                    <span class="badge">${fn:escapeXml(comment.rate)}/5</span>
                                    <button type="button" data-comment-id="${fn:escapeXml(comment.id)}" onclick="openReportModal(this.getAttribute('data-comment-id'))" style="background: transparent; border: 1px solid #fee2e2; border-radius: 6px; padding: 3px 8px; cursor: pointer; color: #ef4444; font-size: 0.8rem; font-weight: 600;" title="Báo cáo bình luận vi phạm">
                                        Báo cáo
                                    </button>
                                </div>
                            </div>
                            <p>${fn:escapeXml(comment.content)}</p>
                        </article>
                    </c:forEach>
                    <c:if test="${empty comments}">
                        <article class="panel">
                            <h3>Chưa có bình luận</h3>
                            <p class="muted">Hãy là người đầu tiên để lại nhận xét cho phim này.</p>
                        </article>
                    </c:if>
                </div>
            </div>
        </section>

        <!-- REPORT COMMENT MODAL -->
        <dialog id="reportModal" style="border: 1px solid #e2e8f0; border-radius: 14px; padding: 24px; max-width: 440px; width: 90%; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1);">
            <form method="post" action="${pageContext.request.contextPath}/films/${fn:escapeXml(film.id)}">
                <cb:csrf/>
                <input type="hidden" name="action" value="reportComment">
                <input type="hidden" name="commentId" id="reportCommentId" value="">
                <h3 style="margin-top: 0; color: #0f172a; display: flex; align-items: center; gap: 8px;">Báo cáo bình luận vi phạm</h3>
                <p class="muted" style="font-size: 0.9rem; margin-bottom: 16px;">Vui lòng nhập lý do bạn báo cáo bình luận này tới Quản trị viên:</p>
                <div style="margin-bottom: 16px;">
                    <textarea name="reason" rows="3" style="width: 100%; padding: 10px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 0.9rem; box-sizing: border-box;" placeholder="VD: Ngôn từ xúc phạm, nội dung rác (spam)..." required></textarea>
                </div>
                <div style="display: flex; justify-content: flex-end; gap: 8px;">
                    <button type="button" onclick="document.getElementById('reportModal').close()" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #cbd5e1; background: #fff; cursor: pointer;">Hủy</button>
                    <button type="submit" style="padding: 8px 16px; border-radius: 6px; border: none; background: #ef4444; color: #fff; font-weight: 700; cursor: pointer;">Gửi báo cáo</button>
                </div>
            </form>
        </dialog>
        <script>
            function openReportModal(commentId) {
                document.getElementById('reportCommentId').value = commentId;
                document.getElementById('reportModal').showModal();
            }
        </script>
    </main>

    <!-- DYNAMIC SHOWTIME SELECTION SCRIPT (Image 2 & Image 3 Logic) -->
    <script id="showtimeDataJson" type="application/json">
        [
            <c:forEach var="st" items="${showtimes}" varStatus="status">
                {
                    "id": ${st.id},
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
                    "isSoldOut": ${st.soldOut},
                    "startTimeIso": "${st.startTime != null ? st.startTime.toString() : ''}"
                }${not status.last ? ',' : ''}
            </c:forEach>
        ]
    </script>
    <script>
        var showtimeData = JSON.parse(document.getElementById('showtimeDataJson').textContent || '[]');
        var selectedDateStr = "";
        var selectedShowtimeId = null;

        document.addEventListener('DOMContentLoaded', function() {
            initDateTabs();
            initCinemaFilter();
            renderShowtimeGrid();
        });

        function initDateTabs() {
            var tabsContainer = document.getElementById('stDateTabs');
            tabsContainer.innerHTML = '';

            // Collect unique dates from showtimeData
            var uniqueDates = [];
            showtimeData.forEach(function(st) {
                if (st.dateStr && uniqueDates.indexOf(st.dateStr) === -1) {
                    uniqueDates.push(st.dateStr);
                }
            });

            if (uniqueDates.length === 0) {
                // Default date fallback if no showtimes
                var today = new Date();
                var day = String(today.getDate()).padStart(2, '0');
                var month = String(today.getMonth() + 1).padStart(2, '0');
                var year = today.getFullYear();
                uniqueDates.push(day + '/' + month + '/' + year);
            }

            selectedDateStr = uniqueDates[0];

            uniqueDates.forEach(function(dStr, idx) {
                var btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'st-date-tab' + (idx === 0 ? ' is-active' : '');
                
                // Get Day of week text
                var matchingSt = showtimeData.find(function(s) { return s.dateStr === dStr; });
                var dayOfWeek = matchingSt ? matchingSt.dayOfWeekStr : 'Thứ';
                
                btn.innerHTML = '<div>' + dayOfWeek + '</div><div style="font-size:0.95rem; font-weight:800;">' + dStr.substring(0, 5) + '</div>';
                btn.onclick = function() {
                    document.querySelectorAll('.st-date-tab').forEach(function(t) { t.classList.remove('is-active'); });
                    btn.classList.add('is-active');
                    selectedDateStr = dStr;
                    renderShowtimeGrid();
                };
                tabsContainer.appendChild(btn);
            });
        }

        function initCinemaFilter() {
            var select = document.getElementById('stCinemaFilter');
            var uniqueCinemas = [];
            showtimeData.forEach(function(st) {
                if (st.cinemaName && uniqueCinemas.indexOf(st.cinemaName) === -1) {
                    uniqueCinemas.push(st.cinemaName);
                }
            });
            uniqueCinemas.forEach(function(cName) {
                var opt = document.createElement('option');
                opt.value = cName;
                opt.textContent = cName;
                select.appendChild(opt);
            });
        }

        function filterShowtimes() {
            renderShowtimeGrid();
        }

        function renderShowtimeGrid() {
            var container = document.getElementById('stShowtimeContainer');
            container.innerHTML = '';

            var cinemaFilter = document.getElementById('stCinemaFilter').value;

            var nowMs = Date.now();
            // Filter showtimes by selected date, cinema, and ensure showtime has not passed
            var filtered = showtimeData.filter(function(st) {
                var matchDate = !selectedDateStr || st.dateStr === selectedDateStr;
                var matchCinema = cinemaFilter === 'ALL' || st.cinemaName === cinemaFilter;
                var isFuture = !st.startTimeIso || new Date(st.startTimeIso).getTime() > nowMs;
                return matchDate && matchCinema && isFuture;
            });

            if (filtered.length === 0) {
                container.innerHTML = '<div style="padding:40px; text-align:center; color:#94a3b8; font-weight:600;">Không có suất chiếu nào phù hợp với bộ lọc.</div>';
                return;
            }

            // Group filtered showtimes by Cinema + Room
            var groups = {};
            filtered.forEach(function(st) {
                var key = st.cinemaName + ' - ' + st.roomName;
                if (!groups[key]) groups[key] = [];
                groups[key].push(st);
            });

            var autoSelectCandidate = null;

            Object.keys(groups).forEach(function(key) {
                var showtimesInGroup = groups[key];
                var firstSt = showtimesInGroup[0];

                var groupDiv = document.createElement('div');
                groupDiv.className = 'st-cinema-group';

                var nameEl = document.createElement('div');
                nameEl.className = 'st-cinema-name';
                nameEl.style.display = 'flex';
                nameEl.style.alignItems = 'center';
                nameEl.style.justifyContent = 'space-between';
                nameEl.style.flexWrap = 'wrap';
                nameEl.style.gap = '8px';

                var titleSpan = document.createElement('span');
                titleSpan.textContent = key;
                nameEl.appendChild(titleSpan);

                // C.3: JdbcShowtimeDAO.findByFilmAndCinema loc `ISNULL(r.Status,'active') = 'active'`
                // ngay trong SQL, nen moi suat toi duoc trang cong khai deu thuoc phong dang hoat
                // dong. Nhanh "phong ngung hoat dong" khong bao gio chay.
                var statusBadge = document.createElement('span');
                statusBadge.style.fontSize = '0.78rem';
                statusBadge.style.padding = '2px 8px';
                statusBadge.style.borderRadius = '4px';
                statusBadge.style.fontWeight = '700';
                statusBadge.style.background = '#dcfce7';
                statusBadge.style.color = '#15803d';
                statusBadge.style.border = '1px solid #86efac';
                statusBadge.textContent = 'Status: Đang Hoạt Động';
                nameEl.appendChild(statusBadge);
                groupDiv.appendChild(nameEl);

                var formatEl = document.createElement('div');
                formatEl.className = 'st-format-label';
                formatEl.textContent = firstSt.formatVersion || '2D Phụ Đề';
                groupDiv.appendChild(formatEl);

                var slotsGrid = document.createElement('div');
                slotsGrid.className = 'st-slots-grid';

                showtimesInGroup.forEach(function(st) {
                    var btn = document.createElement('button');
                    btn.type = 'button';
                    if (st.isSoldOut) {
                        btn.className = 'st-slot-btn is-soldout-slot';
                        btn.disabled = true;
                        btn.style.opacity = '0.75';
                        btn.style.cursor = 'not-allowed';
                        btn.style.background = '#fef2f2';
                        btn.style.borderColor = '#fca5a5';
                        btn.style.color = '#dc2626';
                        btn.title = 'Suất chiếu lúc ' + st.timeStr + ' đã HẾT GHẾ!';
                        btn.innerHTML = st.timeStr + '<div style="font-size:0.7rem; font-weight:800; color:#dc2626;">Hết ghế</div>';
                    } else {
                        btn.className = 'st-slot-btn' + (selectedShowtimeId === st.id ? ' is-selected' : '');
                        btn.setAttribute('data-showtime-id', st.id);
                        btn.innerHTML = st.timeStr + (st.availableSeats > 0 ? '<div style="font-size:0.68rem; font-weight:600; color:#64748b;">Còn ' + st.availableSeats + '/' + st.totalSeats + '</div>' : '');
                        btn.onclick = function() {
                            toggleShowtime(st);
                        };
                        if (!autoSelectCandidate) autoSelectCandidate = st;
                    }
                    slotsGrid.appendChild(btn);
                });

                groupDiv.appendChild(slotsGrid);
                container.appendChild(groupDiv);

                // Add horizontal divider line between separate room sections
                if (idx < groupKeys.length - 1) {
                    var divider = document.createElement('hr');
                    divider.className = 'st-group-divider';
                    divider.style.border = 'none';
                    divider.style.borderTop = '1px solid #e2e8f0';
                    divider.style.margin = '20px 0';
                    container.appendChild(divider);
                }
            });

            // Auto-select candidate if none selected or selected not in view
            if (!selectedShowtimeId && autoSelectCandidate) {
                selectShowtime(autoSelectCandidate);
            }
        }

        function toggleShowtime(st) {
            if (selectedShowtimeId === st.id) {
                deselectShowtime();
            } else {
                selectShowtime(st);
            }
        }

        function deselectShowtime() {
            selectedShowtimeId = null;
            document.querySelectorAll('.st-slot-btn').forEach(function(btn) {
                btn.classList.remove('is-selected');
            });
            document.getElementById('summaryCinemaName').textContent = 'Chưa chọn rạp & phòng';
            document.getElementById('summaryShowtimeTime').innerHTML = 'Suất: --:--';
            document.getElementById('summaryTotalPrice').textContent = '0 đ';
            var btnContinue = document.getElementById('summaryBtnContinue');
            btnContinue.href = 'javascript:void(0)';
            btnContinue.classList.add('is-disabled');
        }

        function selectShowtime(st) {
            selectedShowtimeId = st.id;

            // Highlight active time button by exact showtime ID
            document.querySelectorAll('.st-slot-btn').forEach(function(btn) {
                var btnStId = btn.getAttribute('data-showtime-id');
                if (btnStId && parseInt(btnStId, 10) === st.id) {
                    btn.classList.add('is-selected');
                } else {
                    btn.classList.remove('is-selected');
                }
            });

            // Update Right Summary Banner (Image 3)
            document.getElementById('summaryCinemaName').textContent = st.cinemaName + ' - ' + st.roomName;
            document.getElementById('summaryShowtimeTime').innerHTML = 'Suất: <strong>' + st.timeStr + '</strong> - ' + st.dayOfWeekStr + ', ' + st.dateStr;
            if (document.getElementById('summaryFormatVersion')) {
                document.getElementById('summaryFormatVersion').textContent = st.formatVersion || '2D Phụ Đề';
            }
            
            var formattedPrice = new Intl.NumberFormat('vi-VN').format(st.basePrice) + ' đ';
            document.getElementById('summaryTotalPrice').textContent = formattedPrice;

            var btnContinue = document.getElementById('summaryBtnContinue');
            btnContinue.href = '${pageContext.request.contextPath}/booking?showtimeId=' + st.id;
            btnContinue.classList.remove('is-disabled');
        }
    </script>

    <%@ include file="/WEB-INF/views/shared/trailer-modal.jspf" %>
    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>
    <script src="${pageContext.request.contextPath}/assets/js/main.js"></script>
</body>
</html>
