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
    <title>CineTag# Merchandise - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css?v=1.0.2">
    <style>
        .layout-2cols {
            display: grid;
            grid-template-columns: 1.8fr 1fr;
            gap: 28px;
            margin-top: 24px;
            margin-bottom: 50px;
        }
        
        /* CineTag Tabs */
        .cinetag-tabs {
            display: flex;
            gap: 16px;
            border-bottom: 2px solid var(--color-gray-100);
            padding-bottom: 8px;
            margin-bottom: 24px;
        }
        .tag-tab {
            font-size: 15px;
            font-weight: 700;
            color: var(--color-gray-500);
            text-decoration: none;
            padding-bottom: 8px;
            position: relative;
        }
        .tag-tab.active {
            color: var(--color-primary);
        }
        .tag-tab.active::after {
            content: '';
            position: absolute;
            bottom: -10px;
            left: 0;
            right: 0;
            height: 3px;
            background: var(--color-primary);
            border-radius: 2px;
        }

        /* Products Grid */
        .products-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 20px;
        }
        .product-card {
            background: var(--surface);
            border-radius: var(--radius-md);
            border: 1px solid var(--color-gray-200);
            overflow: hidden;
            box-shadow: var(--shadow-card);
            display: flex;
            flex-direction: column;
            transition: transform 0.2s;
        }
        .product-card:hover {
            transform: translateY(-2px);
        }
        .product-img {
            padding-top: 100%; /* 1:1 */
            background-size: cover;
            background-position: center;
            background-color: var(--color-gray-50);
        }
        .product-info {
            padding: 16px;
            display: flex;
            flex-direction: column;
            gap: 8px;
            flex: 1;
        }
        .product-name {
            font-size: 14px;
            font-weight: 700;
            color: var(--ink);
            margin: 0;
            line-height: 1.4;
            height: 40px;
            overflow: hidden;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            line-clamp: 2;
            -webkit-box-orient: vertical;
        }
        .product-price {
            font-size: 16px;
            font-weight: 800;
            color: var(--color-primary);
        }
        .product-actions {
            display: flex;
            gap: 8px;
            margin-top: auto;
        }
        .btn-buy-now {
            flex: 1;
            background: var(--color-primary);
            color: white;
            border: none;
            padding: 8px;
            border-radius: var(--radius-sm);
            font-weight: 700;
            font-size: 12px;
            cursor: pointer;
            text-align: center;
            text-decoration: none;
        }
        .btn-buy-now:hover {
            background: var(--color-primary-dark);
        }
        .btn-add-cart {
            background: transparent;
            border: 1px solid var(--color-primary);
            color: var(--color-primary);
            padding: 8px;
            border-radius: var(--radius-sm);
            font-weight: 700;
            font-size: 12px;
            cursor: pointer;
        }
        .btn-add-cart:hover {
            background: #fff8f2;
        }

        /* Sidebar Widgets */
        .sidebar-widget {
            border: 1px solid var(--color-gray-200);
            border-radius: var(--radius-md);
            background: var(--surface);
            box-shadow: var(--shadow-card);
            overflow: hidden;
            margin-bottom: 24px;
        }
        .widget-header-blue {
            background: var(--color-secondary);
            color: white;
            padding: 12px 20px;
            font-weight: 700;
            font-size: 15px;
        }
        .widget-body {
            padding: 20px;
        }
        .widget-select {
            width: 100%;
            padding: 10px;
            border-radius: var(--radius-sm);
            border: 1px solid var(--color-gray-300);
            margin-bottom: 12px;
            outline: none;
            font-weight: 600;
        }
        .widget-btn {
            width: 100%;
            background: var(--color-primary);
            color: white;
            border: none;
            padding: 12px;
            border-radius: var(--radius-sm);
            font-weight: 700;
            cursor: pointer;
        }

        .side-film-card {
            display: flex;
            gap: 12px;
            text-decoration: none;
            color: var(--ink);
            margin-bottom: 16px;
        }
        .side-film-card:last-child {
            margin-bottom: 0;
        }
        .side-film-img {
            width: 50px;
            height: 75px;
            border-radius: var(--radius-sm);
            background-size: cover;
            background-position: center;
            background-color: var(--color-gray-100);
        }
        .side-film-info {
            display: flex;
            flex-direction: column;
            gap: 4px;
            justify-content: center;
        }
        .side-film-title {
            font-size: 13px;
            font-weight: 700;
            margin: 0;
        }
    </style>
</head>
<body class="public-page">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="container">
        <!-- Breadcrumb -->
        <nav style="margin-top:20px; font-size:13px; color:var(--color-gray-500);">
            Trang chủ / CineTag# / <span style="color:var(--color-primary); font-weight:700;">${fn:escapeXml(displayTag)}</span>
        </nav>

        <div class="layout-2cols">
            <!-- Cột trái: Nội dung sản phẩm -->
            <div>
                <h1 style="font-size: 24px; font-weight: 800; border-left: 4px solid var(--color-secondary); padding-left: 12px; margin-bottom: 20px;">
                    CINETAG# : ${fn:escapeXml(displayTag)}
                </h1>

                <!-- Tab con ngang -->
                <div class="cinetag-tabs" style="flex-wrap: wrap;">
                    <c:forEach var="t" items="${allTags}">
                        <a href="${pageContext.request.contextPath}/cinetags?tag=${fn:escapeXml(t.slug)}" class="tag-tab ${fn:escapeXml(activeTag == t.slug ? 'active' : '')}">${fn:escapeXml(t.name)}</a>
                    </c:forEach>
                </div>

                <!-- Lưới sản phẩm 4 cột (ở đây 2 cột trên di động, 2 cột rộng) -->
                <div class="products-grid">
                    <c:forEach var="p" items="${products}">
                        <div class="product-card">
                            <div class="product-img" style="background-image: url('${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, p.imageUrl))}')"></div>
                            <div class="product-info">
                                <h3 class="product-name">${fn:escapeXml(p.name)}</h3>
                                <div class="product-price">
                                    ${cbf:whole(p.price)} đ
                                </div>
                                <div class="product-actions">
                                    <button type="button" class="btn-buy-now" onclick="alert('Đã mua sản phẩm ${fn:escapeXml(p.name)}!')">Mua ngay</button>
                                    <button type="button" class="btn-add-cart" onclick="alert('Đã thêm ${fn:escapeXml(p.name)} vào giỏ hàng!')">🛒</button>
                                </div>
                            </div>
                        </div>
                    </c:forEach>
                </div>
            </div>

            <!-- Cột phải: Sidebar -->
            <aside>
                <!-- Box Mua vé nhanh -->
                <div class="sidebar-widget">
                    <div class="widget-header-blue">MUA VÉ NHANH</div>
                    <form class="widget-body" method="get" action="${pageContext.request.contextPath}/booking">
                        <select class="widget-select" name="filmId" id="sideFilm" required>
                            <option value="">Chọn phim...</option>
                            <c:forEach var="f" items="${featuredFilms}">
                                <option value="${fn:escapeXml(f.id)}">${fn:escapeXml(f.title)}</option>
                            </c:forEach>
                        </select>
                        
                        <select class="widget-select" name="cinemaId" id="sideCinema" disabled required>
                            <option value="">Chọn rạp...</option>
                            <c:forEach var="c" items="${cinemas}">
                                <option value="${fn:escapeXml(c.id)}">${fn:escapeXml(c.name)}</option>
                            </c:forEach>
                        </select>

                        <select class="widget-select" name="showtimeId" id="sideShowtime" disabled required>
                            <option value="">Chọn suất...</option>
                        </select>

                        <button type="submit" class="widget-btn" id="sideSubmit" disabled>Đặt Vé Ngay</button>
                    </form>
                </div>

                <!-- Phim đang chiếu -->
                <div class="sidebar-widget">
                    <div class="widget-header-blue" style="background:var(--color-primary);">PHIM ĐANG CHIẾU</div>
                    <div class="widget-body" style="display:flex; flex-direction:column; gap:16px;">
                        <c:forEach var="f" items="${featuredFilms}" varStatus="status">
                            <c:if test="${status.index < 2}">
                                <c:set var="sideThumb" value="${cbf:assetUrl(pageContext.request.contextPath, empty f.thumbnail ? '/assets/img/default-film.jpg' : f.thumbnail)}" />
                                <a href="${pageContext.request.contextPath}/films/${fn:escapeXml(f.id)}" class="side-film-card">
                                    <div class="side-film-img" style="background-image: url('${fn:escapeXml(sideThumb)}');"></div>
                                    <div class="side-film-info">
                                        <h4 class="side-film-title">${fn:escapeXml(f.title)}</h4>
                                        <span style="font-size:11px; color:var(--color-gold);">⭐ ${fn:escapeXml(f.rating)}</span>
                                    </div>
                                </a>
                            </c:if>
                        </c:forEach>
                    </div>
                </div>
            </aside>
        </div>
    </main>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>

    <!-- Tích hợp logic lọc cho sidebar Mua Vé Nhanh -->
    <script id="sideShowtimesJson" type="application/json">
        [
            <c:forEach var="st" items="${showtimes}" varStatus="status">
                {
                    "id": ${st.id},
                    "filmId": ${st.filmId},
                    "cinemaId": ${st.cinemaId},
                    "cinemaName": "${st.cinemaName}",
                    "displayTime": "${st.startTimeDisplay}"
                }${not status.last ? ',' : ''}
            </c:forEach>
        ]
    </script>
    <script>
        const sideFilm = document.getElementById('sideFilm');
        const sideCinema = document.getElementById('sideCinema');
        const sideShowtime = document.getElementById('sideShowtime');
        const sideSubmit = document.getElementById('sideSubmit');

        // Lấy danh sách showtimes từ JSON
        const sideShowtimes = JSON.parse(document.getElementById('sideShowtimesJson').textContent || '[]');

        sideFilm.addEventListener('change', () => {
            const filmId = sideFilm.value;
            if (!filmId) {
                sideCinema.disabled = true;
                sideShowtime.disabled = true;
                sideSubmit.disabled = true;
                return;
            }
            const cinemaIds = [...new Set(sideShowtimes.filter(x => x.filmId == filmId).map(x => x.cinemaId))];
            Array.from(sideCinema.options).forEach(opt => {
                if (opt.value === "") return;
                opt.disabled = !cinemaIds.includes(Number(opt.value));
            });
            sideCinema.disabled = false;
            sideCinema.value = "";
            sideShowtime.disabled = true;
            sideSubmit.disabled = true;
        });

        sideCinema.addEventListener('change', () => {
            const filmId = sideFilm.value;
            const cinemaId = sideCinema.value;
            if (!cinemaId) {
                sideShowtime.disabled = true;
                sideSubmit.disabled = true;
                return;
            }
            const sts = sideShowtimes.filter(x => x.filmId == filmId && x.cinemaId == cinemaId);
            sideShowtime.innerHTML = '<option value="">Chọn suất...</option>';
            sts.forEach(st => {
                const opt = document.createElement('option');
                opt.value = st.id;
                opt.textContent = st.displayTime;
                sideShowtime.appendChild(opt);
            });
            sideShowtime.disabled = false;
            sideSubmit.disabled = true;
        });

        sideShowtime.addEventListener('change', () => {
            sideSubmit.disabled = !sideShowtime.value;
        });
    </script>
</body>
</html>
