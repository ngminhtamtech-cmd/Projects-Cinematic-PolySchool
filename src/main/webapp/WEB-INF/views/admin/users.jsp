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
    <title>Quản lý thành viên & Hạng loyalty - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
    <style>
        .tier-badge {
            display: inline-block;
            padding: 4px 10px;
            border-radius: 12px;
            font-size: 0.78rem;
            font-weight: 700;
        }
        .tier-bronze { background: #fef3c7; color: #92400e; border: 1px solid #fde68a; }
        .tier-silver { background: #f1f5f9; color: #475569; border: 1px solid #cbd5e1; }
        .tier-diamond { background: #e0f2fe; color: #0369a1; border: 1px solid #bae6fd; }
        .tier-emerald { background: #dcfce7; color: #15803d; border: 1px solid #86efac; }
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
                        <h1 style="font-size:22px;font-weight:600;color:#1A1A21;margin:0 0 4px;">Quản lý thành viên & Hạng Loyalty</h1>
                        <p class="muted" style="font-size:13px;color:#6E6E7A;margin:0;">Xem danh sách member, theo dõi số điểm tích lũy và nâng/hạ hạng trực tiếp cho tài khoản.</p>
                    </div>
                </div>

                <!-- SECTION 1: FORM TẠO MEMBER MỚI (ĐẶT Ở PHÍA TRÊN) -->
                <article class="panel-card" style="margin-bottom: 24px; padding: 20px;">
                    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;padding-bottom:10px;border-bottom:1px solid #E8E8EE;">
                        <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="8.5" cy="7" r="4"/><line x1="20" y1="8" x2="20" y2="14"/><line x1="17" y1="11" x2="23" y2="11"/></svg>
                            <span>Tạo tài khoản thành viên mới</span>
                        </h2>
                        <span style="font-size:12px;color:#8A8A96;">Điền đầy đủ thông tin bên dưới để tạo tài khoản</span>
                    </div>

                    <form method="post" action="${pageContext.request.contextPath}/admin/users">
                        <cb:csrf/>
                        <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(280px, 1fr));gap:14px;margin-bottom:16px;">
                            <div>
                                <label><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Username</label>
                                <input type="text" name="username" required placeholder="Nhập username..." style="width:100%;">
                            </div>
                            <div>
                                <label><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Họ và tên</label>
                                <input type="text" name="fullName" required placeholder="Nhập họ tên đầy đủ..." style="width:100%;">
                            </div>
                            <div>
                                <label><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Địa chỉ Email</label>
                                <input type="email" name="email" required placeholder="example@domain.com" style="width:100%;">
                            </div>
                            <div>
                                <label><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Mật khẩu tạm</label>
                                <input type="password" name="password" minlength="10" required placeholder="Tối thiểu 10 ký tự, đủ 3/4 nhóm..." style="width:100%;">
                            </div>
                            <div>
                                <label>Số điện thoại</label>
                                <input type="text" name="phone" placeholder="0901 xxx xxx" style="width:100%;">
                            </div>
                            <div>
                                <label>Địa chỉ thường trú</label>
                                <input type="text" name="address" placeholder="Nhập địa chỉ thành viên..." style="width:100%;">
                            </div>
                        </div>

                        <div style="display:flex;justify-content:flex-end;">
                            <button type="submit" class="btn-primary">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px;"><path d="M12 5v14M5 12h14"/></svg>
                                <span>Tạo member mới</span>
                            </button>
                        </div>
                    </form>
                </article>

                <!-- SECTION 2: DANH SÁCH THÀNH VIÊN (MỞ RỘNG 100% Ở PHÍA DƯỚI) -->
                <section class="panel-card" style="padding: 20px;">
                    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
                        <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M3 20a5 5 0 0 1 10 0M8 5a3.5 3.5 0 1 1 0 7 3.5 3.5 0 0 1 0-7M16 12a3 3 0 1 0 0-6M21 20a4.5 4.5 0 0 0-4-4.4"/></svg>
                            <span>Danh sách thành viên (${fn:escapeXml(users.size())} tài khoản)</span>
                        </h2>
                    </div>

                    <div class="table-responsive">
                        <table class="table-data">
                            <thead>
                                <tr>
                                    <th scope="col" style="width:60px;">Mã</th>
                                    <th scope="col">Thành viên</th>
                                    <th scope="col">Email / Username</th>
                                    <th scope="col">Hạng Thành Viên</th>
                                    <th scope="col">Điểm / Chi Tiêu</th>
                                    <th scope="col">Trạng thái</th>
                                    <th scope="col">Nâng / Đổi Hạng</th>
                                    <th scope="col" style="text-align:right;">Hành động</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="user" items="${users}">
                                    <tr>
                                        <td style="font-family:monospace;font-weight:600;color:#8A8A96;">#${fn:escapeXml(user.id)}</td>
                                        <td>
                                            <strong style="font-weight:600;color:#1A1A21;display:block;">${fn:escapeXml(user.fullName)}</strong>
                                            <c:if test="${not empty user.phone}">
                                                <div style="font-size:12px;color:#6E6E7A;margin-top:2px;">${fn:escapeXml(empty user.phone ? '—' : user.phone)}</div>
                                            </c:if>
                                        </td>
                                        <td>
                                            <div style="color:#1A1A21;">${fn:escapeXml(user.email)}</div>
                                            <div style="font-family:monospace;font-size:11px;color:#8A8A96;margin-top:2px;">@${fn:escapeXml(user.username)}</div>
                                        </td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${user.membershipTier eq 'EMERALD'}">
                                                    <span class="tier-badge tier-emerald">Lục Bảo</span>
                                                </c:when>
                                                <c:when test="${user.membershipTier eq 'DIAMOND'}">
                                                    <span class="tier-badge tier-diamond">Kim Cương</span>
                                                </c:when>
                                                <c:when test="${user.membershipTier eq 'SILVER'}">
                                                        <span class="tier-badge tier-silver">Bạc</span>
                                                </c:when>
                                                <c:otherwise>
                                                        <span class="tier-badge tier-bronze">Đồng</span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td>
                                    <div style="font-weight:600;color:#6D28D9;">${cbf:whole(user.loyaltyPoints)} điểm</div>
                                    <div style="font-size:12px;color:#6E6E7A;margin-top:2px;">Tổng chi tiêu: <strong style="color:#0f172a;">${cbf:whole(not empty user.totalSpent ? user.totalSpent : 0)} vnđ</strong></div>
                                        </td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${user.deleted}">
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
                                        <td>
                                            <form method="post" action="${pageContext.request.contextPath}/admin/users" style="display:flex;gap:6px;align-items:center;margin:0;">
                                                <cb:csrf/>
                                                <input type="hidden" name="id" value="${fn:escapeXml(user.id)}">
                                                <input type="hidden" name="action" value="changeTier">
                                                <select name="membershipTier" style="padding:4px 8px;font-size:12px;border-radius:6px;border:1px solid #E4E4EA;background:#FAFAFC;">
                                                        <option value="BRONZE" ${fn:escapeXml(user.membershipTier eq 'BRONZE' ? 'selected' : '')}>Đồng</option>
                                                        <option value="SILVER" ${fn:escapeXml(user.membershipTier eq 'SILVER' ? 'selected' : '')}>Bạc</option>
                                                    <option value="DIAMOND" ${fn:escapeXml(user.membershipTier eq 'DIAMOND' ? 'selected' : '')}>Kim Cương</option>
                                                    <option value="EMERALD" ${fn:escapeXml(user.membershipTier eq 'EMERALD' ? 'selected' : '')}>Lục Bảo</option>
                                                </select>
                                                <button type="submit" class="btn-secondary" style="padding:4px 10px!important;font-size:12px!important;">Lưu</button>
                                            </form>
                                        </td>
                                        <td style="text-align:right;">
                                            <form method="post" action="${pageContext.request.contextPath}/admin/users" style="display:inline;margin:0;">
                                                <cb:csrf/>
                                                <input type="hidden" name="id" value="${fn:escapeXml(user.id)}">
                                                <c:choose>
                                                    <c:when test="${user.deleted}">
                                                        <button class="btn-secondary" type="submit" name="action" value="unlock" style="padding:4px 10px!important;font-size:12px!important;">Mở khóa</button>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <button class="btn-danger-outline" type="submit" name="action" value="lock" onclick="return confirm('Bạn có chắc chắn muốn khóa tài khoản member này không?')" style="padding:4px 10px!important;font-size:12px!important;">Khóa</button>
                                                    </c:otherwise>
                                                </c:choose>
                                            </form>
                                        </td>
                                    </tr>
                                </c:forEach>
                                <c:if test="${empty users}">
                                    <tr>
                                        <td colspan="8" style="text-align:center;padding:36px;color:#8A8A96;">Chưa có thành viên nào trong hệ thống.</td>
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
