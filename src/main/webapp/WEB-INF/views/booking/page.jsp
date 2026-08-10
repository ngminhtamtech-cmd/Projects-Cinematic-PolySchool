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
    <title>Đặt vé - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css?v=1.0.2">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/cinema-seat-3d.css?v=20260810c">
    <style>
        /* CSS cho Booking Page mới */
        .booking-stepper {
            display: flex;
            justify-content: space-between;
            align-items: center;
            background: var(--color-gray-50);
            padding: 8px 18px;
            border-radius: 10px;
            margin-bottom: 14px;
            border: 1px solid var(--color-gray-200);
        }
        .step-nav-item {
            font-size: 13px;
            font-weight: 700;
            color: var(--color-gray-500);
            position: relative;
            flex: 1;
            text-align: center;
            cursor: pointer;
            transition: color 0.2s ease;
        }
        .step-nav-item:not(:last-child)::after {
            content: '➔';
            position: absolute;
            right: -8px;
            top: 50%;
            transform: translateY(-50%);
            color: var(--color-gray-300);
            font-size: 12px;
        }
        .step-nav-item.is-active {
            color: #ff7a00;
            font-weight: 800;
            text-decoration: underline;
        }
        .step-nav-item.is-done {
            color: var(--color-success);
        }

        .booking-layout-new {
            display: grid;
            grid-template-columns: 1.8fr 1fr;
            gap: 16px;
            align-items: start;
        }
        
        /* Step visibility */
        .booking-step {
            display: none;
        }
        .booking-step.active {
            display: flex;
            flex-direction: column;
            gap: 12px;
        }

        /* Step 1: Accordions */
        .accordion-item {
            border: 1px solid var(--color-gray-200);
            border-radius: 10px;
            background: var(--surface);
            overflow: hidden;
        }
        .accordion-header {
            background: var(--color-gray-50);
            padding: 10px 14px;
            font-size: 14px;
            font-weight: 700;
            display: flex;
            justify-content: space-between;
            align-items: center;
            cursor: pointer;
            border-bottom: 1px solid var(--color-gray-200);
        }
        .accordion-header .chevron {
            font-size: 14px;
            color: var(--color-secondary);
            transition: transform 0.2s;
        }
        .accordion-item.closed .accordion-content {
            display: none;
        }
        .accordion-item.closed .chevron {
            transform: rotate(-90deg);
        }
        .accordion-content {
            padding: 12px 14px;
        }

        /* Chips */
        .chips-container {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
        }
        .chip {
            padding: 6px 14px;
            border-radius: 16px;
            border: 1px solid var(--color-gray-200);
            background: var(--color-gray-50);
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
        }
        .chip.active {
            background: var(--color-secondary);
            color: white;
            border-color: var(--color-secondary);
        }

        /* Film selection step 1 - Landscape Horizontal Posters */
        .film-select-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 12px;
        }
        .film-select-card {
            border: 1.5px solid var(--color-gray-200);
            border-radius: 10px;
            overflow: hidden;
            cursor: pointer;
            position: relative;
            transition: all 0.2s ease;
            background: #ffffff;
        }
        .film-select-card:hover {
            border-color: #ff7a00;
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(255, 122, 0, 0.15);
        }
        .film-select-card.active {
            border-color: #ff7a00;
            box-shadow: 0 0 0 2px #ff7a00, 0 4px 12px rgba(255, 122, 0, 0.25);
        }
        /* Horizontal landscape poster (16:9 ratio) */
        .film-select-poster {
            padding-top: 52%;
            background-size: cover;
            background-position: center;
            position: relative;
            border-radius: 8px 8px 0 0;
        }
        .film-select-card.active .film-select-poster::after {
            content: '✓';
            position: absolute;
            inset: 0;
            background: rgba(255, 122, 0, 0.7);
            color: white;
            display: flex;
            justify-content: center;
            align-items: center;
            font-size: 28px;
            font-weight: bold;
        }
        .film-select-title {
            padding: 6px 8px;
            font-size: 12px;
            font-weight: 800;
            color: #0f172a;
            text-align: center;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        /* Showtime selection */
        .cinema-group {
            border-bottom: 1px solid var(--color-gray-200);
            padding-bottom: 12px;
            margin-bottom: 12px;
        }
        .cinema-group-title {
            font-size: 13px;
            font-weight: 700;
            color: var(--ink);
            margin-bottom: 8px;
        }
        .format-group {
            margin-left: 12px;
            margin-bottom: 8px;
        }
        .format-label {
            font-size: 11px;
            font-weight: 700;
            color: var(--color-gray-500);
            margin-bottom: 6px;
        }
        .showtimes-list {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
        }
        .showtime-btn {
            border: 1px solid var(--color-gray-200);
            background: var(--surface);
            padding: 6px 12px;
            font-size: 13px;
            font-weight: 700;
            border-radius: var(--radius-sm);
            cursor: pointer;
            transition: all 0.2s;
        }
        .showtime-btn:hover {
            border-color: var(--color-primary);
            color: var(--color-primary);
        }
        .showtime-btn.active {
            background: var(--color-primary);
            color: white;
            border-color: var(--color-primary);
        }

        /* Step 3 Showtime Selection Redesign - Match Image [2] */
        .showtime-toolbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 12px;
            margin-bottom: 16px;
            padding-bottom: 16px;
            border-bottom: 1px solid var(--color-gray-200);
        }
        .date-chips-wrapper {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
        }
        .date-chip-item {
            padding: 8px 18px;
            border-radius: 12px;
            border: 1.5px solid #e2e8f0;
            background: #f8fafc;
            font-size: 13px;
            font-weight: 700;
            color: #0f172a;
            cursor: pointer;
            transition: all 0.2s ease;
            display: inline-flex;
            flex-direction: column;
            align-items: center;
            line-height: 1.3;
            min-width: 80px;
        }
        .date-chip-item .date-dow {
            font-size: 11px;
            color: #64748b;
            font-weight: 600;
            text-transform: uppercase;
        }
        .date-chip-item .date-num {
            font-size: 14px;
            font-weight: 800;
        }
        .date-chip-item:hover {
            border-color: #ff7a00;
            color: #ff7a00;
            background: #fff8f3;
        }
        .date-chip-item:hover .date-dow {
            color: #ff7a00;
        }
        .date-chip-item.active {
            background: #fff8f3;
            color: #ff7a00;
            border-color: #ff7a00;
            box-shadow: 0 4px 12px rgba(255, 122, 0, 0.2);
        }
        .date-chip-item.active .date-dow {
            color: #ff7a00;
        }
        .selected-film-badge {
            font-size: 12px;
            font-weight: 700;
            color: #ff7a00;
            background: #fff8f3;
            border: 1px solid rgba(255, 122, 0, 0.3);
            padding: 3px 10px;
            border-radius: 12px;
            margin-left: 10px;
            display: inline-flex;
            align-items: center;
            gap: 4px;
        }
        .cinema-filter-select {
            padding: 9px 16px;
            border-radius: 20px;
            border: 1.5px solid #cbd5e1;
            background: var(--surface);
            font-size: 13px;
            font-weight: 700;
            color: #0f172a;
            cursor: pointer;
            outline: none;
            box-shadow: 0 1px 3px rgba(0,0,0,0.04);
            transition: border-color 0.2s ease;
        }
        .cinema-filter-select:focus {
            border-color: #ff7a00;
        }
        .cinema-card-block {
            background: #ffffff;
            border-radius: 14px;
            padding: 18px 20px;
            margin-bottom: 16px;
            border: 1px solid #e2e8f0;
            box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
        }
        .cinema-card-header {
            font-size: 16px;
            font-weight: 800;
            color: #0f172a;
            margin-bottom: 12px;
            display: flex;
            align-items: center;
            gap: 8px;
            padding-bottom: 8px;
            border-bottom: 1px solid #f1f5f9;
        }
        
        /* Version groups (Lồng tiếng / Thuyết minh) separated by horizontal line */
        .version-showtime-group {
            padding: 10px 0 12px 0;
            border-bottom: 1px solid #f1f5f9;
        }
        .version-showtime-group:last-child {
            border-bottom: none;
        }
        .version-label-text {
            font-size: 13px;
            font-weight: 700;
            color: #475569;
            margin-bottom: 10px;
        }
        .showtimes-flex-list {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
        }
        button.showtime-btn,
        .showtime-btn {
            display: inline-flex !important;
            align-items: center !important;
            justify-content: center !important;
            min-height: 40px !important;
            border: 1.5px solid #cbd5e1 !important;
            background: #ffffff !important;
            padding: 8px 22px !important;
            font-size: 15px !important;
            font-weight: 800 !important;
            border-radius: 10px !important;
            cursor: pointer !important;
            color: #0f172a !important;
            transition: all 0.2s ease !important;
            box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06) !important;
            outline: none !important;
        }
        button.showtime-btn:hover,
        .showtime-btn:hover {
            border-color: #ff7a00 !important;
            color: #ff7a00 !important;
            background: #fff8f3 !important;
            transform: translateY(-2px) !important;
            box-shadow: 0 4px 12px rgba(255, 122, 0, 0.25) !important;
        }
        button.showtime-btn.active,
        .showtime-btn.active {
            background: #ff7a00 !important;
            color: #ffffff !important;
            border-color: #ff7a00 !important;
            box-shadow: 0 4px 14px rgba(255, 122, 0, 0.4) !important;
        }

        /* Step 2: Seats UI styling */
        .ent-screen-wrapper {
            text-align: center;
            margin: 10px 0 25px 0;
        }
        .ent-screen-arc {
            height: 18px;
            border-top: 3px solid #0284c7;
            border-radius: 50% 50% 0 0 / 100% 100% 0 0;
            margin: 0 auto 6px auto;
            max-width: 550px;
        }
        .ent-screen-text {
            font-size: 0.78rem;
            font-weight: 800;
            text-transform: uppercase;
            letter-spacing: 0.25em;
            color: #94a3b8;
        }
        .seat-map-wrapper {
            position: relative;
            overflow-x: auto;
            padding: 16px 10px;
            border: 1px solid var(--color-gray-200);
            border-radius: var(--radius-md);
            background: var(--surface-soft);
        }
        .seat-grid-container {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 8px;
            width: max-content;
            margin: 0 auto;
        }
        .seat-row {
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .row-label-cell {
            font-weight: 800;
            width: 24px;
            text-align: center;
            color: #64748b;
            font-size: 0.9rem;
        }
        .seat-buttons-grid {
            display: flex;
            gap: 8px;
        }
        .seat-cell {
            height: 34px;
            width: 34px;
            flex: 0 0 34px;
            min-width: 34px;
            border-radius: 6px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-size: 11px;
            font-weight: 700;
            cursor: pointer;
            user-select: none;
            transition: all 0.12s ease;
            border: 1px solid #93c5fd;
            background: #ffffff;
            color: #1d4ed8;
            padding: 0;
        }
        .seat-cell:hover {
            border-color: #2563eb;
            background: #eff6ff;
        }
        .seat-cell.standard {
            background: #ffffff;
            border-color: #93c5fd;
            color: #1d4ed8;
        }
        .seat-cell.vip {
            background: #fef9c3;
            border-color: #facc15;
            color: #a16207;
        }
        .seat-cell.couple {
            background: #ffffff;
            border-color: #fdba74;
            color: #c2410c;
            min-width: 76px;
            width: 76px;
            flex-basis: 76px;
        }
        .seat-cell.selected {
            background: var(--color-primary, #ff7a00) !important;
            border-color: var(--color-primary, #ff7a00) !important;
            color: #ffffff !important;
            box-shadow: 0 4px 10px rgba(255, 122, 0, 0.35) !important;
        }
        .seat-cell.booked {
            background: #e2e8f0 !important;
            border-color: #cbd5e1 !important;
            color: #64748b !important;
            cursor: not-allowed;
        }
        .seat-cell.held {
            background: #dbeafe !important;
            border-color: #93c5fd !important;
            color: #1d4ed8 !important;
            cursor: not-allowed;
        }
        .seat-slot-spacer {
            height: 34px;
            width: 34px;
            flex: 0 0 34px;
            min-width: 34px;
            visibility: hidden;
        }

        .legend-new {
            display: flex;
            flex-wrap: wrap;
            justify-content: center;
            gap: 16px;
            margin-top: 20px;
            font-size: 12px;
            font-weight: 600;
        }
        .legend-item {
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .legend-color {
            width: 14px;
            height: 14px;
            border-radius: 3px;
            border: 1px solid var(--color-gray-200);
        }

        /* Step 3: Food combo list */
        .countdown-banner {
            background: #fff3cd;
            border: 1px solid #ffeeba;
            color: #856404;
            padding: 12px 16px;
            border-radius: var(--radius-md);
            font-weight: 700;
            font-size: 14px;
            display: flex;
            justify-content: center;
            align-items: center;
            gap: 8px;
        }
        .combo-list-new {
            display: flex;
            flex-direction: column;
            gap: 12px;
        }
        .combo-row-new {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 16px;
            border: 1px solid var(--color-gray-200);
            border-radius: var(--radius-md);
            background: var(--surface);
            gap: 16px;
        }
        .combo-img {
            width: 70px;
            height: 70px;
            background-color: #fff7ed;
            border: 1px solid #ffedd5;
            border-radius: var(--radius-sm);
            background-size: cover;
            background-position: center;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 32px;
            flex-shrink: 0;
        }
        .combo-details {
            flex: 1;
            display: flex;
            flex-direction: column;
            gap: 4px;
        }
        .combo-title {
            font-weight: 700;
            font-size: 14px;
        }
        .combo-desc {
            font-size: 12px;
            color: var(--color-gray-500);
        }
        .combo-price {
            font-weight: 700;
            color: var(--color-primary);
        }
        .qty-controls {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .qty-btn {
            width: 28px;
            height: 28px;
            border-radius: 50%;
            border: 1px solid var(--color-primary);
            background: transparent;
            color: var(--color-primary);
            font-weight: 800;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .qty-val {
            font-weight: 700;
            font-size: 14px;
            width: 20px;
            text-align: center;
        }

        /* Step 4: Payment */
        .payment-option {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 16px;
            border: 1px solid var(--color-gray-200);
            border-radius: var(--radius-md);
            background: var(--surface);
            cursor: pointer;
            margin-bottom: 12px;
        }
        .payment-option.active {
            border-color: var(--color-primary);
            background: var(--surface-soft);
        }

        /* Step 5: Confirm */
        .confirm-wrapper {
            text-align: center;
            padding: 30px;
            border: 2px dashed var(--color-success);
            border-radius: var(--radius-lg);
            background: var(--surface);
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 20px;
        }
        .qr-code-img {
            width: 180px;
            height: 180px;
            border: 1px solid var(--color-gray-200);
            padding: 8px;
            border-radius: var(--radius-md);
        }

        /* Sidebar summary */
        .booking-sidebar-sticky {
            position: sticky;
            top: calc(var(--header-height) + 20px);
            border: 1px solid var(--color-gray-200);
            border-radius: var(--radius-md);
            background: var(--surface);
            overflow: hidden;
            box-shadow: var(--shadow-card);
        }
        .sidebar-progress-orange {
            height: 4px;
            background: var(--color-primary);
            width: 100%;
        }
        .sidebar-body {
            padding: 14px;
            display: flex;
            flex-direction: column;
            gap: 12px;
        }
        .sidebar-film-row {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .sidebar-poster {
            width: 90px;
            height: 55px;
            border-radius: 8px;
            background-size: cover;
            background-position: center;
            background-color: var(--color-gray-100);
            flex-shrink: 0;
        }
        .sidebar-film-info {
            display: flex;
            flex-direction: column;
            gap: 4px;
        }
        .sidebar-film-title {
            font-weight: 700;
            font-size: 15px;
            margin: 0;
        }
        .sidebar-cinema-info {
            font-size: 13px;
            color: var(--ink);
        }
        .sidebar-cinema-info strong {
            display: block;
        }
        .sidebar-divider {
            border-top: 1px dashed var(--color-gray-200);
            margin: 4px 0;
        }
        .sidebar-details-list {
            display: flex;
            flex-direction: column;
            gap: 8px;
            font-size: 13px;
        }
        .detail-item-row {
            display: flex;
            justify-content: space-between;
            gap: 12px;
        }
        .detail-item-val {
            font-weight: 700;
            text-align: right;
        }
        .sidebar-total-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 14px;
        }
        .sidebar-total-price {
            font-size: 20px;
            font-weight: 800;
            color: var(--color-primary);
        }
        .sidebar-actions {
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .btn-continue {
            background: var(--color-primary);
            color: white;
            border: none;
            padding: 12px;
            border-radius: var(--radius-sm);
            font-weight: 700;
            font-size: 14px;
            cursor: pointer;
            text-align: center;
            transition: background 0.2s;
        }
        .btn-continue:hover {
            background: var(--color-primary-dark);
        }
        .btn-back {
            color: var(--color-primary);
            background: transparent;
            border: none;
            cursor: pointer;
            font-weight: 700;
            text-align: center;
            text-decoration: underline;
            padding: 4px;
        }

        @media (max-width: 900px) {
            .booking-layout-new {
                grid-template-columns: minmax(0, 1fr);
            }
            .booking-layout-new > *,
            .booking-left-col,
            .booking-step,
            .booking-step > .panel {
                min-width: 0;
            }
            .booking-sidebar-sticky {
                position: static;
                top: auto;
            }
            .seat-map-wrapper {
                width: 100%;
                max-width: 100%;
            }
            .booking-stepper {
                justify-content: flex-start;
                overflow-x: auto;
                overscroll-behavior-inline: contain;
                scrollbar-width: thin;
            }
            .step-nav-item {
                min-width: 104px;
            }
        }

        @media (max-width: 600px) {
            .booking-stepper {
                padding-inline: 10px;
            }
            .film-select-grid {
                grid-template-columns: repeat(2, minmax(0, 1fr));
                gap: 8px;
            }
            .booking-step > .panel,
            .accordion-content,
            .cinema-card-block {
                padding: 12px;
            }
            .showtime-toolbar {
                align-items: stretch;
                flex-direction: column;
            }
            .cinema-filter-select {
                width: 100%;
            }
            .seat-map-wrapper {
                padding: 12px 8px;
            }
            .legend-new {
                justify-content: flex-start;
            }
        }
    </style>
</head>
<body class="public-page">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="container public-main" data-booking-root 
          data-context-path="${pageContext.request.contextPath}" 
          data-showtime-id="${fn:escapeXml(showtime.id)}" 
          data-base-price="${fn:escapeXml(showtime.basePrice)}"
          data-hold-minutes="${fn:escapeXml(holdMinutes)}"
          data-logged-in="${fn:escapeXml(not empty sessionScope.currentUser)}">
          
        <div class="booking-stepper" data-stepper>
            <span data-step="1" class="step-nav-item">Chọn suất</span>
            <span data-step="2" class="step-nav-item">Chọn ghế</span>
            <span data-step="3" class="step-nav-item">Chọn thức ăn</span>
            <span data-step="4" class="step-nav-item">Thanh toán</span>
            <span data-step="5" class="step-nav-item">Xác nhận</span>
        </div>

        <div class="booking-layout-new">
            <!-- Cột trái: nội dung thao tác -->
            <div class="booking-left-col">
                
                <!-- BƯỚC 1: CHỌN PHIM / RẠP / SUẤT -->
                <div class="booking-step" data-step-id="1">
                    <!-- Khối 1: Chọn vị trí (Đã ẩn theo yêu cầu) -->
                    <div class="accordion-item" id="accordionLoc" style="display:none;">
                        <div class="accordion-header" onclick="toggleAccordion('accordionLoc')">
                            <span>1. Chọn vị trí</span>
                            <span class="chevron">▾</span>
                        </div>
                        <div class="accordion-content">
                            <div class="chips-container" id="locationChips">
                                <c:forEach var="city" items="${cities}" varStatus="status">
                                    <span class="chip <c:if test='${status.first}'>active</c:if>" data-city-id="${fn:escapeXml(city.cityId)}">
                                        ${fn:escapeXml(city.cityName)}
                                    </span>
                                </c:forEach>
                            </div>
                        </div>
                    </div>

                    <!-- Khối 2: Chọn phim -->
                    <div class="accordion-item" id="accordionFilm">
                        <div class="accordion-header" onclick="toggleAccordion('accordionFilm')">
                            <span>2. Chọn phim</span>
                            <span class="chevron">▾</span>
                        </div>
                        <div class="accordion-content">
                            <div class="film-select-grid" id="filmSelectGrid">
                                <c:forEach var="f" items="${films}">
                                    <c:set var="fPoster" value="${cbf:assetUrl(pageContext.request.contextPath, empty f.thumbnail ? '/assets/img/default-film.jpg' : f.thumbnail)}" />
                                    <div class="film-select-card <c:if test='${f.id == showtime.filmId}'>active</c:if>'" 
                                         data-film-id="${fn:escapeXml(f.id)}" 
                                         data-title="${fn:escapeXml(f.title)}" 
                                         data-poster="${fn:escapeXml(fPoster)}" 
                                         data-age="${fn:escapeXml(f.ageRating)}">
                                        <div class="film-select-poster" style="background-image: url('${fn:escapeXml(fPoster)}');"></div>
                                        <div class="film-select-title">${fn:escapeXml(f.title)}</div>
                                    </div>
                                </c:forEach>
                            </div>
                        </div>
                    </div>

                    <!-- Khối 3: Chọn suất -->
                    <div class="accordion-item" id="accordionShowtime">
                        <div class="accordion-header" onclick="toggleAccordion('accordionShowtime')">
                            <span>3. Chọn suất chiếu</span>
                            <span class="chevron">▾</span>
                        </div>
                        <div class="accordion-content">
                            <!-- Thanh Toolbar chọn ngày & lọc rạp -->
                            <div class="showtime-toolbar">
                                <div class="date-chips-wrapper" id="dateChips">
                                    <!-- Dynamic date tabs -->
                                </div>
                                <div id="cinemaFilterContainer">
                                    <select id="cinemaFilterSelect" class="cinema-filter-select">
                                        <option value="0">Tất cả các rạp</option>
                                    </select>
                                </div>
                            </div>
                            
                            <!-- Danh sách suất chiếu phân nhóm theo rạp & phòng chiếu -->
                            <div id="showtimeGroupList">
                                <p class="muted">Vui lòng chọn phim trước để xem lịch chiếu.</p>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- BƯỚC 2: CHỌN GHẾ -->
                <div class="booking-step" data-step-id="2">
                    <div class="panel">
                        <div style="display:flex; justify-content:space-between; align-items:center;">
                            <span class="eyebrow">Bước 2: Chọn ghế</span>
                            <div id="showtimeChipsContainer" class="chips-container">
                                <!-- Chip giờ chiếu đổi suất nhanh -->
                            </div>
                        </div>
                        
                        <div class="ent-screen-wrapper">
                            <div class="ent-screen-arc"></div>
                            <div class="ent-screen-text">MÀN HÌNH</div>
                            <button type="button" id="seatView3dButton" class="seat-view3d-button" disabled>
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="m12 2 8 4.5v11L12 22l-8-4.5v-11L12 2Z"/><path d="m4 6.5 8 4.5 8-4.5M12 11v11"/></svg>
                                Xem ghế 3D
                            </button>
                        </div>

                        <div class="seat-map-wrapper">
                            <div class="seat-grid-container" id="seatGridNew">
                                <!-- Load sơ đồ ghế qua Ajax -->
                            </div>
                        </div>

                        <!-- Chú thích -->
                        <div class="legend-new">
                            <div class="legend-item"><span class="legend-color" style="background:#ffffff; border-color:#7ea3ea;"></span> Ghế đơn</div>
                            <div class="legend-item"><span class="legend-color" style="background:#fffaf0; border-color:var(--color-gold);"></span> Ghế VIP</div>
                            <div class="legend-item"><span class="legend-color" style="background:#ffffff; border-color:var(--color-primary-light); width:30px;"></span> Ghế đôi</div>
                            <div class="legend-item"><span class="legend-color" style="background:#e5e7eb; border-color:#d3d8e2;"></span> Ghế đã bán</div>
                            <div class="legend-item"><span class="legend-color" style="background:#e8f0ff; border-color:#7ea3ea;"></span> Ghế đang giữ</div>
                            <div class="legend-item"><span class="legend-color" style="background:var(--color-primary); border-color:var(--color-primary);"></span> Ghế đang chọn</div>
                        </div>
                    </div>
                </div>

                <!-- BƯỚC 3: CHỌN THỨ ĂN -->
                <div class="booking-step" data-step-id="3">
                    <div class="panel">
                        <%-- Han giu ghe do server tra ve (GET /api/v1/orders/{id}/hold), khong phai dong ho dem doc lap cua trinh duyet. --%>
                        <div class="countdown-banner">
                            ⏱️ <span id="countdownTimer">Đang đồng bộ hạn giữ ghế…</span>
                        </div>
                        <h3 style="margin-top: 20px; margin-bottom: 12px;">Chọn thức ăn đi kèm</h3>
                        <div class="combo-list-new">
                            <c:forEach var="c" items="${combos}">
                                <div class="combo-row-new">
                                    <c:set var="cImg" value="${not empty c.image ? cbf:assetUrl(pageContext.request.contextPath, c.image) : ''}" />
                                    <div class="combo-img" style="<c:if test='${not empty cImg}'>background-image: url('${fn:escapeXml(cImg)}');</c:if>">
                                        <c:if test="${empty cImg}">🍿</c:if>
                                    </div>
                                    <div class="combo-details">
                                        <span class="combo-title">${fn:escapeXml(c.name)}</span>
                                        <span class="combo-desc">${fn:escapeXml(c.description)}</span>
                                        <span class="combo-price">${fn:escapeXml(c.price)} đ</span>
                                    </div>
                                    <div class="qty-controls">
                                        <button type="button" class="qty-btn btn-combo-minus" data-combo-id="${fn:escapeXml(c.id)}" data-price="${fn:escapeXml(c.price)}">-</button>
                                        <span class="qty-val" id="qty-${fn:escapeXml(c.id)}">0</span>
                                        <button type="button" class="qty-btn btn-combo-plus" data-combo-id="${fn:escapeXml(c.id)}" data-price="${fn:escapeXml(c.price)}">+</button>
                                        <input type="hidden" class="combo-input-new" data-combo-id="${fn:escapeXml(c.id)}" data-price="${fn:escapeXml(c.price)}" data-name="${fn:escapeXml(c.name)}" id="input-combo-${fn:escapeXml(c.id)}" value="0">
                                    </div>
                                </div>
                            </c:forEach>
                        </div>
                    </div>
                </div>

                <!-- BƯỚC 4: THANH TOÁN -->
                <div class="booking-step" data-step-id="4">
                    <div class="panel">
                        <%-- Cung mot dong ho voi buoc 3: het han thi nut thanh toan bi khoa ngay tai day. --%>
                        <div class="countdown-banner" style="margin-bottom: 16px;">
                            ⏱️ <span id="countdownTimerPay">Đang đồng bộ hạn giữ ghế…</span>
                        </div>
                        <div style="background: #fff3cd; color: #856404; padding: 10px 14px; border-radius: var(--radius-sm); margin-bottom: 16px; font-weight: 600; font-size: 13px;">
                            ⚠️ CHẾ ĐỘ THANH TOÁN GIẢ LẬP — Hệ thống đang chạy thử nghiệm, không trừ tiền thực tế.
                        </div>
                        <h3>Thông tin thanh toán</h3>
                        
                        <div style="margin-bottom: 20px;">
                            <label style="display:block; font-weight:700; margin-bottom:6px;">Mã khuyến mại (nếu có)</label>
                            <div style="display:flex; gap:10px;">
                                <input type="text" id="promoCodeNew" placeholder="Ví dụ: CINE10" style="padding: 10px; border-radius: var(--radius-sm); border: 1px solid var(--color-gray-300); flex: 1;">
                                <button type="button" class="button" id="applyPromoBtn" style="padding:10px 20px;">Áp dụng</button>
                            </div>
                            <span id="promoMessage" class="muted" style="display:block; margin-top:6px; font-size:12px;"></span>
                        </div>

                        <div>
                            <label style="display:block; font-weight:700; margin-bottom:12px;">Chọn phương thức thanh toán</label>
                            <div class="payment-option active" onclick="selectPayment('card', this)">
                                💳 &nbsp; <strong>Thẻ thanh toán demo</strong> (Thanh toán trực tuyến giả lập)
                            </div>
                            <div class="payment-option" onclick="selectPayment('counter', this)">
                                💵 &nbsp; <strong>Thanh toán tại quầy</strong> (Giữ vé và thanh toán tại rạp)
                            </div>
                            <input type="hidden" id="paymentMethodNew" value="card">
                        </div>
                    </div>
                </div>

                <!-- BƯỚC 5: XÁC NHẬN -->
                <div class="booking-step" data-step-id="5">
                    <div class="confirm-wrapper">
                        <span style="font-size: 40px;">🎉</span>
                        <h2 style="color: var(--color-success); margin: 0;">Đặt vé thành công!</h2>
                        <p class="lead-copy" style="margin: 0;">Cảm ơn bạn đã lựa chọn dịch vụ của CineBook.</p>
                        
                        <div class="sidebar-divider" style="width: 100%;"></div>
                        
                        <div style="text-align: left; width: 100%; display: flex; flex-direction: column; gap: 8px;">
                            <div>Mã vé: <strong id="resTicketCode" style="color: var(--color-primary); font-size: 18px;">CB123456</strong></div>
                            <div>Phim: <strong id="resFilmTitle"></strong></div>
                            <div>Rạp: <span id="resCinemaName"></span></div>
                            <div>Suất chiếu: <span id="resShowtime"></span></div>
                            <div>Ghế chọn: <strong id="resSeats"></strong></div>
                            <div id="resComboRow">Đồ ăn kèm: <span id="resCombos">Không có</span></div>
                            <div>Tổng tiền: <strong id="resTotal" style="color: var(--color-primary);">0 đ</strong></div>
                        </div>

                        <div class="sidebar-divider" style="width: 100%;"></div>

                        <p class="muted" style="font-size:12px;">Vui lòng đưa mã QR bên dưới tại quầy vé để lấy vé cứng vào rạp:</p>
                        <img id="resQrCode" class="qr-code-img" src="" alt="Ticket QR">
                    </div>
                </div>

            </div>

            <!-- Cột phải: tóm tắt đơn hàng sticky -->
            <div class="booking-sidebar-sticky">
                <div class="sidebar-progress-orange"></div>
                <div class="sidebar-body">
                    <!-- Ảnh & Tên Phim -->
                    <div class="sidebar-film-row">
                        <c:set var="stThumb" value="${cbf:assetUrl(pageContext.request.contextPath, empty showtime.thumbnail ? '/assets/img/default-film.jpg' : showtime.thumbnail)}" />
                        <div class="sidebar-poster" id="sideFilmPoster" style="background-image: url('${fn:escapeXml(stThumb)}');"></div>
                        <div class="sidebar-film-info">
                            <h3 class="sidebar-film-title" id="sideFilmTitle">${fn:escapeXml(not empty showtime ? showtime.filmTitle : 'Chưa chọn phim')}</h3>
                            <div style="display:flex; align-items:center; gap:6px; margin-top:4px;">
                                <span id="sideFilmFormatVer" style="font-size:12px; font-weight:700; color:#64748b;">${fn:escapeXml(not empty showtime ? showtime.formatVersionDisplay : '2D Phụ Đề')}</span>
                                <span class="badge" id="sideFilmAge" style="background:var(--color-primary); color:white; font-size:10px; padding:2px 6px; border-radius:3px;">${fn:escapeXml(not empty showtime ? showtime.ageRating : '')}</span>
                            </div>
                        </div>
                    </div>
                    
                    <div class="sidebar-divider"></div>

                    <!-- Thông tin Rạp & Suất chiếu -->
                    <div class="sidebar-cinema-info" id="sideCinemaInfo">
                        <c:choose>
                            <c:when test="${not empty showtime}">
                                <strong>${fn:escapeXml(showtime.cinemaName)}</strong>
                                <span>${fn:escapeXml(showtime.roomName)}</span>
                                <div style="margin-top: 6px; font-weight: 700; color: var(--color-secondary);">
                                    📅 ${fn:escapeXml(showtime.startTimeDisplay)}
                                </div>
                            </c:when>
                            <c:otherwise>
                                <span>Vui lòng chọn rạp và suất chiếu ở bước 1.</span>
                            </c:otherwise>
                        </c:choose>
                    </div>

                    <div class="sidebar-divider"></div>

                    <!-- Danh sách Chi tiết Ghế và Combo -->
                    <div class="sidebar-details-list" id="sideDetailsList">
                        <div class="detail-item-row" id="sideSeatRowPlaceholder">
                            <span>Vé xem phim</span>
                            <span class="detail-item-val">Chưa chọn ghế</span>
                        </div>
                    </div>

                    <div class="sidebar-divider"></div>

                    <!-- Tổng cộng -->
                    <div class="sidebar-total-row">
                        <span>Tổng cộng</span>
                        <strong class="sidebar-total-price" id="sideTotalPrice">0 đ</strong>
                    </div>

                    <!-- Các nút hành động -->
                    <div class="sidebar-actions">
                        <button type="button" class="btn-continue" id="btnSideContinue">Tiếp tục</button>
                        <button type="button" class="btn-back" id="btnSideBack" style="display: none;">Quay lại</button>
                    </div>
                    
                    <div id="sideErrorMessage" class="muted" style="color:var(--color-danger); text-align:center; font-size:12px; margin-top:8px; font-weight:700;"></div>
                </div>
            </div>
        </div>
    </main>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>
    <script src="${pageContext.request.contextPath}/assets/js/main.js" charset="UTF-8"></script>
    
    <!-- JSON data chứa tất cả showtimes -->
    <script id="allShowtimesJson" type="application/json">
        [
            <c:forEach var="st" items="${showtimes}" varStatus="status">
                {
                    "id": ${st.id},
                    "filmId": ${st.filmId},
                    "cinemaId": ${st.cinemaId},
                    "cityId": ${st.cityId},
                    "roomId": ${st.roomId},
                    "cinemaName": "${fn:escapeXml(st.cinemaName)}",
                    "roomName": "${fn:escapeXml(st.roomName)}",
                    "roomStatus": "${st.roomStatus != null ? st.roomStatus : 'active'}",
                    "startTime": "${st.startTime}",
                    "displayTime": "${st.startTimeDisplay}",
                    "onlyTime": "${st.timeDisplay}",
                    "dayOfWeek": "${st.dayOfWeekDisplay}",
                    "dateDisplay": "${st.dateDisplay}",
                    "format": "${fn:escapeXml(st.format)}",
                    "version": "${fn:escapeXml(st.version)}",
                    "formatVersion": "${fn:escapeXml(st.formatVersionDisplay)}",
                    "roomFormatLabel": "${fn:escapeXml(st.roomFormatLabel)}",
                    "basePrice": ${st.basePrice}
                }${not status.last ? ',' : ''}
            </c:forEach>
        ]
    </script>
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            document.querySelectorAll('.btn-combo-minus').forEach(function(btn) {
                btn.addEventListener('click', function() {
                    var id = this.getAttribute('data-combo-id');
                    var price = parseFloat(this.getAttribute('data-price') || 0);
                    if (typeof adjustCombo === 'function') adjustCombo(id, -1, price);
                });
            });
            document.querySelectorAll('.btn-combo-plus').forEach(function(btn) {
                btn.addEventListener('click', function() {
                    var id = this.getAttribute('data-combo-id');
                    var price = parseFloat(this.getAttribute('data-price') || 0);
                    if (typeof adjustCombo === 'function') adjustCombo(id, 1, price);
                });
            });
        });
    </script>
    <script src="${pageContext.request.contextPath}/assets/js/cinema-seat-3d.js?v=20260810c" charset="UTF-8"></script>
    <script src="${pageContext.request.contextPath}/assets/js/seat-map.js?v=20260810a" charset="UTF-8"></script>
</body>
</html>
