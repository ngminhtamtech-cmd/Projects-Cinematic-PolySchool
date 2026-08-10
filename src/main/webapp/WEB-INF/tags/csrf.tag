<%--
    Chen token CSRF vao mot form POST.

    Cach dung (hop dong dat ten chot o P05, P06-P09 phai theo dung):
      1. Mot lan o dau file JSP:   <%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
      2. Trong MOI <form method="post">:   <cb:csrf/>

    Neu file JSP kho them directive (vi du doan HTML sinh boi script), dung ban thay the
    khong can khai bao gi:  <%@ include file="/WEB-INF/views/shared/csrf.jspf" %>

    Gia tri token la Base64-URL (A-Z a-z 0-9 - _) nen khong can escape HTML.
--%>
<%@ tag pageEncoding="UTF-8" trimDirectiveWhitespaces="true"
        description="Hidden input mang token CSRF cho form POST"
        import="com.mycompany.website.ban.ve.xem.phim.api.CsrfFilter" %>
<input type="hidden" name="_csrf" value="<%= CsrfFilter.currentToken(request) %>">
