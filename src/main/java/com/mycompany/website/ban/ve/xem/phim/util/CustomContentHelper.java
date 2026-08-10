package com.mycompany.website.ban.ve.xem.phim.util;

import com.mycompany.website.ban.ve.xem.phim.controller.CineTagServlet;
import com.mycompany.website.ban.ve.xem.phim.controller.CinemaCornerServlet;
import com.mycompany.website.ban.ve.xem.phim.controller.EventServlet;
import com.mycompany.website.ban.ve.xem.phim.controller.SpecialCinemaServlet;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.CinemaContentService;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

/**
 * Doc noi dung tuy bien (CineTag, Goc dien anh, Su kien, Rap dac biet) tu
 * {@code SystemSettings}.
 *
 * <p><b>Van de goc (CT-01).</b> Moi ham o day tung ket thuc bang mot khoi
 * "fallback to default mock data if empty": khi setting trong, hoac khi mot section khong co
 * muc nao khop, lop nay <i>tu sinh ra</i> hang chuc san pham, dien vien, su kien gia — kem anh
 * Unsplash. Hau qua thuc te ma nguoi dung gap:</p>
 * <ul>
 *   <li>Admin xoa sach du lieu trong DB nhung trang nguoi dung <b>khong doi gi ca</b> — nhin
 *       nhu chuc nang xoa bi hong.</li>
 *   <li>Admin them mot muc that vao section dang trong: mot muc do thay the ca danh sach mock,
 *       nen trang trong nhu vua "mat" du lieu.</li>
 *   <li>JSON hong thi im lang hien mock, khong ai biet cau hinh dang loi.</li>
 * </ul>
 *
 * <p><b>Nguyen tac moi.</b> DB trong thi tra danh sach <b>rong</b> va JSP hien empty state co
 * chu dinh. JSON hong thi bao loi thay vi che di. Khong con du lieu demo nao sinh ra tu code.</p>
 */
public class CustomContentHelper {
    private static final AdminService adminService = new AdminService();
    private static final CinemaContentService cinemaContentService = new CinemaContentService();
    private static final Logger LOGGER = Logger.getLogger(CustomContentHelper.class.getName());

    /**
     * Doc mang JSON tu mot setting.
     *
     * @return mang JSON, hoac {@code null} khi setting chua duoc dat (DB trong — hop le)
     * @throws BookingException khi setting co du lieu nhung khong parse duoc. Cau hinh hong la
     *         loi that: bao ra de admin sua, khong duoc am tham thay bang du lieu khac.
     */
    private static JsonArray readArraySetting(String key) {
        String json = adminService.getSettingValue(key);
        if (json == null || json.isBlank()) {
            return null;
        }
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readArray();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Du lieu JSON cua setting '" + key + "' khong hop le", ex);
            throw new BookingException(500,
                    "Dữ liệu nội dung tùy chỉnh (" + key + ") không phải JSON hợp lệ. "
                    + "Vui lòng kiểm tra lại ở Quản trị → Nội dung.");
        }
    }

    private static JsonArray readArraySetting(String key, Integer cinemaId) {
        if (cinemaId == null || cinemaId <= 0) return readArraySetting(key);
        String json = cinemaContentService.getContent(cinemaId, key);
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readArray();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Du lieu JSON theo rap cua '" + key + "' khong hop le", ex);
            throw new BookingException(500, "Dữ liệu nội dung của rạp không hợp lệ.");
        }
    }

    /** So sanh gia tri phan loai, bo qua hoa thuong va khoang trang thua. */
    private static boolean matches(String value, String expected) {
        return value != null && expected != null
                && value.trim().equalsIgnoreCase(expected.trim());
    }

    public static class TagInfo {
        private String slug;
        private String name;

        public TagInfo(String slug, String name) {
            this.slug = slug;
            this.name = name;
        }

        public String getSlug() { return slug; }
        public String getName() { return name; }
    }

    public static List<TagInfo> getAllCineTags() {
        return getAllCineTags(null);
    }

    public static List<TagInfo> getAllCineTags(Integer cinemaId) {
        List<TagInfo> tags = new ArrayList<>();
        tags.add(new TagInfo("movie-verse", "Movie-verse"));
        tags.add(new TagInfo("fan-wibu", "Fan Wibu"));
        tags.add(new TagInfo("inner-child", "Inner Child"));
        tags.add(new TagInfo("yolo", "#Yolo"));

        JsonArray array = readArraySetting("cinetags_data", cinemaId);
        if (array != null) {
            for (int i = 0; i < array.size(); i++) {
                JsonObject obj = array.getJsonObject(i);
                String rawTag = obj.getString("tag", "").trim();
                if (rawTag.isBlank()) continue;

                String slug = rawTag.toLowerCase();
                boolean exists = false;
                for (TagInfo t : tags) {
                    if (t.getSlug().equalsIgnoreCase(slug)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    tags.add(new TagInfo(slug, formatTagName(rawTag)));
                }
            }
        }
        return tags;
    }

    public static String formatTagName(String tag) {
        if (tag == null || tag.isBlank()) return "Movie-verse";
        String t = tag.toLowerCase().trim();
        switch (t) {
            case "fan-wibu": return "Fan Wibu";
            case "inner-child": return "Inner Child";
            case "yolo": return "#Yolo";
            case "movie-verse": return "Movie-verse";
            default:
                String[] words = t.replace('-', ' ').split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (String w : words) {
                    if (w.isEmpty()) continue;
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
                }
                return sb.toString();
        }
    }

    // 1. CineTags merchandise
    public static List<CineTagServlet.Product> getCineTagProducts(String tag) {
        return getCineTagProducts(tag, null);
    }

    public static List<CineTagServlet.Product> getCineTagProducts(String tag, Integer cinemaId) {
        List<CineTagServlet.Product> list = new ArrayList<>();
        JsonArray array = readArraySetting("cinetags_data", cinemaId);
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.size(); i++) {
            JsonObject obj = array.getJsonObject(i);
            if (!matches(obj.getString("tag", ""), tag)) {
                continue;
            }
            list.add(new CineTagServlet.Product(
                    obj.getString("name", ""),
                    obj.containsKey("price") ? obj.getJsonNumber("price").doubleValue() : 0d,
                    obj.getString("imageUrl", "")));
        }
        return list;
    }

    // 2. Goc Dien Anh content
    public static List<CinemaCornerServlet.CornerItem> getCornerItems(String section) {
        return getCornerItems(section, null);
    }

    public static List<CinemaCornerServlet.CornerItem> getCornerItems(String section, Integer cinemaId) {
        List<CinemaCornerServlet.CornerItem> list = new ArrayList<>();
        JsonArray array = readArraySetting("corner_items_data", cinemaId);
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.size(); i++) {
            JsonObject obj = array.getJsonObject(i);
            if (!matches(obj.getString("section", ""), section)) {
                continue;
            }
            list.add(new CinemaCornerServlet.CornerItem(
                    obj.getString("title", ""),
                    obj.getString("subtitle", ""),
                    obj.getString("imageUrl", ""),
                    obj.getString("description", ""),
                    obj.getInt("likes", 0),
                    obj.getInt("views", 0),
                    obj.containsKey("prefix") ? obj.getString("prefix") : null));
        }
        return list;
    }

    // 3. Su Kien events
    public static List<EventServlet.EventItem> getEventItems(String section) {
        return getEventItems(section, null);
    }

    public static List<EventServlet.EventItem> getEventItems(String section, Integer cinemaId) {
        List<EventServlet.EventItem> list = new ArrayList<>();
        JsonArray array = readArraySetting("events_data", cinemaId);
        if (array == null) {
            return list;
        }
        var filmDAO = new com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcFilmDAO();
        for (int i = 0; i < array.size(); i++) {
            JsonObject obj = array.getJsonObject(i);
            String itemSection = obj.getString("section", "");
            if (section != null && !section.isBlank() && !matches(itemSection, section)) {
                continue;
            }
            Integer filmId = obj.containsKey("filmId") && !obj.isNull("filmId") ? obj.getInt("filmId") : null;
            String imgUrl = obj.containsKey("imageUrl") ? obj.getString("imageUrl", "") : "";
            String title = obj.containsKey("title") ? obj.getString("title", "") : "";
            String desc = obj.containsKey("description") ? obj.getString("description", "") : "";

            String releaseDate = null;
            String endDate = null;
            String directors = null;

            if (filmId != null && filmId > 0) {
                try {
                    var filmOpt = filmDAO.findById(filmId);
                    if (filmOpt.isPresent()) {
                        var f = filmOpt.get();
                        if (title == null || title.isBlank()) {
                            title = f.getTitle();
                        }
                        if (f.getBanner() != null && !f.getBanner().isBlank()) {
                            imgUrl = f.getBanner();
                        } else if ((imgUrl == null || imgUrl.isBlank()) && f.getThumbnail() != null) {
                            imgUrl = f.getThumbnail();
                        }
                        if (desc == null || desc.isBlank()) {
                            desc = f.getDescription() != null ? f.getDescription() : "";
                        }
                        if (f.getReleaseDate() != null) {
                            releaseDate = f.getReleaseDate().toString();
                        }
                        if (f.getEndDate() != null) {
                            endDate = f.getEndDate().toString();
                        }
                        directors = f.getDirectors();
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Khong the nap thong tin phim #" + filmId + " cho event item", ex);
                }
            }

            list.add(new EventServlet.EventItem(
                    title,
                    imgUrl,
                    obj.containsKey("targetUrl") ? obj.getString("targetUrl", "") : null,
                    filmId,
                    desc,
                    itemSection,
                    releaseDate,
                    endDate,
                    directors));
        }
        return list;
    }

    // 4. Rap Dac Biet lounges
    public static List<SpecialCinemaServlet.SpecialCinema> getSpecialCinemas() {
        return getSpecialCinemas(null);
    }

    public static List<SpecialCinemaServlet.SpecialCinema> getSpecialCinemas(Integer cinemaId) {
        List<SpecialCinemaServlet.SpecialCinema> list = new ArrayList<>();
        JsonArray array = readArraySetting("special_cinemas_data", cinemaId);
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.size(); i++) {
            JsonObject obj = array.getJsonObject(i);
            list.add(new SpecialCinemaServlet.SpecialCinema(
                    obj.getString("title", ""),
                    obj.getString("address", ""),
                    obj.getString("imageUrl", ""),
                    obj.getString("description", "")));
        }
        return list;
    }

    // 5. Ve Chung Toi - Thanh vien & Gia tri cot loi
    public static class AboutUsMember {
        private int id;
        private String name;
        private String role;
        private String imageUrl;

        public AboutUsMember(int id, String name, String role, String imageUrl) {
            this.id = id;
            this.name = name;
            this.role = role;
            this.imageUrl = imageUrl;
        }
        public int getId() { return id; }
        public String getName() { return name; }
        public String getRole() { return role; }
        public String getImageUrl() { return imageUrl; }
    }

    public static class AboutUsFeature {
        private String key;
        private String title;
        private String description;
        private String icon;

        public AboutUsFeature(String key, String title, String description, String icon) {
            this.key = key;
            this.title = title;
            this.description = description;
            this.icon = icon;
        }
        public String getKey() { return key; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getIcon() { return icon; }
    }

    public static List<AboutUsMember> getAboutUsMembers() {
        List<AboutUsMember> list = new ArrayList<>();
        JsonArray array = readArraySetting("about_us_members_data");
        if (array != null && !array.isEmpty()) {
            for (int i = 0; i < array.size(); i++) {
                JsonObject obj = array.getJsonObject(i);
                list.add(new AboutUsMember(
                        obj.containsKey("id") ? obj.getInt("id", i + 1) : (i + 1),
                        obj.getString("name", "Thành viên"),
                        obj.getString("role", "Thành viên CineBook"),
                        obj.getString("imageUrl", "/images/" + (i + 1) + ".jpg")));
            }
            return list;
        }
        // Mac dinh 4 thanh vien theo dung yeu cau
        list.add(new AboutUsMember(1, "Nguyễn Minh Tâm", "Trưởng nhóm / Founder", "/images/1.jpg"));
        list.add(new AboutUsMember(2, "Lương Hoàng Dũng", "Phát triển Hệ thống / Co-Founder", "/images/2.jpg"));
        list.add(new AboutUsMember(3, "Nguyễn Vĩnh Đức", "Thiết kế & Trải nghiệm / UI UX Lead", "/images/3.jpg"));
        list.add(new AboutUsMember(4, "Nguyễn Lâm Thi", "Vận hành & Quảng bá / Operations Lead", "/images/4.jpg"));
        return list;
    }

    public static List<AboutUsFeature> getAboutUsFeatures() {
        List<AboutUsFeature> list = new ArrayList<>();
        JsonArray array = readArraySetting("about_us_features_data");
        if (array != null && !array.isEmpty()) {
            for (int i = 0; i < array.size(); i++) {
                JsonObject obj = array.getJsonObject(i);
                list.add(new AboutUsFeature(
                        obj.getString("key", "feature_" + (i + 1)),
                        obj.getString("title", "Tính năng"),
                        obj.getString("description", ""),
                        obj.getString("icon", "target")));
            }
            return list;
        }
        // Mac dinh 3 phan theo dung anh minh hoa [2]
        list.add(new AboutUsFeature("mission", "Sứ mệnh",
                "Mang đến trải nghiệm xem phim tuyệt vời thông qua công nghệ hiện đại, dịch vụ tận tâm và không ngừng đổi mới.", "target"));
        list.add(new AboutUsFeature("experience", "Trải nghiệm đặt vé",
                "Đặt vé nhanh chóng, chọn ghế dễ dàng, thanh toán tiện lợi. Mọi thao tác chỉ trong vài bước đơn giản.", "ticket"));
        list.add(new AboutUsFeature("cinemas", "Hệ thống rạp",
                "Liên kết với hàng trăm cụm rạp trên toàn quốc, mang đến nhiều lựa chọn và chất lượng phục vụ đồng nhất.", "screen"));
        return list;
    }
}
