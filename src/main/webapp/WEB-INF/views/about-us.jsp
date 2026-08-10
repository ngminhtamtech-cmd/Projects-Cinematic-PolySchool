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
    <title>Về Chúng Tôi - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/layout.css">
    <style>
        .about-page-wrapper {
            display: flex;
            flex-direction: column;
            min-height: 100vh;
            background: #F8FAFC;
        }

        .about-main-content {
            flex: 1 0 auto;
            width: 100%;
            max-width: 1140px;
            margin: 40px auto 60px;
            padding: 0 20px;
            box-sizing: border-box;
        }

        .about-hero {
            text-align: center;
            margin-bottom: 48px;
        }

        .about-hero-badge {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 4px 14px;
            background: #F5F3FF;
            color: #6D28D9;
            font-size: 13px;
            font-weight: 600;
            border-radius: 20px;
            border: 1px solid #DDD6FE;
            margin-bottom: 12px;
        }

        .about-hero h1 {
            font-size: 32px;
            font-weight: 800;
            color: #0F172A;
            margin: 0 0 12px 0;
            letter-spacing: -0.02em;
        }

        .about-hero p {
            font-size: 16px;
            color: #64748B;
            max-width: 680px;
            margin: 0 auto;
            line-height: 1.6;
        }

        /* 4 Team Member Cards */
        .team-grid {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 24px;
            margin-bottom: 56px;
        }

        .team-card {
            background: #FFFFFF;
            border-radius: 16px;
            border: 1px solid #E2E8F0;
            padding: 24px 20px;
            text-align: center;
            box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03);
            transition: all 0.25s ease;
            display: flex;
            flex-direction: column;
            align-items: center;
        }

        .team-card:hover {
            transform: translateY(-4px);
            box-shadow: 0 12px 24px -4px rgba(15, 23, 42, 0.08);
            border-color: #CBD5E1;
        }

        .team-avatar-wrap {
            width: 110px;
            height: 110px;
            border-radius: 50%;
            overflow: hidden;
            background: #F1F5F9;
            border: 3px solid #F8FAFC;
            box-shadow: 0 4px 10px rgba(0,0,0,0.08);
            margin-bottom: 16px;
            position: relative;
            flex-shrink: 0;
        }

        .team-avatar-wrap img {
            width: 100%;
            height: 100%;
            object-fit: cover;
            display: block;
        }

        .team-avatar-fallback {
            width: 100%;
            height: 100%;
            display: flex;
            align-items: center;
            justify-content: center;
            background: linear-gradient(135deg, #6D28D9 0%, #4C1D95 100%);
            color: #FFFFFF;
            font-size: 28px;
            font-weight: 700;
        }

        .team-name {
            font-size: 16px;
            font-weight: 700;
            color: #0F172A;
            margin: 0 0 6px 0;
            line-height: 1.3;
        }

        .team-role {
            font-size: 13px;
            color: #64748B;
            font-weight: 500;
            margin: 0;
        }

        /* 3 Core Values / Features Cards */
        .features-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 24px;
        }

        .feature-card {
            background: #FFFFFF;
            border-radius: 16px;
            border: 1px solid #E2E8F0;
            padding: 24px 28px;
            display: flex;
            align-items: flex-start;
            gap: 20px;
            box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03);
            transition: all 0.25s ease;
        }

        .feature-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 10px 20px -4px rgba(15, 23, 42, 0.06);
        }

        .feature-icon-box {
            width: 56px;
            height: 56px;
            border-radius: 50%;
            background: #EFF6FF;
            color: #1D4ED8;
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
        }

        .feature-body {
            flex: 1;
            min-width: 0;
            padding-left: 12px;
            border-left: 1px solid #F1F5F9;
        }

        .feature-title {
            font-size: 17px;
            font-weight: 700;
            color: #1E40AF;
            margin: 0 0 8px 0;
        }

        .feature-desc {
            font-size: 13.5px;
            color: #475569;
            line-height: 1.6;
            margin: 0;
        }

        @media (max-width: 980px) {
            .team-grid {
                grid-template-columns: repeat(2, 1fr);
            }
            .features-grid {
                grid-template-columns: 1fr;
            }
        }

        @media (max-width: 540px) {
            .team-grid {
                grid-template-columns: 1fr;
            }
        }
    </style>
</head>
<body class="site-body">
<div class="about-page-wrapper">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="about-main-content">
        <div class="about-hero">
            <span class="about-hero-badge">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px;"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                <span>Đội ngũ CineBook</span>
            </span>
            <h1>Về Chúng Tôi</h1>
            <p>Hệ thống đặt vé xem phim trực tuyến hiện đại, mang lại sự tiện lợi và trải nghiệm điện ảnh hoàn hảo cho khán giả trên toàn quốc.</p>
        </div>

        <!-- Section 1: 4 Team Members -->
        <section style="margin-bottom: 40px;">
            <div class="team-grid">
                <c:forEach var="member" items="${members}" varStatus="status">
                    <div class="team-card">
                        <div class="team-avatar-wrap">
                            <c:set var="resolvedUrl" value="${cbf:assetUrl(pageContext.request.contextPath, member.imageUrl)}" />
                            <img src="${fn:escapeXml(resolvedUrl)}" 
                                 alt="${fn:escapeXml(member.name)}" 
                                 onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                            <div class="team-avatar-fallback" style="display:none;">
                                ${fn:escapeXml(empty member.name ? 'C' : member.name.substring(0, 1).toUpperCase())}
                            </div>
                        </div>
                        <h3 class="team-name">${fn:escapeXml(member.name)}</h3>
                        <p class="team-role">${fn:escapeXml(member.role)}</p>
                    </div>
                </c:forEach>
            </div>
        </section>

        <!-- Section 2: 3 Core Features (Matching screenshot [2]) -->
        <section>
            <div class="features-grid">
                <c:forEach var="feature" items="${features}">
                    <div class="feature-card">
                        <div class="feature-icon-box">
                            <c:choose>
                                <c:when test="${feature.icon eq 'ticket'}">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="#2563EB" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:26px;height:26px;"><path d="M2 9a3 3 0 0 1 0 6v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-2a3 3 0 0 1 0-6V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v2z"/><path d="M13 5v2"/><path d="M13 11v2"/><path d="M13 17v2"/></svg>
                                </c:when>
                                <c:when test="${feature.icon eq 'screen'}">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="#2563EB" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:26px;height:26px;"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/><path d="M7 11h2M11 11h2M15 11h2"/></svg>
                                </c:when>
                                <c:otherwise>
                                    <svg viewBox="0 0 24 24" fill="none" stroke="#2563EB" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:26px;height:26px;"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/></svg>
                                </c:otherwise>
                            </c:choose>
                        </div>
                        <div class="feature-body">
                            <h3 class="feature-title">${fn:escapeXml(feature.title)}</h3>
                            <p class="feature-desc">${fn:escapeXml(feature.description)}</p>
                        </div>
                    </div>
                </c:forEach>
            </div>
        </section>
    </main>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>
</div>
</body>
</html>
