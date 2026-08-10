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
    <title>Quản lý khuyến mãi & Voucher - CineBook</title>
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
        
        .promo-form-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
            gap: 14px;
            margin-bottom: 16px;
        }

        .promo-form-grid label {
            display: block;
            font-size: 12px;
            font-weight: 600;
            color: #475569;
            margin-bottom: 6px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .promo-form-grid input,
        .promo-form-grid select {
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
        
        .promo-form-grid input:focus,
        .promo-form-grid select:focus {
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
                        <h1 style="font-size:22px;font-weight:600;color:#1A1A21;margin:0 0 4px;">Quản lý khuyến mãi & Voucher theo hạng</h1>
                        <p class="muted" style="font-size:13px;color:#6E6E7A;margin:0;">Tạo voucher dùng chung, voucher giới hạn cho hạng (Đồng, Bạc, Kim Cương, Lục Bảo) và kho voucher đổi điểm.</p>
                    </div>
                </div>

                <!-- SECTION 1: FORM TẠO MÃ KHUYẾN MÃI MỚI (TOP FULL-WIDTH PANEL) -->
                <article class="panel-card" style="margin-bottom: 24px; padding: 20px;">
                    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;padding-bottom:10px;border-bottom:1px solid #E8E8EE;">
                        <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>
                            <span>Tạo mã khuyến mãi mới</span>
                        </h2>
                        <span style="font-size:12px;color:#8A8A96;">Điền đầy đủ thông tin bên dưới để tạo mã voucher hoặc mã đổi điểm</span>
                    </div>

                    <form method="post" action="${pageContext.request.contextPath}/admin/promotions">
                        <cb:csrf/>
                        <div class="promo-form-grid">
                            <div>
                                <label title="Mã Code (Voucher Code)"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Mã Code (Voucher Code)</label>
                                <input type="text" name="code" required placeholder="Ví dụ: CINEBOOK50, KIMCUONG10...">
                            </div>
                            <div>
                                <label title="Loại Voucher">Loại Voucher</label>
                                <select name="voucherType">
                                    <option value="PUBLIC">Công khai dùng chung</option>
                                    <option value="TIER_RESTRICTED">Dành riêng cho hạng cụ thể</option>
                                    <option value="REDEEMABLE">Quy đổi bằng điểm Loyalty</option>
                                </select>
                            </div>
                            <div>
                                <label title="Hạng Thành Viên Áp Dụng">Hạng Thành Viên Áp Dụng</label>
                                <select name="targetTier">
                                    <option value="ALL">Tất cả hạng</option>
                                    <option value="BRONZE">Hạng Đồng</option>
                                    <option value="SILVER">Hạng Bạc</option>
                                    <option value="DIAMOND">Hạng Kim Cương</option>
                                    <option value="EMERALD">Hạng Lục Bảo</option>
                                </select>
                            </div>
                            <div>
                                <label title="Điểm Loyalty cần đổi">Điểm Loyalty cần đổi</label>
                                <input type="number" min="0" name="pointsRequired" value="0" placeholder="200">
                            </div>
                            <div>
                                <label title="Giảm (%)">Giảm (%)</label>
                                <input type="number" min="0" max="100" step="0.1" name="discountPercent" placeholder="10">
                            </div>
                            <div>
                                <label title="Giảm tối đa (VNĐ)">Giảm tối đa (VNĐ)</label>
                                <input type="number" min="0" step="1000" name="maxDiscount" placeholder="50000">
                            </div>
                            <div>
                                <label title="Lượt sử dụng tối đa">Lượt sử dụng tối đa</label>
                                <input type="number" min="1" name="usageLimit" placeholder="100">
                            </div>
                            <div>
                                <label title="Giới hạn / tài khoản">Giới hạn / tài khoản</label>
                                <select name="perUserLimit">
                                    <option value="0">Không giới hạn</option>
                                    <option value="1">Tối đa 1 lần</option>
                                </select>
                            </div>
                            <div>
                                <label title="Ngày bắt đầu"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Ngày bắt đầu</label>
                                <input type="date" name="startDate" required>
                            </div>
                            <div>
                                <label title="Ngày kết thúc"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Ngày kết thúc</label>
                                <input type="date" name="endDate" required>
                            </div>
                            <div>
                                <label title="Trạng thái ban đầu">Trạng thái ban đầu</label>
                                <select name="status">
                                    <option value="active">Kích hoạt ngay (active)</option>
                                    <option value="inactive">Chưa kích hoạt (inactive)</option>
                                </select>
                            </div>
                        </div>

                        <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(280px, 1fr));gap:14px;margin-bottom:16px;">
                            <div>
                                <label style="display:block;font-size:12px;font-weight:600;color:#475569;margin-bottom:6px;">Mô tả ngắn</label>
                                <input type="text" name="description" placeholder="Voucher giảm 10% dành riêng cho hạng Kim Cương..." style="width:100%;height:40px !important;border:1px solid #E2E8F0;border-radius:8px;padding:8px 12px;font-size:13px;box-sizing:border-box;">
                            </div>
                            <div>
                                <label style="display:block;font-size:12px;font-weight:600;color:#475569;margin-bottom:6px;">Điều kiện áp dụng (JSON / text)</label>
                                <input type="text" name="conditionsJson" placeholder="{}" style="width:100%;height:40px !important;border:1px solid #E2E8F0;border-radius:8px;padding:8px 12px;font-size:13px;box-sizing:border-box;">
                            </div>
                        </div>

                        <div style="display:flex;justify-content:flex-end;">
                            <button type="submit" class="btn-primary">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px;"><path d="M12 5v14M5 12h14"/></svg>
                                <span>Tạo mã khuyến mãi</span>
                            </button>
                        </div>
                    </form>
                </article>

                <!-- SECTION 2: DANH SÁCH MÃ KHUYẾN MÃI (MAIN DATA TABLE) -->
                <section class="panel-card" style="padding: 20px;">
                    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
                        <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M4 6h16M4 10h16M4 14h16M4 18h16"/></svg>
                            <span>Danh sách mã khuyến mãi (${fn:escapeXml(promotions.size())} mã)</span>
                        </h2>
                    </div>

                    <div class="table-responsive">
                        <table class="table-data">
                            <thead>
                                <tr>
                                    <th scope="col">Mã khuyến mãi</th>
                                    <th scope="col">Loại Voucher</th>
                                    <th scope="col">Hạng áp dụng</th>
                                    <th scope="col">Giảm giá</th>
                                    <th scope="col">Thời gian áp dụng</th>
                                    <th scope="col">Giới hạn</th>
                                    <th scope="col">Trạng thái</th>
                                    <th scope="col">Người tạo</th>
                                    <th scope="col" style="text-align:right;">Hành động</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="promotion" items="${promotions}">
                                    <tr>
                                        <td>
                                            <strong style="font-family:monospace;font-size:14px;color:#1A1A21;display:block;">${fn:escapeXml(promotion.code)}</strong>
                                            <c:if test="${not empty promotion.description}">
                                                <div style="font-size:12px;color:#6E6E7A;margin-top:2px;" class="admin-clamp-2">${fn:escapeXml(promotion.description)}</div>
                                            </c:if>
                                        </td>
                                        <td>
                                            <span class="tier-badge" style="background:#f1f5f9;color:#475569;border:1px solid #cbd5e1;">
                                                ${fn:escapeXml(promotion.voucherTypeDisplay)}
                                            </span>
                                            <c:if test="${promotion.voucherType eq 'REDEEMABLE'}">
                                                <div style="font-size:11px;color:#92400e;margin-top:2px;font-weight:600;">⚡ ${fn:escapeXml(promotion.pointsRequired)} điểm đổi</div>
                                            </c:if>
                                        </td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${promotion.targetTier eq 'EMERALD'}">
                                                    <span class="tier-badge tier-emerald">Lục Bảo</span>
                                                </c:when>
                                                <c:when test="${promotion.targetTier eq 'DIAMOND'}">
                                                    <span class="tier-badge tier-diamond">Kim Cương</span>
                                                </c:when>
                                                <c:when test="${promotion.targetTier eq 'SILVER'}">
                                                    <span class="tier-badge tier-silver">Bạc</span>
                                                </c:when>
                                                <c:when test="${promotion.targetTier eq 'BRONZE'}">
                                                    <span class="tier-badge tier-bronze">Đồng</span>
                                                </c:when>
                                                <c:otherwise>
                                                    <span class="tier-badge" style="background:#f3f4f6;color:#374151;border:1px solid #e5e7eb;">Tất cả hạng</span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td>
                                            <strong style="color:#6D28D9;font-weight:700;">${cbf:decimal(promotion.discountPercent)}%</strong>
                                            <c:if test="${not empty promotion.maxDiscount}">
                                                <div style="font-size:12px;color:#6E6E7A;margin-top:2px;">Tối đa: <strong style="color:#0f172a;">${cbf:whole(promotion.maxDiscount)} vnđ</strong></div>
                                            </c:if>
                                        </td>
                                        <td>
                                            <div style="font-weight:600;color:#1A1A21;">${fn:escapeXml(promotion.startDate)}</div>
                                            <div style="font-size:12px;color:#6E6E7A;margin-top:2px;">đến ${fn:escapeXml(promotion.endDate)}</div>
                                        </td>
                                        <td>
                                            <div style="font-weight:600;color:#1A1A21;">${fn:escapeXml(promotion.usageLimit)} lượt</div>
                                            <div style="font-size:11px;color:#6E6E7A;margin-top:2px;">${promotion.perUserLimit eq 0 ? 'Không GH/tài khoản' : '1 lần/tài khoản'}</div>
                                        </td>
                                        <td>
                                            <span class="badge-status ${promotion.status eq 'active' ? 'status-success' : 'status-danger'}">
                                                <span class="status-dot"></span>${promotion.status eq 'active' ? 'Đang hoạt động' : 'Ngừng hoạt động'}
                                            </span>
                                        </td>
                                        <td>
                                            <span style="font-size:12px;color:#475569;">${fn:escapeXml(empty promotion.createdByName ? 'Admin hệ thống' : promotion.createdByName)}</span>
                                        </td>
                                        <td style="text-align:right;">
                                            <c:if test="${sessionScope.currentUser.role eq 'admin' or promotion.createdByUserId eq sessionScope.currentUser.id}">
                                            <button type="button" class="button secondary" style="padding:5px 12px;font-size:12px;"
                                                    onclick="openEditPromotionModal({
                                                        id: '${fn:escapeXml(promotion.id)}',
                                                        code: '${fn:escapeXml(promotion.code)}',
                                                        voucherType: '${fn:escapeXml(promotion.voucherType)}',
                                                        targetTier: '${fn:escapeXml(promotion.targetTier)}',
                                                        pointsRequired: '${fn:escapeXml(promotion.pointsRequired)}',
                                                        discountPercent: '${fn:escapeXml(promotion.discountPercent)}',
                                                        maxDiscount: '${fn:escapeXml(promotion.maxDiscount)}',
                                                        usageLimit: '${fn:escapeXml(promotion.usageLimit)}',
                                                        perUserLimit: '${fn:escapeXml(promotion.perUserLimit)}',
                                                        startDate: '${fn:escapeXml(promotion.startDate)}',
                                                        endDate: '${fn:escapeXml(promotion.endDate)}',
                                                        status: '${fn:escapeXml(promotion.status)}',
                                                        description: '${fn:escapeXml(promotion.description)}',
                                                        conditionsJson: '${fn:escapeXml(promotion.conditionsJson)}'
                                                    })">Sửa</button>
                                            </c:if>
                                            <c:if test="${sessionScope.currentUser.role ne 'admin' and promotion.createdByUserId ne sessionScope.currentUser.id}">
                                                <span class="muted" style="font-size:12px;">Chỉ xem</span>
                                            </c:if>
                                        </td>
                                    </tr>
                                </c:forEach>
                                <c:if test="${empty promotions}">
                                    <tr>
                                        <td colspan="9" style="text-align:center;padding:30px;color:#8A8A96;">
                                            Chưa có mã khuyến mãi nào. Hãy tạo mã khuyến mãi mới ở trên!
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

    <!-- QUICK EDIT PROMOTION MODAL -->
    <div id="editPromotionModal" class="modal-backdrop" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(15,23,42,0.6); z-index:9999; align-items:center; justify-content:center;" aria-hidden="true">
        <div class="modal-content" style="background:#ffffff; border-radius:12px; width:92%; max-width:680px; max-height:90vh; overflow-y:auto; padding:24px; box-shadow:0 20px 25px -5px rgba(0,0,0,0.15);" role="dialog" aria-modal="true" aria-labelledby="editModalTitle">
            <div style="display:flex; align-items:center; justify-content:space-between; margin-bottom:16px; padding-bottom:10px; border-bottom:1px solid #e2e8f0;">
                <h3 style="margin:0; font-size:16px; font-weight:700; color:#0f172a;" id="editModalTitle">Chỉnh sửa mã khuyến mãi</h3>
                <button type="button" onclick="closeEditPromotionModal()" aria-label="Đóng" style="background:none; border:none; font-size:24px; cursor:pointer; color:#64748b; line-height:1;">&times;</button>
            </div>

            <form method="post" action="${pageContext.request.contextPath}/admin/promotions" id="editPromotionForm">
                <cb:csrf/>
                <input type="hidden" name="id" id="editPromoId">
                
                <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(200px, 1fr)); gap:12px; margin-bottom:16px;">
                    <div>
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Mã Code</label>
                        <input type="text" name="code" id="editPromoCode" required style="width:100%;">
                    </div>
                    <div>
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;">Loại Voucher</label>
                        <select name="voucherType" id="editPromoVoucherType" style="width:100%;">
                            <option value="PUBLIC">Công khai dùng chung</option>
                            <option value="TIER_RESTRICTED">Dành riêng cho hạng cụ thể</option>
                            <option value="REDEEMABLE">Quy đổi bằng điểm Loyalty</option>
                        </select>
                    </div>
                    <div>
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;">Hạng Áp Dụng</label>
                        <select name="targetTier" id="editPromoTargetTier" style="width:100%;">
                            <option value="ALL">Tất cả hạng</option>
                            <option value="BRONZE">Hạng Đồng</option>
                            <option value="SILVER">Hạng Bạc</option>
                            <option value="DIAMOND">Hạng Kim Cương</option>
                            <option value="EMERALD">Hạng Lục Bảo</option>
                        </select>
                    </div>
                    <div>
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;">Điểm Loyalty cần đổi</label>
                        <input type="number" min="0" name="pointsRequired" id="editPromoPointsRequired" style="width:100%;">
                    </div>
                    <div>
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;">Giảm (%)</label>
                        <input type="number" min="0" max="100" step="0.1" name="discountPercent" id="editPromoDiscountPercent" style="width:100%;">
                    </div>
                    <div>
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;">Giảm tối đa (VNĐ)</label>
                        <input type="number" min="0" step="1000" name="maxDiscount" id="editPromoMaxDiscount" style="width:100%;">
                    </div>
                    <div>
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;">Lượt sử dụng tối đa</label>
                        <input type="number" min="1" name="usageLimit" id="editPromoUsageLimit" style="width:100%;">
                    </div>
                    <div>
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;">Giới hạn / tài khoản</label>
                        <select name="perUserLimit" id="editPromoPerUserLimit" style="width:100%;">
                            <option value="0">Không giới hạn</option>
                            <option value="1">Tối đa 1 lần</option>
                        </select>
                    </div>
                    <div>
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Ngày bắt đầu</label>
                        <input type="date" name="startDate" id="editPromoStartDate" required style="width:100%;">
                    </div>
                    <div>
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Ngày kết thúc</label>
                        <input type="date" name="endDate" id="editPromoEndDate" required style="width:100%;">
                    </div>
                    <div style="grid-column: span 2;">
                        <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;">Trạng thái</label>
                        <select name="status" id="editPromoStatus" style="width:100%;">
                            <option value="active">active (Đang hoạt động)</option>
                            <option value="inactive">inactive (Ngừng hoạt động)</option>
                        </select>
                    </div>
                </div>

                <div style="margin-bottom:12px;">
                    <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;">Mô tả</label>
                    <input type="text" name="description" id="editPromoDescription" style="width:100%;">
                </div>

                <div style="margin-bottom:16px;">
                    <label style="display:block;margin-bottom:6px;font-size:12px;font-weight:600;color:#475569;">Điều kiện áp dụng (JSON / text)</label>
                    <textarea name="conditionsJson" id="editPromoConditionsJson" rows="2" style="width:100%;"></textarea>
                </div>

                <div style="display:flex; justify-content:space-between; align-items:center; margin-top:20px; padding-top:14px; border-top:1px solid #e2e8f0;">
                    <button type="button" class="button danger" style="padding:8px 16px;" onclick="confirmPromotionDelete(document.getElementById('editPromotionForm'))">Xóa mã khuyến mãi</button>
                    <div style="display:flex; gap:10px;">
                        <button type="button" onclick="closeEditPromotionModal()" class="button secondary" style="padding:8px 16px;">Hủy</button>
                        <button type="submit" class="btn-primary" style="padding:8px 20px;">Lưu thay đổi</button>
                    </div>
                </div>
            </form>
        </div>
    </div>

    <script>
        function openEditPromotionModal(p) {
            document.getElementById('editPromoId').value = p.id || '';
            document.getElementById('editPromoCode').value = p.code || '';
            document.getElementById('editPromoVoucherType').value = p.voucherType || 'PUBLIC';
            document.getElementById('editPromoTargetTier').value = p.targetTier || 'ALL';
            document.getElementById('editPromoPointsRequired').value = p.pointsRequired || '0';
            document.getElementById('editPromoDiscountPercent').value = p.discountPercent || '';
            document.getElementById('editPromoMaxDiscount').value = p.maxDiscount || '';
            document.getElementById('editPromoUsageLimit').value = p.usageLimit || '';
            document.getElementById('editPromoPerUserLimit').value = p.perUserLimit || '0';
            document.getElementById('editPromoStartDate').value = p.startDate || '';
            document.getElementById('editPromoEndDate').value = p.endDate || '';
            document.getElementById('editPromoStatus').value = p.status || 'active';
            document.getElementById('editPromoDescription').value = p.description || '';
            document.getElementById('editPromoConditionsJson').value = p.conditionsJson || '';

            var modal = document.getElementById('editPromotionModal');
            modal.style.display = 'flex';
            modal.setAttribute('aria-hidden', 'false');
        }

        function closeEditPromotionModal() {
            var modal = document.getElementById('editPromotionModal');
            modal.style.display = 'none';
            modal.setAttribute('aria-hidden', 'true');
        }

        function confirmPromotionDelete(form) {
            if (form.dataset.confirmed === '1') return true;
            var id = form.querySelector('input[name="id"]');
            fetch('${pageContext.request.contextPath}/admin/promotions?action=delete-impact&id=' + encodeURIComponent(id ? id.value : '0'), {
                headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
            }).then(function(r) { return r.ok ? r.json() : Promise.reject(r.status); })
              .then(function(i) {
                  showAdminConfirm({
                      title: 'Xóa khuyến mãi #' + i.code,
                      message: 'Bạn có chắc chắn muốn xóa chương trình khuyến mãi này?',
                      subnote: 'Đơn tham chiếu: ' + i.orderRefs + ' • Đã dùng: ' + i.usageRefs + ' • Voucher: ' + i.voucherRefs + ' • Trace: ' + i.traceId + '\nCó tham chiếu sẽ chuyển inactive, không xóa lịch sử.',
                      confirmText: 'Xóa / Ẩn khuyến mãi',
                      cancelText: 'Hủy bỏ',
                      isDanger: true,
                      onConfirm: function() {
                          form.dataset.confirmed = '1';
                          var action = document.createElement('input');
                          action.type = 'hidden'; action.name = 'action'; action.value = 'delete';
                          form.appendChild(action);
                          form.submit();
                      }
                  });
              }).catch(function() {
                   window.alert('Không tải được dữ liệu tác động. Thao tác xóa đã bị khóa; vui lòng thử lại.');
               });
            return false;
        }
    </script>
</body>
</html>
