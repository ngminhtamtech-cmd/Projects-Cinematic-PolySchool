<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Quản lý tài khoản Manager - CineBook System</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
    <style>
        .overview-system-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 16px;
            margin-bottom: 20px;
        }

        .manager-form-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 14px;
            margin-bottom: 16px;
        }

        .manager-form-grid label {
            display: block;
            font-size: 12px;
            font-weight: 600;
            color: #475569;
            margin-bottom: 6px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .manager-form-grid input,
        .manager-form-grid select {
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

        .manager-form-grid input:focus,
        .manager-form-grid select:focus {
            border-color: #6D28D9;
            outline: none;
            box-shadow: 0 0 0 3px rgba(109, 40, 217, 0.1);
        }
    </style>
</head>
<body class="admin-body">
    <div class="dashboard">
        <%@ include file="/WEB-INF/views/system/sidebar.jspf" %>
        <main class="dashboard-main">
            <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
            <div class="dashboard-content">
                <%@ include file="/WEB-INF/views/shared/flash.jspf" %>
                
                <!-- PAGE HEADER -->
                <div class="portal-head" style="margin-bottom:20px;">
                    <div>
                        <h1 style="font-size:22px;font-weight:600;color:#1A1A21;margin:0 0 4px;">Quản lý tài khoản Manager</h1>
                        <p class="muted" style="font-size:13px;color:#6E6E7A;margin:0;">
                            Tạo mới, khóa hoặc mở khóa tài khoản người vận hành hệ thống rạp (Manager).
                        </p>
                    </div>
                </div>

                <!-- OVERVIEW METRICS -->
                <c:set var="activeManagerCount" value="0" />
                <c:set var="lockedManagerCount" value="0" />
                <c:forEach var="managerMetric" items="${managers}">
                    <c:choose>
                        <c:when test="${managerMetric.deleted}"><c:set var="lockedManagerCount" value="${lockedManagerCount + 1}" /></c:when>
                        <c:otherwise><c:set var="activeManagerCount" value="${activeManagerCount + 1}" /></c:otherwise>
                    </c:choose>
                </c:forEach>
                
                <section class="overview-system-grid" aria-label="Số liệu tài khoản Manager">
                    <div class="overview-metric-card">
                        <span>Tổng Manager</span>
                        <strong>${fn:length(managers)}</strong>
                        <small>Tài khoản thực tế</small>
                    </div>
                    <div class="overview-metric-card">
                        <span>Đang hoạt động</span>
                        <strong style="color:#059669;">${activeManagerCount}</strong>
                        <small>Có thể đăng nhập</small>
                    </div>
                    <div class="overview-metric-card">
                        <span>Đã khóa</span>
                        <strong style="color:#dc2626;">${lockedManagerCount}</strong>
                        <small>Không thể đăng nhập</small>
                    </div>
                    <div class="overview-metric-card">
                        <span>Cụm rạp khả dụng</span>
                        <strong style="color:#6D28D9;">${fn:length(cinemas)}</strong>
                        <small>Dữ liệu phân công hiện có</small>
                    </div>
                </section>

                <!-- SECTION 1: FORM TẠO TÀI KHOẢN MANAGER MỚI (TOP FULL-WIDTH PANEL) -->
                <article class="panel-card" style="margin-bottom: 24px; padding: 20px;">
                    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;padding-bottom:10px;border-bottom:1px solid #E8E8EE;">
                        <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="8.5" cy="7" r="4"/><line x1="20" y1="8" x2="20" y2="14"/><line x1="17" y1="11" x2="23" y2="11"/></svg>
                            <span>Tạo tài khoản Manager mới</span>
                        </h2>
                        <span style="font-size:12px;color:#8A8A96;">Cấp tài khoản vận hành cho người quản lý cụm rạp</span>
                    </div>

                    <form method="post" action="${pageContext.request.contextPath}/system/managers">
                        <cb:csrf/>
                        <div class="manager-form-grid">
                            <div>
                                <label title="Tên đăng nhập (Username)"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Tên đăng nhập (Username)</label>
                                <input type="text" name="username" required placeholder="manager_ha_dong">
                            </div>
                            <div>
                                <label title="Họ và tên"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Họ và tên</label>
                                <input type="text" name="fullName" required placeholder="Nguyễn Văn A">
                            </div>
                            <div>
                                <label title="Địa chỉ Email"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Địa chỉ Email</label>
                                <input type="email" name="email" required placeholder="m_hadong@cinebook.local">
                            </div>
                            <div>
                                <label title="Mật khẩu tạm thời"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Mật khẩu tạm thời</label>
                                <input type="password" name="password" minlength="10" required placeholder="Tối thiểu 10 ký tự">
                            </div>
                            <div>
                                <label title="Số điện thoại">Số điện thoại</label>
                                <input type="text" name="phone" placeholder="09xxxxxxxx">
                            </div>
                            <div>
                                <label title="Địa chỉ công tác">Địa chỉ công tác</label>
                                <input type="text" name="address" placeholder="CineBook Hà Đông">
                            </div>
                            <div>
                                <label title="Cụm rạp phụ trách"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Cụm rạp phụ trách</label>
                                <select name="cinemaId" required>
                                    <option value="">-- Chọn cụm rạp --</option>
                                    <c:forEach var="cinema" items="${cinemas}">
                                        <option value="${fn:escapeXml(cinema.id)}">${fn:escapeXml(cinema.name)}</option>
                                    </c:forEach>
                                </select>
                            </div>
                        </div>

                        <div style="display:flex;justify-content:flex-end;">
                            <button type="submit" class="btn-primary">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px;"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="8.5" cy="7" r="4"/><line x1="20" y1="8" x2="20" y2="14"/><line x1="17" y1="11" x2="23" y2="11"/></svg>
                                <span>Tạo tài khoản Manager</span>
                            </button>
                        </div>
                    </form>
                </article>

                <!-- SECTION 2: DANH SÁCH MANAGERS (MAIN DATA TABLE) -->
                <section class="panel-card" style="padding: 20px;">
                    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
                        <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                            <span>Danh sách Manager (${fn:length(managers)})</span>
                        </h2>
                    </div>

                    <div class="table-responsive">
                        <table class="table-data">
                            <thead>
                                <tr>
                                    <th scope="col">Họ tên &amp; Username</th>
                                    <th scope="col">Email</th>
                                    <th scope="col">Số điện thoại</th>
                                    <th scope="col">Cụm rạp phụ trách</th>
                                    <th scope="col">Trạng thái</th>
                                    <th scope="col" style="text-align:right;">Hành động</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="manager" items="${managers}">
                                    <tr>
                                        <td>
                                            <div style="display:flex;align-items:center;gap:10px;">
                                                <div style="width:36px;height:36px;border-radius:50%;background:#F3E8FF;color:#6D28D9;font-weight:700;font-size:14px;display:grid;place-items:center;">
                                                    ${fn:substring(manager.fullName, 0, 1)}
                                                </div>
                                                <div>
                                                    <strong style="font-size:14px;color:#1A1A21;display:block;">${fn:escapeXml(manager.fullName)}</strong>
                                                    <span style="font-family:monospace;font-size:12px;color:#64748B;">@${fn:escapeXml(manager.username)}</span>
                                                </div>
                                            </div>
                                        </td>
                                        <td>
                                            <span style="font-size:13px;color:#334155;">${fn:escapeXml(manager.email)}</span>
                                        </td>
                                        <td>
                                            <span style="font-size:13px;color:#475569;">${fn:escapeXml(empty manager.phone ? '—' : manager.phone)}</span>
                                        </td>
                                        <td>
                                            <strong style="font-size:13px;color:#0f172a;">${fn:escapeXml(manager.cinemaName)}</strong>
                                        </td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${manager.deleted}">
                                                    <span class="badge-status status-danger">
                                                        <span class="status-dot"></span>Đã khóa
                                                    </span>
                                                </c:when>
                                                <c:otherwise>
                                                    <span class="badge-status status-success">
                                                        <span class="status-dot"></span>Đang hoạt động
                                                    </span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td style="text-align:right;">
                                            <form method="post" action="${pageContext.request.contextPath}/system/managers" style="display:flex;gap:6px;justify-content:flex-end;margin:0 0 6px;">
                                                <cb:csrf/>
                                                <input type="hidden" name="id" value="${fn:escapeXml(manager.id)}">
                                                <input type="hidden" name="action" value="reassign">
                                                <select name="cinemaId" required aria-label="Rạp phụ trách mới" style="height:30px;max-width:180px;font-size:12px;padding:3px 8px;">
                                                    <c:forEach var="cinema" items="${cinemas}">
                                                        <option value="${fn:escapeXml(cinema.id)}" ${cinema.id eq manager.cinemaId ? 'selected' : ''}>${fn:escapeXml(cinema.name)}</option>
                                                    </c:forEach>
                                                </select>
                                                <button class="button secondary" type="submit" style="padding:4px 10px;font-size:12px;">Đổi rạp</button>
                                            </form>
                                            <form method="post" action="${pageContext.request.contextPath}/system/managers" style="display:inline;">
                                                <cb:csrf/>
                                                <input type="hidden" name="id" value="${fn:escapeXml(manager.id)}">
                                                <c:choose>
                                                    <c:when test="${manager.deleted}">
                                                        <button class="button secondary" type="submit" name="action" value="unlock" style="padding:4px 12px;font-size:12px;">Mở khóa</button>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <button class="button danger" type="submit" name="action" value="lock" style="padding:4px 12px;font-size:12px;" onclick="return confirm('Khóa tài khoản manager này?')">Khóa tài khoản</button>
                                                    </c:otherwise>
                                                </c:choose>
                                            </form>
                                        </td>
                                    </tr>
                                </c:forEach>
                                <c:if test="${empty managers}">
                                    <tr>
                                        <td colspan="6" style="text-align:center;padding:30px;color:#8A8A96;">Chưa có tài khoản manager nào được tạo.</td>
                                    </tr>
                                </c:if>
                            </tbody>
                        </table>
                    </div>
                </section>
            </div>
        </main>
    </div>
</body>
</html>
