<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${fn:escapeXml(empty room.id || room.id eq 0 ? 'Thêm Phòng Mới' : 'Chỉnh Sửa Phòng')} - CineBook Enterprise</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
    <style>
        .ent-page-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 24px;
            padding-bottom: 16px;
            border-bottom: 1px solid #e2e8f0;
            flex-wrap: wrap;
            gap: 12px;
        }

        .ent-title {
            font-size: 1.5rem;
            font-weight: 800;
            color: #0f172a;
            margin: 0;
            letter-spacing: -0.02em;
        }

        .ent-subtitle {
            font-size: 0.88rem;
            color: #475569;
            margin: 4px 0 0 0;
        }

        .ent-form-card {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 10px;
            padding: 32px;
            max-width: 640px;
            margin: 0 auto;
        }

        .ent-form-group {
            margin-bottom: 20px;
        }

        .ent-form-label {
            display: block;
            font-size: 0.88rem;
            font-weight: 700;
            color: #0f172a;
            margin-bottom: 6px;
        }

        .ent-form-input, .ent-form-select {
            width: 100%;
            background: #ffffff;
            border: 1px solid #cbd5e1;
            border-radius: 6px;
            padding: 10px 14px;
            font-size: 0.9rem;
            color: #0f172a;
            font-weight: 500;
            outline: none;
            transition: border-color 0.15s ease;
        }

        .ent-form-input:focus, .ent-form-select:focus {
            border-color: #0284c7;
        }

        .ent-btn-group {
            display: flex;
            gap: 12px;
            justify-content: flex-end;
            margin-top: 32px;
            border-top: 1px solid #e2e8f0;
            padding-top: 20px;
        }

        .ent-btn-cancel {
            border: 1px solid #cbd5e1;
            background: #ffffff;
            color: #475569;
            padding: 10px 20px;
            border-radius: 6px;
            font-size: 0.88rem;
            font-weight: 700;
            text-decoration: none;
        }
        .ent-btn-submit {
            background: #0284c7;
            border: 1px solid #0284c7;
            color: #ffffff;
            padding: 10px 24px;
            border-radius: 6px;
            font-size: 0.88rem;
            font-weight: 700;
            cursor: pointer;
        }
        .ent-btn-submit:hover {
            background: #0369a1;
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

                <!-- ENTERPRISE PAGE HEADER -->
                <div class="ent-page-header">
                    <div>
                        <h1 class="ent-title">${fn:escapeXml(empty room.id || room.id eq 0 ? 'Thêm phòng chiếu mới' : 'Chỉnh sửa phòng chiếu')}</h1>
                        <p class="ent-subtitle">
                            ${fn:escapeXml(empty room.id || room.id eq 0 ? 'Tạo mới thông tin phòng chiếu và chuyển sang thiết kế ma trận ghế.' : 'Cập nhật thông tin phòng chiếu.')}
                        </p>
                    </div>
                    <div>
                        <a href="${pageContext.request.contextPath}/admin/rooms" class="ent-btn-cancel">
                            ← Danh sách phòng
                        </a>
                    </div>
                </div>

                <!-- ENTERPRISE FORM CARD -->
                <div class="ent-form-card">
                    <form method="post" action="${pageContext.request.contextPath}/admin/rooms">
                        <cb:csrf/>
                        <input type="hidden" name="id" value="${fn:escapeXml(room.id)}">

                        <div class="ent-form-group">
                            <label class="ent-form-label"><span style="color:#ef4444; font-weight:bold;">*</span> Cụm rạp chiếu</label>
                            <select name="cinemaId" class="ent-form-select" required>
                                <c:forEach var="cinema" items="${cinemas}">
                                    <option value="${fn:escapeXml(cinema.id)}" ${fn:escapeXml(room.cinemaId eq cinema.id ? 'selected' : '')}>${fn:escapeXml(cinema.name)} (${fn:escapeXml(cinema.cityName)})</option>
                                </c:forEach>
                            </select>
                        </div>

                        <div class="ent-form-group">
                            <label class="ent-form-label"><span style="color:#ef4444; font-weight:bold;">*</span> Tên phòng chiếu</label>
                            <input type="text" name="name" value="${fn:escapeXml(room.name)}" class="ent-form-input" placeholder="Ví dụ: Phòng 01 - IMAX, Phòng 02 - Standard..." required>
                        </div>

                        <div class="ent-form-group">
                            <label class="ent-form-label"><span style="color:#ef4444; font-weight:bold;">*</span> Loại phòng</label>
                            <select name="roomType" class="ent-form-select" required>
                                <option value="STANDARD" ${empty room.roomType or room.roomType eq 'STANDARD' ? 'selected' : ''}>STANDARD — Phòng thường</option>
                                <option value="VIP" ${room.roomType eq 'VIP' ? 'selected' : ''}>VIP — Phòng cao cấp</option>
                            </select>
                            <small class="muted">Phân loại này quyết định chính sách đổi phòng.</small>
                        </div>

                        <c:if test="${empty room.id || room.id eq 0}">
                            <div style="background:#f8fafc; border:1px solid #e2e8f0; border-radius:8px; padding:16px; margin-top:20px; margin-bottom:20px;">
                                <div style="font-weight:700; color:#0f172a; margin-bottom:12px; font-size:0.9rem;">KHỞI TẠO MA TRẬN GHẾ TỰ ĐỘNG</div>
                                <div style="display:grid; grid-template-columns:1fr 1fr; gap:16px; margin-bottom:12px;">
                                    <div class="ent-form-group" style="margin-bottom:0;">
                                        <label class="ent-form-label"><span style="color:#ef4444; font-weight:bold;">*</span> Số hàng ghế</label>
                                        <input type="number" min="1" max="26" name="rowCount" value="10" class="ent-form-input" required title="Tương ứng các hàng A, B, C...">
                                    </div>
                                    <div class="ent-form-group" style="margin-bottom:0;">
                                        <label class="ent-form-label"><span style="color:#ef4444; font-weight:bold;">*</span> Số ghế mỗi hàng</label>
                                        <input type="number" min="1" max="30" name="seatsPerRow" value="12" class="ent-form-input" required title="Số lượng ghế trên 1 hàng">
                                    </div>
                                </div>
                                <div class="ent-form-group" style="margin-bottom:0;">
                                    <label class="ent-form-label">Các hàng ghế VIP (Phân cách bằng dấu phẩy)</label>
                                    <input type="text" name="vipRows" value="C,D,E,F,G" class="ent-form-input" placeholder="Ví dụ: C,D,E,F,G">
                                </div>
                            </div>
                        </c:if>

                        <div class="ent-btn-group">
                            <a href="${pageContext.request.contextPath}/admin/rooms" class="ent-btn-cancel">Hủy bỏ</a>
                            <button type="submit" class="ent-btn-submit">
                                ${fn:escapeXml(empty room.id || room.id eq 0 ? 'Tạo phòng & Vẽ ghế' : 'Cập nhật tên phòng')}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </main>
    </div>
</body>
</html>
