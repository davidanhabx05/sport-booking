package vn.sportbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.PaymentMethod method;

    @Column(nullable = false)
    private Long amount;

    /** Mã giao dịch phía hệ thống gửi sang cổng thanh toán (vnp_TxnRef / MoMo orderId). */
    @Column(nullable = false, unique = true, length = 50)
    private String txnRef;

    /** Mã giao dịch phía cổng thanh toán trả về. */
    @Column(length = 50)
    private String gatewayTransactionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.PaymentStatus status;

    @Column(length = 20)
    private String responseCode;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime paidAt;
}
