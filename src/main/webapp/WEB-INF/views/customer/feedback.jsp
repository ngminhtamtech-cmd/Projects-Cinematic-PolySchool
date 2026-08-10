<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Góp ý / Cải thiện - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/components.css">
    <style>
        body {
            background-color: #0B0F19;
            color: #E2E8F0;
            font-family: 'Inter', system-ui, -apple-system, sans-serif;
        }

        .feedback-page-container {
            max-width: 1280px;
            margin: 0 auto;
            padding: 24px 20px 60px;
        }

        .feedback-header {
            margin-bottom: 24px;
        }

        .feedback-header h1 {
            font-size: 26px;
            font-weight: 800;
            color: #FFFFFF;
            margin: 0 0 6px 0;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .gradient-text {
            background: linear-gradient(135deg, #F97316 0%, #F59E0B 100%);
            -webkit-background-clip: text;
            background-clip: text;
            -webkit-text-fill-color: transparent;
        }

        .feedback-header p {
            color: #94A3B8;
            font-size: 14px;
            margin: 0;
        }

        .feedback-grid {
            display: grid;
            grid-template-columns: 1.4fr 1fr;
            gap: 24px;
        }

        .feedback-card {
            background: #131B2E;
            border: 1px solid #1E293B;
            border-radius: 16px;
            padding: 24px;
            margin-bottom: 24px;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.25);
        }

        .feedback-card-title {
            font-size: 14px;
            font-weight: 700;
            letter-spacing: 0.5px;
            text-transform: uppercase;
            color: #F97316;
            margin-bottom: 18px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
        }

        .form-group {
            margin-bottom: 16px;
        }

        .form-group.clearfix::after {
            content: "";
            clear: both;
            display: table;
        }

        .form-label {
            display: block;
            font-size: 13px;
            font-weight: 600;
            color: #CBD5E1;
            margin-bottom: 8px;
        }

        .form-label span.req {
            color: #EF4444;
            margin-left: 2px;
        }

        .type-pills-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 8px;
            margin-bottom: 16px;
        }

        .type-pill-btn {
            background: #1E293B;
            border: 1px solid #334155;
            color: #94A3B8;
            padding: 10px 12px;
            border-radius: 10px;
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 6px;
            transition: all 0.2s ease;
        }

        .type-pill-btn:hover {
            border-color: #F97316;
            color: #FFFFFF;
        }

        .type-pill-btn.active {
            background: rgba(249, 115, 22, 0.15);
            border-color: #F97316;
            color: #F97316;
        }

        .priority-pills {
            display: flex;
            gap: 8px;
            margin-bottom: 16px;
        }

        .priority-btn {
            flex: 1;
            background: #1E293B;
            border: 1px solid #334155;
            color: #94A3B8;
            padding: 8px 12px;
            border-radius: 8px;
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
            text-align: center;
            transition: all 0.2s ease;
        }

        .priority-btn.active {
            background: #F97316;
            border-color: #F97316;
            color: #FFFFFF;
        }

        .form-input, .form-textarea, .form-select {
            width: 100%;
            background: #0F172A;
            border: 1px solid #334155;
            border-radius: 10px;
            padding: 12px 14px;
            color: #FFFFFF;
            font-size: 14px;
            outline: none;
            transition: border-color 0.2s ease;
            box-sizing: border-box;
        }

        .form-input:focus, .form-textarea:focus, .form-select:focus {
            border-color: #F97316;
            box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.2);
        }

        .char-counter {
            font-size: 11px;
            color: #64748B;
            float: right;
            margin-top: 4px;
        }

        .dropzone-box {
            border: 2px dashed #334155;
            border-radius: 12px;
            padding: 20px;
            text-align: center;
            background: #0F172A;
            cursor: pointer;
            transition: all 0.2s ease;
            position: relative;
        }

        .dropzone-box:hover {
            border-color: #F97316;
            background: rgba(249, 115, 22, 0.05);
        }

        .dropzone-box input[type="file"] {
            position: absolute;
            top: 0; left: 0; width: 100%; height: 100%;
            opacity: 0;
            cursor: pointer;
        }

        .dropzone-icon {
            font-size: 24px;
            margin-bottom: 6px;
        }

        .dropzone-text {
            font-size: 13px;
            font-weight: 600;
            color: #CBD5E1;
        }

        .text-highlight {
            color: #F97316;
            text-decoration: underline;
        }

        .dropzone-subtext {
            font-size: 11px;
            color: #64748B;
            margin-top: 4px;
        }

        .contact-box {
            margin-bottom: 20px;
            background: #0F172A;
            padding: 14px;
            border-radius: 12px;
            border: 1px solid #1E293B;
        }

        .contact-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 10px;
        }

        .contact-title {
            font-size: 13px;
            color: #FFFFFF;
        }

        .contact-sub {
            font-size: 11px;
            color: #64748B;
            margin: 2px 0 0 0;
        }

        .contact-checkbox {
            width: 18px;
            height: 18px;
            accent-color: #F97316;
            cursor: pointer;
        }

        .contact-inputs-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 10px;
        }

        .btn-submit-feedback {
            width: 100%;
            background: linear-gradient(135deg, #F97316 0%, #EA580C 100%);
            color: #FFFFFF;
            border: none;
            padding: 14px 20px;
            border-radius: 12px;
            font-size: 15px;
            font-weight: 700;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            box-shadow: 0 4px 14px rgba(249, 115, 22, 0.35);
            transition: all 0.2s ease;
        }

        .btn-submit-feedback:hover {
            transform: translateY(-1px);
            box-shadow: 0 6px 20px rgba(249, 115, 22, 0.5);
        }

        .security-note {
            text-align: center;
            font-size: 11px;
            color: #64748B;
            margin-top: 10px;
        }

        .rating-box {
            text-align: center;
        }

        .rating-prompt {
            font-size: 13px;
            color: #CBD5E1;
            margin: 0 0 8px;
        }

        .star-rating {
            display: flex;
            gap: 8px;
            justify-content: center;
            margin: 12px 0;
        }

        .star-rating span {
            font-size: 28px;
            color: #334155;
            cursor: pointer;
            transition: color 0.2s ease;
        }

        .star-rating span.active, .star-rating span:hover {
            color: #F59E0B;
        }

        .rating-status {
            font-size: 12px;
            color: #F97316;
            font-weight: 600;
        }

        .process-steps {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 4px;
            margin-top: 12px;
        }

        .process-step-item {
            text-align: center;
            flex: 1;
        }

        .process-step-icon {
            width: 36px; height: 36px;
            border-radius: 50%;
            background: #1E293B;
            border: 1px solid #334155;
            display: flex;
            align-items: center;
            justify-content: center;
            margin: 0 auto 6px;
            font-size: 16px;
        }

        .process-step-title {
            font-size: 11px;
            font-weight: 600;
            color: #94A3B8;
        }

        .process-arrow {
            color: #334155;
        }

        .support-features-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 12px;
        }

        .support-feature-card {
            background: #0F172A;
            border: 1px solid #1E293B;
            border-radius: 12px;
            padding: 14px;
            text-align: center;
        }

        .support-feature-icon {
            font-size: 22px;
            margin-bottom: 6px;
        }

        .support-feature-title {
            font-size: 12px;
            font-weight: 700;
            color: #FFFFFF;
            margin-bottom: 2px;
        }

        .support-feature-desc {
            font-size: 11px;
            color: #64748B;
        }

        .history-list-item {
            background: #0F172A;
            border: 1px solid #1E293B;
            border-radius: 12px;
            padding: 14px;
            margin-bottom: 10px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
        }

        .history-title {
            font-size: 13px;
            color: #FFFFFF;
            display: block;
            margin-bottom: 2px;
        }

        .history-meta {
            font-size: 11px;
            color: #64748B;
        }

        .admin-reply-box {
            margin-top: 6px;
            background: rgba(109, 40, 217, 0.15);
            border: 1px solid rgba(109, 40, 217, 0.3);
            padding: 6px 10px;
            border-radius: 6px;
            font-size: 11px;
            color: #C084FC;
        }

        .empty-history {
            text-align: center;
            padding: 20px;
            color: #64748B;
            font-size: 13px;
        }

        .status-pill {
            padding: 4px 10px;
            border-radius: 999px;
            font-size: 11px;
            font-weight: 700;
            white-space: nowrap;
        }

        .status-submitted { background: rgba(59, 130, 246, 0.15); color: #60A5FA; border: 1px solid rgba(59, 130, 246, 0.3); }
        .status-reviewing { background: rgba(249, 115, 22, 0.15); color: #FB923C; border: 1px solid rgba(249, 115, 22, 0.3); }
        .status-replied { background: rgba(168, 85, 247, 0.15); color: #C084FC; border: 1px solid rgba(168, 85, 247, 0.3); }
        .status-implemented { background: rgba(34, 197, 94, 0.15); color: #4ADE80; border: 1px solid rgba(34, 197, 94, 0.3); }
    </style>
</head>
<body>
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <div class="feedback-page-container">
        <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

        <header class="feedback-header">
            <h1>💬 Góp ý / Cải thiện</h1>
            <p>Gửi phản hồi để giúp hệ thống tốt hơn. Ý kiến của bạn rất quan trọng với chúng tôi!</p>
        </header>

        <div class="feedback-grid">
            <!-- LEFT COLUMN: FORM & RATING & PIPELINE -->
            <div>
                <!-- FORM GỬI GÓP Ý MỚI -->
                <article class="feedback-card">
                    <div class="feedback-card-title">
                        <span>📝 GỬI GÓP Ý MỚI</span>
                    </div>

                    <form method="post" action="${pageContext.request.contextPath}/feedback" enctype="multipart/form-data" id="feedbackForm">
                        <%@ include file="/WEB-INF/views/shared/csrf.jspf" %>
                        <input type="hidden" name="feedbackType" id="selectedFeedbackType" value="UI/UX">
                        <input type="hidden" name="priority" id="selectedPriority" value="Trung bình">
                        <input type="hidden" name="rating" id="selectedRating" value="5">

                        <!-- LOẠI GÓP Ý -->
                        <div class="form-group">
                            <label class="form-label">Loại góp ý <span class="req">*</span></label>
                            <div class="type-pills-grid">
                                <button type="button" class="type-pill-btn active" onclick="selectType(this, 'UI/UX')">🖥️ UI/UX</button>
                                <button type="button" class="type-pill-btn" onclick="selectType(this, 'Lỗi hệ thống')">🐞 Lỗi hệ thống</button>
                                <button type="button" class="type-pill-btn" onclick="selectType(this, 'Tính năng mới')">✨ Tính năng mới</button>
                                <button type="button" class="type-pill-btn" onclick="selectType(this, 'Hiệu năng')">⚡ Hiệu năng</button>
                                <button type="button" class="type-pill-btn" onclick="selectType(this, 'Khác')">💬 Khác</button>
                            </div>
                        </div>

                        <!-- MỨC ĐỘ ƯU TIÊN -->
                        <div class="form-group">
                            <label class="form-label">Mức độ ưu tiên <span class="req">*</span></label>
                            <div class="priority-pills">
                                <button type="button" class="priority-btn" onclick="selectPriority(this, 'Thấp')">⬇️ Thấp</button>
                                <button type="button" class="priority-btn active" onclick="selectPriority(this, 'Trung bình')">➖ Trung bình</button>
                                <button type="button" class="priority-btn" onclick="selectPriority(this, 'Cao')">⬆️ Cao</button>
                            </div>
                        </div>

                        <!-- TIÊU ĐỀ GÓP Ý -->
                        <div class="form-group">
                            <label class="form-label" for="titleInput">Tiêu đề góp ý <span class="req">*</span></label>
                            <input type="text" class="form-input" id="titleInput" name="title" required maxlength="120" placeholder="Nhập tiêu đề ngắn gọn, súc tích..." oninput="updateCounter('titleInput', 'titleCounter', 120)">
                            <span class="char-counter" id="titleCounter">0/120</span>
                        </div>

                        <!-- MÔ TẢ CHI TIẾT -->
                        <div class="form-group clearfix">
                            <label class="form-label" for="contentInput">Mô tả chi tiết <span class="req">*</span></label>
                            <textarea class="form-textarea" id="contentInput" name="content" rows="4" required maxlength="2000" placeholder="Mô tả chi tiết vấn đề, đề xuất hoặc ý tưởng của bạn..." oninput="updateCounter('contentInput', 'contentCounter', 2000)"></textarea>
                            <span class="char-counter" id="contentCounter">0/2000</span>
                        </div>

                        <!-- ĐÍNH KÈM ẢNH / VIDEO -->
                        <div class="form-group clearfix">
                            <label class="form-label">Đính kèm ảnh / video (tùy chọn)</label>
                            <div class="dropzone-box" id="dropzoneBox">
                                <input type="file" name="attachment" accept="image/*,video/*" onchange="showFileName(this)">
                                <div class="dropzone-icon">☁️</div>
                                <div class="dropzone-text" id="fileNameDisplay">
                                    Kéo thả ảnh hoặc video vào đây hoặc <span class="text-highlight">Chọn tệp</span>
                                </div>
                                <div class="dropzone-subtext">
                                    Hỗ trợ JPG, PNG, GIF, MP4, WebM (Tối đa 50MB)
                                </div>
                            </div>
                        </div>

                        <!-- CHO PHÉP LIÊN HỆ LAI -->
                        <div class="contact-box">
                            <div class="contact-row">
                                <div>
                                    <strong class="contact-title">Cho phép liên hệ lại</strong>
                                    <p class="contact-sub">Chúng tôi có thể liên hệ để làm rõ thông tin (nếu cần).</p>
                                </div>
                                <input type="checkbox" name="allowContact" value="true" checked class="contact-checkbox">
                            </div>
                            <div class="contact-inputs-grid">
                                <div>
                                    <input type="email" class="form-input" name="contactEmail" placeholder="✉️ Nhập email của bạn" value="${fn:escapeXml(sessionScope.currentUser.email)}">
                                </div>
                                <div>
                                    <input type="tel" class="form-input" name="contactPhone" placeholder="📞 Nhập số điện thoại" value="${fn:escapeXml(sessionScope.currentUser.phone)}">
                                </div>
                            </div>
                        </div>

                        <!-- NÚT GỬI GÓP Ý -->
                        <button type="submit" class="btn-submit-feedback">
                            <span>✈️ Gửi góp ý</span>
                        </button>
                        <div class="security-note">
                            🔒 Thông tin của bạn được bảo mật và chỉ dùng để xử lý góp ý.
                        </div>
                    </form>
                </article>

                <!-- MỨC ĐỘ HÀI LÒNG CHUNG -->
                <article class="feedback-card">
                    <div class="feedback-card-title">
                        <span>😊 MỨC ĐỘ HÀI LÒNG CHUNG</span>
                    </div>
                    <div class="rating-box">
                        <p class="rating-prompt">Bạn hài lòng với trải nghiệm hiện tại của CineBook chứ?</p>
                        <div class="star-rating" id="starRating">
                            <span onclick="setRating(1)">★</span>
                            <span onclick="setRating(2)">★</span>
                            <span onclick="setRating(3)">★</span>
                            <span onclick="setRating(4)">★</span>
                            <span onclick="setRating(5)" class="active">★</span>
                        </div>
                        <span class="rating-status" id="ratingText">5/5 - Rất hài lòng</span>
                    </div>
                </article>

                <!-- QUY TRÌNH XỬ LÝ GÓP Ý -->
                <article class="feedback-card">
                    <div class="feedback-card-title">
                        <span>🔄 QUY TRÌNH XỬ LÝ GÓP Ý</span>
                    </div>
                    <div class="process-steps">
                        <div class="process-step-item">
                            <div class="process-step-icon">✈️</div>
                            <div class="process-step-title">1. Gửi góp ý</div>
                        </div>
                        <div class="process-arrow">➔</div>
                        <div class="process-step-item">
                            <div class="process-step-icon">📩</div>
                            <div class="process-step-title">2. Xác nhận</div>
                        </div>
                        <div class="process-arrow">➔</div>
                        <div class="process-step-item">
                            <div class="process-step-icon">🔍</div>
                            <div class="process-step-title">3. Xem xét</div>
                        </div>
                        <div class="process-arrow">➔</div>
                        <div class="process-step-item">
                            <div class="process-step-icon">💬</div>
                            <div class="process-step-title">4. Phản hồi</div>
                        </div>
                        <div class="process-arrow">➔</div>
                        <div class="process-step-item">
                            <div class="process-step-icon">✔️</div>
                            <div class="process-step-title">5. Cải thiện</div>
                        </div>
                    </div>
                </article>
            </div>

            <!-- RIGHT COLUMN: SUPPORT FEATURES & HISTORY -->
            <div>
                <!-- TÍNH NĂNG HỖ TRỢ -->
                <article class="feedback-card">
                    <div class="feedback-card-title">
                        <span>⭐ TÍNH NĂNG HỖ TRỢ</span>
                    </div>
                    <div class="support-features-grid">
                        <div class="support-feature-card">
                            <div class="support-feature-icon">🕒</div>
                            <div class="support-feature-title">Theo dõi trạng thái</div>
                            <div class="support-feature-desc">Cập nhật tiến trình xử lý góp ý</div>
                        </div>
                        <div class="support-feature-card">
                            <div class="support-feature-icon">📎</div>
                            <div class="support-feature-title">Đính kèm ảnh / video</div>
                            <div class="support-feature-desc">Minh họa vấn đề rõ ràng hơn</div>
                        </div>
                        <div class="support-feature-card">
                            <div class="support-feature-icon">😊</div>
                            <div class="support-feature-title">Đánh giá hài lòng</div>
                            <div class="support-feature-desc">Giúp chúng tôi phục vụ tốt hơn</div>
                        </div>
                        <div class="support-feature-card">
                            <div class="support-feature-icon">🔍</div>
                            <div class="support-feature-title">Lọc theo loại góp ý</div>
                            <div class="support-feature-desc">Tìm kiếm và quản lý dễ dàng</div>
                        </div>
                        <div class="support-feature-card">
                            <div class="support-feature-icon">💬</div>
                            <div class="support-feature-title">Phản hồi từ QTV</div>
                            <div class="support-feature-desc">Nhận phản hồi & giải đáp chi tiết</div>
                        </div>
                        <div class="support-feature-card">
                            <div class="support-feature-icon">📜</div>
                            <div class="support-feature-title">Lịch sử góp ý</div>
                            <div class="support-feature-desc">Xem lại các góp ý trước đó</div>
                        </div>
                    </div>
                </article>

                <!-- LỊCH SỬ GÓP Ý GẦN ĐÂY -->
                <article class="feedback-card">
                    <div class="feedback-card-title">
                        <span>📜 LỊCH SỬ GÓP Ý GẦN ĐÂY</span>
                    </div>

                    <c:forEach var="fb" items="${userFeedbacks}">
                        <div class="history-list-item">
                            <div>
                                <strong class="history-title">${fn:escapeXml(fb.title)}</strong>
                                <span class="history-meta">${fn:escapeXml(fb.feedbackCode)} • ${fn:escapeXml(fb.createdAtDisplay)}</span>
                                <c:if test="${not empty fb.adminReply}">
                                    <div class="admin-reply-box">
                                        <strong>💬 Phản hồi QTV:</strong> ${fn:escapeXml(fb.adminReply)}
                                    </div>
                                </c:if>
                            </div>
                            <div>
                                <span class="status-pill ${fn:escapeXml(fb.statusClass)}">${fn:escapeXml(fb.statusLabel)}</span>
                            </div>
                        </div>
                    </c:forEach>

                    <c:if test="${empty userFeedbacks}">
                        <div class="empty-history">
                            Bạn chưa gửi góp ý nào.
                        </div>
                    </c:if>
                </article>
            </div>
        </div>
    </div>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>

    <script>
        function selectType(btn, typeName) {
            var buttons = document.querySelectorAll('.type-pill-btn');
            buttons.forEach(function(b) { b.classList.remove('active'); });
            btn.classList.add('active');
            document.getElementById('selectedFeedbackType').value = typeName;
        }

        function selectPriority(btn, priorityName) {
            var buttons = document.querySelectorAll('.priority-btn');
            buttons.forEach(function(b) { b.classList.remove('active'); });
            btn.classList.add('active');
            document.getElementById('selectedPriority').value = priorityName;
        }

        function updateCounter(inputId, counterId, maxLen) {
            var input = document.getElementById(inputId);
            var counter = document.getElementById(counterId);
            if (input && counter) {
                counter.innerText = input.value.length + '/' + maxLen;
            }
        }

        function showFileName(input) {
            var display = document.getElementById('fileNameDisplay');
            if (input.files && input.files[0]) {
                display.innerText = '📄 Đã chọn: ' + input.files[0].name;
                display.style.color = '#4ADE80';
            }
        }

        function setRating(rating) {
            document.getElementById('selectedRating').value = rating;
            var stars = document.querySelectorAll('#starRating span');
            stars.forEach(function(s, index) {
                if (index < rating) {
                    s.classList.add('active');
                } else {
                    s.classList.remove('active');
                }
            });
            var ratingText = document.getElementById('ratingText');
            var texts = {1: '1/5 - Tệ', 2: '2/5 - Chưa hài lòng', 3: '3/5 - Bình thường', 4: '4/5 - Hài lòng', 5: '5/5 - Rất hài lòng'};
            ratingText.innerText = texts[rating] || (rating + '/5');
        }
    </script>
</body>
</html>
