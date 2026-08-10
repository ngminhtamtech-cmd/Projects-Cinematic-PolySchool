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
    <title>Điều kiện hoàn tiền - Admin CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260806">
    <style>
        .policy-editor-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 24px;
            margin-top: 20px;
        }

        .policy-panel {
            background: #FFFFFF;
            border-radius: 14px;
            border: 1px solid #E2E8F0;
            padding: 24px;
            box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03);
            display: flex;
            flex-direction: column;
        }

        .policy-panel-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 20px;
            padding-bottom: 14px;
            border-bottom: 1px solid #F1F5F9;
        }

        .policy-panel-title {
            font-size: 17px;
            font-weight: 700;
            color: #0F172A;
            margin: 0;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .policy-form-group {
            margin-bottom: 18px;
        }

        .policy-form-group label {
            display: block;
            font-size: 13px;
            font-weight: 600;
            color: #334155;
            margin-bottom: 6px;
        }

        .policy-form-group input[type="text"] {
            width: 100%;
            height: 42px;
            padding: 8px 14px;
            border: 1px solid #CBD5E1;
            border-radius: 8px;
            font-size: 14px;
            color: #0F172A;
            box-sizing: border-box;
            outline: none;
            transition: border-color 0.2s ease;
        }

        .policy-form-group input[type="text"]:focus {
            border-color: #6D28D9;
            box-shadow: 0 0 0 3px rgba(109, 40, 217, 0.1);
        }

        .policy-form-group textarea {
            width: 100%;
            min-height: 380px;
            padding: 14px;
            border: 1px solid #CBD5E1;
            border-radius: 8px;
            font-size: 14px;
            line-height: 1.6;
            color: #0F172A;
            box-sizing: border-box;
            outline: none;
            resize: vertical;
            transition: border-color 0.2s ease;
        }

        .policy-form-group textarea:focus {
            border-color: #6D28D9;
            box-shadow: 0 0 0 3px rgba(109, 40, 217, 0.1);
        }

        .policy-preview-box {
            background: #FAF5FF;
            border: 1px solid #F3E8FF;
            border-radius: 10px;
            padding: 20px;
            font-size: 14px;
            line-height: 1.7;
            color: #334155;
            white-space: pre-line;
            flex: 1;
            overflow-y: auto;
            max-height: 480px;
        }

        .policy-actions {
            display: flex;
            align-items: center;
            justify-content: flex-end;
            gap: 12px;
            margin-top: 10px;
        }

        @media (max-width: 980px) {
            .policy-editor-grid {
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
                    <h1>Quản lý Điều kiện hoàn tiền</h1>
                    <p class="muted">Chỉnh sửa, xem trước và xuất bản điều kiện hoàn tiền công khai cho toàn hệ thống CineBook.</p>
                </div>
            </section>

            <div class="policy-editor-grid">
                <!-- PANEL 1: EDIT FORM -->
                <div class="policy-panel">
                    <div class="policy-panel-header">
                        <h2 class="policy-panel-title">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#6D28D9" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                            <span>Soạn thảo & Chỉnh sửa</span>
                        </h2>
                        <c:if test="${not empty policyDraft}">
                            <span class="badge" style="background:#FEF3C7;color:#D97706;border:1px solid #FDE68A;font-weight:600;">Đang có bản nháp</span>
                        </c:if>
                    </div>

                    <form method="post" action="${pageContext.request.contextPath}/admin/content/refund-policy">
                        <cb:csrf/>
                        <div class="policy-form-group">
                            <label><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Tiêu đề chính sách</label>
                            <input type="text" name="title" required maxlength="255" value="<c:out value='${empty policyDraft ? policyPublished.title : policyDraft.title}'/>" placeholder="Điều kiện & Quy định hoàn tiền...">
                        </div>

                        <div class="policy-form-group">
                            <label><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Nội dung chính sách (Mỗi dòng là một quy định)</label>
                            <textarea name="bodyText" required placeholder="Nhập từng quy định hoàn tiền trên một dòng..."><c:out value="${empty policyDraft ? policyPublished.bodyText : policyDraft.bodyText}"/></textarea>
                        </div>

                        <div class="policy-actions">
                            <button type="submit" name="action" value="save" class="btn-secondary" style="height:38px;padding:0 16px;font-weight:600;">
                                Lưu bản nháp
                            </button>
                            <button type="submit" name="action" value="publish" class="btn-primary" style="height:38px;padding:0 18px;font-weight:600;background:#6D28D9;" onclick="return confirm('Bạn có chắc chắn muốn XUẤT BẢN phiên bản điều kiện hoàn tiền mới này không?')">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:15px;height:15px;"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
                                <span>Xuất bản ngay</span>
                            </button>
                        </div>
                    </form>
                </div>

                <!-- PANEL 2: CURRENT PUBLISHED VERSION -->
                <div class="policy-panel">
                    <div class="policy-panel-header">
                        <h2 class="policy-panel-title">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#059669" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
                            <span>Đang hiển thị công khai (v<c:out value="${policyPublished.versionNumber}"/>)</span>
                        </h2>
                        <a href="${pageContext.request.contextPath}/refund-policy" target="_blank" rel="noopener" class="btn-secondary" style="font-size:12px;padding:4px 10px;height:auto;display:inline-flex;align-items:center;gap:4px;">
                            <span>Mở trang công khai</span>
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:13px;height:13px;"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
                        </a>
                    </div>

                    <h3 style="font-size:16px;font-weight:700;color:#0F172A;margin:0 0 12px 0;"><c:out value="${policyPublished.title}"/></h3>
                    
                    <div class="policy-preview-box">
                        <c:out value="${policyPublished.bodyText}"/>
                    </div>

                    <div style="margin-top:16px;font-size:12px;color:#64748B;display:flex;align-items:center;justify-content:space-between;">
                        <span>Cập nhật gần nhất: <c:out value="${policyPublished.updatedAt}"/></span>
                        <span style="font-weight:600;color:#059669;">● Đang áp dụng</span>
                    </div>
                </div>
            </div>
        </div>
    </main>
</div>
</body>
</html>
