package vn.sportbooking.service.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Ký dữ liệu bằng HMAC – dùng cho chữ ký VNPay (SHA512) và MoMo (SHA256). */
public final class HmacUtil {
    private HmacUtil() {}

    public static String hmacSha512(String key, String data) {
        return hmac("HmacSHA512", key, data);
    }

    public static String hmacSha256(String key, String data) {
        return hmac("HmacSHA256", key, data);
    }

    /** So sánh chữ ký không lộ thông tin thời gian (constant-time). */
    public static boolean safeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.toLowerCase().getBytes(StandardCharsets.UTF_8),
                b.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private static String hmac(String algorithm, String key, String data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo chữ ký " + algorithm, e);
        }
    }
}
