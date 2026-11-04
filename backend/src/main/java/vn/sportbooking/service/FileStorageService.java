package vn.sportbooking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.sportbooking.exception.ApiException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/** Lưu ảnh tải lên vào thư mục trên đĩa; ảnh được phục vụ qua đường dẫn /uploads/**. */
@Service
public class FileStorageService {

    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    /** Lưu ảnh vào {@code root/folder/uuid.ext}, trả về URL công khai "/uploads/folder/uuid.ext". */
    public String storeImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Chưa chọn ảnh");
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("Ảnh tối đa 5MB");
        String ext = ALLOWED.get(file.getContentType());
        if (ext == null) throw ApiException.badRequest("Chỉ nhận ảnh JPG, PNG hoặc WEBP");
        try (InputStream in = file.getInputStream()) {
            if (!looksLikeImage(in.readNBytes(12))) throw ApiException.badRequest("Tệp không phải ảnh hợp lệ");
        } catch (IOException e) {
            throw ApiException.badRequest("Không đọc được tệp ảnh");
        }

        String name = UUID.randomUUID() + ext;
        Path dir = root.resolve(folder).normalize();
        if (!dir.startsWith(root)) throw ApiException.badRequest("Thư mục không hợp lệ");
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Không lưu được ảnh", e);
        }
        return "/uploads/" + folder + "/" + name;
    }

    /** Kiểm tra "magic bytes" – không tin vào phần mở rộng hay Content-Type do client gửi. */
    static boolean looksLikeImage(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xff) == 0xFF && (h[1] & 0xff) == 0xD8 && (h[2] & 0xff) == 0xFF) return true; // JPEG
        if (h.length >= 8 && (h[0] & 0xff) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return true;      // PNG
        return h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';                                    // WEBP
    }
}
