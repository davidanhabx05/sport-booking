package vn.sportbooking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import vn.sportbooking.exception.ApiException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir
    Path dir;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};

    @Test
    void storesValidPng() throws Exception {
        FileStorageService s = new FileStorageService(dir.toString());
        String url = s.storeImage(new MockMultipartFile("file", "san.png", "image/png", PNG), "venues");
        assertTrue(url.startsWith("/uploads/venues/") && url.endsWith(".png"));
        assertTrue(Files.exists(dir.resolve(url.substring("/uploads/".length()))));
    }

    @Test
    void rejectsFakeImage() {
        FileStorageService s = new FileStorageService(dir.toString());
        // Đổi đuôi .png nhưng nội dung là script → bị chặn nhờ kiểm tra magic bytes
        var fake = new MockMultipartFile("file", "virus.png", "image/png", "<script>alert(1)</script>".getBytes());
        assertThrows(ApiException.class, () -> s.storeImage(fake, "venues"));
        var pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", PNG);
        assertThrows(ApiException.class, () -> s.storeImage(pdf, "venues"));
    }
}
