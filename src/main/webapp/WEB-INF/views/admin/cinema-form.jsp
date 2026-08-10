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
    <title>${fn:escapeXml(empty cinema.id || cinema.id eq 0 ? 'Thêm Rạp Chiếu Mới' : 'Chỉnh Sửa Rạp Chiếu')} - CineBook Admin</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805i">
    <style>
        .cinema-form-header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            margin-bottom: 24px;
        }
        .cinema-form-breadcrumb {
            font-size: 0.85rem;
            color: #64748b;
            margin-bottom: 6px;
        }
        .cinema-form-breadcrumb a {
            color: #64748b;
            text-decoration: none;
        }
        .cinema-form-breadcrumb a:hover {
            color: #38bdf8;
        }
        .cinema-form-grid {
            display: grid;
            grid-template-columns: 2fr 1fr;
            gap: 24px;
        }
        @media (max-width: 1024px) {
            .cinema-form-grid {
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

        /* MEDIA DROPZONE STYLING MATCHING IMAGE [2] */
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
        .upload-preview {
            max-width: 100%;
            max-height: 180px;
            border-radius: 8px;
            margin-top: 12px;
            object-fit: cover;
        }
        .form-actions {
            margin-top: 0;
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
        .form-label,
        .admin-body label {
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
        .admin-body input[type="url"] {
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
        .admin-body textarea.form-input,
        .admin-body textarea {
            height: auto !important;
            min-height: 110px !important;
            max-height: none !important;
            padding: 12px 14px !important;
            line-height: 1.5 !important;
            border: 1px solid #cbd5e1 !important;
            border-radius: 8px !important;
            font-size: 13.5px !important;
        }

        .btn-submit-purple {
            background: #6d28d9 !important;
            color: #ffffff !important;
            border: none !important;
            border-radius: 8px !important;
            padding: 10px 20px !important;
            font-weight: 600 !important;
            font-size: 13.5px !important;
            cursor: pointer !important;
            transition: background 0.15s ease !important;
            box-shadow: 0 2px 6px rgba(109, 40, 217, 0.2) !important;
        }
        .btn-submit-purple:hover {
            background: #5b21b6 !important;
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
                <div class="cinema-form-header">
                    <div>
                        <div class="cinema-form-breadcrumb">
                            <a href="${pageContext.request.contextPath}/admin/dashboard">Dashboard</a> &gt; 
                            <a href="${pageContext.request.contextPath}/admin/cinemas">Quản lý rạp</a> &gt; 
                            <span>${fn:escapeXml(empty cinema.id || cinema.id eq 0 ? 'Thêm rạp chiếu mới' : 'Chỉnh sửa rạp chiếu')}</span>
                        </div>
                        <h1 style="font-size: 1.6rem; font-weight: 800; color: #0f172a; margin: 0;">
                            ${fn:escapeXml(empty cinema.id || cinema.id eq 0 ? 'Thêm Rạp Chiếu Mới' : 'Chỉnh Sửa Rạp Chiếu')}
                        </h1>
                        <p class="muted" style="margin: 4px 0 0 0;">
                            <c:choose><c:when test="${sessionScope.currentUser.role eq 'manager'}">Cập nhật thông tin vận hành và hình ảnh của rạp được gán.</c:when><c:otherwise>Cấu hình thông tin địa lý, thành phố, hotline và hình ảnh banner quảng cáo rạp chiếu.</c:otherwise></c:choose>
                        </p>
                    </div>
                    <div>
                        <a href="${pageContext.request.contextPath}/admin/cinemas" class="button secondary" style="display: inline-flex; align-items: center; gap: 6px; text-decoration: none; padding: 10px 18px; border-radius: 8px; font-weight: 600;">
                            ← Quay lại danh sách
                        </a>
                    </div>
                </div>

                <!-- MAIN FORM -->
                <form method="post" action="${pageContext.request.contextPath}/admin/cinemas" enctype="multipart/form-data">
                    <cb:csrf/>
                    <c:if test="${not empty cinema.id && cinema.id gt 0}">
                        <input type="hidden" name="id" value="${fn:escapeXml(cinema.id)}">
                    </c:if>

                    <div class="cinema-form-grid">
                        <!-- LEFT COLUMN: THÔNG TIN RẠP CHIẾU -->
                        <article class="panel" style="padding: 24px;">
                            <div class="form-section-title">THÔNG TIN RẠP CHIẾU</div>

                            <c:if test="${sessionScope.currentUser.role eq 'admin'}">
                            <div class="form-group">
                                <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Loại cụm rạp</label>
                                <select name="cinemaType" class="form-input" required>
                                    <option value="STANDARD" ${empty cinema.cinemaType or cinema.cinemaType eq 'STANDARD' ? 'selected' : ''}>STANDARD — Cụm rạp thường</option>
                                    <option value="VIP" ${cinema.cinemaType eq 'VIP' ? 'selected' : ''}>VIP — Cụm rạp cao cấp</option>
                                </select>
                            </div>

                            <div class="form-row-2">
                                <div class="form-group">
                                    <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Tên rạp chiếu</label>
                                    <input type="text" name="name" value="${fn:escapeXml(cinema.name)}" class="form-input" placeholder="Ví dụ: CineBook FPT Center" required>
                                </div>
                                <div class="form-group">
                                    <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Tỉnh / Thành phố</label>
                                    <select name="cityId" id="citySelect" class="form-input" required onchange="toggleOtherCityInput(this)">
                                        <option value="">-- Chọn Thành Phố --</option>
                                        <c:forEach var="city" items="${cities}">
                                            <option value="${fn:escapeXml(city.key)}" ${fn:escapeXml(cinema.cityId eq city.key ? 'selected' : '')}>${fn:escapeXml(city.value)}</option>
                                        </c:forEach>
                                        <option value="other">-- Khác (Nhập thành phố mới...) --</option>
                                    </select>
                                    <div id="otherCityContainer" style="display:none; margin-top:8px;">
                                        <input type="text" name="newCityName" id="newCityInput" class="form-input" placeholder="Nhập tên Tỉnh / Thành phố mới...">
                                    </div>
                                </div>
                            </div>
                            </c:if>

                            <c:if test="${sessionScope.currentUser.role eq 'manager'}">
                                <div style="margin-bottom:18px;padding:12px 14px;border:1px solid #ddd3fa;border-radius:9px;background:#f6f3ff;">
                                    <strong>${fn:escapeXml(cinema.name)}</strong>
                                    <div class="muted" style="margin-top:3px;">Tên, thành phố, loại và trạng thái rạp do Admin quản lý.</div>
                                </div>
                            </c:if>

                            <div class="form-row-2">
                                <div class="form-group">
                                    <label class="form-label">Số điện thoại liên hệ</label>
                                    <input type="text" name="phone" value="${fn:escapeXml(cinema.phone)}" class="form-input" placeholder="1900 1234...">
                                </div>
                                <c:if test="${sessionScope.currentUser.role eq 'admin'}">
                                <div class="form-group">
                                    <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Trạng thái</label>
                                    <select name="status" class="form-input" required>
                                        <option value="active" ${fn:escapeXml(cinema.status eq 'active' || empty cinema.status ? 'selected' : '')}>Hoạt động (Active)</option>
                                        <option value="inactive" ${fn:escapeXml(cinema.status eq 'inactive' ? 'selected' : '')}>Tạm ngưng (Inactive)</option>
                                    </select>
                                </div>
                                </c:if>
                            </div>

                            <div class="form-group">
                                <label class="form-label"><span style="color:#ef4444; font-weight:700;">*</span> Địa chỉ chi tiết</label>
                                <input type="text" name="address" value="${fn:escapeXml(cinema.address)}" class="form-input" placeholder="Số nhà, Đường, Phường/Xã, Quận/Huyện..." required>
                            </div>

                            <!-- Special Cinema Confirmation -->
                            <c:if test="${sessionScope.currentUser.role eq 'admin'}">
                            <div style="margin-bottom: 18px; padding: 14px 16px; background: #f3e8ff; border: 1px solid #e9d5ff; border-radius: 10px; display: flex; align-items: center; justify-content: space-between; gap: 12px;">
                                <div>
                                    <strong style="font-size: 13.5px; color: #6d28d9; display: flex; align-items: center; gap: 6px;">
                                        Phân loại rạp đặc biệt
                                    </strong>
                                    <span style="font-size: 12px; color: #64748b; display: block; margin-top: 2px;">
                                        Tích chọn nếu bạn muốn đưa cụm rạp này vào danh sách Rạp Đặc Biệt (Premium Lounge). Nếu không chọn, mặc định rạp sẽ là Rạp thường.
                                    </span>
                                </div>
                                <label style="display: inline-flex; align-items: center; gap: 8px; cursor: pointer; white-space: nowrap; font-weight: 700; color: #6d28d9; font-size: 13px; margin: 0 !important;">
                                    <input type="checkbox" name="isSpecial" value="true" ${isSpecial ? 'checked' : ''} style="width: 18px; height: 18px; accent-color: #6d28d9; cursor: pointer;">
                                    <span>Là Rạp đặc biệt</span>
                                </label>
                            </div>
                            </c:if>

                            <div class="form-group" style="margin-bottom: 0 !important;">
                                <label class="form-label">Mô tả rạp/cơ sở chiếu / Hướng dẫn chỉ đường</label>
                                <textarea name="description" class="form-input" rows="4" placeholder="Giới thiệu trang thiết bị rạp (IMAX, Dolby Atmos), bãi đỗ xe...">${fn:escapeXml(cinema.description)}</textarea>
                            </div>
                        </article>

                        <!-- RIGHT COLUMN: HÌNH ẢNH & MEDIA MATCHING IMAGE [2] -->
                        <div>
                            <article class="panel" style="padding: 24px; margin-bottom: 24px;">
                                <div class="form-section-title">HÌNH ẢNH & MEDIA</div>

                                <!-- AVATAR IMAGE UPLOAD -->
                                <div class="form-group" style="margin-bottom: 20px !important;">
                                    <label class="form-label">Ảnh đại diện (Avatar)</label>
                                    <input type="hidden" name="avatar" value="${fn:escapeXml(cinema.avatar)}">
                                    <div class="upload-dropzone">
                                        <input type="file" name="avatarFile" accept="image/*" onchange="previewImage(this, 'avatarPreview')">
                                        <div style="font-size: 0.9rem; font-weight: 600; color: #0f172a; margin-bottom: 4px;">
                                            Kéo & thả file vào đây
                                        </div>
                                        <div style="font-size: 0.8rem; color: #64748b;">hoặc</div>
                                        <button type="button" class="button secondary" style="margin-top: 8px; padding: 6px 14px; font-size: 0.8rem;">Chọn file</button>
                                    </div>
                                    <div class="muted" style="font-size: 0.75rem; margin-top: 6px; text-align: center;">
                                        JPG, PNG, WEBP (tối đa 5MB) • Kích thước đề xuất: 800×800px
                                    </div>
                                    <div style="text-align: center;">
                                        <c:choose>
                                            <c:when test="${not empty cinema.avatar}">
                                                <img id="avatarPreview" class="upload-preview" src="${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, cinema.avatar))}" alt="Avatar preview">
                                            </c:when>
                                            <c:otherwise>
                                                <img id="avatarPreview" class="upload-preview" src="" style="display:none;" alt="Avatar preview">
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </div>

                                <!-- BANNER IMAGE UPLOAD -->
                                <div class="form-group" style="margin-bottom: 0 !important;">
                                    <label class="form-label">Banner rạp chiếu phim (không bắt buộc)</label>
                                    <input type="text" name="bannerUrl" value="${fn:escapeXml(cinema.bannerUrl)}" placeholder="Hoặc nhập URL banner..." class="form-input" style="margin-bottom: 12px !important;">
                                    <div class="upload-dropzone">
                                        <input type="file" name="bannerFile" accept="image/*" onchange="previewImage(this, 'bannerPreview')">
                                        <div style="font-size: 0.9rem; font-weight: 600; color: #0f172a; margin-bottom: 4px;">
                                            Kéo & thả file vào đây
                                        </div>
                                        <div style="font-size: 0.8rem; color: #64748b;">hoặc</div>
                                        <button type="button" class="button secondary" style="margin-top: 8px; padding: 6px 14px; font-size: 0.8rem;">Chọn file</button>
                                    </div>
                                    <div class="muted" style="font-size: 0.75rem; margin-top: 6px; text-align: center;">
                                        JPG, PNG, WEBP (tối đa 5MB) • Kích thước đề xuất: 1920×1080px
                                    </div>
                                    <div style="text-align: center;">
                                        <c:choose>
                                            <c:when test="${not empty cinema.bannerUrl}">
                                                <img id="bannerPreview" class="upload-preview" src="${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, cinema.bannerUrl))}" alt="Banner preview">
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
                                    <a href="${pageContext.request.contextPath}/admin/cinemas" class="button secondary">Hủy bỏ</a>
                                    <button type="submit" class="btn-submit-purple">
                                        ${fn:escapeXml(empty cinema.id || cinema.id eq 0 ? 'Lưu & Tiếp tục Quản lý Phim' : 'Cập nhật rạp chiếu')}
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
                var previewId = fileInput.name === 'avatarFile' ? 'avatarPreview' : 'bannerPreview';
                
                var handled = await openDownloadFolderPicker(fileInput, previewId);
                if (!handled) {
                    fileInput.click();
                }
            });
        });

        function toggleOtherCityInput(selectEl) {
            var container = document.getElementById('otherCityContainer');
            var input = document.getElementById('newCityInput');
            if (!container || !input) return;
            if (selectEl && selectEl.value === 'other') {
                container.style.display = 'block';
                input.required = true;
                input.focus();
            } else {
                container.style.display = 'none';
                input.required = false;
                input.value = '';
            }
        }
        document.addEventListener('DOMContentLoaded', function() {
            var selectEl = document.getElementById('citySelect');
            if (selectEl) {
                toggleOtherCityInput(selectEl);
            }
        });
    </script>
</body>
</html>
