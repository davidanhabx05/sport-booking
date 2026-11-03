package vn.sportbooking.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.sportbooking.exception.ApiException;

import java.util.Optional;

/** Tiện ích lấy người dùng đang đăng nhập từ SecurityContext. */
public final class CurrentUser {
    private CurrentUser() {}

    public static Optional<UserPrincipal> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public static UserPrincipal require() {
        return get().orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập"));
    }

    public static Long id() { return require().getId(); }
}
