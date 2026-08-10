package com.mycompany.website.ban.ve.xem.phim.dao.impl;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.PolicyDocumentDAO;
import com.mycompany.website.ban.ve.xem.phim.model.PolicyDocument;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

public class JdbcPolicyDocumentDAO implements PolicyDocumentDAO {
    @Override
    public Optional<PolicyDocument> findPublished(String policyKey) {
        return find(policyKey, "published");
    }

    @Override
    public Optional<PolicyDocument> findLatestDraft(String policyKey) {
        return find(policyKey, "draft");
    }

    @Override
    public PolicyDocument saveDraft(String policyKey, String title, String bodyText, int actorId) {
        return write(policyKey, title, bodyText, actorId, false);
    }

    @Override
    public PolicyDocument publish(String policyKey, String title, String bodyText, int actorId) {
        return write(policyKey, title, bodyText, actorId, true);
    }

    private Optional<PolicyDocument> find(String key, String status) {
        String sql = "SELECT TOP 1 Id, PolicyKey, VersionNumber, Title, BodyText, Status, UpdatedBy, UpdatedAt, PublishedAt "
                + "FROM PolicyDocuments WHERE PolicyKey = ? AND Status = ? ORDER BY VersionNumber DESC";
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to load policy document", ex);
        }
    }

    private PolicyDocument write(String key, String title, String body, int actorId, boolean publish) {
        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            throw new IllegalArgumentException("Policy title and body are required");
        }
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                int version = 1;
                try (PreparedStatement ps = c.prepareStatement("SELECT ISNULL(MAX(VersionNumber),0)+1 FROM PolicyDocuments WITH (UPDLOCK, HOLDLOCK) WHERE PolicyKey=?")) {
                    ps.setString(1, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            version = rs.getInt(1);
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE PolicyDocuments SET Status='archived' WHERE PolicyKey=? AND Status IN ('draft','published')")) {
                    ps.setString(1, key);
                    ps.executeUpdate();
                }
                int id;
                String insert = "INSERT INTO PolicyDocuments (PolicyKey, VersionNumber, Title, BodyText, Status, UpdatedBy, UpdatedAt, PublishedAt) VALUES (?,?,?,?,?,?,SYSDATETIME(),CASE WHEN ?=1 THEN SYSDATETIME() ELSE NULL END)";
                try (PreparedStatement ps = c.prepareStatement(insert, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, key);
                    ps.setInt(2, version);
                    ps.setString(3, title);
                    ps.setString(4, body);
                    ps.setString(5, publish ? "published" : "draft");
                    ps.setInt(6, actorId);
                    ps.setBoolean(7, publish);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new SQLException("Policy id missing");
                        }
                        id = rs.getInt(1);
                    }
                }
                c.commit();
                return loadById(id);
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to save policy document", ex);
        }
    }

    private PolicyDocument loadById(int id) {
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT Id, PolicyKey, VersionNumber, Title, BodyText, Status, UpdatedBy, UpdatedAt, PublishedAt FROM PolicyDocuments WHERE Id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to load policy document", ex);
        }
        throw new IllegalStateException("Policy document was not created");
    }

    private PolicyDocument map(ResultSet rs) throws SQLException {
        PolicyDocument p = new PolicyDocument();
        p.setId(rs.getInt("Id"));
        p.setPolicyKey(rs.getString("PolicyKey"));
        p.setVersionNumber(rs.getInt("VersionNumber"));
        p.setTitle(rs.getString("Title"));
        p.setBodyText(rs.getString("BodyText"));
        p.setStatus(rs.getString("Status"));
        int user = rs.getInt("UpdatedBy");
        p.setUpdatedBy(rs.wasNull() ? null : user);
        Timestamp updated = rs.getTimestamp("UpdatedAt");
        p.setUpdatedAt(updated == null ? null : updated.toLocalDateTime());
        Timestamp published = rs.getTimestamp("PublishedAt");
        p.setPublishedAt(published == null ? null : published.toLocalDateTime());
        return p;
    }
}
