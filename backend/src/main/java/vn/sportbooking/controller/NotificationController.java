package vn.sportbooking.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.sportbooking.dto.Responses.NotificationDto;
import vn.sportbooking.security.CurrentUser;
import vn.sportbooking.service.NotificationService;

import java.util.List;
import java.util.Map;

@Tag(name = "Notifications", description = "Thông báo trong ứng dụng, đẩy realtime bằng Server-Sent Events")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationDto> list() {
        return notificationService.list(CurrentUser.id());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread() {
        return Map.of("unread", notificationService.unreadCount(CurrentUser.id()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable Long id) {
        notificationService.markRead(CurrentUser.id(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll() {
        notificationService.markAllRead(CurrentUser.id());
    }

    /**
     * Kết nối SSE. Trình duyệt dùng EventSource, vốn không gửi được header Authorization,
     * nên token JWT được truyền qua tham số ?token= (JwtAuthFilter chỉ nhận cách này ở đường dẫn này).
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return notificationService.subscribe(CurrentUser.id());
    }
}
