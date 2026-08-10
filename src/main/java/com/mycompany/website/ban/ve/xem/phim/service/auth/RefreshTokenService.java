package com.mycompany.website.ban.ve.xem.phim.service.auth;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Vong doi refresh token opaque co xoay vong va phat hien replay
 * (FLOW-AUTH-REF-001..011).
 *
 * <p><b>Token la gi.</b> 32 byte tu {@link SecureRandom}, ma hoa Base64URL khong padding.
 * Khong phai JWT: khong co gi de doc, khong co gi de gia mao, va quyen luc cua no nam hoan
 * toan o dong tuong ung trong DB. Doi lai, moi lan dung deu phai tra cuu DB — day chinh la
 * dieu kien can de thu hoi duoc tuc thi.</p>
 *
 * <p><b>DB chi luu hash.</b> {@code TokenHash} la SHA-256 hex cua token. Khac voi mat khau,
 * o day khong dung BCrypt: token da co 256 bit entropy ngau nhien nen khong the do duoc, va
 * tra cuu phai chay tren mot lan doc index — mot KDF cham se bien moi lan refresh thanh mot
 * phep tinh nang.</p>
 *
 * <p><b>Xoay vong va replay.</b> Moi lan refresh: token cu bi danh dau {@code ConsumedAt},
 * mot token moi cung {@code FamilyId} duoc phat, va {@code ReplacedById} noi hai the he lai.
 * Dung lai mot token da {@code ConsumedAt}/{@code RevokedAt} la bang chung token da bi sao
 * chep — khi do <b>ca family bi thu hoi</b>, ke cap va nguoi dung that cung bi dang xuat.
 * Bat nguoi dung that dang nhap lai la cai gia dung de doi lay viec cat duoc phien cua ke cap.</p>
 *
 * <p><b>Dong thoi.</b> Buoc doc dung {@code WITH (UPDLOCK, ROWLOCK)} trong mot transaction,
 * nen N request cung mot token bi xep hang: dung mot request thay token con nguyen va thang,
 * so con lai thay {@code ConsumedAt} da co gia tri va roi vao nhanh replay. Khong dua vao
 * kiem tra o tang Java — hai JVM khong thay duoc bien cua nhau.</p>
 */
public final class RefreshTokenService {
    private static final Logger LOG = Logger.getLogger(RefreshTokenService.class.getName());

    /** Han tuyet doi cua mot family, tinh tu lan dang nhap. Design muc 6 chot 30 ngay. */
    public static final int ABSOLUTE_LIFETIME_DAYS = 30;
    /** So byte ngau nhien trong mot token — 32 byte = 256 bit. */
    public static final int TOKEN_BYTES = 32;

    public static final String COOKIE_NAME = "refresh_token";
    /**
     * Cookie chi duoc gui kem cac endpoint auth. Duong dan hep lai theo dung design muc 6:
     * mot credential dai han khong co ly do gi de di kem moi request tinh cua ung dung.
     */
    public static final String COOKIE_PATH = "/api/v1/auth";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /** Token vua phat: gia tri tho <b>chi</b> tra ve cho client, khong ghi log, khong luu DB. */
    public record IssuedRefreshToken(String token, int userId, String familyId) {
    }

    /**
     * Cap mot family moi. Goi sau khi xac thuc thanh cong.
     *
     * @param clientIp dia chi ghi kem de dieu tra su co; khong dung de xac thuc
     */
    public IssuedRefreshToken issue(int userId, String clientIp) {
        String token = randomToken();
        String familyId = UUID.randomUUID().toString();
        try (Connection connection = DBConnection.getConnection()) {
            insert(connection, userId, familyId, hash(token), clientIp);
            return new IssuedRefreshToken(token, userId, familyId);
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the cap phien dang nhap.", ex);
        }
    }

    /**
     * Doi mot refresh token lay the he ke tiep.
     *
     * @return token moi khi hop le; {@link Optional#empty()} cho moi truong hop tu choi
     *         (khong ton tai, het han, da dung, da thu hoi). Goi ben tra 401 cho tat ca —
     *         phan biet ly do la giup ke tan cong do trang thai token.
     */
    public Optional<IssuedRefreshToken> rotate(String presentedToken, String clientIp) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return Optional.empty();
        }
        String presentedHash = hash(presentedToken);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Existing existing = lockByHash(connection, presentedHash);
                if (existing == null) {
                    connection.commit();
                    return Optional.empty();
                }
                if (existing.spent()) {
                    // Replay: token nay da duoc dung mot lan roi. Ai do dang giu ban sao.
                    int revoked = revokeFamily(connection, existing.familyId(), "replay-detected");
                    connection.commit();
                    LOG.log(Level.WARNING,
                            "Phat hien tai su dung refresh token: thu hoi {0} token cua family {1} (user {2})",
                            new Object[] {revoked, existing.familyId(), existing.userId()});
                    return Optional.empty();
                }
                if (existing.expired()) {
                    revokeFamily(connection, existing.familyId(), "expired");
                    connection.commit();
                    return Optional.empty();
                }

                String nextToken = randomToken();
                int nextId = insert(connection, existing.userId(), existing.familyId(),
                        hash(nextToken), clientIp);
                consume(connection, existing.id(), nextId);
                connection.commit();
                return Optional.of(new IssuedRefreshToken(
                        nextToken, existing.userId(), existing.familyId()));
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the lam moi phien dang nhap.", ex);
        }
    }

    /** Thu hoi moi family con hieu luc cua mot user (logout-all, doi mat khau, khoa tai khoan). */
    public int revokeAllForUser(int userId, String reason) {
        String sql = """
                UPDATE RefreshTokens
                SET RevokedAt = SYSDATETIME(), RevocationReason = ?
                WHERE UserId = ? AND RevokedAt IS NULL
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setInt(2, userId);
            return ps.executeUpdate();
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the thu hoi phien dang nhap.", ex);
        }
    }

    /** Thu hoi dung family cua token dang cam (logout mot thiet bi). Im lang neu token la. */
    public void revokeFamilyOf(String presentedToken, String reason) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return;
        }
        String sql = """
                UPDATE RefreshTokens
                SET RevokedAt = SYSDATETIME(), RevocationReason = ?
                WHERE RevokedAt IS NULL
                  AND FamilyId = (SELECT TOP(1) FamilyId FROM RefreshTokens WHERE TokenHash = ?)
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setString(2, hash(presentedToken));
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the thu hoi phien dang nhap.", ex);
        }
    }

    /** Chu so huu cua mot token con hieu luc — dung de xac thuc logout khi khong con session. */
    public Optional<Integer> ownerOfUsableToken(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return Optional.empty();
        }
        String sql = """
                SELECT UserId FROM RefreshTokens
                WHERE TokenHash = ? AND RevokedAt IS NULL AND ConsumedAt IS NULL
                  AND ExpiresAt > SYSDATETIME()
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, hash(presentedToken));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the kiem tra phien dang nhap.", ex);
        }
    }

    // ------------------------------------------------------------------- SQL

    private record Existing(int id, int userId, String familyId, boolean spent, boolean expired) {
    }

    private Existing lockByHash(Connection connection, String tokenHash) throws SQLException {
        String sql = """
                SELECT Id, UserId, FamilyId,
                       CASE WHEN ConsumedAt IS NOT NULL OR RevokedAt IS NOT NULL THEN 1 ELSE 0 END AS Spent,
                       CASE WHEN ExpiresAt <= SYSDATETIME() THEN 1 ELSE 0 END AS Expired
                FROM RefreshTokens WITH (UPDLOCK, ROWLOCK)
                WHERE TokenHash = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Existing(rs.getInt("Id"), rs.getInt("UserId"), rs.getString("FamilyId"),
                        rs.getInt("Spent") == 1, rs.getInt("Expired") == 1);
            }
        }
    }

    private int insert(Connection connection, int userId, String familyId, String tokenHash,
            String clientIp) throws SQLException {
        String sql = """
                INSERT INTO RefreshTokens (UserId, FamilyId, TokenHash, ExpiresAt, CreatedByIp)
                VALUES (?, ?, ?, DATEADD(DAY, ?, SYSDATETIME()), ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, familyId);
            ps.setString(3, tokenHash);
            ps.setInt(4, ABSOLUTE_LIFETIME_DAYS);
            ps.setString(5, clientIp == null ? "" : clientIp);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    /**
     * Danh dau token cu da dung. Dieu kien {@code ConsumedAt IS NULL} la hang rao thu hai sau
     * {@code UPDLOCK}: neu vi ly do nao do khoa khong giu duoc thi UPDATE nay van khong the
     * ghi de mot lan tieu thu da xay ra.
     */
    private void consume(Connection connection, int tokenId, int replacementId) throws SQLException {
        String sql = """
                UPDATE RefreshTokens
                SET ConsumedAt = SYSDATETIME(), ReplacedById = ?
                WHERE Id = ? AND ConsumedAt IS NULL AND RevokedAt IS NULL
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, replacementId);
            ps.setInt(2, tokenId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Refresh token bi tieu thu boi mot request khac: id=" + tokenId);
            }
        }
    }

    private int revokeFamily(Connection connection, String familyId, String reason) throws SQLException {
        String sql = """
                UPDATE RefreshTokens
                SET RevokedAt = SYSDATETIME(), RevocationReason = ?
                WHERE FamilyId = ? AND RevokedAt IS NULL
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setString(2, familyId);
            return ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------- primitives

    private static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** SHA-256 hex thuong, 64 ky tu — khop kieu {@code CHAR(64)} cua cot {@code TokenHash}. */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 khong kha dung", ex);
        }
    }
}
