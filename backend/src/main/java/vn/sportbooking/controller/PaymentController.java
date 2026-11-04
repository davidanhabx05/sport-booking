package vn.sportbooking.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.sportbooking.dto.Requests.MockPaymentConfirmRequest;
import vn.sportbooking.dto.Responses.PaymentDto;
import vn.sportbooking.dto.Responses.PaymentInitResponse;
import vn.sportbooking.entity.Enums.PaymentMethod;
import vn.sportbooking.security.CurrentUser;
import vn.sportbooking.service.payment.PaymentService;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "Payments", description = "Thanh toán VNPay, MoMo, thanh toán thử")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Tạo giao dịch và trả về URL để frontend chuyển hướng sang cổng thanh toán. */
    @PostMapping("/bookings/{bookingId}")
    public PaymentInitResponse init(@PathVariable Long bookingId, @RequestParam PaymentMethod method,
                                    HttpServletRequest request) {
        return paymentService.init(CurrentUser.require(), bookingId, method, clientIp(request));
    }

    @GetMapping("/bookings/{bookingId}")
    public List<PaymentDto> history(@PathVariable Long bookingId) {
        return paymentService.listForBooking(CurrentUser.require(), bookingId);
    }

    @PostMapping("/mock/confirm")
    public PaymentDto mockConfirm(@Valid @RequestBody MockPaymentConfirmRequest req) {
        return paymentService.mockConfirm(CurrentUser.require(), req.txnRef(), req.success());
    }

    // ---------- VNPay ----------

    @GetMapping("/vnpay/return")
    public ResponseEntity<Void> vnpayReturn(@RequestParam Map<String, String> params) {
        return redirect(() -> paymentService.handleVnPayReturn(params));
    }

    @GetMapping("/vnpay/ipn")
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        try {
            return paymentService.handleVnPayIpn(params);
        } catch (Exception e) {
            log.error("VNPay IPN error", e);
            return Map.of("RspCode", "99", "Message", "Unknown error");
        }
    }

    // ---------- MoMo ----------

    @GetMapping("/momo/return")
    public ResponseEntity<Void> momoReturn(@RequestParam Map<String, String> params) {
        return redirect(() -> paymentService.handleMomoReturn(params));
    }

    @PostMapping("/momo/ipn")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void momoIpn(@RequestBody Map<String, Object> body) {
        paymentService.handleMomoIpn(body);
    }

    private ResponseEntity<Void> redirect(java.util.function.Supplier<String> handler) {
        String target;
        try {
            target = handler.get();
        } catch (Exception e) {
            log.warn("Payment return error: {}", e.getMessage());
            target = frontendUrl + "/payment/result?status=failed";
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
