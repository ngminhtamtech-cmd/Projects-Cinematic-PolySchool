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
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Quản lý bắp nước & combo - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
    <style>
        .combo-thumb-preview {
            width: 56px;
            height: 56px;
            border-radius: 8px;
            background-color: #f1f5f9;
            object-fit: cover;
            border: 1px solid #e2e8f0;
            flex-shrink: 0;
            display: grid;
            place-items: center;
        }

        .combo-form-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 14px;
            margin-bottom: 16px;
        }

        .combo-form-grid label {
            display: block;
            font-size: 12px;
            font-weight: 600;
            color: #475569;
            margin-bottom: 6px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .combo-form-grid input:not([type="file"]),
        .combo-form-grid select {
            width: 100%;
            height: 40px !important;
            box-sizing: border-box !important;
            border: 1px solid #E2E8F0;
            border-radius: 8px;
            padding: 8px 12px;
            font-size: 13px;
            background-color: #FFFFFF;
            color: #1E293B;
        }

        .combo-form-grid input[type="file"] {
            width: 100%;
            height: 40px !important;
            box-sizing: border-box !important;
            border: 1px solid #E2E8F0;
            border-radius: 8px;
            padding: 6px 10px;
            font-size: 12px;
            background-color: #FFFFFF;
        }
        
        .combo-form-grid input:focus,
        .combo-form-grid select:focus {
            border-color: #6D28D9;
            outline: none;
            box-shadow: 0 0 0 3px rgba(109, 40, 217, 0.1);
        }
    </style>
</head>
<body class="admin-body">
    <div class="dashboard">
        <%@ include file="/WEB-INF/views/admin/sidebar.jspf" %>
        <main class="dashboard-main">
            <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
            <div class="dashboard-content">
                <%@ include file="/WEB-INF/views/shared/flash.jspf" %>
                
                <!-- PAGE HEADER -->
                <div class="portal-head" style="margin-bottom:20px;">
                    <div>
                        <h1 style="font-size:22px;font-weight:600;color:#1A1A21;margin:0 0 4px;">Quản lý bắp nước &amp; combo</h1>
                        <p class="muted" style="font-size:13px;color:#6E6E7A;margin:0;">
                            Combo ở trạng thái <strong>active</strong> sẽ hiện trong bước "Chọn thức ăn đi kèm" của trang đặt vé. Combo đã có trong đơn hàng không xóa được — hãy chuyển sang <strong>Ngừng bán</strong> để ẩn khỏi trang đặt vé mà vẫn giữ nguyên lịch sử đơn.
                        </p>
                    </div>
                </div>

                <!-- SECTION 1: FORM THÊM COMBO MỚI (TOP FULL-WIDTH PANEL) -->
                <article class="panel-card" style="margin-bottom: 24px; padding: 20px;">
                    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;padding-bottom:10px;border-bottom:1px solid #E8E8EE;">
                        <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/></svg>
                            <span>Thêm combo mới</span>
                        </h2>
                        <span style="font-size:12px;color:#8A8A96;">Điền đầy đủ thông tin bên dưới để tạo combo thức ăn / bắp nước mới</span>
                    </div>

                    <form method="post" action="${pageContext.request.contextPath}/admin/combos" enctype="multipart/form-data">
                        <cb:csrf/>
                        <div class="combo-form-grid">
                            <div>
                                <label title="Tên combo"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Tên combo</label>
                                <input type="text" name="name" required placeholder="Combo Bắp Nước, Combo Couple...">
                            </div>
                            <div>
                                <label title="Giá bán (VNĐ)"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Giá bán (VNĐ)</label>
                                <input type="number" min="0" step="1000" name="price" required placeholder="69000">
                            </div>
                            <div>
                                <label title="Trạng thái">Trạng thái</label>
                                <select name="status">
                                    <option value="active">Đang bán (active)</option>
                                    <option value="inactive">Ngừng bán (inactive)</option>
                                </select>
                            </div>
                            <div>
                                <label title="Phạm vi áp dụng">Phạm vi áp dụng</label>
                                <select name="cinemaId" required>
                                    <option value="">Chọn rạp sở hữu</option>
                                    <c:forEach var="c" items="${cinemas}">
                                        <option value="${c.id}" ${not empty cinemaContextId and c.id eq cinemaContextId ? 'selected' : ''}><c:out value="${c.name}"/></option>
                                    </c:forEach>
                                </select>
                            </div>
                            <div>
                                <label title="Ảnh combo">Ảnh combo</label>
                                <input type="file" name="imageFile" accept="image/png,image/jpeg,image/gif,image/webp">
                            </div>
                            <div>
                                <label title="Mô tả ngắn">Mô tả ngắn</label>
                                <input type="text" name="description" placeholder="1 bắp lớn + 1 nước lớn">
                            </div>
                        </div>

                        <div style="display:flex;justify-content:flex-end;">
                            <button type="submit" class="btn-primary">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px;"><path d="M12 5v14M5 12h14"/></svg>
                                <span>Thêm combo</span>
                            </button>
                        </div>
                    </form>
                </article>

                <!-- SECTION 2: DANH SÁCH COMBO (MAIN DATA TABLE) -->
                <section class="panel-card" style="padding: 20px;">
                    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
                        <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M4 6h16M4 10h16M4 14h16M4 18h16"/></svg>
                            <span>Danh sách combo (${fn:escapeXml(fn:length(combos))})</span>
                        </h2>
                    </div>

                    <div class="table-responsive">
                        <table class="table-data">
                            <thead>
                                <tr>
                                    <th scope="col">Combo</th>
                                    <th scope="col">Giá bán</th>
                                    <th scope="col">Thành phần / Mô tả</th>
                                    <th scope="col">Phạm vi áp dụng</th>
                                    <th scope="col">Đã bán</th>
                                    <th scope="col">Trạng thái</th>
                                    <th scope="col" style="text-align:right;">Hành động</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="combo" items="${combos}">
                                    <c:set var="comboTableImg" value="${cbf:assetUrl(pageContext.request.contextPath, combo.image)}"/>
                                    <tr>
                                        <td>
                                            <div style="display:flex;align-items:center;gap:12px;">
                                                <c:choose>
                                                    <c:when test="${not empty comboTableImg}">
                                                        <img class="combo-thumb-preview" src="${fn:escapeXml(comboTableImg)}" alt="Ảnh ${fn:escapeXml(combo.name)}">
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span class="combo-thumb-preview" style="font-size:10px;color:#8A8A96;text-align:center;">Không ảnh</span>
                                                    </c:otherwise>
                                                </c:choose>
                                                <div>
                                                    <strong style="font-size:14px;color:#1A1A21;display:block;">${fn:escapeXml(combo.name)}</strong>
                                                    <span style="font-family:monospace;font-size:11px;color:#8A8A96;">#${fn:escapeXml(combo.id)}</span>
                                                </div>
                                            </div>
                                        </td>
                                        <td>
                                            <strong style="color:#ea580c;font-weight:700;">${cbf:whole(combo.price)} đ</strong>
                                        </td>
                                        <td>
                                            <span class="admin-clamp-2" style="font-size:13px;color:#475569;">${fn:escapeXml(empty combo.description ? '—' : combo.description)}</span>
                                        </td>
                                        <td>
                                            <span style="font-size:12px;color:#334155;font-weight:600;">
                                                ${fn:escapeXml(combo.cinemaName)}
                                            </span>
                                        </td>
                                        <td>
                                            <strong style="font-size:13px;color:#0f172a;">${fn:escapeXml(combo.soldQuantity)}</strong>
                                        </td>
                                        <td>
                                            <span class="badge-status ${combo.status eq 'active' ? 'status-success' : 'status-danger'}">
                                                <span class="status-dot"></span>${combo.status eq 'active' ? 'Đang bán' : 'Ngừng bán'}
                                            </span>
                                        </td>
                                        <td style="text-align:right;">
                                            <button type="button" class="button secondary" style="padding:5px 12px;font-size:12px;"
                                                    data-id="${fn:escapeXml(combo.id)}"
                                                    data-name="${fn:escapeXml(combo.name)}"
                                                    data-price="${fn:escapeXml(combo.price)}"
                                                    data-status="${fn:escapeXml(combo.status)}"
                                                    data-cinema-id="${fn:escapeXml(combo.cinemaId)}"
                                                    data-cinema-name="${fn:escapeXml(combo.cinemaName)}"
                                                    data-global="${combo.global ? 'true' : 'false'}"
                                                    data-description="${fn:escapeXml(combo.description)}"
                                                    data-image="${fn:escapeXml(combo.image)}"
                                                    data-image-url="${fn:escapeXml(comboTableImg)}"
                                                    data-sold="${combo.soldQuantity}"
                                                    onclick="openEditComboFromBtn(this)">Sửa</button>
                                        </td>
                                    </tr>
                                </c:forEach>
                                <c:if test="${empty combos}">
                                    <tr>
                                        <td colspan="7" style="text-align:center;padding:30px;color:#8A8A96;">
                                            Chưa có combo nào. Hãy thêm combo mới ở trên!
                                        </td>
                                    </tr>
                                </c:if>
                            </tbody>
                        </table>
                    </div>
                </section>
            </div>
        </main>
    </div>

    <!-- QUICK EDIT COMBO MODAL -->
    <div id="editComboModal" class="modal-backdrop" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(15,23,42,0.6); z-index:9999; align-items:center; justify-content:center;" aria-hidden="true">
        <div class="modal-content" style="background:#ffffff; border-radius:12px; width:92%; max-width:620px; max-height:90vh; overflow-y:auto; padding:24px; box-shadow:0 20px 25px -5px rgba(0,0,0,0.15);" role="dialog" aria-modal="true" aria-labelledby="editComboModalTitle">
            <div style="display:flex; align-items:center; justify-content:space-between; margin-bottom:16px; padding-bottom:10px; border-bottom:1px solid #e2e8f0;">
                <h3 style="margin:0; font-size:16px; font-weight:700; color:#0f172a;" id="editComboModalTitle">Chỉnh sửa combo bắp nước</h3>
                <button type="button" onclick="closeEditComboModal()" aria-label="Đóng" style="background:none; border:none; font-size:24px; cursor:pointer; color:#64748b; line-height:1;">&times;</button>
            </div>

            <form method="post" action="${pageContext.request.contextPath}/admin/combos" id="editComboForm" enctype="multipart/form-data">
                <cb:csrf/>
                <input type="hidden" name="id" id="editComboId">
                <input type="hidden" name="image" id="editComboImage">
                <input type="hidden" name="action" id="editComboAction" value="">
                
                <div style="display:flex; align-items:center; gap:16px; margin-bottom:16px; padding:12px; background:#f8fafc; border-radius:8px; border:1px solid #e2e8f0;">
                    <img id="editComboPreviewImg" class="combo-thumb-preview" src="" alt="Ảnh combo" style="display:none; width:64px; height:64px;">
                    <div id="editComboNoImg" class="combo-thumb-preview" style="display:grid; width:64px; height:64px; font-size:11px; color:#64748b; text-align:center;">Không ảnh</div>
                    <div>
                        <strong id="editComboNameHeader" style="font-size:15px; color:#0f172a; display:block;"></strong>
                        <span id="editComboSoldText" style="font-size:12px; color:#047857; font-weight:600;"></span>
                    </div>
                </div>

                <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(220px, 1fr)); gap:12px; margin-bottom:16px;">
                    <div>
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Tên combo</label>
                        <input type="text" name="name" id="editComboName" required style="width:100%;height:40px;padding:8px 12px;border:1px solid #e2e8f0;border-radius:8px;font-size:13px;box-sizing:border-box;">
                    </div>
                    <div>
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Giá bán (VNĐ)</label>
                        <input type="number" min="0" step="1000" name="price" id="editComboPrice" required style="width:100%;height:40px;padding:8px 12px;border:1px solid #e2e8f0;border-radius:8px;font-size:13px;box-sizing:border-box;">
                    </div>
                    <div>
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;">Trạng thái</label>
                        <select name="status" id="editComboStatus" style="width:100%;height:40px;padding:8px 12px;border:1px solid #e2e8f0;border-radius:8px;font-size:13px;box-sizing:border-box;">
                            <option value="active">Đang bán (active)</option>
                            <option value="inactive">Ngừng bán (inactive)</option>
                        </select>
                    </div>
                    <div>
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;">Phạm vi áp dụng</label>
                        <input type="hidden" name="cinemaId" id="editComboCinemaId">
                        <input type="text" id="editComboCinemaDisplay" readonly style="width:100%;height:40px;padding:8px 12px;border:1px solid #e2e8f0;border-radius:8px;font-size:13px;background:#f1f5f9;box-sizing:border-box;">
                    </div>
                </div>

                <div style="margin-bottom:12px;">
                    <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;">Đổi ảnh combo</label>
                    <input type="file" name="imageFile" accept="image/png,image/jpeg,image/gif,image/webp" style="width:100%;padding:6px;border:1px solid #e2e8f0;border-radius:8px;font-size:12px;box-sizing:border-box;">
                    <span style="font-size:11px;color:#64748b;margin-top:2px;display:block;">Bỏ trống để giữ ảnh hiện tại.</span>
                </div>

                <div style="margin-bottom:16px;">
                    <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;">Mô tả ngắn</label>
                    <input type="text" name="description" id="editComboDescription" placeholder="1 bắp lớn + 1 nước lớn" style="width:100%;height:40px;padding:8px 12px;border:1px solid #e2e8f0;border-radius:8px;font-size:13px;box-sizing:border-box;">
                </div>

                <div style="display:flex; justify-content:space-between; align-items:center; margin-top:20px; padding-top:14px; border-top:1px solid #e2e8f0;">
                    <div>
                        <button type="button" id="editComboDeleteBtn" class="button danger" style="padding:8px 16px;" onclick="submitComboDelete()">Xóa combo</button>
                    </div>
                    <div style="display:flex; gap:10px;">
                        <button type="button" id="editComboToggleBtn" class="button secondary" style="padding:8px 16px;" onclick="submitComboToggleStatus()"></button>
                        <button type="button" onclick="closeEditComboModal()" class="button secondary" style="padding:8px 16px;">Hủy</button>
                        <button type="submit" class="btn-primary" style="padding:8px 20px;">Lưu thay đổi</button>
                    </div>
                </div>
            </form>
        </div>
    </div>

    <script>
        function openEditComboFromBtn(btn) {
            if (!btn || !btn.dataset) return;
            var d = btn.dataset;
            openEditComboModal({
                id: d.id,
                name: d.name,
                price: d.price,
                status: d.status,
                cinemaId: d.cinemaId,
                cinemaName: d.cinemaName,
                global: d.global === 'true',
                description: d.description,
                image: d.image,
                imageUrl: d.imageUrl,
                soldQuantity: parseInt(d.sold || '0', 10)
            });
        }

        function openEditComboModal(c) {
            document.getElementById('editComboId').value = c.id || '';
            document.getElementById('editComboImage').value = c.image || '';
            document.getElementById('editComboAction').value = '';
            document.getElementById('editComboName').value = c.name || '';
            document.getElementById('editComboNameHeader').innerText = c.name || '';
            document.getElementById('editComboPrice').value = c.price || '0';
            document.getElementById('editComboStatus').value = c.status || 'active';
            document.getElementById('editComboDescription').value = c.description || '';

            var previewImg = document.getElementById('editComboPreviewImg');
            var noImg = document.getElementById('editComboNoImg');
            if (c.imageUrl && c.imageUrl.trim() !== '') {
                previewImg.src = c.imageUrl;
                previewImg.style.display = 'block';
                noImg.style.display = 'none';
            } else {
                previewImg.style.display = 'none';
                noImg.style.display = 'grid';
            }

            var soldText = document.getElementById('editComboSoldText');
            if (c.soldQuantity > 0) {
                soldText.innerText = 'Đã bán ' + c.soldQuantity + ' phần';
            } else {
                soldText.innerText = 'Chưa có lượt bán';
            }

            var cinemaSelect = document.getElementById('editComboCinemaId');
            if (cinemaSelect) {
                cinemaSelect.value = c.cinemaId || '';
            }
            var cinemaDisplay = document.getElementById('editComboCinemaDisplay');
            if (cinemaDisplay) {
                cinemaDisplay.value = c.cinemaName || '';
            }

            var deleteBtn = document.getElementById('editComboDeleteBtn');
            if (c.soldQuantity > 0) {
                deleteBtn.disabled = true;
                deleteBtn.title = 'Combo đã có trong ' + c.soldQuantity + ' phần đã bán nên không thể xóa.';
                deleteBtn.style.opacity = '0.5';
                deleteBtn.style.cursor = 'not-allowed';
            } else {
                deleteBtn.disabled = false;
                deleteBtn.title = '';
                deleteBtn.style.opacity = '1';
                deleteBtn.style.cursor = 'pointer';
            }

            var toggleBtn = document.getElementById('editComboToggleBtn');
            if (c.status === 'active') {
                toggleBtn.innerText = 'Ngừng bán';
                toggleBtn.dataset.targetStatus = 'inactive';
            } else {
                toggleBtn.innerText = 'Mở bán lại';
                toggleBtn.dataset.targetStatus = 'active';
            }

            var modal = document.getElementById('editComboModal');
            modal.style.display = 'flex';
            modal.setAttribute('aria-hidden', 'false');
        }

        function closeEditComboModal() {
            var modal = document.getElementById('editComboModal');
            modal.style.display = 'none';
            modal.setAttribute('aria-hidden', 'true');
        }

        function submitComboDelete() {
            showAdminConfirm({
                title: 'Xóa Combo bắp nước',
                message: 'Bạn có chắc chắn muốn xóa combo này không?',
                subnote: 'Lưu ý: Thao tác này không thể hoàn tác.',
                confirmText: 'Xóa combo',
                cancelText: 'Hủy bỏ',
                isDanger: true,
                onConfirm: function() {
                    var form = document.getElementById('editComboForm');
                    document.getElementById('editComboAction').value = 'delete';
                    form.submit();
                }
            });
        }

        function submitComboToggleStatus() {
            var form = document.getElementById('editComboForm');
            var toggleBtn = document.getElementById('editComboToggleBtn');
            document.getElementById('editComboAction').value = 'toggleStatus';
            document.getElementById('editComboStatus').value = toggleBtn.dataset.targetStatus;
            form.submit();
        }
    </script>
</body>
</html>
