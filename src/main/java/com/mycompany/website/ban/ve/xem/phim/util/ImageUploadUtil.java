package com.mycompany.website.ban.ve.xem.phim.util;

import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;

public final class ImageUploadUtil {
    public static final long MAX_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private ImageUploadUtil() {
    }

    public static String validateAndStore(
            InputStream input, String submittedName, long declaredSize, Path uploadDirectory) throws IOException {
        String extension = extension(submittedName);
        if (!EXTENSIONS.contains(extension)) {
            throw new BookingException(400, "Định dạng tệp không hỗ trợ (chỉ nhận PNG, JPG, JPEG, WEBP).");
        }
        if (declaredSize <= 0 || declaredSize > MAX_BYTES) {
            throw new BookingException(400, "Ảnh tải lên phải nhỏ hơn hoặc bằng 5 MB.");
        }
        byte[] bytes = input.readNBytes((int) MAX_BYTES + 1);
        if (bytes.length == 0 || bytes.length > MAX_BYTES || bytes.length != declaredSize) {
            throw new BookingException(400, "Kích thước tệp tải lên không hợp lệ.");
        }
        if (!isImage(bytes, extension)) {
            throw new BookingException(400, "Nội dung tệp không phải ảnh hợp lệ.");
        }
        Path directory = uploadDirectory.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        String fileName = UUID.randomUUID() + "." + extension;
        Path target = directory.resolve(fileName).normalize();
        if (!target.getParent().equals(directory)) {
            throw new BookingException(400, "Tên tệp không hợp lệ.");
        }
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        return fileName;
    }

    public static String contentType(String fileName) {
        return switch (extension(fileName)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> null;
        };
    }

    private static boolean isImage(byte[] bytes, String extension) throws IOException {
        if ("webp".equals(extension)) {
            return bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP");
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        return image != null && image.getWidth() > 0 && image.getHeight() > 0;
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        if (bytes.length < offset + expected.length()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (bytes[offset + i] != (byte) expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static String extension(String submittedName) {
        if (submittedName == null || submittedName.isBlank()
                || submittedName.contains("..") || submittedName.contains("/")
                || submittedName.contains("\\")) {
            return "";
        }
        int dot = submittedName.lastIndexOf('.');
        if (dot < 1 || dot == submittedName.length() - 1) {
            return "";
        }
        return submittedName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
