package vn.sportbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Thông báo trong ứng dụng (chuông thông báo), được đẩy realtime qua Server-Sent Events. */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notification_user", columnList = "user_id, created_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Enums.NotificationType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 500)
    private String message;

    /** Đường dẫn trên frontend khi bấm vào thông báo. */
    @Column(length = 200)
    private String link;

    /** "read" là từ khoá của MySQL nên đặt tên cột khác. */
    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
