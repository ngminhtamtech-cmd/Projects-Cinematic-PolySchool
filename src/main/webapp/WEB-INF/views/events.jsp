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
    <title>${fn:escapeXml(displaySection)} - CineBook Ưu Đãi & Sự Kiện</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css?v=1.0.2">
    <style>
        /* ==========================================================================
           MINIMALIST COMPACT PROMOTIONS & EVENTS PAGE
           ========================================================================== */
        .events-hero-compact {
            background: linear-gradient(135deg, #0F172A 0%, #1E293B 100%);
            border-radius: 20px;
            padding: 24px 30px;
            color: #FFFFFF;
            margin-top: 16px;
            margin-bottom: 20px;
            box-shadow: 0 10px 30px rgba(15, 23, 42, 0.15);
            display: flex;
            align-items: center;
            justify-content: space-between;
            flex-wrap: wrap;
            gap: 16px;
        }

        .events-hero-title {
            font-size: 24px;
            font-weight: 800;
            margin: 0;
            color: #FFFFFF;
            letter-spacing: -0.01em;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .events-hero-sub {
            font-size: 13.5px;
            color: #94A3B8;
            margin: 4px 0 0 0;
        }

        .events-layout {
            display: grid;
            grid-template-columns: 1fr 340px;
            gap: 24px;
            margin-bottom: 50px;
            align-items: start;
        }

        /* MINIMALIST SEGMENTED FILTER CONTROL */
        .promo-filter-box {
            background: #FFFFFF;
            border: 1px solid #E2E8F0;
            border-radius: 16px;
            padding: 5px;
            margin-bottom: 20px;
            box-shadow: 0 2px 10px rgba(15, 23, 42, 0.03);
        }

        .promo-filter-nav {
            display: flex;
            gap: 6px;
            background: #F8FAFC;
            border-radius: 12px;
            padding: 4px;
            border: 1px solid #F1F5F9;
            overflow-x: auto;
        }

        .promo-filter-btn {
            flex: 1;
            padding: 9px 14px;
            font-size: 13px;
            font-weight: 700;
            color: #64748B;
            background: transparent;
            border: 1px solid transparent;
            border-radius: 8px;
            cursor: pointer;
            transition: all 0.2s ease;
            white-space: nowrap;
            text-align: center;
        }

        .promo-filter-btn:hover {
            color: #0F172A;
            background: rgba(255, 255, 255, 0.7);
        }

        .promo-filter-btn.active {
            background: #FFFFFF;
            color: #EA580C;
            border-color: #E2E8F0;
            box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
            font-weight: 800;
        }

        /* MODERN OFFER CARDS GRID */
        .offer-cards-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 18px;
        }

        .offer-card-compact {
            background: #FFFFFF;
            border: 1px solid #E2E8F0;
            border-radius: 16px;
            overflow: hidden;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
            transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            cursor: pointer;
        }

        .offer-card-compact:hover {
            transform: translateY(-3px);
            border-color: #EA580C;
            box-shadow: 0 12px 28px rgba(234, 88, 12, 0.12);
        }

        .offer-banner-img {
            width: 100%;
            height: 150px;
            background-size: cover;
            background-position: center;
            background-color: #F1F5F9;
            position: relative;
        }

        .offer-badge-tag {
            position: absolute;
            top: 10px;
            left: 10px;
            background: rgba(15, 23, 42, 0.82);
            backdrop-filter: blur(8px);
            color: #FACC15;
            font-size: 11px;
            font-weight: 800;
            padding: 4px 10px;
            border-radius: 20px;
            text-transform: uppercase;
            border: 1px solid rgba(250, 204, 21, 0.3);
        }

        .offer-info-body {
            padding: 16px;
            display: flex;
            flex-direction: column;
            gap: 6px;
            flex: 1;
        }

        .offer-title {
            font-size: 15px;
            font-weight: 800;
            color: #0F172A;
            margin: 0;
            line-height: 1.35;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }

        .offer-desc {
            font-size: 12.5px;
            color: #64748B;
            margin: 0;
            line-height: 1.4;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }

        .offer-card-footer {
            padding: 12px 16px;
            background: #F8FAFC;
            border-top: 1px solid #F1F5F9;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        .offer-validity {
            font-size: 11.5px;
            font-weight: 600;
            color: #94A3B8;
            display: flex;
            align-items: center;
            gap: 4px;
        }

        .btn-use-offer {
            background: #EA580C;
            color: #FFFFFF;
            border: none;
            padding: 6px 14px;
            border-radius: 8px;
            font-size: 12px;
            font-weight: 700;
            cursor: pointer;
            transition: all 0.2s ease;
            text-decoration: none;
            display: inline-flex;
            align-items: center;
            gap: 4px;
        }

        .btn-use-offer:hover {
            background: #C2410C;
            box-shadow: 0 4px 12px rgba(234, 88, 12, 0.25);
        }

        /* SIDEBAR CLEAN WIDGETS */
        .sidebar-card-clean {
            background: #FFFFFF;
            border: 1px solid #E2E8F0;
            border-radius: 18px;
            overflow: hidden;
            box-shadow: 0 2px 10px rgba(15, 23, 42, 0.03);
            margin-bottom: 20px;
        }

        .sidebar-card-head {
            background: #0F172A;
            color: #FFFFFF;
            padding: 14px 18px;
            font-size: 14px;
            font-weight: 800;
            letter-spacing: 0.5px;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        .sidebar-card-body {
            padding: 18px;
        }

        .clean-select {
            width: 100%;
            padding: 10px 12px;
            border-radius: 10px;
            border: 1px solid #CBD5E1;
            background: #F8FAFC;
            font-size: 13px;
            font-weight: 600;
            color: #0F172A;
            margin-bottom: 10px;
            outline: none;
            transition: border 0.2s ease;
        }

        .clean-select:focus {
            border-color: #EA580C;
            background: #FFFFFF;
        }

        .clean-submit-btn {
            width: 100%;
            padding: 11px;
            border-radius: 10px;
            background: #EA580C;
            color: #FFFFFF;
            font-size: 13.5px;
            font-weight: 800;
            border: none;
            cursor: pointer;
            transition: all 0.2s ease;
            box-shadow: 0 4px 14px rgba(234, 88, 12, 0.2);
        }

        .clean-submit-btn:hover:not(:disabled) {
            background: #C2410C;
            box-shadow: 0 6px 18px rgba(234, 88, 12, 0.3);
        }

        .clean-submit-btn:disabled {
            background: #E2E8F0;
            color: #94A3B8;
            cursor: not-allowed;
            box-shadow: none;
        }

        .side-film-item {
            display: flex;
            gap: 12px;
            align-items: center;
            text-decoration: none;
            color: #0F172A;
            padding: 10px 0;
            border-bottom: 1px solid #F1F5F9;
            transition: all 0.2s ease;
        }

        .side-film-item:last-child {
            border-bottom: none;
            padding-bottom: 0;
        }

        .side-film-item:hover {
            transform: translateX(4px);
        }

        .side-film-thumb {
            width: 60px;
            height: 75px;
            border-radius: 8px;
            background-size: cover;
            background-position: center;
            flex-shrink: 0;
            background-color: #F1F5F9;
        }

        .side-film-meta {
            display: flex;
            flex-direction: column;
            gap: 2px;
        }

        .side-film-name {
            font-size: 13.5px;
            font-weight: 700;
            color: #0F172A;
            margin: 0;
            line-height: 1.3;
        }

        .side-film-date {
            font-size: 11.5px;
            color: #64748B;
        }

        @media (max-width: 900px) {
            .events-layout {
                grid-template-columns: 1fr;
            }
            .offer-cards-grid {
                grid-template-columns: 1fr;
            }
        }
    </style>
</head>
<body class="public-page">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="container">
        <!-- BREADCRUMB -->
        <nav style="margin-top: 16px; font-size: 13px; color: #64748B;">
            Trang chủ / Sự kiện / <span style="color: #EA580C; font-weight: 700;">${fn:escapeXml(displaySection)}</span>
        </nav>

        <!-- COMPACT HERO BANNER -->
        <div class="events-hero-compact">
            <div>
                <h1 class="events-hero-title">
                    🎁 ${fn:escapeXml(displaySection)}
                </h1>
                <p class="events-hero-sub">Khám phá vô vàn chương trình khuyến mãi, ưu đãi bắp nước và mã giảm giá HOT nhất tại CineBook!</p>
            </div>
            <a href="${pageContext.request.contextPath}/booking" class="btn-use-offer" style="padding: 10px 20px; font-size: 13.5px; font-weight: 800; border-radius: 12px;">
                🎟️ Đặt Vé Ngay
            </a>
        </div>

        <div class="events-layout">
            <!-- CỘT TRÁI: DANH SÁCH UƯ ĐÃI -->
            <div>
                <!-- BORDER BOX SEGMENTED FILTER NAV -->
                <div class="promo-filter-box">
                    <div class="promo-filter-nav">
                        <button id="filterBtn_all" class="promo-filter-btn active" onclick="filterOffers('all')">
                            🔥 Tất Cả Ưu Đãi (${fn:escapeXml(events.size())})
                        </button>

                        <button id="filterBtn_voucher" class="promo-filter-btn" onclick="filterOffers('voucher')">
                            💳 Mã Giảm Giá
                        </button>

                        <button id="filterBtn_bap-nuoc" class="promo-filter-btn" onclick="filterOffers('bap-nuoc')">
                            🍿 Ưu Đãi Bắp Nước
                        </button>

                        <button id="filterBtn_hot" class="promo-filter-btn" onclick="filterOffers('hot')">
                            ⭐ Phim & Sự Kiện
                        </button>
                    </div>
                </div>

                <!-- LƯỚI THẺ ƯU ĐÃI SANG TRỌNG -->
                <div class="offer-cards-grid">
                    <c:forEach var="e" items="${events}">
                        <c:set var="cardUrl" value="" />
                        <c:choose>
                            <c:when test="${not empty e.filmId and e.filmId > 0}">
                                <c:set var="cardUrl" value="${pageContext.request.contextPath}/booking?filmId=${e.filmId}" />
                            </c:when>
                            <c:when test="${not empty e.targetUrl}">
                                <c:choose>
                                    <c:when test="${fn:startsWith(e.targetUrl, 'http')}">
                                        <c:set var="cardUrl" value="${e.targetUrl}" />
                                    </c:when>
                                    <c:otherwise>
                                        <c:set var="cardUrl" value="${pageContext.request.contextPath}${fn:startsWith(e.targetUrl, '/') ? '' : '/'}${e.targetUrl}" />
                                    </c:otherwise>
                                </c:choose>
                            </c:when>
                            <c:otherwise>
                                <c:set var="cardUrl" value="${pageContext.request.contextPath}/booking" />
                            </c:otherwise>
                        </c:choose>

                        <c:set var="titleLower" value="${fn:toLowerCase(e.title)}" />
                        <c:set var="descLower" value="${fn:toLowerCase(e.description)}" />
                        <c:set var="catTag" value="hot" />
                        <c:choose>
                            <c:when test="${fn:contains(titleLower, 'bắp') or fn:contains(titleLower, 'nước') or fn:contains(descLower, 'bắp') or fn:contains(descLower, 'nước')}">
                                <c:set var="catTag" value="bap-nuoc" />
                                <c:set var="badgeText" value="🍿 Bắp Nước" />
                            </c:when>
                            <c:when test="${fn:contains(titleLower, 'mã') or fn:contains(titleLower, 'voucher') or fn:contains(titleLower, 'giảm') or fn:contains(descLower, 'voucher')}">
                                <c:set var="catTag" value="voucher" />
                                <c:set var="badgeText" value="💳 Voucher" />
                            </c:when>
                            <c:otherwise>
                                <c:set var="catTag" value="hot" />
                                <c:set var="badgeText" value="🔥 Ưu Đãi Hot" />
                            </c:otherwise>
                        </c:choose>

                        <c:set var="eImg" value="${empty e.imageUrl ? '/assets/img/hero-banner.png' : e.imageUrl}" />

                        <div class="offer-card-compact" data-category="${catTag}" onclick="window.location.href='${fn:escapeXml(cardUrl)}'" title="${fn:escapeXml(e.title)}">
                            <div>
                                <div class="offer-banner-img" style="background-image: url('${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, eImg))}')">
                                    <span class="offer-badge-tag">${badgeText}</span>
                                </div>
                                <div class="offer-info-body">
                                    <h3 class="offer-title">${fn:escapeXml(e.title)}</h3>
                                    <c:if test="${not empty e.description}">
                                        <p class="offer-desc">${fn:escapeXml(e.description)}</p>
                                    </c:if>
                                </div>
                            </div>
                            <div class="offer-card-footer">
                                <div class="offer-validity">
                                    <span>📅</span>
                                    <span>${empty e.endDate ? 'Đang diễn ra' : fn:escapeXml(e.endDate)}</span>
                                </div>
                                <span class="btn-use-offer">Áp dụng →</span>
                            </div>
                        </div>
                    </c:forEach>

                    <c:if test="${empty events}">
                        <div style="grid-column: 1/-1; text-align: center; color: #94A3B8; padding: 48px 24px; background: #FFFFFF; border-radius: 18px; border: 1px solid #E2E8F0;">
                            <div style="font-size: 36px; margin-bottom: 8px;">🎁</div>
                            <h3 style="margin: 0 0 4px 0; color: #0F172A;">Chưa có ưu đãi nào</h3>
                            <p style="margin: 0; font-size: 13px;">Hãy quay lại sau để cập nhật các chương trình khuyến mãi mới nhất từ CineBook!</p>
                        </div>
                    </c:if>
                </div>
            </div>

            <!-- CỘT PHẢI: SIDEBAR -->
            <aside>
                <!-- WIDGET 1: MUA VÉ NHANH -->
                <div class="sidebar-card-clean">
                    <div class="sidebar-card-head">
                        <span>⚡ MUA VÉ NHANH</span>
                        <span style="font-size: 11px; opacity: 0.8;">CineBook</span>
                    </div>
                    <form class="sidebar-card-body" method="get" action="${pageContext.request.contextPath}/booking">
                        <select class="clean-select" name="filmId" id="sideFilm" required>
                            <option value="">1. Chọn phim...</option>
                            <c:forEach var="f" items="${featuredFilms}">
                                <option value="${fn:escapeXml(f.id)}">${fn:escapeXml(f.title)}</option>
                            </c:forEach>
                        </select>
                        
                        <select class="clean-select" name="cinemaId" id="sideCinema" disabled required>
                            <option value="">2. Chọn rạp...</option>
                            <c:forEach var="c" items="${cinemas}">
                                <option value="${fn:escapeXml(c.id)}">${fn:escapeXml(c.name)}</option>
                            </c:forEach>
                        </select>

                        <select class="clean-select" name="showtimeId" id="sideShowtime" disabled required>
                            <option value="">3. Chọn suất chiếu...</option>
                        </select>

                        <button type="submit" class="clean-submit-btn" id="sideSubmit" disabled>Đặt Vé Ngay →</button>
                    </form>
                </div>

                <!-- WIDGET 2: PHIM ĐANG CHIẾU HOT -->
                <div class="sidebar-card-clean">
                    <div class="sidebar-card-head" style="background: #EA580C;">
                        <span>🎬 PHIM ĐANG CHIẾU</span>
                    </div>
                    <div class="sidebar-card-body" style="padding-top: 8px; padding-bottom: 8px;">
                        <c:forEach var="f" items="${featuredFilms}" varStatus="status">
                            <c:if test="${status.index < 3}">
                                <c:set var="sideBanner" value="${cbf:assetUrl(pageContext.request.contextPath, empty f.thumbnail ? (empty f.banner ? '/assets/img/hero-banner.png' : f.banner) : f.thumbnail)}" />
                                <a href="${pageContext.request.contextPath}/films/${fn:escapeXml(f.id)}" class="side-film-item">
                                    <div class="side-film-thumb" style="background-image: url('${fn:escapeXml(sideBanner)}');"></div>
                                    <div class="side-film-meta">
                                        <h4 class="side-film-name">${fn:escapeXml(f.title)}</h4>
                                        <span class="side-film-date">Khởi chiếu: ${fn:escapeXml(f.releaseDate)}</span>
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
        function filterOffers(category) {
            var btns = document.querySelectorAll('.promo-filter-btn');
            btns.forEach(function(b) { b.classList.remove('active'); });

            var targetBtn = document.getElementById('filterBtn_' + category);
            if (targetBtn) targetBtn.classList.add('active');

            var cards = document.querySelectorAll('.offer-card-compact');
            cards.forEach(function(card) {
                var cardCat = card.getAttribute('data-category') || 'hot';
                if (category === 'all' || cardCat === category) {
                    card.style.display = 'flex';
                } else {
                    card.style.display = 'none';
                }
            });
        }

        // Quick buy widget logic
        const sideFilm = document.getElementById('sideFilm');
        const sideCinema = document.getElementById('sideCinema');
        const sideShowtime = document.getElementById('sideShowtime');
        const sideSubmit = document.getElementById('sideSubmit');

        const sideShowtimes = JSON.parse(document.getElementById('sideShowtimesJson').textContent || '[]');

        if (sideFilm) {
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
        }

        if (sideCinema) {
            sideCinema.addEventListener('change', () => {
                const filmId = sideFilm.value;
                const cinemaId = sideCinema.value;
                if (!cinemaId) {
                    sideShowtime.disabled = true;
                    sideSubmit.disabled = true;
                    return;
                }
                const sts = sideShowtimes.filter(x => x.filmId == filmId && x.cinemaId == cinemaId);
                sideShowtime.innerHTML = '<option value="">3. Chọn suất chiếu...</option>';
                sts.forEach(st => {
                    const opt = document.createElement('option');
                    opt.value = st.id;
                    opt.textContent = st.displayTime;
                    sideShowtime.appendChild(opt);
                });
                sideShowtime.disabled = false;
                sideSubmit.disabled = true;
            });
        }

        if (sideShowtime) {
            sideShowtime.addEventListener('change', () => {
                sideSubmit.disabled = !sideShowtime.value;
            });
        }
    </script>
</body>
</html>
