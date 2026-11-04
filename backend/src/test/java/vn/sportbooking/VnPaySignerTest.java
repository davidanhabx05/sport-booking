package vn.sportbooking;

import org.junit.jupiter.api.Test;
import vn.sportbooking.service.payment.HmacUtil;
import vn.sportbooking.service.payment.MomoSigner;
import vn.sportbooking.service.payment.VnPaySigner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VnPaySignerTest {

    private static final String SECRET = "SECRETKEY123";

    @Test
    void queryIsSortedAndEncoded() {
        Map<String, String> p = new HashMap<>();
        p.put("vnp_TxnRef", "SB1");
        p.put("vnp_Amount", "15000000");
        p.put("vnp_OrderInfo", "Thanh toan dat san");
        assertEquals("vnp_Amount=15000000&vnp_OrderInfo=Thanh+toan+dat+san&vnp_TxnRef=SB1", VnPaySigner.buildQuery(p));
    }

    @Test
    void signatureRoundTripAndTamperDetection() {
        Map<String, String> p = new HashMap<>();
        p.put("vnp_TxnRef", "SB2610221234");
        p.put("vnp_Amount", "30000000");
        p.put("vnp_ResponseCode", "00");
        p.put("vnp_TransactionStatus", "00");
        p.put("vnp_SecureHash", VnPaySigner.sign(SECRET, p));
        p.put("vnp_SecureHashType", "HmacSHA512");
        assertTrue(VnPaySigner.verify(SECRET, p));

        p.put("vnp_Amount", "100"); // kẻ gian sửa số tiền
        assertFalse(VnPaySigner.verify(SECRET, p));
    }

    @Test
    void hmacSha256KnownVector() {
        // RFC 4231 test case 2
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                HmacUtil.hmacSha256("Jefe", "what do ya want for nothing?"));
    }

    @Test
    void momoResultSignatureVerifies() {
        Map<String, String> p = new HashMap<>(Map.of(
                "partnerCode", "MOMO", "orderId", "SB1", "requestId", "SB1", "amount", "150000",
                "orderInfo", "Thanh toan", "orderType", "momo_wallet", "transId", "123", "resultCode", "0",
                "message", "Successful.", "payType", "qr"));
        p.put("responseTime", "1700000000000");
        p.put("extraData", "");
        p.put("signature", MomoSigner.signResult("ACCESS", "SECRET", p));
        assertTrue(MomoSigner.verifyResult("ACCESS", "SECRET", p));
        p.put("resultCode", "1006");
        assertFalse(MomoSigner.verifyResult("ACCESS", "SECRET", p));
    }
}
