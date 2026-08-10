<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="historyView" value="${param.view eq 'history'}" />
<!DOCTYPE html>
<html lang="vi" data-staff-theme="dark">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${historyView ? 'Lịch sử check-in' : 'Soát vé & Thu tiền'} — Quầy vé CineBook</title>
    <script>
        (function () {
            try {
                var saved = localStorage.getItem('cinebook_staff_theme');
                var theme = saved === 'light' || saved === 'dark'
                    ? saved
                    : (window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark');
                document.documentElement.setAttribute('data-staff-theme', theme);
            } catch (ignore) {}
        })();
    </script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <style>
        :root {
            --staff-bg:#0b1628; --staff-rail:#07111f; --staff-surface:#111f35; --staff-surface-soft:#152640;
            --staff-input:#091525; --staff-line:#263a59; --staff-text:#f3f6fc; --staff-muted:#9aacc8;
            --staff-subtle:#7086a8; --staff-primary:#ff7a00; --staff-primary-hover:#e86f00; --staff-on-primary:#fff;
            --staff-success:#39d082; --staff-success-soft:#103728; --staff-warning:#f2c94c; --staff-warning-soft:#3a310f;
            --staff-danger:#ff6b78; --staff-danger-soft:#3b1821; --staff-shadow:0 12px 32px rgba(0,0,0,.16);
        }
        html[data-staff-theme="light"] {
            --staff-bg:#f4f6fa; --staff-rail:#fff; --staff-surface:#fff; --staff-surface-soft:#f7f8fb;
            --staff-input:#fff; --staff-line:#dfe4ec; --staff-text:#182033; --staff-muted:#5d6b82;
            --staff-subtle:#718097; --staff-primary:#d95f00; --staff-primary-hover:#bd5200; --staff-on-primary:#fff;
            --staff-success:#087a45; --staff-success-soft:#e9f8f0; --staff-warning:#8a6500; --staff-warning-soft:#fff7d9;
            --staff-danger:#c72d3e; --staff-danger-soft:#fff0f2; --staff-shadow:0 8px 24px rgba(31,45,72,.07);
        }
        * { box-sizing:border-box; }
        html { scroll-behavior:smooth; }
        .staff-body { margin:0; min-width:0; overflow-x:hidden; background:var(--staff-bg); color:var(--staff-text); font-family:Inter,system-ui,-apple-system,"Segoe UI",sans-serif; transition:background-color .2s ease,color .2s ease; }
        .staff-shell { display:flex; min-height:100vh; }
        .staff-rail { position:sticky; top:0; width:224px; height:100vh; flex:0 0 224px; display:flex; flex-direction:column; padding:18px 14px; background:var(--staff-rail); border-right:1px solid var(--staff-line); }
        .staff-rail-brand { display:flex; align-items:center; gap:10px; padding:0 6px 17px; border-bottom:1px solid var(--staff-line); }
        .staff-rail-logo { display:grid; place-items:center; width:38px; height:38px; border-radius:10px; color:var(--staff-on-primary); background:var(--staff-primary); }
        .staff-rail-logo svg { width:21px; height:21px; }
        .staff-rail-brand strong { display:block; color:var(--staff-text); font-size:15px; }
        .staff-rail-sub { color:var(--staff-muted); font-size:11px; }
        .staff-rail-nav { display:flex; flex:1; flex-direction:column; gap:4px; margin-top:14px; }
        .staff-rail-nav a { display:flex; align-items:center; gap:10px; min-height:44px; padding:9px 11px; border-radius:9px; color:var(--staff-muted); text-decoration:none; font-size:13px; font-weight:650; transition:background-color .18s ease,color .18s ease; }
        .staff-rail-nav a svg { width:18px; height:18px; flex:none; }
        .staff-rail-nav a:hover { color:var(--staff-text); background:var(--staff-surface-soft); }
        .staff-rail-nav a.is-active { color:var(--staff-on-primary); background:var(--staff-primary); }
        .staff-rail-nav a:focus-visible, .staff-rail-foot a:focus-visible { outline:3px solid color-mix(in srgb,var(--staff-primary) 38%,transparent); outline-offset:2px; }
        .staff-rail-foot { display:flex; flex-direction:column; gap:7px; padding-top:14px; border-top:1px solid var(--staff-line); }
        .staff-rail-user strong { display:block; color:var(--staff-text); font-size:12px; }
        .staff-rail-user span { display:block; color:var(--staff-subtle); font-size:10px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
        .staff-rail-back,.staff-rail-logout { min-height:32px; display:flex; align-items:center; color:var(--staff-muted); font-size:11px; text-decoration:none; }
        .staff-rail-back:hover,.staff-rail-logout:hover { color:var(--staff-primary); }
        .staff-rail-logout { color:var(--staff-danger); }

        .staff-main { flex:1; min-width:0; padding:22px 26px 40px; }
        .staff-content { width:min(100%,1440px); margin:0 auto; }
        .staff-head { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; margin-bottom:16px; }
        .staff-head h1 { margin:0 0 4px; color:var(--staff-text); font-size:clamp(22px,2.4vw,30px); letter-spacing:-.025em; }
        .staff-head p { margin:0; color:var(--staff-muted); font-size:13px; }
        .theme-toggle { display:inline-flex; align-items:center; gap:8px; min-height:44px; padding:0 13px; border:1px solid var(--staff-line); border-radius:10px; color:var(--staff-text); background:var(--staff-surface); font:650 12px/1 inherit; cursor:pointer; box-shadow:var(--staff-shadow); }
        .theme-toggle svg { width:17px; height:17px; }
        .theme-toggle:hover { border-color:var(--staff-primary); }
        .theme-toggle:focus-visible,.btn:focus-visible,.manual-entry input:focus-visible { outline:3px solid color-mix(in srgb,var(--staff-primary) 34%,transparent); outline-offset:2px; }
        .theme-icon-sun { display:none; }
        html[data-staff-theme="light"] .theme-icon-sun { display:block; }
        html[data-staff-theme="light"] .theme-icon-moon { display:none; }

        .shift-summary { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; margin-bottom:14px; }
        .shift-card { display:flex; align-items:center; justify-content:space-between; min-height:92px; padding:16px 18px; border:1px solid var(--staff-line); border-radius:12px; background:var(--staff-surface); box-shadow:var(--staff-shadow); }
        .shift-card span { display:block; margin-bottom:6px; color:var(--staff-muted); font-size:11px; font-weight:700; text-transform:uppercase; letter-spacing:.06em; }
        .shift-card strong { display:block; color:var(--staff-text); font-size:25px; line-height:1; font-variant-numeric:tabular-nums; }
        .shift-card small { display:block; margin-top:7px; color:var(--staff-subtle); font-size:10px; }
        .shift-card svg { width:24px; height:24px; color:var(--staff-primary); }

        .staff-grid { display:grid; grid-template-columns:minmax(310px,390px) minmax(0,1fr); gap:14px; align-items:start; }
        .panel { min-width:0; padding:16px; border:1px solid var(--staff-line); border-radius:12px; background:var(--staff-surface); box-shadow:var(--staff-shadow); }
        .panel-title { display:flex; align-items:center; gap:8px; margin:0 0 13px; color:var(--staff-muted); font-size:12px; font-weight:750; text-transform:uppercase; letter-spacing:.06em; }
        .panel-title svg { width:17px; height:17px; color:var(--staff-primary); }
        .scanner-frame { position:relative; display:grid; place-items:center; width:100%; aspect-ratio:16/10; overflow:hidden; border:1.5px dashed var(--staff-line); border-radius:10px; background:var(--staff-input); }
        .scanner-frame video { display:none; width:100%; height:100%; object-fit:cover; }
        body.scanner-live .scanner-frame video { display:block; }
        body.scanner-live .scanner-frame { border-style:solid; border-color:var(--staff-success); }
        .scanner-idle-art { display:grid; justify-items:center; gap:8px; color:var(--staff-subtle); font-size:11px; }
        .scanner-idle-art svg { width:44px; height:44px; }
        body.scanner-live .scanner-idle-art { display:none; }
        .scanner-status { margin:10px 0 0; padding:9px 11px; border-radius:8px; color:var(--staff-muted); background:var(--staff-surface-soft); font-size:12px; line-height:1.45; }
        .scanner-status--live,.scanner-status--ok { color:var(--staff-success); background:var(--staff-success-soft); }
        .scanner-status--error { color:var(--staff-danger); background:var(--staff-danger-soft); }
        .btn-row { display:flex; gap:8px; margin-top:10px; }
        .btn { min-height:44px; border:1px solid transparent; border-radius:9px; padding:10px 14px; font:700 13px/1.2 inherit; cursor:pointer; transition:background-color .18s ease,border-color .18s ease,opacity .18s ease; }
        .btn:disabled { opacity:.45; cursor:not-allowed; }
        .btn-primary { color:var(--staff-on-primary); background:var(--staff-primary); }
        .btn-primary:hover:not(:disabled) { background:var(--staff-primary-hover); }
        .btn-ghost { color:var(--staff-text); border-color:var(--staff-line); background:var(--staff-surface-soft); }
        .btn-ghost:hover:not(:disabled) { border-color:var(--staff-primary); }
        .btn-success { color:#062c1a; background:#52dc92; }
        .btn-success:hover { background:#35c97d; }
        .btn-warn { color:#302500; background:#f3cb4f; }
        .btn-warn:hover { background:#e2ba37; }
        .btn-lg { width:100%; min-height:50px; }
        .manual-entry { margin-top:14px; padding-top:14px; border-top:1px solid var(--staff-line); }
        .manual-entry label { display:block; margin-bottom:6px; color:var(--staff-muted); font-size:11px; font-weight:650; }
        .manual-entry .input-row { display:flex; gap:8px; }
        .manual-entry input { flex:1; min-width:0; min-height:44px; padding:10px 12px; border:1px solid var(--staff-line); border-radius:9px; color:var(--staff-text); background:var(--staff-input); font:500 14px/1 ui-monospace,"Cascadia Mono",Consolas,monospace; }
        .window-hint { margin:12px 0 0; color:var(--staff-subtle); font-size:10px; line-height:1.5; }

        .verdict { display:flex; align-items:flex-start; gap:11px; margin-bottom:12px; padding:13px 14px; border:1px solid; border-radius:10px; }
        .verdict-ok { color:var(--staff-success); border-color:var(--staff-success); background:var(--staff-success-soft); }
        .verdict-warn { color:var(--staff-warning); border-color:var(--staff-warning); background:var(--staff-warning-soft); }
        .verdict-danger { color:var(--staff-danger); border-color:var(--staff-danger); background:var(--staff-danger-soft); }
        .verdict-icon { flex:none; width:23px; height:23px; }
        .verdict-text strong { display:block; margin-bottom:3px; color:var(--staff-text); font-size:15px; }
        .verdict-text span { display:block; color:var(--staff-text); font-size:12px; line-height:1.45; }
        .verdict-code { margin-top:5px; color:var(--staff-subtle) !important; font:10px/1.3 ui-monospace,Consolas,monospace !important; }
        .ticket-products { display:grid; grid-template-columns:1.35fr .65fr; gap:10px; }
        .product-card { min-width:0; padding:14px; border:1px solid var(--staff-line); border-radius:10px; background:var(--staff-surface-soft); }
        .product-card-head { display:flex; align-items:center; gap:8px; margin-bottom:11px; color:var(--staff-text); font-size:12px; font-weight:750; }
        .product-card-head svg { width:18px; height:18px; color:var(--staff-primary); }
        .product-primary { margin:0 0 10px; color:var(--staff-text); font-size:17px; line-height:1.3; }
        .product-details { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:9px; margin:0; }
        .product-details div { min-width:0; }
        .product-details dt,.order-fact dt { margin-bottom:3px; color:var(--staff-subtle); font-size:9px; font-weight:700; text-transform:uppercase; letter-spacing:.05em; }
        .product-details dd,.order-fact dd { margin:0; color:var(--staff-text); font-size:12px; font-weight:650; overflow-wrap:anywhere; }
        .combo-value { margin:0; color:var(--staff-text); font-size:13px; line-height:1.55; }
        .combo-empty { color:var(--staff-subtle); }
        .order-facts { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:1px; margin:10px 0 0; overflow:hidden; border:1px solid var(--staff-line); border-radius:10px; background:var(--staff-line); }
        .order-fact { min-width:0; padding:10px 12px; background:var(--staff-surface); }
        .order-fact dd.money { color:var(--staff-primary); font-size:14px; font-variant-numeric:tabular-nums; }
        .order-fact dd.code { font-family:ui-monospace,Consolas,monospace; }
        .empty-state { display:grid; justify-items:center; gap:8px; padding:58px 20px; color:var(--staff-subtle); text-align:center; font-size:12px; }
        .empty-state svg { width:42px; height:42px; }
        .action-form { margin-top:12px; }
        .flash { margin-bottom:14px; padding:11px 14px; border:1px solid; border-radius:9px; font-size:12px; font-weight:650; }
        .flash.success { color:var(--staff-success); border-color:var(--staff-success); background:var(--staff-success-soft); }
        .flash.error { color:var(--staff-danger); border-color:var(--staff-danger); background:var(--staff-danger-soft); }

        .history-panel { margin-top:0; padding:0; overflow:hidden; }
        .history-head { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:15px 16px; border-bottom:1px solid var(--staff-line); }
        .history-head .panel-title { margin:0; }
        .history-scope { color:var(--staff-subtle); font-size:10px; }
        .history-table-wrap { overflow-x:auto; }
        .history-table { width:100%; min-width:760px; border-collapse:collapse; }
        .history-table th { padding:9px 14px; color:var(--staff-subtle); background:var(--staff-surface-soft); font-size:9px; text-align:left; text-transform:uppercase; letter-spacing:.05em; }
        .history-table td { padding:11px 14px; border-top:1px solid var(--staff-line); color:var(--staff-text); font-size:11px; vertical-align:top; }
        .history-table tbody tr:first-child td { border-top:0; }
        .history-table .money { color:var(--staff-primary); font-weight:750; font-variant-numeric:tabular-nums; }
        .history-table .ticket { font-family:ui-monospace,Consolas,monospace; }
        .history-empty { padding:30px 16px; color:var(--staff-subtle); text-align:center; font-size:12px; }

        @media (max-width:1080px) {
            .staff-grid { grid-template-columns:minmax(290px,350px) minmax(0,1fr); }
            .ticket-products { grid-template-columns:1fr; }
            .order-facts { grid-template-columns:repeat(2,minmax(0,1fr)); }
        }
        @media (max-width:840px) {
            .staff-shell { display:block; }
            .staff-rail { position:static; width:100%; height:auto; padding:12px 14px; border-right:0; border-bottom:1px solid var(--staff-line); }
            .staff-rail-brand { padding:0 0 10px; }
            .staff-rail-nav { flex-direction:row; overflow-x:auto; margin-top:10px; }
            .staff-rail-nav a { flex:0 0 auto; }
            .staff-rail-foot { display:none; }
            .staff-main { padding:18px 16px 32px; }
            .staff-grid { grid-template-columns:1fr; }
            .scanner-frame { max-height:280px; }
        }
        @media (max-width:560px) {
            .staff-head { align-items:stretch; flex-direction:column; }
            .theme-toggle { align-self:flex-start; }
            .shift-summary { grid-template-columns:1fr; }
            .shift-card { min-height:80px; }
            .manual-entry .input-row,.btn-row { flex-direction:column; }
            .order-facts,.product-details { grid-template-columns:1fr; }
            .history-head { align-items:flex-start; flex-direction:column; }
        }
        @media (prefers-reduced-motion:reduce) {
            html { scroll-behavior:auto; }
            *,*::before,*::after { transition-duration:.01ms !important; animation-duration:.01ms !important; animation-iteration-count:1 !important; }
        }
    </style>
</head>
<body class="staff-body">
<div class="staff-shell">
    <%@ include file="/WEB-INF/views/staff/sidebar.jspf" %>

    <main class="staff-main">
        <div class="staff-content">
            <header class="staff-head">
                <div>
                    <c:choose>
                        <c:when test="${historyView}">
                            <h1>Lịch sử check-in</h1>
                            <p>Theo dõi lượt check-in và tổng tiền của ngày hôm nay.</p>
                        </c:when>
                        <c:otherwise>
                            <h1>Soát vé &amp; Thu tiền</h1>
                            <p>Quét QR hoặc nhập mã vé để xử lý khách tại quầy.</p>
                        </c:otherwise>
                    </c:choose>
                </div>
                <button type="button" class="theme-toggle" id="staffThemeToggle" aria-label="Chuyển chế độ sáng tối">
                    <svg class="theme-icon-moon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8Z"/></svg>
                    <svg class="theme-icon-sun" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>
                    <span id="staffThemeLabel">Chế độ sáng</span>
                </button>
            </header>

            <c:if test="${historyView}">
                <section class="shift-summary" aria-label="Tổng quan check-in hôm nay">
                    <article class="shift-card">
                        <div><span>Tổng tiền hôm nay</span><strong id="todayRevenue">0 đ</strong><small>Từ vé đã check-in trên thiết bị này</small></div>
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M16 8h-6a2 2 0 0 0 0 4h4a2 2 0 0 1 0 4H8m4-10v12"/></svg>
                    </article>
                    <article class="shift-card">
                        <div><span>Đơn vé hôm nay</span><strong id="todayOrders">0</strong><small>Cập nhật sau mỗi check-in thành công</small></div>
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M3 9a2 2 0 0 0 0 4v4a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-4a2 2 0 0 0 0-4V7a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v2Z"/><path d="M13 5v2m0 3v2m0 3v4"/></svg>
                    </article>
                </section>
            </c:if>

            <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

            <c:if test="${not historyView}">
            <div class="staff-grid">
                <section class="panel" aria-labelledby="scannerTitle">
                    <h2 class="panel-title" id="scannerTitle">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M3 9V5a2 2 0 0 1 2-2h4m6 0h4a2 2 0 0 1 2 2v4m0 6v4a2 2 0 0 1-2 2h-4M9 21H5a2 2 0 0 1-2-2v-4"/><path d="M8 8h8v8H8z"/></svg>
                        Quét mã QR
                    </h2>
                    <div class="scanner-frame">
                        <span class="scanner-idle-art" aria-hidden="true">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M5 7h3l1-2h6l1 2h3a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2Z"/><circle cx="12" cy="13" r="3"/></svg>
                            Đưa mã QR vào khung hình
                        </span>
                        <video id="qrVideo" muted playsinline></video>
                    </div>
                    <div class="btn-row">
                        <button type="button" id="btnStartScan" class="btn btn-primary" style="flex:1;">Bật camera</button>
                        <button type="button" id="btnStopScan" class="btn btn-ghost" style="flex:1;" hidden>Tắt camera</button>
                    </div>
                    <p id="scannerStatus" class="scanner-status scanner-status--idle" aria-live="polite">Đang khởi tạo…</p>
                    <form id="scanForm" method="post" action="${pageContext.request.contextPath}/staff/checkin">
                        <cb:csrf/><input type="hidden" name="action" value="lookup"><input type="hidden" name="ticketCode" id="scannedTicketCode">
                    </form>
                    <div class="manual-entry">
                        <form method="post" action="${pageContext.request.contextPath}/staff/checkin">
                            <cb:csrf/><input type="hidden" name="action" value="lookup">
                            <label for="manualTicketCode">Nhập mã vé thủ công</label>
                            <div class="input-row">
                                <input type="text" id="manualTicketCode" name="ticketCode" value="<c:out value='${ticketCode}'/>" placeholder="CB1753..." autocomplete="off" spellcheck="false" required>
                                <button type="submit" class="btn btn-ghost">Tra cứu</button>
                            </div>
                        </form>
                    </div>
                    <p class="window-hint">Cổng check-in mở 60 phút trước và đóng 30 phút sau giờ bắt đầu.</p>
                </section>

                <section class="panel" aria-labelledby="lookupTitle">
                    <h2 class="panel-title" id="lookupTitle">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
                        Kết quả tra cứu
                    </h2>
                    <c:choose>
                        <c:when test="${empty lookup}">
                            <div class="empty-state">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M3 9a2 2 0 0 0 0 4v4a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-4a2 2 0 0 0 0-4V7a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v2Z"/><path d="M13 5v2m0 3v2m0 3v4"/></svg>
                                <span>Chưa có vé được tra cứu.<br>Quét QR hoặc nhập mã vé để bắt đầu.</span>
                            </div>
                        </c:when>
                        <c:otherwise>
                            <div class="verdict verdict-${fn:escapeXml(lookup.tone)}">
                                <c:choose>
                                    <c:when test="${lookup.tone eq 'ok'}"><svg class="verdict-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="m8 12 2.5 2.5L16 9"/></svg></c:when>
                                    <c:when test="${lookup.tone eq 'warn'}"><svg class="verdict-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M10.3 3.6 2.4 17.2A2 2 0 0 0 4.1 20h15.8a2 2 0 0 0 1.7-2.8L13.7 3.6a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4m0 3h.01"/></svg></c:when>
                                    <c:otherwise><svg class="verdict-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="m9 9 6 6m0-6-6 6"/></svg></c:otherwise>
                                </c:choose>
                                <div class="verdict-text">
                                    <strong><c:choose><c:when test="${lookup.verdictName eq 'READY'}">Vé hợp lệ</c:when><c:when test="${lookup.verdictName eq 'CHECKED_IN'}">Đã check-in xong</c:when><c:when test="${lookup.verdictName eq 'NEEDS_PAYMENT'}">Cần thu tiền</c:when><c:when test="${lookup.verdictName eq 'ALREADY_USED'}">Vé đã sử dụng</c:when><c:when test="${lookup.verdictName eq 'CANCELLED'}">Đơn đã hủy</c:when><c:when test="${lookup.verdictName eq 'TOO_EARLY'}">Chưa đến giờ</c:when><c:when test="${lookup.verdictName eq 'TOO_LATE'}">Quá giờ check-in</c:when><c:when test="${lookup.verdictName eq 'UNPAID'}">Chưa thanh toán</c:when><c:when test="${lookup.verdictName eq 'NOT_CONFIRMED'}">Đơn chưa hoàn tất</c:when><c:when test="${lookup.verdictName eq 'NOT_FOUND'}">Không tìm thấy vé</c:when><c:otherwise>Không xác định</c:otherwise></c:choose></strong>
                                    <span><c:out value="${lookup.message}"/></span>
                                    <span class="verdict-code">Mã trạng thái: ${fn:escapeXml(lookup.verdictName)}</span>
                                </div>
                            </div>

                            <c:if test="${lookup.found}">
                                <div class="ticket-products">
                                    <article class="product-card">
                                        <div class="product-card-head"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M4 6h16v12H4z"/><path d="m10 9 5 3-5 3V9Z"/></svg>Phim &amp; vé</div>
                                        <h3 class="product-primary"><c:out value="${empty lookup.order.filmTitle ? 'Chưa có tên phim' : lookup.order.filmTitle}"/></h3>
                                        <dl class="product-details">
                                            <div><dt>Suất chiếu</dt><dd><c:out value="${empty lookup.order.startTimeDisplay ? '—' : lookup.order.startTimeDisplay}"/></dd></div>
                                            <div><dt>Rạp / Phòng</dt><dd><c:out value="${lookup.order.cinemaName}"/> · <c:out value="${lookup.order.roomName}"/></dd></div>
                                            <div><dt>Ghế</dt><dd><c:out value="${lookup.order.seatsDisplay}"/></dd></div>
                                            <div><dt>Khách hàng</dt><dd><c:out value="${lookup.order.userFullName}"/></dd></div>
                                        </dl>
                                    </article>
                                    <article class="product-card">
                                        <div class="product-card-head"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M6 8h12l-1 12H7L6 8Z"/><path d="M8 8 7 4h3l2 4m4-4v4M5 12h14"/></svg>Combo</div>
                                        <p class="combo-value ${empty lookup.order.combosDisplay ? 'combo-empty' : ''}"><c:out value="${empty lookup.order.combosDisplay ? 'Không có combo trong đơn này.' : lookup.order.combosDisplay}"/></p>
                                    </article>
                                </div>
                                <dl class="order-facts">
                                    <div class="order-fact"><dt>Thành tiền</dt><dd class="money">${fn:escapeXml(lookup.order.totalAmountDisplay)} đ</dd></div>
                                    <div class="order-fact"><dt>Phương thức</dt><dd><c:choose><c:when test="${lookup.order.paymentMethod eq 'counter'}">Tại quầy</c:when><c:otherwise><c:out value="${lookup.order.paymentMethod}"/></c:otherwise></c:choose></dd></div>
                                    <div class="order-fact"><dt>Thanh toán</dt><dd>${lookup.order.paymentStatus eq 'paid' ? 'Đã trả' : 'Chưa trả'}</dd></div>
                                    <div class="order-fact"><dt>Mã vé</dt><dd class="code"><c:out value="${lookup.order.ticketCode}"/></dd></div>
                                </dl>
                                <c:if test="${lookup.canCheckIn}">
                                    <form method="post" action="${pageContext.request.contextPath}/staff/checkin" class="action-form">
                                        <cb:csrf/><input type="hidden" name="action" value="redeem"><input type="hidden" name="ticketCode" value="<c:out value='${lookup.order.ticketCode}'/>">
                                        <button type="submit" class="btn btn-success btn-lg">Check-in và cho khách vào</button>
                                    </form>
                                </c:if>
                                <c:if test="${lookup.canCollectPayment}">
                                    <form method="post" action="${pageContext.request.contextPath}/staff/checkin" class="action-form" onsubmit="return confirm('Xác nhận đã nhận đủ tiền mặt từ khách?');">
                                        <cb:csrf/><input type="hidden" name="action" value="markPaid"><input type="hidden" name="orderId" value="${fn:escapeXml(lookup.order.id)}"><input type="hidden" name="ticketCode" value="<c:out value='${lookup.order.ticketCode}'/>">
                                        <button type="submit" class="btn btn-warn btn-lg">Thu ${fn:escapeXml(lookup.order.totalAmountDisplay)} đ tiền mặt</button>
                                    </form>
                                </c:if>
                                <div id="staffLookupSnapshot" hidden
                                     data-verdict="${fn:escapeXml(lookup.verdictName)}"
                                     data-ticket="${fn:escapeXml(lookup.order.ticketCode)}"
                                     data-film="${fn:escapeXml(lookup.order.filmTitle)}"
                                     data-showtime="${fn:escapeXml(lookup.order.startTimeDisplay)}"
                                     data-room="${fn:escapeXml(lookup.order.cinemaName)} · ${fn:escapeXml(lookup.order.roomName)}"
                                     data-seats="${fn:escapeXml(lookup.order.seatsDisplay)}"
                                     data-combos="${fn:escapeXml(empty lookup.order.combosDisplay ? 'Không có' : lookup.order.combosDisplay)}"
                                     data-customer="${fn:escapeXml(lookup.order.userFullName)}"
                                     data-total="${fn:escapeXml(lookup.order.totalAmount)}"></div>
                            </c:if>
                        </c:otherwise>
                    </c:choose>
                </section>
            </div>
            </c:if>

            <c:if test="${historyView}">
            <section class="panel history-panel" id="checkinHistory" aria-labelledby="historyTitle">
                <div class="history-head">
                    <h2 class="panel-title" id="historyTitle"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5m4-1v5l3 2"/></svg>Lịch sử check-in hôm nay</h2>
                    <span class="history-scope">Lưu trên trình duyệt của quầy này · tối đa 100 lượt gần nhất</span>
                </div>
                <div class="history-table-wrap" id="historyTableWrap" hidden>
                    <table class="history-table">
                        <thead><tr><th>Thời gian</th><th>Mã vé</th><th>Phim</th><th>Ghế</th><th>Combo</th><th>Khách hàng</th><th>Thành tiền</th></tr></thead>
                        <tbody id="historyRows"></tbody>
                    </table>
                </div>
                <div class="history-empty" id="historyEmpty">Chưa có lượt check-in thành công nào trong hôm nay.</div>
            </section>
            </c:if>
        </div>
    </main>
</div>

<c:if test="${not historyView}">
    <script src="${pageContext.request.contextPath}/assets/js/qr-codec.js?v=1.4.0-bc40c8a1" charset="UTF-8"></script>
    <script src="${pageContext.request.contextPath}/assets/js/qr-scanner.js?v=1.0.6-9672d985" charset="UTF-8"></script>
</c:if>
<script>
    (function () {
        var themeToggle = document.getElementById('staffThemeToggle');
        var themeLabel = document.getElementById('staffThemeLabel');
        function currentTheme() { return document.documentElement.getAttribute('data-staff-theme') === 'light' ? 'light' : 'dark'; }
        function syncThemeLabel() {
            var next = currentTheme() === 'dark' ? 'sáng' : 'tối';
            themeLabel.textContent = 'Chế độ ' + next;
            themeToggle.setAttribute('aria-label', 'Chuyển sang chế độ ' + next);
        }
        syncThemeLabel();
        themeToggle.addEventListener('click', function () {
            var next = currentTheme() === 'dark' ? 'light' : 'dark';
            document.documentElement.setAttribute('data-staff-theme', next);
            try { localStorage.setItem('cinebook_staff_theme', next); } catch (ignore) {}
            syncThemeLabel();
        });

        var historyKey = 'cinebook_staff_checkins_v1:${fn:escapeXml(sessionScope.currentUser.id)}';
        function localDateKey(date) {
            var year = date.getFullYear();
            var month = String(date.getMonth() + 1).padStart(2, '0');
            var day = String(date.getDate()).padStart(2, '0');
            return year + '-' + month + '-' + day;
        }
        function loadHistory() {
            try {
                var value = JSON.parse(localStorage.getItem(historyKey) || '[]');
                return Array.isArray(value) ? value : [];
            } catch (ignore) { return []; }
        }
        function saveHistory(items) {
            try { localStorage.setItem(historyKey, JSON.stringify(items.slice(0, 100))); } catch (ignore) {}
        }
        function escapeHtml(value) {
            return String(value || '').replace(/[&<>'"]/g, function (char) {
                return {'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char];
            });
        }
        function recordSuccessfulCheckIn(items) {
            var snapshot = document.getElementById('staffLookupSnapshot');
            if (!snapshot || snapshot.dataset.verdict !== 'CHECKED_IN' || !snapshot.dataset.ticket) return items;
            var now = new Date();
            var id = localDateKey(now) + '|' + snapshot.dataset.ticket;
            if (items.some(function (item) { return item.id === id; })) return items;
            items.unshift({
                id:id, date:localDateKey(now), timestamp:now.toISOString(), ticket:snapshot.dataset.ticket,
                film:snapshot.dataset.film || '—', showtime:snapshot.dataset.showtime || '—', room:snapshot.dataset.room || '—',
                seats:snapshot.dataset.seats || '—', combos:snapshot.dataset.combos || 'Không có', customer:snapshot.dataset.customer || '—',
                total:Number(snapshot.dataset.total || 0)
            });
            saveHistory(items);
            return items;
        }
        function renderHistory(items) {
            var today = localDateKey(new Date());
            var todayItems = items.filter(function (item) { return item.date === today; });
            var total = todayItems.reduce(function (sum, item) { return sum + (Number(item.total) || 0); }, 0);
            var orderTotal = document.getElementById('todayOrders');
            var revenueTotal = document.getElementById('todayRevenue');
            if (orderTotal) orderTotal.textContent = String(todayItems.length);
            if (revenueTotal) revenueTotal.textContent = new Intl.NumberFormat('vi-VN').format(total) + ' đ';
            var rows = document.getElementById('historyRows');
            var wrap = document.getElementById('historyTableWrap');
            var empty = document.getElementById('historyEmpty');
            if (!rows || !wrap || !empty) return;
            if (!todayItems.length) {
                wrap.hidden = true; empty.hidden = false; rows.innerHTML = ''; return;
            }
            wrap.hidden = false; empty.hidden = true;
            rows.innerHTML = todayItems.map(function (item) {
                var time = new Date(item.timestamp).toLocaleTimeString('vi-VN', {hour:'2-digit',minute:'2-digit',second:'2-digit'});
                return '<tr><td>' + escapeHtml(time) + '</td><td class="ticket">' + escapeHtml(item.ticket) + '</td>'
                    + '<td><strong>' + escapeHtml(item.film) + '</strong><br><span class="history-scope">' + escapeHtml(item.showtime) + ' · ' + escapeHtml(item.room) + '</span></td>'
                    + '<td>' + escapeHtml(item.seats) + '</td><td>' + escapeHtml(item.combos) + '</td><td>' + escapeHtml(item.customer) + '</td>'
                    + '<td class="money">' + new Intl.NumberFormat('vi-VN').format(Number(item.total) || 0) + ' đ</td></tr>';
            }).join('');
        }
        renderHistory(recordSuccessfulCheckIn(loadHistory()));
    })();
</script>
</body>
</html>
