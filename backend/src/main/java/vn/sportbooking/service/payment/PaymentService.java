package vn.sportbooking.service.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import vn.sportbooking.dto.Responses.PaymentDto;
import vn.sportbooking.dto.Responses.PaymentInitResponse;
import vn.sportbooking.entity.Booking;
import vn.sportbooking.entity.Enums;
import vn.sportbooking.entity.Enums.PaymentMethod;
import vn.sportbooking.entity.Enums.PaymentStatus;
import vn.sportbooking.exception.ApiException;
import vn.sportbooking.security.UserPrincipal;
import vn.sportbooking.service.BookingService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Điều phối thanh toán: VNPay sandbox, MoMo sandbox và MOCK (giả lập để demo/test).
 * Quy tắc: chỉ IPN/Return đã xác thực chữ ký mới được đổi trạng thái đơn; xử lý idempotent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;
    private final RestClient restClient = RestClient.create();

    @Value("${app.frontend-url}") private String frontendUrl;
    @Value("${app.backend-url}") private String backendUrl;
    @Value("${app.payment.mock-enabled:true}") private boolean mockEnabled;

    @Value("${app.payment.vnpay.tmn-code:}") private String vnpTmnCode;
    @Value("${app.payment.vnpay.hash-secret:}") private String vnpHashSecret;
    @Value("${app.payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") private String vnpPayUrl;

    @Value("${app.payment.momo.partner-code:}") private String momoPartnerCode;
    @Value("${app.payment.momo.access-key:}") private String momoAccessKey;
    @Value("${app.payment.momo.secret-key:}") private String momoSecretKey;
    @Value("${app.payment.momo.endpoint:https://test-payment.momo.vn/v2/gateway/api/create}") private String momoEndpoint;

    /** Các phương thức đang bật (để frontend hiển thị). */
    public List<PaymentMethod> availableMethods() {
        List<PaymentMethod> list = new ArrayList<>();
        if (!vnpTmnCode.isBlank() && !vnpHashSecret.isBlank()) list.add(PaymentMethod.VNPAY);
        if (!momoPartnerCode.isBlank() && !momoSecretKey.isBlank()) list.add(PaymentMethod.MOMO);
        if (mockEnabled) list.add(PaymentMethod.MOCK);
        return list;
    }

    @Transactional
    public PaymentInitResponse init(UserPrincipal me, Long bookingId, PaymentMethod method, String clientIp) {
        Booking booking = bookingService.getBooking(bookingId);
        if (!booking.getUser().getId().equals(me.getId())) throw ApiException.forbidden("Đây không phải đơn của bạn");
        if (booking.getStatus() != Enums.BookingStatus.PENDING_PAYMENT
                || booking.getHoldExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("Đơn không còn ở trạng thái chờ thanh toán (có thể đã hết hạn giữ chỗ)");
        }
        if (!availableMethods().contains(method)) {
            throw ApiException.badRequest("Phương thức " + method + " chưa được cấu hình trên hệ thống");
        }

        // Đặt định kỳ: một lần thanh toán cho mọi buổi còn chờ trong nhóm
        long amount = bookingService.payableAmount(booking);
        if (amount <= 0) throw ApiException.badRequest("Không còn khoản nào cần thanh toán cho đơn này");

        String txnRef = booking.getCode() + (System.currentTimeMillis() % 100_000_000L);
        Payment payment = Payment.builder()
                .booking(booking).method(method).amount(amount)
                .txnRef(txnRef).status(PaymentStatus.PENDING).build();
        paymentRepository.save(payment);

        String url = switch (method) {
            case VNPAY -> buildVnPayUrl(payment, booking, clientIp);
            case MOMO -> createMomoPayment(payment, booking);
            case MOCK -> frontendUrl + "/payment/mock?txnRef=" + txnRef + "&amount=" + payment.getAmount()
                    + "&code=" + booking.getCode() + "&bookingId=" + booking.getId();
        };
        return new PaymentInitResponse(url, txnRef, method);
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> listForBooking(UserPrincipal me, Long bookingId) {
        bookingService.checkCanView(me, bookingService.getBooking(bookingId));
        return paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId).stream().map(PaymentDto::of).toList();
    }

    // ===================== VNPAY =====================

    private String buildVnPayUrl(Payment p, Booking b, String clientIp) {
        LocalDateTime now = LocalDateTime.now(VN);
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnpTmnCode);
        params.put("vnp_Amount", String.valueOf(p.getAmount() * 100)); // VNPay yêu cầu nhân 100
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", p.getTxnRef());
        params.put("vnp_OrderInfo", "Thanh toan dat san " + b.getCode()); // không dấu theo khuyến nghị VNPay
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", backendUrl + "/api/payments/vnpay/return");
        params.put("vnp_IpAddr", clientIp == null ? "127.0.0.1" : clientIp);
        params.put("vnp_CreateDate", now.format(VNP_TIME));
        params.put("vnp_ExpireDate", b.getHoldExpiresAt().format(VNP_TIME));
        return VnPaySigner.buildPaymentUrl(vnpPayUrl, vnpHashSecret, params);
    }

    /** Return URL: trình duyệt khách quay về sau khi thanh toán → trả về URL frontend để redirect. */
    @Transactional
    public String handleVnPayReturn(Map<String, String> params) {
        Payment p = processVnPay(params);
        return resultUrl(p);
    }

    /** IPN: VNPay gọi server-to-server. Trả về RspCode theo đặc tả. */
    @Transactional
    public Map<String, String> handleVnPayIpn(Map<String, String> params) {
        if (!VnPaySigner.verify(vnpHashSecret, params)) return Map.of("RspCode", "97", "Message", "Invalid signature");
        Optional<Payment> opt = paymentRepository.findByTxnRef(params.get("vnp_TxnRef"));
        if (opt.isEmpty()) return Map.of("RspCode", "01", "Message", "Order not found");
        Payment p = opt.get();
        if (!String.valueOf(p.getAmount() * 100).equals(params.get("vnp_Amount"))) {
            return Map.of("RspCode", "04", "Message", "Invalid amount");
        }
        if (p.getStatus() != PaymentStatus.PENDING) return Map.of("RspCode", "02", "Message", "Order already confirmed");
        processVnPay(params);
        return Map.of("RspCode", "00", "Message", "Confirm Success");
    }

    private Payment processVnPay(Map<String, String> params) {
        if (!VnPaySigner.verify(vnpHashSecret, params)) {
            throw ApiException.badRequest("Chữ ký VNPay không hợp lệ");
        }
        Payment p = paymentRepository.findByTxnRef(params.get("vnp_TxnRef"))
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy giao dịch"));
        boolean amountOk = String.valueOf(p.getAmount() * 100).equals(params.get("vnp_Amount"));
        boolean success = amountOk && "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));
        complete(p, success, params.get("vnp_TransactionNo"), params.get("vnp_ResponseCode"));
        return p;
    }

    // ===================== MOMO =====================

    @SuppressWarnings("unchecked")
    private String createMomoPayment(Payment p, Booking b) {
        String requestId = p.getTxnRef();
        String orderInfo = "Thanh toan dat san " + b.getCode();
        String redirectUrl = backendUrl + "/api/payments/momo/return";
        String ipnUrl = backendUrl + "/api/payments/momo/ipn";
        String requestType = "captureWallet";
        String extraData = "";
        String signature = MomoSigner.signCreate(momoAccessKey, momoSecretKey, p.getAmount(), extraData, ipnUrl,
                p.getTxnRef(), orderInfo, momoPartnerCode, redirectUrl, requestId, requestType);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", momoPartnerCode);
        body.put("requestId", requestId);
        body.put("amount", p.getAmount());
        body.put("orderId", p.getTxnRef());
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", redirectUrl);
        body.put("ipnUrl", ipnUrl);
        body.put("requestType", requestType);
        body.put("extraData", extraData);
        body.put("lang", "vi");
        body.put("signature", signature);

        Map<String, Object> res;
        try {
            res = restClient.post().uri(momoEndpoint).contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(Map.class);
        } catch (Exception e) {
            log.error("MoMo create payment failed", e);
            throw ApiException.badRequest("Không kết nối được MoMo, vui lòng thử lại hoặc chọn phương thức khác");
        }
        if (res == null || !"0".equals(String.valueOf(res.get("resultCode")))) {
            throw ApiException.badRequest("MoMo từ chối giao dịch: " + (res == null ? "không phản hồi" : res.get("message")));
        }
        return String.valueOf(res.get("payUrl"));
    }

    @Transactional
    public String handleMomoReturn(Map<String, String> params) {
        Payment p = processMomo(params);
        return resultUrl(p);
    }

    @Transactional
    public void handleMomoIpn(Map<String, Object> body) {
        Map<String, String> params = new HashMap<>();
        body.forEach((k, v) -> params.put(k, v == null ? "" : String.valueOf(v)));
        processMomo(params);
    }

    private Payment processMomo(Map<String, String> params) {
        if (!MomoSigner.verifyResult(momoAccessKey, momoSecretKey, params)) {
            throw ApiException.badRequest("Chữ ký MoMo không hợp lệ");
        }
        Payment p = paymentRepository.findByTxnRef(params.get("orderId"))
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy giao dịch"));
        boolean success = "0".equals(params.get("resultCode"))
                && String.valueOf(p.getAmount()).equals(params.get("amount"));
        complete(p, success, params.get("transId"), params.get("resultCode"));
        return p;
    }

    // ===================== MOCK =====================

    @Transactional
    public PaymentDto mockConfirm(UserPrincipal me, String txnRef, boolean success) {
        if (!mockEnabled) throw ApiException.forbidden("Thanh toán thử đã bị tắt");
        Payment p = paymentRepository.findByTxnRef(txnRef)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy giao dịch"));
        if (p.getMethod() != PaymentMethod.MOCK) throw ApiException.badRequest("Giao dịch không phải thanh toán thử");
        if (!p.getBooking().getUser().getId().equals(me.getId())) throw ApiException.forbidden("Đây không phải giao dịch của bạn");
        complete(p, success, "MOCK" + System.currentTimeMillis(), success ? "00" : "24");
        return PaymentDto.of(p);
    }

    // ===================== COMMON =====================

    /** Cập nhật kết quả; idempotent – giao dịch đã chốt thì bỏ qua. */
    private void complete(Payment p, boolean success, String gatewayTxn, String responseCode) {
        if (p.getStatus() != PaymentStatus.PENDING) return;
        p.setGatewayTransactionNo(gatewayTxn);
        p.setResponseCode(responseCode);
        if (success) {
            p.setStatus(PaymentStatus.SUCCESS);
            p.setPaidAt(LocalDateTime.now());
            bookingService.markPaid(p.getBooking().getId());
            log.info("Payment {} SUCCESS for booking {}", p.getTxnRef(), p.getBooking().getCode());
        } else {
            p.setStatus(PaymentStatus.FAILED);
            log.info("Payment {} FAILED code={}", p.getTxnRef(), responseCode);
        }
    }

    private String resultUrl(Payment p) {
        return frontendUrl + "/payment/result?bookingId=" + p.getBooking().getId()
                + "&status=" + (p.getStatus() == PaymentStatus.SUCCESS ? "success" : "failed")
                + "&method=" + p.getMethod();
    }
}
