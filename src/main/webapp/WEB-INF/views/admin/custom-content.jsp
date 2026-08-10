<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="managerPromotionOnly" value="${sessionScope.currentUser.role eq 'manager'}" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/views/shared/favicon.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${managerPromotionOnly ? 'Quản lý Ưu đãi' : 'Quản lý Nội dung Khác'} - CineBook</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css?v=20260805e">
    <style>
        .tabs-header {
            display: flex;
            gap: 10px;
            margin-bottom: 20px;
            border-bottom: 2px solid #E2E8F0;
            padding-bottom: 2px;
        }
        .tab-btn {
            background: none;
            border: none;
            color: #64748b;
            font-weight: 600;
            font-size: 14px;
            padding: 10px 18px;
            cursor: pointer;
            border-radius: 8px 8px 0 0;
            transition: all 0.2s ease;
            position: relative;
            bottom: -2px;
        }
        .tab-btn:hover {
            color: #6D28D9;
            background: #F3E8FF;
        }
        .tab-btn.active {
            color: #6D28D9;
            background: #FFFFFF;
            font-weight: 700;
            border-bottom: 3px solid #6D28D9;
        }
        .tab-pane {
            display: none;
        }
        .tab-pane.active {
            display: block;
        }
        .badge-tag {
            display: inline-block;
            padding: 3px 8px;
            font-size: 11px;
            font-weight: 700;
            text-transform: uppercase;
            border-radius: 6px;
            background: #E2E8F0;
            color: #334155;
        }
        .badge-tag.primary {
            background: #F3E8FF;
            color: #6D28D9;
            border: 1px solid #DDD6FE;
        }
        .custom-form-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 14px;
            margin-bottom: 14px;
        }
        .custom-form-grid label {
            display: block;
            font-size: 12px;
            font-weight: 600;
            color: #475569;
            margin-bottom: 6px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .custom-form-grid input:not([type="file"]),
        .custom-form-grid select,
        .custom-form-grid textarea {
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
        .custom-form-grid textarea {
            height: 60px !important;
        }
        .custom-form-grid input:focus,
        .custom-form-grid select:focus,
        .custom-form-grid textarea:focus {
            border-color: #6D28D9;
            outline: none;
            box-shadow: 0 0 0 3px rgba(109, 40, 217, 0.1);
        }
        .dropzone-area {
            background: #F8FAFC;
            border: 2px dashed #CBD5E1;
            border-radius: 8px;
            padding: 14px 16px;
            text-align: center;
            cursor: pointer;
            transition: all 0.2s ease;
        }
        .dropzone-area:hover {
            border-color: #6D28D9;
            background: #F3E8FF;
        }
        .dropzone-btn {
            background: #FFFFFF;
            color: #475569;
            border: 1px solid #CBD5E1;
            border-radius: 6px;
            padding: 4px 14px;
            font-weight: 600;
            font-size: 12px;
            cursor: pointer;
            margin-left: 8px;
        }
        .dropzone-info {
            font-size: 12px;
            color: #64748B;
            margin-top: 4px;
        }
        .content-scope-empty {
            padding: 16px 18px;
            border: 1px solid #ddd6fe;
            border-radius: 10px;
            background: #f5f3ff;
            color: #4c1d95;
            font-size: 13px;
            margin-bottom: 18px;
        }
        .manager-promotion-page .promotion-workspace { max-width: 1180px; }
        .manager-promotion-page .promotion-form-card,
        .manager-promotion-page .promotion-list-card { padding: 18px !important; }
        .manager-promotion-page .promotion-form-card .custom-form-grid { grid-template-columns:repeat(3,minmax(0,1fr)); }
        .manager-promotion-page .promotion-list-card .table-responsive { border-radius:10px; }
        @media (max-width: 980px) {
            .manager-promotion-page .promotion-form-card .custom-form-grid { grid-template-columns:1fr 1fr; }
        }
        @media (max-width: 640px) {
            .manager-promotion-page .promotion-form-card .custom-form-grid { grid-template-columns:1fr; }
        }
    </style>
</head>
<body class="admin-body ${managerPromotionOnly ? 'manager-promotion-page' : ''}">
    <div class="dashboard">
        <%@ include file="/WEB-INF/views/admin/sidebar.jspf" %>
        <main class="dashboard-main">
            <%@ include file="/WEB-INF/views/admin/admin-topbar.jspf" %>
            <div class="dashboard-content">
                <%@ include file="/WEB-INF/views/shared/flash.jspf" %>
                
                <!-- PAGE HEADER -->
                <div class="portal-head" style="margin-bottom:20px; display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px;">
                    <div>
                        <h1 style="font-size:22px;font-weight:600;color:#1A1A21;margin:0 0 4px;">${managerPromotionOnly ? 'Quản lý Ưu đãi' : 'Quản lý Nội dung Khác'}</h1>
                        <p class="muted" style="font-size:13px;color:#6E6E7A;margin:0;">${managerPromotionOnly ? 'Tạo và cập nhật các ưu đãi đang hiển thị tại rạp.' : 'Thêm, sửa, xóa CineTags, Góc Điện Ảnh, Ưu Đãi và Phim Hay Tháng lưu trữ động trong hệ thống.'}</p>
                    </div>
                    <c:if test="${not contentCinemaRequired}"><button type="button" class="btn-primary" onclick="toggleFormCardForActiveTab()" style="height:40px; padding:0 20px; font-weight:600; font-size:13px; display:inline-flex; align-items:center; gap:8px; border-radius:10px; box-shadow:0 4px 12px rgba(109,40,217,0.25);">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" width="16" height="16"><path d="M12 5v14M5 12h14"/></svg>
                        <span>${managerPromotionOnly ? '+ Tạo ưu đãi mới' : '+ Tạo nội dung mới'}</span>
                    </button></c:if>
                </div>

                <c:if test="${contentCinemaRequired}">
                    <div class="content-scope-empty"><strong>Chọn một rạp cụ thể</strong><br>Admin hãy dùng bộ chọn rạp trên thanh công cụ để xem và chỉnh sửa đúng nội dung của rạp đó.</div>
                </c:if>

                <c:if test="${not contentCinemaRequired}">

                <c:if test="${not managerPromotionOnly}">
                    <!-- Tabs Navigation -->
                    <div class="tabs-header">
                        <button class="tab-btn active" onclick="switchTab('cinetag', this)">CineTag# Merchandise</button>
                        <button class="tab-btn" onclick="switchTab('gocdienanh', this)">Góc Điện Ảnh</button>
                        <button class="tab-btn" onclick="switchTab('uudai', this)">Ưu đãi Khuyến mãi</button>
                        <button class="tab-btn" onclick="switchTab('phimhay', this)">Phim Hay Tháng</button>
                    </div>
                </c:if>

                <c:if test="${not managerPromotionOnly}">
                <!-- TAB 1: CineTag# Merchandise -->
                <div id="pane-cinetag" class="tab-pane active">
                    <!-- FORM CARD (Collapsible) -->
                    <article class="panel-card" style="margin-bottom: 24px; padding: 20px; display: none;" id="form-card-cinetag">
                        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;padding-bottom:10px;border-bottom:1px solid #E8E8EE;">
                            <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>
                                <span id="form-title-cinetag">Thêm vật phẩm CineTag#</span>
                            </h2>
                            <span style="font-size:12px;color:#8A8A96;">Điền đầy đủ thông tin bên dưới để tạo vật phẩm merchandise</span>
                        </div>

                        <form method="post" action="${pageContext.request.contextPath}/admin/films?tab=custom" enctype="multipart/form-data">
                            <cb:csrf/>
                            <input type="hidden" name="type" value="cinetag">
                            <input type="hidden" name="sub" value="cinetag">
                            <input type="hidden" name="action" value="save">
                            <input type="hidden" name="index" id="index-cinetag" value="-1">
                            
                            <div class="custom-form-grid">
                                <div>
                                    <label title="Phân loại Tag"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Phân loại Tag</label>
                                    <select id="tag-cinetag-select" onchange="handleTagSelectChange(this.value)" required style="width:100%;">
                                        <option value="movie-verse">movie-verse (Phụ kiện Phim ảnh)</option>
                                        <option value="fan-wibu">fan-wibu (Phụ kiện Anime/Manga)</option>
                                        <option value="inner-child">inner-child (Tuổi thơ / Đồ chơi)</option>
                                        <option value="yolo">yolo (#Yolo Phụ kiện độc lạ)</option>
                                        <option value="__NEW__" style="color:#6D28D9;font-weight:bold;">+ Thêm loại Tag mới...</option>
                                    </select>
                                    <input type="hidden" name="tag" id="tag-cinetag" value="movie-verse">
                                    <div id="custom-tag-container-cinetag" style="display:none;margin-top:8px;">
                                        <div style="display:flex;gap:8px;align-items:center;">
                                            <input type="text" id="custom-tag-input-cinetag" placeholder="Nhập tên Tag mới (Ví dụ: marvel, k-pop...)" style="flex:1;height:38px;padding:6px 12px;border:1.5px solid #6D28D9;border-radius:8px;font-size:13px;box-sizing:border-box;outline:none;" oninput="updateCustomTagValue(this.value)" onkeydown="if(event.key==='Enter'){event.preventDefault();saveCustomTag();}">
                                            <button type="button" id="btn-save-custom-tag-cinetag" onclick="saveCustomTag()" style="height:38px;padding:0 14px;background:#6D28D9;color:#FFFFFF;border:none;border-radius:8px;font-size:13px;font-weight:600;cursor:pointer;display:inline-flex;align-items:center;gap:6px;white-space:nowrap;transition:all 0.2s ease;box-shadow:0 2px 4px rgba(109,40,217,0.2);" title="Lưu loại Tag mới">
                                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px;"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
                                                <span>Lưu Tag</span>
                                            </button>
                                        </div>
                                        <div id="custom-tag-feedback" style="display:none;font-size:12px;color:#10B981;font-weight:600;margin-top:4px;"></div>
                                    </div>
                                </div>
                                <div>
                                    <label title="Tên vật phẩm"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Tên vật phẩm</label>
                                    <input type="text" name="name" id="name-cinetag" required placeholder="Figure Zoro, Ly nước Spider-man...">
                                </div>
                                <div>
                                    <label title="Giá bán (VNĐ)"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Giá bán (VNĐ)</label>
                                    <input type="number" name="price" id="price-cinetag" required placeholder="150000">
                                </div>
                                <div>
                                    <label title="URL ảnh có sẵn">Hoặc dán URL ảnh có sẵn</label>
                                    <input type="text" name="imageUrl" id="image-cinetag" placeholder="/assets/uploads/...">
                                </div>
                            </div>

                            <div style="margin-bottom:16px;">
                                <label style="display:block;font-size:12px;font-weight:600;color:#475569;margin-bottom:6px;"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Tải ảnh vật phẩm</label>
                                <div class="dropzone-area" onclick="document.getElementById('file-cinetag').click()">
                                    <span style="font-size:13px;font-weight:600;color:#1E293B;">Kéo & thả file vào đây hoặc</span>
                                    <button type="button" class="dropzone-btn">Chọn file ảnh</button>
                                    <input type="file" name="imageFile" id="file-cinetag" accept="image/*" style="display:none;" onchange="updateFileName(this, 'fileName-cinetag')">
                                    <div class="dropzone-info" id="fileName-cinetag">JPG, PNG, WEBP (tối đa 5MB) • Kích thước đề xuất: 800×1200px</div>
                                </div>
                            </div>

                            <div style="display:flex;justify-content:flex-end;gap:10px;">
                                <button type="button" class="button secondary" onclick="closeFormCard('cinetag')">Hủy / Đóng form</button>
                                <button type="submit" class="btn-primary">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px;"><path d="M12 5v14M5 12h14"/></svg>
                                    <span>Lưu vật phẩm</span>
                                </button>
                            </div>
                        </form>
                    </article>

                    <!-- LIST CARD -->
                    <section class="panel-card" style="padding: 20px;">
                        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
                            <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M4 6h16M4 10h16M4 14h16M4 18h16"/></svg>
                                <span>Danh sách vật phẩm CineTag#</span>
                            </h2>
                            <button type="button" class="btn-primary" style="padding:6px 14px;font-size:12px;display:inline-flex;align-items:center;gap:4px;" onclick="toggleFormCard('cinetag')">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M12 5v14M5 12h14"/></svg>
                                <span>+ Tạo vật phẩm mới</span>
                            </button>
                        </div>
                        <div id="list-cinetag"></div>
                    </section>
                </div>

                <!-- TAB 2: GÓC ĐIỆN ẢNH -->
                <div id="pane-gocdienanh" class="tab-pane">
                    <!-- FORM CARD (Collapsible) -->
                    <article class="panel-card" style="margin-bottom: 24px; padding: 20px; display: none;" id="form-card-gocdienanh">
                        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;padding-bottom:10px;border-bottom:1px solid #E8E8EE;">
                            <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M4 11v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8H4zM4 6.5L8 3h3L7 6.5h4L15 3h3l-4 3.5h4a2 2 0 0 1 2 2V8H4v-.5z"/></svg>
                                <span id="form-title-gocdienanh">Thêm Mục Góc Điện Ảnh</span>
                            </h2>
                            <span style="font-size:12px;color:#8A8A96;">Quản lý các bài viết Thể loại, Diễn viên, Đạo diễn, Review & Blog phim</span>
                        </div>

                        <form method="post" action="${pageContext.request.contextPath}/admin/films?tab=custom" enctype="multipart/form-data">
                            <cb:csrf/>
                            <input type="hidden" name="type" value="corner">
                            <input type="hidden" name="sub" value="gocdienanh">
                            <input type="hidden" name="action" value="save">
                            <input type="hidden" name="index" id="index-gocdienanh" value="-1">
                            
                            <div class="custom-form-grid">
                                <div>
                                    <label title="Chuyên mục Góc Điện Ảnh"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Chuyên mục</label>
                                    <select name="section" id="section-gocdienanh" required style="width:100%;">
                                        <option value="the-loai">Thể Loại Phim</option>
                                        <option value="dien-vien">Diễn Viên</option>
                                        <option value="dao-dien">Đạo Diễn</option>
                                        <option value="binh-luan">Bình Luận Phim</option>
                                        <option value="blog">Blog Điện Ảnh</option>
                                    </select>
                                </div>
                                <div>
                                    <label title="Tiêu đề bài viết"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Tiêu đề bài viết / Nội dung</label>
                                    <input type="text" name="title" id="title-gocdienanh" required placeholder="HOT SCANDAL SPIDERMAN, Đạo diễn Christopher Nolan...">
                                </div>
                                <div>
                                    <label title="Nhãn phụ / Prefix">Nhãn phụ / Prefix (Ví dụ: HOT SCANDAL, REVIEW...)</label>
                                    <input type="text" name="prefix" id="prefix-gocdienanh" placeholder="HOT SCANDAL, REVIEW, BLOG...">
                                </div>
                                <div>
                                    <label title="Lượt thích">Lượt thích (Likes)</label>
                                    <input type="number" name="likes" id="likes-gocdienanh" value="0" placeholder="0">
                                </div>
                                <div>
                                    <label title="Lượt xem">Lượt xem (Views)</label>
                                    <input type="number" name="views" id="views-gocdienanh" value="0" placeholder="0">
                                </div>
                                <div>
                                    <label title="URL ảnh có sẵn">Hoặc dán URL ảnh có sẵn</label>
                                    <input type="text" name="imageUrl" id="image-gocdienanh" placeholder="/assets/uploads/...">
                                </div>
                            </div>

                            <div style="margin-bottom:14px;">
                                <label style="display:block;font-size:12px;font-weight:600;color:#475569;margin-bottom:6px;">Mô tả ngắn / Trích dẫn</label>
                                <textarea name="description" id="desc-gocdienanh" rows="2" placeholder="Nội dung mô tả ngắn về thể loại, diễn viên, đạo diễn hoặc bài review..." style="width:100%;border:1px solid #E2E8F0;border-radius:8px;padding:8px 12px;font-size:13px;box-sizing:border-box;"></textarea>
                            </div>

                            <div style="margin-bottom:16px;">
                                <label style="display:block;font-size:12px;font-weight:600;color:#475569;margin-bottom:6px;"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Tải ảnh bài viết / banner</label>
                                <div class="dropzone-area" onclick="document.getElementById('file-gocdienanh').click()">
                                    <span style="font-size:13px;font-weight:600;color:#1E293B;">Kéo & thả file vào đây hoặc</span>
                                    <button type="button" class="dropzone-btn">Chọn file ảnh</button>
                                    <input type="file" name="imageFile" id="file-gocdienanh" accept="image/*" style="display:none;" onchange="updateFileName(this, 'fileName-gocdienanh')">
                                    <div class="dropzone-info" id="fileName-gocdienanh">JPG, PNG, WEBP (tối đa 5MB) • Kích thước đề xuất: 800×800px hoặc 1200×675px</div>
                                </div>
                            </div>

                            <div style="display:flex;justify-content:flex-end;gap:10px;">
                                <button type="button" class="button secondary" onclick="closeFormCard('gocdienanh')">Hủy / Đóng form</button>
                                <button type="submit" class="btn-primary">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px;"><path d="M12 5v14M5 12h14"/></svg>
                                    <span>Lưu Góc Điện Ảnh</span>
                                </button>
                            </div>
                        </form>
                    </article>

                    <!-- LIST CARD -->
                    <section class="panel-card" style="padding: 20px;">
                        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
                            <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M4 6h16M4 10h16M4 14h16M4 18h16"/></svg>
                                <span>Danh sách nội dung Góc Điện Ảnh</span>
                            </h2>
                            <button type="button" class="btn-primary" style="padding:6px 14px;font-size:12px;display:inline-flex;align-items:center;gap:4px;" onclick="toggleFormCard('gocdienanh')">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M12 5v14M5 12h14"/></svg>
                                <span>+ Tạo bài viết mới</span>
                            </button>
                        </div>
                        <div id="list-gocdienanh"></div>
                    </section>
                </div>
                </c:if>

                <!-- TAB 3: Ưu Đãi -->
                <div id="pane-uudai" class="tab-pane ${managerPromotionOnly ? 'active promotion-workspace' : ''}">
                    <!-- FORM CARD (Collapsible) -->
                    <article class="panel-card promotion-form-card" style="margin-bottom: 16px; padding: 20px; display: none;" id="form-card-uudai">
                        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;padding-bottom:10px;border-bottom:1px solid #E8E8EE;">
                            <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
                                <span id="form-title-uudai">Thêm Ưu Đãi Khuyến Mãi</span>
                            </h2>
                            <span style="font-size:12px;color:#8A8A96;">Điền đầy đủ thông tin để tạo chương trình ưu đãi khuyến mãi mới</span>
                        </div>

                        <form method="post" action="${pageContext.request.contextPath}/admin/films?tab=custom" enctype="multipart/form-data">
                            <cb:csrf/>
                            <input type="hidden" name="type" value="event">
                            <input type="hidden" name="sub" value="uudai">
                            <input type="hidden" name="section" value="uu-dai">
                            <input type="hidden" name="action" value="save">
                            <input type="hidden" name="index" id="index-uudai" value="-1">
                            
                            <div class="custom-form-grid">
                                <div>
                                    <label title="Tiêu đề Ưu Đãi"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Tiêu đề Ưu Đãi</label>
                                    <input type="text" name="title" id="title-uudai" required placeholder="Ví dụ: Ưu đãi HSSV giảm 20% vé xem phim...">
                                </div>
                                <div>
                                    <label title="Đường dẫn điểm nhận / Xem chi tiết">Đường dẫn xem chi tiết (Target URL)</label>
                                    <input type="text" name="targetUrl" id="targetUrl-uudai" placeholder="Ví dụ: /booking, /cinetag, hoặc https://...">
                                </div>
                                <div>
                                    <label title="URL ảnh Banner có sẵn">Hoặc dán URL ảnh Banner có sẵn</label>
                                    <input type="text" name="imageUrl" id="image-uudai" placeholder="/assets/uploads/...">
                                </div>
                            </div>

                            <div style="margin-bottom:14px;">
                                <label style="display:block;font-size:12px;font-weight:600;color:#475569;margin-bottom:6px;">Mô tả chi tiết / Thể lệ ưu đãi</label>
                                <textarea name="description" id="desc-uudai" rows="2" placeholder="Chi tiết điều kiện áp dụng, thời gian ưu đãi..." style="width:100%;border:1px solid #E2E8F0;border-radius:8px;padding:8px 12px;font-size:13px;box-sizing:border-box;"></textarea>
                            </div>

                            <div style="margin-bottom:16px;">
                                <label style="display:block;font-size:12px;font-weight:600;color:#475569;margin-bottom:6px;"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Banner ảnh Ưu đãi</label>
                                <div class="dropzone-area" onclick="document.getElementById('file-uudai').click()">
                                    <span style="font-size:13px;font-weight:600;color:#1E293B;">Kéo & thả file vào đây hoặc</span>
                                    <button type="button" class="dropzone-btn">Chọn file ảnh</button>
                                    <input type="file" name="imageFile" id="file-uudai" accept="image/*" style="display:none;" onchange="updateFileName(this, 'fileName-uudai')">
                                    <div class="dropzone-info" id="fileName-uudai">JPG, PNG, WEBP (tối đa 5MB) • Kích thước đề xuất: 1200×675px</div>
                                </div>
                            </div>

                            <div style="display:flex;justify-content:flex-end;gap:10px;">
                                <button type="button" class="button secondary" onclick="closeFormCard('uudai')">Hủy / Đóng form</button>
                                <button type="submit" class="btn-primary">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px;"><path d="M12 5v14M5 12h14"/></svg>
                                    <span>Lưu Ưu Đãi</span>
                                </button>
                            </div>
                        </form>
                    </article>

                    <!-- LIST CARD -->
                    <section class="panel-card promotion-list-card" style="padding: 20px;">
                        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
                            <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M4 6h16M4 10h16M4 14h16M4 18h16"/></svg>
                                <span>Danh sách Ưu Đãi Khuyến Mãi</span>
                            </h2>
                            <button type="button" class="btn-primary" style="padding:6px 14px;font-size:12px;display:inline-flex;align-items:center;gap:4px;" onclick="toggleFormCard('uudai')">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M12 5v14M5 12h14"/></svg>
                                <span>+ Tạo ưu đãi mới</span>
                            </button>
                        </div>
                        <div id="list-uudai"></div>
                    </section>
                </div>

                <c:if test="${not managerPromotionOnly}">
                <!-- TAB 4: Phim Hay Tháng -->
                <div id="pane-phimhay" class="tab-pane">
                    <!-- FORM CARD (Collapsible) -->
                    <article class="panel-card" style="margin-bottom: 24px; padding: 20px; display: none;" id="form-card-phimhay">
                        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;padding-bottom:10px;border-bottom:1px solid #E8E8EE;">
                            <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M15 10l5 5-5 5M4 4v7a4 4 0 0 0 4 4h12"/></svg>
                                <span id="form-title-phimhay">Thêm Phim Hay Tháng</span>
                            </h2>
                            <span style="font-size:12px;color:#8A8A96;">Điền đầy đủ thông tin để thêm bộ phim nổi bật đáng xem nhất tháng</span>
                        </div>

                        <form method="post" action="${pageContext.request.contextPath}/admin/films?tab=custom" enctype="multipart/form-data">
                            <cb:csrf/>
                            <input type="hidden" name="type" value="event">
                            <input type="hidden" name="sub" value="phimhay">
                            <input type="hidden" name="section" value="phim-hay">
                            <input type="hidden" name="action" value="save">
                            <input type="hidden" name="index" id="index-phimhay" value="-1">
                            
                            <div style="display:grid; grid-template-columns: 1fr; gap: 16px; margin-bottom: 16px;">
                                <div>
                                    <label title="Chọn Phim Nổi Bật Trong DB"><span style="color:#ef4444;font-weight:bold;margin-right:2px;">*</span>Chọn Phim Đang / Sắp Chiếu</label>
                                    <select name="filmId" id="filmId-phimhay" required onchange="onPhimHayFilmChange(this)">
                                        <option value="">-- Chọn phim đang / sắp chiếu --</option>
                                        <c:forEach var="film" items="${films}">
                                            <c:set var="bannerImg" value="${cbf:assetUrl(pageContext.request.contextPath, empty film.banner ? film.thumbnail : film.banner)}" />
                                            <c:if test="${not film.deleted and (film.status eq 'showing' or film.status eq 'coming')}">
                                                <option value="${fn:escapeXml(film.id)}" 
                                                        data-title="${fn:escapeXml(film.title)}"
                                                        data-banner="${fn:escapeXml(bannerImg)}"
                                                        data-date="${fn:escapeXml(film.releaseDate)}"
                                                        data-status="${film.status eq 'showing' ? 'Đang chiếu' : 'Sắp chiếu'}">
                                                    [${film.status eq 'showing' ? 'Đang chiếu' : 'Sắp chiếu'}] ${fn:escapeXml(film.title)} (${fn:escapeXml(film.releaseDate)})
                                                </option>
                                            </c:if>
                                        </c:forEach>
                                    </select>
                                </div>

                                <div>
                                    <label style="display:block;font-size:12px;font-weight:600;color:#475569;margin-bottom:6px;">Mô tả / Đánh giá ngắn (Tùy chọn)</label>
                                    <textarea name="description" id="desc-phimhay" rows="2" placeholder="Tóm tắt lý do phim này đáng xem nhất tháng (bỏ trống sẽ sử dụng mô tả của phim)..." style="width:100%;border:1px solid #E2E8F0;border-radius:8px;padding:8px 12px;font-size:13px;box-sizing:border-box;"></textarea>
                                </div>

                                <div>
                                    <label style="display:block;font-size:12px;font-weight:600;color:#475569;margin-bottom:6px;">Xem trước Poster Ngang của Phim (Tự động lấy từ DB)</label>
                                    <div id="phimhay-poster-demo" style="max-width: 440px; border: 1.5px dashed #CBD5E1; border-radius: 10px; padding: 18px; text-align: center; background: #F8FAFC; color: #64748B; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px;">
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="26" height="26" style="color:#94A3B8;"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
                                        <div style="font-size: 12px; font-weight: 600; color: #475569;">Bản xem trước Poster ngang</div>
                                        <div style="font-size: 11px; color: #94A3B8;">Chọn phim ở trên để xem trước Poster ngang 16:9 sẽ hiển thị trên trang người dùng.</div>
                                    </div>
                                </div>
                            </div>

                            <div style="display:flex;justify-content:flex-end;gap:10px;">
                                <button type="button" class="button secondary" onclick="closeFormCard('phimhay')">Hủy / Đóng form</button>
                                <button type="submit" class="btn-primary">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px;"><path d="M12 5v14M5 12h14"/></svg>
                                    <span>Lưu Phim Hay Tháng</span>
                                </button>
                            </div>
                        </form>
                    </article>

                    <!-- LIST CARD -->
                    <section class="panel-card" style="padding: 20px;">
                        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
                            <h2 style="font-size:14px;font-weight:600;color:#1A1A21;margin:0;display:flex;align-items:center;gap:8px;">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px;color:#6D28D9;"><path d="M4 6h16M4 10h16M4 14h16M4 18h16"/></svg>
                                <span>Danh sách Phim Hay Tháng</span>
                            </h2>
                            <button type="button" class="btn-primary" style="padding:6px 14px;font-size:12px;display:inline-flex;align-items:center;gap:4px;" onclick="toggleFormCard('phimhay')">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M12 5v14M5 12h14"/></svg>
                                <span>+ Thêm phim hay tháng</span>
                            </button>
                        </div>
                        <div id="list-phimhay"></div>
                    </section>
                </div>
                </c:if>

                <!-- Hidden delete form -->
                <form id="delete-form" method="post" action="${pageContext.request.contextPath}/admin/films?tab=custom" style="display:none;">
                    <cb:csrf/>
                    <input type="hidden" name="type" id="delete-type">
                    <input type="hidden" name="sub" id="delete-sub">
                    <input type="hidden" name="action" value="delete">
                    <input type="hidden" name="index" id="delete-index">
                </form>
                </c:if>
            </div>
        </main>
    </div>

    <!-- Client-side script to render JSON and handle updates -->
    <script id="cinetags-data-json" type="application/json">${empty cinetagsJson ? '[]' : cinetagsJson}</script>
    <script id="corner-data-json" type="application/json">${empty cornerItemsJson ? '[]' : cornerItemsJson}</script>
    <script id="events-data-json" type="application/json">${empty eventsJson ? '[]' : eventsJson}</script>
    <script id="films-map-json" type="application/json">
        {
            <c:forEach var="f" items="${films}" varStatus="status">
                "${f.id}": {
                    "id": ${f.id},
                    "title": "${fn:escapeXml(f.title)}",
                    "banner": "${fn:escapeXml(cbf:assetUrl(pageContext.request.contextPath, empty f.banner ? (empty f.thumbnail ? '/assets/img/hero-banner.png' : f.thumbnail) : f.banner))}",
                    "releaseDate": "${fn:escapeXml(f.releaseDate)}",
                    "endDate": "${fn:escapeXml(f.endDate)}",
                    "directors": "${fn:escapeXml(f.directors)}",
                    "description": "${fn:escapeXml(f.description)}"
                }${not status.last ? ',' : ''}
            </c:forEach>
        }
    </script>

    <script>
        var cinetagsData = JSON.parse(document.getElementById('cinetags-data-json').textContent || '[]');
        var cornerItemsData = JSON.parse(document.getElementById('corner-data-json').textContent || '[]');
        var eventsData = JSON.parse(document.getElementById('events-data-json').textContent || '[]');
        var filmsMap = JSON.parse(document.getElementById('films-map-json').textContent || '{}');
        var CTX = '${pageContext.request.contextPath}';
        var DEFAULT_IMG = '';
        var managerPromotionOnly = ${managerPromotionOnly};

        function assetUrl(value) {
            if (!value) return '';
            var raw = String(value).trim();
            if (!raw) return '';
            if (/^https?:\/\//i.test(raw) || raw.indexOf('//') === 0) return raw;
            if (/^[a-z][a-z0-9+.-]*:/i.test(raw)) return '';
            if (raw === CTX || raw.indexOf(CTX + '/') === 0) return raw;
            return CTX + (raw.charAt(0) === '/' ? raw : '/' + raw);
        }

        function updateFileName(input, infoId) {
            var info = document.getElementById(infoId);
            if (input.files && input.files[0]) {
                info.innerHTML = '<strong>Đã chọn file:</strong> ' + esc(input.files[0].name);
            }
        }

        function onPhimHayFilmChange(selectEl) {
            var container = document.getElementById('phimhay-poster-demo');
            if (!container) return;
            if (!selectEl || !selectEl.value) {
                container.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="26" height="26" style="color:#94A3B8;"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>'
                    + '<div style="font-size: 12px; font-weight: 600; color: #475569;">Bản xem trước Poster ngang</div>'
                    + '<div style="font-size: 11px; color: #94A3B8;">Chọn phim ở trên để xem trước Poster ngang 16:9 sẽ hiển thị trên trang người dùng.</div>';
                return;
            }
            var selectedOpt = selectEl.options[selectEl.selectedIndex];
            if (!selectedOpt) return;
            var title = selectedOpt.dataset.title || '';
            var banner = selectedOpt.dataset.banner || '';
            var date = selectedOpt.dataset.date || '';
            var status = selectedOpt.dataset.status || '';

            var bannerSrc = banner ? esc(banner) : (CTX + '/assets/img/hero-banner.png');

            container.innerHTML = '<div style="max-width:440px; border:1px solid #E2E8F0; border-radius:10px; overflow:hidden; background:#fff; text-align:left; box-shadow:0 2px 8px rgba(0,0,0,0.06);">'
                + '<div style="position:relative; width:100%; aspect-ratio:16/9; background:#0F172A; overflow:hidden;">'
                + '<img src="' + bannerSrc + '" alt="' + esc(title) + '" style="width:100%; height:100%; object-fit:cover; display:block;" onerror="this.onerror=null; this.src=\'' + CTX + '/assets/img/hero-banner.png\';">'
                + '<span style="position:absolute; top:8px; left:8px; background:rgba(109,40,217,0.9); color:#fff; font-size:10px; font-weight:700; padding:2px 8px; border-radius:12px; text-transform:uppercase;">' + esc(status) + '</span>'
                + '</div>'
                + '<div style="padding:10px 14px; background:#fff;">'
                + '<div style="font-size:14px; font-weight:700; color:#1A1A21; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">' + esc(title) + '</div>'
                + '<div style="font-size:11px; color:#64748B; margin-top:2px;">Khởi chiếu: ' + esc(date) + ' • <span style="color:#6D28D9; font-weight:600;">Poster ngang chuẩn từ DB</span></div>'
                + '</div></div>';
        }

        // Switch workspace tabs
        function switchTab(tabId, btnEl) {
            document.querySelectorAll('.tab-btn').forEach(function(btn) { btn.classList.remove('active'); });
            document.querySelectorAll('.tab-pane').forEach(function(pane) { pane.classList.remove('active'); });
            if (btnEl) {
                btnEl.classList.add('active');
            } else {
                document.querySelectorAll('.tab-btn').forEach(function(btn) {
                    if (btn.getAttribute('onclick') && btn.getAttribute('onclick').includes("'" + tabId + "'")) {
                        btn.classList.add('active');
                    }
                });
            }
            var targetPane = document.getElementById('pane-' + tabId);
            if (targetPane) {
                targetPane.classList.add('active');
            }
        }

        function populateCineTagSelectOptions() {
            var select = document.getElementById('tag-cinetag-select');
            if (!select) return;

            var currentSelection = select.value;
            var defaultTags = [
                { slug: 'movie-verse', name: 'movie-verse (Phụ kiện Phim ảnh)' },
                { slug: 'fan-wibu', name: 'fan-wibu (Phụ kiện Anime/Manga)' },
                { slug: 'inner-child', name: 'inner-child (Tuổi thơ / Đồ chơi)' },
                { slug: 'yolo', name: 'yolo (#Yolo Phụ kiện độc lạ)' }
            ];

            var customTagsMap = {};
            for (var i = 0; i < cinetagsData.length; i++) {
                var t = cinetagsData[i].tag;
                if (t && t.trim()) {
                    var slug = t.trim().toLowerCase();
                    var isDefault = defaultTags.some(function(dt) { return dt.slug === slug; });
                    if (!isDefault) {
                        customTagsMap[slug] = t.trim();
                    }
                }
            }

            var html = '';
            defaultTags.forEach(function(dt) {
                html += '<option value="' + esc(dt.slug) + '">' + esc(dt.name) + '</option>';
            });

            for (var slug in customTagsMap) {
                html += '<option value="' + esc(slug) + '">' + esc(customTagsMap[slug]) + ' (Tag tùy chỉnh)</option>';
            }

            html += '<option value="__NEW__" style="color:#6D28D9;font-weight:bold;">+ Thêm loại Tag mới...</option>';
            select.innerHTML = html;

            if (currentSelection && Array.from(select.options).some(function(opt){ return opt.value === currentSelection; })) {
                select.value = currentSelection;
            }
        }

        function handleTagSelectChange(val) {
            var container = document.getElementById('custom-tag-container-cinetag');
            var customInput = document.getElementById('custom-tag-input-cinetag');
            var hiddenTag = document.getElementById('tag-cinetag');

            if (val === '__NEW__') {
                if (container) container.style.display = 'block';
                if (customInput) {
                    customInput.value = '';
                    customInput.focus();
                }
                hiddenTag.value = '';
            } else {
                if (container) container.style.display = 'none';
                hiddenTag.value = val;
            }
        }

        function updateCustomTagValue(txt) {
            var hiddenTag = document.getElementById('tag-cinetag');
            var slug = txt.toLowerCase().trim().replace(/\s+/g, '-');
            hiddenTag.value = slug || txt.trim();
        }

        function saveCustomTag() {
            var input = document.getElementById('custom-tag-input-cinetag');
            var select = document.getElementById('tag-cinetag-select');
            var hiddenTag = document.getElementById('tag-cinetag');
            var container = document.getElementById('custom-tag-container-cinetag');
            var feedback = document.getElementById('custom-tag-feedback');

            var rawVal = input ? input.value.trim() : '';
            if (!rawVal) {
                alert('Vui lòng nhập tên loại Tag mới!');
                if (input) input.focus();
                return;
            }

            var slug = rawVal.toLowerCase().replace(/\s+/g, '-');
            var displayTitle = rawVal + ' (Tag mới)';

            var optionsArr = Array.prototype.slice.call(select.options);
            var existingOpt = optionsArr.find(function(opt) {
                return opt.value.toLowerCase() === slug.toLowerCase();
            });

            if (!existingOpt) {
                var newOpt = document.createElement('option');
                newOpt.value = slug;
                newOpt.textContent = displayTitle;

                var newOptMarker = optionsArr.find(function(opt) {
                    return opt.value === '__NEW__';
                });
                if (newOptMarker) {
                    select.insertBefore(newOpt, newOptMarker);
                } else {
                    select.appendChild(newOpt);
                }
                select.value = slug;
            } else {
                existingOpt.selected = true;
                select.value = existingOpt.value;
                slug = existingOpt.value;
            }

            hiddenTag.value = slug;

            if (feedback) {
                feedback.style.display = 'block';
                feedback.textContent = '✓ Đã tạo và chọn loại Tag "' + rawVal + '"!';
                setTimeout(function() {
                    feedback.style.display = 'none';
                }, 3500);
            }

            if (container) {
                container.style.display = 'none';
            }
        }

        window.addEventListener('DOMContentLoaded', function() {
            populateCineTagSelectOptions();

            if (managerPromotionOnly) {
                switchTab('uudai');
                return;
            }

            var params = new URLSearchParams(window.location.search);
            var sub = params.get('sub');
            if (sub === 'gocdienanh' || sub === 'corner') {
                switchTab('gocdienanh');
            } else if (sub === 'phimhay' || (sub === 'event' && params.get('section') === 'phim-hay')) {
                switchTab('phimhay');
            } else if (sub === 'uudai' || sub === 'event') {
                switchTab('uudai');
            } else if (sub === 'cinetag') {
                switchTab('cinetag');
            }
        });

        // Reset inputs on form
        function resetForm(type) {
            document.getElementById('index-' + type).value = "-1";
            if (type === 'cinetag') {
                document.getElementById('form-title-cinetag').textContent = "Thêm vật phẩm CineTag#";
                populateCineTagSelectOptions();
                var select = document.getElementById('tag-cinetag-select');
                select.value = 'movie-verse';
                handleTagSelectChange('movie-verse');
                document.getElementById('name-cinetag').value = "";
                document.getElementById('price-cinetag').value = "";
                document.getElementById('image-cinetag').value = "";
                document.getElementById('fileName-cinetag').innerHTML = "JPG, PNG, WEBP (tối đa 5MB) • Kích thước đề xuất: 800×1200px";
            } else if (type === 'gocdienanh') {
                document.getElementById('form-title-gocdienanh').textContent = "Thêm Mục Góc Điện Ảnh";
                document.getElementById('section-gocdienanh').value = "the-loai";
                document.getElementById('title-gocdienanh').value = "";
                document.getElementById('prefix-gocdienanh').value = "";
                document.getElementById('desc-gocdienanh').value = "";
                document.getElementById('likes-gocdienanh').value = "0";
                document.getElementById('views-gocdienanh').value = "0";
                document.getElementById('image-gocdienanh').value = "";
                document.getElementById('fileName-gocdienanh').innerHTML = "JPG, PNG, WEBP (tối đa 5MB) • Kích thước đề xuất: 800×800px hoặc 1200×675px";
            } else if (type === 'uudai') {
                document.getElementById('form-title-uudai').textContent = "Thêm Ưu Đãi Khuyến Mãi";
                document.getElementById('title-uudai').value = "";
                document.getElementById('targetUrl-uudai').value = "";
                document.getElementById('desc-uudai').value = "";
                document.getElementById('image-uudai').value = "";
                document.getElementById('fileName-uudai').innerHTML = "JPG, PNG, WEBP (tối đa 5MB) • Kích thước đề xuất: 1200×675px";
            } else if (type === 'phimhay') {
                document.getElementById('form-title-phimhay').textContent = "Thêm Phim Hay Tháng";
                var filmSelect = document.getElementById('filmId-phimhay');
                if (filmSelect) filmSelect.value = "";
                var descInput = document.getElementById('desc-phimhay');
                if (descInput) descInput.value = "";
                onPhimHayFilmChange(filmSelect);
            }
        }

        // Toggle & Open Form Cards
        function toggleFormCard(type) {
            var card = document.getElementById('form-card-' + type);
            if (!card) return;
            var isHidden = card.style.display === 'none' || getComputedStyle(card).display === 'none';
            if (isHidden) {
                resetForm(type);
                card.style.display = 'block';
                card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            } else {
                card.style.display = 'none';
                resetForm(type);
            }
        }

        function openFormCard(type) {
            var card = document.getElementById('form-card-' + type);
            if (!card) return;
            card.style.display = 'block';
            card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }

        function closeFormCard(type) {
            var card = document.getElementById('form-card-' + type);
            if (card) {
                card.style.display = 'none';
            }
            resetForm(type);
        }

        function toggleFormCardForActiveTab() {
            var activePane = document.querySelector('.tab-pane.active');
            if (!activePane) return;
            var type = activePane.id.replace('pane-', '');
            toggleFormCard(type);
        }

        // Populate form fields for editing
        function editItem(type, index) {
            openFormCard(type);
            if (type === 'cinetag') {
                document.getElementById('index-cinetag').value = index;
                document.getElementById('form-title-cinetag').textContent = "Chỉnh sửa vật phẩm CineTag#";
                var item = cinetagsData[index];
                
                populateCineTagSelectOptions();
                var select = document.getElementById('tag-cinetag-select');
                var container = document.getElementById('custom-tag-container-cinetag');
                var customInput = document.getElementById('custom-tag-input-cinetag');
                var hiddenTag = document.getElementById('tag-cinetag');

                hiddenTag.value = item.tag || 'movie-verse';
                var itemTagSlug = (item.tag || '').toLowerCase().trim();
                var existsInSelect = false;
                for (var i = 0; i < select.options.length; i++) {
                    if (select.options[i].value.toLowerCase() === itemTagSlug && select.options[i].value !== '__NEW__') {
                        select.value = select.options[i].value;
                        existsInSelect = true;
                        break;
                    }
                }
                if (!existsInSelect) {
                    select.value = '__NEW__';
                    if (container) container.style.display = 'block';
                    if (customInput) customInput.value = item.tag || '';
                } else {
                    if (container) container.style.display = 'none';
                    if (customInput) customInput.value = '';
                }

                document.getElementById('name-cinetag').value = item.name;
                document.getElementById('price-cinetag').value = item.price;
                document.getElementById('image-cinetag').value = item.imageUrl || "";
                var card = document.getElementById('form-card-cinetag');
                if (card) card.scrollIntoView({ behavior: 'smooth' });
            } else if (type === 'gocdienanh') {
                document.getElementById('index-gocdienanh').value = index;
                document.getElementById('form-title-gocdienanh').textContent = "Chỉnh sửa bài viết Góc Điện Ảnh";
                var item = cornerItemsData[index];
                document.getElementById('section-gocdienanh').value = item.section || 'the-loai';
                document.getElementById('title-gocdienanh').value = item.title || "";
                document.getElementById('prefix-gocdienanh').value = item.prefix || item.subtitle || "";
                document.getElementById('desc-gocdienanh').value = item.description || "";
                document.getElementById('likes-gocdienanh').value = item.likes || 0;
                document.getElementById('views-gocdienanh').value = item.views || 0;
                document.getElementById('image-gocdienanh').value = item.imageUrl || "";
                var card = document.getElementById('form-card-gocdienanh');
                if (card) card.scrollIntoView({ behavior: 'smooth' });
            } else if (type === 'uudai') {
                document.getElementById('index-uudai').value = index;
                document.getElementById('form-title-uudai').textContent = "Chỉnh sửa Ưu Đãi Khuyến Mãi";
                var item = eventsData[index];
                document.getElementById('title-uudai').value = item.title;
                document.getElementById('targetUrl-uudai').value = item.targetUrl || "";
                document.getElementById('desc-uudai').value = item.description || "";
                document.getElementById('image-uudai').value = item.imageUrl || "";
                var card = document.getElementById('form-card-uudai');
                if (card) card.scrollIntoView({ behavior: 'smooth' });
            } else if (type === 'phimhay') {
                document.getElementById('index-phimhay').value = index;
                document.getElementById('form-title-phimhay').textContent = "Chỉnh sửa Phim Hay Tháng";
                var item = eventsData[index];
                var filmSelect = document.getElementById('filmId-phimhay');
                if (filmSelect) filmSelect.value = item.filmId || "";
                var descInput = document.getElementById('desc-phimhay');
                if (descInput) descInput.value = item.description || "";
                onPhimHayFilmChange(filmSelect);
                var card = document.getElementById('form-card-phimhay');
                if (card) card.scrollIntoView({ behavior: 'smooth' });
            }
        }

        // Trigger item deletion with minimalist popup
        function deleteItem(type, index, sub) {
            showAdminConfirm({
                title: 'Xóa mục khỏi hệ thống',
                message: 'Bạn có chắc chắn muốn xóa mục này khỏi cơ sở dữ liệu không?',
                subnote: 'Lưu ý: Dữ liệu này sẽ bị xóa vĩnh viễn và không thể khôi phục.',
                confirmText: 'Xóa ngay',
                cancelText: 'Hủy bỏ',
                isDanger: true,
                onConfirm: function() {
                    document.getElementById('delete-type').value = type;
                    document.getElementById('delete-sub').value = sub || type;
                    document.getElementById('delete-index').value = index;
                    document.getElementById('delete-form').submit();
                }
            });
        }

        function esc(str) {
            if (!str) return '';
            return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
        }

        function contentImage(value, alt) {
            var src = assetUrl(value);
            if (!src) {
                return '<div style="width:48px;height:48px;border-radius:6px;background:#F1F5F9;display:grid;place-items:center;font-size:10px;color:#94A3B8;border:1px solid #E2E8F0;">Không ảnh</div>';
            }
            return '<img src="' + esc(src) + '" alt="' + esc(alt || '') + '" style="width:48px;height:48px;object-fit:cover;border-radius:6px;border:1px solid #E2E8F0;">';
        }

        function contentBannerImage(value, alt) {
            var src = assetUrl(value);
            if (!src) {
                return '<div style="width:100px;height:56px;border-radius:6px;background:#F1F5F9;display:grid;place-items:center;font-size:10px;color:#94A3B8;border:1px solid #E2E8F0;">Không ảnh</div>';
            }
            return '<img src="' + esc(src) + '" alt="' + esc(alt || '') + '" style="width:100px;height:56px;object-fit:cover;border-radius:6px;border:1px solid #E2E8F0;display:block;" onerror="this.onerror=null; this.src=\'' + CTX + '/assets/img/hero-banner.png\';">';
        }

        function contentImageSource(item) {
            return assetUrl(item.imageUrl) || DEFAULT_IMG;
        }

        function contentTable(headers, rows, colspan, emptyText) {
            var body = rows || '<tr><td colspan="' + colspan + '" style="text-align:center;padding:30px;color:#8A8A96;">' + esc(emptyText) + '</td></tr>';
            return '<div class="table-responsive"><table class="table-data">'
                + '<thead><tr>' + headers + '</tr></thead><tbody>' + body + '</tbody></table></div>';
        }

        // Render CineTag List
        function renderCineTags() {
            var list = document.getElementById('list-cinetag');
            if (!list) return;
            var html = "";
            for (var i = 0; i < cinetagsData.length; i++) {
                var item = cinetagsData[i];
                var price = item.price === null || item.price === undefined || item.price === ''
                    ? '—' : Number(item.price).toLocaleString('vi-VN') + ' đ';
                html += '<tr>'
                    + '<td>' + contentImage(contentImageSource(item), item.name) + '</td>'
                    + '<td><strong style="font-size:14px;color:#1A1A21;">' + esc(item.name || '—') + '</strong></td>'
                    + '<td><span class="badge-tag primary">' + esc(item.tag || '—') + '</span></td>'
                    + '<td><strong style="color:#6D28D9;">' + esc(price) + '</strong></td>'
                    + '<td style="text-align:right;"><div style="display:flex;gap:6px;justify-content:flex-end;">'
                    + '<button type="button" class="button secondary" style="padding:4px 10px;font-size:12px;" onclick="editItem(\'cinetag\',' + i + ')">Sửa</button>'
                    + '<button type="button" class="button danger" style="padding:4px 10px;font-size:12px;" onclick="deleteItem(\'cinetag\',' + i + ',\'cinetag\')">Xóa</button>'
                    + '</div></td></tr>';
            }
            list.innerHTML = contentTable('<th scope="col">Ảnh</th><th scope="col">Vật phẩm</th><th scope="col">CineTag</th><th scope="col">Giá bán</th><th scope="col" style="text-align:right;">Thao tác</th>', html, 5, 'Chưa có vật phẩm tự chọn nào.');
        }

        // Render Goc Dien Anh List
        function renderGocDienAnh() {
            var list = document.getElementById('list-gocdienanh');
            if (!list) return;
            var html = "";
            var secNames = {
                'the-loai': 'Thể Loại Phim',
                'dien-vien': 'Diễn Viên',
                'dao-dien': 'Đạo Diễn',
                'binh-luan': 'Bình Luận Phim',
                'blog': 'Blog Điện Ảnh'
            };
            for (var i = 0; i < cornerItemsData.length; i++) {
                var item = cornerItemsData[i];
                var secLabel = secNames[item.section] || item.section || 'Góc Điện Ảnh';
                var prefixTag = (item.prefix || item.subtitle) ? '<span class="badge-tag primary" style="margin-right:6px;">' + esc(item.prefix || item.subtitle) + '</span>' : '';
                html += '<tr>'
                    + '<td>' + contentImage(contentImageSource(item), item.title) + '</td>'
                    + '<td>' + prefixTag + '<strong style="font-size:14px;color:#1A1A21;">' + esc(item.title || '—') + '</strong><div style="font-size:12px;color:#6E6E7A;margin-top:2px;" class="admin-clamp-2">' + esc(item.description || '—') + '</div></td>'
                    + '<td><span class="badge-tag">' + esc(secLabel) + '</span></td>'
                    + '<td><div style="font-size:12px;color:#475569;">👁️ ' + (item.views || 0) + ' lượt xem<br>👍 ' + (item.likes || 0) + ' thích</div></td>'
                    + '<td style="text-align:right;"><div style="display:flex;gap:6px;justify-content:flex-end;">'
                    + '<button type="button" class="button secondary" style="padding:4px 10px;font-size:12px;" onclick="editItem(\'gocdienanh\',' + i + ')">Sửa</button>'
                    + '<button type="button" class="button danger" style="padding:4px 10px;font-size:12px;" onclick="deleteItem(\'corner\',' + i + ',\'gocdienanh\')">Xóa</button>'
                    + '</div></td></tr>';
            }
            list.innerHTML = contentTable('<th scope="col">Ảnh</th><th scope="col">Bài viết / Tiêu đề</th><th scope="col">Chuyên mục</th><th scope="col">Tương tác</th><th scope="col" style="text-align:right;">Thao tác</th>', html, 5, 'Chưa có bài viết Góc Điện Ảnh nào.');
        }

        // Render Uu Dai List
        function renderUuDai() {
            var list = document.getElementById('list-uudai');
            if (!list) return;
            var html = "";
            for (var i = 0; i < eventsData.length; i++) {
                var item = eventsData[i];
                if (item.section && item.section !== 'uu-dai') continue;
                html += '<tr>'
                    + '<td>' + contentImage(contentImageSource(item), item.title) + '</td>'
                    + '<td><strong style="font-size:14px;color:#1A1A21;">' + esc(item.title || '—') + '</strong><div style="font-size:12px;color:#6E6E7A;margin-top:2px;" class="admin-clamp-2">' + esc(item.description || '—') + '</div></td>'
                    + '<td><code style="font-size:12px;background:#F1F5F9;padding:2px 6px;border-radius:4px;color:#334155;">' + esc(item.targetUrl || '—') + '</code></td>'
                    + '<td style="text-align:right;"><div style="display:flex;gap:6px;justify-content:flex-end;">'
                    + '<button type="button" class="button secondary" style="padding:4px 10px;font-size:12px;" onclick="editItem(\'uudai\',' + i + ')">Sửa</button>'
                    + '<button type="button" class="button danger" style="padding:4px 10px;font-size:12px;" onclick="deleteItem(\'event\',' + i + ',\'uudai\')">Xóa</button>'
                    + '</div></td></tr>';
            }
            list.innerHTML = contentTable('<th scope="col">Ảnh</th><th scope="col">Ưu đãi</th><th scope="col">Đường dẫn</th><th scope="col" style="text-align:right;">Thao tác</th>', html, 4, 'Chưa có Ưu Đãi nào.');
        }

        // Render Phim Hay List
        function renderPhimHay() {
            var list = document.getElementById('list-phimhay');
            if (!list) return;
            var html = "";
            for (var i = 0; i < eventsData.length; i++) {
                var item = eventsData[i];
                if (item.section !== 'phim-hay') continue;
                var film = item.filmId ? filmsMap[item.filmId] : null;

                var title = item.title || (film ? film.title : '—');
                var img = item.imageUrl || (film ? film.banner : '');
                var desc = item.description || (film ? film.description : '');
                var releaseDate = film ? film.releaseDate : '';
                var endDate = film && film.endDate ? film.endDate : '';
                var directors = film && film.directors ? film.directors : '';

                var datesText = (releaseDate ? 'Khởi chiếu: ' + esc(releaseDate) : '')
                    + (endDate ? ' • Kết thúc: ' + esc(endDate) : '');
                var directorText = directors ? ' • Đạo diễn: ' + esc(directors) : '';

                html += '<tr>'
                    + '<td>' + contentBannerImage(img, title) + '</td>'
                    + '<td><strong style="font-size:14px;color:#1A1A21;">' + esc(title) + '</strong>'
                    + '<div style="font-size:12px;color:#475569;margin-top:2px;" class="admin-clamp-2">' + esc(desc || '—') + '</div>'
                    + '<div style="font-size:11px;color:#6D28D9;margin-top:4px;font-weight:600;">' + datesText + directorText + '</div></td>'
                    + '<td>' + (item.filmId ? '<strong style="font-family:monospace;color:#6D28D9;">#' + esc(item.filmId) + '</strong>' : '—') + '</td>'
                    + '<td style="text-align:right;"><div style="display:flex;gap:6px;justify-content:flex-end;">'
                    + '<button type="button" class="button secondary" style="padding:4px 10px;font-size:12px;" onclick="editItem(\'phimhay\',' + i + ')">Sửa</button>'
                    + '<button type="button" class="button danger" style="padding:4px 10px;font-size:12px;" onclick="deleteItem(\'event\',' + i + ',\'phimhay\')">Xóa</button>'
                    + '</div></td></tr>';
            }
            list.innerHTML = contentTable('<th scope="col">Ảnh</th><th scope="col">Phim hay tháng</th><th scope="col">Mã phim</th><th scope="col" style="text-align:right;">Thao tác</th>', html, 4, 'Chưa có Phim Hay Tháng nào.');
        }

        // Render all lists on load
        window.addEventListener('DOMContentLoaded', function() {
            renderCineTags();
            renderGocDienAnh();
            renderUuDai();
            renderPhimHay();
        });
    </script>
</body>
</html>
