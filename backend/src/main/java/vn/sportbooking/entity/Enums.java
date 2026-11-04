package vn.sportbooking.entity;

/**
 * Gom các enum dùng chung của hệ thống vào một chỗ cho dễ đọc.
 */
public final class Enums {
    private Enums() {}

    public enum Role { CUSTOMER, OWNER, ADMIN }

    public enum SportType {
        PICKLEBALL("Pickleball"),
        BADMINTON("Cầu lông"),
        FOOTBALL_5("Bóng đá sân 5"),
        FOOTBALL_7("Bóng đá sân 7"),
        TENNIS("Tennis");

        private final String label;
        SportType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    /** Vòng đời đơn đặt sân. */
    public enum BookingStatus {
        PENDING_PAYMENT, // đang giữ chỗ, chờ thanh toán (tối đa N phút)
        CONFIRMED,       // đã thanh toán, giữ sân thành công
        COMPLETED,       // đã check-in / đã chơi xong
        CANCELLED,       // khách hoặc chủ sân huỷ
        EXPIRED          // quá hạn giữ chỗ mà chưa thanh toán
    }

    public enum PaymentMethod { VNPAY, MOMO, MOCK }

    public enum PaymentStatus { PENDING, SUCCESS, FAILED }

    public enum SkillLevel { BEGINNER, INTERMEDIATE, ADVANCED }

    public enum MatchStatus { OPEN, FULL, CLOSED }

    public enum ParticipantStatus { PENDING, ACCEPTED, REJECTED }

    public enum NotificationType {
        BOOKING_CONFIRMED, BOOKING_CANCELLED, BOOKING_EXPIRED, NEW_BOOKING,
        MATCH_REQUEST, MATCH_ACCEPTED, MATCH_REJECTED
    }
}
