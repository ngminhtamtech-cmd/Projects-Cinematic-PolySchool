<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><c:out value="${policy.title}"/> - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/layout.css">
    <style>
        .terms-page-wrapper {
            display: flex;
            flex-direction: column;
            min-height: 100vh;
            background: #F8FAFC;
        }

        .terms-main-content {
            flex: 1 0 auto;
            width: 100%;
            max-width: 920px;
            margin: 40px auto 60px;
            padding: 0 20px;
            box-sizing: border-box;
        }

        .terms-card {
            background: #FFFFFF;
            border-radius: 16px;
            border: 1px solid #E2E8F0;
            border-top: 4px solid #2563EB;
            box-shadow: 0 10px 30px -5px rgba(15, 23, 42, 0.06), 0 4px 12px -2px rgba(15, 23, 42, 0.03);
            padding: 36px 40px;
        }

        .terms-header {
            display: flex;
            align-items: flex-start;
            justify-content: space-between;
            gap: 16px;
            padding-bottom: 24px;
            margin-bottom: 28px;
            border-bottom: 1px solid #F1F5F9;
        }

        .terms-header-left h1 {
            font-size: 26px;
            font-weight: 700;
            color: #0F172A;
            margin: 0 0 8px 0;
            line-height: 1.3;
            letter-spacing: -0.01em;
        }

        .terms-meta {
            display: flex;
            align-items: center;
            gap: 10px;
            font-size: 13px;
            color: #64748B;
        }

        .terms-badge {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            padding: 3px 10px;
            background: #EFF6FF;
            color: #1D4ED8;
            font-size: 12px;
            font-weight: 600;
            border-radius: 20px;
            border: 1px solid #BFDBFE;
        }

        .terms-content-box {
            font-size: 15px;
            line-height: 1.85;
            color: #334155;
            white-space: pre-line;
            background: #F8FAFC;
            border: 1px solid #E2E8F0;
            border-radius: 12px;
            padding: 24px 28px;
        }

        .terms-quick-actions {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-top: 32px;
            padding-top: 20px;
            border-top: 1px solid #F1F5F9;
            gap: 12px;
            flex-wrap: wrap;
        }

        .terms-back-btn {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            color: #475569;
            font-size: 14px;
            font-weight: 600;
            text-decoration: none;
            padding: 8px 16px;
            border-radius: 8px;
            background: #F1F5F9;
            transition: all 0.2s ease;
        }

        .terms-back-btn:hover {
            background: #E2E8F0;
            color: #0F172A;
        }

        .terms-action-group {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .terms-link-btn {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            color: #1D4ED8;
            font-size: 13px;
            font-weight: 600;
            text-decoration: none;
            padding: 8px 14px;
            border-radius: 8px;
            background: #EFF6FF;
            transition: all 0.2s ease;
        }

        .terms-link-btn:hover {
            background: #DBEAFE;
            color: #1E40AF;
        }

        @media (max-width: 640px) {
            .terms-card {
                padding: 24px 20px;
            }
            .terms-header {
                flex-direction: column;
            }
            .terms-content-box {
                padding: 18px 20px;
                font-size: 14px;
            }
        }
    </style>
</head>
<body class="site-body">
<div class="terms-page-wrapper">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="terms-main-content">
        <div class="terms-card">
            <div class="terms-header">
                <div class="terms-header-left">
                    <span class="terms-badge">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px;"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
                        <span>Quy định sử dụng an toàn</span>
                    </span>
                    <h1 style="margin-top:10px;"><c:out value="${policy.title}"/></h1>
                    <div class="terms-meta">
                        <span>Phiên bản v<c:out value="${policy.versionNumber}"/></span>
                        <span>•</span>
                        <span>Cập nhật: <c:out value="${policy.updatedAt}"/></span>
                    </div>
                </div>
            </div>

            <article class="terms-content-box">
                <c:out value="${policy.bodyText}"/>
            </article>

            <div class="terms-quick-actions">
                <a href="${pageContext.request.contextPath}/home" class="terms-back-btn">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px;"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
                    <span>Trang chủ</span>
                </a>
                <div class="terms-action-group">
                    <a href="${pageContext.request.contextPath}/ve-chung-toi" class="terms-link-btn">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:15px;height:15px;"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
                        <span>Về Chúng Tôi</span>
                    </a>
                    <a href="${pageContext.request.contextPath}/refund-policy" class="terms-link-btn">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:15px;height:15px;"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/></svg>
                        <span>Điều kiện hoàn tiền</span>
                    </a>
                </div>
            </div>
        </div>
    </main>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>
</div>
</body>
</html>
