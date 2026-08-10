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
    <title>Quản Lý Phòng Chiếu - CineBook Enterprise</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
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
                        <h1>Quản lý danh sách phòng chiếu</h1>
                        <p class="muted">Hệ thống quản lý thông tin phòng chiếu và thiết kế sơ đồ ma trận ghế tương tác.</p>
                    </div>
                    <c:choose>
                        <c:when test="${sessionScope.currentUser.role eq 'manager'}"><a href="${pageContext.request.contextPath}/admin/requests?action=room" class="btn-primary">Đề nghị phòng mới</a></c:when>
                        <c:otherwise><a href="${pageContext.request.contextPath}/admin/rooms?action=create" class="btn-primary">Thêm phòng chiếu mới</a></c:otherwise>
                    </c:choose>
                </section>

                <nav class="lifecycle-tabs" aria-label="Vòng đời phòng chiếu">
                    <a href="${pageContext.request.contextPath}/admin/rooms?lifecycle=active" aria-current="${lifecycle eq 'deleted' ? 'false' : 'page'}">Đang quản lý</a>
                    <a href="${pageContext.request.contextPath}/admin/rooms?lifecycle=deleted" aria-current="${lifecycle eq 'deleted' ? 'page' : 'false'}">Đã bị xóa</a>
                </nav>

                <c:if test="${lifecycle eq 'deleted'}">
                    <div class="alert-banner alert-info" role="status">
                        Phòng chiếu đã xóa vẫn được giữ nguyên trong hệ thống: toàn bộ suất chiếu, vé đã bán
                        và số liệu doanh thu của chúng <strong>không thay đổi</strong> trong Báo cáo. Chỉ có
                        phần vận hành là dừng lại.
                    </div>
                </c:if>

                <c:if test="${not empty rooms}">
                    <div class="admin-table-wrap">
                        <div class="admin-table-scroll" tabindex="0" aria-label="Danh sách phòng chiếu">
                            <table class="admin-data-table admin-entity-table">
                                <thead><tr><th scope="col">Phòng chiếu</th><th scope="col">Rạp / Cơ sở</th><th scope="col">Sức chứa</th><th scope="col">Trạng thái</th><th scope="col">Loại phòng</th><th scope="col">Sơ đồ ghế</th><th scope="col">Mã phòng</th><th scope="col">Thao tác</th></tr></thead>
                                <tbody>
                                    <c:forEach var="room" items="${rooms}">
                                        <tr data-admin-search-item>
                                            <td><strong>${fn:escapeXml(room.name)}</strong></td>
                                            <td>${fn:escapeXml(empty room.cinemaName ? '—' : room.cinemaName)}</td>
                                            <td><strong>${fn:escapeXml(room.seatCount)}</strong> ghế</td>
                                            <td><c:choose><c:when test="${room.status eq 'deleted'}"><span class="badge-status status-neutral"><span class="status-dot"></span>Đã bị xóa</span></c:when><c:when test="${room.status eq 'inactive'}"><span class="badge-status status-danger"><span class="status-dot"></span>Ngừng hoạt động</span></c:when><c:otherwise><span class="badge-status status-success"><span class="status-dot"></span>Hoạt động</span></c:otherwise></c:choose></td>
                                            <td>${fn:escapeXml(empty room.roomType ? '—' : room.roomType)}</td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${room.status eq 'deleted'}"><span class="muted">${fn:escapeXml(room.seatCount)} ghế (lưu trữ)</span></c:when>
                                                    <c:otherwise><a href="${pageContext.request.contextPath}/admin/rooms/seats?roomId=${fn:escapeXml(room.id)}" class="button secondary">Chỉnh sửa ghế</a></c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td><span class="badge-status status-info">#${fn:escapeXml(room.id)}</span></td>
                                            <td>
                                                <div class="admin-row-actions">
                                                    <c:choose>
                                                        <c:when test="${room.status eq 'deleted'}">
                                                            <span class="muted">Chỉ xem — dữ liệu doanh thu được giữ nguyên</span>
                                                        </c:when>
                                                        <c:otherwise>
                                                            <a href="${pageContext.request.contextPath}/admin/rooms?action=edit&id=${fn:escapeXml(room.id)}" class="button secondary">Chỉnh sửa</a>
                                                            <c:if test="${room.status eq 'inactive'}">
                                                                <form method="post" action="${pageContext.request.contextPath}/admin/rooms" style="display:inline-block;">
                                                                    <cb:csrf/><input type="hidden" name="id" value="${fn:escapeXml(room.id)}"><input type="hidden" name="action" value="activate">
                                                                    <button type="submit" class="button success">Khôi phục</button>
                                                                </form>
                                                                <button type="button" class="button danger" style="background:#dc2626; color:#ffffff; margin-left:4px;" onclick="openConfirmDeleteRoomModal('${fn:escapeXml(room.id)}', '${fn:escapeXml(room.name)}')">Xóa</button>
                                                            </c:if>
                                                            <c:if test="${room.status ne 'inactive'}"><button type="button" class="button danger btn-delete-room" data-id="${fn:escapeXml(room.id)}">Tạm ngừng</button></c:if>
                                                        </c:otherwise>
                                                    </c:choose>
                                                </div>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </c:if>

                <c:if test="${empty rooms}">
                    <article class="empty-state-block">
                        <c:choose>
                            <c:when test="${lifecycle eq 'deleted'}">
                                <h3>Chưa có phòng chiếu nào bị xóa</h3>
                                <p class="muted">Phòng chiếu bị xóa sẽ được liệt kê ở đây thay vì biến mất khỏi hệ thống.</p>
                            </c:when>
                            <c:otherwise>
                                <h3>Chưa có phòng chiếu nào</h3>
                                <p class="muted">Bấm nút bên dưới để khởi tạo phòng chiếu đầu tiên và bắt đầu vẽ sơ đồ ghế.</p>
                                <a href="${pageContext.request.contextPath}${sessionScope.currentUser.role eq 'manager' ? '/admin/requests?action=room' : '/admin/rooms?action=create'}" class="btn-primary">
                                    ${sessionScope.currentUser.role eq 'manager' ? 'Đề nghị phòng mới' : 'Thêm phòng chiếu mới ngay'}
                                </a>
                            </c:otherwise>
                        </c:choose>
                    </article>
                </c:if>
            </div>
        </main>
    </div>

    <!-- MODAL XÁC NHẬN XÓA 2 BƯỚC -->
    <div id="deleteRoomModal" class="modal-overlay" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(15,23,42,0.6); z-index:9999; align-items:center; justify-content:center;">
        <div class="modal-card" style="background:#ffffff; border-radius:12px; width:90%; max-width:540px; padding:24px; box-shadow:0 20px 25px -5px rgba(0,0,0,0.1);">
            <!-- BƯỚC 1: CẢNH BÁO VÀ THỐNG KÊ TÁC ĐỘNG -->
            <div id="deleteStep1">
                <div style="display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid #e2e8f0; padding-bottom:12px; margin-bottom:16px;">
                    <h3 style="margin:0; font-size:1.15rem; color:#0f172a;" id="deleteModalTitle">Bước 1: Phân tích tác động tạm ngừng phòng chiếu</h3>
                    <button type="button" onclick="closeDeleteModal()" class="admin-modal-close" aria-label="Đóng">×</button>
                </div>
                <div id="deleteImpactContent">
                    <p class="muted">Đang tải dữ liệu kiểm tra tác động...</p>
                </div>
                <div style="display:flex; justify-content:flex-end; gap:10px; margin-top:20px; border-top:1px solid #e2e8f0; padding-top:16px;">
                    <button type="button" class="button secondary" onclick="closeDeleteModal()">Hủy bỏ</button>
                    <button type="button" id="btnNextStep2" class="button danger" onclick="goToStep2()">Tiếp tục Bước 2</button>
                </div>
            </div>

            <!-- BƯỚC 2: XÁC NHẬN HÀNH ĐỘNG CỦA NGƯỜI THAO TÁC -->
            <div id="deleteStep2" style="display:none;">
                <div style="display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid #e2e8f0; padding-bottom:12px; margin-bottom:16px;">
                    <h3 style="margin:0; font-size:1.15rem; color:#dc2626;">Bước 2: Xác nhận thực thi thao tác</h3>
                    <button type="button" onclick="closeDeleteModal()" class="admin-modal-close" aria-label="Đóng">×</button>
                </div>
                <div id="step2SummaryMessage" style="font-size:0.92rem; color:#334155; line-height:1.5; margin-bottom:16px;">
                </div>
                <form method="post" action="${pageContext.request.contextPath}/admin/rooms" id="deleteRoomForm">
                    <cb:csrf/>
                    <input type="hidden" name="action" value="deactivate">
                    <input type="hidden" name="id" id="deleteRoomIdInput" value="">
                    
                    <div style="display:flex; justify-content:flex-end; gap:10px; border-top:1px solid #e2e8f0; padding-top:16px;">
                        <button type="button" class="button secondary" onclick="goToStep1()">Quay lại Bước 1</button>
                        <button type="submit" class="button danger">
                            Xác nhận tạm ngừng hoạt động
                        </button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <script>
        var currentImpactData = null;
        var currentDeletingRoomId = 0;

        document.addEventListener('DOMContentLoaded', function() {
            var btns = document.querySelectorAll('.btn-delete-room');
            btns.forEach(function(btn) {
                btn.addEventListener('click', function() {
                    var roomId = this.getAttribute('data-id');
                    openDeleteRoomModal(roomId);
                });
            });
        });

        function openDeleteRoomModal(roomId) {
            currentDeletingRoomId = roomId;
            document.getElementById('deleteRoomIdInput').value = roomId;
            document.getElementById('deleteStep1').style.display = 'block';
            document.getElementById('deleteStep2').style.display = 'none';
            document.getElementById('deleteRoomModal').style.display = 'flex';
            document.getElementById('btnNextStep2').disabled = true;
            document.getElementById('deleteImpactContent').innerHTML = '<p class="muted">Đang phân tích dữ liệu suất chiếu và vé liên quan...</p>';

            // BUG-16: phai XIN JSON thi duong loi cua AuthFilter/CsrfFilter moi tra JSON. Thieu
            // header nay, mot phien het han se tra ve HTML trang login va res.json() nem loi parse
            // — quan tri vien thay modal treo thay vi thong bao "vui long dang nhap lai".
            fetch('${pageContext.request.contextPath}/admin/rooms/impact?roomId=' + roomId,
                    { headers: { 'Accept': 'application/json' } })
                .then(function(res) {
                    if (!res.ok) throw new Error('HTTP ' + res.status);
                    return res.json();
                })
                .then(function(data) {
                    currentImpactData = data;
                    renderStep1(data);
                    document.getElementById('btnNextStep2').disabled = false;
                })
                .catch(function(err) {
                    currentImpactData = null;
                    document.getElementById('deleteImpactContent').innerHTML = '<div class="alert-banner alert-danger" role="alert">Không tải được dữ liệu tác động. Thao tác đã bị khóa; vui lòng thử lại.</div>';
                });
        }

        function escapeHtml(value) {
            return String(value == null ? '' : value)
                .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
        }

        function renderStep1(data) {
            var html = '';
            html += '<div style="background:#f8fafc; border:1px solid #e2e8f0; border-radius:8px; padding:14px; margin-bottom:14px;">';
            html += '  <div style="font-weight:800; font-size:1.05rem; color:#0f172a;">' + escapeHtml(data.roomName) + (data.cinemaName ? (' (' + escapeHtml(data.cinemaName) + ')') : '') + '</div>';
            html += '</div>';

            html += '<div style="display:grid; grid-template-columns: 1fr 1fr; gap:12px; margin-bottom:14px;">';
            html += '  <div style="background:#eff6ff; border:1px solid #bfdbfe; padding:12px; border-radius:8px;">';
            html += '    <div style="font-size:0.8rem; color:#1e40af; font-weight:700;">SUẤT CHIẾU LIÊN QUAN</div>';
            html += '    <div style="font-size:1.4rem; font-weight:800; color:#1e3a8a;">' + data.showtimeCount + ' suất</div>';
            html += '    <div style="font-size:0.78rem; color:#3b82f6;">' + data.activeShowtimeCount + ' suất chưa diễn ra</div>';
            html += '  </div>';

            html += '  <div style="background:#fff7ed; border:1px solid #fed7aa; padding:12px; border-radius:8px;">';
            html += '    <div style="font-size:0.8rem; color:#c2410c; font-weight:700;">DỮ LIỆU VÉ ĐÃ BÁN</div>';
            html += '    <div style="font-size:1.4rem; font-weight:800; color:#9a3412;">' + data.totalTicketCount + ' vé</div>';
            html += '    <div style="font-size:0.78rem; color:#ea580c;">' + data.pendingTicketCount + ' vé chờ check-in</div>';
            html += '  </div>';
            html += '</div>';

            if (data.showtimeCount > 0 || data.totalTicketCount > 0) {
                html += '<div style="background:#fef2f2; border:1px solid #fca5a5; color:#991b1b; padding:12px 14px; border-radius:8px; font-size:0.88rem; line-height:1.5;">';
                html += '  <strong>Chế độ Soft Delete tự động:</strong> Phòng chiếu có <strong>' + data.showtimeCount + ' suất chiếu</strong> và <strong>' + data.totalTicketCount + ' vé đã bán</strong>.<br>';
                html += '  Hệ thống sẽ <strong>TẠM NGỪNG HOẠT ĐỘNG</strong> phòng chiếu để bảo toàn báo cáo doanh thu.<br>';
                html += '  Vé người dùng đã mua vẫn hợp lệ để check-in. Phía User sẽ hiển thị nhãn "[Tạm ngưng hoạt động]" và chặn mua thêm vé mới.';
                html += '</div>';
            } else {
                html += '<div style="background:#f0fdf4; border:1px solid #86efac; color:#166534; padding:12px 14px; border-radius:8px; font-size:0.88rem;">';
                html += '  <strong>Phòng chưa có dữ liệu vận hành:</strong> Chưa có suất chiếu hay vé nào. Thao tác này vẫn chỉ chuyển phòng sang Inactive; hard-delete chỉ được thực hiện từ cảnh báo đã hoàn tất.';
                html += '</div>';
            }

            document.getElementById('deleteImpactContent').innerHTML = html;
        }

        function goToStep2() {
            if (!currentImpactData) return;
            document.getElementById('deleteStep1').style.display = 'none';
            document.getElementById('deleteStep2').style.display = 'block';

            var msg = '';
            if (currentImpactData.showtimeCount > 0 || currentImpactData.totalTicketCount > 0) {
                msg = 'Bạn đang chuẩn bị chuyển trạng thái phòng <strong>"' + (currentImpactData.roomName || ('Phòng chiếu #' + currentDeletingRoomId)) + '"</strong> sang <strong>[TẠM NGỪNG HOẠT ĐỘNG]</strong>.<br>Hành động này sẽ được ghi vào Audit Logs kèm thông tin snapshot chi tiết người thao tác.';
            } else {
                msg = 'Bạn đang chuẩn bị chuyển phòng <strong>"' + (currentImpactData.roomName || ('Phòng chiếu #' + currentDeletingRoomId)) + '"</strong> sang <strong>[TẠM NGỪNG HOẠT ĐỘNG]</strong>. Hard-delete không được thực hiện từ thao tác này.';
            }
            document.getElementById('step2SummaryMessage').innerHTML = msg;
        }

        function goToStep1() {
            document.getElementById('deleteStep1').style.display = 'block';
            document.getElementById('deleteStep2').style.display = 'none';
        }

        function closeDeleteModal() {
            document.getElementById('deleteRoomModal').style.display = 'none';
        }

        function openConfirmDeleteRoomModal(roomId, roomName) {
            document.getElementById('confirmDeleteRoomIdInput').value = roomId;
            document.getElementById('deleteRoomNameText').textContent = "'" + roomName + "'";
            document.getElementById('confirmDeleteRoomModal').style.display = 'flex';
        }

        function closeConfirmDeleteRoomModal() {
            document.getElementById('confirmDeleteRoomModal').style.display = 'none';
        }
    </script>

    <!-- MINIMAL CONFIRM DELETE ROOM MODAL -->
    <div id="confirmDeleteRoomModal" class="modal-overlay" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(15,23,42,0.5); backdrop-filter:blur(4px); z-index:99999; align-items:center; justify-content:center;">
        <div class="modal-card" style="background:#ffffff; border-radius:16px; width:90%; max-width:440px; padding:24px; box-shadow:0 20px 25px -5px rgba(0,0,0,0.1), 0 8px 10px -6px rgba(0,0,0,0.1); text-align:left;">
            <div style="display:flex; align-items:center; gap:12px; margin-bottom:14px;">
                <div style="width:40px; height:40px; border-radius:10px; background:#fef2f2; border:1px solid #fee2e2; display:flex; align-items:center; justify-content:center; flex-shrink:0;">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#dc2626" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M10 11v6M14 11v6"/>
                    </svg>
                </div>
                <div>
                    <h3 style="margin:0; font-size:1.1rem; font-weight:700; color:#0f172a;">Xóa phòng chiếu</h3>
                    <span style="font-size:0.8rem; color:#64748b;">Xác nhận thực hiện thao tác xóa</span>
                </div>
            </div>
            
            <p style="font-size:0.92rem; color:#334155; line-height:1.55; margin:0 0 16px 0;">
                Bạn có chắc chắn muốn xóa phòng chiếu <strong id="deleteRoomNameText" style="color:#0f172a;"></strong>?
            </p>

            <div style="background:#f8fafc; border:1px solid #e2e8f0; border-radius:10px; padding:12px 14px; font-size:0.82rem; color:#475569; line-height:1.5; margin-bottom:20px;">
                <strong style="color:#0284c7; display:block; margin-bottom:2px;">ℹ️ Lưu ý bảo toàn dữ liệu:</strong>
                Toàn bộ dữ liệu doanh thu và vé bán lịch sử của phòng chiếu này sẽ được <strong>bảo toàn 100%</strong> trong báo cáo thống kê.
            </div>

            <form method="post" action="${pageContext.request.contextPath}/admin/rooms" id="confirmDeleteRoomForm">
                <cb:csrf/>
                <input type="hidden" name="action" value="delete">
                <input type="hidden" name="id" id="confirmDeleteRoomIdInput" value="">
                <div style="display:flex; justify-content:flex-end; gap:10px;">
                    <button type="button" class="button secondary" onclick="closeConfirmDeleteRoomModal()" style="padding:9px 16px; border-radius:8px; font-weight:600;">Hủy bỏ</button>
                    <button type="submit" class="button danger" style="background:#dc2626; color:#ffffff; padding:9px 18px; border-radius:8px; font-weight:600; border:none; cursor:pointer;">
                        Xác nhận xóa
                    </button>
                </div>
            </form>
        </div>
    </div>
</body>
</html>
