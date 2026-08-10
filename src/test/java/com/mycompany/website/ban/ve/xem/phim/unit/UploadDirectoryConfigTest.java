package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.controller.UploadServlet;
import com.mycompany.website.ban.ve.xem.phim.util.ImageUploadUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * CB-ISS-009 — thu muc luu anh phai lay tu cau hinh, khong gan cung duong dan may lap trinh vien.
 */
class UploadDirectoryConfigTest {
    private static final String PROPERTY = "cinebook.upload.dir";

    @AfterEach
    void clearOverride() {
        System.clearProperty(PROPERTY);
    }

    @Test
    @DisplayName("Duong dan cau hinh duoc ton trong va chuan hoa thanh duong dan tuyet doi")
    void honoursConfiguredDirectory(@TempDir Path temp) {
        Path configured = temp.resolve("cinebook-uploads-configured");
        System.setProperty(PROPERTY, configured.toString());

        assertEquals(configured.toAbsolutePath().normalize(), UploadServlet.uploadDirectory());
    }

    @Test
    @DisplayName("Khong co cau hinh thi dung catalina.base, khong bao gio la duong dan cung trong ma nguon")
    void fallsBackToServerBaseDirectory(@TempDir Path temp) {
        String previousBase = System.getProperty("catalina.base");
        System.setProperty("catalina.base", temp.toString());
        try {
            Path resolved = UploadServlet.uploadDirectory();
            assertEquals(temp.resolve("cinebook-uploads").toAbsolutePath().normalize(), resolved);
            assertFalse(resolved.toString().contains("NetBeansProjects"),
                    "Khong duoc tro vao workspace cua may lap trinh vien: " + resolved);
        } finally {
            if (previousBase == null) {
                System.clearProperty("catalina.base");
            } else {
                System.setProperty("catalina.base", previousBase);
            }
        }
    }

    @Test
    @DisplayName("Luu anh that vao thu muc tam khac may dev, ten ngau nhien, doc lai duoc")
    void storesUploadUnderConfiguredTempDirectory(@TempDir Path temp) throws Exception {
        Path configured = temp.resolve("uploads");
        System.setProperty(PROPERTY, configured.toString());
        Path directory = UploadServlet.uploadDirectory();
        Files.createDirectories(directory);

        byte[] png = pngBytes();
        String stored = ImageUploadUtil.validateAndStore(
                new ByteArrayInputStream(png), "poster.png", png.length, directory);

        assertTrue(stored.endsWith(".png"), stored);
        assertFalse(stored.contains("poster"), "Ten file goc khong duoc giu lai: " + stored);
        Path file = directory.resolve(stored);
        assertTrue(Files.isRegularFile(file), "Phai ghi duoc file vao thu muc cau hinh: " + file);
        assertTrue(file.startsWith(directory), "File phai nam trong thu muc cau hinh");
    }

    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
