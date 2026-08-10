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
    <title>Quản lý Về Chúng Tôi - Admin CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260806">
    <style>
        .about-admin-grid {
            display: flex;
            flex-direction: column;
            gap: 24px;
            margin-top: 20px;
        }

        .admin-card-section {
            background: #FFFFFF;
            border-radius: 14px;
            border: 1px solid #E2E8F0;
            padding: 24px;
            box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03);
        }

        .admin-card-title {
            font-size: 18px;
            font-weight: 700;
            color: #0F172A;
            margin: 0 0 20px 0;
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding-bottom: 12px;
            border-bottom: 1px solid #F1F5F9;
        }

        .members-admin-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 20px;
        }

        .member-edit-box {
            background: #F8FAFC;
            border: 1px solid #E2E8F0;
            border-radius: 12px;
            padding: 18px;
            display: flex;
            flex-direction: column;
            gap: 12px;
        }

        .member-edit-header {
            font-size: 14px;
            font-weight: 700;
            color: #6D28D9;
            display: flex;
            align-items: center;
            gap: 6px;
        }

        .member-avatar-preview {
            display: flex;
            align-items: center;
            gap: 14px;
            margin-bottom: 6px;
        }

        .member-avatar-preview img {
            width: 56px;
            height: 56px;
            border-radius: 50%;
            object-fit: cover;
            border: 2px solid #6D28D9;
        }

        .features-admin-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 20px;
        }

        .feature-edit-box {
            background: #F8FAFC;
            border: 1px solid #E2E8F0;
            border-radius: 12px;
            padding: 18px;
            display: flex;
            flex-direction: column;
            gap: 12px;
        }

        .field-group {
            display: flex;
            flex-direction: column;
            gap: 4px;
        }

        .field-group label {
            font-size: 12px;
            font-weight: 600;
            color: #475569;
        }

        .field-group input[type="text"], .field-group textarea, .field-group select {
            width: 100%;
            padding: 8px 12px;
            border: 1px solid #CBD5E1;
            border-radius: 8px;
            font-size: 13.5px;
            box-sizing: border-box;
        }

        .field-group textarea {
            min-height: 80px;
            resize: vertical;
        }

        @media (max-width: 980px) {
            .members-admin-grid {
                grid-template-columns: 1fr;
            }
            .features-admin-grid {
                grid-template-columns: 1fr;
            }
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

            <section class="portal-head">
                <div>
                    <h1>Quản lý trang Về Chúng Tôi</h1>
                    <p class="muted">Cập nhật danh sách 4 thành viên sáng lập và 3 khung giá trị cốt lõi hiển thị tại trang công khai.</p>
                </div>
                <div>
                    <a href="${pageContext.request.contextPath}/ve-chung-toi" target="_blank" rel="noopener" class="btn-secondary" style="font-size:13px;padding:8px 14px;display:inline-flex;align-items:center;gap:6px;">
                        <span>Xem trang công khai</span>
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px;"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
                    </a>
                </div>
            </section>

            <form method="post" action="${pageContext.request.contextPath}/admin/content/about-us" enctype="multipart/form-data">
                <cb:csrf/>
                
                <div class="about-admin-grid">
                    <!-- SECTION 1: 4 TEAM MEMBERS -->
                    <div class="admin-card-section">
                        <div class="admin-card-title">
                            <span>Quản lý 4 Thành viên Đội ngũ</span>
                            <span style="font-size:12px;font-weight:normal;color:#64748B;">Mã tự động theo thứ tự 1..4</span>
                        </div>

                        <div class="members-admin-grid">
                            <c:forEach var="member" items="${members}" varStatus="status">
                                <c:set var="idx" value="${status.index + 1}" />
                                <div class="member-edit-box">
                                    <div class="member-edit-header">
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px;"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                                        <span>Thành viên #${idx}</span>
                                    </div>

                                    <div class="member-avatar-preview">
                                        <c:set var="resolvedUrl" value="${cbf:assetUrl(pageContext.request.contextPath, member.imageUrl)}" />
                                        <img src="${fn:escapeXml(resolvedUrl)}" 
                                             alt="Avatar" 
                                             onerror="this.onerror=null; this.src='${pageContext.request.contextPath}/assets/img/default-film.jpg';">
                                        <div>
                                            <div style="font-size:13px;font-weight:600;color:#0F172A;">${fn:escapeXml(member.name)}</div>
                                            <div style="font-size:11px;color:#64748B;">${fn:escapeXml(member.imageUrl)}</div>
                                        </div>
                                    </div>

                                    <div class="field-group">
                                        <label>Họ và tên</label>
                                        <input type="text" name="memberName_${idx}" value="${fn:escapeXml(member.name)}" required placeholder="Nguyễn Minh Tâm...">
                                    </div>

                                    <div class="field-group">
                                        <label>Chức danh / Vai trò</label>
                                        <input type="text" name="memberRole_${idx}" value="${fn:escapeXml(member.role)}" placeholder="Trưởng nhóm / Founder...">
                                    </div>

                                    <div class="field-group">
                                        <label>Tải ảnh Avatar mới (Reupload)</label>
                                        <input type="file" name="memberImageFile_${idx}" accept="image/*">
                                    </div>

                                    <div class="field-group">
                                        <label>Hoặc Đường dẫn ảnh (Image URL)</label>
                                        <input type="text" name="memberImageUrl_${idx}" value="${fn:escapeXml(member.imageUrl)}" placeholder="/images/1.jpg hoặc https://...">
                                    </div>
                                </div>
                            </c:forEach>
                        </div>
                    </div>

                    <!-- SECTION 2: 3 CORE FEATURES -->
                    <div class="admin-card-section">
                        <div class="admin-card-title">
                            <span>Quản lý 3 Khung giá trị cốt lõi</span>
                            <span style="font-size:12px;font-weight:normal;color:#64748B;">Sứ mệnh • Trải nghiệm đặt vé • Hệ thống rạp</span>
                        </div>

                        <div class="features-admin-grid">
                            <c:forEach var="feature" items="${features}" varStatus="status">
                                <c:set var="fidx" value="${status.index + 1}" />
                                <div class="feature-edit-box">
                                    <div class="field-group">
                                        <label>Tiêu đề phần #${fidx}</label>
                                        <input type="text" name="featureTitle_${fidx}" value="${fn:escapeXml(feature.title)}" required placeholder="Sứ mệnh...">
                                    </div>

                                    <div class="field-group">
                                        <label>Biểu tượng (Icon)</label>
                                        <select name="featureIcon_${fidx}">
                                            <option value="target" ${feature.icon eq 'target' ? 'selected' : ''}>🎯 Mục tiêu / Sứ mệnh (Target)</option>
                                            <option value="ticket" ${feature.icon eq 'ticket' ? 'selected' : ''}>🎟️ Vé / Đặt vé (Ticket)</option>
                                            <option value="screen" ${feature.icon eq 'screen' ? 'selected' : ''}>🎬 Màn hình rạp (Screen)</option>
                                        </select>
                                    </div>

                                    <div class="field-group">
                                        <label>Mô tả chi tiết</label>
                                        <textarea name="featureDesc_${fidx}" placeholder="Nhập mô tả nội dung...">${fn:escapeXml(feature.description)}</textarea>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>
                    </div>

                    <div style="display:flex;justify-content:flex-end;gap:12px;">
                        <button type="submit" class="btn-primary" style="height:44px;padding:0 28px;font-size:15px;font-weight:600;background:#6D28D9;">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;margin-right:6px;"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
                            <span>Lưu thay đổi</span>
                        </button>
                    </div>
                </div>
            </form>
        </div>
    </main>
</div>
</body>
</html>
