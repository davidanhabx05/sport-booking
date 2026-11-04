package vn.sportbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_court_date", columnList = "court_id, booking_date"),
        @Index(name = "idx_booking_user", columnList = "user_id"),
        @Index(name = "idx_booking_group", columnList = "group_code")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã đặt sân hiển thị cho khách, ví dụ SB2610221234. */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id")
    private Court court;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private Long totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.BookingStatus status;

    /** Hạn giữ chỗ khi đang chờ thanh toán. */
    private LocalDateTime holdExpiresAt;

    /** Mã nhóm khi đặt định kỳ hàng tuần: các buổi cùng nhóm được thanh toán một lần. */
    @Column(name = "group_code", length = 20)
    private String groupCode;

    @Column(length = 500)
    private String note;

    @Column(length = 500)
    private String cancelReason;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public LocalDateTime startDateTime() { return LocalDateTime.of(bookingDate, startTime); }
}
