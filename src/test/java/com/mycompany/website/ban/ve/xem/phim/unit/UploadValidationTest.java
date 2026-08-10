package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.util.ImageUploadUtil;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UploadValidationTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsRenamedJspAndPathTraversal() {
        byte[] shell = "<% Runtime.getRuntime(); %>".getBytes(StandardCharsets.UTF_8);
        assertThrows(BookingException.class, () -> ImageUploadUtil.validateAndStore(
                new ByteArrayInputStream(shell), "shell.jpg", shell.length, tempDir));
        assertThrows(BookingException.class, () -> ImageUploadUtil.validateAndStore(
                new ByteArrayInputStream(shell), "../shell.jpg", shell.length, tempDir));
    }

    @Test
    void storesRealPngUnderRandomName() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        String name = ImageUploadUtil.validateAndStore(
                new ByteArrayInputStream(bytes.toByteArray()), "poster.png", bytes.size(), tempDir);
        assertTrue(name.matches("[0-9a-f-]{36}\\.png"));
        assertTrue(Files.isRegularFile(tempDir.resolve(name)));
        assertEquals("image/png", ImageUploadUtil.contentType(name));
    }
}
