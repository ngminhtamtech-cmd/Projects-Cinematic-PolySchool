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
    <title>Thông tin cá nhân - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
</head>
<body class="public-page">
    <%@ include file="/WEB-INF/views/shared/public-header.jspf" %>

    <main class="container public-main">
        <%@ include file="/WEB-INF/views/shared/flash.jspf" %>

        <c:if test="${sessionScope.currentUser.warningCount > 0}">
            <div class="notice" style="background: #fff7ed; border-left: 4px solid #f97316; padding: 16px 20px; border-radius: 12px; margin-bottom: 24px; color: #9a3412; box-shadow: 0 2px 4px rgba(0,0,0,0.03);">
                <h4 style="margin: 0 0 6px 0; color: #c2410c; font-size: 1.05rem;">⚠️ CẢNH BÁO VI PHẠM TÀI KHOẢN</h4>
                <p style="margin: 0; font-size: 0.92rem; line-height: 1.5;">
                    Tài khoản của bạn đã bị cảnh cáo <strong>${fn:escapeXml(sessionScope.currentUser.warningCount)}/3</strong> lần do vi phạm quy định cộng đồng / bình luận. 
                    Nếu tích lũy đủ 3 lần cảnh cáo, tài khoản sẽ <strong>tự động bị KHÓA</strong>. Vui lòng tuân thủ quy định đăng bình luận của CineBook.
                </p>
            </div>
        </c:if>

        <!-- Modern Header Card Overview -->
        <section class="profile-header-card">
            <div class="profile-avatar-wrapper">
                <c:choose>
                    <c:when test="${not empty sessionScope.currentUser.avatar}">
                        <img src="${fn:escapeXml(sessionScope.currentUser.avatar)}" alt="Avatar" id="avatarPreviewImg" class="profile-avatar-img" onerror="this.style.display='none'; document.getElementById('avatarInitials').style.display='flex';">
                        <div id="avatarInitials" class="profile-avatar-initials" style="display: none;">
                            ${fn:toUpperCase(fn:substring(sessionScope.currentUser.fullName, 0, 1))}
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div id="avatarInitials" class="profile-avatar-initials">
                            <c:choose>
                                <c:when test="${not empty sessionScope.currentUser.fullName}">
                                    ${fn:toUpperCase(fn:substring(sessionScope.currentUser.fullName, 0, 1))}
                                </c:when>
                                <c:otherwise>U</c:otherwise>
                            </c:choose>
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
            <div class="profile-header-info">
                <div class="profile-header-top">
                    <h1 class="profile-name">${fn:escapeXml(sessionScope.currentUser.fullName)}</h1>
                    <span class="status-pill info">${fn:escapeXml(sessionScope.currentUser.role)}</span>
                    <span class="status-pill success">● Hoạt động</span>
                </div>
                <p class="profile-email">${fn:escapeXml(sessionScope.currentUser.email)}</p>
            </div>
        </section>

        <!-- Profile Tabbed Layout -->
        <section class="profile-layout">
            <!-- Sidebar Navigation Tabs -->
            <nav class="profile-tab-nav" aria-label="Profile navigation">
                <button type="button" class="profile-tab-btn is-active" data-tab="tab-info" onclick="switchProfileTab('tab-info')">
                    <span class="tab-icon">👤</span> Thông tin cá nhân
                </button>
                <button type="button" class="profile-tab-btn" data-tab="tab-password" onclick="switchProfileTab('tab-password')">
                    <span class="tab-icon">🔒</span> Đổi mật khẩu
                </button>
                <button type="button" class="profile-tab-btn" data-tab="tab-account" onclick="switchProfileTab('tab-account')">
                    <span class="tab-icon">⚙️</span> Tài khoản & Dữ liệu
                </button>
            </nav>

            <!-- Panels Container -->
            <div class="profile-panels">
                <!-- Panel 1: Personal Info -->
                <article class="profile-panel panel is-active" id="tab-info">
                    <span class="eyebrow dark">Profile</span>
                    <h2>Thông tin cá nhân</h2>
                    <p class="muted">Cập nhật thông tin hiển thị, số điện thoại, địa chỉ và ảnh đại diện tài khoản.</p>

                    <form method="post" action="${pageContext.request.contextPath}/profile" class="form-grid" style="margin-top: 20px;">
                        <cb:csrf/>
                        <input type="hidden" name="action" value="profile">
                        <div class="form-columns">
                            <label>Username
                                <input type="text" name="username" value="${fn:escapeXml(sessionScope.currentUser.username)}">
                            </label>
                            <label>Họ tên
                                <input type="text" name="fullName" value="${fn:escapeXml(sessionScope.currentUser.fullName)}" required>
                            </label>
                            <label>Email
                                <input type="email" value="${fn:escapeXml(sessionScope.currentUser.email)}" disabled style="opacity: 0.7; cursor: not-allowed;">
                            </label>
                            <label>Số điện thoại
                                <input type="text" name="phone" value="${fn:escapeXml(sessionScope.currentUser.phone)}">
                            </label>
                        </div>
                        <label>Địa chỉ
                            <textarea name="address" rows="3">${fn:escapeXml(sessionScope.currentUser.address)}</textarea>
                        </label>
                        
                        <!-- Drag & Drop Avatar Upload Component -->
                        <div class="avatar-upload-container" style="margin-top: 16px; margin-bottom: 20px;">
                            <label style="display: block; font-size: 0.92rem; font-weight: 700; color: var(--ink); margin-bottom: 8px;">
                                Ảnh đại diện
                            </label>
                            
                            <div class="avatar-dropzone" id="avatarDropzone">
                                <input type="file" id="avatarFileInput" accept="image/*" style="display: none;" onchange="handleAvatarFileSelect(this.files)">
                                <div class="dropzone-content" id="dropzoneContent">
                                    <p class="dropzone-title">Kéo & thả file vào đây</p>
                                    <p class="dropzone-sub">hoặc</p>
                                    <button type="button" class="button secondary dropzone-btn" onclick="document.getElementById('avatarFileInput').click()">Chọn file</button>
                                </div>
                            </div>

                            <!-- Hidden Avatar Input to store data URL or direct image URL for form submission -->
                            <input type="hidden" name="avatar" id="avatarInput" value="${fn:escapeXml(sessionScope.currentUser.avatar)}">
                            
                            <div style="margin-top: 8px; font-size: 0.82rem; text-align: right;">
                                <a href="javascript:void(0)" onclick="toggleUrlInput()" style="color: #2563eb; text-decoration: underline; font-weight: 600;">Hoặc nhập URL hình ảnh trực tiếp</a>
                            </div>
                            <div id="urlInputWrapper" style="display: none; margin-top: 8px;">
                                <input type="text" id="avatarUrlDirect" value="${fn:escapeXml(sessionScope.currentUser.avatar)}" placeholder="https://example.com/avatar.jpg" oninput="handleDirectUrlInput(this.value)">
                            </div>
                        </div>

                        <div class="form-actions" style="margin-top: 24px;">
                            <button type="submit" class="button">Lưu thông tin</button>
                        </div>
                    </form>
                </article>

                <!-- Panel 2: Change Password -->
                <article class="profile-panel panel" id="tab-password">
                    <span class="eyebrow dark">Security</span>
                    <h2>Đổi mật khẩu</h2>
                    <p class="muted">Đảm bảo tài khoản của bạn sử dụng mật khẩu mạnh tối thiểu 10 ký tự.</p>

                    <form method="post" action="${pageContext.request.contextPath}/profile" class="form-grid" style="margin-top: 20px;">
                        <cb:csrf/>
                        <input type="hidden" name="action" value="password">
                        <label>Mật khẩu hiện tại
                            <input type="password" name="currentPassword" required>
                        </label>
                        <label>Mật khẩu mới
                            <input type="password" name="newPassword" minlength="10" required>
                        </label>
                        <label>Xác nhận mật khẩu mới
                            <input type="password" name="confirmPassword" minlength="10" required>
                        </label>
                        <div class="form-actions" style="margin-top: 24px;">
                            <button type="submit" class="button">Cập nhật mật khẩu</button>
                        </div>
                    </form>
                </article>

                <!-- Panel 3: Account & Data -->
                <article class="profile-panel panel" id="tab-account">
                    <span class="eyebrow dark">Account</span>
                    <h2>Thông tin tài khoản & Dữ liệu</h2>
                    <p class="muted">Quản lý các thông tin định danh và xuất dữ liệu cá nhân.</p>

                    <div class="account-info-box" style="margin: 20px 0; padding: 18px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px;">
                        <p style="margin: 0 0 10px 0;"><strong>Vai trò hệ thống:</strong> ${fn:escapeXml(sessionScope.currentUser.role)}</p>
                        <p style="margin: 0 0 10px 0;"><strong>Email đăng nhập:</strong> ${fn:escapeXml(sessionScope.currentUser.email)}</p>
                        <c:if test="${sessionScope.currentUser.role eq 'ADMIN' || sessionScope.currentUser.role eq 'MANAGER'}">
                            <p class="avatar-note" style="margin: 8px 0 0 0; color: #64748b;">Nếu tài khoản là Manager hoặc Admin, bạn vẫn có thể quay lại dashboard từ menu trên.</p>
                        </c:if>
                    </div>

                    <div style="margin-bottom: 36px;">
                        <a class="button secondary" href="${pageContext.request.contextPath}/profile/export">Tải dữ liệu cá nhân (JSON)</a>
                    </div>

                    <!-- Danger Zone -->
                    <div class="profile-danger-card" style="border: 1px solid #fecaca; background: #fff5f5; padding: 20px; border-radius: 12px;">
                        <span class="eyebrow dark" style="color: #ef4444;">Privacy</span>
                        <h3 style="margin: 6px 0 8px 0; color: #dc2626;">Xóa tài khoản</h3>
                        <p class="muted" style="margin: 0 0 16px 0;">Thông tin nhận dạng sẽ được ẩn danh. Hóa đơn và đơn hàng vẫn được giữ theo nghĩa vụ kế toán.</p>

                        <form method="post" action="${pageContext.request.contextPath}/profile/delete" class="form-grid">
                            <cb:csrf/>
                            <input type="hidden" name="action" value="delete">
                            <label>Mật khẩu hiện tại
                                <input type="password" name="currentPassword" required>
                            </label>
                            <label>Nhập chính xác “XOA TAI KHOAN”
                                <input type="text" name="confirmation" required autocomplete="off">
                            </label>
                            <div class="form-actions" style="margin-top: 16px;">
                                <button type="submit" class="button danger" onclick="return confirm('Xóa và ẩn danh tài khoản này?')">Xóa tài khoản</button>
                            </div>
                        </form>
                    </div>
                </article>
            </div>
        </section>
    </main>

    <%@ include file="/WEB-INF/views/shared/public-footer.jspf" %>
    
    <script>
        function switchProfileTab(tabId) {
            document.querySelectorAll('.profile-tab-btn').forEach(btn => {
                btn.classList.toggle('is-active', btn.getAttribute('data-tab') === tabId);
            });
            document.querySelectorAll('.profile-panel').forEach(panel => {
                panel.classList.toggle('is-active', panel.id === tabId);
            });
            if (history.replaceState) {
                history.replaceState(null, null, '#' + tabId);
            }
        }

        function updateAvatarPreview(url) {
            const img = document.getElementById('avatarPreviewImg');
            const initials = document.getElementById('avatarInitials');
            if (url && url.trim() !== '') {
                if (img) {
                    img.src = url;
                    img.style.display = 'block';
                }
                if (initials) initials.style.display = 'none';
            } else {
                if (img) img.style.display = 'none';
                if (initials) initials.style.display = 'flex';
            }
        }

        function handleAvatarFileSelect(files) {
            if (!files || files.length === 0) return;
            const file = files[0];
            if (!file.type.startsWith('image/')) {
                alert('Vui lòng chọn file hình ảnh (PNG, JPG, WEBP,...).');
                return;
            }
            if (file.size > 3 * 1024 * 1024) {
                alert('Dung lượng ảnh quá lớn! Vui lòng chọn file ảnh dưới 3MB.');
                return;
            }
            const reader = new FileReader();
            reader.onload = function(e) {
                const dataUrl = e.target.result;
                document.getElementById('avatarInput').value = dataUrl;
                updateAvatarPreview(dataUrl);
                
                const dropContent = document.getElementById('dropzoneContent');
                if (dropContent) {
                    dropContent.innerHTML = `
                        <p class="dropzone-title" style="color: #16a34a;">✓ Đã chọn ảnh: \${file.name}</p>
                        <p class="dropzone-sub">Bấm "Lưu thông tin" bên dưới để hoàn tất cập nhật</p>
                        <button type="button" class="button secondary dropzone-btn" onclick="document.getElementById('avatarFileInput').click()">Đổi ảnh khác</button>
                    `;
                }
            };
            reader.readAsDataURL(file);
        }

        function handleDirectUrlInput(url) {
            document.getElementById('avatarInput').value = url;
            updateAvatarPreview(url);
        }

        function toggleUrlInput() {
            const wrap = document.getElementById('urlInputWrapper');
            if (wrap) {
                wrap.style.display = wrap.style.display === 'none' ? 'block' : 'none';
            }
        }

        document.addEventListener('DOMContentLoaded', () => {
            const hash = window.location.hash.replace('#', '');
            if (hash && document.getElementById(hash)) {
                switchProfileTab(hash);
            }

            // Drag and drop event listeners
            const dropzone = document.getElementById('avatarDropzone');
            if (dropzone) {
                ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
                    dropzone.addEventListener(eventName, (e) => {
                        e.preventDefault();
                        e.stopPropagation();
                    }, false);
                });

                ['dragenter', 'dragover'].forEach(eventName => {
                    dropzone.addEventListener(eventName, () => dropzone.classList.add('drag-active'), false);
                });

                ['dragleave', 'drop'].forEach(eventName => {
                    dropzone.addEventListener(eventName, () => dropzone.classList.remove('drag-active'), false);
                });

                dropzone.addEventListener('drop', (e) => {
                    const dt = e.dataTransfer;
                    if (dt && dt.files) {
                        handleAvatarFileSelect(dt.files);
                    }
                }, false);
            }
        });
    </script>
    <script src="${pageContext.request.contextPath}/assets/js/main.js"></script>
</body>
</html>
