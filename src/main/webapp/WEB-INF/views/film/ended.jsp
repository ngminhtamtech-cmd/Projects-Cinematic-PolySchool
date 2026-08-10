<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%--
  Phim da het chieu (EX-01).

  Tra ve HTTP 410 Gone chu khong phai 404: phim nay TUNG ton tai va link cu co the da duoc
  chia se. 410 noi ro "khong con nua" thay vi "chua bao gio co", va khong con bat ky nut mua ve
  nao tren trang.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
  <meta charset="UTF-8">
  <title><c:out value="${film.title}"/> — đã hết chiếu | CineBook</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
</head>
<body>
<%@ include file="/WEB-INF/views/shared/public-header.jspf" %>
<main class="container">
  <div class="notice" data-film-ended="true">
    <h1>Phim đã kết thúc lịch chiếu</h1>
    <p><strong><c:out value="${film.title}"/></strong> đã ngừng chiếu
      <c:if test="${not empty film.endDate}">từ ngày <c:out value="${film.endDate}"/></c:if>
      nên không còn bán vé.</p>
    <p>Bạn có thể xem các phim đang chiếu để chọn suất khác.</p>
  </div>
  <p>
    <a class="button" href="${pageContext.request.contextPath}/films">Xem phim đang chiếu</a>
    <a class="button" href="${pageContext.request.contextPath}/home">Về trang chủ</a>
  </p>
</main>
<%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>
</body>
</html>
