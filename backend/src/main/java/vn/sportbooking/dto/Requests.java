package vn.sportbooking.dto;

import jakarta.validation.constraints.*;
import vn.sportbooking.entity.Enums;

import java.time.LocalDate;
import java.time.LocalTime;

/** Các DTO dữ liệu client gửi lên (request body). */
public final class Requests {
    private Requests() {}

    public record RegisterRequest(
            @NotBlank(message = "Vui lòng nhập email") @Email(message = "Email không hợp lệ") String email,
            @NotBlank(message = "Vui lòng nhập mật khẩu") @Size(min = 6, message = "Mật khẩu tối thiểu 6 ký tự") String password,
            @NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 100) String fullName,
            @Pattern(regexp = "^(0\\d{9})?$", message = "Số điện thoại phải gồm 10 số, bắt đầu bằng 0") String phone
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 100) String fullName,
            @Pattern(regexp = "^(0\\d{9})?$", message = "Số điện thoại phải gồm 10 số, bắt đầu bằng 0") String phone,
            Enums.SkillLevel skillLevel
    ) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 6, message = "Mật khẩu tối thiểu 6 ký tự") String newPassword
    ) {}

    public record CreateBookingRequest(
            @NotNull(message = "Vui lòng chọn sân") Long courtId,
            @NotNull(message = "Vui lòng chọn ngày") @FutureOrPresent(message = "Không thể đặt ngày trong quá khứ") LocalDate date,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @Size(max = 500) String note
    ) {}

    /** Đặt cùng một khung giờ, cùng sân, lặp lại mỗi tuần trong {@code weeks} tuần. */
    public record RecurringBookingRequest(
            @NotNull(message = "Vui lòng chọn sân") Long courtId,
            @NotNull(message = "Vui lòng chọn ngày bắt đầu") @FutureOrPresent LocalDate startDate,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull @Min(value = 2, message = "Đặt định kỳ tối thiểu 2 tuần") @Max(value = 8, message = "Đặt định kỳ tối đa 8 tuần") Integer weeks,
            @Size(max = 500) String note
    ) {}

    public record CancelBookingRequest(@Size(max = 500) String reason) {}

    public record VenueRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank String address,
            @NotBlank @Size(max = 60) String district,
            @Size(max = 1000) String description,
            String phone,
            String imageUrl,
            @NotNull LocalTime openTime,
            @NotNull LocalTime closeTime,
            LocalTime peakStart,
            LocalTime peakEnd,
            Boolean active
    ) {}

    public record CourtRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull Enums.SportType sportType,
            @NotNull @Min(value = 10000, message = "Giá tối thiểu 10.000đ") Long pricePerHour,
            @Min(10000) Long peakPricePerHour,
            Boolean indoor,
            Boolean active
    ) {}

    public record CreateMatchRequest(
            @NotNull(message = "Vui lòng chọn đơn đặt sân") Long bookingId,
            @NotBlank @Size(max = 150) String title,
            @Size(max = 1000) String description,
            @NotNull Enums.SkillLevel skillLevel,
            @NotNull @Min(1) @Max(20) Integer slotsNeeded,
            @Min(0) Long pricePerPerson
    ) {}

    public record JoinMatchRequest(@Size(max = 300) String message) {}

    public record ReviewRequest(
            @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 1000) String comment
    ) {}

    public record MockPaymentConfirmRequest(@NotBlank String txnRef, boolean success) {}

    public record UpdateUserRequest(Enums.Role role, Boolean enabled) {}
}
