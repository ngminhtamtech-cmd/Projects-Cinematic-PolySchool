package com.mycompany.website.ban.ve.xem.phim.api;

/**
 * Bao bi thong nhat cho moi response cua /api/v1.
 *
 * <pre>
 * Thanh cong : { "data": ..., "meta": { ... } }
 * That bai   : { "error": { "code": "...", "message": "..." } }
 * </pre>
 */
public final class ApiEnvelope {
    private Object data;
    private PageMeta meta;
    private ErrorBody error;

    public static ApiEnvelope ok(Object data) {
        ApiEnvelope envelope = new ApiEnvelope();
        envelope.data = data;
        return envelope;
    }

    public static ApiEnvelope page(Object data, int page, int size, long total) {
        ApiEnvelope envelope = ok(data);
        envelope.meta = new PageMeta(page, size, total);
        return envelope;
    }

    public static ApiEnvelope fail(String code, String message) {
        ApiEnvelope envelope = new ApiEnvelope();
        envelope.error = new ErrorBody(code, message);
        return envelope;
    }

    public Object getData() {
        return data;
    }

    public PageMeta getMeta() {
        return meta;
    }

    public ErrorBody getError() {
        return error;
    }

    public static final class PageMeta {
        private final int page;
        private final int size;
        private final long total;
        private final int totalPages;

        PageMeta(int page, int size, long total) {
            this.page = page;
            this.size = size;
            this.total = total;
            this.totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        }

        public int getPage() {
            return page;
        }

        public int getSize() {
            return size;
        }

        public long getTotal() {
            return total;
        }

        public int getTotalPages() {
            return totalPages;
        }
    }

    public static final class ErrorBody {
        private final String code;
        private final String message;

        ErrorBody(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
