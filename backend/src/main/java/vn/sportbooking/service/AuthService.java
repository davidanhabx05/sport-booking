package vn.sportbooking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sportbooking.dto.Requests.*;
import vn.sportbooking.dto.Responses.AuthResponse;
import vn.sportbooking.dto.Responses.UserDto;
import vn.sportbooking.entity.Enums;
import vn.sportbooking.entity.User;
import vn.sportbooking.exception.ApiException;
import vn.sportbooking.repository.UserRepository;
import vn.sportbooking.security.JwtService;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email đã được sử dụng");
        }
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.password()))
                .fullName(req.fullName().trim())
                .phone(blankToNull(req.phone()))
                .role(Enums.Role.CUSTOMER)
                .skillLevel(Enums.SkillLevel.BEGINNER)
                .build();
        userRepository.save(user);
        return new AuthResponse(jwtService.generateToken(user), UserDto.of(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email().trim().toLowerCase())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPassword()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"));
        if (!user.isEnabled()) {
            throw ApiException.forbidden("Tài khoản đã bị khoá, vui lòng liên hệ quản trị viên");
        }
        return new AuthResponse(jwtService.generateToken(user), UserDto.of(user));
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return UserDto.of(getUser(userId));
    }

    @Transactional
    public UserDto updateProfile(Long userId, UpdateProfileRequest req) {
        User user = getUser(userId);
        user.setFullName(req.fullName().trim());
        user.setPhone(blankToNull(req.phone()));
        if (req.skillLevel() != null) user.setSkillLevel(req.skillLevel());
        return UserDto.of(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = getUser(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw ApiException.badRequest("Mật khẩu hiện tại không đúng");
        }
        user.setPassword(passwordEncoder.encode(req.newPassword()));
    }

    private User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
