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
    <title>System Panel - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
</head>
<body class="admin-body">
    <div class="dashboard">
        <%@ include file="/WEB-INF/views/system/sidebar.jspf" %>
        <main class="dashboard-main">
            <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
            <div class="dashboard-content">
                <%@ include file="/WEB-INF/views/shared/flash.jspf" %>
                
                <div class="portal-head">
                    <div>
                        <h1>Bảng điều khiển hệ thống</h1>
                        <p class="muted">Không gian riêng cho Admin tối cao: cấu hình hệ thống, quản trị tài khoản manager, audit logs và sao lưu SQL Server.</p>
                    </div>
                    <form method="post" action="${pageContext.request.contextPath}/system/backup">
                        <cb:csrf/>
                        <button type="submit" class="danger">Backup Database ngay</button>
                    </form>
                </div>

                <section class="portal-kpis">
                    <a class="stat-card" href="${pageContext.request.contextPath}/system/managers">
                        <div class="stat-value">${fn:escapeXml(managerCount)}</div>
                        <div class="stat-label">Tài khoản Manager</div>
                        <div class="stat-desc">Quản lý và cấp quyền tài khoản vận hành</div>
                    </a>
                    <a class="stat-card" href="${pageContext.request.contextPath}/system/config">
                        <div class="stat-value">${fn:escapeXml(settingCount)}</div>
                        <div class="stat-label">System settings</div>
                        <div class="stat-desc">Cấu hình tham số và thông tin hệ thống</div>
                    </a>
                    <a class="stat-card" href="${pageContext.request.contextPath}/system/audit-logs">
                        <div class="stat-value">${fn:escapeXml(auditCount)}</div>
                        <div class="stat-label">Dòng audit log</div>
                        <div class="stat-desc">Giám sát các hoạt động nhạy cảm</div>
                    </a>
                </section>

                <section class="portal-grid" style="grid-template-columns: 1fr; margin-top: 24px;">
                    <article class="panel">
                        <h2>Ghi chú bảo trì database</h2>
                        <p class="muted" style="line-height: 1.6;">
                            Chức năng Sao lưu cơ sở dữ liệu (Backup) được gọi trực tiếp bằng lệnh Transact-SQL của SQL Server. 
                            Nếu quá trình sao lưu thất bại, vui lòng kiểm tra quyền ghi tệp tin của tài khoản dịch vụ SQL Server trên thư mục máy chủ.
                        </p>
                    </article>
                </section>
            </div>
        </main>
    </div>
</body>
</html>
