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
    <title>Nhân viên quầy vé - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
    <style>
        .role-scope-note {
            background: #f0f7ff;
            border: 1px solid #bfdbfe;
            border-left: 4px solid #034EA2;
            border-radius: 8px;
            padding: 14px 16px;
            margin-bottom: 22px;
            font-size: 13.5px;
            line-height: 1.65;
            color: #1e3a5f;
        }
        .role-scope-note strong { color: #034EA2; }
        .scope-can, .scope-cannot { margin: 6px 0 0; padding-left: 20px; }
        .scope-can li { color: #047857; }
        .scope-cannot li { color: #9f1239; }
        .staff-locked-row { background: #fff5f5; }
        .lock-reason-text {
            font-size: 12px;
            color: #9f1239;
            font-style: italic;
            display: block;
            margin-top: 3px;
        }
    </style>
</head>
<body class="admin-body">
    <div class="dashboard">
        <%@ include file="/WEB-INF/views/admin/sidebar.jspf" %>
        <main class="dashboard-main">
            <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>

            <div class="dashboard-content">
                <div class="portal-head" style="margin-bottom: 20px;">
                    <h1 style="font-size:22px;font-weight:600;color:#1A1A21;margin:0 0 4px;">Nhân viên quầy vé</h1>
                    <p class="muted">Tài khoản dùng tại quầy để thu tiền mặt và soát vé/check-in bằng mã QR.</p>
                </div>

                <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

                <div class="role-scope-note">
                    <strong>Phạm vi quyền của vai trò "Nhân viên quầy vé"</strong>
                    <ul class="scope-can">
                                                <li>Được truy cập màn hình quầy vé <code>/staff/checkin</code>: quét QR, soát vé, check-in</li>
                                                <li>Được thu tiền mặt cho đơn "thanh toán tại quầy" (khách được cộng điểm loyalty ngay)</li>
                    </ul>
                    <ul class="scope-cannot">
                                                <li>Không vào được khu quản trị: phim, rạp, phòng chiếu, suất chiếu, khuyến mãi, báo cáo</li>
                                                <li>Không xem được doanh thu và không quản lý được tài khoản nào</li>
                    </ul>
                </div>

                <article class="panel-card" style="margin-bottom: 24px; padding: 20px;">
                    <h2 style="font-size:16px;margin:0 0 16px;font-weight:600;color:#1A1A21;">Tạo tài khoản nhân viên mới</h2>
                    <form method="post" action="${pageContext.request.contextPath}/admin/staff">
                        <cb:csrf/>
                        <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:14px;">
                            <div>
                                <label style="font-size:12px;font-weight:600;color:#52525E;"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Username</label>
                                <input type="text" name="username" required placeholder="nv.quayve01" style="width:100%;">
                            </div>
                            <div>
                                <label style="font-size:12px;font-weight:600;color:#52525E;"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Họ và tên</label>
                                <input type="text" name="fullName" required placeholder="Nguyễn Văn A" style="width:100%;">
                            </div>
                            <div>
                                <label style="font-size:12px;font-weight:600;color:#52525E;"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Địa chỉ Email</label>
                                <input type="email" name="email" required placeholder="staff@cinebook.local" style="width:100%;">
                            </div>
                            <div>
                                <label style="font-size:12px;font-weight:600;color:#52525E;"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Mật khẩu tạm</label>
                                <input type="password" name="password" minlength="10" required placeholder="Tối thiểu 10 ký tự, đủ 3/4 nhóm" style="width:100%;">
                            </div>
                            <div>
                                <label style="font-size:12px;font-weight:600;color:#52525E;"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Rạp phân công</label>
                                <select name="cinemaId" required style="width:100%;padding:9px 12px;border:1px solid #E4E4EA;border-radius:8px;font-size:13px;">
                                    <option value="">-- Chọn Cụm Rạp --</option>
                                    <c:forEach var="c" items="${cinemas}">
                                        <option value="${fn:escapeXml(c.id)}">${fn:escapeXml(c.name)}</option>
                                    </c:forEach>
                                </select>
                            </div>
                            <div>
                                <label style="font-size:12px;font-weight:600;color:#52525E;">Số điện thoại</label>
                                <input type="text" name="phone" placeholder="0901 xxx xxx" style="width:100%;">
                            </div>
                            <div>
                                <label style="font-size:12px;font-weight:600;color:#52525E;">Địa chỉ / Ca làm</label>
                                <input type="text" name="address" placeholder="Ca sáng — CineBook Quận 1" style="width:100%;">
                            </div>
                        </div>
                        <div style="margin-top:16px;">
                            <button class="btn-primary" type="submit">Tạo tài khoản nhân viên</button>
                        </div>
                    </form>
                </article>

                <section class="panel-card" style="padding: 20px;">
                    <h2 style="font-size:16px;margin:0 0 16px;font-weight:600;color:#1A1A21;">
                        Danh sách nhân viên
                        <span class="muted" style="font-weight:400;">(${fn:escapeXml(staffs.size())} tài khoản)</span>
                    </h2>

                    <c:choose>
                        <c:when test="${empty staffs}">
                            <p class="muted" style="padding:26px 0;text-align:center;">
                                Chưa có tài khoản nhân viên quầy vé nào. Tạo tài khoản đầu tiên bằng biểu mẫu bên trên.
                            </p>
                        </c:when>
                        <c:otherwise>
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>Nhân viên</th>
                                        <th>Liên hệ</th>
                                        <th>Rạp phân công</th>
                                        <th>Ca làm / Ghi chú</th>
                                        <th>Trạng thái</th>
                                        <th>Ngày tạo</th>
                                        <th style="text-align:right;">Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="s" items="${staffs}">
                                        <tr class="${fn:escapeXml(s.locked ? 'staff-locked-row' : '')}">
                                            <td>
                                                <strong><c:out value="${s.fullName}"/></strong>
                                                <div class="mono muted" style="font-size:12px;">@<c:out value="${s.username}"/></div>
                                            </td>
                                            <td>
                                                <c:out value="${s.email}"/>
                                                <c:if test="${not empty s.phone}">
                                                    <div class="muted" style="font-size:12px;"><c:out value="${s.phone}"/></div>
                                                </c:if>
                                            </td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${not empty s.cinemaName}">
                                                        <span class="status-pill info" style="font-weight:600;"><c:out value="${s.cinemaName}"/></span>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span class="muted">— (Chưa gán rạp)</span>
                                                    </c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td><c:out value="${empty s.address ? '—' : s.address}"/></td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${s.locked}">
                                                        <span class="status-pill danger">Đã khóa</span>
                                                        <c:if test="${not empty s.lockReason}">
                                                            <span class="lock-reason-text"><c:out value="${s.lockReason}"/></span>
                                                        </c:if>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span class="status-pill success">Đang hoạt động</span>
                                                    </c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td><c:out value="${fn:substring(s.createdAt, 0, 10)}"/></td>
                                            <td style="text-align:right;white-space:nowrap;">
                                                <c:if test="${sessionScope.currentUser.role eq 'admin'}">
                                                    <form method="post" action="${pageContext.request.contextPath}/admin/staff" style="display:flex;gap:6px;justify-content:flex-end;margin:0 0 6px;">
                                                        <cb:csrf/>
                                                        <input type="hidden" name="id" value="${fn:escapeXml(s.id)}">
                                                        <input type="hidden" name="action" value="reassign">
                                                        <select name="cinemaId" required aria-label="Rạp phân công mới" style="height:30px;max-width:170px;font-size:12px;padding:3px 8px;">
                                                            <c:forEach var="cinema" items="${cinemas}">
                                                                <option value="${fn:escapeXml(cinema.id)}" ${cinema.id eq s.cinemaId ? 'selected' : ''}>${fn:escapeXml(cinema.name)}</option>
                                                            </c:forEach>
                                                        </select>
                                                        <button class="btn-secondary" type="submit" style="padding:4px 9px!important;font-size:12px!important;">Đổi rạp</button>
                                                    </form>
                                                </c:if>
                                                <form method="post" action="${pageContext.request.contextPath}/admin/staff" style="display:inline;margin:0;">
                                                    <cb:csrf/>
                                                    <input type="hidden" name="id" value="${fn:escapeXml(s.id)}">
                                                    <c:choose>
                                                        <c:when test="${s.locked}">
                                                            <button class="btn-secondary" type="submit" name="action" value="unlock"
                                                                    style="padding:4px 10px!important;font-size:12px!important;">Mở khóa</button>
                                                        </c:when>
                                                        <c:otherwise>
                                                            <input type="hidden" name="lockReason" value="Tài khoản nhân viên bị tạm khóa bởi quản lý.">
                                                            <button class="btn-secondary" type="submit" name="action" value="lock"
                                                                    onclick="return confirm('Khóa tài khoản nhân viên này? Họ sẽ không đăng nhập được cho tới khi mở khóa.')"
                                                                    style="padding:4px 10px!important;font-size:12px!important;">Khóa</button>
                                                        </c:otherwise>
                                                    </c:choose>
                                                </form>
                                                <form method="post" action="${pageContext.request.contextPath}/admin/staff" style="display:inline;margin:0 0 0 4px;">
                                                    <cb:csrf/>
                                                    <input type="hidden" name="id" value="${fn:escapeXml(s.id)}">
                                                    <button class="btn-danger-outline" type="submit" name="action" value="delete"
                                                            onclick="return confirm('Bạn có chắc chắn muốn XÓA vĩnh viễn tài khoản nhân viên này?')"
                                                            style="padding:4px 10px!important;font-size:12px!important;">Xóa</button>
                                                </form>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                </tbody>
                            </table>
                        </c:otherwise>
                    </c:choose>
                </section>
            </div>
        </main>
    </div>
</body>
</html>
