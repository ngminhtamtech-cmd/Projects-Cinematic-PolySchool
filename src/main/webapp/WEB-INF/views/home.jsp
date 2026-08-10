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
    <title>Trang chủ - CineBook Đặt vé xem phim chiếu rạp</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css?v=1.0.6">
    <style>
        /* ==========================================================================
           1. MAIN HOMEPAGE SHELL & 3-PART HERO BANNER (ĐÚNG PHÁC THẢO GIAO DIỆN)
           ========================================================================== */
        .home-main-shell {
            width: 100%;
            max-width: 1200px;
            margin: 0 auto;
            padding: 12px 16px 28px;
            box-sizing: border-box;
        }

        .hero-banner-section {
            display: flex;
            gap: 12px;
            width: 100%;
            height: 264px;
            align-items: stretch;
        }

        /* 1.1 BANNER PHỤ TRÁI (Preview Phim Trước) */
        .banner-side-prev {
            flex: 0 0 76px;
            border-radius: 14px;
            overflow: hidden;
            position: relative;
            cursor: pointer;
            background: #0c1a30;
            opacity: 0.85;
            transition: all 0.3s ease;
            box-shadow: 0 8px 24px rgba(12, 26, 48, 0.12);
        }

        .banner-side-prev:hover {
            opacity: 1;
            transform: scale(1.02);
            box-shadow: 0 12px 28px rgba(12, 26, 48, 0.25);
        }

        .side-poster-bg {
            position: absolute;
            inset: 0;
            background-size: cover;
            background-position: center;
            filter: brightness(0.7) blur(1px);
            transition: transform 0.4s ease;
        }

        .banner-side-prev:hover .side-poster-bg {
            transform: scale(1.08);
            filter: brightness(0.85);
        }

        .side-prev-overlay {
            position: absolute;
            inset: 0;
            background: linear-gradient(180deg, rgba(12, 26, 48, 0.4) 0%, rgba(12, 26, 48, 0.85) 100%);
        }

        /* 1.2 BANNER CHÍNH Ở GIỮA (Center Hero Slider) */
        .banner-center-slider {
            flex: 1;
            position: relative;
            border-radius: 14px;
            overflow: hidden;
            background: #0c1a30;
            box-shadow: 0 16px 40px rgba(12, 26, 48, 0.22);
            min-width: 0;
        }

        .hero-slider-track {
            display: flex;
            width: 100%;
            height: 100%;
            transition: transform 600ms cubic-bezier(0.25, 1, 0.5, 1);
            will-change: transform;
        }

        .hero-slide {
            min-width: 100%;
            height: 100%;
            position: relative;
            overflow: hidden;
            display: flex;
            align-items: flex-end;
            box-sizing: border-box;
            background: #0c1a30;
        }

        .hero-slide-img {
            position: absolute;
            inset: 0;
            width: 100%;
            height: 100%;
            object-fit: cover;
            object-position: center;
            border: none;
            z-index: 1;
            filter: none;
        }

        /* Overlay gradient tối ở cạnh dưới & góc trái giúp chữ sắc nét */
        .hero-slide-overlay {
            position: absolute;
            inset: 0;
            z-index: 2;
            background: linear-gradient(0deg, 
                rgba(12, 26, 48, 0.95) 0%, 
                rgba(12, 26, 48, 0.65) 45%, 
                rgba(12, 26, 48, 0.15) 80%,
                rgba(12, 26, 48, 0.0) 100%);
            pointer-events: none;
        }

        .hero-slide-content {
            position: relative;
            z-index: 3;
            width: 100%;
            padding: 16px 60px 18px;
            color: #ffffff;
            box-sizing: border-box;
        }

        .hero-slide-title {
            font-size: 22px;
            font-weight: 800;
            line-height: 1.2;
            color: #ffffff;
            margin: 0 0 4px 0;
            text-transform: uppercase;
            letter-spacing: -0.5px;
            text-shadow: 0 2px 10px rgba(0, 0, 0, 0.6);
            display: -webkit-box;
            -webkit-line-clamp: 2;
            line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }

        .hero-slide-date {
            font-size: 11px;
            font-weight: 700;
            color: rgba(255, 255, 255, 0.85);
            text-transform: uppercase;
            letter-spacing: 0.8px;
            margin-bottom: 6px;
        }

        .hero-meta-pills {
            display: flex;
            align-items: center;
            gap: 6px;
            margin-bottom: 8px;
            flex-wrap: wrap;
        }

        .fmt-pill {
            background: rgba(255, 255, 255, 0.18);
            color: #ffffff;
            font-size: 11px;
            font-weight: 700;
            padding: 3px 10px;
            border-radius: 5px;
            backdrop-filter: blur(4px);
            border: 1px solid rgba(255, 255, 255, 0.25);
        }

        .hero-slide-buttons {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-wrap: wrap;
        }

        .hero-btn-primary {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            background: #FF7A00;
            color: #ffffff;
            min-height: 38px;
            font-size: 12px;
            font-weight: 800;
            padding: 7px 18px;
            border-radius: 20px;
            text-decoration: none;
            box-shadow: 0 4px 14px rgba(255, 122, 0, 0.45);
            transition: all 0.25s ease;
            border: none;
            cursor: pointer;
        }

        .hero-btn-primary:hover {
            transform: translateY(-2px);
            box-shadow: 0 8px 18px rgba(255, 122, 0, 0.6);
            background: #e06900;
            color: #ffffff;
        }

        .hero-btn-trailer {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            background: rgba(255, 255, 255, 0.15);
            color: #ffffff;
            min-height: 38px;
            font-size: 12px;
            font-weight: 600;
            padding: 7px 16px;
            border-radius: 20px;
            text-decoration: none;
            border: 1px solid rgba(255, 255, 255, 0.35);
            backdrop-filter: blur(8px);
            transition: all 0.25s ease;
            cursor: pointer;
        }

        .hero-btn-trailer:hover {
            background: rgba(255, 255, 255, 0.3);
            border-color: #ffffff;
            color: #ffffff;
            transform: translateY(-2px);
        }

        /* Nav Arrows inside slider - Minimalist & Glassmorphism */
        .hero-slider-nav {
            position: absolute;
            top: 50%;
            transform: translateY(-50%);
            z-index: 10;
            width: 40px;
            height: 40px;
            border-radius: 50%;
            background: rgba(12, 26, 48, 0.65);
            color: #ffffff;
            border: 1.5px solid rgba(255, 255, 255, 0.35);
            backdrop-filter: blur(10px);
            -webkit-backdrop-filter: blur(10px);
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.35);
        }

        .hero-slider-nav svg {
            width: 22px;
            height: 22px;
            stroke: #ffffff;
            display: block;
            flex-shrink: 0;
            transition: transform 0.2s ease;
        }

        .hero-slider-nav:hover {
            background: #FF7A00;
            border-color: #FF7A00;
            color: #ffffff;
            transform: translateY(-50%) scale(1.1);
            box-shadow: 0 6px 20px rgba(255, 122, 0, 0.55);
        }

        .hero-slider-prev:hover svg {
            transform: translateX(-3px);
        }

        .hero-slider-next:hover svg {
            transform: translateX(3px);
        }

        .hero-slider-nav:active {
            transform: translateY(-50%) scale(0.95);
        }

        .hero-slider-prev { left: 14px; }
        .hero-slider-next { right: 14px; }

        /* Dot Indicators (Thanh capsule dẹp nằm ngang 4px chuẩn Ảnh số 4) */
        .hero-slider-dots {
            position: absolute;
            bottom: 12px;
            left: 50%;
            transform: translateX(-50%);
            z-index: 10;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .hero-dot {
            display: inline-block !important;
            min-height: 0 !important;
            height: 4px !important;
            width: 14px !important;
            padding: 0 !important;
            margin: 0 !important;
            border: none !important;
            border-radius: 2px !important;
            background: rgba(255, 255, 255, 0.45) !important;
            cursor: pointer !important;
            transition: all 0.3s ease !important;
            flex-shrink: 0 !important;
            box-shadow: none !important;
            outline: none !important;
            line-height: 0 !important;
            font-size: 0 !important;
        }

        .hero-dot:hover {
            background: rgba(255, 255, 255, 0.8) !important;
            transform: none !important;
        }

        .hero-dot.active {
            width: 26px !important;
            height: 4px !important;
            border-radius: 2px !important;
            background: #ffffff !important;
            box-shadow: 0 0 8px rgba(255, 255, 255, 0.7) !important;
        }

        /* 1.3 BANNER PHỤ PHẢI (Card Xem Trước Phim Tiếp Theo) */
        .banner-side-next {
            flex: 0 0 196px;
            border-radius: 14px;
            overflow: hidden;
            position: relative;
            cursor: pointer;
            background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
            border: 1px solid rgba(255, 255, 255, 0.12);
            padding: 12px;
            box-sizing: border-box;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            box-shadow: 0 8px 24px rgba(12, 26, 48, 0.15);
            transition: all 0.3s ease;
        }

        .banner-side-next:hover {
            border-color: rgba(255, 122, 0, 0.5);
            transform: translateY(-2px);
            box-shadow: 0 12px 32px rgba(12, 26, 48, 0.3);
        }

        .side-next-top {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 7px;
        }

        .side-badge {
            font-size: 11px;
            font-weight: 800;
            color: #38bdf8;
            text-transform: uppercase;
            letter-spacing: 0.8px;
            background: rgba(56, 189, 248, 0.15);
            padding: 3px 8px;
            border-radius: 4px;
        }

        .side-poster-box {
            width: 100%;
            height: 94px;
            border-radius: 8px;
            overflow: hidden;
            position: relative;
            background: #000;
            margin-bottom: 8px;
        }

        .side-poster-box img {
            width: 100%;
            height: 100%;
            object-fit: cover;
            object-position: center;
            transition: transform 0.4s ease;
        }

        .banner-side-next:hover .side-poster-box img {
            transform: scale(1.06);
        }

        .side-next-info {
            flex: 1;
            display: flex;
            flex-direction: column;
            justify-content: flex-end;
        }

        .side-next-title {
            font-size: 13px;
            font-weight: 700;
            color: #ffffff;
            margin: 0 0 3px 0;
            line-height: 1.3;
            text-transform: uppercase;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }

        .side-next-date {
            font-size: 10px;
            color: rgba(255, 255, 255, 0.65);
            margin-bottom: 6px;
            text-transform: uppercase;
        }

        .side-next-btn {
            width: 100%;
            background: rgba(255, 255, 255, 0.1);
            color: #ffffff;
            border: 1px solid rgba(255, 255, 255, 0.2);
            min-height: 34px;
            padding: 5px 10px;
            border-radius: 14px;
            font-size: 11px;
            font-weight: 600;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .side-next-btn:hover {
            background: rgba(255, 255, 255, 0.22);
            border-color: #ffffff;
        }

        /* ==========================================================================
           2. QUICK BUY TICKET BAR (THANH TÌM VÉ NHANH NỔI NỔI)
           ========================================================================== */
        .quick-buy-wrapper {
            position: relative;
            z-index: 20;
            margin-top: 12px;
            padding: 0;
        }

        .quick-buy-bar {
            background: #ffffff;
            border-radius: 16px;
            padding: 10px 18px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 14px;
            box-shadow: 0 10px 28px rgba(12, 26, 48, 0.08);
            border: 1px solid #E2E8F0;
        }

        .quick-step {
            flex: 1;
            display: flex;
            flex-direction: column;
            gap: 4px;
            min-width: 0;
        }

        .quick-step-label {
            font-size: 11px;
            font-weight: 700;
            color: #034EA2;
            display: flex;
            align-items: center;
            gap: 5px;
            text-transform: uppercase;
            letter-spacing: 0.3px;
        }

        .quick-step-num {
            display: inline-flex;
            width: 16px;
            height: 16px;
            border-radius: 50%;
            background: #034EA2;
            color: #ffffff;
            justify-content: center;
            align-items: center;
            font-size: 10px;
            font-weight: 800;
        }

        .quick-select {
            width: 100%;
            border: none;
            background: transparent;
            min-height: 36px;
            font-size: 12px;
            font-weight: 600;
            color: #0F172A;
            cursor: pointer;
            padding: 4px 0;
            border-bottom: 2px solid #E2E8F0;
            outline: none;
            transition: border-color 0.2s ease;
            white-space: nowrap;
            text-overflow: ellipsis;
            overflow: hidden;
        }

        .quick-select:focus {
            border-bottom-color: #FF7A00;
        }

        .quick-select:disabled {
            color: #94A3B8;
            cursor: not-allowed;
        }

        .quick-buy-btn {
            background: linear-gradient(90deg, #FF7A00 0%, #ff9833 100%);
            color: #ffffff;
            border: none;
            min-height: 40px;
            padding: 8px 22px;
            border-radius: 18px;
            font-weight: 800;
            font-size: 13px;
            cursor: pointer;
            transition: all 0.25s ease;
            white-space: nowrap;
            box-shadow: 0 4px 14px rgba(255, 122, 0, 0.4);
        }

        .quick-buy-btn:hover:not(:disabled) {
            transform: translateY(-2px);
            box-shadow: 0 8px 18px rgba(255, 122, 0, 0.55);
            background: #e06900;
        }

        .quick-buy-btn:disabled {
            background: #CBD5E1;
            color: #64748B;
            box-shadow: none;
            cursor: not-allowed;
        }

        /* ==========================================================================
           3. MOVIE LIST TABS & CARDS GRID (DANH SÁCH PHIM ĐÚNG GIAO DIỆN MẪU)
           ========================================================================== */
        .movie-section {
            margin-top: 14px;
        }

        .movie-tabs-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            border-bottom: 2px solid #E2E8F0;
            margin-bottom: 12px;
        }

        .tabs-list {
            display: flex;
            gap: 28px;
        }

        .tab-item {
            font-size: 16px;
            font-weight: 700;
            color: #64748B;
            cursor: pointer;
            padding-bottom: 10px;
            position: relative;
            transition: color 0.2s ease;
        }

        .tab-item:hover {
            color: #FF7A00;
        }

        .tab-item.active {
            color: #FF7A00;
        }

        .tab-item.active::after {
            content: '';
            position: absolute;
            bottom: -2px;
            left: 0;
            right: 0;
            height: 3px;
            background: #FF7A00;
            border-radius: 3px 3px 0 0;
        }

        .movie-grid-container {
            display: none;
        }

        .movie-grid-container.active {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 18px;
        }

        .movie-card-new {
            background: #ffffff;
            border-radius: 12px;
            overflow: hidden;
            box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05);
            text-decoration: none;
            color: #0F172A;
            display: flex;
            flex-direction: column;
            transition: all 0.25s ease;
            border: 1px solid #F1F5F9;
        }

        .movie-card-new:hover {
            transform: translateY(-5px);
            box-shadow: 0 14px 32px rgba(15, 23, 42, 0.12);
            border-color: #CBD5E1;
        }

        .poster-wrapper {
            position: relative;
            padding-top: 56.25%; /* Tỷ lệ 16:9 ngang */
            background-size: cover;
            background-position: center;
            background-color: #0c1a30;
        }

        .badge-top-left {
            position: absolute;
            top: 9px;
            left: 9px;
            background: #FF7A00;
            color: #ffffff;
            font-weight: 800;
            font-size: 10px;
            padding: 4px 8px;
            border-radius: 4px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .badge-top-left.coming {
            background: #034EA2;
        }

        .badge-bottom-right-rating {
            position: absolute;
            bottom: 12px;
            right: 12px;
            color: #f1c40f;
            font-weight: 800;
            font-size: 13px;
            background: rgba(12, 26, 48, 0.85);
            padding: 3px 8px;
            border-radius: 6px;
            backdrop-filter: blur(4px);
        }

        .movie-info-new {
            padding: 12px;
            display: flex;
            flex-direction: column;
            gap: 6px;
            flex: 1;
            background: #ffffff;
        }

        .movie-title-new {
            font-size: 14px;
            font-weight: 800;
            line-height: 1.35;
            margin: 0;
            color: #0F172A;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
            height: 38px;
        }

        .movie-pills-row {
            display: flex;
            align-items: center;
            gap: 6px;
            flex-wrap: wrap;
        }

        .movie-mini-pill {
            background: #F1F5F9;
            color: #475569;
            font-size: 10px;
            font-weight: 700;
            padding: 2px 6px;
            border-radius: 4px;
            border: 1px solid #E2E8F0;
        }

        /* Responsive Breakpoints */
        @media (max-width: 1120px) {
            .banner-side-prev { display: none; }
            .banner-side-next { flex: 0 0 190px; }
        }

        @media (max-width: 860px) {
            .hero-banner-section { height: 340px; }
            .banner-side-next { display: none; }
            .movie-grid-container.active { grid-template-columns: repeat(2, 1fr); gap: 16px; }
            .quick-buy-bar { flex-wrap: wrap; }
            .quick-step { flex: 1 1 45%; }
            .quick-buy-btn { width: 100%; }
        }

        @media (max-width: 640px) {
            .hero-banner-section { height: 300px; }
            .hero-slide-content { padding: 20px; }
            .hero-slide-title { font-size: 22px; }
            .movie-grid-container.active { grid-template-columns: repeat(2, 1fr); gap: 12px; }
            .tabs-list { gap: 16px; overflow-x: auto; padding-bottom: 8px; }
            .tab-item { font-size: 15px; white-space: nowrap; }
            .quick-buy-wrapper { margin-top: 16px; padding: 0; }
            .quick-buy-bar { padding: 14px; gap: 12px; }
            .quick-step { flex: 1 1 100%; }
        }
    </style>
</head>
<body class="public-page">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="home-main-shell">
        <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

        <c:if test="${dbReady == false}">
            <div style="margin-bottom: 20px; padding: 16px 20px; background: #FEF2F2; color: #DC2626; border-radius: 12px; border: 1px solid #FCA5A5; font-weight: 600;">
                ${fn:escapeXml(dbMessage)}
            </div>
        </c:if>

        <!-- 1. DYNAMIC 3-PART HERO BANNER SECTION -->
        <c:set var="heroList" value="${not empty featuredFilms ? featuredFilms : headerNowShowingFilms}" />
        <c:set var="defaultBannerUrl" value="${cbf:assetUrl(pageContext.request.contextPath, '/assets/img/hero-banner.png')}" />
        
        <section class="hero-banner-section" aria-label="Phim nổi bật CineBook">
            <!-- 1.1 BANNER PHỤ TRÁI (Bấm để xem phim trước) -->
            <div class="banner-side-prev" id="heroPrevSide" title="Phim trước trong danh sách">
                <div class="side-poster-bg" id="heroPrevBg"></div>
                <div class="side-prev-overlay"></div>
            </div>

            <!-- 1.2 BANNER CHÍNH Ở GIỮA (Slider Phim) -->
            <div class="banner-center-slider" id="heroSlider">
                <div class="hero-slider-track" id="heroSliderTrack">
                    <c:forEach var="film" items="${heroList}" varStatus="loop">
                        <c:set var="isComing" value="${film.status eq 'coming'}" />
                        <c:set var="backdropImg" value="${empty film.banner ? '/assets/img/hero-banner.png' : film.banner}" />
                        <c:set var="backdropUrl" value="${cbf:assetUrl(pageContext.request.contextPath, backdropImg)}" />
                        
                        <div class="hero-slide" data-slide-index="${loop.index}">
                            <img class="hero-slide-img" 
                                 src="${fn:escapeXml(backdropUrl)}" 
                                 alt="${fn:escapeXml(film.title)}" 
                                 loading="lazy" 
                                 onerror="this.onerror=null; this.src='${fn:escapeXml(defaultBannerUrl)}';" />
                            <div class="hero-slide-overlay"></div>

                            <div class="hero-slide-content">
                                <h2 class="hero-slide-title">${fn:escapeXml(film.title)}</h2>
                                <div class="hero-slide-date">
                                    KHỞI CHIẾU TẠI RẠP: ${fn:escapeXml(film.releaseDate)}
                                </div>

                                <div class="hero-meta-pills">
                                    <span class="fmt-pill">2D</span>
                                    <span class="fmt-pill">IMAX</span>
                                    <span class="fmt-pill">${fn:escapeXml(empty film.directors ? 'Dolby Atmos' : film.directors)}</span>
                                </div>

                                <div class="hero-slide-buttons">
                                    <c:if test="${not empty film.trailerUrl}">
                                        <button type="button" class="hero-btn-trailer" data-trailer-url="${fn:escapeXml(film.trailerUrl)}" data-movie-title="${fn:escapeXml(film.title)}">
                                            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                                            <span>Xem Trailer</span>
                                        </button>
                                    </c:if>

                                    <c:choose>
                                        <c:when test="${isComing}">
                                            <a href="${pageContext.request.contextPath}/films/${fn:escapeXml(film.id)}" class="hero-btn-primary">
                                                <span>Xem Chi Tiết</span>
                                            </a>
                                        </c:when>
                                        <c:otherwise>
                                            <a href="${pageContext.request.contextPath}/booking?filmId=${fn:escapeXml(film.id)}" class="hero-btn-primary">
                                                <span>Đặt Vé Ngay</span>
                                            </a>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                            </div>
                        </div>
                    </c:forEach>

                    <c:if test="${empty heroList}">
                        <div class="hero-slide">
                            <img class="hero-slide-img" src="${fn:escapeXml(defaultBannerUrl)}" alt="CineBook Banner" />
                            <div class="hero-slide-overlay"></div>
                            <div class="hero-slide-content">
                                <h2 class="hero-slide-title">CINEBOOK ĐẶT VÉ PHIM</h2>
                                <div class="hero-slide-date">TRẢI NGHIỆM ĐIỆN ẢNH ĐỈNH CAO</div>
                                <div class="hero-slide-buttons">
                                    <a href="${pageContext.request.contextPath}/booking" class="hero-btn-primary">Đặt Vé Ngay</a>
                                </div>
                            </div>
                        </div>
                    </c:if>
                </div>

                <!-- Nav Arrows inside center slider - Minimalist Arrow Icons -->
                <button type="button" class="hero-slider-nav hero-slider-prev" id="heroPrevBtn" aria-label="Slide trước" title="Phim trước">
                    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                        <line x1="19" y1="12" x2="5" y2="12"></line>
                        <polyline points="12 19 5 12 12 5"></polyline>
                    </svg>
                </button>
                <button type="button" class="hero-slider-nav hero-slider-next" id="heroNextBtn" aria-label="Slide tiếp theo" title="Phim tiếp theo">
                    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                        <line x1="5" y1="12" x2="19" y2="12"></line>
                        <polyline points="12 5 19 12 12 19"></polyline>
                    </svg>
                </button>

                <!-- Dots -->
                <div class="hero-slider-dots" id="heroSliderDots"></div>
            </div>

            <!-- 1.3 BANNER PHỤ PHẢI (Card Phim Tiếp Theo) -->
            <div class="banner-side-next" id="heroNextSide" title="Bấm để chuyển tới phim tiếp theo">
                <div class="side-next-top">
                    <span class="side-badge" id="heroNextBadge">SẮP CHIẾU</span>
                </div>
                <div class="side-poster-box">
                    <img id="heroNextImg" src="${pageContext.request.contextPath}/assets/img/default-film.jpg" alt="Poster phim tiếp theo">
                </div>
                <div class="side-next-info">
                    <h4 class="side-next-title" id="heroNextTitle">Loading...</h4>
                    <div class="side-next-date" id="heroNextDate">KHỞI CHIẾU TẠI RẠP</div>
                    <button type="button" class="side-next-btn" id="heroNextTrailerBtn">
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                        <span>Xem Trailer</span>
                    </button>
                </div>
            </div>
        </section>

        <!-- 2. QUICK BUY TICKET BAR -->
        <section class="quick-buy-wrapper" aria-label="Mua vé nhanh">
            <form class="quick-buy-bar" method="get" action="${pageContext.request.contextPath}/booking">
                <!-- 1. Chọn phim -->
                <div class="quick-step">
                    <span class="quick-step-label"><span class="quick-step-num">1</span> Chọn Phim</span>
                    <select id="quickFilm" class="quick-select" required aria-label="Chọn phim">
                        <option value="">Chọn phim...</option>
                        <c:forEach var="film" items="${featuredFilms}">
                            <option value="${fn:escapeXml(film.id)}">${fn:escapeXml(film.title)}</option>
                        </c:forEach>
                    </select>
                </div>

                <!-- 2. Chọn Rạp -->
                <div class="quick-step">
                    <span class="quick-step-label"><span class="quick-step-num">2</span> Chọn Rạp</span>
                    <select id="quickCinema" class="quick-select" disabled required aria-label="Chọn rạp">
                        <option value="">Chọn rạp...</option>
                        <c:forEach var="cinema" items="${cinemas}">
                            <option value="${fn:escapeXml(cinema.id)}">${fn:escapeXml(cinema.name)}</option>
                        </c:forEach>
                    </select>
                </div>

                <!-- 3. Chọn Ngày -->
                <div class="quick-step">
                    <span class="quick-step-label"><span class="quick-step-num">3</span> Chọn Ngày</span>
                    <select id="quickDate" class="quick-select" disabled required aria-label="Chọn ngày">
                        <option value="">Chọn ngày...</option>
                    </select>
                </div>

                <!-- 4. Chọn Suất -->
                <div class="quick-step">
                    <span class="quick-step-label"><span class="quick-step-num">4</span> Chọn Suất</span>
                    <select id="quickShowtime" name="showtimeId" class="quick-select" disabled required aria-label="Chọn suất chiếu">
                        <option value="">Chọn suất chiếu...</option>
                    </select>
                </div>

                <!-- Nút CTA -->
                <button type="submit" class="quick-buy-btn" id="quickSubmitBtn" disabled>Tìm Vé</button>
            </form>
        </section>

        <!-- 3. MOVIE LIST TABS & CARDS GRID -->
        <section class="movie-section">
            <div class="movie-tabs-header">
                <div class="tabs-list" role="tablist">
                    <span class="tab-item active" role="tab" aria-selected="true" onclick="switchMovieTab('now-showing', this)">Đang chiếu</span>
                    <span class="tab-item" role="tab" aria-selected="false" onclick="switchMovieTab('upcoming', this)">Sắp chiếu</span>
                    <span class="tab-item" role="tab" aria-selected="false" onclick="switchMovieTab('imax', this)">IMAX</span>
                    <span class="tab-item" role="tab" aria-selected="false" onclick="switchMovieTab('national', this)">Toàn quốc</span>
                </div>
            </div>

            <!-- Tab 1. Đang chiếu -->
            <div id="grid-now-showing" class="movie-grid-container active">
                <c:forEach var="film" items="${headerNowShowingFilms}">
                    <c:set var="fThumb" value="${cbf:assetUrl(pageContext.request.contextPath, empty film.thumbnail ? '/assets/img/default-film.jpg' : film.thumbnail)}" />
                    <a href="${pageContext.request.contextPath}/films/${fn:escapeXml(film.id)}" class="movie-card-new">
                        <div class="poster-wrapper" style="background-image: url('${fn:escapeXml(fThumb)}');">
                            <span class="badge-top-left">ĐANG CHIẾU</span>
                            <c:if test="${not empty film.rating and film.rating > 0}">
                                <span class="badge-bottom-right-rating">★ ${fn:escapeXml(film.rating)}</span>
                            </c:if>
                        </div>
                        <div class="movie-info-new">
                            <h3 class="movie-title-new">${fn:escapeXml(film.title)}</h3>
                            <div class="movie-pills-row">
                                <span class="movie-mini-pill">2D</span>
                                <span class="movie-mini-pill">IMAX</span>
                                <span class="movie-mini-pill">${fn:escapeXml(empty film.directors ? 'Dolby Atmos' : film.directors)}</span>
                            </div>
                        </div>
                    </a>
                </c:forEach>
                <c:if test="${empty headerNowShowingFilms}">
                    <div style="grid-column: 1/-1; text-align: center; color: #64748B; padding: 40px;">Chưa có phim đang chiếu.</div>
                </c:if>
            </div>

            <!-- Tab 2. Sắp chiếu -->
            <div id="grid-upcoming" class="movie-grid-container">
                <c:forEach var="film" items="${headerUpcomingFilms}">
                    <c:set var="fThumb" value="${cbf:assetUrl(pageContext.request.contextPath, empty film.thumbnail ? '/assets/img/default-film.jpg' : film.thumbnail)}" />
                    <a href="${pageContext.request.contextPath}/films/${fn:escapeXml(film.id)}" class="movie-card-new">
                        <div class="poster-wrapper" style="background-image: url('${fn:escapeXml(fThumb)}');">
                            <span class="badge-top-left coming">SẮP CHIẾU</span>
                            <c:if test="${not empty film.rating and film.rating > 0}">
                                <span class="badge-bottom-right-rating">★ ${fn:escapeXml(film.rating)}</span>
                            </c:if>
                        </div>
                        <div class="movie-info-new">
                            <h3 class="movie-title-new">${fn:escapeXml(film.title)}</h3>
                            <div class="movie-pills-row">
                                <span class="movie-mini-pill">2D</span>
                                <span class="movie-mini-pill">IMAX</span>
                                <span class="movie-mini-pill">${fn:escapeXml(empty film.directors ? 'Dolby Atmos' : film.directors)}</span>
                            </div>
                        </div>
                    </a>
                </c:forEach>
                <c:if test="${empty headerUpcomingFilms}">
                    <div style="grid-column: 1/-1; text-align: center; color: #64748B; padding: 40px;">Chưa có phim sắp chiếu.</div>
                </c:if>
            </div>

            <!-- Tab 3. Phim IMAX -->
            <div id="grid-imax" class="movie-grid-container">
                <c:forEach var="film" items="${headerNowShowingFilms}">
                    <c:set var="fThumb" value="${cbf:assetUrl(pageContext.request.contextPath, empty film.thumbnail ? '/assets/img/default-film.jpg' : film.thumbnail)}" />
                    <a href="${pageContext.request.contextPath}/films/${fn:escapeXml(film.id)}" class="movie-card-new">
                        <div class="poster-wrapper" style="background-image: url('${fn:escapeXml(fThumb)}');">
                            <span class="badge-top-left">IMAX 3D</span>
                            <c:if test="${not empty film.rating and film.rating > 0}">
                                <span class="badge-bottom-right-rating">★ ${fn:escapeXml(film.rating)}</span>
                            </c:if>
                        </div>
                        <div class="movie-info-new">
                            <h3 class="movie-title-new">${fn:escapeXml(film.title)} (IMAX)</h3>
                            <div class="movie-pills-row">
                                <span class="movie-mini-pill">2D</span>
                                <span class="movie-mini-pill">IMAX 3D</span>
                            </div>
                        </div>
                    </a>
                </c:forEach>
            </div>

            <!-- Tab 4. Toàn quốc -->
            <div id="grid-national" class="movie-grid-container">
                <c:forEach var="film" items="${featuredFilms}">
                    <c:set var="fThumb" value="${cbf:assetUrl(pageContext.request.contextPath, empty film.thumbnail ? '/assets/img/default-film.jpg' : film.thumbnail)}" />
                    <a href="${pageContext.request.contextPath}/films/${fn:escapeXml(film.id)}" class="movie-card-new">
                        <div class="poster-wrapper" style="background-image: url('${fn:escapeXml(fThumb)}');">
                            <span class="badge-top-left">ĐANG CHIẾU</span>
                            <c:if test="${not empty film.rating and film.rating > 0}">
                                <span class="badge-bottom-right-rating">★ ${fn:escapeXml(film.rating)}</span>
                            </c:if>
                        </div>
                        <div class="movie-info-new">
                            <h3 class="movie-title-new">${fn:escapeXml(film.title)}</h3>
                            <div class="movie-pills-row">
                                <span class="movie-mini-pill">2D</span>
                                <span class="movie-mini-pill">IMAX</span>
                            </div>
                        </div>
                    </a>
                </c:forEach>
            </div>
        </section>
    </main>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>

    <!-- Chứa dữ liệu phim thực tế dưới dạng JSON cho Slider & Side cards đồng bộ -->
    <script id="heroFilmsJson" type="application/json">
        [
            <c:forEach var="film" items="${heroList}" varStatus="loop">
                {
                    "id": ${film.id},
                    "title": "${fn:escapeXml(film.title)}",
                    "releaseDate": "${fn:escapeXml(film.releaseDate)}",
                    "isComing": ${film.status eq 'coming'},
                    "banner": "${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, empty film.banner ? '/assets/img/hero-banner.png' : film.banner))}",
                    "thumbnail": "${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, empty film.thumbnail ? '/assets/img/default-film.jpg' : film.thumbnail))}",
                    "trailerUrl": "${fn:escapeXml(film.trailerUrl)}"
                }${not loop.last ? ',' : ''}
            </c:forEach>
        ]
    </script>

    <!-- Chứa dữ liệu showtimes dạng JSON cho Quick Buy lọc nhanh -->
    <script id="allShowtimesJson" type="application/json">
        [
            <c:forEach var="st" items="${showtimes}" varStatus="status">
                {
                    "id": ${st.id},
                    "filmId": ${st.filmId},
                    "cinemaId": ${st.cinemaId},
                    "startTime": "${st.startTime}",
                    "displayTime": "${st.startTimeDisplay}"
                }${not status.last ? ',' : ''}
            </c:forEach>
        ]
    </script>

    <script>
        /* ==========================================================================
           3-PART HERO BANNER SLIDER CONTROLLER (BÁM SÁT 100% GIAO DIỆN PHÁC THẢO)
           ========================================================================== */
        (function initHeroBanner() {
            var films = JSON.parse(document.getElementById('heroFilmsJson').textContent || '[]');
            var total = films.length;

            var slider = document.getElementById('heroSlider');
            var track = document.getElementById('heroSliderTrack');
            var prevBtn = document.getElementById('heroPrevBtn');
            var nextBtn = document.getElementById('heroNextBtn');
            var dotsContainer = document.getElementById('heroSliderDots');

            var heroPrevSide = document.getElementById('heroPrevSide');
            var heroPrevBg = document.getElementById('heroPrevBg');

            var heroNextSide = document.getElementById('heroNextSide');
            var heroNextBadge = document.getElementById('heroNextBadge');
            var heroNextImg = document.getElementById('heroNextImg');
            var heroNextTitle = document.getElementById('heroNextTitle');
            var heroNextDate = document.getElementById('heroNextDate');
            var heroNextTrailerBtn = document.getElementById('heroNextTrailerBtn');

            if (!slider || !track || total === 0) return;

            var currentIndex = 0;
            var timer = null;
            var isPaused = false;
            var AUTOPLAY_DELAY = 10000; // 10 giây tự động chuyển slide

            // Tạo Dot Indicators
            if (dotsContainer) {
                dotsContainer.innerHTML = '';
                for (var i = 0; i < total; i++) {
                    var dot = document.createElement('button');
                    dot.type = 'button';
                    dot.className = 'hero-dot' + (i === 0 ? ' active' : '');
                    dot.setAttribute('aria-label', 'Slide ' + (i + 1));
                    (function(idx) {
                        dot.addEventListener('click', function() {
                            goToSlide(idx);
                            resetAutoplayTimer();
                        });
                    })(i);
                    dotsContainer.appendChild(dot);
                }
            }

            function updateHeroUI() {
                // 1. Chuyển Slide ở giữa
                track.style.transform = 'translateX(-' + (currentIndex * 100) + '%)';

                // 2. Cập nhật Dots
                if (dotsContainer) {
                    var dots = dotsContainer.querySelectorAll('.hero-dot');
                    dots.forEach(function(d, idx) {
                        if (idx === currentIndex) d.classList.add('active');
                        else d.classList.remove('active');
                    });
                }

                // 3. Cập nhật Banner Phụ Trái (Phim Trước)
                var prevIndex = (currentIndex - 1 + total) % total;
                if (heroPrevBg && films[prevIndex]) {
                    heroPrevBg.style.backgroundImage = "url('" + (films[prevIndex].thumbnail || films[prevIndex].banner) + "')";
                }

                // 4. Cập nhật Banner Phụ Phải (Card Xem Trước Phim Tiếp Theo)
                var nextIndex = (currentIndex + 1) % total;
                var nextFilm = films[nextIndex];
                if (nextFilm) {
                    if (heroNextBadge) heroNextBadge.textContent = nextFilm.isComing ? 'SẮP CHIẾU' : 'ĐANG CHIẾU';
                    if (heroNextImg) heroNextImg.src = nextFilm.thumbnail;
                    if (heroNextTitle) heroNextTitle.textContent = nextFilm.title;
                    if (heroNextDate) heroNextDate.textContent = 'KHỞI CHIẾU TẠI RẠP ' + (nextFilm.releaseDate || '');
                    
                    if (heroNextTrailerBtn) {
                        if (nextFilm.trailerUrl && nextFilm.trailerUrl.trim() !== '') {
                            heroNextTrailerBtn.style.display = 'flex';
                            heroNextTrailerBtn.onclick = function(e) {
                                e.stopPropagation();
                                if (window.openTrailerModal) {
                                    window.openTrailerModal(nextFilm.trailerUrl, nextFilm.title);
                                }
                            };
                        } else {
                            heroNextTrailerBtn.style.display = 'none';
                        }
                    }
                }
            }

            function goToSlide(idx) {
                if (idx < 0) currentIndex = total - 1;
                else if (idx >= total) currentIndex = 0;
                else currentIndex = idx;
                updateHeroUI();
            }

            function nextSlide() { goToSlide(currentIndex + 1); }
            function prevSlide() { goToSlide(currentIndex - 1); }

            function startAutoplay() {
                stopAutoplay();
                timer = setInterval(function() {
                    if (!isPaused) nextSlide();
                }, AUTOPLAY_DELAY);
            }

            function stopAutoplay() {
                if (timer) { clearInterval(timer); timer = null; }
            }

            function resetAutoplayTimer() { startAutoplay(); }

            // Sự kiện click Nav Arrows
            if (prevBtn) {
                prevBtn.addEventListener('click', function(e) {
                    e.stopPropagation();
                    prevSlide();
                    resetAutoplayTimer();
                });
            }

            if (nextBtn) {
                nextBtn.addEventListener('click', function(e) {
                    e.stopPropagation();
                    nextSlide();
                    resetAutoplayTimer();
                });
            }

            // Click vào Banner phụ Trái hoặc Phải để chuyển nhanh
            if (heroPrevSide) {
                heroPrevSide.addEventListener('click', function() {
                    prevSlide();
                    resetAutoplayTimer();
                });
            }

            if (heroNextSide) {
                heroNextSide.addEventListener('click', function() {
                    nextSlide();
                    resetAutoplayTimer();
                });
            }

            // Tạm dừng khi rê chuột vào hoặc khi mở Trailer Modal
            slider.addEventListener('mouseenter', function() { isPaused = true; });
            slider.addEventListener('mouseleave', function() { isPaused = false; });

            window.addEventListener('trailerModalOpen', function() { isPaused = true; });
            window.addEventListener('trailerModalClose', function() { 
                isPaused = false; 
                resetAutoplayTimer();
            });

            // Touch Swipe
            var startX = 0, dist = 0;
            slider.addEventListener('touchstart', function(e) { startX = e.touches[0].clientX; dist = 0; }, { passive: true });
            slider.addEventListener('touchmove', function(e) { dist = e.touches[0].clientX - startX; }, { passive: true });
            slider.addEventListener('touchend', function() {
                if (Math.abs(dist) > 40) {
                    if (dist < 0) nextSlide();
                    else prevSlide();
                    resetAutoplayTimer();
                }
            });

            // Khởi tạo ban đầu
            updateHeroUI();
            startAutoplay();
        })();

        /* ==========================================================================
           QUICK TICKET BUYING LOGIC
           ========================================================================== */
        (function initQuickBuy() {
            const allShowtimes = JSON.parse(document.getElementById('allShowtimesJson').textContent || '[]');

            const quickFilm = document.getElementById('quickFilm');
            const quickCinema = document.getElementById('quickCinema');
            const quickDate = document.getElementById('quickDate');
            const quickShowtime = document.getElementById('quickShowtime');
            const quickSubmitBtn = document.getElementById('quickSubmitBtn');

            if (!quickFilm || !quickCinema) return;

            quickFilm.addEventListener('change', () => {
                const filmId = quickFilm.value;
                if (!filmId) {
                    resetDropdowns(1);
                    return;
                }
                
                const cinemaIds = [...new Set(allShowtimes.filter(st => st.filmId == filmId).map(st => st.cinemaId))];
                
                Array.from(quickCinema.options).forEach(opt => {
                    if (opt.value === "") return;
                    opt.disabled = !cinemaIds.includes(Number(opt.value));
                });
                quickCinema.disabled = false;
                resetDropdowns(2);
            });

            quickCinema.addEventListener('change', () => {
                const filmId = quickFilm.value;
                const cinemaId = quickCinema.value;
                if (!cinemaId) {
                    resetDropdowns(2);
                    return;
                }

                const sts = allShowtimes.filter(st => st.filmId == filmId && st.cinemaId == cinemaId);
                const dates = [...new Set(sts.map(st => st.startTime.substring(0, 10)))];

                quickDate.innerHTML = '<option value="">Chọn ngày...</option>';
                dates.forEach(d => {
                    const opt = document.createElement('option');
                    opt.value = d;
                    opt.textContent = formatVietnameseDate(d);
                    quickDate.appendChild(opt);
                });
                quickDate.disabled = false;
                resetDropdowns(3);
            });

            quickDate.addEventListener('change', () => {
                const filmId = quickFilm.value;
                const cinemaId = quickCinema.value;
                const dateStr = quickDate.value;
                if (!dateStr) {
                    resetDropdowns(3);
                    return;
                }

                const sts = allShowtimes.filter(st => st.filmId == filmId && st.cinemaId == cinemaId && st.startTime.startsWith(dateStr));

                quickShowtime.innerHTML = '<option value="">Chọn suất chiếu...</option>';
                sts.forEach(st => {
                    const opt = document.createElement('option');
                    opt.value = st.id;
                    opt.textContent = st.displayTime;
                    quickShowtime.appendChild(opt);
                });
                quickShowtime.disabled = false;
                quickSubmitBtn.disabled = true;
            });

            quickShowtime.addEventListener('change', () => {
                quickSubmitBtn.disabled = !quickShowtime.value;
            });

            function resetDropdowns(level) {
                if (level <= 1) {
                    quickCinema.value = "";
                    quickCinema.disabled = true;
                }
                if (level <= 2) {
                    quickDate.innerHTML = '<option value="">Chọn ngày...</option>';
                    quickDate.disabled = true;
                }
                if (level <= 3) {
                    quickShowtime.innerHTML = '<option value="">Chọn suất chiếu...</option>';
                    quickShowtime.disabled = true;
                    quickSubmitBtn.disabled = true;
                }
            }

            function formatVietnameseDate(dateStr) {
                const parts = dateStr.split('-');
                if (parts.length !== 3) return dateStr;
                return parts[2] + "/" + parts[1] + "/" + parts[0];
            }
        })();

        // Chuyển Tab phim
        function switchMovieTab(tabId, el) {
            document.querySelectorAll('.tab-item').forEach(item => {
                item.classList.remove('active');
                item.setAttribute('aria-selected', 'false');
            });
            el.classList.add('active');
            el.setAttribute('aria-selected', 'true');

            document.querySelectorAll('.movie-grid-container').forEach(grid => grid.classList.remove('active'));
            const targetGrid = document.getElementById('grid-' + tabId);
            if (targetGrid) {
                targetGrid.classList.add('active');
            }
        }
    </script>
    <%@ include file="/WEB-INF/views/shared/trailer-modal.jspf" %>
</body>
</html>
