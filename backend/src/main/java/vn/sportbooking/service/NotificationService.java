package vn.sportbooking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.sportbooking.dto.Responses.NotificationDto;
import vn.sportbooking.entity.Enums;
import vn.sportbooking.entity.Notification;
import vn.sportbooking.exception.ApiException;
import vn.sportbooking.repository.NotificationRepository;
import vn.sportbooking.repository.UserRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thông báo realtime bằng Server-Sent Events (SSE):
 * mỗi tab trình duyệt đang mở giữ một kết nối HTTP dài tới /api/notifications/stream;
 * khi có sự kiện, server lưu thông báo vào DB rồi đẩy ngay xuống các kết nối của người nhận.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final long STREAM_TIMEOUT_MS = 30 * 60 * 1000L;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /** userId → các kết nối SSE đang mở (một người có thể mở nhiều tab). */
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // ===================== GỬI THÔNG BÁO =====================

    /** Lưu thông báo; đẩy realtime sau khi transaction commit. */
    @Transactional
    public void notify(Long userId, Enums.NotificationType type, String title, String message, String link) {
        Notification n = Notification.builder()
                .user(userRepository.getReferenceById(userId))
                .type(type).title(title).message(message).link(link)
                .build();
        notificationRepository.save(n);
        NotificationDto dto = NotificationDto.of(n);
        AfterCommit.run(() -> push(userId, dto));
    }

    // ===================== API CHO NGƯỜI DÙNG =====================

    @Transactional(readOnly = true)
    public List<NotificationDto> list(Long userId) {
        return notificationRepository.findTop30ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationDto::of).toList();
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification n = notificationRepository.findById(id)
                .filter(x -> x.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông báo"));
        n.setRead(true);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    // ===================== SSE =====================

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> {
            List<SseEmitter> list = emitters.get(userId);
            if (list != null) list.remove(emitter);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("unread", unreadCount(userId))));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    private void push(Long userId, NotificationDto dto) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(dto, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                list.remove(emitter);
            }
        }
    }

    /** Gửi "nhịp tim" 25 giây/lần để proxy (Nginx) không cắt kết nối đang rảnh. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    list.remove(emitter);
                }
            }
        });
    }

    /** Số kết nối đang mở (dùng cho test / giám sát). */
    public int openStreams(Long userId) {
        List<SseEmitter> list = emitters.get(userId);
        return list == null ? 0 : list.size();
    }
}
