<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8"><title>500</title><link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
</head>
<body><main class="container">
  <div class="notice" data-error-page="500"><strong>500</strong> Hệ thống gặp lỗi. Vui lòng thử lại sau.</div>
  <c:if test="${not empty flashError}"><p class="notice"><c:out value="${flashError}"/></p></c:if>
  <c:if test="${not empty errorCorrelationId}">
    <p class="muted">Mã tham chiếu sự cố: <code><c:out value="${errorCorrelationId}"/></code>
      &mdash; gửi mã này cho quản trị viên để tra log.</p>
  </c:if>
  <a class="button" href="${pageContext.request.contextPath}/home">Về trang chủ</a>
</main></body>
</html>
