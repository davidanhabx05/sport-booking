package vn.sportbooking.service.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Logic ký / xác thực theo đặc tả VNPay 2.1.0 (không phụ thuộc Spring để dễ unit test):
 *  - Sắp xếp tham số theo tên tăng dần.
 *  - Nối "key=URLEncode(value)" bằng dấu &amp; → hashData.
 *  - vnp_SecureHash = HMAC-SHA512(hashSecret, hashData).
 */
public final class VnPaySigner {
    private VnPaySigner() {}

    public static String buildQuery(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    public static String sign(String hashSecret, Map<String, String> params) {
        return HmacUtil.hmacSha512(hashSecret, buildQuery(params));
    }

    /** Tạo URL chuyển hướng sang cổng VNPay. */
    public static String buildPaymentUrl(String payUrl, String hashSecret, Map<String, String> params) {
        String query = buildQuery(params);
        return payUrl + "?" + query + "&vnp_SecureHash=" + HmacUtil.hmacSha512(hashSecret, query);
    }

    /** Kiểm tra chữ ký VNPay gửi về (Return URL hoặc IPN). */
    public static boolean verify(String hashSecret, Map<String, String> allParams) {
        String received = allParams.get("vnp_SecureHash");
        Map<String, String> fields = new TreeMap<>();
        allParams.forEach((k, v) -> {
            if (k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")) {
                fields.put(k, v);
            }
        });
        return HmacUtil.safeEquals(sign(hashSecret, fields), received);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.US_ASCII);
    }
}
