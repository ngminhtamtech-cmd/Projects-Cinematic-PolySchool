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
    <title>Đặt lại mật khẩu - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
</head>
<body class="auth-page">
    <div class="auth-layout">
        <section class="auth-showcase">
            <a class="brand auth-brand" href="${pageContext.request.contextPath}/home"><span>CineBook</span><small>Đặt vé xem phim</small></a>
            <span class="eyebrow">Recovery flow</span>
            <h1>Đặt mật khẩu mới cho tài khoản của bạn.</h1>
            <p>Liên kết này chỉ dùng được một lần và sẽ hết hạn sau một khoảng thời gian ngắn kể từ lúc bạn yêu cầu.</p>
            <div class="auth-points">
                <span>Dùng một lần</span>
                <span>Có hạn sử dụng</span>
                <span>Mật khẩu được mã hoá</span>
            </div>
        </section>

        <main class="auth-card">
            <span class="eyebrow dark">Account recovery</span>
            <h2>Đặt lại mật khẩu</h2>
            <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

            <c:choose>
                <c:when test="${tokenUsable}">
                    <p class="muted">Chọn mật khẩu mới, tối thiểu ${fn:escapeXml(passwordMinLength)} ký tự và có ít nhất 3 trong 4 nhóm: chữ thường, chữ hoa, chữ số, ký tự đặc biệt.</p>
                    <form class="form-grid" method="post" action="${pageContext.request.contextPath}/reset-password">
                        <cb:csrf/>
                        <input type="hidden" name="token" value="${fn:escapeXml(resetToken)}">
                        <label>Mật khẩu mới
                            <input type="password" name="password" required minlength="${fn:escapeXml(passwordMinLength)}" autocomplete="new-password">
                        </label>
                        <label>Xác nhận mật khẩu mới
                            <input type="password" name="confirmPassword" required minlength="${fn:escapeXml(passwordMinLength)}" autocomplete="new-password">
                        </label>
                        <button type="submit">Đặt lại mật khẩu</button>
                    </form>
                </c:when>
                <c:otherwise>
                    <p class="muted">Liên kết đã hết hạn hoặc đã được sử dụng. Hãy gửi lại yêu cầu để nhận liên kết mới.</p>
                    <p><a href="${pageContext.request.contextPath}/forgot-password"><strong>Gửi lại yêu cầu quên mật khẩu</strong></a></p>
                </c:otherwise>
            </c:choose>

            <p class="muted"><a href="${pageContext.request.contextPath}/login"><strong>Quay lại đăng nhập</strong></a></p>
        </main>
    </div>
</body>
</html>
