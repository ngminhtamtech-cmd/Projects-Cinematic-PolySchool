package com.mycompany.website.ban.ve.xem.phim.model;

/**
 * Tom tat cac lan dang nhap that bai gan day cua mot email (hoac mot IP).
 *
 * <p>Ca hai con so deu do SQL Server tinh — {@code COUNT} va {@code DATEDIFF} chay cung mot cau
 * truy van, nen khong co chuyen dong ho may ung dung lech lam sai nguong (quy tac P03).</p>
 */
public class LoginAttemptSummary {
    private final int failures;
    private final int unlockInSeconds;

    public LoginAttemptSummary(int failures, int unlockInSeconds) {
        this.failures = failures;
        this.unlockInSeconds = Math.max(0, unlockInSeconds);
    }

    /** So lan sai lien tiep sau lan dang nhap dung gan nhat, trong cua so dang xet. */
    public int getFailures() {
        return failures;
    }

    /** Con bao nhieu giay nua thi het bi chan, tinh tu lan sai gan nhat. */
    public int getUnlockInSeconds() {
        return unlockInSeconds;
    }

    /** Lam tron len phut, de hien thi cho nguoi dung. */
    public int getUnlockInMinutes() {
        return (unlockInSeconds + 59) / 60;
    }

    public static LoginAttemptSummary empty() {
        return new LoginAttemptSummary(0, 0);
    }
}
