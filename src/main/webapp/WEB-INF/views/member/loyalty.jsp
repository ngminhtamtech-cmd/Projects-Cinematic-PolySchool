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
    <title>Thành Viên & Loyalty Points - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <style>
        /* ==========================================================================
           MINIMALIST COMPACT LOYALTY DASHBOARD
           ========================================================================== */
        .loyalty-hero-compact {
            background: linear-gradient(135deg, #0F172A 0%, #1E293B 100%);
            border-radius: 20px;
            padding: 24px 30px;
            color: #FFFFFF;
            margin-top: 16px;
            margin-bottom: 20px;
            box-shadow: 0 10px 30px rgba(15, 23, 42, 0.15);
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 24px;
        }

        .loyalty-user-info {
            display: flex;
            flex-direction: column;
            gap: 8px;
            flex: 1;
            min-width: 280px;
        }

        .loyalty-eyebrow {
            font-size: 11px;
            font-weight: 800;
            text-transform: uppercase;
            letter-spacing: 1px;
            color: #38BDF8;
        }

        .loyalty-user-name {
            font-size: 24px;
            font-weight: 800;
            margin: 0;
            color: #FFFFFF;
            letter-spacing: -0.01em;
            display: flex;
            align-items: center;
            gap: 10px;
            flex-wrap: wrap;
        }

        .tier-badge-pill {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 4px 14px;
            border-radius: 20px;
            font-size: 12.5px;
            font-weight: 800;
            width: fit-content;
        }
        .badge-bronze { background: #FEF3C7; color: #92400E; }
        .badge-silver { background: #F1F5F9; color: #334155; }
        .badge-diamond { background: #E0F2FE; color: #0369A1; }
        .badge-emerald { background: #DCFCE7; color: #15803D; }

        /* Progress Bar to Next Tier */
        .tier-progress-box {
            margin-top: 4px;
            max-width: 460px;
            width: 100%;
        }

        .tier-progress-bar-bg {
            height: 8px;
            background: rgba(255, 255, 255, 0.15);
            border-radius: 10px;
            overflow: hidden;
            margin-bottom: 6px;
        }

        .tier-progress-fill {
            height: 100%;
            background: linear-gradient(90deg, #F59E0B, #FACC15);
            border-radius: 10px;
            transition: width 0.4s ease;
        }

        .tier-progress-text {
            font-size: 12px;
            color: #94A3B8;
            font-weight: 500;
            display: flex;
            justify-content: space-between;
        }

        /* Right Score Widget */
        .loyalty-score-widget {
            background: rgba(255, 255, 255, 0.08);
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255, 255, 255, 0.15);
            border-radius: 16px;
            padding: 16px 24px;
            text-align: center;
            min-width: 200px;
            flex-shrink: 0;
        }

        .score-label {
            font-size: 11px;
            font-weight: 700;
            color: #CBD5E1;
            letter-spacing: 0.5px;
            text-transform: uppercase;
            margin-bottom: 2px;
        }

        .score-value {
            font-size: 30px;
            font-weight: 900;
            color: #FACC15;
            line-height: 1.1;
        }

        .score-sub {
            font-size: 12px;
            color: #94A3B8;
            margin-top: 4px;
        }

        /* MINIMALIST BORDER BOX TAB CONTAINER (SEGMENTED CONTROL) */
        .loyalty-tabs-box {
            background: #FFFFFF;
            border: 1px solid #E2E8F0;
            border-radius: 18px;
            padding: 6px;
            margin-bottom: 20px;
            box-shadow: 0 2px 10px rgba(15, 23, 42, 0.03);
        }

        .loyalty-tabs-segmented {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 6px;
            background: #F8FAFC;
            border-radius: 14px;
            padding: 4px;
            border: 1px solid #F1F5F9;
        }

        .loyalty-tab-pill {
            padding: 10px 14px;
            font-size: 13.5px;
            font-weight: 700;
            color: #64748B;
            background: transparent;
            border: 1px solid transparent;
            border-radius: 10px;
            cursor: pointer;
            transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 7px;
            white-space: nowrap;
            outline: none;
        }

        .loyalty-tab-pill:hover {
            color: #0F172A;
            background: rgba(255, 255, 255, 0.7);
        }

        .loyalty-tab-pill.active {
            background: #FFFFFF;
            color: #EA580C;
            border-color: #E2E8F0;
            box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06);
            font-weight: 800;
        }

        .pill-count {
            background: #F1F5F9;
            color: #64748B;
            font-size: 11px;
            font-weight: 800;
            padding: 2px 7px;
            border-radius: 10px;
            transition: all 0.2s ease;
        }

        .loyalty-tab-pill.active .pill-count {
            background: #FFF7ED;
            color: #EA580C;
        }

        /* Tab Content Panes */
        .loyalty-tab-pane {
            display: none;
        }

        .loyalty-tab-pane.active {
            display: block;
            animation: fadeInTab 0.2s ease forwards;
        }

        @keyframes fadeInTab {
            from { opacity: 0; transform: translateY(4px); }
            to { opacity: 1; transform: translateY(0); }
        }

        /* Compact Tier Cards Grid */
        .compact-tier-grid {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 16px;
        }

        .compact-tier-card {
            background: #FFFFFF;
            border: 1px solid #E2E8F0;
            border-radius: 16px;
            padding: 20px;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
            position: relative;
            transition: all 0.2s ease;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
        }

        .compact-tier-card:hover {
            border-color: #CBD5E1;
            box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
        }

        .compact-tier-card.is-current {
            border: 2px solid #EA580C;
            background: #FFF7ED;
        }

        .current-tier-tag {
            position: absolute;
            top: 12px;
            right: 12px;
            background: #EA580C;
            color: #FFFFFF;
            font-size: 10px;
            font-weight: 800;
            padding: 2px 8px;
            border-radius: 10px;
            text-transform: uppercase;
        }

        .tier-card-title {
            font-size: 16px;
            font-weight: 800;
            color: #0F172A;
            margin-bottom: 4px;
            display: flex;
            align-items: center;
            gap: 6px;
        }

        .tier-card-points {
            font-size: 12px;
            font-weight: 700;
            color: #64748B;
            margin-bottom: 12px;
        }

        .tier-card-perks {
            list-style: none;
            padding: 0;
            margin: 0;
            font-size: 12.5px;
            color: #475569;
            display: flex;
            flex-direction: column;
            gap: 6px;
        }

        .tier-card-perks li {
            display: flex;
            align-items: flex-start;
            gap: 6px;
            line-height: 1.3;
        }

        .tier-card-perks li::before {
            content: '✓';
            color: #16A34A;
            font-weight: 800;
        }

        /* Compact Voucher Exchange Grid */
        .compact-voucher-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
            gap: 16px;
        }

        .compact-voucher-card {
            background: #FFFFFF;
            border: 1px dashed #CBD5E1;
            border-radius: 14px;
            padding: 16px;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            gap: 14px;
            transition: all 0.2s ease;
        }

        .compact-voucher-card:hover {
            border-color: #EA580C;
            box-shadow: 0 6px 18px rgba(234, 88, 12, 0.1);
        }

        .v-code {
            font-size: 15px;
            font-weight: 800;
            color: #EA580C;
            letter-spacing: 0.5px;
        }

        .v-desc {
            font-size: 13px;
            color: #475569;
            margin: 4px 0 0 0;
        }

        .v-bottom-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            border-top: 1px solid #F1F5F9;
            padding-top: 10px;
        }

        .v-pts {
            font-size: 14px;
            font-weight: 800;
            color: #D97706;
        }

        .btn-redeem {
            background: #EA580C;
            color: #FFFFFF;
            border: none;
            padding: 6px 14px;
            border-radius: 8px;
            font-size: 12.5px;
            font-weight: 700;
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .btn-redeem:hover:not(:disabled) {
            background: #C2410C;
            box-shadow: 0 4px 12px rgba(234, 88, 12, 0.25);
        }

        .btn-redeem:disabled {
            background: #E2E8F0;
            color: #94A3B8;
            cursor: not-allowed;
        }

        /* Clean Tables Container */
        .loyalty-table-container {
            background: #FFFFFF;
            border: 1px solid #E2E8F0;
            border-radius: 16px;
            padding: 16px;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
        }

        @media (max-width: 900px) {
            .compact-tier-grid {
                grid-template-columns: repeat(2, 1fr);
            }
            .loyalty-hero-compact {
                flex-direction: column;
                align-items: stretch;
            }
            .loyalty-tabs-segmented {
                grid-template-columns: repeat(2, 1fr);
            }
        }
    </style>
</head>
<body class="public-page">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="container public-main">
        <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

        <!-- Calculate Progress for Tier -->
        <c:set var="userPts" value="${not empty user.loyaltyPoints ? user.loyaltyPoints : 0}" />
        <c:choose>
            <c:when test="${user.membershipTier eq 'EMERALD'}">
                <c:set var="tierNameLabel" value="Lục Bảo (Emerald)" />
                <c:set var="badgeClass" value="badge-emerald" />
                <c:set var="badgeIcon" value="💚" />
                <c:set var="targetPts" value="3000" />
                <c:set var="ptsProgress" value="100" />
                <c:set var="nextTierText" value="Đã đạt Hạng Cao Nhất" />
            </c:when>
            <c:when test="${user.membershipTier eq 'DIAMOND'}">
                <c:set var="tierNameLabel" value="Kim Cương (Diamond)" />
                <c:set var="badgeClass" value="badge-diamond" />
                <c:set var="badgeIcon" value="💎" />
                <c:set var="targetPts" value="3000" />
                <c:set var="ptsNeeded" value="${3000 - userPts}" />
                <c:set var="ptsProgress" value="${(userPts / 3000.0) * 100.0}" />
                <c:set var="nextTierText" value="Còn ${ptsNeeded} điểm để lên Lục Bảo" />
            </c:when>
            <c:when test="${user.membershipTier eq 'SILVER'}">
                <c:set var="tierNameLabel" value="Bạc (Silver)" />
                <c:set var="badgeClass" value="badge-silver" />
                <c:set var="badgeIcon" value="🥈" />
                <c:set var="targetPts" value="1500" />
                <c:set var="ptsNeeded" value="${1500 - userPts}" />
                <c:set var="ptsProgress" value="${(userPts / 1500.0) * 100.0}" />
                <c:set var="nextTierText" value="Còn ${ptsNeeded} điểm để lên Kim Cương" />
            </c:when>
            <c:otherwise>
                <c:set var="tierNameLabel" value="Đồng (Bronze)" />
                <c:set var="badgeClass" value="badge-bronze" />
                <c:set var="badgeIcon" value="🥉" />
                <c:set var="targetPts" value="500" />
                <c:set var="ptsNeeded" value="${500 - userPts}" />
                <c:set var="ptsProgress" value="${(userPts / 500.0) * 100.0}" />
                <c:set var="nextTierText" value="Còn ${ptsNeeded} điểm để lên Hạng Bạc" />
            </c:otherwise>
        </c:choose>

        <!-- COMPACT HERO BANNER -->
        <div class="loyalty-hero-compact">
            <div class="loyalty-user-info">
                <span class="loyalty-eyebrow">CineBook Membership</span>
                <h1 class="loyalty-user-name">
                    Xin chào, ${fn:escapeXml(user.fullName)}!
                    <span class="tier-badge-pill ${badgeClass}">${badgeIcon} Hạng ${tierNameLabel}</span>
                </h1>

                <div class="tier-progress-box">
                    <div class="tier-progress-bar-bg">
                        <div class="tier-progress-fill" style="width: ${ptsProgress > 100 ? 100 : (ptsProgress < 0 ? 0 : ptsProgress)}%;"></div>
                    </div>
                    <div class="tier-progress-text">
                        <span>Tiến trình thăng hạng</span>
                        <span>${nextTierText}</span>
                    </div>
                </div>
            </div>

            <div class="loyalty-score-widget">
                <div class="score-label">Điểm Loyalty Khả Dụng</div>
                <div class="score-value">⭐ ${cbf:whole(user.loyaltyPoints)}</div>
                <div class="score-sub">Tổng chi tiêu: <strong>${cbf:whole(not empty user.totalSpent ? user.totalSpent : 0)} VNĐ</strong></div>
            </div>
        </div>

        <!-- MINIMALIST BORDER BOX TAB CONTAINER (SEGMENTED CONTROL) -->
        <div class="loyalty-tabs-box">
            <div class="loyalty-tabs-segmented">
                <button id="redeemBtn" class="loyalty-tab-pill active" onclick="switchLoyaltyTab('redeem')">
                    <span class="pill-icon">🎁</span>
                    <span>Kho Đổi Voucher</span>
                    <span class="pill-count">${fn:escapeXml(redeemablePromos.size())}</span>
                </button>

                <button id="tiersBtn" class="loyalty-tab-pill" onclick="switchLoyaltyTab('tiers')">
                    <span class="pill-icon">🏆</span>
                    <span>Cấp Hạng & Đặc Quyền</span>
                </button>

                <button id="myVouchersBtn" class="loyalty-tab-pill" onclick="switchLoyaltyTab('myVouchers')">
                    <span class="pill-icon">🎟️</span>
                    <span>Voucher Của Tôi</span>
                    <span class="pill-count">${fn:escapeXml(myVouchers.size())}</span>
                </button>

                <button id="historyBtn" class="loyalty-tab-pill" onclick="switchLoyaltyTab('history')">
                    <span class="pill-icon">📜</span>
                    <span>Lịch Sử Biến Động</span>
                    <span class="pill-count">${fn:escapeXml(pointHistory.size())}</span>
                </button>
            </div>
        </div>

        <!-- TAB 1: KHO ĐỔI VOUCHER (DEFAULT) -->
        <div id="redeemTab" class="loyalty-tab-pane active">
            <div class="compact-voucher-grid">
                <c:forEach var="promo" items="${redeemablePromos}">
                    <div class="compact-voucher-card">
                        <div>
                            <div class="v-code">🎁 ${fn:escapeXml(promo.code)}</div>
                            <p class="v-desc">${fn:escapeXml(promo.description)}</p>
                            <div style="font-size: 12px; color: #64748b; margin-top: 6px;">
                                Giảm <strong>${cbf:decimal(promo.discountPercent)}%</strong> (Tối đa <strong style="color: #0f172a;">${cbf:whole(promo.maxDiscount)} VNĐ</strong>)
                            </div>
                        </div>
                        <div class="v-bottom-row">
                            <span class="v-pts">⭐ ${fn:escapeXml(promo.pointsRequired)} điểm</span>
                            <form method="post" action="${pageContext.request.contextPath}/member/loyalty" style="margin: 0;">
                                <cb:csrf/>
                                <input type="hidden" name="action" value="redeem">
                                <input type="hidden" name="promotionId" value="${fn:escapeXml(promo.id)}">
                                <input type="hidden" name="redemptionKey" value="${fn:escapeXml(redemptionKey)}">
                                <button type="submit" class="btn-redeem" ${fn:escapeXml(user.loyaltyPoints < promo.pointsRequired ? 'disabled title="Bạn chưa đủ điểm"' : '')}>
                                    Đổi Voucher
                                </button>
                            </form>
                        </div>
                    </div>
                </c:forEach>
                <c:if test="${empty redeemablePromos}">
                    <div style="grid-column: 1/-1; padding: 40px; text-align: center; color: #94a3b8; background: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0;">
                        <div style="font-size: 32px; margin-bottom: 6px;">🎁</div>
                        Hiện chưa có voucher đổi điểm nào trong kho quà.
                    </div>
                </c:if>
            </div>
        </div>

        <!-- TAB 2: QUYỀN LỢI & CẤP HẠNG THÀNH VIÊN -->
        <div id="tiersTab" class="loyalty-tab-pane">
            <div class="compact-tier-grid">
                <div class="compact-tier-card ${fn:escapeXml(user.membershipTier eq 'BRONZE' ? 'is-current' : '')}">
                    <c:if test="${user.membershipTier eq 'BRONZE'}"><span class="current-tier-tag">Đang sở hữu</span></c:if>
                    <div>
                        <div class="tier-card-title">🥉 Hạng Đồng</div>
                        <div class="tier-card-points">0 - 499 điểm</div>
                        <ul class="tier-card-perks">
                            <li>Tích 1% điểm mỗi đơn hàng</li>
                            <li>Đổi điểm lấy voucher cơ bản</li>
                            <li>Quà tặng sinh nhật thành viên</li>
                        </ul>
                    </div>
                </div>

                <div class="compact-tier-card ${fn:escapeXml(user.membershipTier eq 'SILVER' ? 'is-current' : '')}">
                    <c:if test="${user.membershipTier eq 'SILVER'}"><span class="current-tier-tag">Đang sở hữu</span></c:if>
                    <div>
                        <div class="tier-card-title" style="color:#034EA2;">🥈 Hạng Bạc</div>
                        <div class="tier-card-points">500 - 1,499 điểm</div>
                        <ul class="tier-card-perks">
                            <li>Tích 3% điểm mỗi đơn hàng</li>
                            <li>Áp dụng mã giảm giá Hạng Bạc</li>
                            <li>Ưu tiên đặt chỗ phim bom tấn</li>
                        </ul>
                    </div>
                </div>

                <div class="compact-tier-card ${fn:escapeXml(user.membershipTier eq 'DIAMOND' ? 'is-current' : '')}">
                    <c:if test="${user.membershipTier eq 'DIAMOND'}"><span class="current-tier-tag">Đang sở hữu</span></c:if>
                    <div>
                        <div class="tier-card-title" style="color:#0369A1;">💎 Hạng Kim Cương</div>
                        <div class="tier-card-points">1,500 - 2,999 điểm</div>
                        <ul class="tier-card-perks">
                            <li>Tích 5% điểm mỗi đơn hàng</li>
                            <li>Voucher đặc quyền KIMCUONG10</li>
                            <li>Miễn phí nâng size bắp nước</li>
                        </ul>
                    </div>
                </div>

                <div class="compact-tier-card ${fn:escapeXml(user.membershipTier eq 'EMERALD' ? 'is-current' : '')}">
                    <c:if test="${user.membershipTier eq 'EMERALD'}"><span class="current-tier-tag">Đang sở hữu</span></c:if>
                    <div>
                        <div class="tier-card-title" style="color:#15803D;">💚 Hạng Lục Bảo</div>
                        <div class="tier-card-points">3,000+ điểm</div>
                        <ul class="tier-card-perks">
                            <li>Tích 8% điểm mỗi đơn hàng</li>
                            <li>Voucher đặc quyền LỤC BẢO</li>
                            <li>Vé mời sự kiện công chiếu đặc biệt</li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>

        <!-- TAB 3: VOUCHER CÁ NHÂN ĐÃ SỞ HỮU -->
        <div id="myVouchersTab" class="loyalty-tab-pane">
            <div class="loyalty-table-container">
                <div class="table-wrap">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Mã Voucher</th>
                                <th>Mô tả</th>
                                <th>Mức Giảm</th>
                                <th>Trạng Thái</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="uv" items="${myVouchers}">
                                <tr>
                                    <td class="mono" style="font-weight: 800; color: #EA580C;">${fn:escapeXml(uv.code)}</td>
                                    <td>${fn:escapeXml(uv.promotionDescription)}</td>
                                    <td>Giảm ${cbf:decimal(uv.discountPercent)}% (Tối đa <strong style="color: #0f172a;">${cbf:whole(uv.maxDiscount)} VNĐ</strong>)</td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${uv.used}">
                                                <span class="status-pill danger">Đã sử dụng</span>
                                            </c:when>
                                            <c:otherwise>
                                                <span class="status-pill success">Khả dụng</span>
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty myVouchers}">
                                <tr>
                                    <td colspan="4" class="text-center" style="color: #94a3b8; padding: 32px 16px;">Bạn chưa sở hữu voucher cá nhân nào. Hãy dùng điểm Loyalty đổi voucher trên Kho quà!</td>
                                </tr>
                            </c:if>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

        <!-- TAB 4: LỊCH SỬ BIẾN ĐỘNG ĐIỂM -->
        <div id="historyTab" class="loyalty-tab-pane">
            <div class="loyalty-table-container">
                <div class="table-wrap">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Thời gian</th>
                                <th>Loại giao dịch</th>
                                <th>Số điểm</th>
                                <th>Mô tả</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="pt" items="${pointHistory}">
                                <c:set var="rawTs" value="${pt.createdAt}" />
                                <c:choose>
                                    <c:when test="${fn:contains(rawTs, 'T')}">
                                        <c:set var="datePart" value="${fn:substringBefore(rawTs, 'T')}" />
                                        <c:set var="timePart" value="${fn:substringAfter(rawTs, 'T')}" />
                                        <c:set var="timeClean" value="${fn:substring(timePart, 0, 5)}" />
                                        <c:set var="ymd" value="${fn:split(datePart, '-')}" />
                                        <c:set var="formattedTs" value="${timeClean} ${ymd[2]}/${ymd[1]}/${ymd[0]}" />
                                    </c:when>
                                    <c:otherwise>
                                        <c:set var="formattedTs" value="${rawTs}" />
                                    </c:otherwise>
                                </c:choose>
                                <tr>
                                    <td style="font-weight: 600; color: #475569; white-space: nowrap;">${fn:escapeXml(formattedTs)}</td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${pt.points > 0}">
                                                <span style="color: #16a34a; font-weight: 700;">+ Tích Điểm</span>
                                            </c:when>
                                            <c:otherwise>
                                                <span style="color: #dc2626; font-weight: 700;">- Dùng Điểm</span>
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                    <td style="font-weight: 800; color: ${fn:escapeXml(pt.points > 0 ? '#16a34a' : '#dc2626')}; white-space: nowrap;">
                                        ${fn:escapeXml(pt.points > 0 ? '+' : '')}${fn:escapeXml(pt.points)} điểm
                                    </td>
                                    <td class="pt-desc-text" data-raw-desc="${fn:escapeXml(pt.description)}">${fn:escapeXml(pt.description)}</td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty pointHistory}">
                                <tr>
                                    <td colspan="4" class="text-center" style="color: #94a3b8; padding: 32px 16px;">Chưa có lịch sử biến động điểm.</td>
                                </tr>
                            </c:if>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

    </main>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>
    <script src="${pageContext.request.contextPath}/assets/js/main.js"></script>
    <script>
        function switchLoyaltyTab(tabId) {
            var panes = document.querySelectorAll('.loyalty-tab-pane');
            var btns = document.querySelectorAll('.loyalty-tab-pill');

            panes.forEach(function(p) { p.classList.remove('active'); });
            btns.forEach(function(b) { b.classList.remove('active'); });

            var targetPane = document.getElementById(tabId + 'Tab');
            var targetBtn = document.getElementById(tabId + 'Btn');

            if (targetPane) targetPane.classList.add('active');
            if (targetBtn) targetBtn.classList.add('active');
        }

        function formatMoneyInDescriptions() {
            var descEls = document.querySelectorAll('.pt-desc-text');
            descEls.forEach(function(el) {
                var text = el.getAttribute('data-raw-desc') || el.textContent;
                if (!text) return;
                var formatted = text.replace(/(\d+)(?:\.\d+)?\s*đ/gi, function(match, numStr) {
                    var num = parseInt(numStr, 10);
                    if (!isNaN(num)) {
                        return num.toLocaleString('en-US') + ' VNĐ';
                    }
                    return match;
                });
                el.textContent = formatted;
            });
        }

        document.addEventListener('DOMContentLoaded', function() {
            formatMoneyInDescriptions();
        });
    </script>
</body>
</html>
