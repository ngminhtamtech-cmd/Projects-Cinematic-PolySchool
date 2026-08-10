package com.mycompany.website.ban.ve.xem.phim.util;

import com.mycompany.website.ban.ve.xem.phim.config.DatabaseConfig;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class QrCodeUtil {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SECRET_PROPERTY = "ticket.hmac.secret";

    private QrCodeUtil() {
    }

    public static void writePng(String content, int size, OutputStream outputStream) throws IOException {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    size,
                    size,
                    Map.of(EncodeHintType.MARGIN, 1)
            );
            MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);
        } catch (WriterException ex) {
            throw new IOException("Cannot generate QR code", ex);
        }
    }

    public static String signedPayload(String ticketCode) {
        return signedPayload(ticketCode, requiredSecret());
    }

    static String signedPayload(String ticketCode, String secret) {
        return ticketCode + "." + signature(ticketCode, secret);
    }

    public static String verifiedTicketCode(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        int separator = payload.lastIndexOf('.');
        if (separator < 0) {
            return isNewTicketCode(payload) ? null : payload;
        }
        String ticketCode = payload.substring(0, separator);
        String supplied = payload.substring(separator + 1);
        String expected = signature(ticketCode, requiredSecret());
        return MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII)) ? ticketCode : null;
    }

    public static boolean isNewTicketCode(String code) {
        return code != null && code.matches("CB[A-Z2-7]{26}");
    }

    private static String signature(String value, String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("ticket.hmac.secret must contain at least 32 characters");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
        }
    }

    private static String requiredSecret() {
        String secret = DatabaseConfig.load().getProperty(SECRET_PROPERTY);
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Missing required configuration property: " + SECRET_PROPERTY);
        }
        return secret.trim();
    }
}
