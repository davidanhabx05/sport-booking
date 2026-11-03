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

}
