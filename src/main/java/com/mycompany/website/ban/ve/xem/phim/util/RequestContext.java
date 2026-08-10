package com.mycompany.website.ban.ve.xem.phim.util;

/**
 * Ngu canh HTTP cua request dang xu ly, de tang service ghi duoc "tu dau" vao audit (BUG-09, INV-9).
 *
 * <p><b>Van de goc.</b> Bang {@code AuditLogs} co san {@code IpAddress} va {@code UserAgent}, nhung
 * {@code AdminService.logAction} 5 tham so — ban duoc goi o gan nhu moi cho — khong he co ngu canh
 * HTTP de dien vao. Do thuc te tren 71 dong: {@code IpAddress} 0, {@code UserAgent} 0. Ghi duoc
 * ai/gi/khi nao nhung khong truy duoc ve IP hay thiet bi.</p>
 *
 * <p><b>Bat buoc phai xoa trong {@code finally}.</b> Tomcat tai dung thread giua cac request; quen
 * xoa la ngu canh cua nguoi nay ro ri sang dong audit cua nguoi khac — sai lech con nguy hiem hon
 * la de trong. {@link com.mycompany.website.ban.ve.xem.phim.filter.RequestContextFilter} chiu trach
 * nhiem nay.</p>
 *
 * <p>Doc ra khi khong co ngu canh (job nen, sweeper, test) tra {@code null} — dung y: mot thao tac
 * khong den tu HTTP thi khong co IP that de ghi.</p>
 */
public final class RequestContext {

    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    /** Do dai toi da luu vao audit; User-Agent that co the rat dai. Cot la NVARCHAR(512). */
    private static final int MAX_USER_AGENT_LENGTH = 400;

    /**
     * Khop dung {@code AuditLogs.IpAddress NVARCHAR(64)} (A.3, BUG-09).
     *
     * <p>Truoc day ipAddress dung chung hang so 400 cua User-Agent. Vi {@code X-Forwarded-For} la
     * do client gui len, bat ky ai cung dat duoc mot chuoi 100 ky tu: INSERT audit loi truncation,
     * {@code logAction} nem 500 — va cho nem do nam NGAY SAU {@code commit()} cua
     * {@code refundOrder}, tuc la tien da hoan xong roi. Hai gioi han khac nhau thi phai la hai
     * hang so khac nhau; dung chung mot hang so chinh la goc cua loi nay.</p>
     */
    private static final int MAX_IP_ADDRESS_LENGTH = 64;

    private RequestContext() {
    }

    public record Snapshot(String ipAddress, String userAgent) {
    }

    public static void set(String ipAddress, String userAgent) {
        CURRENT.set(new Snapshot(trimToNull(ipAddress, MAX_IP_ADDRESS_LENGTH),
                trimToNull(userAgent, MAX_USER_AGENT_LENGTH)));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static String ipAddress() {
        Snapshot snapshot = CURRENT.get();
        return snapshot == null ? null : snapshot.ipAddress();
    }

    public static String userAgent() {
        Snapshot snapshot = CURRENT.get();
        return snapshot == null ? null : snapshot.userAgent();
    }

    private static String trimToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
