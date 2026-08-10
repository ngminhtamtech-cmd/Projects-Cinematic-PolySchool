<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi" class="auth-html-page">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Đăng ký - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css?v=2.0">
</head>
<body class="auth-page">
    <c:set var="activeAuthMode" value="is-register"/>
    <%@ include file="/WEB-INF/views/auth/auth-container.jspf" %>
</body>
</html>
