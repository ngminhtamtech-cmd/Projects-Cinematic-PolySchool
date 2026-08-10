package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.model.LoginAttemptSummary;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Nhat ky dang nhap, phuc vu chong do mat khau (D6).
 *
 * <p>Cac ham nhan {@link Connection} de nguoi goi dung <b>mot</b> connection cho ca luot kiem tra
 * + ghi nhat ky, thay vi muon 3 lan tu pool 10 (quy tac chot o P03).</p>
 */
public interface LoginAttemptDAO {
    /** Ghi lai mot lan thu dang nhap. Thoi diem do {@code SYSDATETIME()} cua DB sinh. */
    void record(Connection connection, String email, String ipAddress, boolean success) throws SQLException;

    /** Ban tu mo connection — dung khi nguoi goi khong co san. */
    void record(String email, String ipAddress, boolean success);

    /**
     * Dem so lan sai <b>sau lan dang nhap dung gan nhat</b> cua email, trong {@code windowMinutes} phut,
     * kem so giay con lai cho toi khi het chan.
     */
    LoginAttemptSummary summarizeByEmail(Connection connection, String email, int windowMinutes) throws SQLException;

    /** Nhu tren nhung theo dia chi IP — chan ke do rai tren nhieu email khac nhau. */
    LoginAttemptSummary summarizeByIp(Connection connection, String ipAddress, int windowMinutes) throws SQLException;

    /** Xoa nhat ky cua mot email. Dung cho test va cho thao tac go chan thu cong. */
    int clearForEmail(String email);
}
