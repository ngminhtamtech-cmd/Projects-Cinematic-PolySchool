<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="cbf" uri="https://cinebook.local/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Quản Lý Phim - CineBook Admin</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
    <style>
        .film-table-title { display:flex; align-items:center; gap:10px; min-width:220px; }
        .film-table-poster { width:72px; height:42px; border-radius:7px; object-fit:cover; background:#f1f1f5; flex:none; }
        .film-table-poster-placeholder { display:grid; place-items:center; color:#727587; font-size:10px; text-align:center; }
        .film-table-name { display:block; margin-bottom:3px; font-weight:600; color:#171824; }
        .film-table-sub { display:block; max-width:300px; color:#727587; font-size:11px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
        .film-table-badges { display:flex; flex-wrap:wrap; gap:5px; }
        .film-table-dates { white-space:nowrap; line-height:1.55; }
        .film-table-actions { display:flex; flex-wrap:wrap; gap:6px; align-items:center; min-width:190px; }
        .film-extend-form { display:flex; flex-wrap:wrap; gap:6px; align-items:center; margin:0; }
        .film-extend-form input[type="date"] { width:142px; min-height:34px; }
        .badge-age { background:#fff0ef; border-color:#f2c0bd; color:#b42318; }
        .badge-format { background:#eef4ff; border-color:#cbd9f5; color:#2459c4; }
        .badge-showing { background:#ebfaf2; border-color:#b9e5ce; color:#167a53; }
        .badge-coming { background:#fff7e9; border-color:#f0d19f; color:#a95504; }
        .badge-ended { background:#f3f3f6; border-color:#ddddE6; color:#626575; }
        .delete-dialog-backdrop { position:fixed; inset:0; z-index:10000; display:none; place-items:center;
            padding:20px; background:rgba(15,23,42,.65); }
        .delete-dialog-backdrop.is-open { display:grid; }
        .delete-dialog { width:min(620px,100%); max-height:90vh; overflow:auto; background:#fff;
            border-radius:14px; padding:24px; box-shadow:0 24px 60px rgba(0,0,0,.3); }
        .impact-grid { display:grid; grid-template-columns:1fr 1fr; gap:8px 18px; margin:14px 0; }
    </style>
</head>
<body class="admin-body">
    <div class="dashboard">
        <%@ include file="/WEB-INF/views/admin/sidebar.jspf" %>
        <main class="dashboard-main">
            <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
            <div class="dashboard-content">
                <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

                <section class="admin-page-head">
                    <div>
                        <h1>Quản lý danh sách phim</h1>
                        <p class="muted">Quản lý thông tin, vòng đời chiếu và phạm vi hoạt động của từng phim.</p>
                    </div>
                    <c:choose>
                        <c:when test="${sessionScope.currentUser.role eq 'manager'}"><a href="${pageContext.request.contextPath}/admin/requests?action=film" class="btn-primary">Đề nghị phim</a></c:when>
                        <c:otherwise><a href="${pageContext.request.contextPath}/admin/films?action=create" class="btn-primary">Thêm phim mới</a></c:otherwise>
                    </c:choose>
                </section>

                <nav class="lifecycle-tabs" aria-label="Vòng đời phim">
                    <a href="${pageContext.request.contextPath}/admin/films?lifecycle=active" aria-current="${lifecycle eq 'active' ? 'page' : 'false'}">Đang quản lý</a>
                    <a href="${pageContext.request.contextPath}/admin/films?lifecycle=archive" aria-current="${lifecycle eq 'archive' ? 'page' : 'false'}">Hết hạn &amp; ngừng chiếu</a>
                    <a href="${pageContext.request.contextPath}/admin/films?lifecycle=deleted" aria-current="${lifecycle eq 'deleted' ? 'page' : 'false'}">Đã xóa</a>
                </nav>

                <c:if test="${not empty films}">
                    <div class="admin-table-wrap">
                        <table class="admin-data-table film-management-table">
                            <thead>
                                <tr>
                                    <th scope="col">Phim</th>
                                    <th scope="col">Độ tuổi</th>
                                    <th scope="col">Định dạng</th>
                                    <th scope="col">Trạng thái</th>
                                    <th scope="col">Thời lượng</th>
                                    <th scope="col">Quốc gia</th>
                                    <th scope="col">Ngày bắt đầu</th>
                                    <th scope="col">Ngày kết thúc</th>
                                    <th scope="col">Thể loại</th>
                                    <th scope="col">Đạo diễn</th>
                                    <th scope="col">Thao tác</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="film" items="${films}">
                                    <tr data-admin-search-item>
                                        <td>
                                            <div class="film-table-title">
                                                <c:choose>
                                                    <c:when test="${not empty film.thumbnail}">
                                                        <img src="${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, film.thumbnail))}"
                                                             alt="" class="film-table-poster">
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span class="film-table-poster film-table-poster-placeholder">Không có ảnh</span>
                                                    </c:otherwise>
                                                </c:choose>
                                                <span>
                                                    <strong class="film-table-name">${fn:escapeXml(film.title)}</strong>
                                                    <span class="film-table-sub">#${fn:escapeXml(film.id)}</span>
                                                </span>
                                            </div>
                                        </td>
                                        <td><span class="badge-pill badge-age">${fn:escapeXml(empty film.ageRating ? '—' : film.ageRating)}</span></td>
                                        <td><span class="badge-pill badge-format">${fn:escapeXml(empty film.format ? '—' : film.format)}</span></td>
                                        <td>
                                            <div class="film-table-badges">
                                                <c:if test="${film.deleted}"><span class="badge-pill badge-ended">Đã xóa</span></c:if>
                                                <c:choose>
                                                    <c:when test="${film.status eq 'coming'}"><span class="badge-pill badge-coming">Sắp chiếu</span></c:when>
                                                    <c:when test="${film.status eq 'ended'}"><span class="badge-pill badge-ended">Ngừng chiếu</span></c:when>
                                                    <c:when test="${film.status eq 'expired'}"><span class="badge-pill badge-ended" data-film-expired="true">Đã hết hạn</span></c:when>
                                                    <c:otherwise><span class="badge-pill badge-showing">Đang chiếu</span></c:otherwise>
                                                </c:choose>
                                                <c:if test="${film.expiringSoon}">
                                                    <span class="badge-pill badge-coming" data-film-expiring="true">Sắp hết chiếu</span>
                                                </c:if>
                                            </div>
                                        </td>
                                        <td>${fn:escapeXml(film.durationMinutes)} phút</td>
                                        <td>${fn:escapeXml(empty film.country ? '—' : film.country)}</td>
                                        <td>${fn:escapeXml(film.releaseDate)}</td>
                                        <td>${fn:escapeXml(empty film.endDate ? '—' : film.endDate)}</td>
                                        <td><span class="admin-clamp-2">${fn:escapeXml(empty film.categories ? '—' : film.categories)}</span></td>
                                        <td><span class="admin-clamp-2">${fn:escapeXml(empty film.directors ? '—' : film.directors)}</span></td>
                                        <td>
                                            <div class="film-table-actions">
                                                <c:if test="${not film.deleted}">
                                                    <c:choose><c:when test="${sessionScope.currentUser.role eq 'manager'}"><a href="${pageContext.request.contextPath}/admin/requests?action=film&amp;filmId=${fn:escapeXml(film.id)}" class="button secondary">Đề nghị sửa</a></c:when><c:otherwise><a href="${pageContext.request.contextPath}/admin/films?action=edit&amp;id=${fn:escapeXml(film.id)}" class="button secondary">Chỉnh sửa</a></c:otherwise></c:choose>
                                                    <c:if test="${sessionScope.currentUser.role eq 'admin' and (film.expiringSoon or film.status eq 'expired')}">
                                                        <form method="post" action="${pageContext.request.contextPath}/admin/films" class="film-extend-form">
                                                            <cb:csrf/>
                                                            <input type="hidden" name="id" value="${fn:escapeXml(film.id)}">
                                                            <input type="hidden" name="action" value="extend">
                                                            <input type="date" name="endDate" required class="form-input" aria-label="Ngày kết thúc chiếu mới">
                                                            <button type="submit" class="button">Gia hạn</button>
                                                        </form>
                                                    </c:if>
                                                    <c:if test="${sessionScope.currentUser.role eq 'admin'}"><button type="button" class="button danger"
                                                            data-delete-film-id="${fn:escapeXml(film.id)}"
                                                            data-delete-film-title="${fn:escapeXml(film.title)}">Xóa</button></c:if>
                                                </c:if>
                                                <c:if test="${film.deleted}">
                                                    <span class="muted">${fn:escapeXml(film.deletionMode)} · ${fn:escapeXml(film.deletedAt)}</span>
                                                </c:if>
                                            </div>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:if>

                <c:if test="${empty films}">
                    <article class="empty-state-block">
                        <h3>Chưa có bộ phim nào</h3>
                        <p class="muted">Tạo bộ phim đầu tiên để bắt đầu quản lý lịch chiếu.</p>
                        <c:choose><c:when test="${sessionScope.currentUser.role eq 'manager'}"><a href="${pageContext.request.contextPath}/admin/requests?action=film" class="btn-primary">Đề nghị phim</a></c:when><c:otherwise><a href="${pageContext.request.contextPath}/admin/films?action=create" class="btn-primary">Thêm phim mới</a></c:otherwise></c:choose>
                    </article>
                </c:if>
            </div>
        </main>
    </div>
    <div id="filmDeleteBackdrop" class="delete-dialog-backdrop" aria-hidden="true">
        <section class="delete-dialog" role="dialog" aria-modal="true" aria-labelledby="filmDeleteTitle" tabindex="-1">
            <h2 id="filmDeleteTitle" style="margin-top:0">Xóa phim</h2>
            <div id="filmDeleteLive" aria-live="polite">Đang tải dữ liệu kiểm tra…</div>
            <div id="filmDeleteLifecycleHint" hidden style="margin-top:10px;padding:10px 12px;border-radius:8px;background:#fff7ed;color:#9a3412;"></div>
            <div id="filmDeleteImpact" class="impact-grid" hidden></div>
            <form method="post" action="${pageContext.request.contextPath}/admin/films" id="filmDeleteForm">
                <cb:csrf/>
                <input type="hidden" name="id" id="filmDeleteId">
                <input type="hidden" name="action" value="delete">
                <fieldset id="filmDeleteOptions" disabled>
                    <legend>Cách xử lý bình luận</legend>
                    <label><input type="radio" name="deletionMode" value="PRESERVE_COMMENTS" checked> Xóa và lưu lịch sử bình luận tại admin</label><br>
                    <label><input type="radio" name="deletionMode" value="PURGE_COMMENTS"> Xóa hoàn toàn bình luận và report</label>
                    <label style="display:block;margin-top:14px">Nhập đúng tên phim để xác nhận xóa hoàn toàn
                        <input class="form-input" name="confirmationTitle" id="filmConfirmTitle" autocomplete="off">
                    </label>
                </fieldset>
                <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:18px">
                    <button type="button" class="button secondary" id="filmDeleteCancel">Hủy</button>
                    <button type="submit" class="button danger" id="filmDeleteSubmit" disabled>Xác nhận xóa</button>
                </div>
            </form>
        </section>
    </div>
    <script>
        (function() {
          var backdrop = document.getElementById('filmDeleteBackdrop');
          var dialog = backdrop.querySelector('[role="dialog"]');
          var live = document.getElementById('filmDeleteLive');
          var impactBox = document.getElementById('filmDeleteImpact');
          var lifecycleHint = document.getElementById('filmDeleteLifecycleHint');
          var options = document.getElementById('filmDeleteOptions');
          var submit = document.getElementById('filmDeleteSubmit');
          var opener = null;
          function closeDelete() { backdrop.classList.remove('is-open'); backdrop.setAttribute('aria-hidden','true'); if (opener) opener.focus(); }
          document.getElementById('filmDeleteCancel').addEventListener('click', closeDelete);
          backdrop.addEventListener('click', function(e) { if (e.target === backdrop) closeDelete(); });
          document.addEventListener('keydown', function(e) {
            if (!backdrop.classList.contains('is-open')) return;
            if (e.key === 'Escape') { closeDelete(); return; }
            if (e.key === 'Tab') {
              var focusable = Array.from(dialog.querySelectorAll('button:not([disabled]),input:not([disabled])'));
              if (!focusable.length) return;
              var first=focusable[0], last=focusable[focusable.length-1];
              if (e.shiftKey && document.activeElement===first) { e.preventDefault(); last.focus(); }
              else if (!e.shiftKey && document.activeElement===last) { e.preventDefault(); first.focus(); }
            }
          });
          document.querySelectorAll('[data-delete-film-id]').forEach(function(button) {
            button.addEventListener('click', function() {
              opener=button; options.disabled=true; submit.disabled=true; impactBox.hidden=true; lifecycleHint.hidden=true;
              live.textContent='Đang tải dữ liệu kiểm tra…';
              document.getElementById('filmDeleteId').value=button.dataset.deleteFilmId;
              document.getElementById('filmConfirmTitle').value='';
              backdrop.classList.add('is-open'); backdrop.setAttribute('aria-hidden','false'); dialog.focus();
              fetch('${pageContext.request.contextPath}/admin/films?action=delete-impact&id=' + encodeURIComponent(button.dataset.deleteFilmId), {
                headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
              }).then(function(r) { return r.ok ? r.json() : Promise.reject(r.status); })
              .then(function(i) {
                  live.textContent=i.eligible ? 'Server xác nhận phim đủ điều kiện xóa.' : i.blockedReason;
                  if (!i.eligible && !i.expiredOrWithdrawn) {
                    lifecycleHint.innerHTML='Phim vẫn đang trong vòng đời chiếu. '
                      + '<a href="${pageContext.request.contextPath}/admin/films?action=edit&id='+encodeURIComponent(i.filmId)+'" '
                      + 'style="color:#c2410c;font-weight:700;">Mở trang chỉnh sửa và chuyển trạng thái sang “Ngừng chiếu”</a>, '
                      + 'sau đó quay lại thực hiện xóa.';
                    lifecycleHint.hidden=false;
                  }
                  impactBox.innerHTML='<span>Suất hiện tại/tương lai: <b>'+i.currentOrFutureShowtimeCount+'</b></span>'+
                    '<span>Suất lịch sử: <b>'+i.historicalShowtimeCount+'</b></span><span>Hold: <b>'+i.activeHoldCount+'</b></span>'+
                    '<span>Order nháp: <b>'+i.activeDraftOrderCount+'</b></span><span>Order hiệu lực: <b>'+i.committedOrderCount+'</b></span>'+
                    '<span>Order lịch sử: <b>'+i.historicalOrderCount+'</b></span><span>Bình luận: <b>'+i.commentCount+'</b></span>'+
                    '<span>Report: <b>'+i.commentReportCount+'</b></span>';
                  impactBox.hidden=false; options.disabled=!i.eligible; submit.disabled=!i.eligible;
              }).catch(function() {
                  live.textContent='Không tải được preview. Thao tác xóa đã bị khóa; vui lòng thử lại.';
              });
            });
          });
        })();
    </script>
</body>
</html>
