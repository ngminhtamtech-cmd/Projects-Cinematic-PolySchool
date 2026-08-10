<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="cbf" uri="https://cinebook.local/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Đề nghị phim - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260810h">
    <style>
        .request-page { --request-line:#e6e7ef; --request-soft:#f8f8fc; }
        .request-page .admin-page-head { margin-bottom:16px; }
        .request-page .admin-page-head h1 { margin-bottom:4px; }
        .request-form { display:grid; grid-template-columns:minmax(0,1.7fr) minmax(300px,.8fr); gap:16px; align-items:start; }
        .request-card { padding:20px; }
        .request-card-head { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; padding-bottom:14px; margin-bottom:16px; border-bottom:1px solid var(--request-line); }
        .request-card-head h2 { margin:0 0 4px; font-size:16px; }
        .request-card-head p { margin:0; font-size:12px; }
        .request-form-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14px 16px; }
        .request-form-grid .wide { grid-column:1/-1; }
        .request-field { display:flex; flex-direction:column; gap:6px; min-width:0; font-size:12px; font-weight:650; color:#343440; }
        .request-field .required { color:#dc2626; }
        .request-field input, .request-field select, .request-field textarea { width:100%; min-height:42px; box-sizing:border-box; }
        .request-field textarea { min-height:104px; resize:vertical; }
        .request-field small { color:#77798a; font-size:11px; font-weight:400; }
        .genre-suggestions { color:#77798a; }
        .request-media { position:sticky; top:82px; display:grid; gap:12px; }
        .media-stack { display:grid; gap:14px; }
        .media-block { min-width:0; }
        .media-label { display:flex; justify-content:space-between; gap:10px; margin-bottom:7px; font-size:12px; font-weight:700; color:#343440; }
        .media-label span:last-child { color:#77798a; font-size:10px; font-weight:500; }
        .media-input { position:absolute; inline-size:1px; block-size:1px; opacity:0; pointer-events:none; }
        .media-dropzone { position:relative; display:grid; place-items:center; width:100%; aspect-ratio:16/9; overflow:hidden; border:1.5px dashed #cfd1dd; border-radius:10px; background:var(--request-soft); cursor:pointer; transition:border-color .18s ease, background .18s ease; }
        .media-dropzone:hover, .media-dropzone:focus-within { border-color:#6d28d9; background:#f5f2ff; }
        .media-dropzone:focus-visible { outline:3px solid rgba(109,40,217,.22); outline-offset:2px; }
        .media-preview { position:absolute; inset:0; width:100%; height:100%; object-fit:cover; display:none; }
        .media-dropzone.has-image .media-preview { display:block; }
        .media-dropzone.has-image .media-placeholder { opacity:0; }
        .media-placeholder { position:relative; z-index:1; display:grid; justify-items:center; gap:7px; padding:14px; text-align:center; transition:opacity .18s ease; }
        .media-placeholder svg { width:22px; height:22px; color:#6d28d9; }
        .media-placeholder strong { font-size:12px; color:#272733; }
        .media-placeholder small { color:#77798a; font-size:10px; line-height:1.45; }
        .media-file-name { margin-top:6px; color:#77798a; font-size:10px; overflow-wrap:anywhere; }
        .request-actions { display:grid; gap:8px; padding-top:14px; border-top:1px solid var(--request-line); }
        .request-actions .btn-primary, .request-actions .button { width:100%; justify-content:center; min-height:42px; }
        .existing-section { margin-top:16px; padding:18px 20px; }
        .existing-toolbar { display:flex; align-items:center; justify-content:space-between; gap:14px; margin-bottom:12px; }
        .existing-toolbar h2 { margin:0 0 3px; font-size:15px; }
        .existing-toolbar p { margin:0; font-size:11px; }
        .existing-search { width:min(300px,100%); min-height:40px; }
        .compact-film-list { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); border:1px solid var(--request-line); border-radius:10px; overflow:hidden; }
        .compact-film { display:grid; grid-template-columns:minmax(0,1fr) auto; align-items:center; gap:12px; min-height:64px; padding:10px 12px; border-bottom:1px solid var(--request-line); background:#fff; }
        .compact-film:nth-child(odd) { border-right:1px solid var(--request-line); }
        .compact-film:nth-last-child(-n+2) { border-bottom:0; }
        .compact-film-name { min-width:0; }
        .compact-film-name strong { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:12px; }
        .compact-actions { display:flex; gap:6px; align-items:center; justify-content:flex-end; }
        .compact-actions form { margin:0; }
        .compact-actions .button { min-height:34px; padding:5px 10px; font-size:11px; white-space:nowrap; }
        .request-empty-search { display:none; padding:24px; text-align:center; color:#77798a; border:1px dashed var(--request-line); border-radius:10px; }
        @media (max-width:1100px) {
            .request-form { grid-template-columns:1fr; }
            .request-media { position:static; grid-template-columns:1fr; }
            .media-stack { grid-template-columns:repeat(2,minmax(0,1fr)); }
        }
        @media (max-width:760px) {
            .request-card { padding:16px; }
            .request-form-grid, .media-stack, .compact-film-list { grid-template-columns:1fr; }
            .request-form-grid .wide { grid-column:auto; }
            .compact-film { border-right:0 !important; border-bottom:1px solid var(--request-line) !important; }
            .compact-film:last-child { border-bottom:0 !important; }
            .existing-toolbar { align-items:stretch; flex-direction:column; }
            .existing-search { width:100%; }
        }
        @media (max-width:520px) {
            .compact-film { grid-template-columns:1fr; }
            .compact-actions { justify-content:flex-start; }
            .compact-actions .button { min-height:40px; }
        }
        @media (prefers-reduced-motion:reduce) { .request-page * { scroll-behavior:auto !important; transition:none !important; } }
    </style>
</head>
<body class="admin-body">
<div class="dashboard">
    <%@ include file="/WEB-INF/views/admin/sidebar.jspf" %>
    <main class="dashboard-main">
        <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
        <div class="dashboard-content request-page">
            <%@ include file="/WEB-INF/views/shared/flash.jspf" %>
            <section class="admin-page-head">
                <div>
                    <h1>${film.id gt 0 ? 'Cập nhật đề nghị phim' : 'Đề nghị thêm phim'}</h1>
                    <p class="muted">Phạm vi ${fn:escapeXml(cinemaContextName)} · Thông tin sẽ được gửi để admin duyệt.</p>
                </div>
                <a class="button secondary" href="${pageContext.request.contextPath}/admin/requests">Quay lại</a>
            </section>

            <form class="request-form" method="post" action="${pageContext.request.contextPath}/admin/requests" enctype="multipart/form-data">
                <cb:csrf/>
                <c:choose>
                    <c:when test="${film.id gt 0}"><input type="hidden" name="action" value="update-film"></c:when>
                    <c:otherwise><input type="hidden" name="action" value="create-film"></c:otherwise>
                </c:choose>
                <input type="hidden" name="filmId" value="${fn:escapeXml(film.id)}">
                <input type="hidden" name="thumbnail" value="${fn:escapeXml(film.thumbnail)}">
                <input type="hidden" name="banner" value="${fn:escapeXml(film.banner)}">

                <section class="panel-card request-card" aria-labelledby="filmProposalTitle">
                    <div class="request-card-head">
                        <div>
                            <h2 id="filmProposalTitle">Thông tin phim</h2>
                            <p class="muted">Điền thông tin chính trước, sau đó kiểm tra hình ảnh ở cột bên phải.</p>
                        </div>
                    </div>
                    <div class="request-form-grid">
                        <label class="request-field wide">Tên phim <span class="required" aria-hidden="true">*</span><input name="title" maxlength="255" required value="${fn:escapeXml(film.title)}" placeholder="Nhập tên phim"></label>
                        <label class="request-field">Tên khác<input name="otherTitles" maxlength="255" value="${fn:escapeXml(film.otherTitles)}" placeholder="Tên quốc tế hoặc tên viết tắt"></label>
                        <label class="request-field">Quốc gia<input name="country" maxlength="100" value="${fn:escapeXml(film.country)}" placeholder="Ví dụ: Việt Nam"></label>
                        <label class="request-field wide">Diễn viên<input name="actors" maxlength="500" value="${fn:escapeXml(film.actors)}" placeholder="Phân cách bằng dấu phẩy"></label>
                        <label class="request-field">Đạo diễn<input name="directors" maxlength="255" value="${fn:escapeXml(film.directors)}" placeholder="Tên đạo diễn"></label>
                        <label class="request-field">Ngôn ngữ<input name="language" maxlength="50" value="${fn:escapeXml(film.language)}" placeholder="Ví dụ: Tiếng Việt"></label>
                        <label class="request-field">Ngày khởi chiếu <span class="required" aria-hidden="true">*</span><input type="date" name="releaseDate" required value="${fn:escapeXml(film.releaseDate)}"></label>
                        <label class="request-field">Ngày kết thúc <span class="required" aria-hidden="true">*</span><input type="date" name="endDate" required value="${fn:escapeXml(film.endDate)}"></label>
                        <label class="request-field">Thời lượng (phút) <span class="required" aria-hidden="true">*</span><input type="number" name="durationMinutes" min="1" max="1000" required value="${fn:escapeXml(film.durationMinutes)}" placeholder="120"></label>
                        <label class="request-field">Điểm đánh giá<input type="number" name="rating" min="0" max="10" step="0.1" value="${fn:escapeXml(film.rating)}" placeholder="0.0 – 10.0"></label>
                        <label class="request-field">Giới hạn tuổi<input name="ageRating" maxlength="10" value="${fn:escapeXml(film.ageRating)}" placeholder="P, K, T13, T16, T18"></label>
                        <label class="request-field">Định dạng<input name="format" maxlength="50" value="${fn:escapeXml(film.format)}" placeholder="2D, 3D, IMAX"></label>
                        <label class="request-field">Phụ đề<input name="subtitles" maxlength="50" value="${fn:escapeXml(film.subtitles)}" placeholder="Tiếng Việt"></label>
                        <label class="request-field">Trạng thái<select name="status"><option value="showing">Đang chiếu</option><option value="coming" ${film.rawStatus eq 'coming' ? 'selected' : ''}>Sắp chiếu</option><option value="ended" ${film.rawStatus eq 'ended' ? 'selected' : ''}>Ngừng chiếu</option></select></label>
                        <label class="request-field wide">Trailer URL<input type="url" name="trailerUrl" maxlength="255" value="${fn:escapeXml(film.trailerUrl)}" placeholder="https://www.youtube.com/watch?v=..."></label>
                        <label class="request-field wide">Mô tả<textarea name="description" rows="4" placeholder="Tóm tắt nội dung phim">${fn:escapeXml(film.description)}</textarea></label>
                        <label class="request-field wide" for="genreInput">
                            Thể loại
                            <input id="genreInput" name="categories" maxlength="500"
                                   value="${fn:escapeXml(film.categories)}"
                                   list="categorySuggestions" autocomplete="off"
                                   placeholder="Ví dụ: Hành động, Hài hước, Phiêu lưu">
                            <small class="genre-suggestions">Nhập tự do nhiều thể loại và phân cách bằng dấu phẩy.</small>
                        </label>
                        <datalist id="categorySuggestions">
                            <c:forEach var="category" items="${categories}">
                                <option value="${fn:escapeXml(category.value)}" data-category-id="${fn:escapeXml(category.key)}"></option>
                            </c:forEach>
                        </datalist>
                        <span id="categoryIdFields" hidden aria-hidden="true"></span>
                    </div>
                </section>

                <aside class="panel-card request-card request-media" aria-labelledby="filmMediaTitle">
                    <div class="request-card-head">
                        <div>
                            <h2 id="filmMediaTitle">Hình ảnh &amp; media</h2>
                            <p class="muted">Cả poster và banner đều hiển thị theo ảnh ngang.</p>
                        </div>
                    </div>
                    <div class="media-stack">
                        <div class="media-block">
                            <div class="media-label"><span>Poster phim</span><span>16:9</span></div>
                            <input class="media-input" type="file" id="posterUpload" name="thumbnailFile" accept="image/png,image/jpeg,image/webp" data-media-input="poster">
                            <label class="media-dropzone ${not empty film.thumbnail ? 'has-image' : ''}" for="posterUpload" tabindex="0" data-media-drop="poster">
                                <img class="media-preview" data-media-preview="poster" src="${not empty film.thumbnail ? fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, film.thumbnail)) : ''}" alt="Xem trước poster phim">
                                <span class="media-placeholder">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M4 16l4.6-4.6a2 2 0 0 1 2.8 0L16 16m-2-2 1.6-1.6a2 2 0 0 1 2.8 0L20 14m-2-8h.01M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"/></svg>
                                    <strong>Chọn poster ngang</strong>
                                    <small>JPG, PNG, WEBP · tối đa 5MB<br>Khuyến nghị 1280×720px</small>
                                </span>
                            </label>
                            <div class="media-file-name" data-media-name="poster">${not empty film.thumbnail ? 'Đang dùng ảnh hiện tại' : 'Chưa chọn tệp'}</div>
                        </div>
                        <div class="media-block">
                            <div class="media-label"><span>Banner phim</span><span>16:9 hoặc 21:9</span></div>
                            <input class="media-input" type="file" id="bannerUpload" name="bannerFile" accept="image/png,image/jpeg,image/webp" data-media-input="banner">
                            <label class="media-dropzone ${not empty film.banner ? 'has-image' : ''}" for="bannerUpload" tabindex="0" data-media-drop="banner">
                                <img class="media-preview" data-media-preview="banner" src="${not empty film.banner ? fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, film.banner)) : ''}" alt="Xem trước banner phim">
                                <span class="media-placeholder">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M4 16l4.6-4.6a2 2 0 0 1 2.8 0L16 16m-2-2 1.6-1.6a2 2 0 0 1 2.8 0L20 14m-2-8h.01M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"/></svg>
                                    <strong>Chọn banner ngang</strong>
                                    <small>JPG, PNG, WEBP · tối đa 5MB<br>Khuyến nghị 1920×1080px</small>
                                </span>
                            </label>
                            <div class="media-file-name" data-media-name="banner">${not empty film.banner ? 'Đang dùng ảnh hiện tại' : 'Chưa chọn tệp'}</div>
                        </div>
                    </div>
                    <div class="request-actions">
                        <button class="btn-primary" type="submit">${film.id gt 0 ? 'Gửi đề nghị cập nhật' : 'Gửi đề nghị thêm phim'}</button>
                        <a class="button secondary" href="${pageContext.request.contextPath}/admin/requests">Hủy và quay lại</a>
                    </div>
                </aside>
            </form>

            <section class="panel-card existing-section" aria-labelledby="existingFilmsTitle">
                <div class="existing-toolbar">
                    <div>
                        <h2 id="existingFilmsTitle">Phim đã có trong hệ thống</h2>
                        <p class="muted">Tìm nhanh để đề nghị gán, cập nhật hoặc gỡ khỏi rạp.</p>
                    </div>
                    <input id="existingFilmSearch" class="existing-search" type="search" placeholder="Tìm theo tên hoặc mã phim…" aria-label="Tìm phim hiện có">
                </div>
                <div class="compact-film-list" id="existingFilmList">
                    <c:forEach var="item" items="${films}">
                        <div class="compact-film" data-admin-search-item data-film-search="${fn:escapeXml(item.title)} #${fn:escapeXml(item.id)}">
                            <div class="compact-film-name">
                                <strong title="${fn:escapeXml(item.title)}">${fn:escapeXml(item.title)}</strong>
                                <small class="muted">#${fn:escapeXml(item.id)} · ${fn:escapeXml(item.durationMinutes)} phút</small>
                            </div>
                            <div class="compact-actions">
                                <c:choose>
                                    <c:when test="${assignedFilmIds.contains(item.id)}">
                                        <a class="button secondary" href="${pageContext.request.contextPath}/admin/requests?action=film&amp;filmId=${fn:escapeXml(item.id)}">Đề nghị sửa</a>
                                        <form method="post" action="${pageContext.request.contextPath}/admin/requests">
                                            <cb:csrf/><input type="hidden" name="action" value="unassign-film"><input type="hidden" name="filmId" value="${fn:escapeXml(item.id)}">
                                            <button class="button danger" type="submit">Đề nghị gỡ</button>
                                        </form>
                                    </c:when>
                                    <c:otherwise>
                                        <form method="post" action="${pageContext.request.contextPath}/admin/requests">
                                            <cb:csrf/><input type="hidden" name="action" value="assign-film"><input type="hidden" name="filmId" value="${fn:escapeXml(item.id)}">
                                            <button class="button primary" type="submit">Yêu cầu gán</button>
                                        </form>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                        </div>
                    </c:forEach>
                </div>
                <div class="request-empty-search" id="existingFilmEmpty">Không tìm thấy phim phù hợp.</div>
            </section>
        </div>
    </main>
</div>
<script src="${pageContext.request.contextPath}/assets/js/admin-ui.js"></script>
<script>
    (function () {
        var requestForm = document.querySelector('.request-form');
        var genreInput = document.getElementById('genreInput');
        var categoryFields = document.getElementById('categoryIdFields');
        var categoryOptions = document.querySelectorAll('#categorySuggestions option[data-category-id]');

        function normalizeGenre(value) {
            var text = String(value || '').trim().toLocaleLowerCase('vi');
            return typeof text.normalize === 'function'
                ? text.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
                : text;
        }

        if (requestForm && genreInput && categoryFields) {
            requestForm.addEventListener('submit', function () {
                categoryFields.replaceChildren();
                var categoryByName = new Map();
                categoryOptions.forEach(function (option) {
                    categoryByName.set(normalizeGenre(option.value), option.dataset.categoryId);
                });
                var added = new Set();
                genreInput.value.split(',').forEach(function (genre) {
                    var categoryId = categoryByName.get(normalizeGenre(genre));
                    if (!categoryId || added.has(categoryId)) return;
                    added.add(categoryId);
                    var hidden = document.createElement('input');
                    hidden.type = 'hidden';
                    hidden.name = 'categoryIds';
                    hidden.value = categoryId;
                    categoryFields.appendChild(hidden);
                });
            });
        }

        document.querySelectorAll('[data-media-input]').forEach(function (input) {
            input.addEventListener('change', function () {
                var type = input.getAttribute('data-media-input');
                var file = input.files && input.files[0];
                var drop = document.querySelector('[data-media-drop="' + type + '"]');
                var preview = document.querySelector('[data-media-preview="' + type + '"]');
                var name = document.querySelector('[data-media-name="' + type + '"]');
                if (!file || !drop || !preview) return;
                preview.src = URL.createObjectURL(file);
                drop.classList.add('has-image');
                if (name) name.textContent = file.name;
            });
        });

        document.querySelectorAll('.media-dropzone').forEach(function (drop) {
            drop.addEventListener('keydown', function (event) {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    var input = document.getElementById(drop.getAttribute('for'));
                    if (input) input.click();
                }
            });
        });

        var search = document.getElementById('existingFilmSearch');
        var empty = document.getElementById('existingFilmEmpty');
        if (search) {
            search.addEventListener('input', function () {
                var keyword = search.value.trim().toLocaleLowerCase('vi');
                var visible = 0;
                document.querySelectorAll('#existingFilmList .compact-film').forEach(function (item) {
                    var matches = !keyword || (item.getAttribute('data-film-search') || '').toLocaleLowerCase('vi').includes(keyword);
                    item.hidden = !matches;
                    if (matches) visible += 1;
                });
                if (empty) empty.style.display = visible ? 'none' : 'block';
            });
        }
    })();
</script>
</body>
</html>
