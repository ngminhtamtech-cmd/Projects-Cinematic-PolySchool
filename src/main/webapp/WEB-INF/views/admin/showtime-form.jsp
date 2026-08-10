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
    <title>Tạo Lịch Chiếu Mới - CineBook Admin</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
    <style>
        .showtime-form-header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            margin-bottom: 24px;
        }
        .showtime-form-breadcrumb {
            font-size: 0.85rem;
            color: #64748b;
            margin-bottom: 6px;
        }
        .showtime-form-breadcrumb a {
            color: #64748b;
            text-decoration: none;
        }
        .showtime-form-breadcrumb a:hover {
            color: #0284c7;
        }

        .showtime-form-grid {
            display: grid;
            grid-template-columns: 2.2fr 1fr;
            gap: 24px;
        }
        @media (max-width: 1024px) {
            .showtime-form-grid {
                grid-template-columns: 1fr;
            }
        }

        .form-section-title {
            font-size: 0.95rem;
            font-weight: 800;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: #0f172a;
            margin-bottom: 20px;
            padding-bottom: 8px;
            border-bottom: 1px solid #e2e8f0;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .form-row-3 {
            display: grid;
            grid-template-columns: 1fr 1fr 1fr;
            gap: 16px;
        }
        .form-row-4 {
            display: grid;
            grid-template-columns: 1.2fr 1fr 1fr 1fr;
            gap: 12px;
        }
        .form-row-2 {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 16px;
        }
        @media (max-width: 768px) {
            .form-row-3, .form-row-4, .form-row-2 {
                grid-template-columns: 1fr;
            }
        }

        .preview-panel {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 16px;
            padding: 24px;
            box-shadow: 0 4px 16px rgba(0,0,0,0.04);
            position: sticky;
            top: 20px;
        }

        .preview-poster-wrapper {
            position: relative;
            border-radius: 12px;
            overflow: hidden;
            box-shadow: 0 8px 24px rgba(0,0,0,0.15);
            margin-bottom: 16px;
            background: #0f172a;
            aspect-ratio: 2/3;
            max-height: 440px;
        }
        .preview-poster-wrapper img {
            width: 100%;
            height: 100%;
            object-fit: cover;
            display: block;
        }

        .preview-rating-badge {
            position: absolute;
            top: 12px;
            right: 12px;
            background: rgba(239, 68, 68, 0.9);
            color: #ffffff;
            font-size: 0.8rem;
            font-weight: 800;
            padding: 4px 10px;
            border-radius: 6px;
            backdrop-filter: blur(4px);
        }

        .preview-film-title {
            font-size: 1.3rem;
            font-weight: 900;
            color: #0f172a;
            margin-bottom: 4px;
        }
        .preview-film-meta {
            font-size: 0.88rem;
            color: #64748b;
            margin-bottom: 16px;
        }

        .preview-info-box {
            background: #f8fafc;
            border: 1px solid #e2e8f0;
            border-radius: 10px;
            padding: 12px 16px;
            margin-bottom: 10px;
        }
        .preview-info-label {
            font-size: 0.75rem;
            font-weight: 700;
            color: #94a3b8;
            text-transform: uppercase;
        }
        .preview-info-value {
            font-size: 0.95rem;
            font-weight: 700;
            color: #0f172a;
        }

        /* Form labels with red asterisk preceding content */
        .admin-body .form-label,
        .form-label {
            display: inline-flex !important;
            flex-direction: row !important;
            align-items: center !important;
            justify-content: flex-start !important;
            gap: 4px !important;
            font-size: 13px !important;
            font-weight: 700 !important;
            color: #334155 !important;
            margin-bottom: 6px !important;
            height: auto !important;
            width: auto !important;
            line-height: 1.4 !important;
        }
        .form-label span.req-star {
            color: #ef4444 !important;
            font-weight: 700 !important;
            margin-right: 2px !important;
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
                <div class="showtime-form-header">
                    <div>
                        <div class="showtime-form-breadcrumb">
                            <a href="${pageContext.request.contextPath}/admin/dashboard">Dashboard</a> &gt;
                            <a href="${pageContext.request.contextPath}/admin/showtimes">Quản lý lịch chiếu</a> &gt;
                            <span>${not empty showtime and showtime.id gt 0 ? 'Chỉnh sửa lịch chiếu' : 'Tạo lịch chiếu mới'}</span>
                        </div>
                        <h1 style="font-size: 1.6rem; font-weight: 800; color: #0f172a; margin: 0;">${not empty showtime and showtime.id gt 0 ? 'Chỉnh sửa lịch chiếu' : 'Tạo lịch chiếu mới'}</h1>
                        <p class="muted" style="margin-top: 4px; font-size: 0.88rem;">Thiết lập lịch chiếu phim cho các phòng</p>
                    </div>
                    <a href="${pageContext.request.contextPath}/admin/showtimes" class="button secondary" style="padding: 8px 16px; text-decoration: none; border-radius: 8px; font-weight: 600;">
                        ← Quay lại danh sách
                    </a>
                </div>

                <form method="post" action="${pageContext.request.contextPath}/admin/showtimes" id="showtimeForm">
                    <cb:csrf/>
                    <input type="hidden" name="id" value="${fn:escapeXml(showtime.id)}">
                    <div class="showtime-form-grid">
                        
                        <!-- LEFT COLUMN: THÔNG TIN LỊCH CHIẾU -->
                        <div>
                            <!-- 1. THÔNG TIN LỊCH CHIẾU -->
                            <article class="panel" style="padding: 24px; margin-bottom: 24px; background:#fff; border-radius:12px; border:1px solid #e2e8f0;">
                                <div class="form-section-title">THÔNG TIN LỊCH CHIẾU</div>

                                <div class="form-row-3" style="margin-bottom: 16px;">
                                    <div class="form-group">
                                        <label class="form-label"><span class="req-star">*</span> Cụm rạp</label>
                                        <select name="cinemaId" id="cinemaSelect" class="form-select" required onchange="onCinemaChange()">
                                            <option value="">-- Chọn cụm rạp --</option>
                                            <c:forEach var="cinema" items="${cinemas}">
                                                <option value="${fn:escapeXml(cinema.id)}" ${showtime.cinemaId eq cinema.id ? 'selected' : ''}>${fn:escapeXml(cinema.name)}</option>
                                            </c:forEach>
                                        </select>
                                    </div>

                                    <div class="form-group">
                                        <label class="form-label"><span class="req-star">*</span> Phòng chiếu</label>
                                        <select name="roomId" id="roomSelect" class="form-select" required onchange="updatePreview()">
                                            <option value="">-- Chọn phòng chiếu --</option>
                                            <c:forEach var="room" items="${rooms}">
                                                <c:if test="${room.status != 'inactive' or room.id eq showtime.roomId}">
                                                    <option value="${fn:escapeXml(room.id)}" data-cinema="${fn:escapeXml(room.cinemaId)}" ${showtime.roomId eq room.id ? 'selected' : ''}>${fn:escapeXml(room.name)}</option>
                                                </c:if>
                                            </c:forEach>
                                        </select>
                                    </div>

                                    <div class="form-group">
                                        <label class="form-label"><span class="req-star">*</span> Định dạng</label>
                                        <select name="format" id="formatSelect" class="form-select" onchange="updatePreview()">
                                            <option value="2D" ${empty showtime.format or showtime.format eq '2D' ? 'selected' : ''}>2D</option>
                                            <option value="3D" ${showtime.format eq '3D' ? 'selected' : ''}>3D</option>
                                            <option value="IMAX" ${showtime.format eq 'IMAX' ? 'selected' : ''}>IMAX</option>
                                            <option value="4DX" ${showtime.format eq '4DX' ? 'selected' : ''}>4DX</option>
                                        </select>
                                    </div>
                                </div>

                                <div class="form-group" style="margin-bottom: 16px;">
                                    <label class="form-label"><span class="req-star">*</span> Phim</label>
                                    <select name="filmId" id="filmSelect" class="form-select" required onchange="onFilmChange()">
                                        <option value="">-- Chọn bộ phim --</option>
                                        <c:forEach var="film" items="${films}">
                                            <c:choose>
                                                <c:when test="${not empty film.banner}">
                                                    <c:set var="filmThumb" value="${cbf:assetUrl(pageContext.request.contextPath, film.banner)}" />
                                                </c:when>
                                                <c:when test="${not empty film.thumbnail}">
                                                    <c:set var="filmThumb" value="${cbf:assetUrl(pageContext.request.contextPath, film.thumbnail)}" />
                                                </c:when>
                                                <c:otherwise>
                                                    <c:set var="filmThumb" value="" />
                                                </c:otherwise>
                                            </c:choose>
                                            <option value="${fn:escapeXml(film.id)}" <c:if test="${(not empty param.filmId && param.filmId == film.id) || (not empty selectedFilmId && selectedFilmId == film.id)}">selected</c:if>
                                                    data-title="${fn:escapeXml(film.title)}" 
                                                    data-thumbnail="${fn:escapeXml(filmThumb)}" 
                                                    data-duration="${fn:escapeXml(film.duration)}" 
                                                    data-genre="${fn:escapeXml(film.genre)}" 
                                                    data-rating="${fn:escapeXml(film.ageRating)}">
                                                ${fn:escapeXml(film.title)} (${fn:escapeXml(film.duration)} phút | ${fn:escapeXml(film.genre)})
                                            </option>
                                        </c:forEach>
                                    </select>
                                </div>

                                <div class="form-row-3" style="margin-bottom: 0;">
                                    <div class="form-group">
                                        <label class="form-label" style="font-weight:700; color:#334155;">Phiên bản</label>
                                        <select name="version" class="form-select">
                                            <option value="Phụ đề" ${showtime.version eq 'Phụ đề' or empty showtime.version ? 'selected' : ''}>Phụ đề</option>
                                            <option value="Lồng tiếng" ${showtime.version eq 'Lồng tiếng' ? 'selected' : ''}>Lồng tiếng</option>
                                            <option value="Thuyết minh" ${showtime.version eq 'Thuyết minh' ? 'selected' : ''}>Thuyết minh</option>
                                        </select>
                                    </div>
                                    <div class="form-group">
                                        <label class="form-label" style="font-weight:700; color:#334155;">Ngôn ngữ</label>
                                        <select name="language" class="form-select">
                                            <option value="Tiếng Việt" ${showtime.language eq 'Tiếng Việt' or empty showtime.language ? 'selected' : ''}>Tiếng Việt</option>
                                            <option value="Tiếng Anh" ${showtime.language eq 'Tiếng Anh' ? 'selected' : ''}>Tiếng Anh</option>
                                            <option value="Tiếng Hàn" ${showtime.language eq 'Tiếng Hàn' ? 'selected' : ''}>Tiếng Hàn</option>
                                            <option value="Tiếng Nhật" ${showtime.language eq 'Tiếng Nhật' ? 'selected' : ''}>Tiếng Nhật</option>
                                        </select>
                                    </div>
                                    <div class="form-group">
                                        <label class="form-label" style="font-weight:700; color:#334155;">Thời lượng</label>
                                        <input type="text" id="durationDisplay" class="form-input" value="-- phút" readonly style="background:#f1f5f9; color:#475569; font-weight:700;">
                                    </div>
                                </div>
                            </article>

                            <!-- 2. THỜI GIAN CHIẾU -->
                            <article class="panel" style="padding: 24px; margin-bottom: 24px; background:#fff; border-radius:12px; border:1px solid #e2e8f0;">
                                <div class="form-section-title">THỜI GIAN CHIẾU</div>

                                <div class="form-row-4" style="margin-bottom: 16px;">
                                    <div class="form-group">
                                        <label class="form-label"><span class="req-star">*</span> Ngày chiếu</label>
                                        <input type="date" name="showDate" id="showDate" class="form-input" required value="${fn:escapeXml(showtime.startTime.toLocalDate())}" onchange="calculateEndTime(); updatePreview();">
                                    </div>

                                    <%-- ST-02: nhap gio theo he 24h.
                                         step="60" bo o giay; list preset giup chon nhanh ma van go tay duoc
                                         chinh xac HH:mm. Trinh duyet co the hien SA/CH tuy locale may, nhung
                                         GIA TRI gui len luon la 24h ("14:00") nen server khong bao gio phai
                                         doan. Server con chap nhan "24:00" va chuyen thanh 00:00 hom sau. --%>
                                    <div class="form-group">
                                        <label class="form-label"><span class="req-star">*</span> Giờ bắt đầu (24h)</label>
                                        <select name="startTimeOnly" id="startTimeOnly" class="form-select"
                                                required onchange="calculateEndTime(); updatePreview();">
                                            <option value="">-- Chọn giờ chiếu (24h) --</option>
                                            <option value="01:00">01:00 - Sáng</option>
                                            <option value="01:30">01:30 - Sáng</option>
                                            <option value="02:00">02:00 - Sáng</option>
                                            <option value="02:30">02:30 - Sáng</option>
                                            <option value="03:00">03:00 - Sáng</option>
                                            <option value="03:30">03:30 - Sáng</option>
                                            <option value="04:00">04:00 - Sáng</option>
                                            <option value="04:30">04:30 - Sáng</option>
                                            <option value="05:00">05:00 - Sáng</option>
                                            <option value="05:30">05:30 - Sáng</option>
                                            <option value="06:00">06:00 - Sáng</option>
                                            <option value="06:30">06:30 - Sáng</option>
                                            <option value="07:00">07:00 - Sáng</option>
                                            <option value="07:30">07:30 - Sáng</option>
                                            <option value="08:00">08:00 - Sáng</option>
                                            <option value="08:30">08:30 - Sáng</option>
                                            <option value="09:00" selected>09:00 - Sáng</option>
                                            <option value="09:30">09:30 - Sáng</option>
                                            <option value="10:00">10:00 - Sáng</option>
                                            <option value="10:30">10:30 - Sáng</option>
                                            <option value="11:00">11:00 - Trưa</option>
                                            <option value="11:30">11:30 - Trưa</option>
                                            <option value="12:00">12:00 - Trưa</option>
                                            <option value="12:30">12:30 - Trưa</option>
                                            <option value="13:00">13:00 - Chiều</option>
                                            <option value="13:30">13:30 - Chiều</option>
                                            <option value="14:00">14:00 - Chiều</option>
                                            <option value="14:30">14:30 - Chiều</option>
                                            <option value="15:00">15:00 - Chiều</option>
                                            <option value="15:30">15:30 - Chiều</option>
                                            <option value="16:00">16:00 - Chiều</option>
                                            <option value="16:30">16:30 - Chiều muộn</option>
                                            <option value="17:00">17:00 - Chiều muộn</option>
                                            <option value="17:30">17:30 - Chiều muộn</option>
                                            <option value="18:00">18:00 - Tối</option>
                                            <option value="18:30">18:30 - Tối</option>
                                            <option value="19:00">19:00 - Tối</option>
                                            <option value="19:30">19:30 - Tối</option>
                                            <option value="20:00">20:00 - Tối</option>
                                            <option value="20:30">20:30 - Tối</option>
                                            <option value="21:00">21:00 - Tối</option>
                                            <option value="21:30">21:30 - Đêm</option>
                                            <option value="22:00">22:00 - Khuya</option>
                                            <option value="22:30">22:30 - Khuya</option>
                                            <option value="23:00">23:00 - Khuya</option>
                                            <option value="23:30">23:30 - Khuya</option>
                                        </select>
                                    </div>

                                    <%-- ST-02: gio ket thuc do SERVER tinh tu thoi luong phim.
                                         O nay chi la XEM TRUOC, khong con name= nen khong gui len nua.
                                         Ban cu gui endTimeOnly va server tin luon, nen mot request sua tay
                                         co the dat gio ket thuc bat ky. Ngoai ra JS cu cong them "thoi gian
                                         ve sinh" vao gio ket thuc, trong khi tang service lai cong buffer
                                         don phong mot lan nua khi kiem trung lich — thanh ra tru hai lan. --%>
                                    <div class="form-group">
                                        <label class="form-label">Giờ kết thúc (server tính)</label>
                                        <input type="text" id="endTimeOnly" class="form-input" readonly
                                               style="background:#f1f5f9; color:#475569; font-weight:700;"
                                               aria-describedby="endTimeHint">
                                        <small id="endTimeHint" class="muted">= giờ bắt đầu + thời lượng phim</small>
                                    </div>

                                    <div class="form-group">
                                        <label class="form-label">Thời gian dọn phòng</label>
                                        <input type="text" class="form-input" readonly
                                               style="background:#f1f5f9; color:#475569;"
                                               value="Theo cấu hình hệ thống">
                                        <small class="muted">Chỉ dùng khi kiểm tra trống phòng, không cộng vào thời lượng phim.</small>
                                    </div>
                                </div>

                                <div class="form-row-2" style="margin-bottom: 0; align-items: center;">
                                    <div class="form-group">
                                        <label class="form-label">Lặp lại lịch chiếu (Tự động tạo nhiều ngày)</label>
                                        <select name="repeatDays" class="form-select">
                                            <option value="1">Không lặp lại (Chỉ 1 suất ngày chọn)</option>
                                            <option value="3">Tự động lặp lại cho 3 ngày tiếp theo</option>
                                            <option value="5">Tự động lặp lại cho 5 ngày tiếp theo</option>
                                            <option value="7">Tự động lặp lại cho 7 ngày tiếp theo</option>
                                        </select>
                                    </div>
                                    <div class="form-group">
                                        <label class="form-label"><span class="req-star">*</span> Giá cơ bản (VNĐ)</label>
                                        <input type="number" min="0" step="1000" name="basePrice" id="basePrice" class="form-input" value="${empty showtime.basePrice ? 80000 : showtime.basePrice}" required onchange="updatePreview()">
                                    </div>
                                </div>
                            </article>

                            <!-- 3. THÔNG TIN KHÁC -->
                            <article class="panel" style="padding: 24px; margin-bottom: 24px; background:#fff; border-radius:12px; border:1px solid #e2e8f0;">
                                <div class="form-section-title">THÔNG TIN KHÁC</div>
                                <div class="form-group" style="margin-bottom:16px;">
                                    <label class="form-label">Ghi chú nội bộ</label>
                                    <input type="text" name="note" class="form-input" placeholder="Nhập ghi chú nội bộ (nếu có)...">
                                </div>
                                <%-- BUG-06: doi gio/phim cua suat DA BAN VE mac dinh bi chan.
                                     Tich o nay thi he thong doi tham so VA gui thong bao cho moi
                                     nguoi dang giu ve, trong cung mot transaction.

                                     B.5: DOI PHONG khong nam trong pham vi o tick nay. Doi phong
                                     bat buoc dung lai so do ghe nen khong giu duoc ghe da ban;
                                     tang service chan thang va bao ro ly do. Noi ra o day de o
                                     tick khong hua mot kha nang khong ton tai. --%>
                                <c:if test="${not empty showtime and showtime.id gt 0}">
                                <div id="impactConfirmationBlock" class="form-group" style="display:none; margin-bottom:0; padding:12px; background:#fffbeb; border:1px solid #fcd34d; border-radius:8px;">
                                    <label class="form-label" style="font-weight:700; color:#92400e; display:flex; gap:8px; align-items:flex-start;">
                                        <input type="checkbox" name="confirmImpact" value="true" style="margin-top:3px;">
                                        <span>Tôi xác nhận thay đổi suất chiếu <strong>đã bán vé</strong> — hệ thống sẽ gửi thông báo tới toàn bộ khách đang giữ vé của suất này.</span>
                                    </label>
                                    <p style="margin:8px 0 0 26px; font-size:12px; color:#92400e; font-weight:400;">
                                        Đổi phòng STANDARD chỉ được thực hiện khi phòng đích có đủ từng <strong>SeatKey</strong> đang được giữ/bán; hệ thống giữ nguyên ghế và vé. Mọi thay đổi liên quan VIP đều bị chặn.
                                    </p>
                                </div>
                                </c:if>
                            </article>

                            <!-- ACTIONS -->
                            <div class="form-actions" style="display:flex; justify-content:flex-end; gap:12px;">
                                <a href="${pageContext.request.contextPath}/admin/showtimes" class="button secondary" style="padding:12px 24px; text-decoration:none; border-radius:8px; font-weight:600;">Hủy bỏ</a>
                                <button type="submit" class="button primary" style="padding:12px 32px; border-radius:8px; font-weight:700; background:linear-gradient(135deg, #0284c7, #38bdf8);">
                                    ${not empty showtime and showtime.id gt 0 ? 'Lưu thay đổi' : 'Tạo lịch chiếu mới'}
                                </button>
                            </div>
                        </div>

                        <!-- RIGHT COLUMN: QUICK PREVIEW PANEL -->
                        <div>
                            <div class="preview-panel">
                                <div class="form-section-title">TỔNG QUAN LỊCH CHIẾU</div>

                                <div class="preview-poster-wrapper">
                                    <img id="previewPoster" src="" alt="Poster phim đã chọn" hidden>
                                    <span id="previewPosterEmpty" class="admin-record-placeholder">Không có ảnh</span>
                                    <span id="previewRating" class="preview-rating-badge">—</span>
                                </div>

                                <div id="previewTitle" class="preview-film-title">Tên Phim Chưa Chọn</div>
                                <div id="previewMeta" class="preview-film-meta">-- phút | Thể loại</div>

                                <div class="preview-info-box">
                                    <div class="preview-info-label">Cụm Rạp & Phòng</div>
                                    <div id="previewCinemaRoom" class="preview-info-value">Chưa chọn rạp & phòng</div>
                                </div>

                                <div class="preview-info-box">
                                    <div class="preview-info-label">Thời Gian Chiếu</div>
                                    <div id="previewShowtime" class="preview-info-value">--:-- - --:--</div>
                                </div>

                                <div class="preview-info-box">
                                    <div class="preview-info-label">Giá Vé Cơ Bản</div>
                                    <div id="previewPrice" class="preview-info-value" style="color:#0284c7; font-size:1.1rem; font-weight:900;">80,000 VNĐ</div>
                                </div>
                            </div>
                        </div>

                    </div>
                </form>
            </div>
        </main>
    </div>

    <!-- CINEMA FILM MAP JSON DATA -->
    <script id="cinemaFilmMapData" type="application/json">
    {
        <c:forEach var="entry" items="${cinemaFilmMap}" varStatus="st">
            "<c:out value='${entry.key}'/>": [<c:forEach var="fId" items="${entry.value}" varStatus="fst">${fId}<c:if test="${!fst.last}">,</c:if></c:forEach>]<c:if test="${!st.last}">,</c:if>
        </c:forEach>
    }
    </script>

    <script>
        // Goi y san ngay hom nay va 14:00 cho tien thao tac.
        //
        // Day chi la TIEN ICH NHAP LIEU, khong phai gia tri nghiep vu: neu nguoi dung xoa
        // trong hai o nay, server se TU CHOI voi thong bao ro rang chu khong tu bia ra gio
        // mac dinh nhu ban cu (LocalDateTime.now().plusDays(1).withHour(14)). Moi so sanh
        // thoi gian o backend deu dung gio cua CSDL, khong dung dong ho trinh duyet.
        var impactState = { requiresConfirmation: false };
        var originalShowtime = {};
        window.addEventListener('DOMContentLoaded', function() {
            var editingShowtime = ${not empty showtime and showtime.id gt 0};
            originalShowtime = {
                filmId: '${not empty showtime ? showtime.filmId : ''}',
                cinemaId: '${not empty showtime ? showtime.cinemaId : ''}',
                roomId: '${not empty showtime ? showtime.roomId : ''}',
                date: '${not empty showtime ? showtime.startTime.toLocalDate() : ''}',
                time: '${not empty showtime ? showtime.startTime.toLocalTime() : ''}',
                format: '${not empty showtime ? fn:escapeXml(showtime.format) : ''}',
                version: '${not empty showtime ? fn:escapeXml(showtime.version) : ''}',
                language: '${not empty showtime ? fn:escapeXml(showtime.language) : ''}',
                price: '${not empty showtime ? showtime.basePrice : ''}'
            };
            if (!editingShowtime) {
                var today = new Date();
                var yyyy = today.getFullYear();
                var mm = String(today.getMonth() + 1).padStart(2, '0');
                var dd = String(today.getDate()).padStart(2, '0');
                document.getElementById('showDate').value = yyyy + '-' + mm + '-' + dd;
                document.getElementById('startTimeOnly').value = '14:00';
            }
            
            // Initialize Cinema Map
            try {
                var rawMapJson = document.getElementById('cinemaFilmMapData').textContent || '{}';
                window.cinemaFilmMap = JSON.parse(rawMapJson);
            } catch (e) {
                window.cinemaFilmMap = {};
            }

            onCinemaChange();
            if (editingShowtime) {
                document.getElementById('roomSelect').value = '${fn:escapeXml(showtime.roomId)}';
                document.getElementById('startTimeOnly').value = '${fn:escapeXml(showtime.startTime.toLocalTime())}';
            }
            var filmSelect = document.getElementById('filmSelect');
            if (filmSelect.selectedIndex <= 0 && filmSelect.options.length > 1) {
                filmSelect.selectedIndex = 1;
            }
            onFilmChange();
            if (editingShowtime) {
                fetch('${pageContext.request.contextPath}/admin/showtimes?action=impact&showtimeId=${showtime.id}', {
                    headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
                }).then(function(r) { return r.ok ? r.json() : Promise.reject(r.status); })
                  .then(function(data) {
                      impactState.requiresConfirmation = !!data.requiresConfirmation;
                      updateImpactVisibility();
                  }).catch(function() { /* backend still enforces confirmation safely */ });
            }
            ['filmSelect','cinemaSelect','roomSelect','showDate','startTimeOnly','formatSelect','basePrice']
                .forEach(function(id) {
                    var element = document.getElementById(id);
                    if (element) element.addEventListener('change', updateImpactVisibility);
                });
        });

        function updateImpactVisibility() {
            var block = document.getElementById('impactConfirmationBlock');
            if (!block || typeof originalShowtime === 'undefined' || !impactState.requiresConfirmation) return;
            var changed = String(document.getElementById('filmSelect').value) !== originalShowtime.filmId
                || String(document.getElementById('cinemaSelect').value) !== originalShowtime.cinemaId
                || String(document.getElementById('roomSelect').value) !== originalShowtime.roomId
                || document.getElementById('showDate').value !== originalShowtime.date
                || document.getElementById('startTimeOnly').value !== originalShowtime.time.substring(0, 5)
                || document.getElementById('formatSelect').value !== originalShowtime.format
                || document.getElementById('basePrice').value !== originalShowtime.price;
            block.style.display = changed ? 'block' : 'none';
        }

        function onCinemaChange() {
            var cinemaSelect = document.getElementById('cinemaSelect');
            var roomSelect = document.getElementById('roomSelect');
            var filmSelect = document.getElementById('filmSelect');
            var cinemaId = cinemaSelect.value;
            var cIdInt = parseInt(cinemaId, 10);

            // 1. Filter Rooms by Cinema
            for (var i = 0; i < roomSelect.options.length; i++) {
                var opt = roomSelect.options[i];
                var cinId = opt.getAttribute('data-cinema');
                if (!cinId) continue;
                if (cinemaId === "" || cinId === cinemaId) {
                    opt.style.display = "";
                } else {
                    opt.style.display = "none";
                }
            }
            roomSelect.selectedIndex = 0;

            // 2. Keep all films active and enabled for admin scheduling
            for (var f = 0; f < filmSelect.options.length; f++) {
                var fOpt = filmSelect.options[f];
                if (!fOpt.value) continue;
                fOpt.style.display = "";
                fOpt.disabled = false;
            }

            onFilmChange();
            updatePreview();
        }

        function onFilmChange() {
            var filmSelect = document.getElementById('filmSelect');
            var selectedOpt = filmSelect.options[filmSelect.selectedIndex];
            
            if (selectedOpt && selectedOpt.value) {
                var duration = selectedOpt.getAttribute('data-duration') || '120';
                document.getElementById('durationDisplay').value = duration + ' phút';
            } else {
                document.getElementById('durationDisplay').value = '-- phút';
            }
            calculateEndTime();
            updatePreview();
        }

        /**
         * Xem truoc gio ket thuc (ST-02).
         *
         * Day chi la HIEN THI. Gia tri that do server tinh lai tu DurationMinutes cua phim,
         * va o input nay khong con thuoc tinh name= nen khong duoc gui len nua.
         *
         * Hai loi cua ban cu duoc sua o day:
         *   1. Cong them "thoi gian ve sinh" vao gio ket thuc, trong khi tang service lai
         *      cong buffer don phong mot lan nua khi kiem trung lich -> tru hai lan.
         *   2. Dung new Date() roi setHours/setMinutes nen ket qua nhay theo NGAY HOM NAY;
         *      ca qua nua dem (23:30 + 120') hien 01:30 ma khong noi la ngay hom sau.
         *      Nay tinh trên chinh ngay chieu da chon va ghi ro "(+1 ngay)".
         */
        function calculateEndTime() {
            var startTimeStr = document.getElementById('startTimeOnly').value;
            var showDateStr = document.getElementById('showDate').value;
            var filmSelect = document.getElementById('filmSelect');
            var selectedOpt = filmSelect.options[filmSelect.selectedIndex];
            var endField = document.getElementById('endTimeOnly');

            if (!startTimeStr || !selectedOpt || !selectedOpt.value) {
                endField.value = '';
                return;
            }

            var duration = parseInt(selectedOpt.getAttribute('data-duration') || '0', 10);
            if (!duration || duration <= 0) {
                endField.value = 'Phim chưa khai báo thời lượng';
                return;
            }

            var parts = startTimeStr.split(':');
            var startH = parseInt(parts[0], 10);
            var startM = parseInt(parts[1], 10);

            // Neu nguoi dung go 24:00 (cach viet nua dem hay gap), coi nhu 00:00 hom sau —
            // dung quy uoc voi server, va khong bao gio luu chuoi "24:00".
            var rollDays = 0;
            if (startH >= 24) {
                startH -= 24;
                rollDays = 1;
            }

            var base = showDateStr ? new Date(showDateStr + 'T00:00:00') : new Date();
            base.setDate(base.getDate() + rollDays);
            base.setHours(startH, startM + duration, 0, 0);

            var endH = String(base.getHours()).padStart(2, '0');
            var endM = String(base.getMinutes()).padStart(2, '0');

            var startDay = showDateStr ? new Date(showDateStr + 'T00:00:00').getDate() : new Date().getDate();
            var crossesMidnight = base.getDate() !== startDay;
            endField.value = endH + ':' + endM + (crossesMidnight ? ' (+1 ngày)' : '');
        }

        function updatePreview() {
            // 1. Film info
            var filmSelect = document.getElementById('filmSelect');
            var selectedFilm = filmSelect.options[filmSelect.selectedIndex];
            if (selectedFilm && selectedFilm.value) {
                document.getElementById('previewTitle').innerText = selectedFilm.getAttribute('data-title') || 'Chưa chọn';
                document.getElementById('previewMeta').innerText = (selectedFilm.getAttribute('data-duration') || '--') + ' phút | ' + (selectedFilm.getAttribute('data-genre') || '');
                document.getElementById('previewRating').innerText = selectedFilm.getAttribute('data-rating') || '—';
                var poster = document.getElementById('previewPoster');
                var posterEmpty = document.getElementById('previewPosterEmpty');
                var thumbnail = selectedFilm.getAttribute('data-thumbnail') || '';
                poster.src = thumbnail;
                poster.hidden = !thumbnail;
                posterEmpty.hidden = !!thumbnail;
            }

            // 2. Cinema & Room info
            var cinemaSelect = document.getElementById('cinemaSelect');
            var roomSelect = document.getElementById('roomSelect');
            var formatSelect = document.getElementById('formatSelect');
            
            var cinName = cinemaSelect.selectedIndex > 0 ? cinemaSelect.options[cinemaSelect.selectedIndex].text : 'Chưa chọn rạp';
            var roomName = roomSelect.selectedIndex > 0 ? roomSelect.options[roomSelect.selectedIndex].text : 'Chưa chọn phòng';
            var format = formatSelect.value || '2D';
            
            document.getElementById('previewCinemaRoom').innerText = cinName + ' - ' + roomName + ' (' + format + ')';

            // 3. Showtime info
            var showDate = document.getElementById('showDate').value;
            var startT = document.getElementById('startTimeOnly').value || '--:--';
            var endT = document.getElementById('endTimeOnly').value || '--:--';
            document.getElementById('previewShowtime').innerText = startT + ' - ' + endT + ' (' + showDate + ')';

            // 4. Base Price info
            var price = document.getElementById('basePrice').value || '0';
            document.getElementById('previewPrice').innerText = parseInt(price, 10).toLocaleString('vi-VN') + ' VNĐ';
        }
    </script>
</body>
</html>
