package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Set;
import javax.json.Json;
import javax.json.JsonReader;

/** Stores the four marketing-content collections under an explicit cinema owner. */
public class CinemaContentService {
    private static final Set<String> KEYS = Set.of(
            "cinetags_data", "corner_items_data", "events_data", "special_cinemas_data");
    private static final int MAX_JSON_CHARS = 1_000_000;

    public String getContent(int cinemaId, String key) {
        requireKey(key);
        if (cinemaId <= 0) throw new BookingException(400, "Vui lòng chọn rạp để xem nội dung.");
        String sql = "SELECT ContentJson FROM CinemaContents WHERE CinemaId=? AND ContentKey=?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cinemaId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "[]";
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tải nội dung của rạp.", ex);
        }
    }

    public void saveContent(int cinemaId, String key, String json, User actor) {
        requireKey(key);
        CinemaCapabilityPolicy.requireCinema(actor, cinemaId);
        String normalized = validateArray(json);
        String sql = """
                UPDATE CinemaContents SET ContentJson=?,UpdatedByUserId=?,UpdatedAt=SYSDATETIME()
                WHERE CinemaId=? AND ContentKey=?;
                IF @@ROWCOUNT=0
                    INSERT INTO CinemaContents(CinemaId,ContentKey,ContentJson,UpdatedByUserId)
                    VALUES(?,?,?,?);
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, normalized);
            if (actor == null || actor.getId() <= 0) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, actor.getId());
            }
            ps.setInt(3, cinemaId);
            ps.setString(4, key);
            ps.setInt(5, cinemaId);
            ps.setString(6, key);
            ps.setString(7, normalized);
            if (actor == null || actor.getId() <= 0) {
                ps.setNull(8, Types.INTEGER);
            } else {
                ps.setInt(8, actor.getId());
            }
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể lưu nội dung của rạp.", ex);
        }
    }

    private String validateArray(String json) {
        String value = json == null || json.isBlank() ? "[]" : json.trim();
        if (value.length() > MAX_JSON_CHARS) {
            throw new BookingException(400, "Nội dung vượt quá dung lượng cho phép.");
        }
        try (JsonReader reader = Json.createReader(new StringReader(value))) {
            reader.readArray();
            return value;
        } catch (RuntimeException ex) {
            throw new BookingException(400, "Nội dung phải là một mảng JSON hợp lệ.");
        }
    }

    private void requireKey(String key) {
        if (!KEYS.contains(key)) throw new BookingException(400, "Loại nội dung không hợp lệ.");
    }
}
