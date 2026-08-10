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
    <title>Rạp Đặc Biệt - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css?v=1.0.2">
    <style>
        .special-header {
            text-align: center;
            padding: 40px 0 20px;
            background: linear-gradient(180deg, var(--color-gray-50) 0%, var(--surface) 100%);
            border-bottom: 1px solid var(--color-gray-200);
            margin-bottom: 40px;
        }
        .special-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 28px;
            margin-bottom: 50px;
        }
        .special-card {
            background: var(--surface);
            border-radius: var(--radius-md);
            overflow: hidden;
            box-shadow: var(--shadow-card);
            border: 1px solid var(--color-gray-200);
            transition: transform 0.2s;
        }
        .special-card:hover {
            transform: translateY(-4px);
        }
        .special-img {
            padding-top: 56.25%; /* 16:9 */
            background-size: cover;
            background-position: center;
            background-color: var(--color-gray-100);
        }
        .special-info {
            padding: 20px;
            display: flex;
            flex-direction: column;
            gap: 8px;
        }
        .special-title {
            font-size: 18px;
            font-weight: 800;
            color: var(--color-secondary);
            margin: 0;
        }
        .special-addr {
            font-size: 13px;
            color: var(--color-gray-500);
            line-height: 1.4;
        }
        .special-desc {
            font-size: 13px;
            color: var(--ink);
            line-height: 1.5;
        }
    </style>
</head>
<body class="public-page">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <div class="special-header">
        <span class="eyebrow">Premium Lounges</span>
        <h1 class="section-title">RẠP ĐẶC BIỆT</h1>
        <p class="lead-copy">Khám phá các phòng chiếu phim đẳng cấp quốc tế với dịch vụ thượng hạng.</p>
    </div>

    <main class="container">
        <div class="special-grid">
            <c:forEach var="l" items="${lounges}">
                <div class="special-card">
                    <div class="special-img" style="background-image: url('${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, l.imageUrl))}')"></div>
                    <div class="special-info">
                        <h3 class="special-title">${fn:escapeXml(l.title)}</h3>
                        <p class="special-addr">${fn:escapeXml(l.address)}</p>
                        <p class="special-desc">${fn:escapeXml(l.description)}</p>
                    </div>
                </div>
            </c:forEach>
        </div>
    </main>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>
</body>
</html>
