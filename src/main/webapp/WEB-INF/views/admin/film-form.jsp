<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="cbf" uri="https://cinebook.local/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${fn:escapeXml(empty film.id || film.id eq 0 ? 'Thêm Phim Mới' : 'Chỉnh Sửa Phim')} - CineBook Admin</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805h">
    <style>
        .film-form-header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            margin-bottom: 24px;
        }
        .film-form-breadcrumb {
            font-size: 0.85rem;
            color: #64748b;
            margin-bottom: 6px;
        }
        .film-form-breadcrumb a {
            color: #64748b;
            text-decoration: none;
        }
        .film-form-breadcrumb a:hover {
            color: #38bdf8;
        }
        .film-form-grid {
            display: grid;
            grid-template-columns: 2fr 1fr;
            gap: 24px;
        }
        @media (max-width: 1024px) {
            .film-form-grid {
                grid-template-columns: 1fr;
            }
        }
        .form-section-title {
            font-size: 0.95rem;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: #94a3b8;
            margin-bottom: 20px;
            padding-bottom: 8px;
            border-bottom: 1px solid rgba(255, 255, 255, 0.08);
        }
        .form-row-2 {
            display: grid !important;
            grid-template-columns: 1fr 1fr !important;
            gap: 18px !important;
            align-items: start !important;
            margin-bottom: 0 !important;
        }
        @media (max-width: 640px) {
            .form-row-2 {
                grid-template-columns: 1fr !important;
            }
        }
        .upload-dropzone {
            border: 2px dashed #cbd5e1;
            border-radius: 12px;
            padding: 24px 16px;
            text-align: center;
            background: #f8fafc;
            cursor: pointer;
            transition: all 0.2s ease;
            position: relative;
        }
        .upload-dropzone:hover {
            border-color: #6d28d9;
            background: rgba(109, 40, 217, 0.03);
        }
        .upload-dropzone input[type="file"] {
            position: absolute;
            top: 0; left: 0; width: 100%; height: 100%;
            opacity: 0;
            pointer-events: none;
        }
        .upload-icon {
            font-size: 32px;
            color: #6d28d9;
            margin-bottom: 8px;
        }
        .upload-preview {
            width: 100%;
            aspect-ratio: 16 / 9;
            max-height: 200px;
            border-radius: 8px;
            margin-top: 12px;
            object-fit: cover;
        }
        .form-actions {
            margin-top: 24px;
            display: flex;
            gap: 12px;
            justify-content: flex-end;
        }

        /* FORM FIELD ALIGNMENT & UNIFORM SIZING */
        .admin-body .form-group {
            margin-bottom: 18px !important;
            display: flex !important;
            flex-direction: column !important;
            align-items: stretch !important;
        }

        /* RED ASTERISK PRECEDING LABEL TEXT ON SINGLE HORIZONTAL LINE */
        .admin-body .form-label,
        .form-label {
            display: inline-flex !important;
            flex-direction: row !important;
            align-items: center !important;
            justify-content: flex-start !important;
            gap: 4px !important;
            font-size: 13px !important;
            font-weight: 600 !important;
            color: #475569 !important;
            margin-bottom: 6px !important;
            height: auto !important;
            width: auto !important;
        }

        /* UNIFORM INPUT CONTROL HEIGHTS (42px) */
        .admin-body .form-input,
        .admin-body select,
        .admin-body input[type="text"],
        .admin-body input[type="number"],
        .admin-body input[type="date"],
        .admin-body input[type="url"],
        .cinema-multiselect-trigger {
            width: 100% !important;
            height: 42px !important;
            min-height: 42px !important;
            max-height: 42px !important;
            padding: 0 14px !important;
            border: 1px solid #cbd5e1 !important;
            border-radius: 8px !important;
            background: #ffffff !important;
            color: #0f172a !important;
            font-size: 13.5px !important;
            font-weight: 400 !important;
            box-sizing: border-box !important;
            outline: none !important;
            margin: 0 !important;
            line-height: 40px !important;
            transition: all 0.15s ease !important;
        }
        .admin-body .form-input:focus,
        .admin-body select:focus,
        .admin-body input:focus {
            border-color: #6d28d9 !important;
            box-shadow: 0 0 0 3px rgba(109, 40, 217, 0.1) !important;
        }
        .admin-body textarea.form-input {
            height: auto !important;
            min-height: 110px !important;
            max-height: none !important;
            padding: 12px 14px !important;
            line-height: 1.5 !important;
        }

        /* CLEAN SELECT DROPDOWN STYLE MATCHING IMAGE [2] */
        .cinema-multiselect { position: relative; width: 100%; }
        .cinema-multiselect-trigger::after {
            content: '';
            position: absolute; right: 14px; top: 50%;
            width: 8px; height: 8px;
            border-right: 2px solid #64748b;
            border-bottom: 2px solid #64748b;
            transform: translateY(-65%) rotate(45deg);
            transition: transform 0.2s ease;
        }
        .cinema-multiselect.is-open .cinema-multiselect-trigger::after {
            transform: translateY(-25%) rotate(-135deg);
        }
        .cinema-checkbox-container {
            display: none; position: absolute; z-index: 99; top: calc(100% + 4px); left: 0; right: 0;
            border: 1px solid #cbd5e1; border-radius: 8px; padding: 4px 0;
            max-height: 250px; overflow-y: auto; background: #ffffff;
            box-shadow: 0 12px 28px rgba(15,23,42,0.12);
        }
        .cinema-multiselect.is-open .cinema-checkbox-container { display: block; }
        .cinema-option-header {
            display: flex; align-items: center; gap: 12px;
            padding: 8px 14px 6px 14px; border-bottom: 1px solid #f1f5f9;
            font-size: 12px; font-weight: 600; color: #64748b;
        }
        .cinema-option-header button {
            background: none; border: none; color: #6d28d9; cursor: pointer;
            font-weight: 600; font-size: 12px; padding: 0;
        }
        .cinema-option-header button:hover { text-decoration: underline; }

        /* SPECIFICITY OVERRIDES FOR ADMIN-BODY LABEL RULES */
        .admin-body .cinema-option-item,
        .admin-body label.cinema-option-item,
        .cinema-checkbox-container label {
            display: flex !important;
            flex-direction: row !important;
            align-items: center !important;
            justify-content: flex-start !important;
            gap: 10px !important;
            padding: 10px 14px !important;
            cursor: pointer !important;
            transition: background 0.15s ease !important;
            text-align: left !important;
            font-size: 13.5px !important;
            color: #0f172a !important;
            border-bottom: 1px solid #f1f5f9 !important;
            margin: 0 !important;
            width: 100% !important;
            box-sizing: border-box !important;
        }
        .admin-body .cinema-option-item:last-child {
            border-bottom: none !important;
        }
        .admin-body .cinema-option-item:hover {
            background: #f1f5f9 !important;
            color: #6d28d9 !important;
        }
        .admin-body .cinema-option-item input[type="checkbox"] {
            width: 16px !important;
            height: 16px !important;
            accent-color: #6d28d9 !important;
            cursor: pointer !important;
            flex-shrink: 0 !important;
            margin: 0 !important;
            display: inline-block !important;
        }
        .admin-body .cinema-option-text {
            font-weight: 500 !important;
            color: #0f172a !important;
            display: inline-flex !important;
            align-items: center !important;
            gap: 4px !important;
            font-size: 13.5px !important;
            text-align: left !important;
            margin: 0 !important;
        }
        .admin-body .cinema-option-sub {
            color: #64748b !important;
            font-size: 12px !important;
            font-weight: 400 !important;
            margin-left: 4px !important;
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
                <div class="film-form-header">
                    <div>
                        <div class="film-form-breadcrumb">
                            <a href="${pageContext.request.contextPath}/admin/dashboard">Dashboard</a> &gt; 
                            <a href="${pageContext.request.contextPath}/admin/films">Quản lý phim</a> &gt; 
                            <span>${fn:escapeXml(empty film.id || film.id eq 0 ? 'Thêm phim mới' : 'Chỉnh sửa phim')}</span>
                        </div>
                        <h1 style="font-size: 1.6rem; font-weight: 800; color: #0f172a; margin: 0;">
                            ${fn:escapeXml(empty film.id || film.id eq 0 ? 'Thêm phim mới' : 'Chỉnh sửa phim')}
                        </h1>
                        <p class="muted" style="margin: 4px 0 0 0;">
                            ${fn:escapeXml(empty film.id || film.id eq 0 ? 'Nhập thông tin để thêm phim mới vào hệ thống' : 'Cập nhật thông tin chi tiết của bộ phim')}
                        </p>
                    </div>
                    <div>
                        <a href="${pageContext.request.contextPath}/admin/films" class="button secondary" style="display: inline-flex; align-items: center; gap: 6px; text-decoration: none; padding: 10px 18px; border-radius: 8px; font-weight: 600;">
                            ← Quay lại danh sách
                        </a>
                    </div>
                </div>

                <!-- MAIN FORM (2 COLUMNS) -->
                <form method="post" action="${pageContext.request.contextPath}/admin/films" enctype="multipart/form-data">
                    <cb:csrf/>
                    <input type="hidden" name="id" value="${fn:escapeXml(film.id)}">
                    <input type="hidden" name="thumbnail" value="${fn:escapeXml(film.thumbnail)}">
                    <input type="hidden" name="banner" value="${fn:escapeXml(film.banner)}">

                    <div class="film-form-grid">
                        <!-- LEFT COLUMN: THÔNG TIN PHIM -->
                        <article class="panel" style="padding: 24px;">
                            <div class="form-section-title">THÔNG TIN PHIM</div>

                            <div class="form-row-2">
                                <div class="form-group">
                                    <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Tên phim</label>
                                    <input type="text" name="title" value="${fn:escapeXml(film.title)}" class="form-input" placeholder="Nhập tên phim" required>
                                </div>
                                <div class="form-group">
                                    <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Ngôn ngữ</label>
                                    <input type="text" name="language" value="${fn:escapeXml(empty film.language ? 'Tiếng Việt' : film.language)}" class="form-input" placeholder="Ví dụ: Tiếng Việt, Phụ đề Tiếng Việt" required>
                                </div>
                            </div>

                            <div class="form-row-2">
                                <div class="form-group">
                                    <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Thể loại</label>
                                    <input type="text" name="categories" value="${fn:escapeXml(film.categories)}" class="form-input" placeholder="Nhập thể loại (Ví dụ: Hành động, Phiêu lưu)" required>
                                </div>
                                <div class="form-group">
                                    <label class="form-label">Quốc gia</label>
                                    <select name="country" class="form-input" required>
                                        <option value="Mỹ" ${fn:escapeXml(film.country eq 'Mỹ' ? 'selected' : '')}>Mỹ</option>
                                        <option value="Hàn Quốc" ${fn:escapeXml(film.country eq 'Hàn Quốc' ? 'selected' : '')}>Hàn Quốc</option>
                                        <option value="Nhật Bản" ${fn:escapeXml(film.country eq 'Nhật Bản' ? 'selected' : '')}>Nhật Bản</option>
                                        <option value="Việt Nam" ${fn:escapeXml(empty film.country or film.country eq 'Việt Nam' ? 'selected' : '')}>Việt Nam</option>
                                        <option value="Trung Quốc" ${fn:escapeXml(film.country eq 'Trung Quốc' ? 'selected' : '')}>Trung Quốc</option>
                                        <option value="Anh" ${fn:escapeXml(film.country eq 'Anh' ? 'selected' : '')}>Anh</option>
                                        <option value="Khác" ${fn:escapeXml(film.country eq 'Khác' ? 'selected' : '')}>Khác</option>
                                    </select>
                                </div>
                            </div>

                            <div class="form-row-2">
                                <div class="form-group">
                                    <label class="form-label">Đạo diễn</label>
                                    <input type="text" name="directors" value="${fn:escapeXml(film.directors)}" class="form-input" placeholder="Nhập tên đạo diễn" required>
                                </div>
                                <div class="form-group">
                                    <label class="form-label">Định dạng</label>
                                    <select name="format" class="form-input" required>
                                        <option value="2D" ${fn:escapeXml(empty film.format or film.format eq '2D' ? 'selected' : '')}>2D</option>
                                        <option value="3D" ${fn:escapeXml(film.format eq '3D' ? 'selected' : '')}>3D</option>
                                        <option value="IMAX 2D" ${fn:escapeXml(film.format eq 'IMAX 2D' ? 'selected' : '')}>IMAX 2D</option>
                                        <option value="IMAX 3D" ${fn:escapeXml(film.format eq 'IMAX 3D' ? 'selected' : '')}>IMAX 3D</option>
                                        <option value="4DX" ${fn:escapeXml(film.format eq '4DX' ? 'selected' : '')}>4DX</option>
                                    </select>
                                </div>
                            </div>

                            <div class="form-row-2">
                                <div class="form-group">
                                    <label class="form-label">Diễn viên</label>
                                    <input type="text" name="actors" value="${fn:escapeXml(film.actors)}" class="form-input" placeholder="Nhập diễn viên (cách nhau bằng dấu phẩy)" required>
                                </div>
                                <div class="form-group">
                                    <label class="form-label">Phân loại độ tuổi (Rating)</label>
                                    <select name="ageRating" class="form-input" required>
                                        <option value="P" ${fn:escapeXml(film.ageRating eq 'P' ? 'selected' : '')}>P - Phim dành cho mọi độ tuổi</option>
                                        <option value="K" ${fn:escapeXml(film.ageRating eq 'K' ? 'selected' : '')}>K - Phim cho người xem dưới 13 tuổi có phụ huynh đi cùng</option>
                                        <option value="T13" ${fn:escapeXml(film.ageRating eq 'T13' ? 'selected' : '')}>T13 - Cấm phổ biến đến người xem dưới 13 tuổi</option>
                                        <option value="T16" ${fn:escapeXml(empty film.ageRating or film.ageRating eq 'T16' ? 'selected' : '')}>T16 - Cấm phổ biến đến người xem dưới 16 tuổi</option>
                                        <option value="T18" ${fn:escapeXml(film.ageRating eq 'T18' ? 'selected' : '')}>T18 - Cấm phổ biến đến người xem dưới 18 tuổi</option>
                                    </select>
                                </div>
                            </div>

                            <div class="form-row-2">
                                <div class="form-group">
                                    <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Thời lượng (phút)</label>
                                    <input type="number" name="durationMinutes" value="${fn:escapeXml(film.durationMinutes)}" class="form-input" placeholder="Ví dụ: 120" min="1" required>
                                </div>
                                <div class="form-group">
                                    <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Trạng thái</label>
                                    <select name="status" class="form-input" required>
                                        <option value="showing" ${fn:escapeXml(empty film.status or film.status eq 'showing' ? 'selected' : '')}>Đang chiếu</option>
                                        <option value="coming" ${fn:escapeXml(film.status eq 'coming' ? 'selected' : '')}>Sắp chiếu</option>
                                        <option value="ended" ${fn:escapeXml(film.status eq 'ended' ? 'selected' : '')}>Ngừng chiếu</option>
                                    </select>
                                </div>
                            </div>

                            <div class="form-row-2">
                                <div class="form-group">
                                    <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Ngày khởi chiếu</label>
                                    <input type="date" name="releaseDate" id="releaseDate"
                                           value="${fn:escapeXml(film.releaseDate)}" class="form-input" required>
                                </div>
                                <div class="form-group">
                                    <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Ngày kết thúc chiếu</label>
                                    <input type="date" name="endDate" id="endDate"
                                           value="${fn:escapeXml(film.endDate)}" class="form-input" required>
                                    <small class="form-hint" style="font-size: 11px; color: #94a3b8; margin-top: 4px;">Phim sẽ tự ẩn khỏi trang người dùng sau ngày này.</small>
                                </div>
                            </div>

                            <div class="form-row-2">
                                <div class="form-group">
                                    <label class="form-label">Trailer (YouTube URL)</label>
                                    <input type="url" name="trailerUrl" value="${fn:escapeXml(film.trailerUrl)}" class="form-input" placeholder="https://www.youtube.com/watch?v=..." required>
                                </div>
                                
                                <!-- CLEAN MULTISELECT DROPDOWN UI MATCHING IMAGE [2] -->
                                <div class="form-group">
                                    <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Chiếu tại cụm rạp</label>
                                    <input type="hidden" name="cinemaIdsPresent" value="1">
                                    <div class="cinema-multiselect" id="cinemaMultiselect">
                                        <button type="button" id="cinemaMultiselectTrigger" class="cinema-multiselect-trigger"
                                                aria-haspopup="true" aria-expanded="false" aria-controls="cinemaMultiselectPanel">
                                            <span id="cinemaSelectionSummary" aria-live="polite">Chọn một hoặc nhiều cụm rạp</span>
                                        </button>
                                        <div class="cinema-checkbox-container" id="cinemaMultiselectPanel" role="group" aria-label="Danh sách cụm rạp">
                                            <div class="cinema-option-header">
                                                <button type="button" onclick="toggleAllCinemas(true)">Chọn tất cả</button>
                                                <span style="color:#cbd5e1;">|</span>
                                                <button type="button" onclick="toggleAllCinemas(false)">Bỏ chọn tất cả</button>
                                            </div>
                                            <c:forEach var="c" items="${cinemas}">
                                                <c:set var="isAssigned" value="false"/>
                                                <c:forEach var="assigned" items="${assignedCinemaIds}">
                                                    <c:if test="${assigned eq c.id}">
                                                        <c:set var="isAssigned" value="true"/>
                                                    </c:if>
                                                </c:forEach>
                                                <label class="cinema-option-item">
                                                    <input type="checkbox" name="cinemaIds" value="${c.id}" class="cinema-checkbox"
                                                           <c:if test="${isAssigned}">checked</c:if>>
                                                    <span class="cinema-option-text">
                                                        <c:out value="${c.name}"/>
                                                        <c:if test="${not empty c.address}">
                                                            <small class="cinema-option-sub">(<c:out value="${c.address}"/>)</small>
                                                        </c:if>
                                                    </span>
                                                </label>
                                            </c:forEach>
                                        </div>
                                    </div>
                                    <small class="form-hint" style="font-size: 11px; color: #94a3b8; margin-top: 4px;">Tích chọn các cụm rạp chiếu phim này.</small>
                                </div>
                            </div>

                            <div class="form-group" style="margin-bottom: 0 !important;">
                                <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Mô tả phim</label>
                                <textarea name="description" class="form-input" rows="5" placeholder="Nhập nội dung mô tả ngắn gọn bộ phim..." required>${fn:escapeXml(film.description)}</textarea>
                            </div>
                        </article>

                        <!-- RIGHT COLUMN: HÌNH ẢNH & MEDIA -->
                        <div>
                            <article class="panel" style="padding: 24px; margin-bottom: 24px;">
                                <div class="form-section-title">HÌNH ẢNH & MEDIA</div>

                                <!-- POSTER UPLOAD -->
                                <div class="form-group">
                                    <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Poster phim</label>
                                    <div class="upload-dropzone">
                                        <input type="file" name="thumbnailFile" accept="image/*" onchange="previewImage(this, 'posterPreview')">
                                        <div style="font-size: 0.9rem; font-weight: 600; color: #0f172a; margin-bottom: 4px;">
                                            Kéo & thả file vào đây
                                        </div>
                                        <div style="font-size: 0.8rem; color: #64748b;">hoặc</div>
                                        <button type="button" class="button secondary" style="margin-top: 8px; padding: 6px 14px; font-size: 0.8rem;">Chọn file</button>
                                    </div>
                                    <div class="muted" style="font-size: 0.75rem; margin-top: 6px; text-align: center;">
                                        JPG, PNG, WEBP (tối đa 5MB) • Khuyến nghị poster ngang tỷ lệ 16:9 (1280×720px hoặc 1920×1080px)
                                    </div>
                                    <div style="text-align: center;">
                                        <c:choose>
                                            <c:when test="${not empty film.thumbnail}">
                                                <img id="posterPreview" class="upload-preview" src="${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, film.thumbnail))}" alt="Poster preview">
                                            </c:when>
                                            <c:otherwise>
                                                <img id="posterPreview" class="upload-preview" src="" style="display:none;" alt="Poster preview">
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </div>

                                <!-- BANNER UPLOAD -->
                                <div class="form-group" style="margin-bottom: 0 !important;">
                                    <label class="form-label">Banner phim (không bắt buộc)</label>
                                    <div class="upload-dropzone">
                                        <input type="file" name="bannerFile" accept="image/*" onchange="previewImage(this, 'bannerPreview')">
                                        <div style="font-size: 0.9rem; font-weight: 600; color: #0f172a; margin-bottom: 4px;">
                                            Kéo & thả file vào đây
                                        </div>
                                        <div style="font-size: 0.8rem; color: #64748b;">hoặc</div>
                                        <button type="button" class="button secondary" style="margin-top: 8px; padding: 6px 14px; font-size: 0.8rem;">Chọn file</button>
                                    </div>
                                    <div class="muted" style="font-size: 0.75rem; margin-top: 6px; text-align: center;">
                                        JPG, PNG, WEBP (tối đa 5MB) • Khuyến nghị banner ngang tỷ lệ 16:9 / 21:9 (1920×1080px hoặc 1920×800px)
                                    </div>
                                    <div style="text-align: center;">
                                        <c:choose>
                                            <c:when test="${not empty film.banner}">
                                                <img id="bannerPreview" class="upload-preview" src="${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, film.banner))}" alt="Banner preview">
                                            </c:when>
                                            <c:otherwise>
                                                <img id="bannerPreview" class="upload-preview" src="" style="display:none;" alt="Banner preview">
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </div>
                            </article>

                            <!-- ACTIONS CARD -->
                            <article class="panel" style="padding: 20px;">
                                <div class="form-actions" style="margin: 0;">
                                    <a href="${pageContext.request.contextPath}/admin/films" class="button secondary">Hủy</a>
                                    <button type="submit" class="button primary">
                                        ${fn:escapeXml(empty film.id || film.id eq 0 ? 'Lưu Phim Mới' : 'Cập Nhật Phim')}
                                    </button>
                                </div>
                            </article>
                        </div>
                    </div>
                </form>
            </div>
        </main>
    </div>

    <script>
        function previewImage(input, previewId) {
            var preview = document.getElementById(previewId);
            if (input && input.files && input.files[0]) {
                var reader = new FileReader();
                reader.onload = function(e) {
                    preview.src = e.target.result;
                    preview.style.display = 'inline-block';
                }
                reader.readAsDataURL(input.files[0]);
            }
        }

        async function openDownloadFolderPicker(inputElement, previewId) {
            if ('showOpenFilePicker' in window) {
                try {
                    const [fileHandle] = await window.showOpenFilePicker({
                        startIn: 'downloads',
                        types: [{
                            description: 'Hình ảnh (Image Files)',
                            accept: {
                                'image/*': ['.jpg', '.jpeg', '.png', '.webp', '.gif']
                            }
                        }]
                    });
                    if (fileHandle) {
                        const file = await fileHandle.getFile();
                        const dataTransfer = new DataTransfer();
                        dataTransfer.items.add(file);
                        inputElement.files = dataTransfer.files;
                        previewImage(inputElement, previewId);
                        return true;
                    }
                } catch (err) {
                    if (err.name === 'AbortError') {
                        return true;
                    }
                }
            }
            return false;
        }

        document.querySelectorAll('.upload-dropzone').forEach(function(zone) {
            zone.addEventListener('click', async function(e) {
                var fileInput = zone.querySelector('input[type="file"]');
                if (!fileInput) return;
                var previewId = fileInput.name === 'thumbnailFile' ? 'posterPreview' : 'bannerPreview';
                
                var handled = await openDownloadFolderPicker(fileInput, previewId);
                if (!handled) {
                    fileInput.click();
                }
            });
        });

        function toggleAllCinemas(checked) {
            document.querySelectorAll('.cinema-checkbox').forEach(function(cb) {
                cb.checked = checked;
            });
            updateCinemaSelectionSummary();
        }

        var cinemaMultiselect = document.getElementById('cinemaMultiselect');
        var cinemaMultiselectTrigger = document.getElementById('cinemaMultiselectTrigger');
        
        function updateCinemaSelectionSummary() {
            var selected = Array.from(document.querySelectorAll('.cinema-checkbox:checked'));
            var summary = document.getElementById('cinemaSelectionSummary');
            if (selected.length === 0) {
                summary.textContent = 'Chọn một hoặc nhiều cụm rạp';
            } else if (selected.length === 1) {
                var itemText = selected[0].closest('.cinema-option-item').querySelector('.cinema-option-text');
                summary.textContent = itemText ? itemText.childNodes[0].textContent.trim() : '1 cụm rạp';
            } else {
                summary.textContent = 'Đã chọn ' + selected.length + ' cụm rạp';
            }
        }

        function closeCinemaMultiselect() {
            cinemaMultiselect.classList.remove('is-open');
            cinemaMultiselectTrigger.setAttribute('aria-expanded', 'false');
        }

        cinemaMultiselectTrigger.addEventListener('click', function(e) {
            e.stopPropagation();
            var open = cinemaMultiselect.classList.toggle('is-open');
            cinemaMultiselectTrigger.setAttribute('aria-expanded', String(open));
        });

        document.querySelectorAll('.cinema-checkbox').forEach(function(cb) {
            cb.addEventListener('change', updateCinemaSelectionSummary);
        });

        document.addEventListener('click', function(event) {
            if (!cinemaMultiselect.contains(event.target)) closeCinemaMultiselect();
        });

        document.addEventListener('keydown', function(event) {
            if (event.key === 'Escape' && cinemaMultiselect.classList.contains('is-open')) {
                closeCinemaMultiselect();
                cinemaMultiselectTrigger.focus();
            }
        });

        updateCinemaSelectionSummary();
    </script>
</body>
</html>
