package vn.sportbooking.service.payment;

import java.util.Map;

/**
 * Chuỗi ký theo tài liệu MoMo Payment Gateway (API v2, captureWallet).
 * Thứ tự các trường là cố định theo đặc tả, KHÔNG sắp xếp tự do.
 */
public final class MomoSigner {
    private MomoSigner() {}

    /** Chữ ký cho request tạo giao dịch. */
    public static String signCreate(String accessKey, String secretKey, long amount, String extraData, String ipnUrl,
                                    String orderId, String orderInfo, String partnerCode, String redirectUrl,
                                    String requestId, String requestType) {
        String raw = "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData=" + extraData
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=" + requestType;
        return HmacUtil.hmacSha256(secretKey, raw);
    }

    /** Chữ ký kết quả MoMo trả về (redirect và IPN dùng chung công thức). */
    public static String signResult(String accessKey, String secretKey, Map<String, String> p) {
        String raw = "accessKey=" + accessKey
                + "&amount=" + p.getOrDefault("amount", "")
                + "&extraData=" + p.getOrDefault("extraData", "")
                + "&message=" + p.getOrDefault("message", "")
                + "&orderId=" + p.getOrDefault("orderId", "")
                + "&orderInfo=" + p.getOrDefault("orderInfo", "")
                + "&orderType=" + p.getOrDefault("orderType", "")
                + "&partnerCode=" + p.getOrDefault("partnerCode", "")
                + "&payType=" + p.getOrDefault("payType", "")
                + "&requestId=" + p.getOrDefault("requestId", "")
                + "&responseTime=" + p.getOrDefault("responseTime", "")
                + "&resultCode=" + p.getOrDefault("resultCode", "")
                + "&transId=" + p.getOrDefault("transId", "");
        return HmacUtil.hmacSha256(secretKey, raw);
    }

    public static boolean verifyResult(String accessKey, String secretKey, Map<String, String> p) {
        return HmacUtil.safeEquals(signResult(accessKey, secretKey, p), p.get("signature"));
    }
}
