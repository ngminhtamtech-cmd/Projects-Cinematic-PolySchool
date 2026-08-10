<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Thiết kế sơ đồ ghế - ${fn:escapeXml(room.name)} - CineBook Enterprise</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-seat-designer.css?v=20260810b">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/cinema-seat-3d.css?v=20260810c">
</head>
<body class="admin-body">
    <div class="dashboard">
        <%@ include file="/WEB-INF/views/admin/sidebar.jspf" %>
        <main class="dashboard-main">
            <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
            <div class="dashboard-content">
                <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

                <div id="seatDesigner"
                     class="seat-designer"
                     data-room-id="${fn:escapeXml(room.id)}"
                     data-room-name="${fn:escapeXml(room.name)}"
                    data-cinema-name="${fn:escapeXml(room.cinemaName)}"
                    data-context-path="${pageContext.request.contextPath}">
                    <header class="seat-designer__page-head">
                        <div class="seat-designer__heading-wrap">
                            <h1>Sơ đồ ghế · ${fn:escapeXml(room.name)}</h1>
                            <p class="seat-designer__subtitle">${fn:escapeXml(room.cinemaName)}</p>
                        </div>
                        <div class="seat-designer__head-meta" aria-label="Thông tin phòng">
                            <span class="seat-designer__chip"><strong id="seatHeadTotal">0</strong>&nbsp;ghế</span>
                            <span id="seatDirtyStatus" class="seat-designer__save-state" data-dirty="false">
                                <span class="seat-designer__status-dot" aria-hidden="true"></span>
                                <span>Đã lưu</span>
                            </span>
                        </div>
                    </header>

                    <div class="seat-designer__layout">
                        <section class="seat-designer__workspace" aria-labelledby="seatCanvasTitle">
                            <div class="seat-designer__toolbar" aria-label="Công cụ thiết kế ghế">
                                <div class="seat-designer__toolbar-group" role="toolbar" aria-label="Chế độ chỉnh sửa">
                                    <button type="button" class="seat-designer__tool" data-tool="select" aria-pressed="false" title="Chọn hoặc di chuyển ghế">
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="m5 3 14 9-7 2-3 7L5 3Z"/></svg>
                                        <span>Chọn / Di chuyển</span>
                                    </button>
                                    <button type="button" class="seat-designer__tool is-active" data-tool="add" aria-pressed="true" title="Kéo để thêm ghế thường">
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M12 5v14M5 12h14"/><rect x="2.5" y="2.5" width="19" height="19" rx="4"/></svg>
                                        <span>Thêm ghế</span>
                                    </button>
                                    <span class="seat-designer__toolbar-divider" aria-hidden="true"></span>
                                    <button type="button" class="seat-designer__icon-button" data-action="undo" aria-label="Hoàn tác" title="Hoàn tác (Ctrl+Z)" disabled>
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="m9 7-5 5 5 5"/><path d="M20 17a8 8 0 0 0-8-8H4"/></svg>
                                    </button>
                                    <button type="button" class="seat-designer__icon-button" data-action="redo" aria-label="Làm lại" title="Làm lại (Ctrl+Y)" disabled>
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="m15 7 5 5-5 5"/><path d="M4 17a8 8 0 0 1 8-8h8"/></svg>
                                    </button>
                                </div>

                                <div class="seat-designer__toolbar-group">
                                    <button type="button" class="seat-designer__button seat-designer__inspector-toggle" data-action="toggle-inspector" aria-controls="seatInspector" aria-expanded="false">
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M4 5h16M4 12h16M4 19h16"/><circle cx="9" cy="5" r="2"/><circle cx="15" cy="12" r="2"/><circle cx="7" cy="19" r="2"/></svg>
                                        Thuộc tính
                                    </button>
                                    <button type="button" class="seat-designer__button" data-action="preview">
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z"/><circle cx="12" cy="12" r="2.5"/></svg>
                                        Xem trước
                                    </button>
                                    <button type="button" id="seatDesigner3dButton" class="seat-designer__button seat-designer__button--3d" data-action="view-3d" disabled>
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="m12 2 8 4.5v11L12 22l-8-4.5v-11L12 2Z"/><path d="m4 6.5 8 4.5 8-4.5M12 11v11"/></svg>
                                        Xem ghế 3D
                                    </button>
                                    <div class="seat-designer__danger-wrap">
                                        <button type="button" class="seat-designer__icon-button" data-action="toggle-danger" aria-haspopup="menu" aria-expanded="false" aria-label="Mở thao tác nguy hiểm" title="Thao tác khác">
                                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/></svg>
                                        </button>
                                        <div id="seatDangerMenu" class="seat-designer__danger-menu" role="menu" hidden>
                                            <button type="button" class="seat-designer__menu-action" data-danger-action="template" role="menuitem">
                                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>
                                                Mẫu ghế trung tâm
                                            </button>
                                            <button type="button" class="seat-designer__menu-action seat-designer__menu-action--danger" data-danger-action="delete-seat" role="menuitem">
                                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M3 6h18M8 6V4h8v2M7 6l1 14h8l1-14"/></svg>
                                                Xóa ghế đang chọn
                                            </button>
                                            <button type="button" class="seat-designer__menu-action seat-designer__menu-action--danger" data-danger-action="delete-row" role="menuitem">
                                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16"/><path d="m19 4 3 3-3 3"/></svg>
                                                Xóa hàng đang chọn
                                            </button>
                                            <button type="button" class="seat-designer__menu-action seat-designer__menu-action--danger" data-danger-action="delete-column" role="menuitem">
                                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M7 4v16M12 4v16M17 4v16"/><path d="m4 19 3 3 3-3"/></svg>
                                                Xóa cột đang chọn
                                            </button>
                                            <button type="button" class="seat-designer__menu-action seat-designer__menu-action--danger" data-danger-action="clear" role="menuitem">
                                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M3 6h18M8 6V4h8v2M7 6l1 14h8l1-14"/></svg>
                                                Xóa toàn bộ sơ đồ
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div class="seat-designer__canvas-card">
                                <div class="seat-designer__canvas-head">
                                    <h2 id="seatCanvasTitle" class="seat-designer__canvas-title">Ghế trong phòng</h2>
                                    <span id="seatModeHint" class="seat-designer__mode-hint">Đang thêm ghế thường</span>
                                </div>
                                <div class="seat-designer__screen-wrap" aria-hidden="true">
                                    <div class="seat-designer__screen"></div>
                                    <div class="seat-designer__screen-label">Màn hình</div>
                                </div>
                                <div id="seatDesignerStatus" class="seat-designer__live-status" data-tone="info" aria-live="polite">
                                    Kéo hoặc chọn hai điểm thẳng hàng để thêm ghế.
                                </div>
                                <div id="seatCanvasScroll" class="seat-designer__canvas-scroll">
                                    <div id="seatGrid" class="seat-designer__grid" role="grid" aria-label="Sơ đồ ghế phòng ${fn:escapeXml(room.name)}"></div>
                                </div>
                                <div class="seat-designer__legend" aria-label="Chú giải ghế">
                                    <span class="seat-designer__legend-item"><span class="seat-designer__legend-swatch"></span>Ghế thường</span>
                                    <span class="seat-designer__legend-item"><span class="seat-designer__legend-swatch seat-designer__legend-swatch--vip"></span>VIP</span>
                                    <span class="seat-designer__legend-item"><span class="seat-designer__legend-swatch seat-designer__legend-swatch--couple"></span>Ghế đôi</span>
                                    <span class="seat-designer__legend-item"><span class="seat-designer__legend-swatch seat-designer__legend-swatch--locked"></span>Đang khóa</span>
                                    <span class="seat-designer__legend-item"><span class="seat-designer__legend-swatch seat-designer__legend-swatch--growth"></span>Vùng thêm ghế</span>
                                </div>
                            </div>
                        </section>

                        <aside id="seatInspector" class="seat-designer__inspector" aria-labelledby="seatInspectorTitle">
                            <div class="seat-designer__inspector-head">
                                <h2 id="seatInspectorTitle">Thuộc tính</h2>
                                <button type="button" class="seat-designer__icon-button seat-designer__inspector-close" data-action="close-inspector" aria-label="Đóng bảng thuộc tính">×</button>
                            </div>

                            <section class="seat-designer__inspector-section" aria-labelledby="seatStatsTitle">
                                <h3 id="seatStatsTitle" class="seat-designer__section-title">Thống kê phòng</h3>
                                <div class="seat-designer__stats">
                                    <div class="seat-designer__stat"><span>Tổng chỗ</span><strong id="statTotalSeats">0</strong></div>
                                    <div class="seat-designer__stat"><span>Ghế thường</span><strong id="statStandardSeats">0</strong></div>
                                    <div class="seat-designer__stat"><span>Ghế VIP</span><strong id="statVipSeats">0</strong></div>
                                    <div class="seat-designer__stat"><span>Ghế đôi</span><strong id="statCoupleSeats">0 cặp</strong></div>
                                    <div class="seat-designer__stat"><span>Đang khóa</span><strong id="statLockedSeats">0</strong></div>
                                </div>
                            </section>

                            <section class="seat-designer__inspector-section" aria-labelledby="seatPricesTitle">
                                <h3 id="seatPricesTitle" class="seat-designer__section-title">Giá mặc định</h3>
                                <div class="seat-designer__price-list">
                                    <div class="seat-designer__field">
                                        <label for="priceStandardInput">Ghế thường <small>Ghế mới</small></label>
                                        <div class="seat-designer__price-input-wrap"><input id="priceStandardInput" class="seat-designer__input" type="number" min="0" step="5000" inputmode="numeric" data-price-type="standard"></div>
                                    </div>
                                    <div class="seat-designer__field">
                                        <label for="priceVipInput">Ghế VIP <small>Khi đổi loại</small></label>
                                        <div class="seat-designer__price-input-wrap"><input id="priceVipInput" class="seat-designer__input" type="number" min="0" step="5000" inputmode="numeric" data-price-type="vip"></div>
                                    </div>
                                    <div class="seat-designer__field">
                                        <label for="priceCoupleInput">Ghế đôi <small>Mỗi ghế vật lý</small></label>
                                        <div class="seat-designer__price-input-wrap"><input id="priceCoupleInput" class="seat-designer__input" type="number" min="0" step="5000" inputmode="numeric" data-price-type="couple"></div>
                                    </div>
                                </div>
                                <p class="seat-designer__helper">Thay đổi tại đây không ghi đè giá của ghế đã có. Di chuyển ghế luôn giữ nguyên phụ thu.</p>
                            </section>

                            <section class="seat-designer__inspector-section" aria-labelledby="seatSelectionTitle">
                                <h3 id="seatSelectionTitle" class="seat-designer__section-title">Ghế đang chọn</h3>
                                <div id="seatSelectionEmpty" class="seat-designer__selection-empty">Chọn hoặc focus một ghế để xem chi tiết.</div>
                                <div id="seatSelectionCard" class="seat-designer__selection-card" hidden>
                                    <div class="seat-designer__selection-key">
                                        <strong id="seatSelectionKey">A1</strong>
                                        <span id="seatSelectionType" class="seat-designer__type-badge">Thường</span>
                                    </div>
                                    <div class="seat-designer__selection-meta">
                                        <span id="seatSelectionPosition">Hàng A · Ghế 1</span>
                                        <span id="seatSelectionPrice">Phụ thu 0đ</span>
                                        <span id="seatSelectionLock">Có thể chỉnh sửa</span>
                                    </div>
                                    <div class="seat-designer__selection-actions">
                                        <button type="button" class="seat-designer__button" data-selection-action="cycle">Đổi loại</button>
                                        <button type="button" class="seat-designer__button" data-selection-action="move">Di chuyển</button>
                                        <button type="button" class="seat-designer__button seat-designer__button--danger" data-selection-action="delete">Xóa ghế</button>
                                    </div>
                                </div>
                            </section>

                        </aside>
                    </div>

                    <button type="button" id="seatInspectorBackdrop" class="seat-designer__inspector-backdrop" data-action="close-inspector" aria-label="Đóng bảng thuộc tính" hidden></button>

                    <div class="seat-designer__action-bar">
                        <div class="seat-designer__action-buttons">
                            <a class="seat-designer__button" href="${pageContext.request.contextPath}/admin/rooms">Quay lại</a>
                            <button type="button" class="seat-designer__button seat-designer__button--primary" data-action="save">Lưu sơ đồ</button>
                        </div>
                    </div>

                    <form id="saveSeatsForm" method="post" action="${pageContext.request.contextPath}/admin/rooms/seats" hidden>
                        <cb:csrf/>
                        <input type="hidden" name="roomId" value="${fn:escapeXml(room.id)}">
                        <input type="hidden" name="action" value="save_seats">
                        <input type="hidden" name="seatsJson" id="seatsJsonInput">
                    </form>
                </div>
            </div>
        </main>
    </div>

    <div id="seatContextMenu" class="seat-designer__context-menu" role="menu" hidden>
        <button type="button" class="seat-designer__menu-action" data-context-action="cycle" role="menuitem">Đổi loại ghế</button>
        <button type="button" class="seat-designer__menu-action" data-context-action="move" role="menuitem">Di chuyển ghế</button>
        <button type="button" class="seat-designer__menu-action seat-designer__menu-action--danger" data-context-action="delete" role="menuitem">Xóa ghế</button>
    </div>

    <div id="seatPreviewModal" class="seat-designer__modal" aria-hidden="true">
        <div class="seat-designer__dialog" role="dialog" aria-modal="true" aria-labelledby="seatPreviewTitle" tabindex="-1">
            <div class="seat-designer__dialog-head">
                <div>
                    <h2 id="seatPreviewTitle">Xem trước thay đổi</h2>
                    <p>Kiểm tra vị trí, loại và giá ghế trước khi ghi vào phòng chiếu.</p>
                </div>
                <button type="button" class="seat-designer__icon-button" data-modal-close="preview" aria-label="Đóng xem trước">×</button>
            </div>
            <div class="seat-designer__dialog-body">
                <div id="seatPreviewSummary" class="seat-designer__preview-summary"></div>
                <div id="seatServerPreview" class="seat-designer__server-preview" data-tone="info" aria-live="polite">Đang chờ kiểm tra…</div>
                <div id="seatPreviewGrid" class="seat-designer__preview-grid" aria-label="Bản xem trước sơ đồ ghế"></div>
            </div>
            <div class="seat-designer__dialog-actions">
                <button type="button" class="seat-designer__button" data-modal-close="preview">Tiếp tục chỉnh sửa</button>
                <button type="button" id="seatPreviewSaveButton" class="seat-designer__button seat-designer__button--primary" data-action="confirm-save" disabled>Lưu sơ đồ này</button>
            </div>
        </div>
    </div>

    <div id="seatConfirmModal" class="seat-designer__modal" aria-hidden="true">
        <div class="seat-designer__dialog seat-designer__dialog--confirm" role="dialog" aria-modal="true" aria-labelledby="seatConfirmTitle" tabindex="-1">
            <div class="seat-designer__dialog-head">
                <div>
                    <h2 id="seatConfirmTitle">Xác nhận thao tác</h2>
                    <p id="seatConfirmMessage">Thao tác này sẽ thay đổi bản nháp sơ đồ ghế.</p>
                </div>
                <button type="button" class="seat-designer__icon-button" data-modal-close="confirm" aria-label="Đóng xác nhận">×</button>
            </div>
            <div class="seat-designer__dialog-actions">
                <button type="button" class="seat-designer__button" data-modal-close="confirm">Hủy</button>
                <button type="button" id="seatConfirmButton" class="seat-designer__button seat-designer__button--danger">Xác nhận</button>
            </div>
        </div>
    </div>

    <script id="seatsData" type="application/json">
    [
        <c:forEach var="seat" items="${seats}" varStatus="status">
            {
                "rowLabel": "<c:out value='${seat.rowLabel}'/>",
                "seatNumber": ${seat.seatNumber},
                "seatType": "<c:out value='${seat.seatType}'/>",
                "seatKey": "<c:out value='${seat.seatKey}'/>",
                "priceSurcharge": <c:out value="${seat.priceSurcharge}" default="0"/>,
                "occupied": ${seat.occupied ? 'true' : 'false'}
            }<c:if test="${!status.last}">,</c:if>
        </c:forEach>
    ]
    </script>
    <script src="${pageContext.request.contextPath}/assets/js/cinema-seat-3d.js?v=20260810c"></script>
    <script src="${pageContext.request.contextPath}/assets/js/admin-seat-designer-core.js?v=20260810b"></script>
    <script src="${pageContext.request.contextPath}/assets/js/admin-seat-designer.js?v=20260810b"></script>
</body>
</html>
