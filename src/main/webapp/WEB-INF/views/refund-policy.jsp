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
        .refund-policy-page-wrapper {
            display: flex;
            flex-direction: column;
            min-height: 100vh;
            background: #F8FAFC;
        }

        .policy-main-content {
            flex: 1 0 auto;
            width: 100%;
            max-width: 920px;
            margin: 40px auto 60px;
            padding: 0 20px;
            box-sizing: border-box;
        }

        .policy-card {
            background: #FFFFFF;
            border-radius: 16px;
            border: 1px solid #E2E8F0;
            border-top: 4px solid #6D28D9;
            box-shadow: 0 10px 30px -5px rgba(15, 23, 42, 0.06), 0 4px 12px -2px rgba(15, 23, 42, 0.03);
            padding: 36px 40px;
            transition: transform 0.2s ease, box-shadow 0.2s ease;
        }

        .policy-header {
            display: flex;
            align-items: flex-start;
            justify-content: space-between;
            gap: 16px;
            padding-bottom: 24px;
            margin-bottom: 28px;
            border-bottom: 1px solid #F1F5F9;
        }

        .policy-header-left h1 {
            font-size: 26px;
            font-weight: 700;
            color: #0F172A;
            margin: 0 0 8px 0;
            line-height: 1.3;
            letter-spacing: -0.01em;
        }

        .policy-meta {
            display: flex;
            align-items: center;
            gap: 10px;
            font-size: 13px;
            color: #64748B;
        }

        .policy-badge {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            padding: 3px 10px;
            background: #F5F3FF;
            color: #6D28D9;
            font-size: 12px;
            font-weight: 600;
            border-radius: 20px;
            border: 1px solid #DDD6FE;
        }

        .policy-content-box {
            font-size: 15px;
            line-height: 1.8;
            color: #334155;
            white-space: pre-line;
            background: #FAF5FF;
            border: 1px solid #F3E8FF;
            border-radius: 12px;
            padding: 24px 28px;
        }

        .policy-content-box p {
            margin: 0 0 14px 0;
        }

        .policy-content-box p:last-child {
            margin-bottom: 0;
        }

        .policy-quick-actions {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-top: 32px;
            padding-top: 20px;
            border-top: 1px solid #F1F5F9;
            gap: 12px;
            flex-wrap: wrap;
        }

        .policy-back-btn {
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

        .policy-back-btn:hover {
            background: #E2E8F0;
            color: #0F172A;
        }

        .policy-action-group {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .policy-link-btn {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            color: #6D28D9;
            font-size: 13px;
            font-weight: 600;
            text-decoration: none;
            padding: 8px 14px;
            border-radius: 8px;
            background: #F5F3FF;
            transition: all 0.2s ease;
        }

        .policy-link-btn:hover {
            background: #DDD6FE;
            color: #5B21B6;
        }

        @media (max-width: 640px) {
            .policy-card {
                padding: 24px 20px;
            }
            .policy-header {
                flex-direction: column;
            }
            .policy-content-box {
                padding: 18px 20px;
                font-size: 14px;
            }
        }
    </style>
</head>
<body class="site-body">
<div class="refund-policy-page-wrapper">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="policy-main-content">
        <div class="policy-card">
            <div class="policy-header">
                <div class="policy-header-left">
                    <span class="policy-badge">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px;"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
                        <span>Chính sách chính thức</span>
                    </span>
                    <h1 style="margin-top:10px;"><c:out value="${policy.title}"/></h1>
                    <div class="policy-meta">
                        <span>Phiên bản v<c:out value="${policy.versionNumber}"/></span>
                        <span>•</span>
                        <span>Cập nhật: <c:out value="${policy.updatedAt}"/></span>
                    </div>
                </div>
            </div>

            <article class="policy-content-box">
                <c:out value="${policy.bodyText}"/>
            </article>

            <div class="policy-quick-actions">
                <a href="${pageContext.request.contextPath}/home" class="policy-back-btn">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px;"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
                    <span>Trang chủ</span>
                </a>
                <div class="policy-action-group">
                    <a href="${pageContext.request.contextPath}/orders/history" class="policy-link-btn">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:15px;height:15px;"><path d="M3 8h18v3a2 2 0 0 0 0 4v3H3v-3a2 2 0 0 0 0-4zM9 8v12"/></svg>
                        <span>Lịch sử đặt vé</span>
                    </a>
                    <a href="${pageContext.request.contextPath}/profile" class="policy-link-btn">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:15px;height:15px;"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                        <span>Góp ý & Hỗ trợ</span>
                    </a>
                </div>
            </div>
        </div>
    </main>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>
</div>
</body>
</html>
