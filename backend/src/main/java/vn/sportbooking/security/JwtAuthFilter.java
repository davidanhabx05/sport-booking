package vn.sportbooking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.sportbooking.repository.UserRepository;

import java.io.IOException;

/** Đọc header "Authorization: Bearer <token>" và đặt người dùng vào SecurityContext. */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parseUserId(token)
                    .flatMap(userRepository::findById)
                    .filter(u -> u.isEnabled())
                    .ifPresent(user -> {
                        UserPrincipal principal = new UserPrincipal(user);
                        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }

    /**
     * Lấy token từ header "Authorization: Bearer ...".
     * Riêng luồng SSE (/api/notifications/stream) cho phép ?token= vì EventSource không gửi được header.
     */
    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header.substring(7);
        if ("/api/notifications/stream".equals(request.getRequestURI())) {
            String q = request.getParameter("token");
            if (q != null && !q.isBlank()) return q;
        }
        return null;
    }
}
