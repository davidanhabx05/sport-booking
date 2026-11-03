package vn.sportbooking.entity;

public final class Enums {
    private Enums() {}

    public enum Role{ CUSTOMER, OWNER, ADMIN}

    public enum SkillLevel {BEGINNER, INTERMEDIATE, ADVANCED}

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


    public enum BookingStatus {
        PENDING_PAYMENT,
        CONFIRMED,
        CANCELLED,
        EXPIRED
    }

    public enum PaymentMethod { VNPAY, MOMO, MOCK }

    public enum PaymentStatus { PENDING, SUCCESS, FAILED }


    public enum MatchStatus { OPEN, FULL, CLOSED }

    public enum ParticipantStatus { PENDING, ACCEPTED, REJECTED }

    public enum NotificationType {
        BOOKING_CONFIRMED, BOOKING_CANCELLED, BOOKING_EXPIRED, NEW_BOOKING,
        MATCH_REQUEST, MATCH_ACCEPTED, MATCH_REJECTED
    }

}
