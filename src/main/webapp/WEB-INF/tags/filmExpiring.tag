<%--
    Badge "Phim sap het chieu" (EX-01).

    Cach dung:
      1. Mot lan o dau file JSP:  <%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
      2. Tren poster / banner:    <cb:filmExpiring film="${film}"/>

    Dieu kien hien do FilmAvailabilityPolicy quyet dinh (Film.isExpiringSoon), khong phai do JSP
    tu tinh ngay — nho vay poster, banner va trang chi tiet luon hien badge cung mot luc.
    Cua so canh bao doc tu SystemSettings 'film.expiringSoonDays' (mac dinh 3 ngay).
--%>
<%@ tag pageEncoding="UTF-8" trimDirectiveWhitespaces="true"
        description="Badge nhac phim sap het lich chieu" %>
<%@ attribute name="film" required="true" type="com.mycompany.website.ban.ve.xem.phim.model.Film"
              description="Phim can kiem tra" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:if test="${film.expiringSoon}">
    <span class="badge film-expiring-badge" data-film-expiring="true"
          style="background:#dc2626;color:#fff;font-weight:700;display:inline-block;padding:4px 10px;border-radius:999px;font-size:0.78rem;line-height:1.3;">
        Phim sắp hết chiếu!! Đặt vé liền tay
    </span>
</c:if>
