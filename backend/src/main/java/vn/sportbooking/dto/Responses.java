package vn.sportbooking.dto;

import vn.sportbooking.entity.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Các DTO trả về cho client. Không trả entity trực tiếp để tránh lộ dữ liệu và lỗi lazy-loading. */
public final class Responses {
    private Responses() {}

    public record AuthResponse(String token, UserDto user) {}

    public record UserDto(Long id, String email, String fullName, String phone,
                          Enums.Role role, Enums.SkillLevel skillLevel, boolean enabled, LocalDateTime createdAt) {
        public static UserDto of(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(),
                    u.getRole(), u.getSkillLevel(), u.isEnabled(), u.getCreatedAt());
        }
    }

    /** Thông tin công khai của người dùng (không có email). */
    public record PublicUserDto(Long id, String fullName, Enums.SkillLevel skillLevel) {
        public static PublicUserDto of(User u) {
            return new PublicUserDto(u.getId(), u.getFullName(), u.getSkillLevel());
        }
    }

    public record CourtDto(Long id, String name, Enums.SportType sportType, String sportLabel,
                           Long pricePerHour, Long peakPricePerHour, boolean indoor, boolean active) {
        public static CourtDto of(Court c) {
            return new CourtDto(c.getId(), c.getName(), c.getSportType(), c.getSportType().getLabel(),
                    c.getPricePerHour(), c.getPeakPricePerHour(), c.isIndoor(), c.isActive());
        }
    }

    public record VenueSummaryDto(Long id, String name, String address, String district, String imageUrl,
                                  LocalTime openTime, LocalTime closeTime,
                                  List<Enums.SportType> sports, Long minPrice, Double rating, long reviewCount,
                                  boolean active) {}

    public record VenueDetailDto(Long id, String name, String address, String district, String description,
                                 String phone, String imageUrl, LocalTime openTime, LocalTime closeTime,
                                 LocalTime peakStart, LocalTime peakEnd, boolean active,
                                 List<CourtDto> courts, Double rating, long reviewCount, Long ownerId) {}

    public record SlotDto(LocalTime start, LocalTime end, long price, boolean peak, String status) {}

    public record CourtAvailabilityDto(CourtDto court, List<SlotDto> slots) {}

    public record AvailabilityDto(Long venueId, LocalDate date, List<CourtAvailabilityDto> courts) {}

    public record BookingDto(Long id, String code, Long courtId, String courtName, Enums.SportType sportType,
                             Long venueId, String venueName, String venueAddress,
                             LocalDate date, LocalTime startTime, LocalTime endTime, Long totalPrice,
                             Enums.BookingStatus status, LocalDateTime holdExpiresAt, String note,
                             String cancelReason, LocalDateTime createdAt,
                             String customerName, String customerPhone, boolean hasMatchPost,
                             String groupCode, int groupSize, long payableAmount) {
        /**
         * @param groupSize     số buổi trong nhóm đặt định kỳ (1 nếu đặt lẻ)
         * @param payableAmount số tiền còn phải trả cho cả nhóm (0 nếu không còn buổi nào chờ thanh toán)
         */
        public static BookingDto of(Booking b, boolean hasMatchPost, int groupSize, long payableAmount) {
            Court c = b.getCourt();
            Venue v = c.getVenue();
            return new BookingDto(b.getId(), b.getCode(), c.getId(), c.getName(), c.getSportType(),
                    v.getId(), v.getName(), v.getAddress(),
                    b.getBookingDate(), b.getStartTime(), b.getEndTime(), b.getTotalPrice(),
                    b.getStatus(), b.getHoldExpiresAt(), b.getNote(), b.getCancelReason(), b.getCreatedAt(),
                    b.getUser().getFullName(), b.getUser().getPhone(), hasMatchPost,
                    b.getGroupCode(), groupSize, payableAmount);
        }
    }

    public record PaymentInitResponse(String paymentUrl, String txnRef, Enums.PaymentMethod method) {}

    public record PaymentDto(Long id, Enums.PaymentMethod method, Long amount, String txnRef,
                             String gatewayTransactionNo, Enums.PaymentStatus status, String responseCode,
                             LocalDateTime createdAt, LocalDateTime paidAt) {
        public static PaymentDto of(Payment p) {
            return new PaymentDto(p.getId(), p.getMethod(), p.getAmount(), p.getTxnRef(),
                    p.getGatewayTransactionNo(), p.getStatus(), p.getResponseCode(), p.getCreatedAt(), p.getPaidAt());
        }
    }

    public record ParticipantDto(Long id, PublicUserDto user, Enums.ParticipantStatus status,
                                 String message, String contactPhone, LocalDateTime createdAt) {}

    public record MatchDto(Long id, String title, String description, Enums.SkillLevel skillLevel,
                           Integer slotsNeeded, long acceptedCount, Long pricePerPerson, Enums.MatchStatus status,
                           PublicUserDto host, String hostPhone,
                           Long bookingId, LocalDate date, LocalTime startTime, LocalTime endTime,
                           Enums.SportType sportType, String courtName, Long venueId, String venueName,
                           String venueAddress, String district,
                           List<ParticipantDto> participants, String myStatus, boolean isHost,
                           LocalDateTime createdAt) {}

    public record ReviewDto(Long id, PublicUserDto user, Integer rating, String comment, LocalDateTime createdAt) {
        public static ReviewDto of(Review r) {
            return new ReviewDto(r.getId(), PublicUserDto.of(r.getUser()), r.getRating(), r.getComment(), r.getCreatedAt());
        }
    }

    public record NotificationDto(Long id, Enums.NotificationType type, String title, String message,
                                  String link, boolean read, LocalDateTime createdAt) {
        public static NotificationDto of(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(),
                    n.isRead(), n.getCreatedAt());
        }
    }

    public record MetaDto(List<Map<String, String>> sports, List<String> districts, List<Map<String, String>> skillLevels) {}

    public record StatsDto(LocalDate from, LocalDate to,
                           long totalRevenue, long totalBookings, long confirmedBookings, long cancelledBookings,
                           double occupancyRate,
                           List<Map<String, Object>> revenueByDay,
                           List<Map<String, Object>> revenueBySport,
                           List<Map<String, Object>> topCourts,
                           List<Map<String, Object>> bookingsByHour) {}
}
