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
    <title>Quản lý suất chiếu - CineBook Admin</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805f">
    <style>
        .showtimes-page-container {
            padding-bottom: 60px;
        }
        
        .showtime-top-bar {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            gap: 16px;
            margin-bottom: 24px;
        }
        .showtime-top-bar h1 {
            font-size: 26px;
            font-weight: 800;
            color: #0f172a;
            margin: 0 0 6px;
            letter-spacing: -0.02em;
        }
        .showtime-top-bar p {
            margin: 0;
            color: #64748b;
            font-size: 13px;
        }

        .btn-create-showtime {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            padding: 10px 22px;
            border-radius: 9px;
            background: #6d28d9;
            color: #ffffff !important;
            font-size: 13px;
            font-weight: 600;
            text-decoration: none;
            transition: background-color 0.15s ease;
            box-shadow: 0 2px 6px rgba(109, 40, 217, 0.2);
            white-space: nowrap;
        }
        .btn-create-showtime:hover {
            background: #5b21b6;
            color: #ffffff !important;
        }

        /* 1. CONTROL TOOLBAR CARD */
        .showtime-controls-card {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 14px;
            padding: 14px 20px;
            margin-bottom: 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 16px;
            flex-wrap: wrap;
            box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
        }
        .showtime-controls-left {
            display: flex;
            align-items: center;
            gap: 20px;
            flex-wrap: wrap;
        }
        .control-field {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .control-field label {
            font-size: 13px;
            font-weight: 600;
            color: #475569;
            white-space: nowrap;
        }
        .control-field select {
            height: 38px;
            padding: 0 32px 0 12px;
            border: 1px solid #cbd5e1;
            border-radius: 8px;
            background: #ffffff;
            color: #0f172a;
            font-size: 13px;
            font-weight: 500;
            outline: none;
            cursor: pointer;
        }
        .showtime-search-wrap {
            position: relative;
            display: inline-flex;
            align-items: center;
            width: 280px;
        }
        .showtime-search-wrap svg {
            position: absolute;
            left: 12px;
            top: 50%;
            transform: translateY(-50%);
            pointer-events: none;
            color: #94a3b8;
            z-index: 2;
        }
        .showtime-search-wrap input {
            width: 100%;
            height: 38px;
            line-height: 24px;
            padding: 7px 14px 7px 36px !important;
            border: 1px solid #cbd5e1;
            border-radius: 9px;
            background: #ffffff;
            color: #0f172a;
            font-size: 13px;
            outline: none;
            box-sizing: border-box;
            transition: all 0.15s ease;
        }
        .showtime-search-wrap input:focus {
            border-color: #6d28d9;
            box-shadow: 0 0 0 3px rgba(109, 40, 217, 0.1);
        }

        /* 2. DATE NAVIGATION BAR */
        .showtime-date-nav {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-bottom: 24px;
        }
        .date-nav-arrow {
            width: 36px;
            height: 50px;
            border: 1px solid #e2e8f0;
            border-radius: 10px;
            background: #ffffff;
            color: #64748b;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: all 0.15s ease;
            flex-shrink: 0;
        }
        .date-nav-arrow:hover {
            border-color: #cbd5e1;
            background: #f8fafc;
            color: #0f172a;
        }
        .date-tabs-container {
            display: flex;
            gap: 10px;
            overflow-x: auto;
            scrollbar-width: none;
            flex: 1;
            padding: 2px 0;
        }
        .date-tabs-container::-webkit-scrollbar {
            display: none;
        }
        .date-tab-btn {
            flex: 1;
            min-width: 92px;
            height: 50px;
            border: 1px solid #e2e8f0;
            border-radius: 10px;
            background: #ffffff;
            color: #475569;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            transition: all 0.15s ease;
            padding: 4px;
            position: relative;
        }
        .date-tab-btn:hover {
            border-color: #cbd5e1;
            background: #f8fafc;
        }
        .date-tab-btn.is-active {
            background: #6d28d9;
            border-color: #6d28d9;
            color: #ffffff;
            box-shadow: 0 4px 12px rgba(109, 40, 217, 0.25);
        }
        .date-tab-day {
            font-size: 11px;
            font-weight: 600;
            text-transform: uppercase;
            line-height: 1.2;
        }
        .date-tab-val {
            font-size: 14px;
            font-weight: 700;
            line-height: 1.3;
        }
        .date-tab-btn.is-active .date-tab-day,
        .date-tab-btn.is-active .date-tab-val {
            color: #ffffff;
        }
        .date-has-showtimes-dot {
            width: 5px;
            height: 5px;
            border-radius: 50%;
            background: #6d28d9;
            position: absolute;
            bottom: 4px;
        }
        .date-tab-btn.is-active .date-has-showtimes-dot {
            background: #ffffff;
        }

        /* 3. SUMMARY STATS GRID */
        .showtime-stats-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 20px;
            margin-bottom: 28px;
        }
        .showtime-stat-card {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 14px;
            padding: 20px 24px;
            display: flex;
            align-items: center;
            gap: 16px;
            box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
        }
        .stat-icon-box {
            width: 44px;
            height: 44px;
            border-radius: 12px;
            background: #f3e8ff;
            color: #6d28d9;
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
        }
        .stat-info label {
            display: block;
            color: #64748b;
            font-size: 12px;
            font-weight: 500;
            margin-bottom: 4px;
        }
        .stat-info strong {
            display: block;
            color: #6d28d9;
            font-size: 22px;
            font-weight: 800;
            line-height: 1.1;
        }

        /* 4. MAIN SHOWTIMES PANEL */
        .showtimes-main-panel {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 16px;
            padding: 24px;
            box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
        }
        .showtimes-panel-header {
            font-size: 18px;
            font-weight: 800;
            color: #0f172a;
            margin-bottom: 20px;
            padding-bottom: 12px;
            border-bottom: 1px solid #f1f5f9;
        }

        /* ROOM GROUP CARD */
        .room-group-card {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 12px;
            margin-bottom: 20px;
            overflow: hidden;
        }
        .room-group-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 14px 20px;
            background: #f8fafc;
            border-bottom: 1px solid #e2e8f0;
            cursor: pointer;
            user-select: none;
        }
        .room-group-title {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .room-toggle-icon {
            color: #64748b;
            font-size: 12px;
            transition: transform 0.2s ease;
        }
        .room-group-card.is-collapsed .room-toggle-icon {
            transform: rotate(-90deg);
        }
        .room-group-card.is-collapsed .room-table-wrap {
            display: none;
        }
        .room-name-text {
            font-size: 14px;
            font-weight: 700;
            color: #0f172a;
        }
        .room-count-badge {
            padding: 2px 10px;
            border-radius: 999px;
            background: #f3e8ff;
            color: #6b21a8;
            font-size: 12px;
            font-weight: 600;
        }
        .room-count-badge.is-zero {
            background: #f1f5f9;
            color: #64748b;
        }
        .btn-toggle-room {
            background: #ffffff;
            border: 1px solid #cbd5e1;
            border-radius: 6px;
            padding: 4px 12px;
            color: #475569;
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
        }
        .btn-toggle-room:hover {
            background: #f1f5f9;
            color: #0f172a;
        }

        /* SHOWTIME TABLE INSIDE ROOM */
        .showtime-table-wrap {
            overflow-x: auto;
        }
        .showtime-table {
            width: 100%;
            border-collapse: collapse;
        }
        .showtime-table th,
        .showtime-table td {
            padding: 14px 20px;
            text-align: left;
            vertical-align: middle;
            border-bottom: 1px solid #f1f5f9;
        }
        .showtime-table th {
            background: #ffffff;
            color: #64748b;
            font-size: 12px;
            font-weight: 600;
            letter-spacing: 0.01em;
        }
        .showtime-table td {
            font-size: 13px;
            color: #334155;
        }
        /* BALANCED COLUMN WIDTH DISTRIBUTION */
        .showtime-table th:nth-child(1) { width: 12%; }
        .showtime-table th:nth-child(2) { width: 28%; }
        .showtime-table th:nth-child(3) { width: 12%; }
        .showtime-table th:nth-child(4) { width: 16%; }
        .showtime-table th:nth-child(5) { width: 16%; }
        .showtime-table th:nth-child(6) { width: 16%; }

        .time-cell-bold {
            font-size: 15px;
            font-weight: 700;
            color: #0f172a;
        }
        .film-cell-wrap {
            display: flex;
            align-items: center;
            gap: 14px;
        }
        /* FIXED UNIFORM LANDSCAPE FRAME */
        .film-poster-thumb {
            width: 72px;
            height: 42px;
            aspect-ratio: 16 / 9;
            border-radius: 6px;
            object-fit: cover;
            background: #f1f5f9;
            border: 1px solid #e2e8f0;
            flex-shrink: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            overflow: hidden;
            box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
        }
        .film-poster-empty {
            background: #f1f5f9;
            color: #94a3b8;
        }
        .film-title-text {
            font-weight: 600;
            color: #0f172a;
            font-size: 14px;
            line-height: 1.3;
        }
        .film-rating-tag {
            display: inline-block;
            margin-left: 6px;
            padding: 1px 6px;
            border-radius: 4px;
            background: #f1f5f9;
            color: #475569;
            font-size: 11px;
            font-weight: 700;
        }

        .seats-text-formatted {
            font-weight: 600;
            color: #0f172a;
            font-size: 13px;
        }

        /* STATUS BADGES */
        .st-status-badge {
            display: inline-flex;
            align-items: center;
            padding: 3px 10px;
            border-radius: 6px;
            font-size: 12px;
            font-weight: 600;
            white-space: nowrap;
        }
        .st-status-onsale {
            background-color: #dcfce7;
            color: #15803d;
        }
        .st-status-upcoming {
            background-color: #dbeafe;
            color: #1d4ed8;
        }
        .st-status-full {
            background-color: #fee2e2;
            color: #dc2626;
        }
        .st-status-suspended {
            background-color: #fef3c7;
            color: #92400e;
        }
        .st-status-deleted {
            background-color: #f1f5f9;
            color: #475569;
        }

        /* ACTION LINKS IN TABLE */
        .showtime-actions {
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .st-action-btn {
            background: transparent;
            color: #6b21a8;
            font-size: 13px;
            font-weight: 600;
            border: 0;
            padding: 0;
            cursor: pointer;
            text-decoration: none;
            transition: color 0.15s ease;
        }
        .st-action-btn:hover {
            color: #581c87;
            text-decoration: underline;
        }
        .st-action-divider {
            color: #cbd5e1;
            font-size: 12px;
        }

        .no-showtimes-msg {
            padding: 20px;
            text-align: center;
            color: #94a3b8;
            font-size: 13px;
            font-weight: 500;
            background: #ffffff;
        }

        /* MODAL POPUP */
        .modal-backdrop {
            position: fixed;
            top: 0; left: 0; width: 100vw; height: 100vh;
            background: rgba(15, 23, 42, 0.6);
            backdrop-filter: blur(4px);
            display: flex;
            justify-content: center;
            align-items: center;
            z-index: 9999;
        }
        .modal-content {
            background: #ffffff;
            border-radius: 16px;
            padding: 28px;
            width: 100%;
            max-width: 480px;
            box-shadow: 0 20px 40px rgba(0,0,0,0.2);
        }

        @media (max-width: 900px) {
            .showtime-stats-grid {
                grid-template-columns: 1fr;
            }
            .showtime-controls-card {
                flex-direction: column;
                align-items: stretch;
            }
            .showtime-search-wrap {
                width: 100%;
            }
        }
    </style>
</head>
<body class="admin-body">
    <div class="dashboard">
        <%@ include file="/WEB-INF/views/admin/sidebar.jspf" %>
        <main class="dashboard-main">
            <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
            <div class="dashboard-content showtimes-page-container">
                <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

                <!-- HEADER TOP BAR -->
                <div class="showtime-top-bar">
                    <div>
                        <h1>Quản lý suất chiếu</h1>
                        <p>Theo dõi lịch chiếu, quản lý theo ngày, rạp và nâng chỉnh thông tin suất chiếu.</p>
                    </div>
                    <div>
                        <a href="${pageContext.request.contextPath}/admin/showtimes?action=create" class="btn-create-showtime">
                            + Tạo lịch chiếu mới
                        </a>
                    </div>
                </div>

                <!-- 1. TOOLBAR BỘ LỌC & TÌM KIẾM -->
                <div class="showtime-controls-card">
                    <div class="showtime-controls-left">
                        <div class="control-field">
                            <label for="cinemaFilter">Cụm rạp</label>
                            <select id="cinemaFilter" onchange="onFilterChange()">
                                <option value="ALL">Tất cả cụm rạp</option>
                                <c:forEach var="cinema" items="${cinemas}">
                                    <option value="${fn:escapeXml(cinema.id)}">${fn:escapeXml(cinema.name)}</option>
                                </c:forEach>
                            </select>
                        </div>
                        <div class="control-field">
                            <label for="filmFilterSelect">Phim</label>
                            <select id="filmFilterSelect" onchange="onFilterChange()">
                                <option value="ALL">Tất cả phim</option>
                                <c:forEach var="film" items="${films}">
                                    <option value="${fn:escapeXml(film.id)}">${fn:escapeXml(film.title)}</option>
                                </c:forEach>
                            </select>
                        </div>
                    </div>
                    <div class="showtime-search-wrap">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="11" cy="11" r="8"></circle>
                            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                        </svg>
                        <input type="search" id="showtimeSearchInput" placeholder="Tìm theo giờ chiếu, phim..." aria-label="Tìm kiếm suất chiếu" oninput="onFilterChange()">
                    </div>
                </div>

                <!-- 2. DATE NAVIGATION BAR -->
                <div class="showtime-date-nav">
                    <button type="button" class="date-nav-arrow" id="prevDateBtn" aria-label="Ngày trước">&lt;</button>
                    <div class="date-tabs-container" id="dateTabsNav">
                        <!-- Populated dynamically via JavaScript -->
                    </div>
                    <button type="button" class="date-nav-arrow" id="nextDateBtn" aria-label="Ngày sau">&gt;</button>
                </div>

                <!-- 3. SUMMARY STATS CARDS (DYNAMIC DỮ LIỆU THẬT 100%) -->
                <div class="showtime-stats-grid">
                    <div class="showtime-stat-card">
                        <div class="stat-icon-box">
                            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                                <line x1="16" y1="2" x2="16" y2="6"></line>
                                <line x1="8" y1="2" x2="8" y2="6"></line>
                                <line x1="3" y1="10" x2="21" y2="10"></line>
                            </svg>
                        </div>
                        <div class="stat-info">
                            <label>Tổng suất chiếu trong ngày</label>
                            <strong id="statTotalShowtimes">0</strong>
                        </div>
                    </div>
                    <div class="showtime-stat-card">
                        <div class="stat-icon-box">
                            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <rect x="2" y="2" width="20" height="20" rx="2.18" ry="2.18"></rect>
                                <line x1="7" y1="2" x2="7" y2="22"></line>
                                <line x1="17" y1="2" x2="17" y2="22"></line>
                                <line x1="2" y1="12" x2="22" y2="12"></line>
                                <line x1="2" y1="7" x2="7" y2="7"></line>
                                <line x1="2" y1="17" x2="7" y2="17"></line>
                                <line x1="17" y1="17" x2="22" y2="17"></line>
                                <line x1="17" y1="7" x2="22" y2="7"></line>
                            </svg>
                        </div>
                        <div class="stat-info">
                            <label>Số phim đang chiếu</label>
                            <strong id="statTotalFilms">0</strong>
                        </div>
                    </div>
                    <div class="showtime-stat-card">
                        <div class="stat-icon-box">
                            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
                                <line x1="8" y1="21" x2="16" y2="21"></line>
                                <line x1="12" y1="17" x2="12" y2="21"></line>
                            </svg>
                        </div>
                        <div class="stat-info">
                            <label>Số phòng đang hoạt động</label>
                            <strong id="statTotalRooms">0</strong>
                        </div>
                    </div>
                </div>

                <!-- 4. MAIN SHOWTIMES PANEL (DANH SÁCH SUẤT CHIẾU) -->
                <div class="showtimes-main-panel">
                    <div class="showtimes-panel-header">
                        Danh sách suất chiếu
                    </div>

                    <div id="roomShowtimesList">
                        <!-- Populated dynamically via JavaScript -->
                    </div>
                    <div id="showtimesDataError" role="alert" style="display:none;padding:16px;border:1px solid #fca5a5;background:#fef2f2;color:#991b1b;border-radius:8px;"></div>
                </div>

            </div>
        </main>
    </div>

    <!-- QUICK EDIT / DELETE MODAL -->
    <div id="showtimeEditModal" class="modal-backdrop" style="display:none;" aria-hidden="true">
        <div class="modal-content" role="dialog" aria-modal="true" aria-labelledby="modalShowtimeId" tabindex="-1">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; border-bottom:1px solid #e2e8f0; padding-bottom:10px;">
                <h3 style="margin:0; font-size:1.2rem; font-weight:800; color:#0f172a;" id="modalShowtimeId">Suất chiếu</h3>
                <button type="button" id="modalClose" onclick="closeModal()" aria-label="Đóng" style="background:none; border:none; font-size:1.5rem; cursor:pointer; color:#64748b;">&times;</button>
            </div>
            
            <form method="post" action="${pageContext.request.contextPath}/admin/showtimes" id="modalForm">
                <cb:csrf/>
                <input type="hidden" name="id" id="modalInputId">
                <div style="margin-bottom:12px; font-size:0.95rem; color:#475569;">
                    <div style="margin-bottom:4px;">Phim: <strong id="modalFilmTitle" style="color:#0f172a;"></strong></div>
                    <div style="margin-bottom:4px;">Rạp & Phòng: <strong id="modalCinemaRoom" style="color:#0f172a;"></strong></div>
                </div>
                <div id="showtimeDeleteLive" aria-live="polite" style="padding:10px;background:#f8fafc;border-radius:8px;margin-bottom:12px">Đang kiểm tra trạng thái…</div>
                <div id="showtimeDeleteImpact" class="muted" style="font-size:.86rem;margin-bottom:12px"></div>

                <div class="form-group" style="margin-bottom:12px;">
                    <label class="form-label" style="font-weight:700;">Thời gian bắt đầu</label>
                    <input type="datetime-local" name="startTime" id="modalStartTime" class="form-input" required>
                </div>

                <div class="form-group" style="margin-bottom:12px;">
                    <label class="form-label" style="font-weight:700;">Thời gian kết thúc</label>
                    <input type="datetime-local" name="endTime" id="modalEndTime" class="form-input" required>
                </div>

                <div class="form-group" style="margin-bottom:20px;">
                    <label class="form-label" style="font-weight:700;">Giá vé cơ bản (VNĐ)</label>
                    <input type="number" min="0" step="1000" name="basePrice" id="modalBasePrice" class="form-input" required>
                </div>

                <div style="display:flex; justify-content:space-between; align-items:center;">
                    <div style="display:flex;gap:6px;flex-wrap:wrap">
                        <button type="submit" name="action" value="requestDelete" id="requestDeleteButton" class="button danger" style="padding:8px 16px;">Ngưng bán để xóa</button>
                        <button type="submit" name="action" value="resumeSale" id="resumeSaleButton" class="button secondary" style="padding:8px 16px;display:none">Mở bán lại</button>
                        <button type="submit" name="action" value="confirmDelete" id="confirmDeleteButton" class="button danger" style="padding:8px 16px;display:none" disabled>Xác nhận xóa</button>
                    </div>
                    <div style="display:flex; gap:8px;">
                        <button type="button" onclick="closeModal()" class="button secondary" style="padding:8px 16px;">Hủy</button>
                        <button type="submit" class="button primary" style="padding:8px 20px;">Lưu thay đổi</button>
                    </div>
                </div>
            </form>
        </div>
    </div>

    <!-- DATA & DYNAMIC SCRIPT -->
    <script id="allShowtimesJson" type="application/json"><c:out value="${showtimesJson}" escapeXml="false"/></script>
    <script id="allRoomsJson" type="application/json">
    [
        <c:forEach var="r" items="${rooms}" varStatus="status">
            {
                "id": ${r.id},
                "cinemaId": ${r.cinemaId},
                "cinemaName": "${fn:escapeXml(r.cinemaName)}",
                "name": "${fn:escapeXml(r.name)}"
            }<c:if test="${not status.last}">,</c:if>
        </c:forEach>
    ]
    </script>
    <div id="showtimePageContext" hidden data-business-date="${fn:escapeXml(businessDate)}" data-focus-showtime-id="${fn:escapeXml(focusShowtimeId)}"></div>
    
    <!-- FILM METADATA MAP FOR RENDER LAYER -->
    <script>
        var filmMap = {};
        <c:forEach var="film" items="${films}">
            <c:choose>
                <c:when test="${not empty film.banner}">
                    <c:set var="fThumb" value="${cbf:assetUrl(pageContext.request.contextPath, film.banner)}" />
                </c:when>
                <c:when test="${not empty film.thumbnail}">
                    <c:set var="fThumb" value="${cbf:assetUrl(pageContext.request.contextPath, film.thumbnail)}" />
                </c:when>
                <c:otherwise>
                    <c:set var="fThumb" value="" />
                </c:otherwise>
            </c:choose>
            filmMap['${fn:escapeXml(film.id)}'] = {
                id: '${fn:escapeXml(film.id)}',
                title: '${fn:escapeXml(film.title)}',
                poster: '${fn:escapeXml(fThumb)}',
                rating: '${empty film.ageRating ? "—" : fn:escapeXml(film.ageRating)}'
            };
        </c:forEach>

        var allShowtimes = [];
        var allRooms = [];
        var showtimesLoadError = null;
        try {
            allShowtimes = JSON.parse(document.getElementById('allShowtimesJson').textContent || '[]');
            allRooms = JSON.parse(document.getElementById('allRoomsJson').textContent || '[]');
        } catch (error) {
            showtimesLoadError = error;
        }

        var selectedDateStr = "";
        var pageContextData = document.getElementById('showtimePageContext');
        var businessDate = pageContextData.getAttribute('data-business-date');
        var focusShowtimeId = parseInt(pageContextData.getAttribute('data-focus-showtime-id') || '0', 10);
        var focusShowtime = allShowtimes.find(function(st) { return st.id === focusShowtimeId; }) || null;

        window.addEventListener('DOMContentLoaded', function() {
            if (showtimesLoadError) {
                var errorBox = document.getElementById('showtimesDataError');
                errorBox.style.display = 'block';
                errorBox.textContent = 'Không thể đọc dữ liệu suất chiếu. Vui lòng tải lại trang hoặc liên hệ quản trị hệ thống.';
                return;
            }
            renderDateTabs();
            if (focusShowtime) {
                var cinemaFilter = document.getElementById('cinemaFilter');
                if (cinemaFilter) cinemaFilter.value = String(focusShowtime.cinemaId);
                var filmFilterSelect = document.getElementById('filmFilterSelect');
                if (filmFilterSelect) filmFilterSelect.value = String(focusShowtime.filmId);
            }
            renderShowtimesList();
            if (focusShowtimeId > 0) {
                var pill = document.querySelector('[data-showtime-id="' + focusShowtimeId + '"]');
                if (pill) {
                    pill.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
            }

            document.getElementById('prevDateBtn')?.addEventListener('click', function() {
                var container = document.getElementById('dateTabsNav');
                container.scrollBy({ left: -200, behavior: 'smooth' });
            });
            document.getElementById('nextDateBtn')?.addEventListener('click', function() {
                var container = document.getElementById('dateTabsNav');
                container.scrollBy({ left: 200, behavior: 'smooth' });
            });
        });

        function renderDateTabs() {
            var tabsContainer = document.getElementById('dateTabsNav');
            tabsContainer.innerHTML = '';

            var daysOfWeek = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
            var businessParts = businessDate.split('-').map(Number);
            var windowStart = new Date(businessParts[0], businessParts[1] - 1, businessParts[2]);
            var dates = [];
            for (var offset = 0; offset < 10; offset += 1) {
                var windowDate = new Date(windowStart.getFullYear(), windowStart.getMonth(), windowStart.getDate() + offset);
                var year = String(windowDate.getFullYear());
                var month = String(windowDate.getMonth() + 1).padStart(2, '0');
                var day = String(windowDate.getDate()).padStart(2, '0');
                dates.push(year + '-' + month + '-' + day);
            }

            var focusDate = focusShowtime && focusShowtime.startTime ? focusShowtime.startTime.substring(0, 10) : null;
            var defaultDate = focusDate && dates.includes(focusDate) ? focusDate : businessDate;
            selectedDateStr = defaultDate;

            dates.forEach(function(dateISO) {
                var parts = dateISO.split('-');
                var d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
                var dayName = daysOfWeek[d.getDay()];
                var mm = parts[1];
                var dd = parts[2];
                var displayVal = dd + '/' + mm;

                // Check if this date has any showtimes
                var hasShowtimes = allShowtimes.some(function(st) {
                    return st.startTime && st.startTime.substring(0, 10) === dateISO;
                });

                var btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'date-tab-btn' + (dateISO === defaultDate ? ' is-active' : '');
                btn.setAttribute('data-date', dateISO);
                btn.onclick = (function(iso, element) {
                    return function() {
                        document.querySelectorAll('.date-tab-btn').forEach(function(b) { b.classList.remove('is-active'); });
                        element.classList.add('is-active');
                        selectedDateStr = iso;
                        renderShowtimesList();
                    };
                })(dateISO, btn);

                var dotHtml = hasShowtimes ? '<span class="date-has-showtimes-dot"></span>' : '';
                btn.innerHTML = '<span class="date-tab-day">' + dayName + '</span><span class="date-tab-val">' + displayVal + '</span>' + dotHtml;
                tabsContainer.appendChild(btn);
            });
        }

        function onFilterChange() {
            renderShowtimesList();
        }

        function renderShowtimesList() {
            var container = document.getElementById('roomShowtimesList');
            container.innerHTML = '';

            var selectedCinemaFilter = document.getElementById('cinemaFilter').value;
            var selectedFilmFilter = document.getElementById('filmFilterSelect').value;
            var searchQuery = (document.getElementById('showtimeSearchInput').value || '').trim().toLowerCase();

            // 1. Filter showtimes matching current Date
            var filteredDateItems = allShowtimes.filter(function(st) {
                var stDateISO = (st.startTime && st.startTime.length >= 10) ? st.startTime.substring(0, 10) : '';
                return stDateISO === selectedDateStr;
            });

            // Calculate dynamic 100% real metrics for the selected date
            var uniqueFilmsOnDate = {};
            var uniqueRoomsOnDate = {};
            filteredDateItems.forEach(function(st) {
                if (st.filmId) uniqueFilmsOnDate[st.filmId] = true;
                if (st.cinemaId && st.roomId) uniqueRoomsOnDate[st.cinemaId + '_' + st.roomId] = true;
            });

            document.getElementById('statTotalShowtimes').textContent = filteredDateItems.length;
            document.getElementById('statTotalFilms').textContent = Object.keys(uniqueFilmsOnDate).length;
            document.getElementById('statTotalRooms').textContent = Object.keys(uniqueRoomsOnDate).length;

            // 2. Filter showtimes further by cinema, film, and search
            var filteredShowtimes = filteredDateItems.filter(function(st) {
                var matchesCinema = (selectedCinemaFilter === "ALL" || String(st.cinemaId) === selectedCinemaFilter);
                var matchesFilm = (selectedFilmFilter === "ALL" || String(st.filmId) === selectedFilmFilter);
                var matchesSearch = true;
                if (searchQuery) {
                    var timeOnly = (st.startTime && st.startTime.length >= 16) ? st.startTime.substring(11, 16) : '';
                    var titleText = (st.filmTitle || '').toLowerCase();
                    var roomText = (st.roomName || '').toLowerCase();
                    var cinemaText = (st.cinemaName || '').toLowerCase();
                    matchesSearch = timeOnly.indexOf(searchQuery) !== -1 ||
                                    titleText.indexOf(searchQuery) !== -1 ||
                                    roomText.indexOf(searchQuery) !== -1 ||
                                    cinemaText.indexOf(searchQuery) !== -1;
                }
                return matchesCinema && matchesFilm && matchesSearch;
            });

            // 3. Build a list of all rooms that match selected cinema filter
            var targetRooms = allRooms.filter(function(r) {
                return selectedCinemaFilter === "ALL" || String(r.cinemaId) === selectedCinemaFilter;
            });

            // Group filtered showtimes by room key (cinemaId_roomId)
            var showtimesByRoom = {};
            filteredShowtimes.forEach(function(st) {
                var key = st.cinemaId + '_' + st.roomId;
                if (!showtimesByRoom[key]) {
                    showtimesByRoom[key] = [];
                }
                showtimesByRoom[key].push(st);
            });

            // If we have rooms from system data, render all rooms!
            if (targetRooms.length > 0) {
                targetRooms.forEach(function(r) {
                    var roomKey = r.cinemaId + '_' + r.id;
                    var roomName = r.cinemaName + ' - ' + r.name;
                    var showtimesInRoom = showtimesByRoom[roomKey] || [];
                    renderRoomCard(container, roomName, showtimesInRoom);
                });
            } else {
                // Fallback if allRooms is empty: group from filteredShowtimes
                var groups = {};
                filteredShowtimes.forEach(function(st) {
                    var roomKey = st.cinemaName + ' - ' + st.roomName;
                    if (!groups[roomKey]) groups[roomKey] = [];
                    groups[roomKey].push(st);
                });
                for (var rName in groups) {
                    renderRoomCard(container, rName, groups[rName]);
                }
            }
        }

        function renderRoomCard(container, roomName, showtimesInRoom) {
            var roomCard = document.createElement('div');
            var isZero = showtimesInRoom.length === 0;
            roomCard.className = 'room-group-card' + (isZero ? ' is-collapsed' : '');

            // Room Group Header
            var headerDiv = document.createElement('div');
            headerDiv.className = 'room-group-header';
            headerDiv.onclick = function() {
                roomCard.classList.toggle('is-collapsed');
                var btn = headerDiv.querySelector('.btn-toggle-room');
                if (btn) btn.textContent = roomCard.classList.contains('is-collapsed') ? 'Mở rộng' : 'Thu gọn';
            };

            var titleDiv = document.createElement('div');
            titleDiv.className = 'room-group-title';
            titleDiv.innerHTML = '<span class="room-toggle-icon">▼</span>' +
                '<span class="room-name-text">' + roomName + '</span>' +
                '<span class="room-count-badge ' + (isZero ? 'is-zero' : '') + '">' + showtimesInRoom.length + ' suất chiếu</span>';

            var toggleBtn = document.createElement('button');
            toggleBtn.type = 'button';
            toggleBtn.className = 'btn-toggle-room';
            toggleBtn.textContent = isZero ? 'Mở rộng' : 'Thu gọn';

            headerDiv.appendChild(titleDiv);
            headerDiv.appendChild(toggleBtn);
            roomCard.appendChild(headerDiv);

            // Room Content (Table or Empty Message)
            var tableWrap = document.createElement('div');
            tableWrap.className = 'room-table-wrap showtime-table-wrap';

            if (isZero) {
                tableWrap.innerHTML = '<div class="no-showtimes-msg">Không có suất chiếu nào ở đây</div>';
            } else {
                var table = document.createElement('table');
                table.className = 'showtime-table';
                table.innerHTML = '<thead><tr>' +
                    '<th scope="col">Giờ chiếu</th>' +
                    '<th scope="col">Phim</th>' +
                    '<th scope="col">Định dạng</th>' +
                    '<th scope="col">Tình trạng</th>' +
                    '<th scope="col">Ghế trống</th>' +
                    '<th scope="col">Thao tác</th>' +
                    '</tr></thead>';

                var tbody = document.createElement('tbody');
                showtimesInRoom.forEach(function(st) {
                    var tr = document.createElement('tr');
                    tr.setAttribute('data-showtime-id', st.id);
                    var timeOnly = (st.startTime && st.startTime.length >= 16) ? st.startTime.substring(11, 16) : '00:00';
                    var meta = filmMap[String(st.filmId)] || { title: st.filmTitle || '', poster: '', rating: '—' };
                    
                    // FIXED POSTER FRAME WITH SVG FALLBACK
                    var posterImg = meta.poster ? 
                        '<img src="' + meta.poster + '" class="film-poster-thumb" alt="' + meta.title + '" onerror="this.onerror=null;this.className=\'film-poster-thumb film-poster-empty\';">' : 
                        '<div class="film-poster-thumb film-poster-empty"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="20" height="20" rx="2.18" ry="2.18"/><line x1="7" y1="2" x2="7" y2="22"/><line x1="17" y1="2" x2="17" y2="22"/></svg></div>';
                    
                    var ratingTag = meta.rating ? '<span class="film-rating-tag">' + meta.rating + '</span>' : '';

                    var saleStatus = st.saleStatus || 'ON_SALE';
                    var statusClass = 'st-status-onsale';
                    var statusLabel = 'Đang bán';
                    if (saleStatus === 'SUSPENDED') {
                        statusClass = 'st-status-suspended';
                        statusLabel = 'Ngừng bán';
                    } else if (saleStatus === 'DELETED') {
                        statusClass = 'st-status-deleted';
                        statusLabel = 'Đã xóa';
                    } else {
                        var nowISO = new Date().toISOString();
                        if (st.startTime && st.startTime > nowISO) {
                            statusClass = 'st-status-onsale';
                            statusLabel = 'Đang bán';
                        }
                    }

                    // FORMAT SEATS: EXACT RATIO & PERCENTAGE (e.g. 12/12 100%)
                    var total = (st.totalSeats && st.totalSeats > 0) ? st.totalSeats : 12;
                    var avail = (st.availableSeats !== undefined && st.availableSeats >= 0) ? st.availableSeats : total;
                    var pct = Math.round((avail / total) * 100);
                    var seatsText = avail + '/' + total + ' ' + pct + '%';

                    tr.innerHTML = '<td><span class="time-cell-bold">' + timeOnly + '</span></td>' +
                        '<td><div class="film-cell-wrap">' + posterImg + '<div><span class="film-title-text">' + meta.title + '</span>' + ratingTag + '</div></div></td>' +
                        '<td><span style="font-weight:600;color:#475569;">2D</span></td>' +
                        '<td><span class="st-status-badge ' + statusClass + '">' + statusLabel + '</span></td>' +
                        '<td><span class="seats-text-formatted">' + seatsText + '</span></td>' +
                        '<td><div class="showtime-actions">' +
                            '<button type="button" class="st-action-btn btn-view-st">Xem</button>' +
                            '<span class="st-action-divider">|</span>' +
                            '<button type="button" class="st-action-btn btn-edit-st">Sửa</button>' +
                            '<span class="st-action-divider">|</span>' +
                            '<button type="button" class="st-action-btn btn-hide-st">Ẩn</button>' +
                        '</div></td>';

                    tr.querySelectorAll('.st-action-btn').forEach(function(btn) {
                        btn.onclick = function(e) {
                            e.stopPropagation();
                            openModal(st);
                        };
                    });

                    tbody.appendChild(tr);
                });

                table.appendChild(tbody);
                tableWrap.appendChild(table);
            }

            roomCard.appendChild(tableWrap);
            container.appendChild(roomCard);
        }

        function openModal(st) {
            document.getElementById('modalShowtimeId').innerText = 'Suất chiếu #' + st.id;
            document.getElementById('modalInputId').value = st.id;
            document.getElementById('modalFilmTitle').innerText = st.filmTitle;
            document.getElementById('modalCinemaRoom').innerText = st.cinemaName + ' - ' + st.roomName;
            document.getElementById('modalStartTime').value = st.startTime;
            document.getElementById('modalEndTime').value = st.endTime;
            document.getElementById('modalBasePrice').value = st.basePrice;
            var modal = document.getElementById('showtimeEditModal');
            modal.style.display = 'flex'; modal.setAttribute('aria-hidden','false');
            modal.querySelector('[role="dialog"]').focus();
            loadDeletionImpact(st.id);
        }

        function closeModal() {
            var modal = document.getElementById('showtimeEditModal');
            modal.style.display = 'none'; modal.setAttribute('aria-hidden','true');
        }

        var countdownTimer;
        function loadDeletionImpact(id) {
            clearInterval(countdownTimer);
            var live = document.getElementById('showtimeDeleteLive'), detail = document.getElementById('showtimeDeleteImpact');
            var requestButton = document.getElementById('requestDeleteButton'), resumeButton = document.getElementById('resumeSaleButton');
            var confirmButton = document.getElementById('confirmDeleteButton');
            live.textContent = 'Đang kiểm tra trạng thái từ server…'; detail.textContent = '';
            requestButton.disabled = true; confirmButton.disabled = true;
            fetch('${pageContext.request.contextPath}/admin/showtimes?action=deletion-impact&id=' + encodeURIComponent(id),
              {headers:{'Accept':'application/json','X-Requested-With':'XMLHttpRequest'}})
              .then(function(r){return r.ok?r.json():Promise.reject(r.status);}).then(function(i){
                function render(seconds) {
                  var status = 'Sẵn sàng xóa';
                  if (i.saleStatus === 'ON_SALE') status = 'Đang bán';
                  else if (i.saleStatus === 'DELETED') status = 'Đã xóa/lịch sử';
                  else if (seconds > 0) status = 'Ngưng bán – còn '
                    + String(Math.floor(seconds / 60)).padStart(2, '0') + ':' + String(seconds % 60).padStart(2, '0');
                  else if (i.activeHoldCount > 0) status = 'Chờ hold kết thúc';
                  else if (i.committedOrderCount > 0) status = 'Bị chặn bởi order';
                  else if (i.activeDraftOrderCount > 0) status = 'Chờ order nháp';
                  live.textContent = status;
                }
                var seconds = Math.max(0, i.secondsRemaining); render(seconds);
                detail.textContent = 'Hold: ' + i.activeHoldCount + ' · order nháp: ' + i.activeDraftOrderCount +
                  ' · order hiệu lực: ' + i.committedOrderCount + ' · lịch sử/terminal: ' + i.terminalOrderCount;
                requestButton.style.display = i.saleStatus === 'ON_SALE' ? 'inline-flex' : 'none'; requestButton.disabled = false;
                resumeButton.style.display = i.saleStatus === 'SUSPENDED' ? 'inline-flex' : 'none';
                confirmButton.style.display = i.saleStatus === 'SUSPENDED' ? 'inline-flex' : 'none'; confirmButton.disabled = !i.ready;
                if (seconds > 0) countdownTimer = setInterval(function(){ seconds = Math.max(0, seconds - 1); render(seconds); if(!seconds){clearInterval(countdownTimer);loadDeletionImpact(id);} }, 1000);
              }).catch(function(){ live.textContent = 'Không tải được preview; mọi thao tác xóa đã bị khóa.'; });
        }

        document.getElementById('modalForm').addEventListener('submit', function(e) {
          var action = e.submitter && e.submitter.value;
          if (!['requestDelete','resumeSale','confirmDelete'].includes(action)) return;
          e.preventDefault(); var form = this, button = e.submitter, live = document.getElementById('showtimeDeleteLive');
          button.disabled = true; live.textContent = 'Đang kiểm tra lại và xử lý…';
          var data = new FormData(form); data.set('action', action);
          fetch(form.getAttribute('action'), {method:'POST', body:data, headers:{'Accept':'application/json','X-Requested-With':'XMLHttpRequest'}})
            .then(function(r){if(!r.ok)return r.json().then(function(j){throw new Error(j.error||'Xung đột trạng thái');}); window.location.reload();})
            .catch(function(err){live.textContent = err.message; button.disabled = false; loadDeletionImpact(document.getElementById('modalInputId').value);});
        });

        document.addEventListener('keydown', function(e){
          var modal = document.getElementById('showtimeEditModal'); if(modal.style.display === 'none') return;
          if(e.key === 'Escape'){closeModal(); return;} if(e.key !== 'Tab') return;
          var items = Array.from(modal.querySelectorAll('button:not([disabled]),input:not([disabled])'));
          if(!items.length) return; var first = items[0], last = items[items.length - 1];
          if(e.shiftKey && document.activeElement === first){e.preventDefault(); last.focus();}
          else if(!e.shiftKey && document.activeElement === last){e.preventDefault(); first.focus();}
        });
    </script>
</body>
</html>
