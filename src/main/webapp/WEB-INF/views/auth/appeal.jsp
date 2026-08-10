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
    <title>Gửi Đơn Kháng Cáo - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <style>
        .appeal-container {
            max-width: 540px;
            margin: 60px auto;
            background: #ffffff;
            border-radius: 16px;
            padding: 32px;
            box-shadow: 0 10px 25px rgba(0, 0, 0, 0.08);
            border: 1px solid #e2e8f0;
        }
        .appeal-header {
            text-align: center;
            margin-bottom: 24px;
        }
        .appeal-header h1 {
            font-size: 1.5rem;
            color: #0f172a;
            margin-bottom: 8px;
        }
        .appeal-header p {
            color: #64748b;
            font-size: 0.92rem;
            line-height: 1.5;
        }
        .form-group {
            margin-bottom: 20px;
        }
        .form-group label {
            display: block;
            font-weight: 600;
            margin-bottom: 8px;
            color: #334155;
            font-size: 0.9rem;
        }
        .form-group input, .form-group textarea {
            width: 100%;
            padding: 12px 14px;
            border: 1px solid #cbd5e1;
            border-radius: 8px;
            font-size: 0.95rem;
            box-sizing: border-box;
        }
        .form-group textarea {
            min-height: 120px;
            resize: vertical;
        }
        .btn-submit-appeal {
            width: 100%;
            padding: 14px;
            background: #f7941e;
            color: #ffffff;
            border: none;
            border-radius: 8px;
            font-weight: 700;
            font-size: 1rem;
            cursor: pointer;
            transition: background 0.2s;
        }
        .btn-submit-appeal:hover {
            background: #e08316;
        }
        .back-link {
            display: block;
            text-align: center;
            margin-top: 16px;
            color: #64748b;
            text-decoration: none;
            font-size: 0.9rem;
        }
        .back-link:hover {
            color: #0f172a;
        }
    </style>
</head>
<body style="background: #f8fafc;">
    <div class="appeal-container">
        <div class="appeal-header">
            <div style="font-size: 48px; margin-bottom: 12px;">
                <c:choose>
                    <c:when test="${not empty ticketCode}">📩</c:when>
                    <c:otherwise>⚖️</c:otherwise>
                </c:choose>
            </div>
            <h1>
                <c:choose>
                    <c:when test="${not empty ticketCode}">Xin Hoàn Tiền Vé (Trường Hợp Đặc Biệt)</c:when>
                    <c:otherwise>Yêu Cầu Mở Khóa / Giải Trình</c:otherwise>
                </c:choose>
            </h1>
            <p>
                <c:choose>
                    <c:when test="${not empty ticketCode}">Bạn đang gửi yêu cầu xin hoàn tiền đặc biệt cho mã vé <strong><c:out value="${ticketCode}"/></strong>. Vui lòng nhập Email và trình bày lý do sự cố để Ban quản lý xem xét.</c:when>
                    <c:otherwise>Điền đầy đủ thông tin để Quản trị viên CineBook xem xét mở khóa tài khoản hoặc giải quyết yêu cầu của bạn.</c:otherwise>
                </c:choose>
            </p>
        </div>

        <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

        <form method="post" action="${pageContext.request.contextPath}/appeal">
            <cb:csrf/>
            <div class="form-group">
                <label for="email"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Địa chỉ Email đăng ký tài khoản</label>
                <input type="email" id="email" name="email" value="${fn:escapeXml(email)}" placeholder="nhapemail@example.com" required>
            </div>

            <div class="form-group">
                <label for="reason"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Nội dung Kháng cáo & Lý do giải trình</label>
                <textarea id="reason" name="reason" placeholder="Vui lòng trình bày rõ hoàn cảnh, lý do bạn cho rằng tài khoản nên được mở khóa..." required>${fn:escapeXml(reason)}</textarea>
            </div>

            <button type="submit" class="btn-submit-appeal">📤 Gửi Đơn Kháng Cáo</button>
        </form>

        <a href="${pageContext.request.contextPath}/login" class="back-link">← Quay lại trang đăng nhập</a>
    </div>
</body>
</html>
