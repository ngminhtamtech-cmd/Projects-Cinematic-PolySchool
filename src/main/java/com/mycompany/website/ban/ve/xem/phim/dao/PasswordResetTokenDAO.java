package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.model.PasswordResetToken;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Phieu dat lai mat khau (D11).
 *
 * <p>Han dung do DB tinh ({@code DATEADD} tren {@code SYSDATETIME()}), khong nhan
 * {@code LocalDateTime} tu tang Java — quy tac thoi gian chot o P03.</p>
 */
public interface PasswordResetTokenDAO {
    /** Tao phieu moi; {@code expiresInMinutes} duoc DB cong vao gio cua chinh no. */
    void create(Connection connection, String tokenHash, int userId, int expiresInMinutes, String requestIp)
            throws SQLException;

    /**
     * Tieu phieu <b>mot lan duy nhat</b>: danh dau da dung va tra ve chu so huu, tat ca trong
     * <b>mot</b> cau lenh nguyen tu. Hai request cung gui mot token thi chi mot request nhan duoc
     * user id, request con lai nhan {@link Optional#empty()}.
     *
     * @return id nguoi dung neu phieu con hieu luc; rong neu sai token / het han / da dung
     */
    Optional<Integer> consume(Connection connection, String tokenHash) throws SQLException;

    /**
     * Tao phieu moi <b>chi khi</b> nguoi dung chua xin phieu nao trong {@code cooldownSeconds} giay
     * vua qua — kiem tra va ghi nam trong <b>mot</b> cau lenh nguyen tu.
     *
     * <p><b>Vi sao khong tach SELECT roi INSERT.</b> Cung mot lop loi voi {@code consume}: hai
     * request toi cach nhau vai mili giay deu doc thay "chua co phieu nao" truoc khi ai kip ghi,
     * roi ca hai cung tao phieu va cung gui thu. Dieu kien cooldown vi vay phai nam trong chinh
     * menh de {@code WHERE NOT EXISTS} cua cau {@code INSERT}, kem {@code UPDLOCK, HOLDLOCK} de
     * SQL Server giu khoa khoang tren {@code UserId} cho toi khi commit.</p>
     *
     * <p>Moc so sanh la {@code CreatedAt}, khong phai {@code UsedAt}: mot phieu da dung van tinh
     * la mot lan gui thu, nen van phai chiu cooldown.</p>
     *
     * @param cooldownSeconds {@code 0} nghia la khong gioi han
     * @return {@code true} neu da tao phieu; {@code false} neu con trong thoi gian cho
     */
    boolean createIfOutsideCooldown(Connection connection, String tokenHash, int userId,
            int expiresInMinutes, String requestIp, int cooldownSeconds) throws SQLException;

    /** Vo hieu moi phieu chua dung cua mot nguoi dung — goi sau khi doi mat khau thanh cong. */
    int invalidateAllForUser(Connection connection, int userId) throws SQLException;

    /**
     * Vo hieu moi phieu chua dung cua mot nguoi dung <b>tru</b> phieu vua tao.
     *
     * <p>Phai goi sau {@link #createIfOutsideCooldown}, khong phai truoc. Vo hieu truoc roi moi
     * thu tao ma vuong cooldown la giet mat lien ket con han cua nguoi dung <i>ma khong cap cai
     * moi</i> — ho mat luon duong vao ma khong hieu tai sao.</p>
     */
    int invalidateOthersForUser(Connection connection, int userId, String keepTokenHash)
            throws SQLException;

    /** Chi dung cho test / chan doan. */
    Optional<PasswordResetToken> findByHash(String tokenHash);
}
